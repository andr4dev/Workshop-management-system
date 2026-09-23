package com.workshopmanagement.rdmotors.compras.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * Una fila del historial de compras: lo que se ve sin abrir la factura.
 *
 * <p>Es una lectura, no una entidad: se arma en la base con una sola consulta por página, en vez de
 * traer cada compra con sus renglones solo para contarlos.
 *
 * @param cuenta    nombre de la cuenta, o {@code null} si se pagó en efectivo
 * @param pagadaDeCaja si se pagó con plata del cajón (spec 0006)
 * @param renglones los vigentes: los reemplazados por una corrección no cuentan
 */
public record ResumenCompra(
        UUID id,
        LocalDate fechaDocumento,
        Instant fechaRegistro,
        String proveedor,
        String numeroFactura,
        FormaPago formaPago,
        String cuenta,
        boolean pagadaDeCaja,
        EstadoCompra estado,
        long renglones,
        Dinero total) {

    /** La consulta trae el total como la columna numérica; aquí vuelve a ser {@link Dinero}. */
    public ResumenCompra(UUID id, LocalDate fechaDocumento, Instant fechaRegistro, String proveedor,
                         String numeroFactura, FormaPago formaPago, String cuenta, boolean pagadaDeCaja,
                         EstadoCompra estado, long renglones, BigDecimal total) {
        this(id, fechaDocumento, fechaRegistro, proveedor, numeroFactura, formaPago, cuenta, pagadaDeCaja,
                estado, renglones, Dinero.de(total));
    }
}
