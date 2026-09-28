package com.workshopmanagement.rdmotors.caja.dominio;

import java.time.LocalDate;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * Qué gastos mostrar (spec 0006, RF-007a). Todo campo {@code null} es "sin filtro".
 *
 * @param delMes {@code true} solo los del mes, {@code false} solo los del día (spec 0014, RF-015)
 *
 * <p>Las fechas son las del gasto, no las de cuando se registró: el arriendo de agosto registrado el 2 de
 * septiembre es un gasto de agosto.
 */
public record FiltroGastos(LocalDate desde, LocalDate hasta, UUID categoriaId, Boolean delMes) {

    /** Sin mirar si son del día o del mes. */
    public FiltroGastos(LocalDate desde, LocalDate hasta, UUID categoriaId) {
        this(desde, hasta, categoriaId, null);
    }

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
