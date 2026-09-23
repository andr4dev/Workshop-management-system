package com.workshopmanagement.rdmotors.inventario.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Kardex: el registro <b>APPEND-ONLY</b> de cada movimiento de inventario.
 *
 * <p><b>Nunca se edita y nunca se borra.</b> Corregir es agregar un movimiento nuevo, jamas
 * modificar uno viejo. Eso es lo que convierte al kardex en la auditoria de inventario — y por eso
 * no hace falta otra tabla para auditar stock.
 *
 * <p>Cada fila guarda el <b>saldo despues</b> y el <b>promedio despues</b>, no solo el delta. Asi
 * cualquier fila se puede leer sola, sin recalcular la historia entera, y un error de hace seis
 * meses se ve en el punto exacto donde ocurrio.
 *
 * <p>Guarda las <b>dos</b> cifras de costo a proposito: {@code costoTotal} en pesos enteros (el
 * hecho, lo que se pago) y {@code costoUnitario} con 4 decimales (derivado). Ver
 * {@link Dinero#dividirEntre(int)} para por que reconstruir uno desde el otro pierde plata.
 */
@Entity
@Table(name = "movimiento_kardex", indexes = {
        @Index(name = "idx_kardex_variante", columnList = "variante_id, creado_en"),
        @Index(name = "idx_kardex_origen", columnList = "origen_tipo, origen_id")
})
@Getter
public class MovimientoKardex {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * El orden del kardex, <b>sin empates</b>. Lo asigna la base al insertar.
     *
     * <p>No alcanza con {@code creadoEn}: al corregir un renglón, la salida que lo revierte y la
     * entrada corregida del mismo repuesto nacen en el mismo instante. Ordenadas por fecha, una
     * segunda corrección no sabría cuál fue primero y tomaría el promedio equivocado.
     *
     * <p>Es {@code null} en un movimiento recién creado hasta que se relee de la base; los que se
     * revierten siempre vienen de una transacción anterior.
     */
    @Column(name = "secuencia", insertable = false, updatable = false)
    private Long secuencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", nullable = false)
    private Variante variante;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoMovimiento tipo;

    /** Con signo: positivo entra, negativo sale. */
    @Column(name = "cantidad_delta", nullable = false)
    private int cantidadDelta;

    @Column(name = "costo_unitario", precision = 14, scale = 4)
    private BigDecimal costoUnitario;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "costo_total"))
    private Dinero costoTotal;

    @Column(name = "saldo_despues", nullable = false)
    private int saldoDespues;

    @Column(name = "costo_promedio_despues", precision = 14, scale = 4)
    private BigDecimal costoPromedioDespues;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen_tipo", nullable = false, length = 20)
    private OrigenMovimiento origenTipo;

    @Column(name = "origen_id")
    private UUID origenId;

    @Column(name = "movimiento_revertido_id")
    private UUID movimientoRevertidoId;

    @Column(name = "motivo", length = 300)
    private String motivo;

    @Column(name = "registrado_por_id", nullable = false)
    private UUID registradoPorId;

    @Column(name = "creado_en", updatable = false, nullable = false)
    private Instant creadoEn;

    protected MovimientoKardex() {
        // JPA
    }

    private MovimientoKardex(Variante variante, TipoMovimiento tipo, int cantidadDelta,
                             BigDecimal costoUnitario, Dinero costoTotal,
                             OrigenMovimiento origenTipo, UUID origenId,
                             UUID movimientoRevertidoId, String motivo,
                             UUID registradoPorId, Instant creadoEn) {
        this.id = UUID.randomUUID();
        this.variante = variante;
        this.tipo = tipo;
        this.cantidadDelta = cantidadDelta;
        this.costoUnitario = costoUnitario;
        this.costoTotal = costoTotal;
        this.origenTipo = origenTipo;
        this.origenId = origenId;
        this.movimientoRevertidoId = movimientoRevertidoId;
        this.motivo = motivo;
        this.registradoPorId = registradoPorId;
        this.creadoEn = creadoEn;
        // Snapshot: se toma DESPUES de que la variante ya se movio.
        this.saldoDespues = variante.getStock();
        this.costoPromedioDespues = variante.getCostoPromedio();
    }

    /**
     * Movimiento de entrada por compra.
     *
     * <p><b>Se construye DESPUES de llamar a {@link Variante#reponerPorCompra}</b>, porque toma la
     * foto del saldo y del promedio ya actualizados. El orden importa: al reves guardaria el estado
     * anterior y el kardex mentiria.
     */
    public static MovimientoKardex porCompra(Variante variante, int cantidad,
                                             BigDecimal costoUnitario, Dinero costoTotal,
                                             UUID compraId, UUID registradoPorId, Instant cuando) {
        return porCompra(variante, cantidad, costoUnitario, costoTotal, compraId, null,
                registradoPorId, cuando);
    }

    /**
     * Entrada por compra con motivo: la de un renglón corregido. Es una COMPRA normal —entra y
     * recalcula el promedio— y lleva el motivo de la corrección para que el kardex explique por qué
     * la misma factura aparece dos veces.
     */
    public static MovimientoKardex porCompra(Variante variante, int cantidad,
                                             BigDecimal costoUnitario, Dinero costoTotal,
                                             UUID compraId, String motivo,
                                             UUID registradoPorId, Instant cuando) {
        return new MovimientoKardex(variante, TipoMovimiento.COMPRA, cantidad,
                costoUnitario, costoTotal, OrigenMovimiento.COMPRA, compraId, null, motivo,
                registradoPorId, cuando);
    }

    /**
     * Salida que revierte la entrada de un renglón de compra (spec 0002).
     *
     * <p><b>Se construye DESPUÉS de {@link Variante#revertirEntradaDeCompra}</b>, igual que la
     * entrada: toma la foto del saldo y del promedio ya revertidos.
     *
     * @param tipo {@link TipoMovimiento#CORRECCION_COMPRA} o {@link TipoMovimiento#ANULACION_COMPRA}
     * @param revertido el movimiento de entrada que se deshace
     */
    public static MovimientoKardex porReversionDeCompra(Variante variante, TipoMovimiento tipo,
                                                        int cantidad, BigDecimal costoUnitario,
                                                        Dinero costoTotal, UUID compraId,
                                                        UUID revertido, String motivo,
                                                        UUID registradoPorId, Instant cuando) {
        if (tipo == null || !tipo.esReversionDeCompra()) {
            throw new IllegalArgumentException("Una reversión de compra es CORRECCION_COMPRA o ANULACION_COMPRA");
        }
        return new MovimientoKardex(variante, tipo, -cantidad, costoUnitario, costoTotal,
                OrigenMovimiento.COMPRA, compraId, revertido, motivo, registradoPorId, cuando);
    }

    /**
     * Movimiento de salida por venta. El costo que se registra es el <b>promedio vigente</b>, no el
     * precio: el kardex mide costo, no ingreso.
     */
    public static MovimientoKardex porVenta(Variante variante, int cantidad,
                                            UUID ventaId, UUID registradoPorId, Instant cuando) {
        BigDecimal promedio = variante.getCostoPromedio();
        Dinero total = promedio == null ? null : Dinero.desdeUnitario(promedio, cantidad);
        return new MovimientoKardex(variante, TipoMovimiento.VENTA, -cantidad,
                promedio, total, OrigenMovimiento.VENTA, ventaId, null, null, registradoPorId, cuando);
    }

    /**
     * Entrada que deshace la salida de una venta anulada (spec 0003, RF-025).
     *
     * <p><b>Se construye DESPUÉS de {@link Variante#reponerPorReversion}</b>: toma la foto del saldo ya
     * repuesto. El promedio no cambia.
     *
     * <p>Lleva el <b>mismo costo que la salida</b>, no el promedio de hoy: la ganancia de una venta es
     * precio menos lo que costó al salir, y con el mismo costo la venta y su reversión se cancelan al
     * peso en cualquier reporte de margen.
     *
     * @param salida el movimiento VENTA que se deshace; la reversión apunta a él
     */
    public static MovimientoKardex porReversionDeVenta(Variante variante, MovimientoKardex salida,
                                                       UUID ventaId, String motivo,
                                                       UUID registradoPorId, Instant cuando) {
        if (salida == null || salida.getTipo() != TipoMovimiento.VENTA) {
            throw new IllegalArgumentException("Una reversión de venta deshace un movimiento VENTA");
        }
        if (!salida.getVariante().getId().equals(variante.getId())) {
            throw new IllegalArgumentException("La reversión tiene que ser del mismo repuesto que la salida");
        }
        return new MovimientoKardex(variante, TipoMovimiento.REVERSION, -salida.getCantidadDelta(),
                salida.getCostoUnitario(), salida.getCostoTotal(), OrigenMovimiento.VENTA, ventaId,
                salida.getId(), motivo, registradoPorId, cuando);
    }

    public boolean esEntrada() {
        return cantidadDelta > 0;
    }
}
