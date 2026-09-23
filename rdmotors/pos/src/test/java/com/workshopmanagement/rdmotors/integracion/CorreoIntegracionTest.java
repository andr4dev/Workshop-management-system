package com.workshopmanagement.rdmotors.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.workshopmanagement.rdmotors.caja.aplicacion.AbrirTurno;
import com.workshopmanagement.rdmotors.caja.aplicacion.CerrarTurno;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.correo.aplicacion.AdministrarCorreos;
import com.workshopmanagement.rdmotors.correo.aplicacion.ConsultarCorreos;
import com.workshopmanagement.rdmotors.correo.aplicacion.MandarCorreosPendientes;
import com.workshopmanagement.rdmotors.correo.dominio.Correo;
import com.workshopmanagement.rdmotors.correo.dominio.EstadoCorreo;
import com.workshopmanagement.rdmotors.correo.dominio.puerto.RepositorioCorreos;

/**
 * El correo del cierre contra Postgres y contra un Brevo de mentira (spec 0010).
 *
 * <p>El servidor de mentira es un {@code HttpServer} del JDK en este mismo proceso: así se prueba lo que de verdad
 * importa del adaptador —que manda la llave en su cabecera, que el cuerpo tiene la forma que Brevo espera, y que un
 * 401 no se reintenta mientras un 503 sí— sin depender de internet ni de una cuenta de Brevo.
 */
