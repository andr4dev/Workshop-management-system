package com.workshopmanagement.rdmotors.reportes.aplicacion;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.reportes.dominio.CobroDeVenta;
import com.workshopmanagement.rdmotors.reportes.dominio.Control;
import com.workshopmanagement.rdmotors.reportes.dominio.Periodo;
import com.workshopmanagement.rdmotors.reportes.dominio.ResultadosDelPeriodo;
import com.workshopmanagement.rdmotors.reportes.dominio.puerto.RepositorioReportes;

/**
 * CASO DE USO — los resultados de un período: cuánto se vendió, cuánto dejó y cuánto se ganó (spec 0007), comparado
 * con el período anterior y con sus señales de control.
 *
 * <p>Solo lee. Las lecturas van en <b>una misma foto de la base</b> ({@code REPEATABLE_READ}): un abono que entra
 * entre la consulta de los cobros y la de los renglones no deja unas cifras que no suman.
 *
 * <p>El período anterior pasa por el <b>mismo cálculo</b>: la comparación no tiene su propia forma de sumar.
 */
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ConsultarResultados {

    private final RepositorioReportes reportes;
    private final Reloj reloj;

    public ConsultarResultados(RepositorioReportes reportes, Reloj reloj) {
        this.reportes = reportes;
        this.reloj = reloj;
    }

    /**
     * Los gastos del mes cuentan como lo dice cada uno (spec 0014, decisión 5): el reporte ya no lo decide.
     *
     * @throws com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException si el período está al revés,
     *         pasa de 366 días o termina después de hoy
     */
    public ReporteDeResultados ejecutar(LocalDate desde, LocalDate hasta, Actor actor) {
        actor.exigirAdministrador();
        LocalDate hoy = reloj.hoy();
        Periodo periodo = Periodo.pedido(desde, hasta, hoy);
        Periodo anterior = periodo.anterior();
        return new ReporteDeResultados(
                calcular(periodo, hoy),
                anterior,
                calcular(anterior, hoy).cifras(),
                Control.de(reportes.ventasAnuladas(periodo.inicio(), periodo.fin()),
                        reportes.turnosCerrados(periodo.inicio(), periodo.fin())),
                reportes.cartera(periodo.inicio(), periodo.fin()));
    }

    /** Lo cobrado en el período (spec 0014), con las ventas y los renglones de esos cobros, aunque sean de antes. */
    private ResultadosDelPeriodo calcular(Periodo periodo, LocalDate hoy) {
        List<CobroDeVenta> cobros = reportes.cobrosDeVentas(periodo.inicio(), periodo.fin());
        List<UUID> ids = cobros.stream().map(CobroDeVenta::ventaId).distinct().toList();
        return ResultadosDelPeriodo.calcular(periodo, reportes.ventasPorId(ids), reportes.renglonesDe(ids), cobros,
                reportes.gastos(periodo), hoy);
    }
}
