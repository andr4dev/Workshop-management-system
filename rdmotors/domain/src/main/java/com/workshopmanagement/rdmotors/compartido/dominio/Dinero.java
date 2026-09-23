package com.workshopmanagement.rdmotors.compartido.dominio;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Pesos colombianos. Objeto de valor inmutable.
 *
 * <p><b>El peso no tiene centavos.</b> Todo monto que se cobra, se paga o se reporta es un entero,
 * y el redondeo se decide aqui, una sola vez, en vez de en treinta sitios distintos.
 *
 * <p><b>Por que existe esta clase y no un BigDecimal suelto:</b> un BigDecimal se puede sumar a
 * otro que representa unidades, un porcentaje o un costo unitario, y el compilador no dice nada.
 * Envolverlo hace que el tipo distinga "plata" de "cualquier otro numero".
 *
 * <p><b>Nunca usar double ni float para dinero.</b> {@code 0.1 + 0.2} no da {@code 0.3} en punto
 * flotante; en un punto de venta eso aparece como una venta de $47.000 que el reporte muestra
 * como $46.999,99999.
 *
 * @see #dividirEntre(int) la operacion que no devuelve Dinero, y por que
 */
@Embeddable
public class Dinero implements Comparable<Dinero>, Serializable {

    /** Escala de los costos unitarios y promedios. Ver {@link #dividirEntre(int)}. */
    public static final int ESCALA_UNITARIA = 4;

    public static final Dinero CERO = Dinero.de(0);

    @Column(nullable = false)
    private BigDecimal monto;

    protected Dinero() {
        // JPA
    }

    private Dinero(BigDecimal monto) {
        this.monto = monto.setScale(0, RoundingMode.HALF_UP);
    }

    public static Dinero de(long pesos) {
        return new Dinero(BigDecimal.valueOf(pesos));
    }

    public static Dinero de(BigDecimal pesos) {
        return new Dinero(Objects.requireNonNull(pesos, "monto"));
    }

    public BigDecimal valor() {
        return monto;
    }

    public Dinero mas(Dinero otro) {
        return new Dinero(monto.add(otro.monto));
    }

    public Dinero menos(Dinero otro) {
        return new Dinero(monto.subtract(otro.monto));
    }

    public Dinero por(int cantidad) {
        return new Dinero(monto.multiply(BigDecimal.valueOf(cantidad)));
    }

    /**
     * Reparte este monto entre {@code cantidad} unidades.
     *
     * <p><b>Devuelve BigDecimal con 4 decimales, NO un Dinero. Esa es toda la gracia.</b>
     *
     * <p>Comprar 15 unidades por $200.000 da $13.333,3333... por unidad. Si el costo unitario se
     * guardara como entero ($13.333), al reconstruir el total daria {@code 15 x 13.333 = $199.995}
     * y <b>se perderian $5</b> — el reporte de compras dejaria de cuadrar con la factura del
     * proveedor. Peor: el error se acumula silenciosamente en cientos de lineas y nadie lo
     * encuentra.
     *
     * <p>Con 4 decimales: {@code 15 x 13.333,3333 = $199.999,995} → redondea a $200.000. Cuadra.
     *
     * <p>Por eso el sistema guarda SIEMPRE las dos cifras: el total en pesos enteros (que es el
     * hecho, lo que realmente se pago) y el unitario con decimales (que es derivado). Nunca se
     * recalcula uno a partir del otro al leer.
     */
    public BigDecimal dividirEntre(int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a cero: " + cantidad);
        }
        return monto.divide(BigDecimal.valueOf(cantidad), ESCALA_UNITARIA, RoundingMode.HALF_UP);
    }

    /** Reconstruye un monto a partir de un costo unitario con decimales. Inversa de {@link #dividirEntre}. */
    public static Dinero desdeUnitario(BigDecimal costoUnitario, int cantidad) {
        return Dinero.de(costoUnitario.multiply(BigDecimal.valueOf(cantidad)));
    }

    public boolean esCero() {
        return monto.signum() == 0;
    }

    public boolean esNegativo() {
        return monto.signum() < 0;
    }

    public boolean esMayorQue(Dinero otro) {
        return compareTo(otro) > 0;
    }

    @Override
    public int compareTo(Dinero otro) {
        return monto.compareTo(otro.monto);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Dinero otro)) return false;
        return monto.compareTo(otro.monto) == 0;
    }

    @Override
    public int hashCode() {
        return monto.stripTrailingZeros().hashCode();
    }

    /**
     * "$38.000": como lo lee el cajero en un mensaje. {@link #toString()} queda para depurar.
     */
    public String enPesos() {
        return "$" + java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("es-CO"))
                .format(monto);
    }

    @Override
    public String toString() {
        return "$" + monto.toPlainString();
    }
}
