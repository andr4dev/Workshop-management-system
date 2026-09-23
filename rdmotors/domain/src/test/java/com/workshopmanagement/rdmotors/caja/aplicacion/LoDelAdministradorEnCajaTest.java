package com.workshopmanagement.rdmotors.caja.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.workshopmanagement.rdmotors.caja.dominio.CategoriaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.FiltroGastos;
import com.workshopmanagement.rdmotors.caja.dominio.Gasto;
import com.workshopmanagement.rdmotors.caja.dominio.NaturalezaGasto;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;
import com.workshopmanagement.rdmotors.compras.aplicacion.ComandoRegistrarCompra;

/**
 * Lo de caja que es del administrador (spec 0004, §5 y RF-010): el gasto por fuera del cajón, las categorías de
 * gasto, la lista de gastos y las compras, aunque se paguen con el cajón. El cajero recibe "no permitido" y nada
 * cambia.
 */
class LoDelAdministradorEnCajaTest {

    private final EscenarioCaja tienda = new EscenarioCaja();
    private final Actor carolina = tienda.cajero;
    private final RegistrarCategoriaGasto registrarCategoria = new RegistrarCategoriaGasto(tienda.categorias);
    private final ActualizarCategoriaGasto actualizarCategoria = new ActualizarCategoriaGasto(tienda.categorias);
    private final DesactivarCategoriaGasto desactivarCategoria = new DesactivarCategoriaGasto(tienda.categorias);
    private final ConsultarGastos consultarGastos = new ConsultarGastos(tienda.gastos, tienda.turnos, tienda.usuarios);

    private static void noPermitido(Executable accion) {
        assertThatThrownBy(accion::execute).isInstanceOf(NoPermitidoException.class)
                .hasMessage("No permitido: es del administrador");
    }

    @Test
    @DisplayName("el cajero no registra ni anula gastos por fuera, no toca las categorías, no ve la lista de gastos ni registra compras")
    void elCajeroNo() {
        tienda.abrir(100_000);
        Gasto arriendo = tienda.arriendoPorNequi(800_000);
        int categorias = tienda.categorias.todas().size();
        FiltroGastos todos = new FiltroGastos(null, null, null);

        noPermitido(() -> tienda.registrarGasto.ejecutar(ComandoRegistrarGasto.porFuera(UUID.randomUUID(),
                tienda.arriendo.getId(), Dinero.de(800_000), "Arriendo", FormaPago.TRANSFERENCIA,
                tienda.nequi.getId(), tienda.reloj.hoy(), carolina)));
        noPermitido(() -> tienda.anularGasto.ejecutar(arriendo.getId(), "Se registró dos veces", carolina));
        noPermitido(() -> registrarCategoria.ejecutar("Publicidad", NaturalezaGasto.GASTO, carolina));
        noPermitido(() -> actualizarCategoria.ejecutar(tienda.fletes.getId(), "Fletes", true, carolina));
        noPermitido(() -> desactivarCategoria.ejecutar(tienda.fletes.getId(), carolina));
        noPermitido(() -> consultarGastos.listar(todos, 0, 25, carolina));
        noPermitido(() -> consultarGastos.totales(todos, carolina));
        noPermitido(() -> consultarGastos.detalle(arriendo.getId(), carolina));
        noPermitido(() -> tienda.registrarCompra.ejecutar(new ComandoRegistrarCompra(tienda.jotapartes.getId(),
                tienda.reloj.hoy(), null, FormaPago.EFECTIVO, null, carolina,
                java.util.List.of(ComandoRegistrarCompra.Linea.porTotal(tienda.repuesto(20_000).getId(), 1,
                        Dinero.de(10_000), null)), true)));

        assertThat(tienda.gastos.datos.values()).containsExactly(arriendo);
        assertThat(arriendo.estaAnulado()).isFalse();
        assertThat(tienda.categorias.todas()).hasSize(categorias);
        CategoriaGasto fletes = tienda.categorias.buscar(tienda.fletes.getId()).orElseThrow();
        assertThat(fletes.getNombre()).isEqualTo("Transporte y fletes");
        assertThat(fletes.isActiva()).isTrue();
        assertThat(fletes.isMensual()).isFalse();
        assertThat(tienda.compras.deCajaEnTurno(tienda.turnoAbierto().getId())).isEmpty();
    }

    @Test
    @DisplayName("el cajero ve un gasto del cajón de su turno, y el administrador cualquiera")
    void gastoDelCajonPropio() {
        tienda.abrir(100_000);
        Gasto flete = tienda.gastoDelCajon(15_000);

        assertThat(consultarGastos.detalle(flete.getId(), carolina)).isPresent();
        assertThat(consultarGastos.detalle(flete.getId(), tienda.administrador)).isPresent();
        assertThat(consultarGastos.listar(new FiltroGastos(null, null, null), 0, 25, tienda.administrador).total())
                .isEqualTo(1);
    }
}
