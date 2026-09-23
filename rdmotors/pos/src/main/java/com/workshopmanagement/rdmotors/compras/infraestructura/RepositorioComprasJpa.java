package com.workshopmanagement.rdmotors.compras.infraestructura;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.EstadoCompra;
import com.workshopmanagement.rdmotors.compras.dominio.FiltroCompras;
import com.workshopmanagement.rdmotors.compras.dominio.LineaCompra;
import com.workshopmanagement.rdmotors.compras.dominio.ResumenCompra;
import com.workshopmanagement.rdmotors.compras.dominio.TotalesCompras;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compras.dominio.Proveedor;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCompras;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioProveedores;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;

/** ADAPTADOR — compras sobre Postgres. */
@Repository
@RequiredArgsConstructor
class RepositorioComprasJpa implements RepositorioCompras {

    private final ComprasSpringData jpa;

    @PersistenceContext
    private EntityManager em;

    @Override
    public Compra guardar(Compra compra) {
        return jpa.save(compra);
    }

    @Override
    public Optional<Compra> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Compra> buscarPorLlave(UUID llave) {
        return llave == null ? Optional.empty() : jpa.buscarPorLlave(llave);
    }

    @Override
    public Optional<Compra> buscarConLineas(UUID id) {
        return jpa.buscarConLineas(id);
    }

    @Override
    public List<Compra> deCajaEnTurno(UUID turnoId) {
        return jpa.deCajaEnTurno(turnoId);
    }

    @Override
    public List<LineaCompra> lineasVigentesDe(Collection<UUID> compraIds) {
        return compraIds.isEmpty() ? List.of() : jpa.lineasVigentesDe(compraIds);
    }

    @Override
    public Optional<Compra> buscarParaModificar(UUID id) {
        // La intención del dominio ("la voy a corregir") se traduce en SELECT ... FOR UPDATE: una
        // segunda corrección espera a la primera y después ve su versión.
        return jpa.bloquearPorId(id);
    }

    @Override
    public List<LineaCompra> lineasQueFijaronPrecio(UUID varianteId) {
        return jpa.lineasQueFijaronPrecio(varianteId, EstadoCompra.VIGENTE);
    }

    /**
     * La consulta se arma <b>solo con los filtros presentes</b>, en vez de escribir una sola con
     * {@code (:param is null or ...)}. Verificado contra Postgres 17 con Hibernate 7: esa forma corta
     * aguanta un id o una fecha nulos, pero <b>la búsqueda de factura revienta</b> — el texto nulo
     * viaja como {@code bytea} y la base responde {@code function lower(bytea) does not exist}.
     * Armarla por partes evita el caso y deja una sola forma para todos los filtros.
     *
     * <p>El orden termina en el id, que es único: sin desempate, dos facturas del mismo día y la
     * misma hora de registro pueden cambiar de página entre una petición y otra.
     */
    @Override
    public Pagina<ResumenCompra> historial(FiltroCompras filtro, int pagina, int tamano) {
        Condiciones condiciones = Condiciones.de(filtro);
        String donde = condiciones.donde();

        TypedQuery<ResumenCompra> consulta = em.createQuery("""
                select new com.workshopmanagement.rdmotors.compras.dominio.ResumenCompra(
                    c.id, c.fechaDocumento, c.fechaRegistro, p.nombre, c.numeroFactura,
                    c.formaPago, cu.nombre, c.pagadaDeCaja, c.estado,
                    (select count(l) from LineaCompra l where l.compra = c and l.vigente = true),
                    c.total.monto)
                from Compra c
                join c.proveedor p
                left join c.cuenta cu
                """ + donde + " order by c.fechaDocumento desc, c.fechaRegistro desc, c.id",
                ResumenCompra.class);
        TypedQuery<Long> conteo = em.createQuery("select count(c) from Compra c" + donde, Long.class);
        condiciones.aplicarA(consulta);
        condiciones.aplicarA(conteo);

        List<ResumenCompra> elementos = consulta
                .setFirstResult(pagina * tamano)
                .setMaxResults(tamano)
                .getResultList();
        return new Pagina<>(elementos, conteo.getSingleResult(), pagina, tamano);
    }

