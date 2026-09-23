package com.workshopmanagement.rdmotors.compras.aplicacion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Motivo;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.LineaCompra;
import com.workshopmanagement.rdmotors.compras.dominio.RenglonesBloqueadosException;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCompras;
import com.workshopmanagement.rdmotors.inventario.dominio.TipoMovimiento;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;

/**
 * CASO DE USO — anular una compra que no debió registrarse (spec 0002, H5).
 *
 * <p>Se registró dos veces, o no era de esta tienda. Todo lo que metió al inventario sale, con las
 * mismas reglas que corregir un renglón, y la compra queda en el historial marcada como anulada.
 *
 * <p><b>Si un solo renglón está bloqueado, no se anula nada</b> y se dice cuáles (RF-022). Anular a
 * medias dejaría una compra "anulada" con parte de su mercancía todavía en el inventario.
 */
@Transactional
public class AnularCompra {

    private final RepositorioCompras compras;
    private final RepositorioTurnos turnos;
    private final RepositorioAuditoria auditoria;
    private final InventarioDeCompra inventario;
    private final Reloj reloj;

    public AnularCompra(RepositorioCompras compras, RepositorioTurnos turnos, RepositorioVariantes variantes,
                        RepositorioKardex kardex, RepositorioAuditoria auditoria, Reloj reloj) {
        this.compras = compras;
        this.turnos = turnos;
        this.auditoria = auditoria;
        // Anular no crea repuestos: no necesita CrearRepuesto.
        this.inventario = new InventarioDeCompra(variantes, kardex, compras, null);
        this.reloj = reloj;
    }

    public ResultadoCorreccion ejecutar(UUID compraId, long versionEsperada, String motivo,
                                        Actor actor) {
        String motivoLimpio = Motivo.exigir(motivo);
        if (actor == null) {
            throw new ReglaDeNegocioException("Falta el usuario que anula");
        }
        actor.exigirAdministrador();
        UUID usuarioId = actor.id();
        Compra compra = compras.buscarParaModificar(compraId)
                .orElseThrow(() -> new ReglaDeNegocioException("La compra no existe"));
        compra.exigirVersion(versionEsperada);
        compra.exigirVigente();
        if (compra.isPagadaDeCaja()) {
            // Se pone en fila con el cierre (spec 0006, RF-017): si su turno sigue abierto, el cierre ve la
            // compra anulada o no la ve anular. Si ya cerró, se anula igual y su arqueo no cambia.
            turnos.abiertoParaMover();
        }

        Map<String, Object> antes = compra.fotografia();
        List<LineaCompra> vigentes = compra.lineasVigentes();

        List<String> bloqueados = inventario.bloqueados(vigentes);
        if (!bloqueados.isEmpty()) {
            throw new RenglonesBloqueadosException(bloqueados);
        }

        Instant ahora = reloj.ahora();
        List<String> avisos = new ArrayList<>();
        for (LineaCompra linea : vigentes) {
            avisos.addAll(inventario.revertirEntrada(linea, TipoMovimiento.ANULACION_COMPRA,
                    compraId, motivoLimpio, usuarioId, ahora));
        }

        // Recién con el inventario revertido la compra se marca: nunca anulada con la mercancía dentro.
        compra.anular(motivoLimpio, usuarioId, ahora);
        auditoria.registrar(EventoAuditoria.nuevo(ahora, usuarioId, AccionAuditada.ANULAR_COMPRA,
                Compra.TIPO_AUDITORIA, compraId, antes, compra.fotografia(), motivoLimpio));

        return new ResultadoCorreccion(compras.guardar(compra), avisos);
    }
}
