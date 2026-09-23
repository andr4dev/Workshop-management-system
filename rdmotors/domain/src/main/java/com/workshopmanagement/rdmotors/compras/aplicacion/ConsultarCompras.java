package com.workshopmanagement.rdmotors.compras.aplicacion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;
import com.workshopmanagement.rdmotors.compras.dominio.BusquedaDeRepuesto;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.EstadoCompra;
import com.workshopmanagement.rdmotors.compras.dominio.FiltroCompras;
import com.workshopmanagement.rdmotors.compras.dominio.LineaCompra;
import com.workshopmanagement.rdmotors.compras.dominio.ResumenCompra;
import com.workshopmanagement.rdmotors.compras.dominio.TotalesCompras;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCompras;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;

/**
 * CASO DE USO — ver las compras registradas (spec 0002, H2).
 *
 * <p>Lleva caso de uso aunque solo lea, por la regla de la bitácora del plan 0001: <b>mapea</b> la
 * compra a su detalle —que necesita leer relaciones dentro de la transacción y juntarla con su rastro
 * de auditoría— y <b>sanea</b> la página que llega por URL.
 */
@Transactional(readOnly = true)
public class ConsultarCompras {

    /** Tope de una página de historial. Pedir diez mil facturas por URL no puede tumbar la tienda. */
    public static final int TAMANO_MAXIMO_PAGINA = 100;

    private final RepositorioCompras compras;
    private final RepositorioAuditoria auditoria;
    private final RepositorioUsuarios usuarios;

    public ConsultarCompras(RepositorioCompras compras, RepositorioAuditoria auditoria,
                            RepositorioUsuarios usuarios) {
        this.usuarios = usuarios;
        this.compras = compras;
        this.auditoria = auditoria;
    }

    /** Una página negativa se vuelve la primera y un tamaño absurdo se recorta; nunca revienta. */
    public Pagina<ResumenCompra> historial(FiltroCompras filtro, int pagina, int tamano, Actor actor) {
        actor.exigirAdministrador();
        return compras.historial(
                filtro == null ? FiltroCompras.sinFiltros() : filtro,
                Math.max(pagina, 0),
                Math.clamp(tamano, 1, TAMANO_MAXIMO_PAGINA));
    }

    /**
     * El historial con, en cada factura, lo que trae: sus primeros renglones (spec 0002, RF-028) y, si se
     * buscó un repuesto, los que coinciden (RF-027).
     *
     * <p><b>Cuáles coinciden lo decide {@link BusquedaDeRepuesto#coincideCon}</b>, la misma función que
     * marca el detalle al abrir la factura. La consulta de la base solo decide qué facturas salen; los
     * renglones que se muestran debajo pasan por la regla del dominio. Así la lista no puede mostrar un
     * renglón que el detalle no resalte, ni al revés.
     *
     * <p>Los renglones vigentes de toda la página se piden <b>en una sola consulta</b>, busque o no.
     */
    public Pagina<FilaHistorial> historialConCoincidencias(FiltroCompras filtro, int pagina, int tamano,
                                                          Actor actor) {
        Pagina<ResumenCompra> facturas = historial(filtro, pagina, tamano, actor);
        if (facturas.elementos().isEmpty()) {
            return facturas.mapear(r -> new FilaHistorial(r, List.of(), List.of()));
        }
        BusquedaDeRepuesto busqueda = filtro == null ? null : filtro.repuesto();
        List<UUID> ids = facturas.elementos().stream().map(ResumenCompra::id).toList();
        Map<UUID, List<LineaCompra>> porCompra = compras.lineasVigentesDe(ids).stream()
                .collect(Collectors.groupingBy(linea -> linea.getCompra().getId(), LinkedHashMap::new,
                        Collectors.toList()));
        return facturas.mapear(r -> {
            List<LineaCompra> lineas = porCompra.getOrDefault(r.id(), List.of());
            return new FilaHistorial(r,
                    lineas.stream().limit(FilaHistorial.MAXIMO_PRIMEROS).map(FilaHistorial.RenglonQueCoincide::de)
                            .toList(),
                    busqueda == null ? List.of()
                            : lineas.stream().filter(linea -> busqueda.coincideCon(linea.getVariante()))
                                    .map(FilaHistorial.RenglonQueCoincide::de).toList());
        });
    }

    /**
     * Los totales del período filtrado. <b>Las anuladas no cuentan, pida lo que pida el filtro</b>
     * (RF-010): un total de plata pagada no puede incluir compras que se deshicieron.
     */
    public TotalesCompras totales(FiltroCompras filtro, Actor actor) {
        actor.exigirAdministrador();
        FiltroCompras f = filtro == null ? FiltroCompras.sinFiltros() : filtro;
        if (f.estado() == EstadoCompra.ANULADA) {
            // Pedir "solo anuladas" no puede devolver los totales de las vigentes: es cero.
            return new TotalesCompras(Dinero.CERO, 0, List.of());
        }
        return compras.totales(f.conEstado(EstadoCompra.VIGENTE));
    }

    public Optional<DetalleCompra> detalle(UUID id, Actor actor) {
        return detalle(id, null, actor);
    }

    /**
     * El detalle, con los renglones que coinciden con la búsqueda marcados (spec 0002, RF-026). Se
     * abre desde la lista filtrada por repuesto, y tiene que resaltar justo lo que la hizo salir.
     *
     * @param busqueda {@code null} si no se viene de una búsqueda: ningún renglón queda marcado
     */
    public Optional<DetalleCompra> detalle(UUID id, BusquedaDeRepuesto busqueda, Actor actor) {
        actor.exigirAdministrador();
        return compras.buscarConLineas(id).map(compra -> {
            List<EventoAuditoria> correcciones = auditoria.historialDe(Compra.TIPO_AUDITORIA, compra.getId());
            // Quienes la registraron, la anularon y la corrigieron, en una sola consulta (spec 0004, RF-022).
            Map<UUID, String> nombres = usuarios.nombresDe(Stream.concat(
                            Stream.of(compra.getRegistradoPorId(), compra.getAnuladaPorId()),
                            correcciones.stream().map(EventoAuditoria::usuarioId))
                    .filter(Objects::nonNull).toList());
            return DetalleCompra.de(compra, correcciones, busqueda, nombres);
        });
    }
}