    /**
     * Dos consultas y no una: el total general se suma aparte de las partes. Si el total fuera la
     * suma de las partes cuadraría siempre, incluso con una compra que no cayera en ninguna; así,
     * que cuadren es algo que se comprueba (spec 0002, RF-010).
     */
    @Override
    public TotalesCompras totales(FiltroCompras filtro) {
        Condiciones condiciones = Condiciones.de(filtro);
        String donde = condiciones.donde();

        TypedQuery<Object[]> general = em.createQuery(
                "select coalesce(sum(c.total.monto), 0), count(c) from Compra c" + donde, Object[].class);
        TypedQuery<Object[]> porPago = em.createQuery("""
                select c.formaPago, cu.id, cu.nombre, count(c), sum(c.total.monto)
                from Compra c
                left join c.cuenta cu
                """ + donde + " group by c.formaPago, cu.id, cu.nombre order by c.formaPago, cu.nombre",
                Object[].class);
        condiciones.aplicarA(general);
        condiciones.aplicarA(porPago);

        Object[] fila = general.getSingleResult();
        List<TotalesCompras.Parte> partes = porPago.getResultList().stream()
                .map(p -> new TotalesCompras.Parte((FormaPago) p[0], (UUID) p[1], (String) p[2],
                        (Long) p[3], Dinero.de((java.math.BigDecimal) p[4])))
                .toList();
        return new TotalesCompras(Dinero.de(new java.math.BigDecimal(fila[0].toString())),
                (Long) fila[1], partes);
    }

    /**
     * Los filtros del historial y de los totales, armados <b>solo con lo que viene</b> y compartidos
     * para que las dos consultas filtren exactamente igual. Si cada una tuviera su copia, un filtro
     * arreglado en una y olvidado en la otra haría que los totales no fueran los de la lista.
     */
    private record Condiciones(List<String> clausulas, Map<String, Object> parametros) {

        static Condiciones de(FiltroCompras filtro) {
            List<String> condiciones = new ArrayList<>();
            Map<String, Object> parametros = new LinkedHashMap<>();

            if (filtro.proveedorId() != null) {
                condiciones.add("c.proveedor.id = :proveedorId");
                parametros.put("proveedorId", filtro.proveedorId());
            }
            if (filtro.desde() != null) {
                condiciones.add("c.fechaDocumento >= :desde");
                parametros.put("desde", filtro.desde());
            }
            if (filtro.hasta() != null) {
                condiciones.add("c.fechaDocumento <= :hasta");
                parametros.put("hasta", filtro.hasta());
            }
            if (filtro.formaPago() != null) {
                condiciones.add("c.formaPago = :formaPago");
                parametros.put("formaPago", filtro.formaPago());
            }
            if (filtro.cuentaId() != null) {
                condiciones.add("c.cuenta.id = :cuentaId");
                parametros.put("cuentaId", filtro.cuentaId());
            }
            if (filtro.factura() != null) {
                condiciones.add("lower(c.numeroFactura) like :factura");
                parametros.put("factura", "%" + filtro.factura().toLowerCase() + "%");
            }
            if (filtro.estado() != null) {
                condiciones.add("c.estado = :estado");
                parametros.put("estado", filtro.estado());
            }
            if (filtro.repuesto() != null) {
                // Imita BusquedaDeRepuesto.coincideCon: mismos cuatro campos, solo renglones vigentes.
                // "exists" y no un join: una factura con dos renglones que coinciden sale una vez, y
                // el conteo de páginas y los totales no la cuentan doble.
                condiciones.add("""
                        exists (select lr.id from LineaCompra lr join lr.variante vr join vr.producto pr
                                where lr.compra = c and lr.vigente = true
                                  and (sin_tildes(vr.codigo) like :repuesto escape '!'
                                    or sin_tildes(pr.nombre) like :repuesto escape '!'
                                    or sin_tildes(vr.marcaRepuesto) like :repuesto escape '!'
                                    or sin_tildes(pr.aplicacionOriginal) like :repuesto escape '!'))""");
                parametros.put("repuesto", "%" + comoTextoLiteral(filtro.repuesto().normalizado()) + "%");
            }
            return new Condiciones(condiciones, parametros);
        }

