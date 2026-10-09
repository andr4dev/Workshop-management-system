package com.workshopmanagement.rdmotors.caja.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.caja.dominio.CuadreDeTransferencias;
import com.workshopmanagement.rdmotors.caja.dominio.TipoRetiro;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compras.aplicacion.ComandoRegistrarCompra;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/**
 * La cuenta de transferencias se cuadra con el turno, como el cajón (2026-10-09). La que más protege es
 * {@link #elEjemploDeLaCompraDe500()}: la compra pagada por transferencia resta en la cuenta, no en el cajón.
 */
class TransferenciasEnLaCajaTest {

    private final EscenarioCaja tienda = new EscenarioCaja();

    private TurnoCaja abrir(long fondo, long saldoTransferencias) {
        return tienda.abrirTurno.ejecutar(Dinero.de(fondo), Dinero.de(saldoTransferencias), tienda.cajero);
    }

    private CuadreDeTransferencias cuadre() {
        return tienda.calcularArqueo.transferenciasDe(tienda.turnoAbierto());
    }

    private Compra compraPorTransferencia(long total) {
        Variante variante = tienda.repuesto(total * 2);
        return tienda.registrarCompra.ejecutar(new ComandoRegistrarCompra(tienda.jotapartes.getId(), tienda.reloj.hoy(),
                "60485", FormaPago.TRANSFERENCIA, tienda.nequi.getId(), tienda.administrador,
                List.of(ComandoRegistrarCompra.Linea.porTotal(variante.getId(), 1, Dinero.de(total), null)), false));
    }

    private void retiro(long monto, TipoRetiro tipo) {
        tienda.registrarRetiro.ejecutar(UUID.randomUUID(), Dinero.de(monto), "Plata del día", tipo, false,
                tienda.cajero);
    }

    @Test
    @DisplayName("EL EJEMPLO: arranca con $599.000, entran $200.000 y se pagan $500.000 a Global: deberían quedar $299.000")
    void elEjemploDeLaCompraDe500() {
        abrir(100_000, 599_000);
        tienda.vender(50_000, 200_000);
        compraPorTransferencia(500_000);

        CuadreDeTransferencias c = cuadre();
        assertThat(c.saldoInicial()).isEqualTo(Dinero.de(599_000));
        assertThat(c.ventas()).isEqualTo(Dinero.de(200_000));
        assertThat(c.compras()).isEqualTo(Dinero.de(500_000));
        assertThat(c.esperado()).isEqualTo(Dinero.de(299_000));
        // El cajón no ve ni la transferencia ni la compra: solo los $50.000 en efectivo.
        assertThat(tienda.calcularArqueo.de(tienda.turnoAbierto()).esperado()).isEqualTo(Dinero.de(150_000));
    }

    @Test
    @DisplayName("pasar efectivo a transferencias resta en el cajón y suma en la cuenta; sacar de la cuenta no toca el cajón")
    void retirosPorTipo() {
        abrir(500_000, 100_000);
        retiro(200_000, TipoRetiro.A_TRANSFERENCIAS);
        retiro(50_000, TipoRetiro.DE_TRANSFERENCIAS);
        retiro(30_000, TipoRetiro.DEL_CAJON);

        assertThat(tienda.calcularArqueo.de(tienda.turnoAbierto()).retiros()).isEqualTo(Dinero.de(230_000));
        assertThat(tienda.calcularArqueo.de(tienda.turnoAbierto()).esperado()).isEqualTo(Dinero.de(270_000));
        assertThat(cuadre().desdeElCajon()).isEqualTo(Dinero.de(200_000));
        assertThat(cuadre().retiros()).isEqualTo(Dinero.de(50_000));
        assertThat(cuadre().esperado()).isEqualTo(Dinero.de(250_000));
    }

    @Test
    @DisplayName("sacar de la cuenta más de lo que hay en el cajón no pide confirmar: no sale del cajón")
    void retiroDeLaCuentaNoMiraElCajon() {
        abrir(10_000, 1_000_000);
        retiro(800_000, TipoRetiro.DE_TRANSFERENCIAS);

        assertThat(cuadre().esperado()).isEqualTo(Dinero.de(200_000));
    }

