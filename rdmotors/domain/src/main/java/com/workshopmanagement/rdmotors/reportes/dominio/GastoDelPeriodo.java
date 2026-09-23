package com.workshopmanagement.rdmotors.reportes.dominio;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import com.workshopmanagement.rdmotors.caja.dominio.NaturalezaGasto;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * FILA DE LECTURA — un gasto no anulado que toca el período: por su fecha, o por su mes si es del mes.
 *
 * @param naturaleza la de su categoría: un COSTO resta en la utilidad bruta, un GASTO en la operativa
 */
public record GastoDelPeriodo(UUID id, LocalDate fecha, UUID categoriaId, String categoria, NaturalezaGasto naturaleza,
                              boolean delMes, Dinero monto) {

    /** El mes de un gasto del mes es el de su fecha (RF-008a). */
    public YearMonth mes() {
        return YearMonth.from(fecha);
    }
}
