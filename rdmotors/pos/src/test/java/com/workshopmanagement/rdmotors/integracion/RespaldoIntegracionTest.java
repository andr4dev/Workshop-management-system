package com.workshopmanagement.rdmotors.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.workshopmanagement.rdmotors.compartido.dominio.Rol;
import com.workshopmanagement.rdmotors.respaldo.aplicacion.BajarRespaldo;
import com.workshopmanagement.rdmotors.respaldo.aplicacion.ConsultarRespaldos;
import com.workshopmanagement.rdmotors.respaldo.aplicacion.CopiaParaBajar;
import com.workshopmanagement.rdmotors.respaldo.aplicacion.EstadoDelRespaldo;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;

/**
 * El respaldo contra un Postgres real (spec 0011, RF-010 a RF-013).
 *
 * <p><b>La prueba que importa es la segunda:</b> la copia se restaura en una base vacía y los datos están. Un
 * respaldo que nunca se restauró no es un respaldo — es un archivo.
 *
 * <p>Necesita {@code pg_dump} y {@code pg_restore} del cliente de Postgres en el equipo. Si no están, la prueba se
 * salta diciendo por qué: en un equipo sin cliente de Postgres no hay nada que probar aquí.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(UsuariosDePrueba.class)
@Testcontainers
class RespaldoIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    /** El cliente de Postgres del equipo; el 17 sirve para el contenedor 17. */
    private static final Path BIN = Path.of("C:/Program Files/PostgreSQL/17/bin");
    private static final Path PG_DUMP = BIN.resolve("pg_dump.exe");
    private static final Path PG_RESTORE = BIN.resolve("pg_restore.exe");

    @TempDir
    static Path carpetaDeTrabajo;

    @DynamicPropertySource
    static void configuracionDelRespaldo(DynamicPropertyRegistry registro) {
        registro.add("rdmotors.respaldo.carpeta-de-trabajo", () -> carpetaDeTrabajo.toString());
        registro.add("rdmotors.respaldo.pg-dump", () -> PG_DUMP.toString());
    }

    @LocalServerPort int puerto;
    @Autowired UsuariosDePrueba personas;
    @Autowired BajarRespaldo bajarRespaldo;
    @Autowired ConsultarRespaldos consultarRespaldos;

    @Test
    @DisplayName("la copia se saca de verdad con pg_dump, pesa, y queda registrado que se bajó")
    void laCopiaSeSacaDeVerdad() {
        assumeThat(Files.isExecutable(PG_DUMP)).as("hace falta el cliente de Postgres en " + BIN).isTrue();

        CopiaParaBajar copia = bajarRespaldo.ejecutar(personas.administrador());

        assertThat(copia.archivo()).exists();
        assertThat(copia.bytes()).isGreaterThan(1_000);
        assertThat(copia.nombre()).startsWith("rdmotors-").endsWith(".dump");
        assertThat(copia.registro().salioBien()).isTrue();
        assertThat(copia.registro().getArchivo()).as("el nombre, no una ruta del servidor").isEqualTo(copia.nombre());

        EstadoDelRespaldo estado = consultarRespaldos.estado(personas.administrador());
        assertThat(estado.ultimaBuena().getId()).isEqualTo(copia.registro().getId());
        assertThat(estado.diasSinBajar()).isZero();
        assertThat(estado.hayQueAvisar()).isFalse();
    }

    @Test
    @DisplayName("LA PRUEBA QUE IMPORTA (RF-012): la copia se restaura en una base vacía y los datos están")
    void laCopiaSeRestaura() throws Exception {
        assumeThat(Files.isExecutable(PG_DUMP) && Files.isExecutable(PG_RESTORE))
                .as("hacen falta pg_dump y pg_restore en " + BIN).isTrue();

        // Algo que reconocer del otro lado: un usuario con un nombre único.
        String nombre = "Respaldo" + UUID.randomUUID().toString().substring(0, 8);
        personas.crear(nombre, Rol.CAJERO);

        CopiaParaBajar copia = bajarRespaldo.ejecutar(personas.administrador());

        String baseNueva = "restaurada_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        crearBase(baseNueva);
        int salida = correr(List.of(
                PG_RESTORE.toString(),
                "--host=" + POSTGRES.getHost(),
                "--port=" + POSTGRES.getFirstMappedPort(),
                "--username=" + POSTGRES.getUsername(),
                "--dbname=" + baseNueva,
                "--no-password",
                copia.archivo().toString()));
        assertThat(salida).as("pg_restore terminó con error").isZero();

        try (Connection c = DriverManager.getConnection(urlDe(baseNueva), POSTGRES.getUsername(),
                POSTGRES.getPassword());
             Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("select count(*) from usuario where nombre = '" + nombre + "'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).as("el usuario que se creó antes del respaldo tiene que estar").isEqualTo(1);
            }
            // Y el esquema completo viajó: las tablas del negocio están, con su historial de Flyway.
            try (ResultSet rs = s.executeQuery("select count(*) from flyway_schema_history where success")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(25);
            }
            try (ResultSet rs = s.executeQuery("""
                    select count(*) from information_schema.tables
                    where table_schema = 'public' and table_name in ('venta', 'compra', 'cliente', 'deuda', 'respaldo')
                    """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(5);
            }
        }
    }

    @Test
    @DisplayName("LA DESCARGA ENTREGA EL ARCHIVO Y NO DEJA NADA EN EL SERVIDOR")
    void laDescargaNoDejaNada() throws Exception {
        assumeThat(Files.isExecutable(PG_DUMP)).as("hace falta el cliente de Postgres en " + BIN).isTrue();
        Navegador ruben = entrarComo(Rol.ADMINISTRADOR);

        HttpResponse<byte[]> respuesta = ruben.bajarElArchivo();

        assertThat(respuesta.statusCode()).isEqualTo(200);
        String comoSeLlama = respuesta.headers().firstValue("Content-Disposition").orElse("");
        assertThat(comoSeLlama).contains("attachment").contains("rdmotors-").contains(".dump");
        assertThat(respuesta.body().length).as("el archivo llegó entero").isGreaterThan(1_000);
        // Un volcado en formato 'custom' empieza por PGDMP: si llegara media respuesta, esto no cuadraría.
        assertThat(new String(respuesta.body(), 0, 5)).isEqualTo("PGDMP");

        // En la nube esta carpeta es memoria del propio contenedor: dejarla llena lo tumba.
        String nombre = comoSeLlama.replaceAll(".*filename=\"([^\"]+)\".*", "$1");
        assertThat(carpetaDeTrabajo.resolve(nombre)).as("el temporal se borró al terminar de mandarlo").doesNotExist();
    }

    @Test
    @DisplayName("VARIAS DESCARGAS A LA VEZ LLEGAN TODAS ENTERAS: ninguna pisa el archivo de otra")
    void descargasSimultaneas() throws Exception {
        // Dos clics seguidos, o dos administradores a la vez. Si comparten archivo de trabajo, el primero en
        // terminar lo borra mientras el otro todavía lo está mandando, y al segundo le llega una copia truncada que
        // parece buena. Aparecía sola al probar la imagen: solo cuando las dos caían en el mismo segundo.
        assumeThat(Files.isExecutable(PG_DUMP) && Files.isExecutable(PG_RESTORE))
                .as("hacen falta pg_dump y pg_restore en " + BIN).isTrue();
        int cuantas = 4;
        List<Navegador> navegadores = new java.util.ArrayList<>();
        for (int i = 0; i < cuantas; i++) {
            navegadores.add(entrarComo(Rol.ADMINISTRADOR));
        }

        var pool = java.util.concurrent.Executors.newFixedThreadPool(cuantas);
        var salida = new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<HttpResponse<byte[]>>> pendientes = new java.util.ArrayList<>();
            for (Navegador n : navegadores) {
                pendientes.add(pool.submit(() -> {
                    salida.await();   // todas salen juntas
                    return n.bajarElArchivo();
                }));
            }
            salida.countDown();

            for (int i = 0; i < cuantas; i++) {
                HttpResponse<byte[]> respuesta = pendientes.get(i).get(2, TimeUnit.MINUTES);
                assertThat(respuesta.statusCode()).as("descarga " + i).isEqualTo(200);

                // No basta con que empiece por PGDMP: una copia truncada también empieza así. La prueba de que está
                // entera es que pg_restore sabe leerla de principio a fin.
                Path guardada = Files.createTempFile(carpetaDeTrabajo, "recibida-" + i + "-", ".dump");
                Files.write(guardada, respuesta.body());
                assertThat(correr(List.of(PG_RESTORE.toString(), "--list", guardada.toString())))
                        .as("descarga " + i + ": pg_restore no pudo leer la copia que llegó (¿truncada?)").isZero();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("bajarse la base entera es del administrador: el cajero no puede")
    void elCajeroNoSeLlevaLaBase() throws Exception {
        Navegador carolina = entrarComo(Rol.CAJERO);

        assertThat(carolina.bajarElArchivo().statusCode()).isEqualTo(403);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private Navegador entrarComo(Rol rol) throws IOException, InterruptedException {
        Usuario quien = personas.crear(rol == Rol.ADMINISTRADOR ? "Rubén" : "Carolina", rol);
        Navegador navegador = new Navegador();
        navegador.entrar(quien.getUsuario());
        return navegador;
    }

    /** Un navegador de prueba: guarda la cookie de la sesión y la manda sola, como el de verdad. */
    private final class Navegador {
        private final CookieManager cookies = new CookieManager();
        private final HttpClient cliente = HttpClient.newBuilder().cookieHandler(cookies).build();

        void entrar(String usuario) throws IOException, InterruptedException {
            // Una primera visita para recibir la cookie del token anti-CSRF, que las escrituras tienen que
            // devolver en su encabezado. Es exactamente lo que hace la página al cargar.
            cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/instalacion")).build(),
                    HttpResponse.BodyHandlers.ofString());

            HttpResponse<String> respuesta = cliente.send(HttpRequest
                    .newBuilder(URI.create("http://localhost:" + puerto + "/api/sesion"))
                    .header("Content-Type", "application/json")
                    .header("X-XSRF-TOKEN", xsrf())
                    .POST(HttpRequest.BodyPublishers.ofString("{\"usuario\":\"" + usuario + "\",\"contrasena\":\""
                            + UsuariosDePrueba.CONTRASENA + "\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(respuesta.statusCode()).as("no pudo entrar: " + respuesta.body()).isEqualTo(200);
        }

        private String xsrf() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(c -> "XSRF-TOKEN".equals(c.getName()))
                    .map(java.net.HttpCookie::getValue)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("el servidor no mandó la cookie XSRF-TOKEN"));
        }

        HttpResponse<byte[]> bajarElArchivo() throws IOException, InterruptedException {
            return cliente.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/respaldos/archivo")).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    private static void crearBase(String nombre) throws SQLException {
        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
             Statement s = c.createStatement()) {
            s.executeUpdate("create database " + nombre);
        }
    }

    private static String urlDe(String base) {
        return POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + base + "$1");
    }

    private static int correr(List<String> orden) throws IOException, InterruptedException {
        ProcessBuilder constructor = new ProcessBuilder(orden);
        constructor.environment().put("PGPASSWORD", POSTGRES.getPassword());
        constructor.redirectErrorStream(true);
        Process proceso = constructor.start();
        String salida = new String(proceso.getInputStream().readAllBytes());
        if (!proceso.waitFor(2, TimeUnit.MINUTES)) {
            proceso.destroyForcibly();
            throw new IllegalStateException("pg_restore se colgó");
        }
        if (proceso.exitValue() != 0) {
            System.out.println("pg_restore dijo: " + salida);
        }
        return proceso.exitValue();
    }
}
