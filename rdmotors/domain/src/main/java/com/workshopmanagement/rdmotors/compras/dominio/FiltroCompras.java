package com.workshopmanagement.rdmotors.compras.dominio;

import java.time.LocalDate;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * Qué compras mostrar en el historial (spec 0002, RF-006 y RF-007). Todo campo {@code null} es
 * "sin filtro": un {@code estado} nulo trae vigentes y anuladas. La pantalla pide las vigentes por
 * defecto.
 *
 * <p><b>Las fechas son de la factura</b>, no de cuando se registró. Es lo que se compró en el
 * período: una factura de agosto capturada en septiembre es una compra de agosto. Confundir las dos
 * fechas mete compras fantasma en meses ya cerrados, que es la razón de que se guarden las dos.
 *
 * @param factura  búsqueda parcial en el número de factura; vacío cuenta como sin filtro
 * @param repuesto las compras con al menos un renglón vigente de un repuesto que coincida
 *                 (spec 0002, RF-025); {@code null} es sin filtro
 */
public record FiltroCompras(
        UUID proveedorId,
        LocalDate desde,
        LocalDate hasta,
        FormaPago formaPago,
        UUID cuentaId,
        String factura,
        EstadoCompra estado,
        BusquedaDeRepuesto repuesto) {

    public FiltroCompras {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ReglaDeNegocioException(
                    "La fecha inicial (" + desde + ") es posterior a la final (" + hasta + ")");
        }
        factura = factura == null || factura.isBlank() ? null : factura.trim();
    }

    public static FiltroCompras sinFiltros() {
        return new FiltroCompras(null, null, null, null, null, null, null, null);
    }

    /**
     * El mismo filtro con otro estado. Existe para que quien cambie el estado no tenga que copiar los
     * demás campos a mano: copiarlos es la forma más fácil de olvidar uno, y un filtro olvidado en los
     * totales hace que no sean los de la lista.
     */
    public FiltroCompras conEstado(EstadoCompra otroEstado) {
        return new FiltroCompras(proveedorId, desde, hasta, formaPago, cuentaId, factura, otroEstado, repuesto);
    }
}
