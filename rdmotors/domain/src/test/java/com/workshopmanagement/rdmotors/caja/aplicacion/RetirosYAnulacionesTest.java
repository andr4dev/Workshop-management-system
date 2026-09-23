package com.workshopmanagement.rdmotors.caja.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.caja.dominio.Gasto;
import com.workshopmanagement.rdmotors.caja.dominio.MasDeLoQueDeberiaHaberException;
import com.workshopmanagement.rdmotors.caja.dominio.Retiro;
import com.workshopmanagement.rdmotors.caja.dominio.SinTurnoAbiertoException;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/** Retiros (spec 0006, H2 y RF-003) y anular gastos y retiros (H7, RF-006). */
class RetirosYAnulacionesTest {

    private final EscenarioCaja tienda = new EscenarioCaja();

    // ── Retiros ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("un retiro queda en el turno con su motivo, pide el turno bloqueado, y no es un gasto")
    void retiro() {
        TurnoCaja turno = tienda.abrir(200_000);
        int bloqueosAntes = tienda.turnos.vecesBloqueadoParaMover;

        Retiro retiro = tienda.retiro(100_000);

        assertThat(retiro.getTurnoId()).isEqualTo(turno.getId());
        assertThat(retiro.getMotivo()).isEqualTo("Se lo llevó don Rubén");
        assertThat(tienda.retiros.datos).containsKey(retiro.getId());
        assertThat(tienda.gastos.datos).isEmpty();
        assertThat(tienda.turnos.vecesBloqueadoParaMover).isEqualTo(bloqueosAntes + 1);
    }

    @Test
    @DisplayName("un retiro sin turno, sin motivo o de $0 no se registra")
    void retiroInvalido() {
        assertThatThrownBy(() -> tienda.retiro(10_000)).isInstanceOf(SinTurnoAbiertoException.class);
        tienda.abrir(100_000);
        assertThatThrownBy(() -> tienda.registrarRetiro.ejecutar(UUID.randomUUID(), Dinero.de(10_000), "  ", false,
                tienda.cajero)).hasMessage("Escribe el motivo del retiro: quién se la llevó o para qué");
        assertThatThrownBy(() -> tienda.registrarRetiro.ejecutar(UUID.randomUUID(), Dinero.CERO, "Dueño", false,
                tienda.cajero)).hasMessage("El monto del retiro tiene que ser mayor a $0");
        assertThat(tienda.retiros.datos).isEmpty();
    }

    @Test
    @DisplayName("la misma llave dos veces deja un solo retiro")
    void retiroMismaLlave() {
        tienda.abrir(100_000);
        UUID llave = UUID.randomUUID();

        Retiro primero = tienda.registrarRetiro.ejecutar(llave, Dinero.de(10_000), "Dueño", false, tienda.cajero);
        Retiro segundo = tienda.registrarRetiro.ejecutar(llave, Dinero.de(10_000), "Dueño", false, tienda.cajero);

        assertThat(segundo.getId()).isEqualTo(primero.getId());
        assertThat(tienda.retiros.datos).hasSize(1);
    }

    @Test
    @DisplayName("DECISIÓN 3: un retiro de $10.000.000 pide confirmar; confirmado, se registra")
    void retiroConfirmado() {
        tienda.abrir(100_000);
        UUID llave = UUID.randomUUID();

        assertThatThrownBy(() -> tienda.registrarRetiro.ejecutar(llave, Dinero.de(10_000_000), "Dueño", false,
                tienda.cajero)).isInstanceOf(MasDeLoQueDeberiaHaberException.class);
        assertThat(tienda.retiros.datos).isEmpty();

        assertThat(tienda.registrarRetiro.ejecutar(llave, Dinero.de(10_000_000), "Dueño", true, tienda.cajero)
                .getMonto()).isEqualTo(Dinero.de(10_000_000));
    }

