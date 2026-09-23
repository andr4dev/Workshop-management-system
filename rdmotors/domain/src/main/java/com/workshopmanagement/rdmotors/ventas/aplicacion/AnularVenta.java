package com.workshopmanagement.rdmotors.ventas.aplicacion;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.caja.dominio.SinTurnoAbiertoException;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.clientes.aplicacion.FiarVenta;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.Motivo;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;
import com.workshopmanagement.rdmotors.inventario.dominio.MovimientoKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;
import com.workshopmanagement.rdmotors.ventas.dominio.LineaVenta;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;
import com.workshopmanagement.rdmotors.ventas.dominio.puerto.RepositorioVentas;

/**
 * CASO DE USO — anular una venta (spec 0003, H8, RF-025 a RF-027).
 *
 * <p>Se cobró mal o el cliente se arrepintió en el mostrador. En orden:
 * <ol>
 *   <li>Exige motivo y un turno abierto, bloqueado antes que nada: lo que devuelve la caja cuenta en el turno
 *       de hoy, aunque la venta sea de un turno anterior (RF-026), y el cierre no puede calcularse en medio
 *       (spec 0006, RF-017).</li>
 *   <li>Bloquea la venta. Dos anulaciones a la vez: la segunda espera y encuentra la venta ya anulada.</li>
 *   <li>Bloquea los repuestos <b>en orden de id</b>, el mismo que usa cobrar: una anulación y un cobro
 *       con los mismos repuestos no se traban.</li>
 *   <li>Devuelve cada unidad al stock sin tocar el costo promedio, y escribe en el kardex una REVERSION
 *       que apunta a la salida que deshace, con su mismo costo.</li>
 *   <li>Marca la venta y deja el evento de auditoría con el antes y el después.</li>
 *   <li>Si quedó algo fiado, anula su deuda (spec 0008, RF-027): lo que se le había abonado pasa a las otras deudas
 *       del cliente o le queda a favor. Por la parte fiada no sale plata del cajón.</li>
 * </ol>
 *
 * <p>El número no se reutiliza: la venta sigue existiendo, anulada. La siguiente sigue la serie.
 */
@Transactional
public class AnularVenta {

    private final RepositorioVentas ventas;
    private final RepositorioTurnos turnos;
    private final RepositorioVariantes variantes;
    private final RepositorioKardex kardex;
    private final RepositorioAuditoria auditoria;
    private final FiarVenta fiar;
    private final Reloj reloj;

    public AnularVenta(RepositorioVentas ventas, RepositorioTurnos turnos, RepositorioVariantes variantes,
                       RepositorioKardex kardex, RepositorioAuditoria auditoria, FiarVenta fiar, Reloj reloj) {
        this.ventas = ventas;
        this.turnos = turnos;
        this.variantes = variantes;
        this.kardex = kardex;
        this.auditoria = auditoria;
        this.fiar = fiar;
        this.reloj = reloj;
    }

    public Venta ejecutar(UUID ventaId, String motivo, Actor actor) {
        String motivoLimpio = Motivo.exigir(motivo);
        if (actor == null) {
            throw new ReglaDeNegocioException("Falta el usuario que anula");
        }
        UUID usuarioId = actor.id();
        TurnoCaja turno = turnos.abiertoParaMover().orElseThrow(() -> new SinTurnoAbiertoException(
                "No hay un turno abierto. Para anular, ábrelo en Vender: lo que se le devuelve al cliente sale del turno de hoy."));
        turno.exigirQuePuedaOperar(actor);

        Venta venta = ventas.buscarParaModificar(ventaId)
                .orElseThrow(() -> new ReglaDeNegocioException("La venta no existe"));
        venta.exigirCobrada();
        Map<String, Object> antes = venta.fotografia();

        Instant ahora = reloj.ahora();
        List<LineaVenta> enOrdenDeBloqueo = venta.getLineas().stream()
                .sorted(Comparator.comparing(l -> l.getVariante().getId()))
                .toList();
        for (LineaVenta linea : enOrdenDeBloqueo) {
            devolver(linea, ventaId, motivoLimpio, usuarioId, ahora);
        }

        // Recién con el stock devuelto la venta se marca: nunca anulada con su mercancía afuera.
        venta.anular(motivoLimpio, usuarioId, turno.getId(), ahora);
        auditoria.registrar(EventoAuditoria.nuevo(ahora, usuarioId, AccionAuditada.ANULAR_VENTA,
                Venta.TIPO_AUDITORIA, ventaId, antes, venta.fotografia(), motivoLimpio));
        Venta guardada = ventas.guardar(venta);
        if (guardada.tieneFiado()) {
            fiar.alAnular(ventaId, guardada.getClienteId(), usuarioId, ahora);
        }
        return guardada;
    }

    private void devolver(LineaVenta linea, UUID ventaId, String motivo, UUID usuarioId, Instant ahora) {
        String codigo = linea.getVariante().getCodigo();
        MovimientoKardex salida = linea.getMovimientoSalidaId() == null ? null
                : kardex.buscar(linea.getMovimientoSalidaId()).orElse(null);
        if (salida == null) {
            // No debería pasar: cobrar anota la salida de cada renglón. Sin ella no hay qué revertir.
            throw new ReglaDeNegocioException("El renglón de " + codigo + " no tiene su salida en el kardex");
        }
        Variante variante = variantes.buscarParaModificar(linea.getVariante().getId())
                .orElseThrow(() -> new ReglaDeNegocioException("El repuesto " + codigo + " no existe"));

        variante.reponerPorReversion(linea.getCantidad());
        kardex.agregar(MovimientoKardex.porReversionDeVenta(variante, salida, ventaId, motivo, usuarioId, ahora));
        variantes.guardar(variante);
    }
}
