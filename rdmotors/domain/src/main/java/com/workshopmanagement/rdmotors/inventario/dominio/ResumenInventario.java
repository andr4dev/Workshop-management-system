package com.workshopmanagement.rdmotors.inventario.dominio;

import java.math.BigDecimal;

/**
 * Cifras de todo el inventario activo.
 *
 * <p>{@code valor} suma solo los repuestos con costo conocido, y {@code sinCosto} dice cuántos
 * quedaron fuera. Contarlos como costo cero haría ver el inventario más barato de lo que es: se
 * reporta lo que se sabe y cuánto falta por medir.
 */
public record ResumenInventario(long referencias, long unidades, BigDecimal valor,
                                long sinCosto, long conStockBajo) {
}
