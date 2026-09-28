package com.workshopmanagement.rdmotors.reportes.dominio;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;

/**
 * FILA DE LECTURA — plata que entró por una venta (spec 0014): lo que se pagó al cobrarla, o lo que un abono le aplicó
 * a su deuda.
 *
 * @param momento cuándo cuenta: lo pagado al cobrar, en el momento del cobro; un abono, en el más tardío entre la
 *                venta y el abono (decisión 2): lo que estaba a favor y se aplica a una venta nueva cuenta con esa venta
 * @param dia     el día de Colombia de ese momento
 * @param forma   la del pago, o la del abono
 * @param deAbono si vino de un abono, y no del cobro de la venta
 */
public record CobroDeVenta(UUID ventaId, Instant momento, LocalDate dia, FormaPago forma, Dinero monto,
                           boolean deAbono) {
}
