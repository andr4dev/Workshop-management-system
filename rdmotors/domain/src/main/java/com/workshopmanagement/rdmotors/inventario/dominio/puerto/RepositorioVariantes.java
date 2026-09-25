package com.workshopmanagement.rdmotors.inventario.dominio.puerto;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;
import com.workshopmanagement.rdmotors.inventario.dominio.ConsultaInventario;
import com.workshopmanagement.rdmotors.inventario.dominio.ConteoCategoria;
import com.workshopmanagement.rdmotors.inventario.dominio.ResumenInventario;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/**
 * PUERTO — como el dominio pide y guarda variantes.
 *
 * <p>Esta interfaz vive <b>con el dominio</b>, no junto al adaptador de Postgres. Esa es la
 * inversion de dependencias: el centro escribe la pregunta y la infraestructura obedece. Si el
 * contrato viviera en el paquete de JPA, no se habria invertido nada — solo habria una interfaz.
 *
 * <p>Fijate en el vocabulario: {@code buscarParaModificar}, no {@code findByIdWithPessimisticLock}.
 * El dominio declara <b>la intencion</b>; el adaptador decide que eso se traduce en un
 * {@code SELECT ... FOR UPDATE}. Cada uno conserva su rol y la fila queda serializada igual.
 */
public interface RepositorioVariantes {

    Optional<Variante> buscar(UUID id);

    /**
     * Carga la variante con intencion de cambiar su stock.
     *
     * <p>El adaptador debe tomar bloqueo de fila. Sin eso, dos peticiones simultaneas leen el mismo
     * saldo y ambas venden la ultima unidad. Con un solo cajero suena improbable; el doble clic en
     * "cobrar" y el reintento de la cola offline lo vuelven cotidiano.
     */
    Optional<Variante> buscarParaModificar(UUID id);

    Optional<Variante> buscarPorCodigo(String codigo);

    /**
     * Las que tienen alguno de esos códigos, activas o no, con su concepto. De una vez: la carga de una factura
     * pregunta por 600 códigos, y de a uno serían 600 viajes a la base (spec 0012).
     *
     * @param codigos como los guarda la ficha: en mayúsculas y sin espacios al borde
     */
    List<Variante> buscarPorCodigos(Collection<String> codigos);

    /** Las marcas que ya tiene algún repuesto activo, sin repetir: con ellas se proponen las de una factura. */
    List<String> marcasEnUso();

    /**
     * Coincidencia parcial en nombre del concepto, marca del repuesto o texto de aplicación.
     *
     * <p>Es el mismo buscador que en la rebanada 2 usará el cajero con un cliente enfrente, así que
     * el adaptador debe traer el concepto en la misma consulta —con {@code join fetch}— en vez de
     * dejarlo perezoso: leer el nombre después, fuera de la transacción, revienta; y dentro de ella
     * dispara una consulta por fila.
     *
     * @param limite tope de resultados. Con miles de repuestos, una búsqueda de dos letras no puede
     *               devolverlo todo.
     */
    List<Variante> buscarPorTexto(String texto, int limite);

    /**
     * El inventario completo, por páginas: lo que ve el administrador sin haber tecleado nada.
     *
     * <p>Se distingue de {@link #buscarPorTexto} en dos cosas. Con texto vacío devuelve todo en vez
     * de nada, y el texto también encuentra por código parcial. El buscador de la compra no hace lo
     * primero a propósito: allí un campo vacío no debe traer el catálogo entero.
     *
     * <p>Mismo cuidado con el concepto y su categoría: vienen en la misma consulta.
     *
     * <p>Lo usa también el catálogo del mostrador (spec 0005), con filtro de categoría y los que tienen
     * stock primero.
     *
     * @param pagina empieza en 0
     */
    Pagina<Variante> listar(ConsultaInventario consulta, int pagina, int tamano);

    /**
     * Cuántos repuestos activos de lo buscado hay en cada categoría (spec 0005, RF-002), con el MISMO
     * texto que {@link #listar}: sumados dan el total del listado sin filtro de categoría. En el orden de
     * las categorías, los que no tienen categoría al final, y solo las que tienen algo.
     *
     * @param texto vacío = todo el inventario activo
     */
    List<ConteoCategoria> contarPorCategoria(String texto);

    /** Totales de todo el inventario activo. Ver {@link ResumenInventario} sobre el costo null. */
    ResumenInventario resumen();

    Variante guardar(Variante variante);
}
