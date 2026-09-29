package com.workshopmanagement.rdmotors.clientes.dominio;

import java.util.List;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * Lo que se llevó el cliente en una venta fiada, para verlo en su ficha de la Cartera: cada repuesto con su cantidad y
 * su precio, y el descuento si hubo. Solo lectura: es la foto que quedó en la venta al cobrarla.
 *
 * <p>{@code total} es el de la venta entera; lo fiado puede ser menos, si pagó una parte al llevárselo.
 *
 * @param descuento {@link Dinero#CERO} si no hubo
 */
public record VentaDeLaDeuda(UUID ventaId, List<Renglon> renglones, Dinero subtotal, Dinero descuento,
                             String motivoDescuento, Dinero total) {

    /**
     * @param cambio en un aceite que paga comisión, lo que se escogió al vender ({@code SE_CAMBIA} o
     *               {@code NO_SE_CAMBIA}, spec 0015); nulo en los demás y en los de antes
     */
    public record Renglon(String codigo, String nombre, String marca, int cantidad, Dinero precioUnitario, Dinero total,
                          String cambio) {
    }
}
