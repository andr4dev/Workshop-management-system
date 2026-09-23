package com.workshopmanagement.rdmotors.reportes.aplicacion;

import com.workshopmanagement.rdmotors.reportes.dominio.CarteraDelPeriodo;
import com.workshopmanagement.rdmotors.reportes.dominio.Control;
import com.workshopmanagement.rdmotors.reportes.dominio.Periodo;
import com.workshopmanagement.rdmotors.reportes.dominio.ResultadosDelPeriodo;

/**
 * Lo que responde el reporte de resultados (spec 0007): el período con todo su desglose, las cifras del período
 * anterior para comparar (RF-022), las señales de control (RF-023) y la cartera (spec 0008, RF-024).
 *
 * @param periodoAnterior con qué se compara ({@link Periodo#anterior()})
 * @param anterior solo las cifras: el anterior se calcula igual, con el mismo modo de los gastos del mes
 * @param cartera lo cobrado en abonos en el período y lo que queda por cobrar hoy
 */
public record ReporteDeResultados(ResultadosDelPeriodo resultados, Periodo periodoAnterior,
                                  ResultadosDelPeriodo.Cifras anterior, Control control, CarteraDelPeriodo cartera) {
}
