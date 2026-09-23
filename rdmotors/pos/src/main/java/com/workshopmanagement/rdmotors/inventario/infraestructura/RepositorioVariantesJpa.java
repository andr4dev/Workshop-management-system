package com.workshopmanagement.rdmotors.inventario.infraestructura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;
import com.workshopmanagement.rdmotors.compartido.dominio.TextoDeBusqueda;
import com.workshopmanagement.rdmotors.inventario.dominio.ConsultaInventario;
import com.workshopmanagement.rdmotors.inventario.dominio.ConteoCategoria;
import com.workshopmanagement.rdmotors.inventario.dominio.FiltroCategoria;
import com.workshopmanagement.rdmotors.inventario.dominio.ResumenInventario;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;

import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR — responde el puerto {@code RepositorioVariantes} usando Postgres.
 *
 * <p>Aqui es donde la intencion que declaro el dominio ("lo voy a modificar") se traduce a la
 * tecnica concreta: {@code SELECT ... FOR UPDATE}. El dominio nunca supo que existia esa sintaxis,
 * y el adaptador nunca supo por que hacia falta bloquear.
 */
@Repository
@RequiredArgsConstructor
class RepositorioVariantesJpa implements RepositorioVariantes {

    private final VariantesSpringData jpa;

    @Override
    public Optional<Variante> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Variante> buscarParaModificar(UUID id) {
        // La traduccion: intencion del dominio -> bloqueo pesimista de fila.
        // Sin esto, dos peticiones simultaneas leen el mismo saldo y una pisa a la otra.
        return jpa.bloquearPorId(id);
    }

    @Override
    public Optional<Variante> buscarPorCodigo(String codigo) {
        return jpa.findByCodigo(codigo);
    }

    @Override
    public List<Variante> buscarPorTexto(String texto, int limite) {
        // Normalizado aquí y comparado con sin_tildes() en la consulta: la misma tabla de letras.
        return jpa.buscarPorTexto(TextoDeBusqueda.normalizar(texto), PageRequest.of(0, limite));
    }

    /** Cuando el filtro no es de UNA categoría, el id no importa; pero Postgres no acepta un UUID nulo sin tipo. */
    private static final UUID SIN_ID = new UUID(0, 0);

    @Override
    public Pagina<Variante> listar(ConsultaInventario consulta, int pagina, int tamano) {
        FiltroCategoria categoria = consulta.categoria();
        Page<Variante> p = jpa.listar(TextoDeBusqueda.normalizar(consulta.texto()), consulta.soloStockBajo(),
                categoria.modo().name(), categoria.categoriaId() == null ? SIN_ID : categoria.categoriaId(),
                consulta.conStockPrimero(), PageRequest.of(pagina, tamano));
        return new Pagina<>(p.getContent(), p.getTotalElements(), pagina, tamano);
    }

    @Override
    public List<ConteoCategoria> contarPorCategoria(String texto) {
        return jpa.contarPorCategoria(TextoDeBusqueda.normalizar(texto == null ? "" : texto));
    }

    @Override
    public ResumenInventario resumen() {
        return jpa.resumen();
    }

    @Override
    public Variante guardar(Variante variante) {
        return jpa.save(variante);
    }
}

/** Detalle de Spring Data. Package-private: nadie fuera de este adaptador lo puede tocar. */
interface VariantesSpringData extends JpaRepository<Variante, UUID> {

    /**
     * El texto del inventario, escrito UNA vez: lo usan el listado, su conteo de páginas y el conteo por
     * categoría. Si cada consulta tuviera su copia, los números de las categorías del catálogo podrían no
     * sumar lo que muestra la lista. {@code :texto} llega normalizado; vacío = sin filtro.
     */
    String CONDICION_TEXTO = """
              (:texto = ''
                or sin_tildes(v.codigo) like concat('%', :texto, '%')
                or sin_tildes(p.nombre) like concat('%', :texto, '%')
                or sin_tildes(v.marcaRepuesto) like concat('%', :texto, '%')
                or sin_tildes(p.aplicacionOriginal) like concat('%', :texto, '%'))
            """;

    /**
     * La categoría con su modo (spec 0005). Con {@code left join}: con un {@code join} a secas los que no
     * tienen categoría desaparecerían de TODAS y de SIN.
     */
    String CONDICION_CATEGORIA = """
              (:modoCategoria = 'TODAS'
                or (:modoCategoria = 'SIN' and c.id is null)
                or (:modoCategoria = 'UNA' and c.id = :categoriaId))
            """;

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Variante v where v.id = :id")
    Optional<Variante> bloquearPorId(@Param("id") UUID id);

    Optional<Variante> findByCodigo(String codigo);