@SpringBootTest
@Import(UsuariosDePrueba.class)
@Testcontainers
class CorreoIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    /** Lo que el Brevo de mentira responde en la próxima petición. */
    static volatile int estadoQueResponde = 201;
    static volatile String cuerpoQueResponde = "{\"messageId\":\"<202609@smtp-relay.mailin.fr>\"}";
    static final List<String> PETICIONES = new ArrayList<>();
    static final List<String> LLAVES = new ArrayList<>();
    static final AtomicInteger RECIBIDAS = new AtomicInteger();

    static HttpServer brevo;

    @BeforeAll
    static void levantarBrevoDeMentira() throws IOException {
        brevo = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        brevo.createContext("/v3/smtp/email", (HttpExchange intercambio) -> {
            LLAVES.add(String.valueOf(intercambio.getRequestHeaders().getFirst("api-key")));
            PETICIONES.add(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            RECIBIDAS.incrementAndGet();
            byte[] cuerpo = cuerpoQueResponde.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estadoQueResponde, cuerpo.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(cuerpo);
            }
        });
        brevo.start();
    }

    @AfterAll
    static void bajarBrevoDeMentira() {
        brevo.stop(0);
    }

    @DynamicPropertySource
    static void apuntarABrevoDeMentira(DynamicPropertyRegistry registro) {
        registro.add("rdmotors.correo.habilitado", () -> "false");   // la tarea no corre sola en las pruebas
        registro.add("rdmotors.correo.brevo.api-key", () -> "xkeysib-de-mentira");
        registro.add("rdmotors.correo.remitente", () -> "caja@rdmotors.co");
        registro.add("rdmotors.correo.brevo.url",
                () -> "http://127.0.0.1:" + brevo.getAddress().getPort() + "/v3/smtp/email");
    }

    @Autowired UsuariosDePrueba personas;
    @Autowired JdbcTemplate jdbc;
    @Autowired AbrirTurno abrirTurno;
    @Autowired CerrarTurno cerrarTurno;
    @Autowired RepositorioTurnos turnos;
    @Autowired RepositorioCorreos correos;
    @Autowired MandarCorreosPendientes mandar;
    @Autowired AdministrarCorreos administrar;
    @Autowired ConsultarCorreos consultar;

    private Actor admin;

    @BeforeEach
    void preparar() {
        admin = personas.administrador();
        estadoQueResponde = 201;
        cuerpoQueResponde = "{\"messageId\":\"<202609@smtp-relay.mailin.fr>\"}";
        PETICIONES.clear();
        LLAVES.clear();
        RECIBIDAS.set(0);
        jdbc.update("delete from correo");
        administrar.cambiarDestinatarios(List.of("ruben@rdmotors.co"), admin);
        turnos.abierto().ifPresent(t -> cerrarTurno.ejecutar(t.getId(),
                Dinero.de(t.getFondo().valor().longValueExact()), admin));
        jdbc.update("delete from correo");
    }

    private TurnoCaja cerrarUnTurno() {
        TurnoCaja turno = abrirTurno.ejecutar(Dinero.de(100_000), admin);
        return cerrarTurno.ejecutar(turno.getId(), Dinero.de(100_000), admin);
    }

    /** Como si hubieran pasado los 3 minutos de espera: se adelanta la fila, no el reloj. */
    private void yaSePuedeMandar() {
        jdbc.update("update correo set no_antes_de = now() - interval '1 minute' where estado = 'POR_MANDAR'");
    }

    @Test
    @DisplayName("cerrar deja el correo en la base, en el mismo commit, y sale con la llave en su cabecera")
    void elCierreEncolaYSale() {
        TurnoCaja cerrado = cerrarUnTurno();

        assertThat(jdbc.queryForObject("select count(*) from correo where turno_id = ? and estado = 'POR_MANDAR'",
                Integer.class, cerrado.getId())).isEqualTo(1);

        yaSePuedeMandar();
        assertThat(mandar.mandar()).isEqualTo(1);

        assertThat(RECIBIDAS.get()).isEqualTo(1);
        assertThat(LLAVES.getFirst()).isEqualTo("xkeysib-de-mentira");
        assertThat(PETICIONES.getFirst())
                .contains("\"sender\"").contains("caja@rdmotors.co")
                .contains("\"to\"").contains("ruben@rdmotors.co")
                .contains("\"subject\":\"Cierre de caja")
                .contains("\"htmlContent\"").contains("\"textContent\"");
        assertThat(jdbc.queryForObject("select estado from correo where turno_id = ?", String.class, cerrado.getId()))
                .isEqualTo("ENVIADO");
        assertThat(jdbc.queryForObject("select id_en_brevo from correo where turno_id = ?", String.class,
                cerrado.getId())).isEqualTo("<202609@smtp-relay.mailin.fr>");
    }

    @Test
    @DisplayName("un 503 de Brevo se reintenta; un 401 no, y queda fallido con lo que dijo Brevo")
    void loQueSeReintentaYLoQueNo() {
        cerrarUnTurno();
        yaSePuedeMandar();
        estadoQueResponde = 503;
        cuerpoQueResponde = "{\"code\":\"unavailable\",\"message\":\"Service unavailable\"}";

        mandar.mandar();

        Correo correo = correos.ultimos(1).getFirst();
        assertThat(correo.getEstado()).as("sin Brevo se espera y se reintenta").isEqualTo(EstadoCorreo.POR_MANDAR);
        assertThat(correo.getIntentos()).isEqualTo(1);
        assertThat(correo.getUltimoError()).contains("503");

        yaSePuedeMandar();
        estadoQueResponde = 401;
        cuerpoQueResponde = "{\"code\":\"unauthorized\",\"message\":\"Key not found\"}";

        mandar.mandar();

        Correo despues = correos.ultimos(1).getFirst();
        assertThat(despues.getEstado()).isEqualTo(EstadoCorreo.FALLO);
        assertThat(despues.getUltimoError()).contains("401").contains("Key not found");
        assertThat(despues.getUltimoError()).as("la llave nunca se escribe").doesNotContain("xkeysib");

        // Arreglada la llave, el administrador lo reintenta y sale.
        estadoQueResponde = 201;
        administrar.reintentar(despues.getId(), admin);
        assertThat(mandar.mandar()).isEqualTo(1);
        assertThat(correos.ultimos(1).getFirst().getEstado()).isEqualTo(EstadoCorreo.ENVIADO);
    }

    @Test
    @DisplayName("el correo no sale antes de los 3 minutos que esperan las observaciones")
    void esperaLasObservaciones() {
        TurnoCaja cerrado = cerrarUnTurno();

        assertThat(mandar.mandar()).isZero();
        assertThat(RECIBIDAS.get()).isZero();
        assertThat(jdbc.queryForObject("select no_antes_de - creado_en >= interval '3 minutes' from correo "
                + "where turno_id = ?", Boolean.class, cerrado.getId())).isTrue();
    }

    @Test
    @DisplayName("sin destinatarios, cerrar no encola nada")
    void sinDestinatarios() {
        administrar.cambiarDestinatarios(List.of(), admin);

        cerrarUnTurno();

        assertThat(jdbc.queryForObject("select count(*) from correo", Integer.class)).isZero();
        assertThatThrownBy(() -> administrar.probar(admin)).isInstanceOf(ReglaDeNegocioException.class);
    }

    @Test
    @DisplayName("LA BASE no deja dos resúmenes del mismo turno, ni un «enviado» sin fecha")
    void laBaseCuidaLaCola() {
        TurnoCaja cerrado = cerrarUnTurno();

        assertThatThrownBy(() -> jdbc.update("""
                insert into correo (id, tipo, turno_id, destinatarios, estado, intentos, creado_en, no_antes_de)
                values (?, 'CIERRE_DE_TURNO', ?, 'otro@x.co', 'POR_MANDAR', 0, now(), now())
                """, UUID.randomUUID(), cerrado.getId()))
                .hasMessageContaining("ux_correo_cierre_por_turno");

        assertThatThrownBy(() -> jdbc.update("""
                insert into correo (id, tipo, turno_id, destinatarios, estado, intentos, creado_en, no_antes_de)
                values (?, 'PRUEBA', null, 'otro@x.co', 'ENVIADO', 1, now(), now())
                """, UUID.randomUUID()))
                .hasMessageContaining("ck_correo_enviado_con_fecha");
    }

    @Test
    @DisplayName("el estado para la pantalla dice a quién llega y qué falta, nunca la llave")
    void elEstadoParaLaPantalla() {
        var estado = consultar.estado(admin);

        assertThat(estado.destinatarios()).containsExactly("ruben@rdmotors.co");
        assertThat(estado.listoParaMandar()).isTrue();
        assertThat(estado.loQueFalta()).isNull();
        assertThat(estado.remitente()).isEqualTo("RD MOTORS <caja@rdmotors.co>");
        assertThat(estado.toString()).doesNotContain("xkeysib");
    }

    @Test
    @DisplayName("el de prueba sale en el momento, con su propio asunto")
    void elDePrueba() {
        Correo prueba = administrar.probar(admin);

        assertThat(prueba.getEstado()).isEqualTo(EstadoCorreo.ENVIADO);
        assertThat(PETICIONES.getFirst()).contains("Prueba de correo");
        assertThat(prueba.getEnviadoEn()).isNotNull();
        assertThat(Duration.between(prueba.getCreadoEn(), prueba.getEnviadoEn())).isLessThan(Duration.ofMinutes(1));
    }
}
