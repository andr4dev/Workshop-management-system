package com.workshopmanagement.rdmotors.compras.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.caja.dominio.ArqueoDeTurno;
import com.workshopmanagement.rdmotors.caja.dominio.SinTurnoAbiertoException;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/**
 * Una compra pagada con plata del cajón (spec 0006, H4, RF-008 a RF-010, y decisión 8 del plan: si su turno ya
 * cerró, ni la marca ni la forma de pago cambian).
 */
class CompraDeCajaTest {

    private EscenarioCompras tienda;
    private Variante filtro;

    @BeforeEach
    void preparar() {
        tienda = new EscenarioCompras();
        filtro = tienda.repuesto("352B59K", 13_000);
    }

    private TurnoCaja abrirTurno() {
        return tienda.turnos.guardar(TurnoCaja.abrir(Dinero.de(100_000), tienda.usuario.id(), tienda.reloj.ahora()));
    }

    private void cerrarTurno(TurnoCaja turno) {
        turno.cerrar(ArqueoDeTurno.calcular(turno, List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                Dinero.de(100_000), tienda.usuario.id(), tienda.reloj.ahora());
    }

    private Compra comprar(FormaPago forma, UUID cuentaId, boolean deCaja) {
        return tienda.registrar.ejecutar(new ComandoRegistrarCompra(tienda.jotapartes.getId(), LocalDate.of(2026, 9, 13),
                "FV-9912", forma, cuentaId, tienda.usuario,
                List.of(EscenarioCompras.unidades(filtro, 5, 10_000)), deCaja));
    }

    private ComandoCorregirCompra corregirPago(Compra compra, FormaPago forma, UUID cuentaId, Boolean deCaja,
                                               String numero) {
        return new ComandoCorregirCompra(compra.getId(), EscenarioCompras.version(compra), "error al digitar",
                tienda.usuario, compra.getProveedor().getId(), compra.getFechaDocumento(), numero, forma, cuentaId,
                null, deCaja);
    }

    @Test
    @DisplayName("en efectivo y con plata del cajón queda en el turno abierto, y pide el turno bloqueado antes que nada")
    void registraDeCaja() {
        TurnoCaja turno = abrirTurno();

        Compra compra = comprar(FormaPago.EFECTIVO, null, true);

        assertThat(compra.isPagadaDeCaja()).isTrue();
        assertThat(compra.getTurnoId()).isEqualTo(turno.getId());
        assertThat(tienda.turnos.vecesBloqueadoParaMover).isEqualTo(1);
        assertThat(filtro.getStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("sin marcar no es del cajón y no pide turno")
    void sinMarcar() {
        Compra compra = comprar(FormaPago.EFECTIVO, null, false);

        assertThat(compra.isPagadaDeCaja()).isFalse();
        assertThat(compra.getTurnoId()).isNull();
        assertThat(tienda.turnos.vecesBloqueadoParaMover).isZero();
    }

    @Test
    @DisplayName("con plata del cajón y sin turno abierto no se registra, ni entra mercancía")
    void sinTurno() {
        assertThatThrownBy(() -> comprar(FormaPago.EFECTIVO, null, true))
                .isInstanceOf(SinTurnoAbiertoException.class)
                .hasMessageContaining("desmárcala");
        assertThat(filtro.getStock()).isZero();
    }

    @Test
    @DisplayName("una transferencia no se paga con plata del cajón")
    void transferenciaNo() {
        abrirTurno();
        assertThatThrownBy(() -> comprar(FormaPago.TRANSFERENCIA, tienda.nequi.getId(), true))
                .hasMessage("Solo una compra en efectivo se paga con plata del cajón");
    }

    @Test
    @DisplayName("con su turno abierto se marca y se desmarca al corregir")
    void corregirConTurnoAbierto() {
        TurnoCaja turno = abrirTurno();
        Compra compra = comprar(FormaPago.EFECTIVO, null, false);

        tienda.corregir.ejecutar(corregirPago(compra, FormaPago.EFECTIVO, null, true, "FV-9912"));
        assertThat(compra.isPagadaDeCaja()).isTrue();
        assertThat(compra.getTurnoId()).isEqualTo(turno.getId());

        tienda.corregir.ejecutar(corregirPago(compra, FormaPago.TRANSFERENCIA, tienda.nequi.getId(), false, "FV-9912"));
        assertThat(compra.isPagadaDeCaja()).isFalse();
        assertThat(compra.getTurnoId()).isNull();
        assertThat(compra.getFormaPago()).isEqualTo(FormaPago.TRANSFERENCIA);
    }

    @Test
    @DisplayName("pasarla a transferencia sin desmarcar «con plata del cajón» se rechaza")
    void transferenciaSinDesmarcar() {
        abrirTurno();
        Compra compra = comprar(FormaPago.EFECTIVO, null, true);

        assertThatThrownBy(() -> tienda.corregir.ejecutar(
                corregirPago(compra, FormaPago.TRANSFERENCIA, tienda.nequi.getId(), null, "FV-9912")))
                .hasMessageContaining("Desmarca «con plata del cajón»");
    }

    @Test
    @DisplayName("DECISIÓN 8: con su turno cerrado no se desmarca ni cambia de forma de pago; el número sí se corrige")
    void corregirConTurnoCerrado() {
        TurnoCaja turno = abrirTurno();
        Compra compra = comprar(FormaPago.EFECTIVO, null, true);
        cerrarTurno(turno);
        abrirTurno();

        assertThatThrownBy(() -> tienda.corregir.ejecutar(corregirPago(compra, FormaPago.EFECTIVO, null, false, "FV-9912")))
                .hasMessageContaining("ya se cerró");
        assertThatThrownBy(() -> tienda.corregir.ejecutar(
                corregirPago(compra, FormaPago.TRANSFERENCIA, tienda.nequi.getId(), null, "FV-9912")))
                .hasMessageContaining("ya se cerró");

        tienda.corregir.ejecutar(corregirPago(compra, FormaPago.EFECTIVO, null, null, "FV-9913"));
        assertThat(compra.getNumeroFactura()).isEqualTo("FV-9913");
        assertThat(compra.isPagadaDeCaja()).isTrue();
        assertThat(compra.getTurnoId()).isEqualTo(turno.getId());
    }

    @Test
    @DisplayName("marcarla con plata del cajón sin turno abierto se rechaza")
    void marcarSinTurno() {
        Compra compra = comprar(FormaPago.EFECTIVO, null, false);

        assertThatThrownBy(() -> tienda.corregir.ejecutar(corregirPago(compra, FormaPago.EFECTIVO, null, true, "FV-9912")))
                .hasMessageContaining("No hay un turno abierto");
    }

    @Test
    @DisplayName("anular una compra de caja pide el turno bloqueado; con su turno ya cerrado se anula igual")
    void anular() {
        TurnoCaja turno = abrirTurno();
        Compra compra = comprar(FormaPago.EFECTIVO, null, true);
        cerrarTurno(turno);
        int bloqueosAntes = tienda.turnos.vecesBloqueadoParaMover;

        tienda.anular.ejecutar(compra.getId(), EscenarioCompras.version(compra), "se registró dos veces", tienda.usuario);

        assertThat(tienda.turnos.vecesBloqueadoParaMover).isEqualTo(bloqueosAntes + 1);
        assertThat(filtro.getStock()).isZero();
    }
}
