package com.workshopmanagement.rdmotors.caja.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.caja.dominio.ArqueoDeTurno;
import com.workshopmanagement.rdmotors.caja.dominio.Gasto;
import com.workshopmanagement.rdmotors.caja.dominio.Retiro;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;

/**
 * Lo que debería haber en el cajón (spec 0006, RF-011). La que más protege es {@link #elEjemploDelSpec()}: el
 * día del §2 da $88.400 y cada parte está en su renglón.
 */
class CalcularArqueoTest {

    private final EscenarioCaja tienda = new EscenarioCaja();

    private ArqueoDeTurno arqueo() {
        return tienda.calcularArqueo.de(tienda.turnoAbierto());
    }

    @Test
    @DisplayName("EL EJEMPLO DEL SPEC: debería haber $88.400, y cada parte en su renglón")
    void elEjemploDelSpec() {
        tienda.diaDelEjemplo();

        ArqueoDeTurno a = arqueo();

        assertThat(a.fondo()).isEqualTo(Dinero.de(100_000));
        assertThat(a.ventasEfectivo()).isEqualTo(Dinero.de(223_400));
        assertThat(a.ventasTransferencia()).isEqualTo(Dinero.de(111_000));
        assertThat(a.devolucionesEfectivo()).isEqualTo(Dinero.de(70_000));
        assertThat(a.gastosCajon()).isEqualTo(Dinero.de(15_000));
        assertThat(a.retiros()).isEqualTo(Dinero.de(100_000));
        assertThat(a.comprasCajon()).isEqualTo(Dinero.de(50_000));
        assertThat(a.esperado()).isEqualTo(Dinero.de(88_400));
    }

    @Test
    @DisplayName("la transferencia no entra al cajón, y en efectivo cuenta lo pagado, no lo recibido")
    void soloElEfectivoPagado() {
        tienda.abrir(100_000);
        tienda.vender(30_000, 20_000);

        assertThat(arqueo().ventasEfectivo()).isEqualTo(Dinero.de(30_000));
        assertThat(arqueo().ventasTransferencia()).isEqualTo(Dinero.de(20_000));
        assertThat(arqueo().esperado()).isEqualTo(Dinero.de(130_000));
    }

    @Test
    @DisplayName("una venta de OTRO turno anulada en este resta su efectivo aquí; una mixta, solo su parte en efectivo")
    void anuladaDeOtroTurno() {
        tienda.abrir(100_000);
        Venta enEfectivo = tienda.venderEnEfectivo(40_000);
        Venta mixta = tienda.vender(30_000, 20_000);
        tienda.cerrar(170_000);

        tienda.abrir(100_000);
        tienda.anular(enEfectivo);
        tienda.anular(mixta);

        assertThat(arqueo().ventasEfectivo()).isEqualTo(Dinero.CERO);
        assertThat(arqueo().devolucionesEfectivo()).isEqualTo(Dinero.de(70_000));
        assertThat(arqueo().esperado()).isEqualTo(Dinero.de(30_000));
    }

    @Test
    @DisplayName("una venta cobrada y anulada en el mismo turno suma y resta: no mueve lo que debería haber")
    void cobradaYAnuladaEnElMismoTurno() {
        tienda.abrir(100_000);
        tienda.anular(tienda.venderEnEfectivo(40_000));

        assertThat(arqueo().ventasEfectivo()).isEqualTo(Dinero.de(40_000));
        assertThat(arqueo().devolucionesEfectivo()).isEqualTo(Dinero.de(40_000));
        assertThat(arqueo().esperado()).isEqualTo(Dinero.de(100_000));
    }

    @Test
    @DisplayName("los gastos y retiros anulados no restan; un gasto por fuera del cajón tampoco")
    void anuladosYPorFueraNoRestan() {
        tienda.abrir(100_000);
        Gasto gasto = tienda.gastoDelCajon(15_000);
        Retiro retiro = tienda.retiro(20_000);
        tienda.arriendoPorNequi(800_000);
        assertThat(arqueo().esperado()).isEqualTo(Dinero.de(65_000));

        tienda.anularGasto.ejecutar(gasto.getId(), "era $1.500", tienda.cajero);
        tienda.anularRetiro.ejecutar(retiro.getId(), "se devolvió", tienda.cajero);

        assertThat(arqueo().gastosCajon()).isEqualTo(Dinero.CERO);
        assertThat(arqueo().retiros()).isEqualTo(Dinero.CERO);
        assertThat(arqueo().esperado()).isEqualTo(Dinero.de(100_000));
    }

    @Test
    @DisplayName("una compra en efectivo resta solo si se pagó con plata del cajón, y deja de restar si se anula")
    void comprasDeCaja() {
        tienda.abrir(100_000);
        Compra deCaja = tienda.compra(50_000, true);
        tienda.compra(30_000, false);
        assertThat(arqueo().comprasCajon()).isEqualTo(Dinero.de(50_000));

        tienda.anularCompra.ejecutar(deCaja.getId(), 0, "se registró dos veces", tienda.administrador);

        assertThat(arqueo().comprasCajon()).isEqualTo(Dinero.CERO);
        assertThat(arqueo().esperado()).isEqualTo(Dinero.de(100_000));
    }

    @Test
    @DisplayName("una fila de otro turno que se cuele en las listas no mueve el arqueo")
    void filtraPorElTurno() {
        tienda.abrir(100_000);
        Venta deOtro = tienda.venderEnEfectivo(40_000);
        Gasto gastoDeOtro = tienda.gastoDelCajon(10_000);
        Retiro retiroDeOtro = tienda.retiro(5_000);
        Compra compraDeOtro = tienda.compra(7_000, true);
        tienda.cerrar(0);
        TurnoCaja hoy = tienda.abrir(50_000);

        ArqueoDeTurno a = ArqueoDeTurno.calcular(hoy, List.of(deOtro), List.of(deOtro), List.of(gastoDeOtro),
                List.of(retiroDeOtro), List.of(compraDeOtro), List.of());

        assertThat(a.esperado()).isEqualTo(Dinero.de(50_000));
    }

    @Test
    @DisplayName("lo que debería haber es la suma de sus partes, aunque dé negativo")
    void esperadoSumaLasPartes() {
        ArqueoDeTurno a = new ArqueoDeTurno(Dinero.de(10), Dinero.de(5), Dinero.de(999), Dinero.de(999),
                Dinero.de(999), Dinero.de(3), Dinero.de(8), Dinero.de(999), Dinero.de(4), Dinero.de(6), Dinero.de(7));

        assertThat(a.esperado()).isEqualTo(Dinero.de(10 + 5 + 8 - 3 - 4 - 6 - 7));
    }
}
