package com.workshopmanagement.rdmotors.compartido;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.workshopmanagement.rdmotors.caja.dominio.CategoriaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.FiltroGastos;
import com.workshopmanagement.rdmotors.caja.dominio.Gasto;
import com.workshopmanagement.rdmotors.caja.dominio.MovimientoRepetidoException;
import com.workshopmanagement.rdmotors.caja.dominio.Retiro;
import com.workshopmanagement.rdmotors.caja.dominio.TotalesGastos;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoYaAbiertoException;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioCategoriasGasto;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioGastos;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioRetiros;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.clientes.dominio.Abono;
import com.workshopmanagement.rdmotors.clientes.dominio.CarteraDelCliente;
import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.clientes.dominio.ClienteRepetidoException;
import com.workshopmanagement.rdmotors.clientes.dominio.Deuda;
import com.workshopmanagement.rdmotors.clientes.dominio.FiltroCartera;
import com.workshopmanagement.rdmotors.clientes.dominio.ResumenDeCliente;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.ConsultasDeCartera;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioAbonos;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioClientes;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioDeudas;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.DatosTienda;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.TextoDeBusqueda;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioDatosTienda;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;
import com.workshopmanagement.rdmotors.compras.dominio.EstadoCompra;
import com.workshopmanagement.rdmotors.compras.dominio.FiltroCompras;
import com.workshopmanagement.rdmotors.compras.dominio.LineaCompra;
import com.workshopmanagement.rdmotors.compras.dominio.Proveedor;
import com.workshopmanagement.rdmotors.compras.dominio.ResumenCompra;
import com.workshopmanagement.rdmotors.compras.dominio.TotalesCompras;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCompras;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCuentas;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioProveedores;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.MovimientoKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.Producto;
import com.workshopmanagement.rdmotors.inventario.dominio.ResumenInventario;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioCategorias;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioProductos;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;
import com.workshopmanagement.rdmotors.reportes.dominio.CarteraDelPeriodo;
import com.workshopmanagement.rdmotors.reportes.dominio.Control;
import com.workshopmanagement.rdmotors.reportes.dominio.GastoDelPeriodo;
import com.workshopmanagement.rdmotors.reportes.dominio.Periodo;
import com.workshopmanagement.rdmotors.reportes.dominio.RenglonVendido;
import com.workshopmanagement.rdmotors.reportes.dominio.VentaCobrada;
import com.workshopmanagement.rdmotors.correo.dominio.AjustesDeCorreo;
import com.workshopmanagement.rdmotors.correo.dominio.Correo;
import com.workshopmanagement.rdmotors.correo.dominio.CorreoArmado;
import com.workshopmanagement.rdmotors.correo.dominio.Destinatarios;
import com.workshopmanagement.rdmotors.correo.dominio.EnvioFallidoException;
import com.workshopmanagement.rdmotors.correo.dominio.puerto.EnviadorDeCorreos;
import com.workshopmanagement.rdmotors.correo.dominio.puerto.RepositorioAjustesDeCorreo;
import com.workshopmanagement.rdmotors.correo.dominio.puerto.RepositorioCorreos;
import com.workshopmanagement.rdmotors.reportes.dominio.puerto.RepositorioReportes;
import com.workshopmanagement.rdmotors.respaldo.dominio.Respaldo;
import com.workshopmanagement.rdmotors.respaldo.dominio.RespaldoFallidoException;
import com.workshopmanagement.rdmotors.respaldo.dominio.puerto.Archivos;
import com.workshopmanagement.rdmotors.respaldo.dominio.puerto.RepositorioRespaldos;
import com.workshopmanagement.rdmotors.respaldo.dominio.puerto.Volcador;
import com.workshopmanagement.rdmotors.usuarios.dominio.Entrada;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.Contrasenas;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioEntradas;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;
import com.workshopmanagement.rdmotors.ventas.dominio.VentaRepetidaException;
import com.workshopmanagement.rdmotors.ventas.dominio.puerto.RepositorioVentas;

/**
 * Implementaciones falsas de los puertos, en memoria.
 *
 * <p><b>Esto es lo que compra la arquitectura hexagonal.</b> Los casos de uso se prueban contra
 * estos falsos: sin Postgres, sin Spring, sin Docker, sin esquema. Milisegundos.
 *
 * <p>Y son <b>falsos, no mocks</b>. Un mock verifica <i>como</i> se llamo al repositorio y se
 * rompe cuando refactorizas algo que no cambia el comportamiento. Un falso se comporta como el
 * de verdad, asi que la prueba afirma <i>que</i> paso — que es lo unico que le importa al negocio.
 */
public final class Falsos {

    private Falsos() {
    }

    /** Un reloj que solo se mueve cuando la prueba lo dice: para lo que depende de cuánto tiempo pasó. */
    public static final class RelojManual implements Reloj {
        private Instant instante;

        public RelojManual(Instant desde) {
            this.instante = desde;
        }

        public void avanzar(java.time.Duration cuanto) {
            instante = instante.plus(cuanto);
        }

        @Override
        public Instant ahora() {
            return instante;
        }

        @Override
        public LocalDate hoy() {
            return LocalDate.ofInstant(instante, ZoneId.of("America/Bogota"));
        }
    }

    public static final class RelojFijo implements Reloj {
        private final Instant instante;

        public RelojFijo(String iso) {
            this.instante = Instant.parse(iso);
        }

        @Override
        public Instant ahora() {
            return instante;
        }

        @Override
        public LocalDate hoy() {
            return LocalDate.ofInstant(instante, ZoneId.of("America/Bogota"));
        }
    }

    public static final class VariantesEnMemoria implements RepositorioVariantes {
        private final Map<UUID, Variante> datos = new LinkedHashMap<>();
        public int vecesBuscadaParaModificar = 0;
        /** En qué orden se bloquearon: la venta tiene que bloquear siempre por id (spec 0003). */
        public final List<UUID> ordenDeBloqueo = new ArrayList<>();

        public Variante sembrar(Variante variante) {
            datos.put(variante.getId(), variante);
            return variante;
        }

        @Override
        public Optional<Variante> buscar(UUID id) {
            return Optional.ofNullable(datos.get(id));
        }

        @Override
        public Optional<Variante> buscarParaModificar(UUID id) {
            vecesBuscadaParaModificar++;
            ordenDeBloqueo.add(id);
            return Optional.ofNullable(datos.get(id));
        }

        @Override
        public Optional<Variante> buscarPorCodigo(String codigo) {
            return datos.values().stream().filter(v -> v.getCodigo().equals(codigo)).findFirst();
        }

        /** Imita al adaptador real: coincidencia parcial, sin mayúsculas ni tildes, acotada. */
        @Override
        public List<Variante> buscarPorTexto(String texto, int limite) {
            String aguja = texto;
            return datos.values().stream()
                    .filter(v -> contiene(v.getProducto().getNombre(), aguja)
                            || contiene(v.getMarcaRepuesto(), aguja)
                            || contiene(v.getProducto().getAplicacionOriginal(), aguja))
                    .limit(limite)
                    .toList();
        }

