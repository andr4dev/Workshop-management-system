package com.workshopmanagement.rdmotors.ventas.aplicacion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.caja.dominio.SinTurnoAbiertoException;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.clientes.aplicacion.FiarVenta;
import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;
import com.workshopmanagement.rdmotors.inventario.dominio.MovimientoKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;
import com.workshopmanagement.rdmotors.ventas.dominio.Descuento;
import com.workshopmanagement.rdmotors.ventas.dominio.LineaVenta;
import com.workshopmanagement.rdmotors.ventas.dominio.PagoVenta;
import com.workshopmanagement.rdmotors.ventas.dominio.ProblemaDeRenglon;
import com.workshopmanagement.rdmotors.ventas.dominio.RenglonesConProblemaException;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;
import com.workshopmanagement.rdmotors.ventas.dominio.puerto.RepositorioVentas;

/**
 * CASO DE USO — cobrar una venta de mostrador (spec 0003, fase 2 del plan).
 *
 * <p>Bajo el mismo commit van: la venta con renglones y pagos, su número, el stock de cada repuesto,
 * los movimientos de kardex, el evento del descuento y, si quedó algo fiado, la deuda del cliente (spec 0008).
 * <b>O todo o nada:</b> jamás stock descontado sin venta, ni una venta sin su stock, ni un fiado sin su deuda.
 *
 * <h2>El orden importa, y cada paso tiene su porqué</h2>
 * <ol>
 *   <li><b>La llave.</b> Si ya se cobró con esta llave (doble clic, reintento tras un corte), se devuelve
 *       esa venta.</li>
 *   <li><b>El turno, bloqueado antes que nada.</b> Sin turno abierto no se vende. Se comparte con otros cobros
 *       y excluye al cierre (spec 0006, RF-017): si el turno se está cerrando, este cobro espera y encuentra
 *       el turno cerrado; si no, el cierre espera a que termine y lo cuenta.</li>
 *   <li><b>Bloquear los repuestos, siempre en el mismo orden (por id).</b> Dos ventas con los mismos
 *       repuestos en distinto orden se esperarían la una a la otra para siempre.</li>
 *   <li><b>La llave otra vez.</b> Si un cobro con la misma llave llegó a la vez, este esperó sus
 *       bloqueos y ahora ya ve la venta guardada. Sin esta segunda mirada, el reintento de una venta de
 *       la última unidad respondería "sin stock" en vez de "ya está cobrada".</li>
 *   <li><b>Revisar todos los renglones</b> y fallar con la lista completa, antes de mover nada.</li>
 *   <li><b>El cliente, bloqueado al final</b> si se le fía: con sus datos completos y el fiado abierto. Después de
 *       los repuestos, como en un abono y en una anulación (plan 0008, decisión 4).</li>
 *   <li><b>Pedir el número</b>, con los repuestos ya bloqueados: el contador queda tomado lo menos
 *       posible y siempre después de los repuestos.</li>
 *   <li>Armar la venta, sacar el stock con su kardex, auditar el descuento y guardar.</li>
 * </ol>
 */
@Transactional
public class CobrarVenta {

    private final RepositorioVentas ventas;
    private final RepositorioTurnos turnos;
    private final RepositorioVariantes variantes;
    private final RepositorioKardex kardex;
    private final RepositorioAuditoria auditoria;
    private final FiarVenta fiar;
    private final Reloj reloj;

    public CobrarVenta(RepositorioVentas ventas, RepositorioTurnos turnos, RepositorioVariantes variantes,
                       RepositorioKardex kardex, RepositorioAuditoria auditoria, FiarVenta fiar, Reloj reloj) {
        this.ventas = ventas;
        this.turnos = turnos;
        this.variantes = variantes;
        this.kardex = kardex;
        this.auditoria = auditoria;
        this.fiar = fiar;
        this.reloj = reloj;
    }

