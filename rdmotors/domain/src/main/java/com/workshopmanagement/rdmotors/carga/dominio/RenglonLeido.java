package com.workshopmanagement.rdmotors.carga.dominio;

import java.math.BigDecimal;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * Un renglón tal como se leyó del archivo, antes de que nadie lo revise (spec 0012, RF-000 y RF-001).
 *
 * <p><b>Puede venir malo, y está bien.</b> Un número que no se entendió llega en {@code null}, no como un cero
 * inventado: la pre-carga lo muestra marcado y el dueño lo corrige mirando el papel. Un lector que "arregla" lo que
 * no entendió es un lector que miente.
 *
 * @param ubicacion      dónde está en el papel: "pág. 12" en un PDF, "fila 34" en un Excel. Para buscarlo cuando
 *                       no cuadra
 * @param precioUnitario el de lista, antes del descuento. Solo lo trae el PDF (o un Excel que tenga la columna); con
 *                       él y el descuento, el renglón se comprueba solo
 * @param descuentoPct   el descuento del proveedor, en porcentaje
 * @param valorTotal     el renglón entero, después del descuento, sin IVA
 * @param marca          si el archivo la trae. El PDF no: se propone después
 * @param categoria      el nombre, si el archivo la trae
 */
public record RenglonLeido(
        String ubicacion,
        String codigo,
        String descripcion,
        Integer cantidad,
        String unidad,
        Dinero precioUnitario,
        BigDecimal descuentoPct,
        Dinero valorTotal,
        String marca,
        String categoria) {
}