        private boolean contiene(String campo, String aguja) {
            return com.workshopmanagement.rdmotors.compartido.dominio.TextoDeBusqueda.contiene(campo, aguja);
        }

        /**
         * Imita al adaptador real: activas, mismo texto, filtro de categoría con la regla del dominio
         * ({@code FiltroCategoria.admite}), y el orden: agotados al final si se pide, luego nombre, marca y
         * código.
         */
        @Override
        public Pagina<Variante> listar(com.workshopmanagement.rdmotors.inventario.dominio.ConsultaInventario consulta,
                                       int pagina, int tamano) {
            List<Variante> todas = coincidenConTexto(consulta.texto())
                    .filter(v -> !consulta.soloStockBajo() || v.tieneStockBajo())
                    .filter(v -> consulta.categoria().admite(v.getProducto().getCategoria()))
                    .sorted(Comparator.comparing((Variante v) -> consulta.conStockPrimero() && v.getStock() <= 0)
                            .thenComparing(v -> v.getProducto().getNombre())
                            .thenComparing(Variante::getMarcaRepuesto)
                            .thenComparing(Variante::getCodigo))
                    .toList();
            List<Variante> trozo = todas.stream()
                    .skip((long) pagina * tamano)
                    .limit(tamano)
                    .toList();
            return new Pagina<>(trozo, todas.size(), pagina, tamano);
        }

        /** Imita al adaptador: por categoría en su orden, los sin categoría al final, solo las que tienen algo. */
        @Override
        public List<com.workshopmanagement.rdmotors.inventario.dominio.ConteoCategoria> contarPorCategoria(String texto) {
            Map<Optional<Categoria>, Long> porCategoria = coincidenConTexto(texto)
                    .collect(java.util.stream.Collectors.groupingBy(
                            v -> Optional.ofNullable(v.getProducto().getCategoria()), LinkedHashMap::new,
                            java.util.stream.Collectors.counting()));
            return porCategoria.entrySet().stream()
                    .sorted(Comparator.comparing(e -> e.getKey().map(Categoria::getOrden).orElse(Integer.MAX_VALUE)))
                    .map(e -> new com.workshopmanagement.rdmotors.inventario.dominio.ConteoCategoria(
                            e.getKey().map(Categoria::getId).orElse(null),
                            e.getKey().map(Categoria::getNombre).orElse(null),
                            e.getValue()))
                    .toList();
        }

        private java.util.stream.Stream<Variante> coincidenConTexto(String texto) {
            String aguja = texto == null ? "" : texto;
            return datos.values().stream()
                    .filter(Variante::isActiva)
                    .filter(v -> aguja.isEmpty()
                            || contiene(v.getCodigo(), aguja)
                            || contiene(v.getProducto().getNombre(), aguja)
                            || contiene(v.getMarcaRepuesto(), aguja)
                            || contiene(v.getProducto().getAplicacionOriginal(), aguja));
        }

        @Override
        public ResumenInventario resumen() {
            List<Variante> activas = datos.values().stream().filter(Variante::isActiva).toList();
            return new ResumenInventario(
                    activas.size(),
                    activas.stream().mapToLong(Variante::getStock).sum(),
                    activas.stream()
                            .filter(v -> v.getCostoPromedio() != null)
                            .map(v -> v.getCostoPromedio().multiply(BigDecimal.valueOf(v.getStock())))
                            .reduce(BigDecimal.ZERO, BigDecimal::add),
                    activas.stream().filter(v -> v.getStock() > 0 && v.getCostoPromedio() == null).count(),
                    activas.stream().filter(Variante::tieneStockBajo).count());
        }

        @Override
        public Variante guardar(Variante variante) {
            datos.put(variante.getId(), variante);
            return variante;
        }
    }

    public static final class ProductosEnMemoria implements RepositorioProductos {
        private final Map<UUID, Producto> datos = new LinkedHashMap<>();

        public Producto sembrar(Producto producto) {
            datos.put(producto.getId(), producto);
            return producto;
        }

        @Override
        public Optional<Producto> buscar(UUID id) {
            return Optional.ofNullable(datos.get(id));
        }

        @Override
        public List<Producto> buscarPorNombre(String texto) {
            return datos.values().stream()
                    .filter(p -> com.workshopmanagement.rdmotors.compartido.dominio.TextoDeBusqueda.contiene(p.getNombre(), texto))
                    .toList();
        }

        @Override
        public Producto guardar(Producto producto) {
            datos.put(producto.getId(), producto);
            return producto;
        }
    }

    public static final class CategoriasEnMemoria implements RepositorioCategorias {
        private final Map<UUID, Categoria> datos = new LinkedHashMap<>();

        public Categoria sembrar(Categoria categoria) {
            datos.put(categoria.getId(), categoria);
            return categoria;
        }

        @Override
        public Optional<Categoria> buscar(UUID id) {
            return Optional.ofNullable(datos.get(id));
        }

        @Override
        public List<Categoria> activas() {
            return datos.values().stream()
                    .filter(Categoria::isActiva)
                    .sorted(java.util.Comparator.comparingInt(Categoria::getOrden))
                    .toList();
        }

        @Override
        public Categoria guardar(Categoria categoria) {
            datos.put(categoria.getId(), categoria);
            return categoria;
        }
    }

    public static final class ComprasEnMemoria implements RepositorioCompras {
        private final Map<UUID, Compra> datos = new LinkedHashMap<>();

        @Override
        public Compra guardar(Compra compra) {
            datos.put(compra.getId(), compra);
            return compra;
        }

        @Override
        public Optional<Compra> buscar(UUID id) {
            return Optional.ofNullable(datos.get(id));
        }

        /** En memoria no hay relaciones perezosas: traer "con líneas" es traerla. */
        @Override
        public Optional<Compra> buscarConLineas(UUID id) {
            return buscar(id);
        }

        @Override
        public Optional<Compra> buscarPorLlave(UUID llave) {
            return llave == null ? Optional.empty()
                    : datos.values().stream().filter(c -> llave.equals(c.getLlaveIdempotencia())).findFirst();
        }

        public int vecesPedidasLineasVigentes = 0;

        @Override
        public List<Compra> deCajaEnTurno(UUID turnoId) {
            return datos.values().stream()
                    .filter(c -> c.isPagadaDeCaja() && turnoId.equals(c.getTurnoId()))
                    .toList();
        }

        @Override
        public List<LineaCompra> lineasVigentesDe(java.util.Collection<UUID> compraIds) {
            vecesPedidasLineasVigentes++;
            return compraIds.stream().map(datos::get).filter(java.util.Objects::nonNull)
                    .flatMap(c -> c.lineasVigentes().stream())
                    .toList();
        }

        public int vecesBuscadaParaModificar = 0;

        @Override
        public Optional<Compra> buscarParaModificar(UUID id) {
            vecesBuscadaParaModificar++;
            return buscar(id);
        }

