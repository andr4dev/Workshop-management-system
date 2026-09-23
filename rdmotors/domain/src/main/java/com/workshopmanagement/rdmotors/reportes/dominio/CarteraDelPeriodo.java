package com.workshopmanagement.rdmotors.reportes.dominio;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * El fiado del período y lo que queda por cobrar (spec 0008, RF-024).
 *
 * <p><b>Lo vendido y lo cobrado no se mezclan.</b> El fiado está en las cifras del período, porque es venta el día que
 * se vende; lo que aquí se dice es <b>cuánto entró por abonos</b> —un cobro, no una venta— y <b>cuánto falta por
 * cobrar</b>, que no es de un período: es lo que deben hoy.
 *
 * @param abonosEfectivo      lo que los clientes abonaron en efectivo en el período, sin lo anulado
 * @param abonosTransferencia lo mismo, por transferencia
 * @param porCobrar           lo que deben hoy todos los clientes, sea de cuando sea
 * @param clientesQueDeben    cuántos deben hoy
 */
public record CarteraDelPeriodo(Dinero abonosEfectivo, Dinero abonosTransferencia, Dinero porCobrar,
                                int clientesQueDeben) {

    public static final CarteraDelPeriodo VACIA =
            new CarteraDelPeriodo(Dinero.CERO, Dinero.CERO, Dinero.CERO, 0);

    /** Todo lo que entró por abonos en el período. */
    public Dinero cobrado() {
        return abonosEfectivo.mas(abonosTransferencia);
    }
}
