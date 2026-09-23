package com.workshopmanagement.rdmotors.caja.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.workshopmanagement.rdmotors.caja.dominio.Gasto;
import com.workshopmanagement.rdmotors.caja.dominio.Retiro;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoAjenoException;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ComandoCobrarVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ConsultarVentas;
import com.workshopmanagement.rdmotors.ventas.dominio.EstadoVenta;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;

/**
 * El turno es de quien lo abrió (spec 0004, decisión 2, RF-012). Carolina abre; Andrés, otro cajero, no mueve nada
 * en ese turno; el administrador sí, y lo que hace queda a su nombre.
 */
class TurnoConDuenoTest {

    private final EscenarioCaja tienda = new EscenarioCaja();
    private final Actor carolina = tienda.cajero;
    private final Actor andres = ActoresDePrueba.cajero("Andrés");

    private ComandoCobrarVenta venta(Variante v, Actor quien) {
        long precio = v.getPrecio().valor().longValueExact();
        return new ComandoCobrarVenta(UUID.randomUUID(), List.of(new ComandoCobrarVenta.Renglon(v.getId(), 1, precio)),
                null, List.of(new ComandoCobrarVenta.Pago(FormaPago.EFECTIVO, precio, null)), quien);
    }

    private void esAjeno(Executable accion) {
        assertThatThrownBy(accion::execute)
                .isInstanceOfSatisfying(TurnoAjenoException.class,
                        e -> assertThat(e.getAbiertoPorId()).isEqualTo(carolina.id()))
                .isInstanceOf(NoPermitidoException.class);
    }

    @Test
    @DisplayName("otro cajero no vende, no registra gastos ni retiros, no anula y no cierra en el turno de Carolina, y nada cambia")
    void otroCajeroNoOpera() {
        TurnoCaja turno = tienda.abrir(100_000);
        Variante filtro = tienda.repuesto(13_000);
        Venta deCarolina = tienda.venderEnEfectivo(20_000);
        Gasto flete = tienda.gastoDelCajon(5_000);
        Retiro retiro = tienda.retiro(10_000);
        int stock = filtro.getStock();

        esAjeno(() -> tienda.cobrarVenta.ejecutar(venta(filtro, andres)));
        esAjeno(() -> tienda.anularVenta.ejecutar(deCarolina.getId(), "Cliente se arrepintió", andres));
        esAjeno(() -> tienda.registrarGasto.ejecutar(ComandoRegistrarGasto.delCajon(UUID.randomUUID(),
                tienda.fletes.getId(), Dinero.de(3_000), "Flete", andres)));
        esAjeno(() -> tienda.anularGasto.ejecutar(flete.getId(), "Se registró dos veces", andres));
        esAjeno(() -> tienda.registrarRetiro.ejecutar(UUID.randomUUID(), Dinero.de(5_000), "Me lo llevo", false, andres));
        esAjeno(() -> tienda.anularRetiro.ejecutar(retiro.getId(), "No era", andres));
        esAjeno(() -> tienda.cerrarTurno.ejecutar(turno.getId(), Dinero.de(85_000), andres));

        assertThat(tienda.ventas.datos).hasSize(1);
        assertThat(filtro.getStock()).isEqualTo(stock);
        assertThat(deCarolina.getEstado()).isEqualTo(EstadoVenta.COBRADA);
        assertThat(tienda.gastos.datos.values()).extracting(Gasto::estaAnulado).containsExactly(false);
        assertThat(tienda.retiros.datos.values()).extracting(Retiro::estaAnulado).containsExactly(false);
        assertThat(tienda.turnoAbierto().getId()).isEqualTo(turno.getId());
    }

    @Test
    @DisplayName("otro cajero tampoco escribe las observaciones del cierre de Carolina")
    void observacionesAjenas() {
        TurnoCaja turno = tienda.abrir(100_000);
        tienda.cerrar(100_000);

        esAjeno(() -> tienda.escribirObservaciones.ejecutar(turno.getId(), "Faltó plata", andres));
        assertThat(tienda.turnos.buscar(turno.getId()).orElseThrow().getObservaciones()).isNull();
    }

    @Test
    @DisplayName("el administrador vende y cierra en el turno de Carolina, y queda a su nombre")
    void elAdministradorSi() {
        TurnoCaja turno = tienda.abrir(100_000);
        Variante filtro = tienda.repuesto(13_000);
        Actor ruben = tienda.administrador;

        Venta venta = tienda.cobrarVenta.ejecutar(venta(filtro, ruben)).venta();
        TurnoCaja cerrado = tienda.cerrarTurno.ejecutar(turno.getId(), Dinero.de(113_000), ruben);

        assertThat(venta.getTurnoId()).isEqualTo(turno.getId());
        assertThat(venta.getVendidoPorId()).isEqualTo(ruben.id());
        assertThat(cerrado.getCerradoPorId()).isEqualTo(ruben.id());
        assertThat(cerrado.getAbiertoPorId()).isEqualTo(carolina.id());
    }

    @Test
    @DisplayName("el cajero ve sus turnos cerrados y el detalle de los suyos; el de otro, no. El administrador, todos")
    void quienVeLosTurnos() {
        TurnoCaja deCarolina = tienda.abrir(100_000);
        tienda.cerrar(100_000);
        TurnoCaja deAndres = tienda.abrirTurno.ejecutar(Dinero.de(50_000), andres);
        tienda.cerrarTurno.ejecutar(deAndres.getId(), Dinero.de(50_000), andres);

        assertThat(tienda.consultarTurnos.cerrados(0, 25, carolina).elementos()).extracting(TurnoCaja::getId)
                .containsExactly(deCarolina.getId());
        assertThat(tienda.consultarTurnos.cerrados(0, 25, tienda.administrador).elementos()).hasSize(2);
        assertThat(tienda.consultarTurnos.detalle(deCarolina.getId(), carolina)).isPresent();
        assertThatThrownBy(() -> tienda.consultarTurnos.detalle(deAndres.getId(), carolina))
                .isInstanceOf(NoPermitidoException.class);
        assertThat(tienda.consultarTurnos.detalle(deAndres.getId(), tienda.administrador)).isPresent();
    }

    @Test
    @DisplayName("el cajero ve las ventas de sus turnos; las de un turno ajeno, no. El administrador, todas")
    void quienVeLasVentas() {
        ConsultarVentas consultar = new ConsultarVentas(tienda.ventas, tienda.turnos, new Falsos.UsuariosEnMemoria(),
                tienda.clientes, tienda.deudas);
        tienda.abrir(100_000);
        Venta deCarolina = tienda.venderEnEfectivo(20_000);

        assertThat(consultar.delTurnoAbierto(carolina)).hasSize(1);
        assertThat(consultar.delTurnoAbierto(andres)).isEmpty();
        assertThat(consultar.delTurnoAbierto(tienda.administrador)).hasSize(1);
        assertThat(consultar.detalle(deCarolina.getId(), carolina)).isPresent();
        assertThat(consultar.detalle(deCarolina.getId(), tienda.administrador)).isPresent();
        assertThatThrownBy(() -> consultar.detalle(deCarolina.getId(), andres)).isInstanceOf(NoPermitidoException.class);
        assertThatThrownBy(() -> consultar.porNumero(deCarolina.getNumero(), andres))
                .isInstanceOf(NoPermitidoException.class);
    }
}
