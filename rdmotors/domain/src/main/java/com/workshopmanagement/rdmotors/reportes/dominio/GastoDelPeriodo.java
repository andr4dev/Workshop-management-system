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
 * @param descripcion en qué se gastó: la ve *Ver cálculo* (spec 0014, RF-008)
 * @param repartir   solo en los del mes: día a día, o entero en el reporte que cubre su mes (spec 0014, decisión 5);
 *                   {@code null} en uno del día
 */
public record GastoDelPeriodo(UUID id, LocalDate fecha, UUID categoriaId, String categoria, NaturalezaGasto naturaleza,
                              String descripcion, boolean delMes, Boolean repartir, Dinero monto) {

    /** Del mes, repartido: cada día de su mes carga una parte igual. */
    public boolean repartido() {
        return delMes && Boolean.TRUE.equals(repartir);
    }

    /** El mes de un gasto del mes es el de su fecha (RF-008a). */
    public YearMonth mes() {
        return YearMonth.from(fecha);
    }
}