        @Override
        public List<LineaCompra> lineasQueFijaronPrecio(UUID varianteId) {
            return datos.values().stream()
                    .filter(c -> c.getEstado() == EstadoCompra.VIGENTE)
                    .flatMap(c -> c.lineasVigentes().stream())
                    .filter(l -> l.getVariante().getId().equals(varianteId) && l.getPrecioVenta() != null)
                    .toList();
        }

        /** Mismos filtros que el historial; el total general y las partes, como el adaptador. */
        @Override
        public TotalesCompras totales(FiltroCompras f) {
            List<Compra> filtradas = filtrar(f).toList();
            Map<String, TotalesCompras.Parte> partes = new LinkedHashMap<>();
            for (Compra c : filtradas) {
                UUID cuentaId = c.getCuenta() == null ? null : c.getCuenta().getId();
                String clave = c.getFormaPago() + "|" + cuentaId;
                TotalesCompras.Parte previa = partes.get(clave);
                partes.put(clave, new TotalesCompras.Parte(c.getFormaPago(), cuentaId,
                        c.getCuenta() == null ? null : c.getCuenta().getNombre(),
                        (previa == null ? 0 : previa.compras()) + 1,
                        (previa == null ? com.workshopmanagement.rdmotors.compartido.dominio.Dinero.CERO
                                : previa.total()).mas(c.getTotal())));
            }
            var total = filtradas.stream().map(Compra::getTotal)
                    .reduce(com.workshopmanagement.rdmotors.compartido.dominio.Dinero.CERO,
                            com.workshopmanagement.rdmotors.compartido.dominio.Dinero::mas);
            return new TotalesCompras(total, filtradas.size(), List.copyOf(partes.values()));
        }

        private java.util.stream.Stream<Compra> filtrar(FiltroCompras f) {
            return datos.values().stream()
                    .filter(c -> f.proveedorId() == null || c.getProveedor().getId().equals(f.proveedorId()))
                    .filter(c -> f.desde() == null || !c.getFechaDocumento().isBefore(f.desde()))
                    .filter(c -> f.hasta() == null || !c.getFechaDocumento().isAfter(f.hasta()))
                    .filter(c -> f.formaPago() == null || c.getFormaPago() == f.formaPago())
                    .filter(c -> f.cuentaId() == null
                            || (c.getCuenta() != null && c.getCuenta().getId().equals(f.cuentaId())))
                    .filter(c -> f.factura() == null || (c.getNumeroFactura() != null
                            && c.getNumeroFactura().toLowerCase().contains(f.factura().toLowerCase())))
                    .filter(c -> f.estado() == null || c.getEstado() == f.estado())
                    // La regla del dominio, no una copia: el doble filtra con la misma que resalta.
                    .filter(c -> f.repuesto() == null || c.lineasVigentes().stream()
                            .anyMatch(l -> f.repuesto().coincideCon(l.getVariante())));
        }

        /** Imita al adaptador: mismos filtros, mismo orden (factura más reciente primero). */
        @Override
        public Pagina<ResumenCompra> historial(FiltroCompras f, int pagina, int tamano) {
            List<ResumenCompra> todas = filtrar(f)
                    .sorted(Comparator.comparing(Compra::getFechaDocumento)
                            .thenComparing(Compra::getFechaRegistro).reversed())
                    .map(c -> new ResumenCompra(c.getId(), c.getFechaDocumento(), c.getFechaRegistro(),
                            c.getProveedor().getNombre(), c.getNumeroFactura(), c.getFormaPago(),
                            c.getCuenta() == null ? null : c.getCuenta().getNombre(), c.isPagadaDeCaja(),
                            c.getEstado(), c.lineasVigentes().size(), c.getTotal()))
                    .toList();
            List<ResumenCompra> trozo = todas.stream().skip((long) pagina * tamano).limit(tamano).toList();
            return new Pagina<>(trozo, todas.size(), pagina, tamano);
        }
    }

    public static final class ProveedoresEnMemoria implements RepositorioProveedores {
        private final Map<UUID, Proveedor> datos = new LinkedHashMap<>();

        public Proveedor sembrar(Proveedor proveedor) {
            datos.put(proveedor.getId(), proveedor);
            return proveedor;
        }

        @Override
        public Optional<Proveedor> buscar(UUID id) {
            return Optional.ofNullable(datos.get(id));
        }

        @Override
        public List<Proveedor> activos() {
            return datos.values().stream().filter(Proveedor::isActivo).toList();
        }

        @Override
        public Proveedor guardar(Proveedor proveedor) {
            datos.put(proveedor.getId(), proveedor);
            return proveedor;
        }
    }

    /** Como la base recién migrada: la única fila, con "RD MOTORS". */
    public static final class TiendaEnMemoria implements RepositorioDatosTienda {
        public DatosTienda datos = DatosTienda.iniciales();
        public int vecesGuardada;

        @Override
        public DatosTienda actuales() {
            return datos;
        }

        @Override
        public DatosTienda guardar(DatosTienda nuevos) {
            vecesGuardada++;
            datos = nuevos;
            return nuevos;
        }
    }

    public static final class CuentasEnMemoria implements RepositorioCuentas {
        private final Map<UUID, CuentaPago> datos = new LinkedHashMap<>();

        public CuentaPago sembrar(CuentaPago cuenta) {
            datos.put(cuenta.getId(), cuenta);
            return cuenta;
        }

        @Override
        public Optional<CuentaPago> buscar(UUID id) {
            return Optional.ofNullable(datos.get(id));
        }

        /** Imita al índice único de la base: sin distinguir mayúsculas. */
        @Override
        public Optional<CuentaPago> buscarPorNombre(String nombre) {
            return datos.values().stream()
                    .filter(c -> c.getNombre().equalsIgnoreCase(nombre))
                    .findFirst();
        }

        @Override
        public List<CuentaPago> activas() {
            return datos.values().stream()
                    .filter(CuentaPago::isActiva)
                    .sorted(Comparator.comparing(CuentaPago::getNombre))
                    .toList();
        }

        @Override
        public CuentaPago guardar(CuentaPago cuenta) {
            datos.put(cuenta.getId(), cuenta);
            return cuenta;
        }
    }

    /**
     * El kardex en memoria. <b>Asigna la secuencia al agregar</b>, como la columna de identidad de la
     * base: sin eso, las reglas que preguntan "¿qué pasó antes o después de esta compra?" no se
     * podrían probar aquí.
     */
    public static final class KardexEnMemoria implements RepositorioKardex {
        public final List<MovimientoKardex> movimientos = new ArrayList<>();
        private long siguiente = 1;