        /**
         * Lo que escribió el usuario se busca tal cual: sin esto, un {@code %} traería todas las
         * compras y un {@code _} valdría por cualquier letra. Se escapa con {@code !} y no con barra
         * invertida, que cambia de sentido entre Java, JPQL y SQL.
         */
        static String comoTextoLiteral(String texto) {
            return texto.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        }

        String donde() {
            return clausulas.isEmpty() ? "" : " where " + String.join(" and ", clausulas);
        }

        void aplicarA(TypedQuery<?> consulta) {
            parametros.forEach(consulta::setParameter);
        }
    }
}

/** ADAPTADOR — proveedores sobre Postgres. */
@Repository
@RequiredArgsConstructor
class RepositorioProveedoresJpa implements RepositorioProveedores {

    private final ProveedoresSpringData jpa;

    @Override
    public Optional<Proveedor> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public List<Proveedor> activos() {
        return jpa.findByActivoTrueOrderByNombreAsc();
    }

    @Override
    public Proveedor guardar(Proveedor proveedor) {
        return jpa.save(proveedor);
    }
}

interface ComprasSpringData extends JpaRepository<Compra, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Compra c where c.id = :id")
    Optional<Compra> bloquearPorId(@Param("id") UUID id);

    /** La compra registrada con esa llave (spec 0009, RF-009): el índice único la hace única. */
    @Query("select c from Compra c where c.llaveIdempotencia = :llave")
    Optional<Compra> buscarPorLlave(@Param("llave") UUID llave);

    @Query("""
            select l from LineaCompra l
            join l.compra c
            where l.variante.id = :varianteId
              and l.vigente = true
              and l.precioVenta.monto is not null
              and c.estado = :vigente
            """)
    List<LineaCompra> lineasQueFijaronPrecio(@Param("varianteId") UUID varianteId,
                                             @Param("vigente") EstadoCompra vigente);

    @Query("""
            select c from Compra c
            join fetch c.proveedor
            where c.pagadaDeCaja = true and c.turnoId = :turnoId
            order by c.fechaRegistro, c.id
            """)
    List<Compra> deCajaEnTurno(@Param("turnoId") UUID turnoId);

    /** Los renglones vigentes de varias compras, con repuesto y concepto: una consulta por página. */
    @Query("""
            select l from LineaCompra l
            join fetch l.compra c
            join fetch l.variante v
            join fetch v.producto
            where c.id in :compraIds
              and l.vigente = true
            order by c.id, l.posicion
            """)
    List<LineaCompra> lineasVigentesDe(@Param("compraIds") Collection<UUID> compraIds);

    /**
     * Todo lo que el detalle pinta, en una consulta. El {@code left join fetch} de la categoría no
     * sobra: el detalle trae la ficha actual de cada repuesto, y la categoría es parte de ella.
     */
    @Query("""
            select c from Compra c
            join fetch c.proveedor
            left join fetch c.cuenta
            left join fetch c.lineas l
            left join fetch l.variante v
            left join fetch v.producto pr
            left join fetch pr.categoria
            where c.id = :id
            """)
    Optional<Compra> buscarConLineas(@Param("id") UUID id);
}

interface ProveedoresSpringData extends JpaRepository<Proveedor, UUID> {
    List<Proveedor> findByActivoTrueOrderByNombreAsc();
}
