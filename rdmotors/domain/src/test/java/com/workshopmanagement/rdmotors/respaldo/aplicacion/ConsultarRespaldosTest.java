package com.workshopmanagement.rdmotors.respaldo.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;
import com.workshopmanagement.rdmotors.respaldo.dominio.OrigenRespaldo;
import com.workshopmanagement.rdmotors.respaldo.dominio.PoliticaDeRespaldo;
import com.workshopmanagement.rdmotors.respaldo.dominio.Respaldo;

/** Cómo va el respaldo, para la pantalla del administrador (spec 0011, RF-011). */
class ConsultarRespaldosTest {

    private static final String HOY = "2026-09-21T14:00:00Z";

    private Falsos.RespaldosEnMemoria respaldos;
    private ConsultarRespaldos consultar;

    @BeforeEach
    void preparar() {
        respaldos = new Falsos.RespaldosEnMemoria();
        consultar = new ConsultarRespaldos(respaldos, new PoliticaDeRespaldo(7, ZoneId.of("America/Bogota")),
                new Falsos.RelojFijo(HOY));
    }

    private Respaldo bueno(String cuando) {
        return respaldos.sembrar(Respaldo.hecho(Instant.parse(cuando), "rdmotors-" + cuando + ".dump", 92_000, 900,
                OrigenRespaldo.A_MANO, null));
    }

    @Test
    @DisplayName("dice cuál fue la última que se bajó y hace cuántos días")
    void elEstado() {
        bueno("2026-09-18T07:00:00Z");
        Respaldo laDeAyer = bueno("2026-09-20T07:00:00Z");

        EstadoDelRespaldo estado = consultar.estado(ActoresDePrueba.administrador());

        assertThat(estado.ultimaBuena()).isEqualTo(laDeAyer);
        assertThat(estado.diasSinBajar()).isEqualTo(1);
        assertThat(estado.diasParaAvisar()).isEqualTo(7);
        assertThat(estado.hayQueAvisar()).isFalse();
        assertThat(estado.copias()).as("de la más reciente a la más vieja").first().isEqualTo(laDeAyer);
        assertThat(estado.copias()).hasSize(2);
    }

    @Test
    @DisplayName("SIN NINGUNA COPIA BAJADA se avisa, y no hay número de días que mostrar")
    void sinNingunaCopia() {
        EstadoDelRespaldo estado = consultar.estado(ActoresDePrueba.administrador());

        assertThat(estado.ultimaBuena()).isNull();
        assertThat(estado.diasSinBajar()).as("nunca no es un número de días").isNull();
        assertThat(estado.hayQueAvisar()).isTrue();
        assertThat(estado.copias()).isEmpty();
    }

    @Test
    @DisplayName("a los ocho días sin bajar una, se avisa")
    void haceDemasiado() {
        bueno("2026-09-13T07:00:00Z");

        EstadoDelRespaldo estado = consultar.estado(ActoresDePrueba.administrador());

        assertThat(estado.diasSinBajar()).isEqualTo(8);
        assertThat(estado.hayQueAvisar()).isTrue();
    }

    @Test
    @DisplayName("los intentos que fallaron siguen en la lista: son la historia de que algo no funcionó")
    void losFallidosSeQuedan() {
        bueno("2026-09-20T07:00:00Z");
        respaldos.sembrar(Respaldo.fallido(Instant.parse("2026-09-21T07:00:00Z"), "rdmotors-hoy.dump",
                "pg_dump terminó con error 1", 30, OrigenRespaldo.A_MANO, null));

        EstadoDelRespaldo estado = consultar.estado(ActoresDePrueba.administrador());

        assertThat(estado.copias()).hasSize(2);
        assertThat(estado.diasSinBajar()).as("el fallido no cuenta como copia bajada").isEqualTo(1);
        assertThat(estado.hayQueAvisar()).as("el último intento falló").isTrue();
    }

    @Test
    @DisplayName("el respaldo es del administrador: al cajero no le sirve y no puede hacer nada con eso")
    void elCajeroNo() {
        assertThatThrownBy(() -> consultar.estado(ActoresDePrueba.cajero()))
                .isInstanceOf(NoPermitidoException.class);
    }
}
