package com.workshopmanagement.rdmotors.ventas.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.Motivo;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Una venta de mostrador cobrada (spec 0003).
 *
 * <p><b>Las partes suman el total, y se construye sumando:</b>
 * <pre>
 *   subtotal          = Σ total de cada renglón
 *   total             = subtotal − descuento
 *   Σ pagos + fiado   = total
 * </pre>
 * Nada de eso se recibe de la pantalla: se calcula aquí a partir de los renglones. La pantalla calcula
 * lo mismo para mostrarlo, y si no coincide lo que se rechaza es el cobro, no la cuenta.
 *
 * <p><b>No se edita.</b> Si quedó mal, se anula y se vende de nuevo (`SPEC_Modelo_Datos.md` §5): anular
 * y recrear deja el rastro gratis.
 *
 * <p><b>Lo fiado no es una forma de pago</b> (spec 0008, decisión 4): es lo que no se pagó, y queda como deuda del
 * cliente. Por eso va aparte de los pagos, y si hay fiado hay cliente.
 *
 * <p>No sabe mover stock. Lo mueve el caso de uso, con el repuesto bloqueado, antes de guardarla.
 */
@Entity
@Table(name = "venta")
@Getter
public class Venta {

    public static final String TIPO_AUDITORIA = "VENTA";

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** {@code Long} y no {@code long}: nulo es lo que le dice a Spring Data que la venta es nueva. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** El número del comprobante: corrido, único, asignado al cobrar y nunca reutilizado. */
    @Column(name = "numero", nullable = false, updatable = false)
    private long numero;

    @Column(name = "turno_id", nullable = false, updatable = false)
    private UUID turnoId;

    @Column(name = "vendido_por_id", nullable = false, updatable = false)
    private UUID vendidoPorId;

