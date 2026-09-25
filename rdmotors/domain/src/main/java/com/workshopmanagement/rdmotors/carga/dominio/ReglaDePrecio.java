package com.workshopmanagement.rdmotors.carga.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * Cómo se sugiere el precio de venta de lo que llega en una factura (spec 0012, decisiones 1 y 2).
 *
 * <h2>Primero el IVA, después la ganancia</h2>
 *
 * El proveedor cobra el IVA aparte, abajo de la factura, y RD Motors no lo recupera por ningún otro lado que no sea
 * el precio de venta: <b>es costo</b>. Entonces la ganancia se calcula sobre lo que de verdad salió del bolsillo:
 *
 * <pre>
 *   costo con IVA   = valor total del renglón × (1 + IVA)  ÷ cantidad
 *   precio sugerido = costo con IVA × (1 + ganancia),  redondeado HACIA ARRIBA
 * </pre>
 *
 * La bujía iridium de la MAG477 —8 unidades por $308.274— queda en $45.855,7575 de costo por unidad y $66.491 de
 * precio: 45% sobre lo pagado. Sumando los porcentajes (19 + 45 = 64%) saldría a $63.196, que parece lo mismo y deja
 * 37,8%.
 *
 * <h2>Por qué hacia arriba</h2>
 *
 * Redondear al múltiplo más cercano bajaría la mitad de los precios, y cada peso de menos se pierde en todas las
 * ventas de ese repuesto. Hacia arriba, la sugerencia nunca deja menos ganancia de la pedida.
 *
 * @param ivaPct      el IVA que cobró el proveedor, en porcentaje (19). En 0 el costo queda sin IVA: la salida
 *                    por si algún día el contador dice que RD Motors sí lo recupera
 * @param gananciaPct lo que se le gana a lo que se pagó, en porcentaje (45)
 * @param redondeo    a qué múltiplo de pesos se sube el sugerido (100 por defecto; 1 es sin redondear)
 */
public record ReglaDePrecio(BigDecimal ivaPct, BigDecimal gananciaPct, int redondeo) {

    public static final BigDecimal IVA_POR_DEFECTO = new BigDecimal("19");
    public static final BigDecimal GANANCIA_POR_DEFECTO = new BigDecimal("45");
    public static final int REDONDEO_POR_DEFECTO = 100;

    private static final BigDecimal CIEN = new BigDecimal("100");
    /** Más de 1000% no es una ganancia, es un dedo que se fue sobre un cero de más. */
    private static final BigDecimal TOPE = new BigDecimal("1000");

    public ReglaDePrecio {
        ivaPct = exigirPorcentaje(ivaPct, "El IVA");
        gananciaPct = exigirPorcentaje(gananciaPct, "La ganancia");
        if (redondeo < 1 || redondeo > 10_000) {
            throw new ReglaDeNegocioException("El redondeo tiene que ser de 1 a 10.000 pesos");
        }
    }

    public static ReglaDePrecio porDefecto() {
        return new ReglaDePrecio(IVA_POR_DEFECTO, GANANCIA_POR_DEFECTO, REDONDEO_POR_DEFECTO);
    }

    /** El IVA de toda la factura: el sub-total por el porcentaje, redondeado al peso como lo imprime el proveedor. */
    public Dinero ivaDe(Dinero subtotal) {
        return Dinero.de(subtotal.valor().multiply(ivaPct).divide(CIEN, 0, RoundingMode.HALF_UP));
    }

    /**
     * Lo que costó cada unidad, con el IVA: el valor total del renglón —que es de <b>todas</b> las unidades— más el
     * IVA, entre la cantidad. Con los 4 decimales del sistema.
     *
     * <p>Dividir es lo que no se puede olvidar: la bujía son 8 unidades por $308.274, y sin dividir su precio
     * sugerido saldría a $505.569.
     *
     * @return {@code null} si con esos datos no hay costo: sin cantidad, o sin un total que sea plata
     */
    public BigDecimal costoConIvaPorUnidad(Dinero valorTotal, Integer cantidad) {
        if (valorTotal == null || valorTotal.valor().signum() <= 0 || cantidad == null || cantidad <= 0) {
            return null;
        }
        return valorTotal.valor().multiply(CIEN.add(ivaPct))
                .divide(CIEN.multiply(BigDecimal.valueOf(cantidad)), Dinero.ESCALA_UNITARIA, RoundingMode.HALF_UP);
    }

    /**
     * El precio sugerido para un costo por unidad que ya trae el IVA.
     *
     * @param costoUnitarioConIva lo que costó cada unidad, con IVA, con los 4 decimales del sistema
     */
    public Dinero sugerido(BigDecimal costoUnitarioConIva) {
        if (costoUnitarioConIva == null || costoUnitarioConIva.signum() <= 0) {
            throw new ReglaDeNegocioException("Sin costo no hay precio que sugerir");
        }
        BigDecimal conGanancia = costoUnitarioConIva.multiply(CIEN.add(gananciaPct)).divide(CIEN, 4,
                RoundingMode.HALF_UP);
        BigDecimal multiplo = BigDecimal.valueOf(redondeo);
        BigDecimal veces = conGanancia.divide(multiplo, 0, RoundingMode.CEILING);
        return Dinero.de(veces.multiply(multiplo));
    }

    private static BigDecimal exigirPorcentaje(BigDecimal pct, String que) {
        if (pct == null) {
            throw new ReglaDeNegocioException(que + " es obligatorio");
        }
        if (pct.signum() < 0 || pct.compareTo(TOPE) > 0) {
            throw new ReglaDeNegocioException(que + " tiene que estar entre 0% y 1.000%");
        }
        // 19, 19.0 y 19.00 son el mismo porcentaje: se guardan igual, para que dos reglas iguales sean iguales.
        BigDecimal limpio = pct.stripTrailingZeros();
        return limpio.scale() < 0 ? limpio.setScale(0) : limpio;
    }
}
