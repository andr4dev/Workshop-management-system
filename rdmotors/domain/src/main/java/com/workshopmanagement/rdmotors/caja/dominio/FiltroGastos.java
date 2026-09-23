package com.workshopmanagement.rdmotors.caja.dominio;

import java.time.LocalDate;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * Qué gastos mostrar (spec 0006, RF-007a). Todo campo {@code null} es "sin filtro".
 *
 * <p>Las fechas son las del gasto, no las de cuando se registró: el arriendo de agosto registrado el 2 de
 * septiembre es un gasto de agosto.
 */
public record FiltroGastos(LocalDate desde, LocalDate hasta, UUID categoriaId) {

    public FiltroGastos {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ReglaDeNegocioException(
                    "La fecha inicial (" + desde + ") es posterior a la final (" + hasta + ")");
        }
    }

    public static FiltroGastos sinFiltros() {
        return new FiltroGastos(null, null, null);
    }
}
