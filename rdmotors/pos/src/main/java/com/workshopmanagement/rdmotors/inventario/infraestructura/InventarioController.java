package com.workshopmanagement.rdmotors.inventario.infraestructura;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;
import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.ActorActual;
import com.workshopmanagement.rdmotors.inventario.aplicacion.BuscarRepuestos;
import com.workshopmanagement.rdmotors.inventario.dominio.ConsultaInventario;
import com.workshopmanagement.rdmotors.inventario.dominio.FiltroCategoria;
import com.workshopmanagement.rdmotors.inventario.dominio.ResumenInventario;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;
import com.workshopmanagement.rdmotors.inventario.infraestructura.RepuestoController.RespuestaRepuesto;

import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — la pantalla de inventario del administrador.
 *
 * <p>A diferencia de la búsqueda de la compra, aquí se quiere ver lo que hay sin teclear nada: con
 * texto vacío devuelve todo, paginado.
 *
 * <p>El listado pasa por el caso de uso porque mapea y aplica la regla del costo desconocido. El
 * resumen usa el puerto directo: no hay nada que mapear ni proteger (ver la bitácora del plan 0001).
 */
@RestController
@RequestMapping("/api/inventario")
@RequiredArgsConstructor
class InventarioController {

    private final BuscarRepuestos buscarRepuestos;
    private final RepositorioVariantes variantes;

    /**
     * @param categoriaId     solo los de esa categoría (spec 0005)
     * @param sinCategoria    solo los que no tienen categoría; no se combina con {@code categoriaId}
     * @param conStockPrimero el orden del catálogo del mostrador: los agotados al final
     */
    @GetMapping
    RespuestaPagina listar(@RequestParam(defaultValue = "") String q,
                           @RequestParam(defaultValue = "false") boolean soloStockBajo,
                           @RequestParam(required = false) UUID categoriaId,
                           @RequestParam(defaultValue = "false") boolean sinCategoria,
                           @RequestParam(defaultValue = "false") boolean conStockPrimero,
                           @RequestParam(defaultValue = "0") int pagina,
                           @RequestParam(defaultValue = "25") int tamano,
                           @ActorActual Actor actor) {
        var consulta = new ConsultaInventario(q, soloStockBajo, FiltroCategoria.desde(categoriaId, sinCategoria),
                conStockPrimero);
        Pagina<Object> p = buscarRepuestos.inventario(consulta, pagina, tamano)
                .mapear(r -> RespuestaRepuesto.de(r).para(actor));
        return new RespuestaPagina(p.elementos(), p.total(), p.numero(), p.tamano(), p.totalPaginas());
    }

    /**
     * Cuántos de lo buscado hay en cada categoría: los números del catálogo del mostrador (spec 0005,
     * RF-002). {@code categoriaId} y {@code nombre} nulos = los que no tienen categoría.
     */
    @GetMapping("/categorias")
    List<RespuestaConteo> categorias(@RequestParam(defaultValue = "") String q) {
        return buscarRepuestos.conteoPorCategoria(q).stream()
                .map(c -> new RespuestaConteo(c.categoriaId(), c.nombre(), c.repuestos()))
                .toList();
    }

    record RespuestaConteo(UUID categoriaId, String nombre, long repuestos) {
    }

    @GetMapping("/resumen")
    Object resumen(@ActorActual Actor actor) {
        ResumenInventario r = variantes.resumen();
        if (!actor.rol().veCostos()) {
            return new RespuestaResumenSinCostos(r.referencias(), r.unidades(), r.conStockBajo());
        }
        return new RespuestaResumen(r.referencias(), r.unidades(),
                Dinero.de(r.valor()).valor().longValueExact(), r.sinCosto(), r.conStockBajo());
    }

    record RespuestaPagina(List<Object> elementos, long total, int numero, int tamano, int totalPaginas) {
    }

    /** El resumen del cajero: sin el valor del inventario ni cuántos no tienen costo (spec 0004, RF-011). */
    record RespuestaResumenSinCostos(long referencias, long unidades, long conStockBajo) {
    }

    /** {@code valor} no incluye los repuestos sin costo; {@code sinCosto} dice cuántos son. */
    record RespuestaResumen(long referencias, long unidades, long valor, long sinCosto,
                            long conStockBajo) {
    }
}
