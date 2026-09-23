package com.workshopmanagement.rdmotors.ventas.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.Motivo;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * Un descuento sobre el total de una venta (spec 0003, RF-015 y RF-016).
 *
 * <p><b>Se guarda el monto en pesos; el porcentaje es cómo se capturó.</b> Si se guardara solo "10%",
 * cualquier cambio del total alteraría el descuento en silencio. El monto es el hecho.
 *
 * <p>Del porcentaje al monto se redondea al peso con {@code HALF_UP}: 10% de $38.500 = $3.850;
 * 7,5% de $13.333 = $999,975 → $1.000. La pantalla calcula igual para mostrarlo antes de cobrar, y
 * las dos pruebas usan la misma tabla de casos.
 *
 * @param porcentaje solo si se capturó en porcentaje; con hasta 2 decimales
 */
public record Descuento(Dinero monto, ModoDescuento modo, BigDecimal porcentaje, String motivo) {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    public Descuento {
        if (monto == null || modo == null) {
            throw new ReglaDeNegocioException("Al descuento le falta el monto o cómo se capturó");
        }
        if (monto.esNegativo() || monto.esCero()) {
            throw new ReglaDeNegocioException("El descuento tiene que ser mayor a $0");
        }
        motivo = Motivo.exigir(motivo);
    }

    /** "Te lo dejo en $35.000" sobre $38.000 es un descuento de $3.000 por monto. */
    public static Descuento porMonto(Dinero monto, String motivo) {
        return new Descuento(monto, ModoDescuento.MONTO, null, motivo);
    }

    /**
     * Cuánto es ese porcentaje del subtotal, redondeado al peso (HALF_UP). Sin validar ni pedir motivo: lo usa
     * también el aviso de pérdida, que se calcula mientras el cajero todavía escribe el descuento.
     */
    public static Dinero montoDelPorcentaje(BigDecimal porcentaje, Dinero subtotal) {
        return Dinero.de(subtotal.valor().multiply(porcentaje).divide(CIEN, 0, RoundingMode.HALF_UP));
    }

    /** El monto sale del porcentaje sobre el subtotal, redondeado al peso. */
    public static Descuento porPorcentaje(BigDecimal porcentaje, Dinero subtotal, String motivo) {
        if (porcentaje == null || porcentaje.signum() <= 0 || porcentaje.compareTo(CIEN) > 0) {
            throw new ReglaDeNegocioException("El porcentaje de descuento va de más de 0 hasta 100");
        }
        if (porcentaje.stripTrailingZeros().scale() > 2) {
            throw new ReglaDeNegocioException("El porcentaje de descuento admite hasta 2 decimales");
        }
        return new Descuento(montoDelPorcentaje(porcentaje, subtotal), ModoDescuento.PORCENTAJE,
                porcentaje.setScale(2, RoundingMode.UNNECESSARY), motivo);
    }
}
