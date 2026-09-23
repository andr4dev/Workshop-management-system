package com.workshopmanagement.rdmotors.inventario.dominio;

import java.util.UUID;

/**
 * Cuántos repuestos activos de lo buscado hay en una categoría (spec 0005, RF-002): con *aceite*,
 * *Filtros 2* y *Lubricantes y químicos 5*.
 *
 * @param categoriaId {@code null} = los que no tienen categoría
 * @param nombre      {@code null} = los que no tienen categoría
 */
public record ConteoCategoria(UUID categoriaId, String nombre, long repuestos) {
}