    public ResultadoCobro ejecutar(ComandoCobrarVenta comando) {
        Optional<Venta> yaCobrada = ventas.buscarPorLlave(comando.llave());
        if (yaCobrada.isPresent()) {
            return ResultadoCobro.repetida(yaCobrada.get());
        }

        TurnoCaja turno = turnos.abiertoParaMover().orElseThrow(SinTurnoAbiertoException::new);
        turno.exigirQuePuedaOperar(comando.actor());
        exigirRepuestosDistintos(comando.renglones());

        Map<UUID, Variante> bloqueadas = bloquearEnOrden(comando.renglones());

        yaCobrada = ventas.buscarPorLlave(comando.llave());
        if (yaCobrada.isPresent()) {
            return ResultadoCobro.repetida(yaCobrada.get());
        }

        exigirQueSePuedanCobrar(comando.renglones(), bloqueadas);

        Cliente cliente = comando.clienteId() == null ? null
                : comando.fia() ? fiar.clienteParaFiar(comando.clienteId())
                : fiar.clienteDeLaVenta(comando.clienteId());

        long numero = ventas.siguienteNumero();
        Instant ahora = reloj.ahora();

        // Los renglones en el orden en que el cajero los agregó: así salen en el comprobante.
        List<LineaVenta> lineas = new ArrayList<>();
        Dinero subtotal = Dinero.CERO;
        for (ComandoCobrarVenta.Renglon renglon : comando.renglones()) {
            LineaVenta linea = LineaVenta.de(bloqueadas.get(renglon.varianteId()), renglon.cantidad());
            lineas.add(linea);
            subtotal = subtotal.mas(linea.getTotal());
        }
        Descuento descuento = comando.descuento() == null ? null : comando.descuento().sobre(subtotal);
        List<PagoVenta> pagos = comando.pagos().stream().map(ComandoCobrarVenta.Pago::aPago).toList();

        Venta venta = Venta.cobrar(numero, turno.getId(), lineas, descuento, pagos, comando.clienteId(),
                Dinero.de(comando.fiado()), comando.actor().id(), comando.llave(), ahora);

        for (LineaVenta linea : venta.getLineas()) {
            Variante variante = linea.getVariante();
            // "No se vende sin stock" vive DENTRO de la variante. Ya se revisó arriba para poder listar
            // todos los problemas; esta es la última palabra, con la fila bloqueada.
            variante.descontar(linea.getCantidad());
            MovimientoKardex salida = MovimientoKardex.porVenta(variante, linea.getCantidad(), venta.getId(),
                    comando.actor().id(), ahora);
            kardex.agregar(salida);
            linea.anotarSalida(salida.getId());
        }

        if (venta.tieneDescuento()) {
            auditoria.registrar(EventoAuditoria.nuevo(ahora, comando.actor().id(),
                    AccionAuditada.APLICAR_DESCUENTO, Venta.TIPO_AUDITORIA, venta.getId(),
                    venta.fotografiaSinDescuento(), venta.fotografiaConDescuento(), venta.getDescuentoMotivo()));
        }

        Venta guardada = ventas.guardar(venta);
        if (guardada.tieneFiado()) {
            fiar.registrar(cliente, guardada.getId(), guardada.getNumero(), reloj.hoy(), guardada.getFiado(),
                    comando.actor().id(), ahora);
        }
        return ResultadoCobro.nueva(guardada);
    }

    private static void exigirRepuestosDistintos(List<ComandoCobrarVenta.Renglon> renglones) {
        Set<UUID> vistos = new HashSet<>();
        for (ComandoCobrarVenta.Renglon renglon : renglones) {
            if (renglon.varianteId() == null) {
                throw new ReglaDeNegocioException("Un renglón no dice qué repuesto se vende");
            }
            if (!vistos.add(renglon.varianteId())) {
                throw new ReglaDeNegocioException("El mismo repuesto aparece dos veces en la venta. Súmalo en un solo renglón.");
            }
        }
    }

    /** Siempre en orden de id: es lo que evita que dos cobros concurrentes se traben entre sí. */
    private Map<UUID, Variante> bloquearEnOrden(List<ComandoCobrarVenta.Renglon> renglones) {
        Map<UUID, Variante> bloqueadas = new HashMap<>();
        renglones.stream()
                .map(ComandoCobrarVenta.Renglon::varianteId)
                .sorted(Comparator.naturalOrder())
                .forEach(id -> variantes.buscarParaModificar(id).ifPresent(v -> bloqueadas.put(id, v)));
        return bloqueadas;
    }

    private static void exigirQueSePuedanCobrar(List<ComandoCobrarVenta.Renglon> renglones,
                                                Map<UUID, Variante> bloqueadas) {
        List<ProblemaDeRenglon> problemas = new ArrayList<>();
        for (ComandoCobrarVenta.Renglon renglon : renglones) {
            Variante variante = bloqueadas.get(renglon.varianteId());
            if (variante == null) {
                problemas.add(ProblemaDeRenglon.noExiste(renglon.varianteId()));
                continue;
            }
            UUID id = variante.getId();
            String codigo = variante.getCodigo();
            if (!variante.isActiva()) {
                problemas.add(ProblemaDeRenglon.de(id, codigo, ProblemaDeRenglon.Tipo.INACTIVO));
            } else if (variante.getPrecio().esCero()) {
                problemas.add(ProblemaDeRenglon.de(id, codigo, ProblemaDeRenglon.Tipo.SIN_PRECIO));
            } else if (!variante.getPrecio().equals(Dinero.de(renglon.precioVisto()))) {
                problemas.add(ProblemaDeRenglon.precioCambiado(id, codigo, renglon.precioVisto(),
                        variante.getPrecio().valor().longValueExact()));
            }
            if (renglon.cantidad() > 0 && variante.getStock() < renglon.cantidad()) {
                problemas.add(ProblemaDeRenglon.sinStock(id, codigo, variante.getStock(), renglon.cantidad()));
            }
        }
        if (!problemas.isEmpty()) {
            throw new RenglonesConProblemaException(problemas);
        }
    }
}
