package com.workshopmanagement.rdmotors.reportes.dominio;

import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * FILA DE LECTURA — un renglón de una venta cobrada del período.
 *
 * @param total precio por cantidad, antes del descuento de la venta
 * @param costo el costo con que salió del kardex al venderse, no el de hoy; {@code null} si el repuesto no tenía
 *              costo (RF-006, RF-007)
 * @param categoriaId {@code null} si el repuesto no tiene categoría
 */
public record RenglonVendido(UUID ventaId, int posicion, UUID varianteId, String codigo, String nombre, String marca,
                             UUID categoriaId, String categoria, int cantidad, Dinero total, Dinero costo) {

    public boolean tieneCosto() {
        return costo != null;
    }
}