    @Column(name = "cobrada_en", nullable = false, updatable = false)
    private Instant cobradaEn;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "subtotal", nullable = false))
    private Dinero subtotal;

    /** $0 si no hubo descuento. */
    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "descuento_monto", nullable = false))
    private Dinero descuentoMonto;

    @Enumerated(EnumType.STRING)
    @Column(name = "descuento_modo", length = 10)
    private ModoDescuento descuentoModo;

    @Column(name = "descuento_porcentaje", precision = 5, scale = 2)
    private BigDecimal descuentoPorcentaje;

    @Column(name = "descuento_motivo", length = Motivo.LARGO_MAXIMO)
    private String descuentoMotivo;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "total", nullable = false))
    private Dinero total;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoVenta estado;

    /** A nombre de quién, si se dijo. Obligatorio si quedó algo fiado (spec 0008). */
    @Column(name = "cliente_id", updatable = false)
    private UUID clienteId;

    /** Lo que no se pagó y queda debiendo el cliente. $0 en una venta de contado. */
    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "fiado", nullable = false, updatable = false))
    private Dinero fiado;

    /**
     * La llave contra el doble cobro. Nace con la venta en el navegador: un doble clic o un reintento
     * tras un corte llegan con la misma, y se devuelve la venta que ya existe.
     */
    @Column(name = "llave_idempotencia", nullable = false, updatable = false)
    private UUID llaveIdempotencia;

    @Column(name = "anulada_en")
    private Instant anuladaEn;

    @Column(name = "anulada_por_id")
    private UUID anuladaPorId;

    /** El turno en que se anuló: lo que devuelve la caja cuenta ahí, no en el turno de la venta. */
    @Column(name = "anulada_en_turno_id")
    private UUID anuladaEnTurnoId;

    @Column(name = "motivo_anulacion", length = Motivo.LARGO_MAXIMO)
    private String motivoAnulacion;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("posicion")
    private List<LineaVenta> lineas = new ArrayList<>();

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PagoVenta> pagos = new ArrayList<>();

    protected Venta() {
        // JPA
    }

    /**
     * Cobra una venta.
     *
     * <p>Que los repuestos existan, estén activos, tengan precio y stock lo revisa el caso de uso con
     * cada repuesto bloqueado. Aquí vive lo que se puede decidir con los números: renglones, descuento
     * y pagos.
     *
     * @param descuento {@code null} si no hubo
     */
    public static Venta cobrar(long numero, UUID turnoId, List<LineaVenta> lineas, Descuento descuento,
                               List<PagoVenta> pagos, UUID vendidoPorId, UUID llave, Instant cuando) {
        return cobrar(numero, turnoId, lineas, descuento, pagos, null, Dinero.CERO, vendidoPorId, llave, cuando);
    }

    /**
     * Cobra una venta a nombre de un cliente, con una parte o todo fiado (spec 0008, RF-006). Que al cliente se le
     * pueda fiar lo revisa el caso de uso con el cliente bloqueado; aquí, que las cuentas cuadren.
     *
     * @param clienteId {@code null} si no se dijo a nombre de quién; obligatorio si hay fiado
     * @param fiado     lo que queda debiendo; {@code CERO} si pagó todo
     */
    public static Venta cobrar(long numero, UUID turnoId, List<LineaVenta> lineas, Descuento descuento,
                               List<PagoVenta> pagos, UUID clienteId, Dinero fiado, UUID vendidoPorId, UUID llave,
                               Instant cuando) {
        if (turnoId == null) {
            throw new ReglaDeNegocioException("Una venta pertenece a un turno abierto");
        }
        if (vendidoPorId == null || llave == null) {
            throw new ReglaDeNegocioException("A la venta le falta quién vende o su llave");
        }
        if (lineas == null || lineas.isEmpty()) {
            throw new ReglaDeNegocioException("La venta no tiene repuestos");
        }
        exigirRepuestosDistintos(lineas);

        Venta venta = new Venta();
        venta.id = UUID.randomUUID();
        venta.numero = numero;
        venta.turnoId = turnoId;
        venta.vendidoPorId = vendidoPorId;
        venta.llaveIdempotencia = llave;
        venta.cobradaEn = cuando;
        venta.estado = EstadoVenta.COBRADA;

        int posicion = 0;
        Dinero subtotal = Dinero.CERO;
        for (LineaVenta linea : lineas) {
            linea.asignarA(venta, posicion++);
            venta.lineas.add(linea);
            subtotal = subtotal.mas(linea.getTotal());
        }
        venta.subtotal = subtotal;

        if (descuento == null) {
            venta.descuentoMonto = Dinero.CERO;
        } else {
            if (descuento.monto().esMayorQue(subtotal)) {
                throw new ReglaDeNegocioException("El descuento (" + descuento.monto().enPesos()
                        + ") no puede ser mayor que el total de la venta (" + subtotal.enPesos() + ")");
            }
            venta.descuentoMonto = descuento.monto();
            venta.descuentoModo = descuento.modo();
            venta.descuentoPorcentaje = descuento.porcentaje();
            venta.descuentoMotivo = descuento.motivo();
        }
        venta.total = subtotal.menos(venta.descuentoMonto);

        Dinero loFiado = fiado == null ? Dinero.CERO : fiado;
        if (loFiado.esNegativo()) {
            throw new ReglaDeNegocioException("Lo fiado no puede ser negativo");
        }
        if (!loFiado.esCero() && clienteId == null) {
            throw new ReglaDeNegocioException("Para fiar hay que decir a quién");
        }
        venta.clienteId = clienteId;
        venta.fiado = loFiado;
        venta.agregarPagos(pagos == null ? List.of() : pagos);
        return venta;
    }

    private static void exigirRepuestosDistintos(List<LineaVenta> lineas) {
        Set<UUID> vistos = new HashSet<>();
        for (LineaVenta linea : lineas) {
            if (!vistos.add(linea.getVariante().getId())) {
                throw new ReglaDeNegocioException("El repuesto " + linea.getVariante().getCodigo()
                        + " aparece dos veces en la venta. Súmalo en un solo renglón.");
            }
        }
    }

    /** Un pago por forma como máximo, y entre todos, más lo fiado, cuadran al peso con el total. */
    private void agregarPagos(List<PagoVenta> nuevos) {
        Set<FormaPago> formas = new HashSet<>();
        Dinero pagado = Dinero.CERO;
        for (PagoVenta pago : nuevos) {
            if (!formas.add(pago.getForma())) {
                throw new ReglaDeNegocioException("Hay dos pagos en " + pago.getForma().name().toLowerCase()
                        + ". Súmalos en uno.");
            }
            pagado = pagado.mas(pago.getMonto());
        }
        Dinero cubierto = pagado.mas(fiado);
        if (!cubierto.equals(total)) {
            Dinero diferencia = total.menos(cubierto);
            String suman = fiado.esCero() ? "Los pagos suman " + pagado.enPesos()
                    : "Lo pagado (" + pagado.enPesos() + ") y lo fiado (" + fiado.enPesos() + ") suman "
                            + cubierto.enPesos();
            throw new ReglaDeNegocioException(diferencia.esNegativo()
                    ? suman + ": sobran " + cubierto.menos(total).enPesos() + " sobre el total de " + total.enPesos()
                    : suman + ": faltan " + diferencia.enPesos() + " para el total de " + total.enPesos());
        }
        for (PagoVenta pago : nuevos) {
            pago.asignarA(this);
            pagos.add(pago);
        }
    }

    // ── Anular ───────────────────────────────────────────────────────────────

    /**
     * Anula la venta (spec 0003, RF-025 a RF-027): queda con quién, cuándo, en qué turno y por qué. Su
     * número no se reutiliza: la venta sigue existiendo, marcada.
     *
     * <p>Devolver el stock lo hace el caso de uso antes de llamar aquí, con cada repuesto bloqueado. Una
     * venta nunca queda anulada con su mercancía afuera.
     *
     * @param turnoId el turno abierto HOY, que puede no ser el de la venta: lo que devuelve la caja cuenta
     *                en el turno en que se devuelve (RF-026)
     */
    public void anular(String motivo, UUID anuladaPorId, UUID turnoId, Instant cuando) {
        exigirCobrada();
        String motivoLimpio = Motivo.exigir(motivo);
        if (anuladaPorId == null) {
            throw new ReglaDeNegocioException("Falta el usuario que anula");
        }
        if (turnoId == null) {
            throw new ReglaDeNegocioException("Para anular una venta tiene que haber un turno abierto");
        }
        this.estado = EstadoVenta.ANULADA;
        this.motivoAnulacion = motivoLimpio;
        this.anuladaPorId = anuladaPorId;
        this.anuladaEnTurnoId = turnoId;
        this.anuladaEn = cuando;
    }

    /** Una anulada no se vuelve a anular (RF-027): su stock ya volvió una vez. */
    public void exigirCobrada() {
        if (estado == EstadoVenta.ANULADA) {
            throw new ReglaDeNegocioException("Esta venta ya fue anulada");
        }
    }

    public boolean estaAnulada() {
        return estado == EstadoVenta.ANULADA;
    }

    // ── Lectura ──────────────────────────────────────────────────────────────

    public List<LineaVenta> getLineas() {
        return Collections.unmodifiableList(lineas);
    }

    public List<PagoVenta> getPagos() {
        return Collections.unmodifiableList(pagos);
    }

    public boolean tieneDescuento() {
        return !descuentoMonto.esCero();
    }

    /** Si quedó algo debiendo (spec 0008). */
    public boolean tieneFiado() {
        return !fiado.esCero();
    }

    /** Lo que se le devolvió al cliente entre todos los pagos en efectivo. */
    public Dinero cambio() {
        return pagos.stream().map(PagoVenta::cambio).reduce(Dinero.CERO, Dinero::mas);
    }

    /** Para la auditoría de la anulación: la venta antes y después, con lo que cambia al anular. */
    public Map<String, Object> fotografia() {
        Map<String, Object> foto = new LinkedHashMap<>();
        foto.put("numero", numero);
        foto.put("estado", estado.name());
        foto.put("total", total.valor().longValueExact());
        foto.put("turnoId", turnoId);
        if (tieneFiado()) {
            foto.put("clienteId", clienteId);
            foto.put("fiado", fiado.valor().longValueExact());
        }
        foto.put("renglones", lineas.stream().map(l -> {
            Map<String, Object> renglon = new LinkedHashMap<>();
            renglon.put("codigo", l.getVariante().getCodigo());
            renglon.put("cantidad", l.getCantidad());
            renglon.put("total", l.getTotal().valor().longValueExact());
            return renglon;
        }).toList());
        if (estado == EstadoVenta.ANULADA) {
            foto.put("anuladaEnTurnoId", anuladaEnTurnoId);
            foto.put("motivoAnulacion", motivoAnulacion);
        }
        return foto;
    }

    /** Para la auditoría del descuento: cómo iba a quedar la venta sin él. */
    public Map<String, Object> fotografiaSinDescuento() {
        Map<String, Object> foto = new LinkedHashMap<>();
        foto.put("numero", numero);
        foto.put("subtotal", subtotal.valor().longValueExact());
        foto.put("total", subtotal.valor().longValueExact());
        return foto;
    }

    /** Para la auditoría del descuento: cuánto, cómo se capturó y cómo quedó el total. */
    public Map<String, Object> fotografiaConDescuento() {
        Map<String, Object> foto = new LinkedHashMap<>();
        foto.put("numero", numero);
        foto.put("subtotal", subtotal.valor().longValueExact());
        foto.put("descuento", descuentoMonto.valor().longValueExact());
        foto.put("modo", descuentoModo == null ? null : descuentoModo.name());
        foto.put("porcentaje", descuentoPorcentaje);
        foto.put("total", total.valor().longValueExact());
        return foto;
    }
}
