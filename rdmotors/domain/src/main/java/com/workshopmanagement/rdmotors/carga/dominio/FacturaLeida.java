package com.workshopmanagement.rdmotors.carga.dominio;

import java.time.LocalDate;
import java.util.List;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * Lo que salió de leer un archivo (spec 0012). Lo impreso —sub-total, IVA, total, número, fecha, NIT— solo lo trae
 * el PDF; en un Excel queda en {@code null} y se le pide a la persona.
 *
 * @param subtotalImpreso el sub-total que dice la factura abajo, antes del IVA. Contra él se valida que la lectura
 *                        esté completa (RF-004)
 * @param nitProveedor    solo dígitos, sin dígito de verificación: para reconocer al proveedor sin preguntarle a nadie
 */
public record FacturaLeida(
        OrigenCarga origen,
        List<RenglonLeido> renglones,
        Dinero subtotalImpreso,
        Dinero ivaImpreso,
        Dinero totalImpreso,
        String nitProveedor,
        String numeroFactura,
        LocalDate fecha) {

    public FacturaLeida {
        renglones = List.copyOf(renglones);
    }

    /** Un Excel o un CSV: trae renglones y nada impreso. */
    public static FacturaLeida soloRenglones(OrigenCarga origen, List<RenglonLeido> renglones) {
        return new FacturaLeida(origen, renglones, null, null, null, null, null, null);
    }
}
