package com.workshopmanagement.rdmotors.caja.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.clientes.dominio.DatosCliente;
import com.workshopmanagement.rdmotors.clientes.dominio.Deuda;
import com.workshopmanagement.rdmotors.clientes.dominio.EstadoDeuda;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ComandoCobrarVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ResultadoCobro;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;

/**
 * Fiar al cobrar, de punta a punta (spec 0008, H1, H2 y H9): la venta, el stock, la deuda del cliente y lo que espera
 * el cajón.
 */
class FiadoEnElCajonTest {

    private final EscenarioCaja tienda = new EscenarioCaja();

    @Test
    @DisplayName("fiar todo: baja el stock, queda la deuda y el cajón no espera esos $50.000")
    void fiarTodo() {
        tienda.abrir(100_000);
        Cliente juan = tienda.cliente("Juan Pérez", "1234567");

        Venta venta = tienda.fiar(juan, 50_000, 0);

        assertThat(venta.getFiado()).isEqualTo(Dinero.de(50_000));
        assertThat(venta.getClienteId()).isEqualTo(juan.getId());
        assertThat(venta.getLineas().getFirst().getVariante().getStock()).isEqualTo(99);
        Deuda deuda = tienda.deudas.deLaVenta(venta.getId()).orElseThrow();
        assertThat(deuda.getMonto()).isEqualTo(Dinero.de(50_000));
        assertThat(deuda.getNumeroVenta()).isEqualTo(venta.getNumero());
        assertThat(deuda.getDebeDespues()).isEqualTo(Dinero.de(50_000));
        assertThat(deuda.getRegistradaPorId()).isEqualTo(tienda.cajero.id());
        assertThat(tienda.calcularArqueo.de(tienda.turnoAbierto()).esperado()).isEqualTo(Dinero.de(100_000));
    }

    @Test
    @DisplayName("fiar una parte: de $80.000, $30.000 en efectivo y $50.000 fiados; el cajón espera solo los $30.000")
    void fiarUnaParte() {
        tienda.abrir(100_000);
        Cliente juan = tienda.cliente("Juan Pérez", "1234567");

        tienda.fiar(juan, 80_000, 30_000);

        assertThat(tienda.calcularArqueo.de(tienda.turnoAbierto()).esperado()).isEqualTo(Dinero.de(130_000));
        assertThat(tienda.deudas.debeDe(List.of(juan.getId()))).containsEntry(juan.getId(), Dinero.de(50_000));
    }

    @Test
    @DisplayName("SE LE FÍA A QUIEN SOLO DIO SU NOMBRE (decisión 2, cambiada el 2026-09-21): la deuda queda igual de buena")
    void seLeFiaConSoloElNombre() {
        tienda.abrir(100_000);
        Cliente soloNombre = tienda.clientes.sembrar(Cliente.nuevo(new DatosCliente("Pedro", null, null, null, null),
                tienda.cajero.id(), tienda.reloj.ahora()));
        Variante filtro = tienda.repuesto(20_000);

        tienda.cobrarVenta.ejecutar(comandoFiado(filtro, soloNombre, 20_000));

        assertThat(tienda.deudas.debeDe(List.of(soloNombre.getId())))
                .containsEntry(soloNombre.getId(), Dinero.de(20_000));
        assertThat(filtro.getStock()).isEqualTo(99);
        // Lo que falta no desaparece: queda dicho para completarlo cuando se pueda.
        assertThat(soloNombre.datosQueFaltan()).containsExactly("la cédula", "el celular");
    }

    @Test
    @DisplayName("a quien se le cerró el fiado no se le fía; de contado a su nombre, sí")
    void fiadoCerrado() {
        tienda.abrir(100_000);
        Cliente juan = tienda.cliente("Juan Pérez", "1234567");
        juan.cerrarFiado("No paga desde julio");
        Variante filtro = tienda.repuesto(20_000);

        assertThatThrownBy(() -> tienda.cobrarVenta.ejecutar(comandoFiado(filtro, juan, 20_000)))
                .hasMessageContaining("no se le fía");

        Venta deContado = tienda.cobrarVenta.ejecutar(new ComandoCobrarVenta(UUID.randomUUID(),
                List.of(new ComandoCobrarVenta.Renglon(filtro.getId(), 1, 20_000)), null,
                List.of(new ComandoCobrarVenta.Pago(FormaPago.EFECTIVO, 20_000, null)),
                juan.getId(), 0, tienda.cajero)).venta();
        assertThat(deContado.getClienteId()).isEqualTo(juan.getId());
        assertThat(tienda.deudas.datos).isEmpty();
    }

    @Test
    @DisplayName("el mismo cobro dos veces (misma llave) fía una sola vez")
    void llaveRepetidaNoFiaDosVeces() {
        tienda.abrir(100_000);
        Cliente juan = tienda.cliente("Juan Pérez", "1234567");
        ComandoCobrarVenta comando = comandoFiado(tienda.repuesto(20_000), juan, 20_000);

        ResultadoCobro primero = tienda.cobrarVenta.ejecutar(comando);
        ResultadoCobro segundo = tienda.cobrarVenta.ejecutar(comando);

        assertThat(segundo.repetida()).isTrue();
        assertThat(segundo.venta().getId()).isEqualTo(primero.venta().getId());
        assertThat(tienda.deudas.datos).hasSize(1);
    }

    @Test
    @DisplayName("fiar bloquea al cliente: todo lo que cambia lo que debe pasa por su candado")
    void fiarBloqueaAlCliente() {
        tienda.abrir(100_000);
        Cliente juan = tienda.cliente("Juan Pérez", "1234567");
        int antes = tienda.clientes.vecesBloqueado;

        tienda.fiar(juan, 20_000, 0);

        assertThat(tienda.clientes.vecesBloqueado).isGreaterThan(antes);
    }

    @Test
    @DisplayName("anular una venta fiada devuelve el stock y anula la deuda; por la parte fiada no sale plata del cajón")
    void anularVentaFiada() {
        tienda.abrir(100_000);
        Cliente juan = tienda.cliente("Juan Pérez", "1234567");
        Venta venta = tienda.fiar(juan, 80_000, 30_000);

        tienda.anular(venta);

        Deuda deuda = tienda.deudas.deLaVenta(venta.getId()).orElseThrow();
        assertThat(deuda.estado()).isEqualTo(EstadoDeuda.ANULADA);
        assertThat(tienda.deudas.debeDe(List.of(juan.getId()))).isEmpty();
        assertThat(venta.getLineas().getFirst().getVariante().getStock()).isEqualTo(100);
        // Entraron $30.000 en efectivo y se devuelven $30.000: el cajón queda con el fondo.
        assertThat(tienda.calcularArqueo.de(tienda.turnoAbierto()).esperado()).isEqualTo(Dinero.de(100_000));
    }

    private ComandoCobrarVenta comandoFiado(Variante variante, Cliente cliente, long total) {
        return new ComandoCobrarVenta(UUID.randomUUID(),
                List.of(new ComandoCobrarVenta.Renglon(variante.getId(), 1, total)), null, List.of(),
                cliente.getId(), total, tienda.cajero);
    }
}