        private static final Field SECUENCIA;
        static {
            try {
                SECUENCIA = MovimientoKardex.class.getDeclaredField("secuencia");
                SECUENCIA.setAccessible(true);
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void agregar(MovimientoKardex movimiento) {
            try {
                SECUENCIA.set(movimiento, siguiente++);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
            movimientos.add(movimiento);
        }

        @Override
        public void agregarTodos(List<MovimientoKardex> nuevos) {
            nuevos.forEach(this::agregar);
        }

        @Override
        public List<MovimientoKardex> historialDe(UUID varianteId) {
            return movimientos.stream()
                    .filter(m -> m.getVariante().getId().equals(varianteId))
                    .toList();
        }

        @Override
        public Optional<MovimientoKardex> buscar(UUID id) {
            return movimientos.stream().filter(m -> m.getId().equals(id)).findFirst();
        }

        /** Imita la consulta de la base, incluida la venta anulada que se canceló al peso (RF-030). */
        @Override
        public boolean huboSalidasDespuesDe(UUID varianteId, long secuencia) {
            return historialDe(varianteId).stream()
                    .anyMatch(m -> m.getSecuencia() > secuencia
                            && m.getCantidadDelta() < 0
                            && !m.getTipo().esReversionDeCompra()
                            && !revertidaAlMismoPromedio(m));
        }

        private boolean revertidaAlMismoPromedio(MovimientoKardex salida) {
            return movimientos.stream().anyMatch(r -> salida.getId().equals(r.getMovimientoRevertidoId())
                    && mismoPromedio(r.getCostoPromedioDespues(), salida.getCostoPromedioDespues()));
        }

        private static boolean mismoPromedio(BigDecimal a, BigDecimal b) {
            return a == null ? b == null : b != null && a.compareTo(b) == 0;
        }

        @Override
        public Optional<MovimientoKardex> ultimoAntesDe(UUID varianteId, long secuencia) {
            return historialDe(varianteId).stream()
                    .filter(m -> m.getSecuencia() < secuencia)
                    .reduce((primero, segundo) -> segundo);
        }
    }

    public static final class AuditoriaEnMemoria implements RepositorioAuditoria {
        public final List<EventoAuditoria> eventos = new ArrayList<>();

        @Override
        public void registrar(EventoAuditoria evento) {
            eventos.add(evento);
        }

        @Override
        public List<EventoAuditoria> historialDe(String entidadTipo, UUID entidadId) {
            return eventos.stream()
                    .filter(e -> e.entidadTipo().equals(entidadTipo) && e.entidadId().equals(entidadId))
                    .toList();
        }
    }

    /**
     * Imita al adaptador: <b>la regla de un solo turno abierto la hace cumplir el guardar</b>, igual
     * que el índice único de la base. Así una prueba que se salte la revisión del caso de uso sigue
     * chocando.
     */
    public static final class TurnosEnMemoria implements RepositorioTurnos {
        public final Map<UUID, TurnoCaja> datos = new LinkedHashMap<>();
        /** Cuántas veces se pidió el turno para mover plata: todo lo que mueve plata tiene que pedirlo así. */
        public int vecesBloqueadoParaMover = 0;
        public int vecesBloqueadoParaCerrar = 0;

        @Override
        public Optional<TurnoCaja> abierto() {
            return datos.values().stream().filter(TurnoCaja::estaAbierto).findFirst();
        }

        @Override
        public Optional<TurnoCaja> abiertoParaMover() {
            vecesBloqueadoParaMover++;
            return abierto();
        }

        @Override
        public Optional<TurnoCaja> buscarParaCerrar(UUID id) {
            vecesBloqueadoParaCerrar++;
            return buscar(id);
        }

        @Override
        public Optional<TurnoCaja> buscar(UUID id) {
            return Optional.ofNullable(datos.get(id));
        }

        /** Imita al adaptador: los cerrados, del cierre más reciente al más antiguo. */
        @Override
        public Pagina<TurnoCaja> cerrados(int pagina, int tamano) {
            List<TurnoCaja> cerrados = datos.values().stream()
                    .filter(t -> !t.estaAbierto())
                    .sorted(Comparator.comparing(TurnoCaja::getCerradoEn).reversed())
                    .toList();
            return new Pagina<>(cerrados.stream().skip((long) pagina * tamano).limit(tamano).toList(),
                    cerrados.size(), pagina, tamano);
        }

        @Override
        public Pagina<TurnoCaja> cerradosDe(UUID abiertoPorId, int pagina, int tamano) {
            List<TurnoCaja> suyos = cerrados(0, Integer.MAX_VALUE).elementos().stream()
                    .filter(t -> t.getAbiertoPorId().equals(abiertoPorId))
                    .toList();
            return new Pagina<>(suyos.stream().skip((long) pagina * tamano).limit(tamano).toList(),
                    suyos.size(), pagina, tamano);
        }

        @Override
        public TurnoCaja guardar(TurnoCaja turno) {
            boolean otroAbierto = datos.values().stream()
                    .anyMatch(t -> t.estaAbierto() && !t.getId().equals(turno.getId()));
            if (turno.estaAbierto() && otroAbierto) {
                throw new TurnoYaAbiertoException(null, null);
            }
            datos.put(turno.getId(), turno);
            return turno;
        }
    }

    /**
     * Imita al adaptador: el contador corre de uno en uno y la llave es única al guardar. Lo que no
     * puede imitar es que un cobro fallido devuelva su número: eso lo prueba la integración.
     */
    public static final class VentasEnMemoria implements RepositorioVentas {
        public final Map<UUID, Venta> datos = new LinkedHashMap<>();
        private long ultimoNumero = 0;

        @Override
        public long siguienteNumero() {
            return ++ultimoNumero;
        }

        @Override
        public Venta guardar(Venta venta) {
            boolean llaveUsada = datos.values().stream().anyMatch(v ->
                    v.getLlaveIdempotencia().equals(venta.getLlaveIdempotencia()) && !v.getId().equals(venta.getId()));
            if (llaveUsada) {
                throw new VentaRepetidaException(venta.getLlaveIdempotencia());
            }
            datos.put(venta.getId(), venta);
            return venta;
        }

        @Override
        public Optional<Venta> buscar(UUID id) {
            return Optional.ofNullable(datos.get(id));
        }

        @Override
        public Optional<Venta> buscarPorLlave(UUID llave) {
            return datos.values().stream().filter(v -> v.getLlaveIdempotencia().equals(llave)).findFirst();
        }

        @Override
        public Optional<Venta> buscarPorNumero(long numero) {
            return datos.values().stream().filter(v -> v.getNumero() == numero).findFirst();
        }

        @Override
        public Optional<Venta> buscarParaModificar(UUID id) {
            return buscar(id);
        }

        @Override
        public List<Venta> delTurno(UUID turnoId) {
            return datos.values().stream()
                    .filter(v -> v.getTurnoId().equals(turnoId))
                    .sorted(Comparator.comparingLong(Venta::getNumero).reversed())
                    .toList();
        }

        @Override
        public Pagina<Venta> delCliente(UUID clienteId, int pagina, int tamano) {
            List<Venta> suyas = datos.values().stream()
                    .filter(v -> clienteId.equals(v.getClienteId()))
                    .sorted(Comparator.comparingLong(Venta::getNumero).reversed())
                    .toList();
            return new Pagina<>(suyas.stream().skip((long) pagina * tamano).limit(tamano).toList(), suyas.size(),
                    pagina, tamano);
        }

        @Override
        public List<Venta> anuladasEnTurno(UUID turnoId) {
            return datos.values().stream()
                    .filter(v -> turnoId.equals(v.getAnuladaEnTurnoId()))
                    .sorted(Comparator.comparingLong(Venta::getNumero).reversed())
                    .toList();
        }
    }

    /** Imita al índice único de la base: sin mayúsculas ni tildes, también al guardar. */
    public static final class CategoriasGastoEnMemoria implements RepositorioCategoriasGasto {
        private final Map<UUID, CategoriaGasto> datos = new LinkedHashMap<>();

        public CategoriaGasto sembrar(CategoriaGasto categoria) {
            datos.put(categoria.getId(), categoria);
            return categoria;
        }

        @Override
        public Optional<CategoriaGasto> buscar(UUID id) {
            return Optional.ofNullable(datos.get(id));
        }

        @Override
        public Optional<CategoriaGasto> buscarPorNombre(String nombre) {
            String buscado = TextoDeBusqueda.normalizar(CategoriaGasto.normalizar(nombre));
            return datos.values().stream()
                    .filter(c -> TextoDeBusqueda.normalizar(c.getNombre()).equals(buscado))
                    .findFirst();
        }

        @Override
        public List<CategoriaGasto> todas() {
            return datos.values().stream().sorted(Comparator.comparing(CategoriaGasto::getNombre)).toList();
        }

        @Override
        public CategoriaGasto guardar(CategoriaGasto categoria) {
            String nombre = TextoDeBusqueda.normalizar(categoria.getNombre());
            boolean repetido = datos.values().stream().anyMatch(c -> !c.getId().equals(categoria.getId())
                    && TextoDeBusqueda.normalizar(c.getNombre()).equals(nombre));
            if (repetido) {
                throw new ReglaDeNegocioException("Ya existe la categoría «" + categoria.getNombre() + "»");
            }
            datos.put(categoria.getId(), categoria);
            return categoria;
        }
    }

    /** Imita al adaptador: la llave es única al guardar, y la lista y los totales filtran igual. */
    public static final class GastosEnMemoria implements RepositorioGastos {
        public final Map<UUID, Gasto> datos = new LinkedHashMap<>();
        public int vecesBuscadoParaModificar = 0;

        @Override
        public Gasto guardar(Gasto gasto) {
            boolean llaveUsada = datos.values().stream().anyMatch(g ->
                    g.getLlaveIdempotencia().equals(gasto.getLlaveIdempotencia()) && !g.getId().equals(gasto.getId()));
            if (llaveUsada) {
                throw new MovimientoRepetidoException(gasto.getLlaveIdempotencia());
            }
            datos.put(gasto.getId(), gasto);
            return gasto;
        }

        @Override
        public Optional<Gasto> buscar(UUID id) {
            return Optional.ofNullable(datos.get(id));
        }

        @Override
        public Optional<Gasto> buscarPorLlave(UUID llave) {
            return datos.values().stream().filter(g -> g.getLlaveIdempotencia().equals(llave)).findFirst();
        }

        @Override
        public Optional<Gasto> buscarParaModificar(UUID id) {
            vecesBuscadoParaModificar++;
            return buscar(id);
        }

        private java.util.stream.Stream<Gasto> filtrar(FiltroGastos f) {
            return datos.values().stream()
                    .filter(g -> f.desde() == null || !g.getFecha().isBefore(f.desde()))
                    .filter(g -> f.hasta() == null || !g.getFecha().isAfter(f.hasta()))
                    .filter(g -> f.categoriaId() == null || g.getCategoria().getId().equals(f.categoriaId()));
        }

        @Override
        public Pagina<Gasto> listar(FiltroGastos filtro, int pagina, int tamano) {
            List<Gasto> todos = filtrar(filtro)
                    .sorted(Comparator.comparing(Gasto::getFecha).thenComparing(Gasto::getRegistradoEn).reversed())
                    .toList();
            return new Pagina<>(todos.stream().skip((long) pagina * tamano).limit(tamano).toList(), todos.size(),
                    pagina, tamano);
        }

        @Override
        public TotalesGastos totales(FiltroGastos filtro) {
            List<Gasto> vigentes = filtrar(filtro).filter(g -> !g.estaAnulado()).toList();
            Dinero total = vigentes.stream().map(Gasto::getMonto).reduce(Dinero.CERO, Dinero::mas);
            Dinero delCajon = vigentes.stream().filter(Gasto::isDelCajon).map(Gasto::getMonto)
                    .reduce(Dinero.CERO, Dinero::mas);
            Dinero porFuera = vigentes.stream().filter(g -> !g.isDelCajon()).map(Gasto::getMonto)
                    .reduce(Dinero.CERO, Dinero::mas);
            return new TotalesGastos(total, vigentes.size(), delCajon, porFuera);
        }

        @Override
        public List<Gasto> delCajonEnTurno(UUID turnoId) {
            return datos.values().stream().filter(g -> g.isDelCajon() && turnoId.equals(g.getTurnoId())).toList();
        }
    }

    public static final class RetirosEnMemoria implements RepositorioRetiros {
        public final Map<UUID, Retiro> datos = new LinkedHashMap<>();

        @Override
        public Retiro guardar(Retiro retiro) {
            boolean llaveUsada = datos.values().stream().anyMatch(r ->
                    r.getLlaveIdempotencia().equals(retiro.getLlaveIdempotencia()) && !r.getId().equals(retiro.getId()));
            if (llaveUsada) {
                throw new MovimientoRepetidoException(retiro.getLlaveIdempotencia());
            }
            datos.put(retiro.getId(), retiro);
            return retiro;
        }

        @Override
        public Optional<Retiro> buscar(UUID id) {
            return Optional.ofNullable(datos.get(id));
        }

        @Override
        public Optional<Retiro> buscarPorLlave(UUID llave) {
            return datos.values().stream().filter(r -> r.getLlaveIdempotencia().equals(llave)).findFirst();
        }

        @Override
        public Optional<Retiro> buscarParaModificar(UUID id) {
            return buscar(id);
        }

        @Override
        public List<Retiro> delTurno(UUID turnoId) {
            return datos.values().stream().filter(r -> r.getTurnoId().equals(turnoId)).toList();
        }
    }

    /**
     * Lo que leen los reportes, sembrado a mano. Filtra como la base: las ventas por su día de Colombia dentro del
     * intervalo, los renglones de esas ventas, y los gastos por fecha o por mes.
     */
    public static final class ReportesEnMemoria implements RepositorioReportes {
        public final List<VentaCobrada> ventas = new ArrayList<>();
        public final List<RenglonVendido> renglones = new ArrayList<>();
        public final List<GastoDelPeriodo> gastos = new ArrayList<>();
        /** Cada intervalo que se pidió, para ver que ventas y renglones se leen con el mismo. */
        public final List<List<Instant>> intervalosPedidos = new ArrayList<>();

        private List<VentaCobrada> ventasEntre(Instant desde, Instant hasta) {
            intervalosPedidos.add(List.of(desde, hasta));
            return ventas.stream().filter(v -> {
                Instant inicioDelDia = v.dia().atStartOfDay(Periodo.ZONA).toInstant();
                return !inicioDelDia.isBefore(desde) && inicioDelDia.isBefore(hasta);
            }).toList();
        }

        @Override
        public List<VentaCobrada> ventasCobradas(Instant desde, Instant hasta) {
            return ventasEntre(desde, hasta);
        }

        @Override
        public List<RenglonVendido> renglonesVendidos(Instant desde, Instant hasta) {
            List<UUID> ids = ventasEntre(desde, hasta).stream().map(VentaCobrada::id).toList();
            return renglones.stream().filter(r -> ids.contains(r.ventaId())).toList();
        }

        @Override
        public List<GastoDelPeriodo> gastos(Periodo periodo) {
            return gastos.stream()
                    .filter(g -> g.delMes() ? periodo.tocaElMes(g.mes()) : periodo.contiene(g.fecha()))
                    .toList();
        }

        /** Las anuladas de cada período, por su primer instante. */
        public final Map<Instant, Control.VentasAnuladas> anuladas = new LinkedHashMap<>();
        public final List<Control.TurnoCerrado> turnos = new ArrayList<>();

        @Override
        public Control.VentasAnuladas ventasAnuladas(Instant desde, Instant hasta) {
            return anuladas.getOrDefault(desde, new Control.VentasAnuladas(0, Dinero.CERO));
        }

        @Override
        public List<Control.TurnoCerrado> turnosCerrados(Instant desde, Instant hasta) {
            return turnos.stream()
                    .filter(t -> !t.cerradoEn().isBefore(desde) && t.cerradoEn().isBefore(hasta))
                    .toList();
        }

        /** Lo cobrado en abonos del período y lo que deben hoy: se siembra a mano, como las demás filas. */
        public CarteraDelPeriodo carteraSembrada = CarteraDelPeriodo.VACIA;

        @Override
        public CarteraDelPeriodo cartera(Instant desde, Instant hasta) {
            return carteraSembrada;
        }
    }

    /**
     * El hash de las contraseñas, instantáneo: el de verdad tarda a propósito. Cada hash sale distinto para la misma
     * contraseña, como el de verdad.
     */
    public static final class ContrasenasFalsas implements Contrasenas {
        public int hashesCalculados = 0;
        public int comparaciones = 0;

        @Override
        public String hash(String contrasena) {
            hashesCalculados++;
            return "falso:" + contrasena + ":" + hashesCalculados;
        }

        @Override
        public boolean coincide(String contrasena, String hash) {
            comparaciones++;
            return hash != null && hash.startsWith("falso:" + contrasena + ":");
        }
    }

    /** Los usuarios, con la misma regla que la base: un usuario no se repite, sin mayúsculas ni tildes. */
    /** El registro de entradas: solo se agrega, y se lee de la más reciente a la más antigua. */
    public static final class EntradasEnMemoria implements RepositorioEntradas {
        public final List<Entrada> datos = new ArrayList<>();

        @Override
        public void registrar(Entrada entrada) {
            datos.add(entrada);
        }

        @Override
        public Pagina<Entrada> ultimas(int pagina, int tamano) {
            List<Entrada> ordenadas = datos.stream()
                    .sorted(Comparator.comparing(Entrada::getMomento).reversed())
                    .toList();
            return new Pagina<>(ordenadas.stream().skip((long) pagina * tamano).limit(tamano).toList(),
                    ordenadas.size(), pagina, tamano);
        }
    }

    /**
     * Los clientes, con la misma regla que la base: la cédula no se repite, sin puntos ni guiones. Busca como el
     * adaptador: nombre sin tildes, cédula y celular por sus caracteres.
     */
    public static final class ClientesEnMemoria implements RepositorioClientes {
        public final Map<UUID, Cliente> datos = new LinkedHashMap<>();
        public int vecesBloqueado = 0;

        public Cliente sembrar(Cliente cliente) {
            datos.put(cliente.getId(), cliente);
            return cliente;
        }

        @Override
        public Optional<Cliente> buscar(UUID id) {
            return Optional.ofNullable(datos.get(id));
        }

        @Override
        public Optional<Cliente> buscarParaModificar(UUID id) {
            vecesBloqueado++;
            return buscar(id);
        }

        @Override
        public Optional<Cliente> buscarPorDocumento(String documento) {
            String buscado = Cliente.normalizarDocumento(documento);
            return datos.values().stream()
                    .filter(c -> buscado != null && buscado.equals(c.getDocumentoNormalizado()))
                    .findFirst();
        }

        @Override
        public List<Cliente> buscarPorTexto(String texto, int limite) {
            String nombre = TextoDeBusqueda.normalizar(texto.trim());
            String documento = Cliente.normalizarDocumento(texto);
            String celular = Cliente.normalizarCelular(texto);
            return datos.values().stream()
                    .filter(c -> c.getNombreNormalizado().contains(nombre)
                            || (documento != null && c.getDocumentoNormalizado() != null
                                    && c.getDocumentoNormalizado().contains(documento))
                            || (celular != null && c.getCelularNormalizado() != null
                                    && c.getCelularNormalizado().contains(celular)))
                    .sorted(Comparator.comparing(Cliente::getNombreNormalizado))
                    .limit(limite)
                    .toList();
        }

        @Override
        public List<Cliente> deIds(java.util.Collection<UUID> ids) {
            return ids.stream().map(datos::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public Cliente guardar(Cliente cliente) {
            datos.values().stream()
                    .filter(c -> !c.getId().equals(cliente.getId()) && c.getDocumentoNormalizado() != null
                            && c.getDocumentoNormalizado().equals(cliente.getDocumentoNormalizado()))
                    .findFirst()
                    .ifPresent(otro -> {
                        throw new ClienteRepetidoException(otro.getId(), otro.getNombre());
                    });
            datos.put(cliente.getId(), cliente);
            return cliente;
        }
    }

    /** Las deudas, con la misma regla que la base: una sola por venta y un solo saldo del cuaderno por cliente. */
    public static final class DeudasEnMemoria implements RepositorioDeudas {
        public final Map<UUID, Deuda> datos = new LinkedHashMap<>();

        @Override
        public List<Deuda> delCliente(UUID clienteId) {
            return datos.values().stream().filter(d -> d.getClienteId().equals(clienteId))
                    .sorted(CarteraDelCliente.ORDEN_DE_PAGO).toList();
        }

        @Override
        public Optional<Deuda> deLaVenta(UUID ventaId) {
            return datos.values().stream().filter(d -> ventaId.equals(d.getVentaId())).findFirst();
        }

        @Override
        public List<Deuda> deLasVentas(java.util.Collection<UUID> ventaIds) {
            return datos.values().stream().filter(d -> d.getVentaId() != null && ventaIds.contains(d.getVentaId()))
                    .toList();
        }

        @Override
        public Map<UUID, Dinero> debeDe(java.util.Collection<UUID> clienteIds) {
            Map<UUID, Dinero> debe = new LinkedHashMap<>();
            for (Deuda d : datos.values()) {
                if (clienteIds.contains(d.getClienteId()) && d.tienePendiente()) {
                    debe.merge(d.getClienteId(), d.pendiente(), Dinero::mas);
                }
            }
            return debe;
        }

        @Override
        public Deuda guardar(Deuda deuda) {
            boolean repetida = datos.values().stream().anyMatch(d -> !d.getId().equals(deuda.getId())
                    && ((d.getVentaId() != null && d.getVentaId().equals(deuda.getVentaId()))
                            || (d.esDelCuaderno() && deuda.esDelCuaderno() && d.getClienteId().equals(deuda.getClienteId()))));
            if (repetida) {
                throw new IllegalStateException("La base no deja dos deudas de la misma venta ni dos saldos del cuaderno");
            }
            datos.put(deuda.getId(), deuda);
            return deuda;
        }
    }

    /**
     * La lista de la Cartera, con las reglas del dominio: arma la cartera de cada cliente y la resume
     * ({@link ResumenDeCliente#de}). Filtra como el adaptador: el historial, los que tuvieron alguna deuda; los que
     * deben, los que deben algo hoy; y el texto, como {@link ClientesEnMemoria#buscarPorTexto}.
     */
    public static final class CarteraEnMemoria implements ConsultasDeCartera {
        private final ClientesEnMemoria clientes;
        private final DeudasEnMemoria deudas;
        private final AbonosEnMemoria abonos;

        public CarteraEnMemoria(ClientesEnMemoria clientes, DeudasEnMemoria deudas, AbonosEnMemoria abonos) {
            this.clientes = clientes;
            this.deudas = deudas;
            this.abonos = abonos;
        }

        @Override
        public List<ResumenDeCliente> resumen(FiltroCartera filtro) {
            List<Cliente> candidatos = filtro.texto() == null ? List.copyOf(clientes.datos.values())
                    : clientes.buscarPorTexto(filtro.texto(), Integer.MAX_VALUE);
            return candidatos.stream()
                    .filter(c -> !deudas.delCliente(c.getId()).isEmpty())
                    .filter(c -> !filtro.tienePeriodo() || enElPeriodo(c.getId(), filtro))
                    .map(c -> ResumenDeCliente.de(new CarteraDelCliente(c, deudas.delCliente(c.getId()),
                            abonos.delCliente(c.getId()))))
                    .filter(r -> filtro.vista() == FiltroCartera.Vista.HISTORIAL || r.debeAlgo())
                    .toList();
        }

        /** Como el adaptador: tuvo una venta fiada, o un abono, dentro del período. */
        private boolean enElPeriodo(UUID clienteId, FiltroCartera filtro) {
            if (filtro.modoFecha() == FiltroCartera.ModoFecha.ABONO) {
                return abonos.delCliente(clienteId).stream().anyMatch(a -> !a.estaAnulado()
                        && !a.getRecibidoEn().atZone(ZoneId.of("America/Bogota")).toLocalDate().isBefore(filtro.desde())
                        && !a.getRecibidoEn().atZone(ZoneId.of("America/Bogota")).toLocalDate().isAfter(filtro.hasta()));
            }
            return deudas.delCliente(clienteId).stream().anyMatch(d -> !d.estaAnulada()
                    && !d.getFecha().isBefore(filtro.desde()) && !d.getFecha().isAfter(filtro.hasta()));
        }
    }

    /** Los abonos: la llave es única al guardar, como en la base, y el contador corre de uno en uno. */
    public static final class AbonosEnMemoria implements RepositorioAbonos {
        public final Map<UUID, Abono> datos = new LinkedHashMap<>();
        private long ultimoNumero = 0;

        @Override
        public long siguienteNumero() {
            return ++ultimoNumero;
        }

        @Override
        public List<Abono> delCliente(UUID clienteId) {
            return datos.values().stream().filter(a -> a.getClienteId().equals(clienteId)).toList();
        }

        @Override
        public Optional<Abono> buscar(UUID id) {
            return Optional.ofNullable(datos.get(id));
        }

        @Override
        public Optional<Abono> buscarPorLlave(UUID llave) {
            return datos.values().stream().filter(a -> a.getLlaveIdempotencia().equals(llave)).findFirst();
        }

        @Override
        public List<Abono> delTurno(UUID turnoId) {
            return datos.values().stream().filter(a -> turnoId.equals(a.getTurnoId())).toList();
        }

        @Override
        public Abono guardar(Abono abono) {
            boolean llaveUsada = datos.values().stream().anyMatch(a ->
                    a.getLlaveIdempotencia().equals(abono.getLlaveIdempotencia()) && !a.getId().equals(abono.getId()));
            if (llaveUsada) {
                throw new MovimientoRepetidoException(abono.getLlaveIdempotencia());
            }
            datos.put(abono.getId(), abono);
            return abono;
        }
    }

    public static final class UsuariosEnMemoria implements RepositorioUsuarios {
        public final Map<UUID, Usuario> datos = new LinkedHashMap<>();
        /** Los nombres de actores sembrados sin crear un Usuario completo. */
        private final Map<UUID, String> nombres = new LinkedHashMap<>();
        public int vecesBloqueadoElAlta = 0;
        /** Cuántas veces se pidieron nombres: una respuesta pregunta UNA vez, no una por fila (spec 0004, RF-022). */
        public int vecesPedidosLosNombres = 0;

        /** Un actor de prueba: {@code nombresDe} sabrá su nombre sin tener que crear su {@link Usuario}. */
        public Actor sembrar(Actor actor) {
            nombres.put(actor.id(), actor.nombre());
            return actor;
        }

        public Usuario sembrar(Usuario usuario) {
            datos.put(usuario.getId(), usuario);
            return usuario;
        }

        @Override
        public Optional<Usuario> buscar(UUID id) {
            return Optional.ofNullable(datos.get(id));
        }

        @Override
        public Optional<Usuario> buscarPorUsuario(String usuario) {
            String buscado = Usuario.normalizar(usuario);
            return datos.values().stream().filter(u -> u.getUsuarioNormalizado().equals(buscado)).findFirst();
        }

        @Override
        public Optional<Usuario> buscarPorUsuarioParaModificar(String usuario) {
            return buscarPorUsuario(usuario);
        }

        @Override
        public Optional<Usuario> buscarParaModificar(UUID id) {
            return buscar(id);
        }

        @Override
        public boolean hayUsuarios() {
            return !datos.isEmpty();
        }

        @Override
        public void bloquearAltaDelPrimero() {
            vecesBloqueadoElAlta++;
        }

        public int vecesBloqueadosLosAdministradores = 0;

        @Override
        public void bloquearCambiosDeAdministradores() {
            vecesBloqueadosLosAdministradores++;
        }

        @Override
        public long administradoresActivos() {
            return datos.values().stream().filter(Usuario::esAdministradorActivo).count();
        }

        @Override
        public List<Usuario> todos() {
            return datos.values().stream().sorted(Comparator.comparing(Usuario::getNombre)).toList();
        }

        @Override
        public Usuario guardar(Usuario usuario) {
            boolean repetido = datos.values().stream().anyMatch(u -> !u.getId().equals(usuario.getId())
                    && u.getUsuarioNormalizado().equals(usuario.getUsuarioNormalizado()));
            if (repetido) {
                throw new ReglaDeNegocioException("Ya existe el usuario «" + usuario.getUsuario() + "»");
            }
            datos.put(usuario.getId(), usuario);
            return usuario;
        }

        @Override
        public Map<UUID, String> nombresDe(java.util.Collection<UUID> ids) {
            vecesPedidosLosNombres++;
            Map<UUID, String> encontrados = new LinkedHashMap<>();
            for (UUID id : ids) {
                if (datos.containsKey(id)) {
                    encontrados.put(id, datos.get(id).getNombre());
                } else if (nombres.containsKey(id)) {
                    encontrados.put(id, nombres.get(id));
                }
            }
            return encontrados;
        }
    }
    // ── El respaldo (spec 0009) ──────────────────────────────────────────────

    /** El registro de respaldos, en memoria: de la más reciente a la más vieja, como el adaptador. */
    public static final class RespaldosEnMemoria implements RepositorioRespaldos {
        public final List<Respaldo> datos = new ArrayList<>();

        @Override
        public Respaldo guardar(Respaldo respaldo) {
            if (!datos.contains(respaldo)) {
                datos.add(respaldo);
            }
            return respaldo;
        }

        @Override
        public List<Respaldo> todos() {
            return datos.stream()
                    .sorted(Comparator.comparing(Respaldo::getHechoEn).reversed())
                    .toList();
        }

        public Respaldo sembrar(Respaldo respaldo) {
            datos.add(respaldo);
            return respaldo;
        }
    }

    /**
     * El volcado, sin Postgres: anota a dónde se le pidió copiar y "escribe" un archivo del tamaño que se le diga.
     * Con {@code falla}, revienta como reventaría {@code pg_dump} si no estuviera instalado.
     */
    public static final class VolcadorFalso implements Volcador {
        public final List<Path> volcados = new ArrayList<>();
        public long bytes = 4_096;
        public String falla;
        public ArchivosFalsos archivos;

        @Override
        public long volcar(Path destino) {
            volcados.add(destino);
            if (falla != null) {
                // Como pg_dump: puede haber dejado un archivo a medias antes de fallar.
                if (archivos != null) {
                    archivos.crear(destino);
                }
                throw new RespaldoFallidoException(falla);
            }
            if (archivos != null) {
                archivos.crear(destino);
            }
            return bytes;
        }
    }

    /** El disco, en un conjunto de rutas. */
    public static final class ArchivosFalsos implements Archivos {
        public final Set<Path> archivos = new LinkedHashSet<>();
        public final Set<Path> carpetas = new LinkedHashSet<>();
        public final List<Path> borrados = new ArrayList<>();
        /** La carpeta que no se deja crear: sin permiso, o el disco lleno. */
        public Path carpetaQueFalla;

        public void crear(Path archivo) {
            archivos.add(archivo);
        }

        @Override
        public void asegurarCarpeta(Path carpeta) {
            if (carpeta.equals(carpetaQueFalla)) {
                throw new RespaldoFallidoException("No se pudo crear la carpeta " + carpeta);
            }
            carpetas.add(carpeta);
        }

        @Override
        public void borrar(Path archivo) {
            borrados.add(archivo);
            archivos.remove(archivo);
        }

        /** Ya no es del puerto; se queda porque las pruebas preguntan si el archivo quedó o se borró. */
        public boolean existe(Path archivo) {
            return archivos.contains(archivo);
        }
    }

    // ── El correo del cierre (spec 0010) ─────────────────────────────────────

    /** La cola de correos, en memoria. `porMandar` imita al adaptador: los listos, del más viejo al más nuevo. */
    public static final class CorreosEnMemoria implements RepositorioCorreos {
        public final List<Correo> datos = new ArrayList<>();

        @Override
        public Correo guardar(Correo correo) {
            if (!datos.contains(correo)) {
                datos.add(correo);
            }
            return correo;
        }

        @Override
        public List<Correo> porMandar(Instant ahora, int cuantos) {
            return datos.stream()
                    .filter(c -> c.sePuedeIntentar(ahora))
                    .sorted(Comparator.comparing(Correo::getCreadoEn))
                    .limit(cuantos)
                    .toList();
        }

        @Override
        public List<Correo> ultimos(int cuantos) {
            return datos.stream().sorted(Comparator.comparing(Correo::getCreadoEn).reversed()).limit(cuantos).toList();
        }

        @Override
        public Optional<Correo> buscarParaModificar(UUID id) {
            return datos.stream().filter(c -> c.getId().equals(id)).findFirst();
        }
    }

    public static final class AjustesDeCorreoEnMemoria implements RepositorioAjustesDeCorreo {
        public AjustesDeCorreo ajustes = AjustesDeCorreo.iniciales();

        @Override
        public AjustesDeCorreo obtener() {
            return ajustes;
        }

        @Override
        public AjustesDeCorreo guardar(AjustesDeCorreo nuevos) {
            this.ajustes = nuevos;
            return nuevos;
        }

        /** Con destinatarios, como los dejaría el administrador. */
        public AjustesDeCorreoEnMemoria para(String... correos) {
            ajustes.cambiarDestinatarios(Destinatarios.de(List.of(correos)), UUID.randomUUID(), Instant.EPOCH);
            return this;
        }
    }

    /**
     * Brevo, sin internet: anota lo que se le pidió mandar. Con `falla`, revienta como Brevo: se arregla sola (sin
     * internet) o no (llave inválida).
     */
    public static final class EnviadorFalso implements EnviadorDeCorreos {
        public final List<CorreoArmado> enviados = new ArrayList<>();
        public final List<Destinatarios> a = new ArrayList<>();
        public boolean configurado = true;
        public EnvioFallidoException falla;

        @Override
        public boolean estaConfigurado() {
            return configurado;
        }

        @Override
        public String loQueFalta() {
            return configurado ? null : "Falta la llave de Brevo (BREVO_API_KEY)";
        }

        @Override
        public String remitente() {
            return "RD MOTORS <caja@rdmotors.co>";
        }

        @Override
        public String enviar(Destinatarios para, CorreoArmado correo) {
            if (falla != null) {
                throw falla;
            }
            enviados.add(correo);
            a.add(para);
            return "<brevo-" + enviados.size() + "@smtp-relay.mailin.fr>";
        }
    }

}
