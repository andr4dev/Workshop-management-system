package com.workshopmanagement.rdmotors.compras.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.EstadoCompra;
import com.workshopmanagement.rdmotors.compras.dominio.FiltroCompras;
import com.workshopmanagement.rdmotors.inventario.aplicacion.ActualizarRepuesto;
import com.workshopmanagement.rdmotors.inventario.aplicacion.ComandoCrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/**
 * Compras, proveedores, cuentas y la ficha del repuesto son del administrador (spec 0004, §5 y RF-010): una compra
 * es precio de costo. El cajero recibe "no permitido" y nada cambia: ni la compra, ni el stock, ni el precio.
 */
class LoDelAdministradorEnComprasTest {

    private final EscenarioCompras t = new EscenarioCompras();
    private final Actor carolina = ActoresDePrueba.cajero();
    private final ConsultarCompras consultar = new ConsultarCompras(t.compras, t.auditoria, t.usuarios);
    private final ActualizarRepuesto actualizarRepuesto = new ActualizarRepuesto(t.variantes, t.categorias,
            t.auditoria, t.reloj);

    private static void noPermitido(Executable accion) {
        assertThatThrownBy(accion::execute).isInstanceOf(NoPermitidoException.class)
                .hasMessage("No permitido: es del administrador");
    }

    @Test
    @DisplayName("el cajero no registra, corrige ni anula compras, ni crea o corrige repuestos, proveedores o cuentas; y nada cambia")
    void elCajeroNo() {
        Variante filtro = t.repuesto("352B59K", 13_000);
        Compra compra = t.comprar(EscenarioCompras.unidades(filtro, 5, 8_000));
        int stock = filtro.getStock();
        long compras = t.compras.historial(FiltroCompras.sinFiltros(), 0, 100).total();

        noPermitido(() -> t.registrar.ejecutar(new ComandoRegistrarCompra(t.jotapartes.getId(),
                LocalDate.of(2026, 9, 1), "FV-CAJERO", FormaPago.EFECTIVO, null, carolina,
                List.of(EscenarioCompras.unidades(filtro, 3, 8_000)))));
        noPermitido(() -> t.corregir.ejecutar(new ComandoCorregirCompra(compra.getId(), 0,
                "número mal", carolina, null, null, "FV-OTRO", null, null, null)));
        noPermitido(() -> t.anular.ejecutar(compra.getId(), 0, "se registró dos veces", carolina));
        noPermitido(() -> t.crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo("PASTILLAS", null, null,
                "152RTX2B", "CBI", Dinero.de(12_000), 1), carolina));
        noPermitido(() -> actualizarRepuesto.ejecutar(filtro.getId(), new ActualizarRepuesto.ComandoActualizarRepuesto(
                "FILTRO", null, null, "352B59K", "INOKI", Dinero.de(1_000), 5, carolina)));
        noPermitido(() -> new RegistrarProveedor(t.proveedores).ejecutar("Importadora Nueva", null, null, carolina));
        noPermitido(() -> new RegistrarCuenta(t.cuentas).ejecutar("Daviplata", carolina));
        noPermitido(() -> new DesactivarCuenta(t.cuentas).ejecutar(t.nequi.getId(), carolina));

        assertThat(t.compras.historial(FiltroCompras.sinFiltros(), 0, 100).total()).isEqualTo(compras);
        Compra igual = t.compras.buscar(compra.getId()).orElseThrow();
        assertThat(igual.getEstado()).isEqualTo(EstadoCompra.VIGENTE);
        assertThat(igual.getNumeroFactura()).isNotEqualTo("FV-OTRO");
        assertThat(filtro.getStock()).isEqualTo(stock);
        assertThat(filtro.getPrecio()).isEqualTo(Dinero.de(13_000));
        assertThat(t.variantes.buscarPorCodigo("152RTX2B")).isEmpty();
        assertThat(t.proveedores.activos()).noneMatch(p -> p.getNombre().equals("IMPORTADORA NUEVA")
                || p.getNombre().equals("Importadora Nueva"));
        assertThat(t.cuentas.activas()).extracting(c -> c.getNombre()).containsExactly(t.nequi.getNombre());
    }

    @Test
    @DisplayName("el cajero no ve el historial, los totales ni el detalle de las compras")
    void elCajeroNoLasVe() {
        Variante filtro = t.repuesto("352B59K", 13_000);
        Compra compra = t.comprar(EscenarioCompras.unidades(filtro, 5, 8_000));

        noPermitido(() -> consultar.historial(FiltroCompras.sinFiltros(), 0, 25, carolina));
        noPermitido(() -> consultar.historialConCoincidencias(FiltroCompras.sinFiltros(), 0, 25, carolina));
        noPermitido(() -> consultar.totales(FiltroCompras.sinFiltros(), carolina));
        noPermitido(() -> consultar.detalle(compra.getId(), carolina));
        assertThat(consultar.detalle(compra.getId(), t.usuario)).isPresent();
    }
}
