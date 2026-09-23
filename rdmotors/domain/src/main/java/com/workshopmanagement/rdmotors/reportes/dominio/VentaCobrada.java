package com.workshopmanagement.rdmotors.reportes.dominio;

import java.time.LocalDate;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * FILA DE LECTURA — una venta cobrada y no anulada del período, con cómo se pagó.
 *
 * @param dia el día de Colombia en que se cobró
 * @param total lo que vale la venta: sus renglones menos el descuento
 * @param fiado lo que quedó debiendo el cliente (spec 0008): es venta el día que se vende, aunque no haya entrado
 */
public record VentaCobrada(UUID id, LocalDate dia, Dinero total, Dinero descuento, Dinero efectivo,
                           Dinero transferencia, Dinero fiado) {

    /** Una venta de contado. */
    public VentaCobrada(UUID id, LocalDate dia, Dinero total, Dinero descuento, Dinero efectivo,
                        Dinero transferencia) {
        this(id, dia, total, descuento, efectivo, transferencia, Dinero.CERO);
    }
}
