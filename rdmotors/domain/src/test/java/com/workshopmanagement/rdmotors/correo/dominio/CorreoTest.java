package com.workshopmanagement.rdmotors.correo.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/** Los destinatarios, la cola y los reintentos (spec 0010, RF-001 a RF-004 y RF-007). */
class CorreoTest {

    private static final Instant CIERRE = Instant.parse("2026-09-22T23:00:00Z");
    private final PoliticaDeReintentos politica = new PoliticaDeReintentos();

    private static Destinatarios dueno() {
        return Destinatarios.de(List.of("ruben@rdmotors.co"));
    }

    // ── Destinatarios ───────────────────────────────────────────────────────

    @Test
    @DisplayName("los correos se guardan limpios: sin espacios, en minúsculas y sin repetir")
    void destinatariosLimpios() {
        Destinatarios d = Destinatarios.de(List.of("  Ruben@RDMotors.co ", "socio@gmail.com", "ruben@rdmotors.co", ""));

        assertThat(d.correos()).containsExactly("ruben@rdmotors.co", "socio@gmail.com");
        assertThat(Destinatarios.desdeTexto(d.comoTexto())).isEqualTo(d);
    }

    @Test
    @DisplayName("un correo mal escrito se dice cuál, antes de guardarlo")
    void correoMalEscrito() {
        assertThatThrownBy(() -> Destinatarios.de(List.of("ruben@gmail")))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessageContaining("«ruben@gmail» no parece un correo");
        assertThatThrownBy(() -> Destinatarios.de(List.of("ruben gmail.com")))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    @DisplayName("hasta 5 correos; y ninguno es válido: es como se apaga el resumen")
    void cuantos() {
        assertThatThrownBy(() -> Destinatarios.de(List.of("a@x.co", "b@x.co", "c@x.co", "d@x.co", "e@x.co", "f@x.co")))
                .hasMessageContaining("5 correos como máximo");
        assertThat(Destinatarios.de(List.of()).hayAlguno()).isFalse();
        assertThat(Destinatarios.desdeTexto("").hayAlguno()).isFalse();
    }

    // ── La cola ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("el del cierre no sale antes de 3 minutos: el tiempo de escribir las observaciones")
    void esperaLasObservaciones() {
        Correo correo = Correo.delCierre(UUID.randomUUID(), dueno(), CIERRE, Duration.ofMinutes(3));

        assertThat(correo.getEstado()).isEqualTo(EstadoCorreo.POR_MANDAR);
        assertThat(correo.sePuedeIntentar(CIERRE.plusSeconds(170))).isFalse();
        assertThat(correo.sePuedeIntentar(CIERRE.plusSeconds(180))).isTrue();
    }

    @Test
    @DisplayName("sin internet se reintenta a los 1, 5, 15 y 30 minutos, y después cada hora, sin rendirse")
    void reintentos() {
        assertThat(politica.esperaTras(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(politica.esperaTras(2)).isEqualTo(Duration.ofMinutes(5));
        assertThat(politica.esperaTras(3)).isEqualTo(Duration.ofMinutes(15));
        assertThat(politica.esperaTras(4)).isEqualTo(Duration.ofMinutes(30));
        assertThat(politica.esperaTras(5)).isEqualTo(Duration.ofHours(1));
        assertThat(politica.esperaTras(200)).isEqualTo(Duration.ofHours(1));

        Correo correo = Correo.delCierre(UUID.randomUUID(), dueno(), CIERRE, Duration.ofMinutes(3));
        Instant primero = CIERRE.plusSeconds(180);
        correo.noSalio("I/O error: api.brevo.com", true, politica, primero);

        assertThat(correo.getEstado()).as("sin internet se sigue intentando").isEqualTo(EstadoCorreo.POR_MANDAR);
        assertThat(correo.getIntentos()).isEqualTo(1);
        assertThat(correo.getNoAntesDe()).isEqualTo(primero.plus(Duration.ofMinutes(1)));
        assertThat(correo.getUltimoError()).contains("api.brevo.com");
    }

    @Test
    @DisplayName("una llave inválida no se arregla sola: queda fallido, a la vista, y el administrador lo reintenta")
    void fallaQueNoSeArreglaSola() {
        Correo correo = Correo.dePrueba(dueno(), CIERRE);
        correo.noSalio("Brevo respondió 401: Key not found", false, politica, CIERRE);

        assertThat(correo.getEstado()).isEqualTo(EstadoCorreo.FALLO);
        assertThat(correo.sePuedeIntentar(CIERRE.plus(Duration.ofDays(3)))).as("no se insiste solo").isFalse();

        correo.reintentar(CIERRE.plus(Duration.ofHours(2)));
        assertThat(correo.getEstado()).isEqualTo(EstadoCorreo.POR_MANDAR);
        assertThat(correo.sePuedeIntentar(CIERRE.plus(Duration.ofHours(2)))).isTrue();
    }

    @Test
    @DisplayName("sin llave de Brevo espera sin gastar intentos, y dice por qué")
    void sinConfigurar() {
        Correo correo = Correo.dePrueba(dueno(), CIERRE);
        correo.esperaConfiguracion("Falta la llave de Brevo", CIERRE, Duration.ofMinutes(10));

        assertThat(correo.getIntentos()).isZero();
        assertThat(correo.getEstado()).isEqualTo(EstadoCorreo.POR_MANDAR);
        assertThat(correo.getUltimoError()).isEqualTo("Falta la llave de Brevo");
        assertThat(correo.sePuedeIntentar(CIERRE.plus(Duration.ofMinutes(10)))).isTrue();
    }

    @Test
    @DisplayName("el que salió queda con el id de Brevo, y no se manda dos veces")
    void enviado() {
        Correo correo = Correo.dePrueba(dueno(), CIERRE);
        correo.seEnvio("<2026@smtp-relay.mailin.fr>", CIERRE);

        assertThat(correo.getEstado()).isEqualTo(EstadoCorreo.ENVIADO);
        assertThat(correo.getIdEnBrevo()).isEqualTo("<2026@smtp-relay.mailin.fr>");
        assertThat(correo.sePuedeIntentar(CIERRE.plus(Duration.ofDays(1)))).isFalse();
        assertThatThrownBy(() -> correo.reintentar(CIERRE)).hasMessageContaining("ya salió");
    }

    @Test
    @DisplayName("un correo sin a quién mandarlo no se encola")
    void sinDestinatarios() {
        assertThatThrownBy(() -> Correo.dePrueba(Destinatarios.de(List.of()), CIERRE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
