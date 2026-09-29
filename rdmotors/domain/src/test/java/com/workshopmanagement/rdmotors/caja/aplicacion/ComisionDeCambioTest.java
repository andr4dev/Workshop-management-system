package com.workshopmanagement.rdmotors.caja.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.caja.dominio.CategoriaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.Gasto;
import com.workshopmanagement.rdmotors.caja.dominio.NaturalezaGasto;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.Rol;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ComandoCobrarVenta;
import com.workshopmanagement.rdmotors.ventas.dominio.CambioDeAceite;
import com.workshopmanagement.rdmotors.ventas.dominio.LineaVenta;
import com.workshopmanagement.rdmotors.ventas.dominio.RenglonesConProblemaException;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;

/**
 * El cambio de aceite en la venta (spec 0015, versión 2): el cajero escoge siempre; <i>no se cambia</i> baja el precio
 * la comisión; <i>se cambia</i> saca la comisión del cajón al cobrar, como un gasto de costo a nombre de quien cambió.
 */
class ComisionDeCambioTest {

    private static final long FONDO = 100_000;

    private final EscenarioCaja tienda = new EscenarioCaja();
    private CategoriaGasto comisiones;
    private Usuario gustavo;
    private Variante motul;

    @BeforeEach
    void preparar() {
        comisiones = tienda.categorias.sembrar(CategoriaGasto.nueva(PagarComisionDeCambio.CATEGORIA,
                NaturalezaGasto.COSTO));
        gustavo = tienda.usuarios.sembrar(Usuario.nuevo("gustavo123", "Gustavo Bravo", Rol.CAJERO, "{noop}x", false,
                Instant.parse("2026-09-01T12:00:00Z")));
        motul = tienda.repuesto(65_000);
        motul.cambiarComisionDeCambio(Dinero.de(3_000));
        tienda.abrir(FONDO);
    }

    private Venta cobrar(Variante variante, int cantidad, long precioVisto, CambioDeAceite cambio, UUID quien) {
        long total = precioVisto * cantidad;
        return tienda.cobrarVenta.ejecutar(new ComandoCobrarVenta(UUID.randomUUID(),
                List.of(new ComandoCobrarVenta.Renglon(variante.getId(), cantidad, precioVisto, cambio, quien)), null,
                List.of(new ComandoCobrarVenta.Pago(FormaPago.EFECTIVO, total, null)), tienda.cajero)).venta();
    }

    private Dinero esperado() {
        return tienda.calcularArqueo.de(tienda.turnoAbierto()).esperado();
    }

    @Test
    @DisplayName("SE CAMBIA: se cobran $65.000 y los $3.000 salen del cajón como un gasto de costo a nombre de Gustavo")
    void seCambia() {
        Venta venta = cobrar(motul, 1, 65_000, CambioDeAceite.SE_CAMBIA, gustavo.getId());

        assertThat(venta.getTotal()).isEqualTo(Dinero.de(65_000));
        LineaVenta linea = venta.getLineas().getFirst();
        assertThat(linea.getComision()).isEqualTo(Dinero.de(3_000));
        assertThat(linea.getCambioPorId()).isEqualTo(gustavo.getId());
        assertThat(tienda.gastos.datos.values()).singleElement().satisfies(g -> {
            assertThat(g.getId()).isEqualTo(linea.getComisionGastoId());
            assertThat(g.getMonto()).isEqualTo(Dinero.de(3_000));
            assertThat(g.isDelCajon()).isTrue();
            assertThat(g.getCategoria()).isEqualTo(comisiones);
            assertThat(g.getDescripcion()).isEqualTo("venta N.º " + venta.getNumero() + " · "
                    + motul.getProducto().getNombre() + " · Gustavo Bravo");
        });
        assertThat(esperado()).as("entró la venta y salió la comisión").isEqualTo(Dinero.de(FONDO + 65_000 - 3_000));
    }

    @Test
    @DisplayName("NO SE CAMBIA: se cobran $62.000, sin gasto y sin persona")
    void noSeCambia() {
        Venta venta = cobrar(motul, 1, 62_000, CambioDeAceite.NO_SE_CAMBIA, null);

        assertThat(venta.getTotal()).isEqualTo(Dinero.de(62_000));
        LineaVenta linea = venta.getLineas().getFirst();
        assertThat(linea.getPrecioUnitario()).isEqualTo(Dinero.de(62_000));
        assertThat(linea.getComision()).as("lo que se descontó").isEqualTo(Dinero.de(3_000));
        assertThat(linea.getCambioPorId()).isNull();
        assertThat(tienda.gastos.datos).isEmpty();
        assertThat(esperado()).isEqualTo(Dinero.de(FONDO + 62_000));
    }