    /**
     * El {@code join fetch} no es opcional: sin el, leer el nombre del concepto por cada fila
     * dispara una consulta extra — cincuenta resultados, cincuenta viajes a la base. Es el mismo
     * buscador que en la rebanada 2 usara el cajero con un cliente enfrente.
     *
     * <p>Nota de rendimiento declarada en el plan: {@code like '%texto%'} NO usa indice. Con unos
     * miles de repuestos es irrelevante; si se queda corto, la salida es {@code pg_trgm}.
     *
     * <p>{@code texto} llega ya normalizado ({@code TextoDeBusqueda}): sin mayúsculas ni tildes, igual
     * que lo que devuelve {@code sin_tildes}.
     */
    @Query("""
            select v from Variante v
            join fetch v.producto p
            where v.activa
              and (sin_tildes(p.nombre) like concat('%', :texto, '%')
                or sin_tildes(v.marcaRepuesto) like concat('%', :texto, '%')
                or sin_tildes(p.aplicacionOriginal) like concat('%', :texto, '%'))
            order by p.nombre, v.marcaRepuesto
            """)
    List<Variante> buscarPorTexto(@Param("texto") String texto, Pageable pagina);

    /**
     * El inventario por páginas. <b>La consulta de conteo va escrita a mano:</b> contar no necesita
     * traer el concepto ni la categoría, y dejar que Spring Data la derive de una con
     * {@code join fetch} depende de que sepa quitarle el fetch.
     *
     * <p>El {@code order by} termina en el código, que es único. Sin un desempate estable, dos
     * repuestos con el mismo nombre y marca pueden cambiar de página entre una petición y la
     * siguiente — y uno aparece dos veces mientras otro no aparece nunca.
     */
    @Query(value = """
            select v from Variante v
            join fetch v.producto p
            left join fetch p.categoria c
            where v.activa
              and (:soloStockBajo = false or v.stock <= v.stockMinimo)
              and """ + CONDICION_TEXTO + " and " + CONDICION_CATEGORIA + """
            order by case when :conStockPrimero = true and v.stock <= 0 then 1 else 0 end,
                     p.nombre, v.marcaRepuesto, v.codigo
            """,
            countQuery = """
            select count(v) from Variante v
            join v.producto p
            left join p.categoria c
            where v.activa
              and (:soloStockBajo = false or v.stock <= v.stockMinimo)
              and """ + CONDICION_TEXTO + " and " + CONDICION_CATEGORIA)
    Page<Variante> listar(@Param("texto") String texto,
                          @Param("soloStockBajo") boolean soloStockBajo,
                          @Param("modoCategoria") String modoCategoria,
                          @Param("categoriaId") UUID categoriaId,
                          @Param("conStockPrimero") boolean conStockPrimero,
                          Pageable pagina);

    /**
     * Cuántos de lo buscado en cada categoría (spec 0005, RF-002). Mismo texto que {@link #listar}. El
     * grupo sin categoría ({@code c.id} nulo) va al final.
     */
    @Query("""
            select new com.workshopmanagement.rdmotors.inventario.dominio.ConteoCategoria(c.id, c.nombre, count(v))
            from Variante v
            join v.producto p
            left join p.categoria c
            where v.activa
              and """ + CONDICION_TEXTO + """
            group by c.id, c.nombre, c.orden
            order by c.orden nulls last
            """)
    List<ConteoCategoria> contarPorCategoria(@Param("texto") String texto);

    /**
     * Una sola consulta, sumada en la base. Traer las variantes para sumarlas en Java funcionaría
     * con cien repuestos y se arrastraría con cinco mil.
     *
     * <p>{@code sum(costoPromedio * stock)} ignora solo las filas con costo null: así se comporta
     * {@code sum} en SQL. Es justo lo que se quiere —no se inventa un cero—, y
     * {@code sinCosto} cuenta cuántas se quedaron fuera para que la pantalla lo diga.
     *
     * <p>Los {@code coalesce} no sobran: sobre una tabla vacía {@code sum} devuelve null, no cero,
     * y el primer arranque de la tienda es justo una tabla vacía.
     */
    @Query("""
            select new com.workshopmanagement.rdmotors.inventario.dominio.ResumenInventario(
                count(v),
                coalesce(sum(v.stock), 0L),
                coalesce(sum(v.costoPromedio * v.stock), 0),
                coalesce(sum(case when v.stock > 0 and v.costoPromedio is null then 1L else 0L end), 0L),
                coalesce(sum(case when v.stock <= v.stockMinimo then 1L else 0L end), 0L))
            from Variante v
            where v.activa
            """)
    ResumenInventario resumen();
}
