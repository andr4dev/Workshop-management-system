package com.workshopmanagement.rdmotors.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * El servidor sirve la pantalla, y sabe cuándo NO servirla (spec 0011, RF-002).
 *
 * <p>La pantalla de estas pruebas es de mentira: {@code src/test/resources/static/index.html}, con una marca
 * adentro. Lo que se prueba es <b>a dónde manda el servidor cada dirección</b>, no qué dice la pantalla.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PantallaIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    /** Lo que dice el index.html de las pruebas. Si la respuesta lo trae, es la pantalla. */
    private static final String MARCA = "ESTA-ES-LA-PANTALLA";

    @LocalServerPort int puerto;

    private final HttpClient cliente = HttpClient.newHttpClient();

    private HttpResponse<String> pedir(String ruta) throws IOException, InterruptedException {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).build();
        return cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("la raíz devuelve la pantalla")
    void laRaiz() throws Exception {
        HttpResponse<String> respuesta = pedir("/");

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains(MARCA);
    }

    @Test
    @DisplayName("RECARGAR EN CUALQUIER PANTALLA DEVUELVE LA PANTALLA, no 'no encontrado'")
    void recargarAdentro() throws Exception {
        // Estas direcciones existen solo dentro del navegador: el servidor no tiene un archivo llamado 'reportes'.
        // Sin la regla del index.html, recargar aquí deja al cajero mirando una página en blanco.
        for (String ruta : new String[] { "/caja", "/reportes", "/inventario", "/ajustes/correos" }) {
            HttpResponse<String> respuesta = pedir(ruta);

            assertThat(respuesta.statusCode()).as(ruta).isEqualTo(200);
            assertThat(respuesta.body()).as(ruta).contains(MARCA);
        }
    }

    @Test
    @DisplayName("UNA DIRECCIÓN DE LA API QUE NO EXISTE NO DEVUELVE LA PANTALLA, aunque sea pública")
    void laApiNuncaCaeEnLaPantalla() throws Exception {
        // La trampa de todo esto. '/api/instalacion/**' es público (spec 0004, RF-018), así que esta petición pasa
        // la seguridad y llega hasta el final sin que ningún controlador la atienda. Si la regla del index.html se
        // escribe de más, el navegador recibe la PÁGINA con estado 200 cuando estaba esperando un JSON, y se queda
        // colgado sin decir por qué. El error aparecería meses después y en cualquier pantalla.
        HttpResponse<String> respuesta = pedir("/api/instalacion/no-existe");

        assertThat(respuesta.statusCode()).isEqualTo(404);
        assertThat(respuesta.body()).doesNotContain(MARCA);
    }

    @Test
    @DisplayName("lo que pide sesión sigue pidiendo sesión: no se le devuelve la pantalla a quien no entró")
    void loProtegidoSigueProtegido() throws Exception {
        HttpResponse<String> respuesta = pedir("/api/ventas");

        assertThat(respuesta.statusCode()).isEqualTo(401);
        assertThat(respuesta.body()).doesNotContain(MARCA);
    }

    @Test
    @DisplayName("un archivo que no está es 'no encontrado', no la pantalla")
    void unArchivoQueNoEsta() throws Exception {
        // Devolverle la página a una etiqueta <script> no arregla nada: esconde que falta el archivo.
        HttpResponse<String> respuesta = pedir("/assets/no-existe-a1b2c3.js");

        assertThat(respuesta.statusCode()).isEqualTo(404);
        assertThat(respuesta.body()).doesNotContain(MARCA);
    }
}
