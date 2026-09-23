package com.workshopmanagement.rdmotors.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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

import com.workshopmanagement.rdmotors.compartido.dominio.Rol;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;

/**
 * Cuando la copia NO se puede sacar (spec 0011, §6).
 *
 * <h2>Por qué esto tiene su propia clase</h2>
 *
 * Pasó de verdad al probar la imagen del contenedor: la ruta de {@code pg_dump} era la de Windows, adentro no
 * existía, y el dueño habría visto <i>"El servidor falló (500)"</i> — que no dice nada y no deja arreglar nada. El
 * mensaje del motor es justo lo que hay que leerle a quien vaya a resolverlo.
 *
 * <p>Para provocarlo hace falta un {@code pg_dump} que no exista, y eso es una propiedad de todo el arranque: de
 * ahí la clase aparte.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(UsuariosDePrueba.class)
@Testcontainers
class RespaldoQueFallaIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void unPgDumpQueNoExiste(DynamicPropertyRegistry registro) {
        registro.add("rdmotors.respaldo.pg-dump", () -> "C:/no/existe/pg_dump.exe");
    }

    @LocalServerPort int puerto;
    @Autowired UsuariosDePrueba personas;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("SI NO SE PUEDE SACAR LA COPIA, se dice por qué — y el intento queda registrado")
    void seDicePorQue() throws Exception {
        HttpResponse<String> respuesta = comoAdministrador("/api/respaldos/archivo");

        // 503 y no 500: no es un error del sistema, es que no se pudo ahora.
        assertThat(respuesta.statusCode()).isEqualTo(503);
        assertThat(respuesta.body())
                .as("el mensaje del motor, que es lo que hay que leerle a quien lo arregle")
                .contains("No encontré pg_dump")
                // La ruta, con las barras que use el sistema: en Windows salen al revés.
                .contains("existe")
                .contains("rdmotors.respaldo.pg-dump");
        assertThat(respuesta.body()).as("y no un archivo a medias").doesNotContain("PGDMP");

        // Por esto el caso de uso no abre transacción: al lanzar, esta fila tenía que sobrevivir.
        assertThat(jdbc.queryForObject("select count(*) from respaldo where estado = 'FALLO'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select error from respaldo where estado = 'FALLO'", String.class))
                .contains("pg_dump");
    }

    @Test
    @DisplayName("y el fallo se ve al entrar: el estado avisa con lo que pasó")
    void seVeAlEntrar() throws Exception {
        comoAdministrador("/api/respaldos/archivo");

        HttpResponse<String> estado = comoAdministrador("/api/respaldos");

        assertThat(estado.statusCode()).isEqualTo(200);
        assertThat(estado.body()).contains("\"hayQueAvisar\":true");
        assertThat(estado.body()).contains("\"estado\":\"FALLO\"").contains("pg_dump");
    }

    private HttpResponse<String> comoAdministrador(String ruta) throws IOException, InterruptedException {
        Usuario quien = personas.crear("Rubén", Rol.ADMINISTRADOR);
        CookieManager galletas = new CookieManager();
        HttpClient cliente = HttpClient.newBuilder().cookieHandler(galletas).build();

        cliente.send(HttpRequest.newBuilder(URI.create(en("/api/instalacion"))).build(),
                HttpResponse.BodyHandlers.ofString());
        String xsrf = galletas.getCookieStore().getCookies().stream()
                .filter(c -> "XSRF-TOKEN".equals(c.getName())).map(HttpCookie::getValue).findFirst().orElseThrow();
        cliente.send(HttpRequest.newBuilder(URI.create(en("/api/sesion")))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", xsrf)
                .POST(HttpRequest.BodyPublishers.ofString("{\"usuario\":\"" + quien.getUsuario()
                        + "\",\"contrasena\":\"" + UsuariosDePrueba.CONTRASENA + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());

        return cliente.send(HttpRequest.newBuilder(URI.create(en(ruta))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String en(String ruta) {
        return "http://localhost:" + puerto + ruta;
    }
}