    // ── Anular ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("un gasto del cajón se anula con su turno abierto: queda con motivo, deja el evento y no resta")
    void anularGasto() {
        tienda.abrir(200_000);
        Gasto gasto = tienda.gastoDelCajon(150_000);

        Gasto anulado = tienda.anularGasto.ejecutar(gasto.getId(), "Era $15.000", tienda.cajero);

        assertThat(anulado.estaAnulado()).isTrue();
        assertThat(anulado.getMotivoAnulacion()).isEqualTo("Era $15.000");
        assertThat(anulado.getAnuladoPorId()).isEqualTo(tienda.cajero.id());
        assertThat(tienda.auditoria.historialDe(Gasto.TIPO_AUDITORIA, gasto.getId())).singleElement()
                .satisfies(e -> {
                    assertThat(e.accion()).isEqualTo(AccionAuditada.ANULAR_GASTO);
                    assertThat(e.motivo()).isEqualTo("Era $15.000");
                    assertThat(e.antes()).containsEntry("anulado", false);
                    assertThat(e.despues()).containsEntry("anulado", true);
                });
        assertThat(tienda.calcularArqueo.de(tienda.turnoAbierto()).esperado()).isEqualTo(Dinero.de(200_000));
    }

    @Test
    @DisplayName("un gasto o retiro de un turno cerrado no se anula: su arqueo ya se firmó")
    void turnoCerradoNoSeAnula() {
        tienda.abrir(200_000);
        Gasto gasto = tienda.gastoDelCajon(15_000);
        Retiro retiro = tienda.retiro(10_000);
        tienda.cerrar(175_000);
        tienda.abrir(50_000);

        assertThatThrownBy(() -> tienda.anularGasto.ejecutar(gasto.getId(), "tarde", tienda.cajero))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessage("Ese turno ya se cerró: su arqueo no se modifica");
        assertThatThrownBy(() -> tienda.anularRetiro.ejecutar(retiro.getId(), "tarde", tienda.cajero))
                .hasMessage("Ese turno ya se cerró: su arqueo no se modifica");
        assertThat(gasto.estaAnulado()).isFalse();
        assertThat(retiro.estaAnulado()).isFalse();
    }

    @Test
    @DisplayName("un gasto por fuera del cajón se anula aunque no haya turno abierto")
    void anularPorFuera() {
        Gasto arriendo = tienda.arriendoPorNequi(800_000);

        assertThat(tienda.anularGasto.ejecutar(arriendo.getId(), "Se registró dos veces", tienda.administrador)
                .estaAnulado()).isTrue();
    }

    @Test
    @DisplayName("un gasto anulado no se anula otra vez, y sin motivo no se anula")
    void anularDosVecesOSinMotivo() {
        tienda.abrir(100_000);
        Gasto gasto = tienda.gastoDelCajon(15_000);

        assertThatThrownBy(() -> tienda.anularGasto.ejecutar(gasto.getId(), " ", tienda.cajero))
                .hasMessageContaining("motivo");
        tienda.anularGasto.ejecutar(gasto.getId(), "error", tienda.cajero);
        assertThatThrownBy(() -> tienda.anularGasto.ejecutar(gasto.getId(), "otra vez", tienda.cajero))
                .hasMessage("Este gasto ya fue anulado");
        assertThat(tienda.auditoria.historialDe(Gasto.TIPO_AUDITORIA, gasto.getId())).hasSize(1);
    }

    @Test
    @DisplayName("un retiro se anula con su turno abierto: deja el evento y no resta")
    void anularRetiro() {
        tienda.abrir(200_000);
        Retiro retiro = tienda.retiro(100_000);

        tienda.anularRetiro.ejecutar(retiro.getId(), "Lo devolvió", tienda.cajero);

        assertThat(retiro.estaAnulado()).isTrue();
        assertThat(tienda.auditoria.historialDe(Retiro.TIPO_AUDITORIA, retiro.getId())).singleElement()
                .satisfies(e -> assertThat(e.accion()).isEqualTo(AccionAuditada.ANULAR_RETIRO));
        assertThat(tienda.calcularArqueo.de(tienda.turnoAbierto()).retiros()).isEqualTo(Dinero.CERO);
    }
}