    @Test
    @DisplayName("dos aceites que se cambian: un gasto de $6.000 que dice cuántos")
    void dosUnidades() {
        Venta venta = cobrar(motul, 2, 65_000, CambioDeAceite.SE_CAMBIA, gustavo.getId());

        assertThat(venta.getTotal()).isEqualTo(Dinero.de(130_000));
        Gasto gasto = tienda.gastos.datos.values().iterator().next();
        assertThat(gasto.getMonto()).isEqualTo(Dinero.de(6_000));
        assertThat(gasto.getDescripcion()).contains("· 2 × ");
    }

    @Test
    @DisplayName("sin escoger no se cobra; el precio visto tiene que ser el de lo que se escogió")
    void loQueNoSeCobra() {
        assertThatThrownBy(() -> cobrar(motul, 1, 65_000, null, null))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessageContaining("se cambia aquí o no");
        assertThatThrownBy(() -> cobrar(motul, 1, 65_000, CambioDeAceite.NO_SE_CAMBIA, null))
                .as("sin cambio son $62.000").isInstanceOf(RenglonesConProblemaException.class);
        assertThatThrownBy(() -> cobrar(motul, 1, 65_000, CambioDeAceite.SE_CAMBIA, null))
                .hasMessageContaining("quién le cambió el aceite");
        assertThat(tienda.gastos.datos).isEmpty();
    }

    @Test
    @DisplayName("un repuesto que no paga comisión no se escoge; y quien no es de la tienda no cobra la comisión")
    void loQueNoCabe() {
        Variante filtro = tienda.repuesto(8_000);
        assertThatThrownBy(() -> cobrar(filtro, 1, 8_000, CambioDeAceite.SE_CAMBIA, gustavo.getId()))
                .hasMessageContaining("no paga comisión");
        assertThatThrownBy(() -> cobrar(motul, 1, 65_000, CambioDeAceite.SE_CAMBIA, UUID.randomUUID()))
                .hasMessageContaining("no es alguien activo");
    }

    @Test
    @DisplayName("ANULAR en el mismo turno: la comisión vuelve al cajón; en otro turno, se queda (decisión 7)")
    void anular() {
        Venta hoy = cobrar(motul, 1, 65_000, CambioDeAceite.SE_CAMBIA, gustavo.getId());
        tienda.anularVenta.ejecutar(hoy.getId(), "El cliente se arrepintió", tienda.cajero);

        Gasto devuelta = tienda.gastos.datos.values().iterator().next();
        assertThat(devuelta.estaAnulado()).isTrue();
        assertThat(devuelta.getMotivoAnulacion()).isEqualTo("Se anuló la venta N.º " + hoy.getNumero());
        assertThat(esperado()).as("la venta y su comisión se deshicieron").isEqualTo(Dinero.de(FONDO));

        Venta ayer = cobrar(motul, 1, 65_000, CambioDeAceite.SE_CAMBIA, gustavo.getId());
        UUID gastoDeAyer = ayer.getLineas().getFirst().getComisionGastoId();
        tienda.cerrar(esperado().valor().longValueExact());
        tienda.abrir(FONDO);
        tienda.anularVenta.ejecutar(ayer.getId(), "Se equivocó de aceite", tienda.cajero);

        assertThat(tienda.gastos.datos.get(gastoDeAyer).estaAnulado())
                .as("salió en un turno que ya cuadró").isFalse();
    }

    @Test
    @DisplayName("la comisión es la del repuesto al cobrar: cambiarla después no toca lo que ya se pagó")
    void laDelMomento() {
        Venta venta = cobrar(motul, 1, 65_000, CambioDeAceite.SE_CAMBIA, gustavo.getId());
        motul.cambiarComisionDeCambio(Dinero.de(4_000));

        assertThat(venta.getLineas().getFirst().getComision()).isEqualTo(Dinero.de(3_000));
        assertThat(tienda.gastos.datos.values().iterator().next().getMonto()).isEqualTo(Dinero.de(3_000));
    }
}
