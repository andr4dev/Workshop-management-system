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
 * @param vendidoFiado        lo que quedó fiado de las ventas del período (spec 0014): informativo, no es plata que
 *                            entró
 * @param cobradoDelCuaderno  la parte de los abonos que pagó saldos del cuaderno (spec 0014, decisión 3): no es venta
 */
public record CarteraDelPeriodo(Dinero abonosEfectivo, Dinero abonosTransferencia, Dinero porCobrar,
                                int clientesQueDeben, Dinero vendidoFiado, Dinero cobradoDelCuaderno) {

    public static final CarteraDelPeriodo VACIA =
            new CarteraDelPeriodo(Dinero.CERO, Dinero.CERO, Dinero.CERO, 0, Dinero.CERO, Dinero.CERO);

    /** Sin lo vendido fiado ni lo del cuaderno. */
    public CarteraDelPeriodo(Dinero abonosEfectivo, Dinero abonosTransferencia, Dinero porCobrar,
                             int clientesQueDeben) {
        this(abonosEfectivo, abonosTransferencia, porCobrar, clientesQueDeben, Dinero.CERO, Dinero.CERO);
    }

    /** Todo lo que entró por abonos en el período. */
    public Dinero cobrado() {
        return abonosEfectivo.mas(abonosTransferencia);
    }
}
