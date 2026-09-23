package com.workshopmanagement.rdmotors.reportes.dominio;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * Las señales de control de un período (spec 0007, RF-023): las ventas anuladas y las diferencias de caja de los
 * turnos cerrados. Los descuentos ya están en las cifras.
 *
 * <p>No mueven la ganancia: una anulada no cuenta en nada (decisión 2), y la diferencia de caja es de la caja, no del
 * resultado. Se muestran para que se vean.
 *
 * @param faltantes lo que faltó en los turnos con diferencia negativa, en positivo
 * @param sobrantes lo que sobró en los turnos con diferencia positiva
 */
public record Control(int ventasAnuladas, Dinero montoAnuladas, List<TurnoCerrado> turnos, Dinero faltantes,
                      Dinero sobrantes) {

    /** Las ventas cobradas en el período que después se anularon, por su fecha de cobro. */
    public record VentasAnuladas(int cuantas, Dinero monto) {
    }

    /**
     * Un turno cerrado en el período.
     *
     * @param diferencia contado − esperado: negativa es faltante, positiva sobrante
     */
    public record TurnoCerrado(UUID id, Instant abiertoEn, Instant cerradoEn, Dinero esperado, Dinero contado,
                               Dinero diferencia) {
    }

    public static Control de(VentasAnuladas anuladas, List<TurnoCerrado> turnos) {
        Dinero faltantes = Dinero.CERO;
        Dinero sobrantes = Dinero.CERO;
        for (TurnoCerrado turno : turnos) {
            if (turno.diferencia().esNegativo()) {
                faltantes = faltantes.menos(turno.diferencia());
            } else {
                sobrantes = sobrantes.mas(turno.diferencia());
            }
        }
        return new Control(anuladas.cuantas(), anuladas.monto(), List.copyOf(turnos), faltantes, sobrantes);
    }
}
