package com.workshopmanagement.rdmotors.compras.dominio;

import java.util.List;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * Lo pagado en un período, sumado por forma de pago y por cuenta (spec 0002, H6 y RF-010).
 *
 * <p><b>{@code total} y {@code partes} salen de dos consultas distintas a propósito.</b> Si el
 * total se calculara sumando las partes, cuadraría siempre, incluso con una compra que no cayera en
 * ninguna. Con dos consultas, que <b>las partes sumen el total</b> es algo que se puede comprobar, y
 * la prueba de integración lo comprueba.
 *
 * <p>Las compras anuladas nunca cuentan.
 */
public record TotalesCompras(Dinero total, long compras, List<Parte> partes) {

    public TotalesCompras {
        partes = List.copyOf(partes);
    }

    /**
     * @param cuentaId {@code null} en efectivo
     * @param cuenta   nombre de la cuenta, {@code null} en efectivo
     */
    public record Parte(FormaPago formaPago, UUID cuentaId, String cuenta, long compras, Dinero total) {
    }

    public Dinero sumaDePartes() {
        return partes.stream().map(Parte::total).reduce(Dinero.CERO, Dinero::mas);
    }
}