    @Test
    @DisplayName("no cuentan: la compra de antes de abrir, la compra anulada, la compra en efectivo ni el gasto anulado")
    void loQueNoCuenta() {
        Compra deAntes = compraPorTransferencia(70_000);       // antes de abrir el turno
        abrir(100_000, 300_000);
        Compra anulada = compraPorTransferencia(40_000);
        tienda.anularCompra.ejecutar(anulada.getId(), 0, "se registró dos veces", tienda.administrador);
        tienda.compra(25_000, false);                          // en efectivo, por fuera del cajón
        var arriendo = tienda.arriendoPorNequi(80_000);
        tienda.anularGasto.ejecutar(arriendo.getId(), "era de octubre", tienda.administrador);
        tienda.arriendoPorNequi(60_000);

        assertThat(deAntes.getFormaPago()).isEqualTo(FormaPago.TRANSFERENCIA);
        assertThat(cuadre().compras()).isEqualTo(Dinero.CERO);
        assertThat(cuadre().gastos()).isEqualTo(Dinero.de(60_000));
        assertThat(cuadre().esperado()).isEqualTo(Dinero.de(240_000));
    }

    @Test
    @DisplayName("un abono por transferencia suma en la cuenta; una venta anulada devuelve su parte por transferencia")
    void abonosYDevoluciones() {
        abrir(100_000, 0);
        Cliente juan = tienda.cliente("Juan Pérez", "1234567");
        tienda.fiar(juan, 90_000, 0);
        tienda.abonarPorTransferencia(juan, 30_000);
        var mixta = tienda.vender(10_000, 40_000);
        tienda.anular(mixta);

        assertThat(cuadre().abonos()).isEqualTo(Dinero.de(30_000));
        assertThat(cuadre().ventas()).isEqualTo(Dinero.de(40_000));
        assertThat(cuadre().devoluciones()).isEqualTo(Dinero.de(40_000));
        assertThat(cuadre().esperado()).isEqualTo(Dinero.de(30_000));
    }

    @Test
    @DisplayName("sin saldo al abrir no hay esperado; se anota una vez y ya cuadra")
    void anotarElSaldoDespues() {
        tienda.abrir(100_000);
        tienda.vender(0, 50_000);
        assertThat(cuadre().esperado()).isNull();
        assertThat(cuadre().entradas()).isEqualTo(Dinero.de(50_000));

        AnotarSaldoTransferencias anotar = new AnotarSaldoTransferencias(tienda.turnos);
        anotar.ejecutar(tienda.turnoAbierto().getId(), Dinero.de(599_000), tienda.cajero);

        assertThat(cuadre().esperado()).isEqualTo(Dinero.de(649_000));
        assertThatThrownBy(() -> anotar.ejecutar(tienda.turnoAbierto().getId(), Dinero.de(1), tienda.cajero))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("ya tiene anotado");
    }

    @Test
    @DisplayName("al cerrar queda el cuadre de la cuenta con lo que dijo, y la diferencia se audita como la del cajón")
    void cerrarConLaCuenta() {
        abrir(100_000, 599_000);
        tienda.vender(0, 200_000);
        compraPorTransferencia(500_000);

        TurnoCaja cerrado = tienda.cerrarTurno.ejecutar(tienda.turnoAbierto().getId(), Dinero.de(100_000),
                Dinero.de(300_000), tienda.cajero);

        assertThat(cerrado.getDiferencia()).isEqualTo(Dinero.CERO);
        assertThat(cerrado.getTransfEsperado()).isEqualTo(Dinero.de(299_000));
        assertThat(cerrado.getTransfContado()).isEqualTo(Dinero.de(300_000));
        assertThat(cerrado.getTransfDiferencia()).isEqualTo(Dinero.de(1_000));
        assertThat(cerrado.getTransfCompras()).isEqualTo(Dinero.de(500_000));
        assertThat(tienda.auditoria.eventos).anyMatch(e -> e.accion() == AccionAuditada.CERRAR_CAJA_CON_DIFERENCIA);

        var detalle = tienda.consultarTurnos.detalle(cerrado.getId(), tienda.cajero).orElseThrow();
        assertThat(detalle.transferencias().esperado()).isEqualTo(Dinero.de(299_000));
        assertThat(detalle.transferencias().comprasPorTransferencia()).hasSize(1);
    }

    @Test
    @DisplayName("cerrar sin mirar la cuenta guarda lo que debería haber, sin diferencia")
    void cerrarSinMirarLaCuenta() {
        abrir(100_000, 599_000);
        tienda.vender(0, 1_000);

        TurnoCaja cerrado = tienda.cerrarTurno.ejecutar(tienda.turnoAbierto().getId(), Dinero.de(100_000), null,
                tienda.cajero);

        assertThat(cerrado.getTransfEsperado()).isEqualTo(Dinero.de(600_000));
        assertThat(cerrado.getTransfContado()).isNull();
        assertThat(cerrado.getTransfDiferencia()).isNull();
        assertThat(tienda.auditoria.eventos).noneMatch(e -> e.accion() == AccionAuditada.CERRAR_CAJA_CON_DIFERENCIA);
    }
}
