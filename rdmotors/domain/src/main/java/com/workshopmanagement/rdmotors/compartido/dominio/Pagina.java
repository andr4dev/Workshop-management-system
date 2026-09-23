package com.workshopmanagement.rdmotors.compartido.dominio;

import java.util.List;
import java.util.function.Function;

/**
 * Una página de resultados: lo que se muestra, más el total para poder paginar.
 *
 * <p>No se usa {@code org.springframework.data.domain.Page} porque el dominio no importa Spring.
 * El adaptador traduce de una a otra.
 */
public record Pagina<T>(List<T> elementos, long total, int numero, int tamano) {

    public int totalPaginas() {
        return tamano <= 0 ? 0 : (int) ((total + tamano - 1) / tamano);
    }

    public <R> Pagina<R> mapear(Function<T, R> funcion) {
        return new Pagina<>(elementos.stream().map(funcion).toList(), total, numero, tamano);
    }
}
