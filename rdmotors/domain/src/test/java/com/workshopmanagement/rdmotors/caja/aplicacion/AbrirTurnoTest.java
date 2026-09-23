package com.workshopmanagement.rdmotors.caja.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.caja.dominio.EstadoTurno;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoYaAbiertoException;
import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/** Abrir el turno de caja (spec 0003, RF-001): sin turno no se vende, y nunca hay dos abiertos. */
class AbrirTurnoTest {

    private final Actor cajero = ActoresDePrueba.cajero();
    private Falsos.TurnosEnMemoria turnos;
    private AbrirTurno abrirTurno;

    @BeforeEach
    void preparar() {
        turnos = new Falsos.TurnosEnMemoria();
        abrirTurno = new AbrirTurno(turnos, new Falsos.RelojFijo("2026-09-14T13:00:00Z"));
    }

    @Test
    @DisplayName("abre el turno con su fondo, quién y cuándo")
    void abre() {
        TurnoCaja turno = abrirTurno.ejecutar(Dinero.de(100_000), cajero);

        assertThat(turno.getEstado()).isEqualTo(EstadoTurno.ABIERTO);
        assertThat(turno.getFondo()).isEqualTo(Dinero.de(100_000));
        assertThat(turno.getAbiertoPorId()).isEqualTo(cajero.id());
        assertThat(turno.getAbiertoEn()).isEqualTo(Instant.parse("2026-09-14T13:00:00Z"));
        assertThat(turno.getCerradoEn()).isNull();
        assertThat(turnos.abierto()).contains(turno);
    }

    @Test
    @DisplayName("un fondo de $0 vale: hay tiendas que arrancan con el cajón vacío")
    void fondoCero() {
        assertThat(abrirTurno.ejecutar(Dinero.CERO, cajero).getFondo()).isEqualTo(Dinero.CERO);
    }

    @Test
    @DisplayName("un fondo negativo o vacío se rechaza")
    void fondoInvalido() {
        assertThatThrownBy(() -> abrirTurno.ejecutar(Dinero.de(-1), cajero))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("negativo");
        assertThatThrownBy(() -> abrirTurno.ejecutar(null, cajero))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("$0");
        assertThat(turnos.abierto()).isEmpty();
    }

    @Test
    @DisplayName("con un turno abierto no se abre otro, y el rechazo dice desde cuándo está abierto")
    void segundoTurnoRechazado() {
        TurnoCaja primero = abrirTurno.ejecutar(Dinero.de(100_000), cajero);

        assertThatThrownBy(() -> abrirTurno.ejecutar(Dinero.de(50_000), ActoresDePrueba.cajero("Andrés")))
                .isInstanceOfSatisfying(TurnoYaAbiertoException.class, e -> {
                    assertThat(e.getAbiertoEn()).isEqualTo(primero.getAbiertoEn());
                    assertThat(e.getAbiertoPorId()).isEqualTo(cajero.id());
                });
        assertThat(turnos.datos).hasSize(1);
    }

    @Test
    @DisplayName("sin decir quién abre, no se abre")
    void sinUsuario() {
        assertThatThrownBy(() -> abrirTurno.ejecutar(Dinero.de(100_000), null))
                .isInstanceOf(ReglaDeNegocioException.class);
    }
}
