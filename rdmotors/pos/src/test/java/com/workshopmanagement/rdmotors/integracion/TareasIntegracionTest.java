package com.workshopmanagement.rdmotors.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import com.workshopmanagement.rdmotors.correo.aplicacion.AdministrarCorreos;

/**
 * El reloj de afuera: la dirección de salud y la que dispara las tareas (spec 0011, RF-006 a RF-008).
 *
 * <p>Por HTTP y sin sesión, que es como las va a llamar el servicio de afuera. El Brevo de mentira es el mismo
 * truco de {@link CorreoIntegracionTest}: un servidor del JDK en este proceso, para poder contar cuántos correos
 * salieron de verdad.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(UsuariosDePrueba.class)
@Testcontainers
class TareasIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String LLAVE = "llave-de-las-tareas-de-prueba";

    static final AtomicInteger CORREOS_QUE_SALIERON = new AtomicInteger();
    static HttpServer brevo;

    @BeforeAll
    static void levantarBrevoDeMentira() throws IOException {
        brevo = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        brevo.createContext("/v3/smtp/email", (HttpExchange intercambio) -> {
            intercambio.getRequestBody().readAllBytes();
            CORREOS_QUE_SALIERON.incrementAndGet();
            byte[] cuerpo = "{\"messageId\":\"<uno@brevo>\"}".getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(201, cuerpo.length);
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
    static void configurar(DynamicPropertyRegistry registro) {
        // La tarea de cada minuto apagada: aquí se prueba el disparo de AFUERA, y si la de adentro corre sola no se
        // sabría cuál de las dos mandó el correo.
        registro.add("rdmotors.correo.habilitado", () -> "false");
        registro.add("rdmotors.correo.brevo.api-key", () -> "xkeysib-de-mentira");
        registro.add("rdmotors.correo.remitente", () -> "caja@rdmotors.co");
        registro.add("rdmotors.correo.brevo.url",
                () -> "http://127.0.0.1:" + brevo.getAddress().getPort() + "/v3/smtp/email");
        registro.add("rdmotors.tareas.llave", () -> LLAVE);
    }

    @LocalServerPort int puerto;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuariosDePrueba personas;
    @Autowired AbrirTurno abrirTurno;
    @Autowired CerrarTurno cerrarTurno;
    @Autowired RepositorioTurnos turnos;
    @Autowired AdministrarCorreos administrar;

    private Actor admin;

    private final HttpClient cliente = HttpClient.newHttpClient();

    @BeforeEach
    void preparar() {
        admin = personas.administrador();
        CORREOS_QUE_SALIERON.set(0);
        jdbc.update("delete from correo");
        administrar.cambiarDestinatarios(List.of("ruben@rdmotors.co"), admin);
        turnos.abierto().ifPresent(t -> cerrarTurno.ejecutar(t.getId(),
                Dinero.de(t.getFondo().valor().longValueExact()), admin));
        jdbc.update("delete from correo");
    }

    /** Un cierre de caja deja su correo esperando. Se adelanta la fila, no el reloj. */
    private void unCorreoEsperando() {
        TurnoCaja turno = abrirTurno.ejecutar(Dinero.de(100_000), admin);
        cerrarTurno.ejecutar(turno.getId(), Dinero.de(100_000), admin);
        jdbc.update("update correo set no_antes_de = now() - interval '1 minute' where estado = 'POR_MANDAR'");
    }

    private int porMandar() {
        return jdbc.queryForObject("select count(*) from correo where estado = 'POR_MANDAR'", Integer.class);
    }

    private HttpResponse<String> dispararCorreos(String llave) throws IOException, InterruptedException {
        HttpRequest.Builder peticion = HttpRequest
                .newBuilder(URI.create("http://localhost:" + puerto + "/api/tareas/correos"))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (llave != null) {
            peticion.header("X-RDMOTORS-LLAVE", llave);
        }
        return cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("SIN LLAVE NO PASA NADA, y la respuesta no dice que ahí había algo")
    void sinLlave() throws Exception {
        unCorreoEsperando();

        HttpResponse<String> respuesta = dispararCorreos(null);

        assertThat(respuesta.statusCode()).as("'no existe', no 'no autorizado'").isEqualTo(404);
        assertThat(respuesta.body()).as("ni una pista de qué había que mandar").doesNotContain("llave", "LLAVE");
        assertThat(CORREOS_QUE_SALIERON.get()).isZero();
        assertThat(porMandar()).isEqualTo(1);
    }

    @Test
    @DisplayName("con la llave equivocada, lo mismo: nada")
    void llaveEquivocada() throws Exception {
        unCorreoEsperando();

        // La primera empieza igual que la buena y se desvía al final; la segunda viene vacía. Que las dos se
        // rechacen es lo que se puede probar aquí. Que se rechacen TARDANDO LO MISMO —lo que impide adivinar la
        // llave letra por letra midiendo microsegundos— no se puede probar con una prueba: se lee en el código,
        // en LlaveDeTareas, y ahí está escrito por qué.
        assertThat(dispararCorreos("llave-de-las-tareas-de-prueeeba").statusCode()).isEqualTo(404);
        assertThat(dispararCorreos("").statusCode()).isEqualTo(404);

        assertThat(CORREOS_QUE_SALIERON.get()).isZero();
        assertThat(porMandar()).isEqualTo(1);
    }

    @Test
    @DisplayName("con la llave, el correo sale aunque nadie haya tocado el sistema")
    void conLaLlave() throws Exception {
        unCorreoEsperando();

        HttpResponse<String> respuesta = dispararCorreos(LLAVE);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"intentados\":1");
        assertThat(CORREOS_QUE_SALIERON.get()).isEqualTo(1);
        assertThat(porMandar()).isZero();
    }

    @Test
    @DisplayName("DISPARARLA DOS VECES SEGUIDAS NO MANDA EL CORREO DOS VECES")
    void dosVecesNoDuplica() throws Exception {
        // El servicio de afuera puede reintentar si no le llegó la respuesta, y la tarea de adentro sigue corriendo
        // cuando el servidor está despierto. Las dos tienen que poder pisarse sin que el dueño reciba dos correos.
        unCorreoEsperando();

        assertThat(dispararCorreos(LLAVE).body()).contains("\"intentados\":1");
        assertThat(dispararCorreos(LLAVE).body()).as("ya no queda nada por mandar").contains("\"intentados\":0");

        assertThat(CORREOS_QUE_SALIERON.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("la dirección de salud responde sin sesión Y NO CUENTA NADA DEL NEGOCIO")
    void laSalud() throws Exception {
        HttpResponse<String> respuesta = cliente.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/salud")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        // Es la única dirección abierta a internet entero que alguien va a estar llamando todo el día: responde sí
        // o no, y nada más. Ni la versión, ni el nombre de la base, ni cuántas ventas hay.
        assertThat(respuesta.body()).isEqualTo("{\"ok\":true}");
    }
}
