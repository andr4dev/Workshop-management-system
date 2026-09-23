package com.workshopmanagement.rdmotors.reportes.dominio.puerto;

import java.time.Instant;
import java.util.List;

import com.workshopmanagement.rdmotors.reportes.dominio.CarteraDelPeriodo;
import com.workshopmanagement.rdmotors.reportes.dominio.Control;
import com.workshopmanagement.rdmotors.reportes.dominio.GastoDelPeriodo;
import com.workshopmanagement.rdmotors.reportes.dominio.Periodo;
import com.workshopmanagement.rdmotors.reportes.dominio.RenglonVendido;
import com.workshopmanagement.rdmotors.reportes.dominio.VentaCobrada;

/**
 * PUERTO — lo que leen los reportes (spec 0007). Solo lectura, y solo filas: las sumas y las reglas viven en
 * {@link com.workshopmanagement.rdmotors.reportes.dominio.ResultadosDelPeriodo}.
 *
 * <p>Ventas y renglones se piden con el mismo intervalo y el mismo filtro de anuladas: el ingreso y el costo miden
 * las mismas ventas.
 */
public interface RepositorioReportes {

    /**
     * Las ventas <b>cobradas y no anuladas</b> cobradas en {@code [desde, hasta)}, con su día de Colombia y lo que se
     * pagó en efectivo y por transferencia.
     */
    List<VentaCobrada> ventasCobradas(Instant desde, Instant hasta);

    /** Los renglones de esas mismas ventas, con el costo con que salió cada uno del kardex. */
    List<RenglonVendido> renglonesVendidos(Instant desde, Instant hasta);

    /**
     * Los gastos no anulados que tocan el período: los que no son del mes, con fecha en el período; los del mes, con
     * fecha en cualquier mes que el período toque.
     */
    List<GastoDelPeriodo> gastos(Periodo periodo);

    /** Cuántas ventas cobradas en {@code [desde, hasta)} están anuladas hoy, y por cuánto (RF-023). */
    Control.VentasAnuladas ventasAnuladas(Instant desde, Instant hasta);

    /** Los turnos cerrados en {@code [desde, hasta)}, con su diferencia, del más antiguo al más reciente (RF-023). */
    List<Control.TurnoCerrado> turnosCerrados(Instant desde, Instant hasta);

    /**
     * Lo cobrado en abonos en {@code [desde, hasta)} y lo que queda por cobrar <b>hoy</b> (spec 0008, RF-024). Un
     * abono anulado no cuenta.
     */
    CarteraDelPeriodo cartera(Instant desde, Instant hasta);
}
