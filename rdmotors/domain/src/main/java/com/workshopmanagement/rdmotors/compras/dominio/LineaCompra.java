package com.workshopmanagement.rdmotors.compras.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Un renglon de la factura del proveedor.
 *
 * <p><b>Dos modos de captura, dos constructores.</b> El car-wash solo acepta costo unitario y
 * calcula el total; aqui se puede escribir cualquiera de los dos y el sistema deduce el otro.
 *
 * <pre>
 *   Modo TOTAL                        Modo UNITARIO
 *   Cantidad:  20                     Cantidad:  20
 *   Pague:     $200.000               C/u:       $10.000
 *   ─────────────────────             ─────────────────────
 *   Sale a:    $10.000 c/u   ←        Total:     $200.000   ←
 * </pre>
 *
 * <p><b>Se guardan SIEMPRE las dos cifras, en cualquier modo.</b> El total es el hecho —lo que
 * realmente se pago— y el unitario es derivado, con 4 decimales. Nunca se recalcula uno a partir
 * del otro al leer, porque ahi es donde se pierden pesos: ver {@link Dinero#dividirEntre(int)}.
 */
@Entity
@Table(name = "linea_compra")
@Getter
public class LineaCompra {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compra_id", nullable = false)
    private Compra compra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", nullable = false)
    private Variante variante;

    /**
     * El lugar del renglón en la factura, empezando en 0. El detalle muestra la compra en el mismo
     * orden en que se capturó, para poder compararla contra el papel renglón por renglón.
     */
    @Column(name = "posicion", nullable = false)
    private int posicion;

    @Column(name = "cantidad", nullable = false)
    private int cantidad;

    /** <b>Autoritativo.</b> Es lo que salio de la caja por este renglon. */
    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "costo_total", nullable = false))
    private Dinero costoTotal;

    /** Derivado, con 4 decimales para que {@code cantidad x unitario} vuelva a dar el total. */
    @Column(name = "costo_unitario", nullable = false, precision = 14, scale = 4)
    private BigDecimal costoUnitario;

    @Enumerated(EnumType.STRING)
    @Column(name = "modo_captura", nullable = false, length = 10)
    private ModoCaptura modoCaptura;

    /**
     * Precio de venta que el usuario escribio al comprar. {@code null} = <b>no tocar el precio</b>.
     *
     * <p>Reponer stock no debe reescribir en silencio un precio que nadie quiso cambiar. Regla
     * heredada del car-wash, donde ya costo aprenderla.
     */
    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "precio_venta"))
    private Dinero precioVenta;

    /**
     * El precio que tenía el repuesto <b>justo antes</b> de que este renglón le fijara uno. Es lo que
     * permite devolverlo si el renglón se deshace (spec 0002, RF-017). {@code null} si este renglón
     * no tocó el precio, o si se registró antes de que existiera esta columna.
     */
    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "precio_anterior"))
    private Dinero precioAnterior;

    /** El movimiento de kardex con que este renglón entró al inventario. Es lo que se revierte. */
    @Column(name = "movimiento_entrada_id")
    private UUID movimientoEntradaId;

    /**
     * {@code false} cuando una corrección lo reemplazó o lo quitó. <b>No se borra nunca</b>: queda como
     * historia de lo que decía la factura antes (spec 0002, RF-018).
     */
    @Column(name = "vigente", nullable = false)
    private boolean vigente;

    @Column(name = "reemplazada_en")
    private Instant reemplazadaEn;

    protected LineaCompra() {
        // JPA
    }

    private LineaCompra(Variante variante, int cantidad, Dinero costoTotal,
                        BigDecimal costoUnitario, ModoCaptura modo, Dinero precioVenta) {
        this.id = UUID.randomUUID();
        this.variante = variante;
        this.cantidad = cantidad;
        this.costoTotal = costoTotal;
        this.costoUnitario = costoUnitario;
        this.modoCaptura = modo;
        this.precioVenta = precioVenta;
        this.vigente = true;
    }

    /**
     * Modo TOTAL: "compre 15 y pague $200.000".
     *
     * <p>El total queda exacto en pesos; el unitario sale con decimales
     * ({@code $200.000 / 15 = $13.333,3333}). Redondear el unitario a entero aqui haria que
     * {@code 15 x $13.333 = $199.995} y <b>faltarian $5</b> contra la factura del proveedor.
     */
    public static LineaCompra porTotal(Variante variante, int cantidad, Dinero total,
                                       Dinero precioVenta) {
        validar(variante, cantidad);
        if (total == null || total.esCero() || total.esNegativo()) {
            throw new ReglaDeNegocioException(
                    "El costo debe ser mayor a cero. Sin costo real, el producto reportaria "
                            + "100% de utilidad.");
        }
        return new LineaCompra(variante, cantidad, total, total.dividirEntre(cantidad),
                ModoCaptura.TOTAL, precioVenta);
    }

    /** Modo UNITARIO: "compre 20 a $10.000 cada uno". El total se deriva y queda exacto. */
    public static LineaCompra porUnitario(Variante variante, int cantidad, BigDecimal unitario,
                                          Dinero precioVenta) {
        validar(variante, cantidad);
        if (unitario == null || unitario.signum() <= 0) {
            throw new ReglaDeNegocioException(
                    "El costo debe ser mayor a cero. Sin costo real, el producto reportaria "
                            + "100% de utilidad.");
        }
        BigDecimal unitarioEscalado = unitario.setScale(Dinero.ESCALA_UNITARIA,
                java.math.RoundingMode.HALF_UP);
        return new LineaCompra(variante, cantidad,
                Dinero.desdeUnitario(unitarioEscalado, cantidad), unitarioEscalado,
                ModoCaptura.UNITARIO, precioVenta);
    }

    /** Utilidad por unidad si el usuario fijo precio en esta linea. Se muestra en vivo al capturar. */
    public BigDecimal utilidadUnitariaProyectada() {
        Dinero precio = precioVenta != null ? precioVenta : variante.getPrecio();
        if (precio == null) return null;
        return precio.valor().subtract(costoUnitario);
    }

    void asignarA(Compra compra, int posicion) {
        this.compra = compra;
        this.posicion = posicion;
    }

    /**
     * Deja anotado con qué movimiento entró al inventario y qué precio había antes. Se llama justo
     * después de mover el inventario, que es cuando los dos datos existen.
     */
    public void anotarEntrada(UUID movimientoId, Dinero precioAntesDeEsteRenglon) {
        this.movimientoEntradaId = movimientoId;
        this.precioAnterior = precioVenta == null ? null : precioAntesDeEsteRenglon;
    }

    /**
     * Este mismo renglón con otro precio de venta. Cantidad y costo no cambian, así que <b>no mueve
     * inventario</b>: comparte el movimiento de entrada del original.
     *
     * @param precioVigente el precio del repuesto ahora; es la base si el original no había tocado
     *                      el precio
     */
    public LineaCompra conOtroPrecio(Dinero nuevoPrecio, Dinero precioVigente) {
        LineaCompra copia = new LineaCompra(variante, cantidad, costoTotal, costoUnitario,
                modoCaptura, nuevoPrecio);
        copia.movimientoEntradaId = movimientoEntradaId;
        if (nuevoPrecio != null) {
            // Si el original ya había cambiado el precio, la base sigue siendo la de antes de él.
            copia.precioAnterior = precioVenta != null && precioAnterior != null
                    ? precioAnterior
                    : precioVigente;
        }
        return copia;
    }

    void darDeBaja(Instant cuando) {
        if (!vigente) {
            throw new ReglaDeNegocioException("Ese renglón ya había sido reemplazado");
        }
        this.vigente = false;
        this.reemplazadaEn = cuando;
    }

    /** El renglón tal como se ve en el antes y el después de una corrección. */
    public Map<String, Object> fotografia() {
        Map<String, Object> foto = new LinkedHashMap<>();
        foto.put("codigo", variante.getCodigo());
        foto.put("repuesto", variante.getProducto().getNombre() + " " + variante.getMarcaRepuesto());
        foto.put("cantidad", cantidad);
        foto.put("modo", modoCaptura.name());
        foto.put("costoUnitario", costoUnitario);
        foto.put("costoTotal", costoTotal.valor().longValueExact());
        foto.put("precioVenta", precioVenta == null ? null : precioVenta.valor().longValueExact());
        return foto;
    }

    private static void validar(Variante variante, int cantidad) {
        if (variante == null) {
            throw new ReglaDeNegocioException("La linea debe apuntar a un repuesto");
        }
        if (cantidad <= 0) {
            throw new ReglaDeNegocioException("La cantidad debe ser mayor a cero: " + cantidad);
        }
    }
}
