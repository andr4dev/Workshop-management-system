package com.workshopmanagement.rdmotors.caja.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;

/** Ver un turno y los cerrados (spec 0006, RF-020 y RF-023). */
class ConsultarTurnosTest {

    private final EscenarioCaja tienda = new EscenarioCaja();

    @Test
    @DisplayName("el turno ABIERTO trae sus movimientos y lo que debería haber EN VIVO, con cada parte; sin cierre")
    void abiertoConArqueoEnVivo() {
        TurnoCaja turno = tienda.diaDelEjemplo();

        DetalleTurno detalle = tienda.consultarTurnos.detalle(turno.getId(), tienda.cajero).orElseThrow();

        assertThat(detalle.cierre()).isNull();
        assertThat(detalle.arqueo().esperado()).isEqualTo(Dinero.de(88_400));
        assertThat(detalle.arqueo().devolucionesEfectivo()).isEqualTo(Dinero.de(70_000));
        assertThat(detalle.arqueo().comprasCajon()).isEqualTo(Dinero.de(50_000));

        tienda.gastoDelCajon(8_400);
        assertThat(tienda.consultarTurnos.detalle(turno.getId(), tienda.cajero).orElseThrow().arqueo().esperado())
                .as("se recalcula al pedirlo").isEqualTo(Dinero.de(80_000));
        assertThat(detalle.gastos()).singleElement().satisfies(g -> assertThat(g.monto()).isEqualTo(Dinero.de(15_000)));
        assertThat(detalle.retiros()).singleElement().satisfies(r -> assertThat(r.motivo()).isEqualTo("Se lo llevó don Rubén"));
        assertThat(detalle.compras()).singleElement().satisfies(c -> {
            assertThat(c.total()).isEqualTo(Dinero.de(50_000));
            assertThat(c.proveedor()).isEqualTo("Importadora Jotapartes");
        });
        assertThat(detalle.ventas()).hasSize(2);
        assertThat(detalle.anuladasDeOtrosTurnos()).singleElement()
                .satisfies(v -> assertThat(v.efectivo()).isEqualTo(Dinero.de(70_000)));
    }

    @Test
    @DisplayName("el turno CERRADO trae las cifras guardadas al cerrar, y sus partes suman lo que debería haber")
    void cerradoConCifras() {
        TurnoCaja turno = tienda.diaDelEjemplo();
        tienda.cerrar(87_000);

        DetalleTurno cerrado = tienda.consultarTurnos.detalle(turno.getId(), tienda.cajero).orElseThrow();
        assertThat(cerrado.arqueo()).as("un cierre ya se firmó: no se recalcula").isNull();
        DetalleTurno.Cierre c = cerrado.cierre();

        assertThat(c.esperado()).isEqualTo(Dinero.de(88_400));
        assertThat(c.diferencia()).isEqualTo(Dinero.de(-1_400));
        assertThat(Dinero.de(100_000).mas(c.ventasEfectivo()).menos(c.devolucionesEfectivo()).menos(c.gastosCajon())
                .menos(c.retiros()).menos(c.comprasCajon())).isEqualTo(c.esperado());
    }

    @Test
    @DisplayName("una venta del turno anulada en el mismo turno sale en sus ventas y no entre las de otros turnos")
    void anuladaEnElMismoTurno() {
        TurnoCaja turno = tienda.abrir(100_000);
        Venta venta = tienda.venderEnEfectivo(10_000);
        tienda.anular(venta);

        DetalleTurno detalle = tienda.consultarTurnos.detalle(turno.getId(), tienda.cajero).orElseThrow();

        assertThat(detalle.ventas()).singleElement().satisfies(v -> assertThat(v.anuladaEn()).isNotNull());
        assertThat(detalle.anuladasDeOtrosTurnos()).isEmpty();
    }

    @Test
    @DisplayName("los cerrados, del cierre más reciente al más antiguo; el abierto no está")
    void cerrados() {
        TurnoCaja primero = tienda.abrir(100_000);
        tienda.cerrar(100_000);
        TurnoCaja segundo = tienda.abrir(50_000);
        tienda.cerrar(50_000);
        tienda.abrir(10_000);

        assertThat(tienda.consultarTurnos.cerrados(0, 25, tienda.cajero).elementos()).extracting(TurnoCaja::getId)
                .containsExactly(segundo.getId(), primero.getId());
        assertThat(tienda.consultarTurnos.detalle(UUID.randomUUID(), tienda.cajero)).isEmpty();
    }
}
