package com.workshopmanagement.rdmotors.inventario.aplicacion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;
import com.workshopmanagement.rdmotors.inventario.dominio.ConsultaInventario;
import com.workshopmanagement.rdmotors.inventario.dominio.ConteoCategoria;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;

/**
 * CASO DE USO — encontrar un repuesto.
 *
 * <p>Dos formas, porque son dos gestos distintos:
 *
 * <ul>
 *   <li>{@link #porCodigoExacto} — el administrador copia el código de la factura del proveedor.
 *       Devuelve uno o ninguno. Es lo que hace que el campo de código sirva de buscador (RF-007b):
 *       si encuentra, selecciona; si no, el borde ofrece crear el repuesto con ese código ya puesto.
 *   <li>{@link #porTexto} — se busca sin saber el código: "filtro", "pulsar", "inoki".
 *   <li>{@link #inventario} — la pantalla de inventario: todo, por páginas, sin teclear nada.
 * </ul>
 *
 * <p>Este mismo caso de uso lo usará el cajero en la rebanada 2 con un cliente enfrente, así que se
 * construye pensando en eso: resultados acotados y el concepto ya resuelto.
 */
@Transactional(readOnly = true)
public class BuscarRepuestos {

    /** Tope por defecto. Con miles de repuestos, teclear dos letras no puede traerlo todo. */
    public static final int LIMITE_POR_DEFECTO = 50;

    /** Tope de una página de inventario. Pedir diez mil filas por URL no puede tumbar el mostrador. */
    public static final int TAMANO_MAXIMO_PAGINA = 100;

    private final RepositorioVariantes variantes;

    public BuscarRepuestos(RepositorioVariantes variantes) {
        this.variantes = variantes;
    }

    public Optional<RepuestoEncontrado> porCodigoExacto(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        return variantes.buscarPorCodigo(codigo.trim().toUpperCase())
                .map(RepuestoEncontrado::de);
    }

    /** La ficha del repuesto: se llega aquí desde el inventario o con el enlace directo. */
    public Optional<RepuestoEncontrado> porId(UUID id) {
        return variantes.buscar(id).map(RepuestoEncontrado::de);
    }

    /**
     * El inventario por páginas.
     *
     * <p>Los parámetros vienen de una URL, así que se sanean aquí y no en el controlador: una página
     * negativa se vuelve la primera y un tamaño absurdo se recorta. Así la regla vale igual si mañana
     * lo llama otra cosa que no sea HTTP.
     */
    public Pagina<RepuestoEncontrado> inventario(String texto, boolean soloStockBajo,
                                                 int pagina, int tamano) {
        return inventario(ConsultaInventario.de(texto, soloStockBajo), pagina, tamano);
    }

    /** El inventario con todos sus filtros: categoría y, para el catálogo del mostrador, stock primero. */
    public Pagina<RepuestoEncontrado> inventario(ConsultaInventario consulta, int pagina, int tamano) {
        int paginaSegura = Math.max(pagina, 0);
        int tamanoSeguro = Math.clamp(tamano, 1, TAMANO_MAXIMO_PAGINA);
        return variantes.listar(consulta == null ? ConsultaInventario.de("", false) : consulta,
                        paginaSegura, tamanoSeguro)
                .mapear(RepuestoEncontrado::de);
    }

    /**
     * Cuántos de lo buscado hay en cada categoría (spec 0005, RF-002): con *aceite*, *Filtros 2 ·
     * Lubricantes y químicos 5*. Sumados, son el total del inventario con ese texto.
     */
    public List<ConteoCategoria> conteoPorCategoria(String texto) {
        return variantes.contarPorCategoria(texto == null ? "" : texto.trim());
    }

    public List<RepuestoEncontrado> porTexto(String texto) {
        return porTexto(texto, LIMITE_POR_DEFECTO);
    }

    public List<RepuestoEncontrado> porTexto(String texto, int limite) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        return variantes.buscarPorTexto(texto.trim(), limite).stream()
                .map(RepuestoEncontrado::de)
                .toList();
    }
}
