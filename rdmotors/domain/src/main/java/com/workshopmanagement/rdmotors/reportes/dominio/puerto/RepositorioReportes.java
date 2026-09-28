package com.workshopmanagement.rdmotors.reportes.dominio.puerto;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.workshopmanagement.rdmotors.reportes.dominio.CarteraDelPeriodo;
import com.workshopmanagement.rdmotors.reportes.dominio.CobroDeVenta;
import com.workshopmanagement.rdmotors.reportes.dominio.Control;
import com.workshopmanagement.rdmotors.reportes.dominio.GastoDelPeriodo;
import com.workshopmanagement.rdmotors.reportes.dominio.Periodo;
import com.workshopmanagement.rdmotors.reportes.dominio.RenglonVendido;
import com.workshopmanagement.rdmotors.reportes.dominio.VentaCobrada;

/**
 * PUERTO — lo que leen los reportes (spec 0007). Solo lectura, y solo filas: las sumas y las reglas viven en
 * {@link com.workshopmanagement.rdmotors.reportes.dominio.ResultadosDelPeriodo}.
 *
 * <p>Cobros, ventas y renglones se piden en la misma foto de la base, y los tres dejan fuera las anuladas: el ingreso y
 * el costo miden los mismos cobros.
 */
public interface RepositorioReportes {

    /**
     * Lo que entró por ventas no anuladas (spec 0014): lo que se pagó al cobrarlas y lo que los abonos vigentes les
     * aplicaron. Trae <b>todos</b> los cobros de las ventas que tienen alguno que cuenta en {@code [desde, hasta)},
     * también los de antes —hacen falta para repartir por acumulado—, y ninguno de después.
     */
    List<CobroDeVenta> cobrosDeVentas(Instant desde, Instant hasta);

    /** Esas ventas, por id, con su día, su total, su descuento y lo que quedó fiado. */
    List<VentaCobrada> ventasPorId(Collection<UUID> ids);

    /** Los renglones de esas ventas, con el costo con que salió cada uno del kardex. */
    List<RenglonVendido> renglonesDe(Collection<UUID> ids);

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
     * Lo cobrado en abonos en {@code [desde, hasta)} y lo que queda por cobrar <b>hoy</b> (spec 0008, RF-024), lo
     * vendido fiado en el período y lo que se cobró del saldo del cuaderno (spec 0014). Un abono anulado no cuenta.
     */
    CarteraDelPeriodo cartera(Instant desde, Instant hasta);
}
