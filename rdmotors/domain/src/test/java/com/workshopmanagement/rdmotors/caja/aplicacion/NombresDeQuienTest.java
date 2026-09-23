package com.workshopmanagement.rdmotors.caja.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.caja.dominio.Gasto;
import com.workshopmanagement.rdmotors.caja.dominio.Retiro;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.Persona;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ConsultarVentas;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;

/**
 * Los detalles dicen el <b>nombre</b> de quién hizo cada cosa (spec 0004, RF-022), no un id que no le dice nada a
 * nadie. Y los nombres se piden <b>una sola vez por respuesta</b>: un turno con veinte movimientos no son veinte
 * consultas.
 */
class NombresDeQuienTest {

    private final EscenarioCaja tienda = new EscenarioCaja();

    @Test
    @DisplayName("el turno dice quién lo abrió y quién lo cerró, y cada gasto y retiro quién lo registró y quién lo anuló")
    void elTurnoDiceLosNombres() {
        TurnoCaja turno = tienda.abrir(100_000);                 // Carolina
        Gasto flete = tienda.gastoDelCajon(15_000);              // Carolina
        Retiro retiro = tienda.retiro(50_000);                   // Carolina
        tienda.anularGasto.ejecutar(flete.getId(), "Se registró dos veces", tienda.administrador);
        tienda.anularRetiro.ejecutar(retiro.getId(), "No era", tienda.administrador);
        tienda.cerrarTurno.ejecutar(turno.getId(), Dinero.de(100_000), tienda.administrador);

        DetalleTurno detalle = tienda.consultarTurnos.detalle(turno.getId(), tienda.administrador).orElseThrow();

        assertThat(detalle.abiertoPor()).isEqualTo(new Persona(tienda.cajero.id(), tienda.cajero.nombre()));
        assertThat(detalle.cerradoPor().nombre()).isEqualTo(tienda.administrador.nombre());
        assertThat(detalle.gastos()).singleElement().satisfies(g -> {
            assertThat(g.registradoPor().nombre()).isEqualTo(tienda.cajero.nombre());
            assertThat(g.anuladoPor().nombre()).isEqualTo(tienda.administrador.nombre());
        });
        assertThat(detalle.retiros()).singleElement().satisfies(r -> {
            assertThat(r.registradoPor().nombre()).isEqualTo(tienda.cajero.nombre());
            assertThat(r.anuladoPor().nombre()).isEqualTo(tienda.administrador.nombre());
        });
    }

    @Test
    @DisplayName("los nombres del turno se piden en una sola consulta, no una por movimiento")
    void unaSolaConsultaDeNombres() {
        TurnoCaja turno = tienda.abrir(100_000);
        for (int i = 0; i < 5; i++) {
            tienda.gastoDelCajon(1_000);
            tienda.retiro(1_000);
        }
        int antes = tienda.usuarios.vecesPedidosLosNombres;

        tienda.consultarTurnos.detalle(turno.getId(), tienda.administrador).orElseThrow();

        assertThat(tienda.usuarios.vecesPedidosLosNombres).isEqualTo(antes + 1);
    }

    @Test
    @DisplayName("la venta dice quién la cobró y quién la anuló")
    void laVentaDiceLosNombres() {
        ConsultarVentas consultar = new ConsultarVentas(tienda.ventas, tienda.turnos, tienda.usuarios, tienda.clientes,
                tienda.deudas);
        tienda.abrir(100_000);
        Venta venta = tienda.venderEnEfectivo(20_000);            // Carolina
        tienda.anularVenta.ejecutar(venta.getId(), "Cliente se arrepintió", tienda.administrador);

        var detalle = consultar.detalle(venta.getId(), tienda.administrador).orElseThrow();

        assertThat(detalle.vendidoPor().nombre()).isEqualTo(tienda.cajero.nombre());
        assertThat(detalle.anuladaPor().nombre()).isEqualTo(tienda.administrador.nombre());
    }

    @Test
    @DisplayName("una venta sin anular no trae quién la anuló: ese dato no existe todavía")
    void sinAnularNoHayQuienAnule() {
        ConsultarVentas consultar = new ConsultarVentas(tienda.ventas, tienda.turnos, tienda.usuarios, tienda.clientes,
                tienda.deudas);
        tienda.abrir(100_000);
        Venta venta = tienda.venderEnEfectivo(20_000);

        assertThat(consultar.detalle(venta.getId(), tienda.cajero).orElseThrow().anuladaPor()).isNull();
    }
}
