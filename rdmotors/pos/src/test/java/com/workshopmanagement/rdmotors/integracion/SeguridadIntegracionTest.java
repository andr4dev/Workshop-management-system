package com.workshopmanagement.rdmotors.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.Rol;
import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.ClaveDelToken;
import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.TokenDeSesion;
import com.workshopmanagement.rdmotors.compras.aplicacion.ComandoRegistrarCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarProveedor;
import com.workshopmanagement.rdmotors.inventario.aplicacion.ComandoCrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.aplicacion.CrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioCategorias;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.Contrasenas;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;

/**
 * Entrar, la sesión y lo que el servidor exige, con el servidor de verdad y un Postgres real (spec 0004).
 *
 * <p>Por HTTP y no llamando a los casos de uso: lo que se prueba aquí es justo lo que está entre el navegador y los
 * casos de uso (la cookie, la firma, el vencimiento, el token anti-CSRF). Las pruebas van en orden porque la primera
 * necesita la base sin usuarios.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(UsuariosDePrueba.class)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SeguridadIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final String ADMIN = "ruben";
    private static final String CLAVE_ADMIN = "secreta-de-ruben";

    @LocalServerPort int puerto;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClaveDelToken clave;
    @Autowired RepositorioUsuarios usuarios;
    @Autowired Contrasenas contrasenas;
    @Autowired UsuariosDePrueba personas;
    @Autowired CrearRepuesto crearRepuesto;
    @Autowired RegistrarProveedor registrarProveedor;
    @Autowired RegistrarCompra registrarCompra;
    @Autowired RepositorioCategorias categorias;

    // ─────────────────────────────────────────────────────────────────────────
    //  Un navegador de prueba: guarda las cookies y manda el token anti-CSRF
    // ─────────────────────────────────────────────────────────────────────────

    private final class Navegador {
        private final CookieManager cookies = new CookieManager();
        private final HttpClient cliente = HttpClient.newBuilder().cookieHandler(cookies).build();

        HttpResponse<String> pedir(String metodo, String ruta, String cuerpo, boolean conCsrf, String... encabezados)
                throws IOException, InterruptedException {
            if (conCsrf && xsrf() == null) {
                pedir("GET", "/api/instalacion", null, false);
            }
            HttpRequest.Builder p = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                    .header("Content-Type", "application/json")
                    .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(cuerpo));
            if (conCsrf) {
                p.header("X-XSRF-TOKEN", xsrf());
            }
            for (int i = 0; i < encabezados.length; i += 2) {
                p.header(encabezados[i], encabezados[i + 1]);
            }
            return cliente.send(p.build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> get(String ruta) throws IOException, InterruptedException {
            return pedir("GET", ruta, null, false);
        }

        HttpResponse<String> escribir(String metodo, String ruta, String cuerpo) throws IOException, InterruptedException {
            return pedir(metodo, ruta, cuerpo, true);
        }

        HttpResponse<String> entrar(String usuario, String contrasena) throws IOException, InterruptedException {
            return escribir("POST", "/api/sesion",
                    "{\"usuario\":\"" + usuario + "\",\"contrasena\":\"" + contrasena + "\"}");
        }

        String xsrf() {
            return cookie("XSRF-TOKEN");
        }

        String cookie(String nombre) {
            return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals(nombre))
                    .map(HttpCookie::getValue).findFirst().orElse(null);
        }
    }

    private Navegador adentroComoAdministrador() throws Exception {
        Navegador n = new Navegador();
        assertThat(n.entrar(ADMIN, CLAVE_ADMIN).statusCode()).isEqualTo(200);
        return n;
    }

    /** Pide con una cookie de sesión puesta a mano (sin el navegador de prueba). */
    private HttpResponse<String> conCookie(String ruta, String token) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                        .header("Cookie", TokenDeSesion.COOKIE + "=" + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String token(UUID usuarioId, long version, Instant emitido, Instant vence, byte[] claveDeFirma) {
        var codificador = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(claveDeFirma, "HmacSHA256"))
                .algorithm(MacAlgorithm.HS256).build();
        return codificador.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer(TokenDeSesion.EMISOR).subject(usuarioId.toString())
                        .claim(TokenDeSesion.VERSION, version).issuedAt(emitido).expiresAt(vence).build()))
                .getTokenValue();
    }

    /** Una sesión de esa persona, ya con la contraseña cambiada. */
    private Navegador entrarComo(Usuario usuario) throws Exception {
        Navegador n = new Navegador();
        assertThat(n.entrar(usuario.getUsuario(), UsuariosDePrueba.CONTRASENA).statusCode()).isEqualTo(200);
        return n;
    }

    private void cerrarTurnosAbiertos() {
        jdbc.update("""
                update turno_caja set estado = 'CERRADO', cerrado_en = now(), cerrado_por_id = abierto_por_id,
                    ventas_efectivo = 0, ventas_transferencia = 0, ventas_fiado = 0, descuentos = 0,
                    devoluciones_efectivo = 0, abonos_efectivo = 0, abonos_transferencia = 0,
                    gastos_cajon = 0, retiros = 0, compras_cajon = 0, esperado = fondo, contado = fondo, diferencia = 0
                where estado = 'ABIERTO'
                """);
    }

    private UUID uno(String sql) {
        return jdbc.queryForObject(sql, UUID.class);
    }

    /**
     * Un campo de costo en el JSON: la clave, no la palabra dentro de un texto. La {@code diferencia} del arqueo no
     * cuenta: es plata del cajón, y el cajero la ve.
     */
    private static final Pattern CAMPO_DE_COSTO = Pattern.compile("\"(costo\\w*|valor|utilidad|margen\\w*)\"\\s*:");

    private UUID idDe(String usuario) {
        return usuarios.buscarPorUsuario(usuario).orElseThrow().getId();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Las pruebas
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("RF-018: el primer administrador pedido dos veces a la vez: queda uno, y la segunda vez ya no existe la opción")
    void primerAdministradorALaVez() throws Exception {
        assertThat(new Navegador().get("/api/instalacion").body()).contains("\"faltaAdministrador\":true");
        String cuerpo = "{\"nombre\":\"Rubén Díaz\",\"usuario\":\"" + ADMIN + "\",\"contrasena\":\"" + CLAVE_ADMIN + "\"}";
        Navegador a = new Navegador();
        Navegador b = new Navegador();
        a.get("/api/instalacion");
        b.get("/api/instalacion");

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (Navegador n : List.of(a, b)) {
            resultados.add(hilos.submit(() -> {
                largada.await();
                return n.escribir("POST", "/api/instalacion/administrador", cuerpo).statusCode();
            }));
        }
        largada.countDown();
        List<Integer> estados = new ArrayList<>();
        for (Future<Integer> r : resultados) {
            estados.add(r.get());
        }
        hilos.shutdown();

        assertThat(estados).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("select count(*) from usuario", Integer.class)).isEqualTo(1);
        assertThat(new Navegador().escribir("POST", "/api/instalacion/administrador", cuerpo).statusCode()).isEqualTo(409);
        assertThat(new Navegador().get("/api/instalacion").body()).contains("\"faltaAdministrador\":false");
    }

    @Test
    @Order(2)
    @DisplayName("RF-004: sin entrar, la API responde 401 SIN_SESION en todo, lecturas y escrituras")
    void sinSesion() throws Exception {
        Navegador n = new Navegador();
        for (String ruta : List.of("/api/inventario", "/api/reportes/resultados?desde=2026-09-01&hasta=2026-09-01",
                "/api/turnos/abierto", "/api/sesion")) {
            HttpResponse<String> r = n.get(ruta);
            assertThat(r.statusCode()).as(ruta).isEqualTo(401);
            assertThat(r.body()).as(ruta).contains("\"codigo\":\"SIN_SESION\"");
        }
        assertThat(n.escribir("POST", "/api/turnos", "{\"fondo\":1000}").statusCode()).isEqualTo(401);
    }

    @Test
    @Order(3)
    @DisplayName("RF-005: entrar deja una cookie HttpOnly, SameSite=Strict, de 24 horas; el token no viene en el cuerpo")
    void cookieDeSesion() throws Exception {
        Navegador n = new Navegador();
        HttpResponse<String> r = n.entrar(ADMIN, CLAVE_ADMIN);

        String cookie = r.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith(TokenDeSesion.COOKIE + "=")).findFirst().orElseThrow();
        assertThat(cookie).contains("HttpOnly").contains("SameSite=Strict").contains("Path=/").contains("Max-Age=86400");
        assertThat(r.body()).contains("\"nombre\":\"Rubén Díaz\"").contains("\"rol\":\"ADMINISTRADOR\"")
                .doesNotContain(n.cookie(TokenDeSesion.COOKIE));
        assertThat(n.get("/api/sesion").statusCode()).isEqualTo(200);
        assertThat(n.get("/api/inventario").statusCode()).isEqualTo(200);
    }

    @Test
    @Order(4)
    @DisplayName("un token con la firma alterada, firmado con otra clave, o vencido: 401")
    void tokensQueNoValen() throws Exception {
        Navegador n = adentroComoAdministrador();
        String bueno = n.cookie(TokenDeSesion.COOKIE);
        String alterado = bueno.substring(0, bueno.length() - 2) + (bueno.endsWith("A") ? "BB" : "AA");
        UUID id = idDe(ADMIN);
        long version = usuarios.buscar(id).orElseThrow().getVersionSesion();
        Instant ahora = Instant.now();

        assertThat(conCookie("/api/inventario", bueno).statusCode()).isEqualTo(200);
        assertThat(conCookie("/api/inventario", alterado).statusCode()).isEqualTo(401);
        assertThat(conCookie("/api/inventario", token(id, version, ahora, ahora.plusSeconds(3600),
                "otra-clave-de-la-que-no-sabe-nada-el-servidor".getBytes())).statusCode()).isEqualTo(401);
        assertThat(conCookie("/api/inventario", token(id, version, ahora.minusSeconds(90_000), ahora.minusSeconds(300),
                clave.clave().getEncoded())).statusCode()).as("vencido hace 5 minutos").isEqualTo(401);
        assertThat(conCookie("/api/inventario", token(id, version, ahora, ahora.plusSeconds(3600),
                clave.clave().getEncoded())).statusCode()).as("el mismo, vigente").isEqualTo(200);
    }

    @Test
    @Order(5)
    @DisplayName("una escritura sin el token anti-CSRF: 403 CSRF, aunque la sesión sea buena")
    void csrf() throws Exception {
        Navegador n = adentroComoAdministrador();

        HttpResponse<String> sinCsrf = n.pedir("POST", "/api/turnos", "{\"fondo\":100000}", false);

        assertThat(sinCsrf.statusCode()).isEqualTo(403);
        assertThat(sinCsrf.body()).contains("\"codigo\":\"CSRF\"");
        assertThat(jdbc.queryForObject("select count(*) from turno_caja", Integer.class)).isZero();
    }

    @Test
    @Order(6)
    @DisplayName("RF-007: el \"quién\" lo decide la sesión: un X-Usuario-Id que mande el navegador se ignora")
    void elQuienLoDecideElServidor() throws Exception {
        Navegador n = adentroComoAdministrador();

        HttpResponse<String> r = n.pedir("POST", "/api/turnos", "{\"fondo\":100000}", true,
                "X-Usuario-Id", UUID.randomUUID().toString());

        assertThat(r.statusCode()).isEqualTo(201);
        assertThat(jdbc.queryForObject("select abierto_por_id from turno_caja where estado = 'ABIERTO'", UUID.class))
                .isEqualTo(idDe(ADMIN));
    }

    @Test
    @Order(7)
    @DisplayName("RF-014: con contraseña inicial solo puede ver quién es y cambiarla; al cambiarla, entra a todo y la cookie vieja deja de valer")
    void contrasenaInicial() throws Exception {
        usuarios.guardar(Usuario.nuevo("carolina", "Carolina", Rol.CAJERO, contrasenas.hash("temporal-1"), true,
                Instant.now()));
        Navegador n = new Navegador();
        assertThat(n.entrar("Carolina", "temporal-1").body()).contains("\"debeCambiarContrasena\":true");
        String cookieVieja = n.cookie(TokenDeSesion.COOKIE);

        HttpResponse<String> antes = n.get("/api/inventario");
        assertThat(antes.statusCode()).isEqualTo(403);
        assertThat(antes.body()).contains("\"codigo\":\"DEBE_CAMBIAR_CONTRASENA\"");
        assertThat(n.get("/api/sesion").statusCode()).isEqualTo(200);

        HttpResponse<String> cambio = n.escribir("PUT", "/api/sesion/contrasena",
                "{\"actual\":\"temporal-1\",\"nueva\":\"la-de-carolina\"}");

        assertThat(cambio.statusCode()).isEqualTo(200);
        assertThat(cambio.body()).contains("\"debeCambiarContrasena\":false");
        assertThat(n.get("/api/inventario").statusCode()).isEqualTo(200);
        assertThat(conCookie("/api/inventario", cookieVieja).statusCode()).as("la versión vieja ya no vale").isEqualTo(401);
    }

    @Test
    @Order(8)
    @DisplayName("RF-003: contraseña mal responde 401 CREDENCIALES, no SIN_SESION; al quinto, 429 aun con la correcta")
    void credencialesYBloqueo() throws Exception {
        usuarios.guardar(Usuario.nuevo("ines", "Inés", Rol.CAJERO, contrasenas.hash("la-de-ines"), false, Instant.now()));
        Navegador n = new Navegador();

        HttpResponse<String> mala = n.entrar("ines", "no-es-esa");
        assertThat(mala.statusCode()).isEqualTo(401);
        assertThat(mala.body()).contains("\"codigo\":\"CREDENCIALES\"").contains("Usuario o contraseña incorrectos");
        assertThat(n.entrar("nadie-se-llama-asi", "no-es-esa").body()).contains("Usuario o contraseña incorrectos");
        for (int i = 0; i < 4; i++) {
            n.entrar("ines", "no-es-esa");
        }

        assertThat(n.entrar("ines", "la-de-ines").statusCode()).isEqualTo(429);
    }

    @Test
    @Order(9)
    @DisplayName("salir borra la cookie; después, 401. Y una cookie vieja no impide volver a entrar")
    void salirYVolverAEntrar() throws Exception {
        Navegador n = adentroComoAdministrador();

        HttpResponse<String> salir = n.escribir("DELETE", "/api/sesion", null);

        assertThat(salir.statusCode()).isEqualTo(204);
        assertThat(salir.headers().allValues("Set-Cookie")).anyMatch(c -> c.startsWith(TokenDeSesion.COOKIE + "=;")
                && c.contains("Max-Age=0"));
        assertThat(n.get("/api/sesion").statusCode()).isEqualTo(401);

        HttpResponse<String> conCookieRota = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + puerto + "/api/sesion"))
                .header("Content-Type", "application/json")
                .header("Cookie", TokenDeSesion.COOKIE + "=esto.no.vale; XSRF-TOKEN=abc")
                .header("X-XSRF-TOKEN", "abc")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"usuario\":\"" + ADMIN + "\",\"contrasena\":\"" + CLAVE_ADMIN + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(conCookieRota.statusCode()).isEqualTo(200);
    }

    @Test
    @Order(10)
    @DisplayName("RF-008 y RF-023: la venta que cobra Carolina queda a su nombre aunque el navegador mande otro \"quién\", y la respuesta trae su nombre para el comprobante")
    void laVentaEsDeQuienEntro() throws Exception {
        Actor administrador = usuarios.buscarPorUsuario(ADMIN).orElseThrow().actor();
        Variante filtro = crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo("FILTRO DE ACEITE",
                categorias.activas().getFirst().getId(), null, "FA-SESION", "INOKI", Dinero.de(13_000), 1), administrador);
        registrarCompra.ejecutar(new ComandoRegistrarCompra(
                registrarProveedor.ejecutar("Jotapartes", null, null, administrador).getId(), LocalDate.now(), null,
                FormaPago.EFECTIVO, null, administrador,
                List.of(ComandoRegistrarCompra.Linea.porTotal(filtro.getId(), 5, Dinero.de(40_000), null))));
        // El turno que abrió el administrador en otra prueba se cierra: Carolina vende en el suyo.
        jdbc.update("""
                update turno_caja set estado = 'CERRADO', cerrado_en = now(), cerrado_por_id = abierto_por_id,
                    ventas_efectivo = 0, ventas_transferencia = 0, ventas_fiado = 0, descuentos = 0,
                    devoluciones_efectivo = 0, abonos_efectivo = 0, abonos_transferencia = 0,
                    gastos_cajon = 0, retiros = 0, compras_cajon = 0, esperado = fondo, contado = fondo, diferencia = 0
                where estado = 'ABIERTO'
                """);
        Usuario carolina = personas.crear("Carolina", Rol.CAJERO);
        Navegador n = new Navegador();
        assertThat(n.entrar(carolina.getUsuario(), UsuariosDePrueba.CONTRASENA).statusCode()).isEqualTo(200);
        assertThat(n.escribir("POST", "/api/turnos", "{\"fondo\":100000}").statusCode()).isEqualTo(201);

        UUID llave = UUID.randomUUID();
        HttpResponse<String> r = n.pedir("POST", "/api/ventas", """
                {"llave":"%s","renglones":[{"varianteId":"%s","cantidad":1,"precioVisto":13000}],
                 "pagos":[{"forma":"EFECTIVO","monto":13000}]}
                """.formatted(llave, filtro.getId()), true, "X-Usuario-Id", administrador.id().toString());

        assertThat(r.statusCode()).isEqualTo(201);
        assertThat(r.body()).contains("\"vendidoPor\":{\"id\":\"" + carolina.getId() + "\",\"nombre\":\"Carolina\"}");
        assertThat(jdbc.queryForObject("select vendido_por_id from venta where llave_idempotencia = ?", UUID.class,
                llave)).isEqualTo(carolina.getId());
    }

    @Test
    @Order(11)
    @DisplayName("RF-011: con sesión de cajero ninguna consulta trae costos, ni el aviso de pérdida sus cifras; el administrador sí los ve")
    void elCajeroNoVeCostos() throws Exception {
        cerrarTurnosAbiertos();
        Navegador ines = entrarComo(personas.crear("Inés", Rol.CAJERO));
        UUID repuesto = uno("select id from variante where codigo = 'FA-SESION'");
        assertThat(ines.escribir("POST", "/api/turnos", "{\"fondo\":50000}").statusCode()).isEqualTo(201);
        HttpResponse<String> cobro = ines.escribir("POST", "/api/ventas", """
                {"llave":"%s","renglones":[{"varianteId":"%s","cantidad":1,"precioVisto":13000}],
                 "pagos":[{"forma":"EFECTIVO","monto":13000}]}
                """.formatted(UUID.randomUUID(), repuesto));
        assertThat(cobro.statusCode()).isEqualTo(201);
        UUID turno = uno("select id from turno_caja where estado = 'ABIERTO'");
        UUID venta = uno("select id from venta where turno_id = '" + turno + "'");

        for (String ruta : List.of("/api/inventario", "/api/inventario?conStockPrimero=true", "/api/inventario/resumen",
                "/api/inventario/categorias", "/api/repuestos?q=filtro", "/api/repuestos?codigo=FA-SESION",
                "/api/repuestos/" + repuesto, "/api/repuestos/" + repuesto + "/kardex",
                "/api/repuestos/" + repuesto + "/correcciones", "/api/categorias", "/api/productos?q=filtro",
                "/api/turnos/abierto", "/api/turnos/" + turno, "/api/turnos?estado=CERRADO",
                "/api/ventas?turno=abierto", "/api/ventas/" + venta, "/api/categorias-gasto", "/api/cuentas",
                "/api/tienda", "/api/sesion")) {
            HttpResponse<String> r = ines.get(ruta);
            assertThat(r.statusCode()).as(ruta).isEqualTo(200);
            assertThat(CAMPO_DE_COSTO.matcher(r.body()).find()).as(ruta + " → " + r.body()).isFalse();
        }

        String aPerdida = """
                {"renglones":[{"varianteId":"%s","cantidad":1,"precioVisto":13000}],
                 "descuento":{"modo":"MONTO","valor":10000}}
                """.formatted(repuesto);
        HttpResponse<String> avisoCajero = ines.escribir("POST", "/api/ventas/aviso-de-perdida", aPerdida);
        assertThat(avisoCajero.body()).isEqualTo("{\"bajoCosto\":true,\"sinCosto\":0}");

        Navegador admin = adentroComoAdministrador();
        assertThat(admin.get("/api/repuestos/" + repuesto).body()).contains("\"costoPromedio\":");
        assertThat(admin.get("/api/inventario/resumen").body()).contains("\"valor\":");
        assertThat(admin.escribir("POST", "/api/ventas/aviso-de-perdida", aPerdida).body())
                .contains("\"bajoCosto\":true").contains("\"costo\":8000").contains("\"cobrado\":3000")
                .contains("\"diferencia\":5000");
    }

    @Test
    @Order(12)
    @DisplayName("RF-010: cada cosa del administrador responde 403 NO_PERMITIDO al cajero, también por fuera de la pantalla")
    void loDelAdministrador() throws Exception {
        Navegador admin = adentroComoAdministrador();
        HttpResponse<String> cuenta = admin.escribir("POST", "/api/cuentas", "{\"nombre\":\"Nequi del dueño\"}");
        assertThat(cuenta.statusCode()).isEqualTo(201);
        UUID cuentaId = UUID.fromString(cuenta.body().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
        UUID repuesto = uno("select id from variante where codigo = 'FA-SESION'");
        UUID proveedor = uno("select id from proveedor limit 1");
        UUID compra = uno("select id from compra limit 1");
        UUID categoria = uno("select id from categoria_gasto where nombre = 'Otros'");
        String hoy = LocalDate.now().toString();
        long compras = jdbc.queryForObject("select count(*) from compra", Long.class);
        Navegador ines = entrarComo(personas.crear("Inés", Rol.CAJERO));

        String[][] pedidos = {
                {"POST", "/api/compras", """
                        {"llave":"%s","proveedorId":"%s","fechaDocumento":"%s","formaPago":"EFECTIVO",
                         "lineas":[{"varianteId":"%s","cantidad":1,"modo":"TOTAL","costoTotal":1000}],
                         "pagadaDeCaja":false}
                        """.formatted(UUID.randomUUID(), proveedor, hoy, repuesto)},
                {"POST", "/api/compras/" + compra + "/correcciones", """
                        {"version":0,"motivo":"número mal","proveedorId":"%s","fechaDocumento":"%s","formaPago":"EFECTIVO"}
                        """.formatted(proveedor, hoy)},
                {"POST", "/api/compras/" + compra + "/anulacion", "{\"version\":0,\"motivo\":\"se registró dos veces\"}"},
                {"GET", "/api/compras", null},
                {"GET", "/api/compras/totales", null},
                {"GET", "/api/compras/" + compra, null},
                {"POST", "/api/repuestos", """
                        {"nombreProducto":"PASTILLAS","codigo":"PAS-CAJERO","marcaRepuesto":"CBI","precio":12000,"stockMinimo":1}
                        """},
                {"PUT", "/api/repuestos/" + repuesto, """
                        {"nombreProducto":"FILTRO","codigo":"FA-SESION","marcaRepuesto":"INOKI","precio":1000,"stockMinimo":1}
                        """},
                {"GET", "/api/proveedores", null},
                {"POST", "/api/proveedores", "{\"nombre\":\"Importadora Nueva\"}"},
                {"POST", "/api/cuentas", "{\"nombre\":\"Daviplata\"}"},
                {"POST", "/api/cuentas/" + cuentaId + "/desactivacion", null},
                {"POST", "/api/categorias-gasto", "{\"nombre\":\"Publicidad\",\"naturaleza\":\"GASTO\",\"mensual\":false}"},
                {"PUT", "/api/categorias-gasto/" + categoria, "{\"nombre\":\"Otros gastos\",\"mensual\":false}"},
                {"POST", "/api/categorias-gasto/" + categoria + "/desactivacion", null},
                {"PUT", "/api/tienda", "{\"nombreComercial\":\"OTRA TIENDA\"}"},
                {"GET", "/api/reportes/resultados?desde=" + hoy + "&hasta=" + hoy, null},
                {"GET", "/api/gastos", null},
                {"GET", "/api/gastos/totales", null},
                {"POST", "/api/gastos", """
                        {"llave":"%s","categoriaId":"%s","monto":800000,"descripcion":"Arriendo","delCajon":false,
                         "formaPago":"TRANSFERENCIA","cuentaId":"%s","fecha":"%s","delMes":false,"confirmado":false}
                        """.formatted(UUID.randomUUID(), categoria, cuentaId, hoy)},
        };
        for (String[] p : pedidos) {
            HttpResponse<String> r = p[0].equals("GET") ? ines.get(p[1]) : ines.escribir(p[0], p[1], p[2]);
            assertThat(r.statusCode()).as(p[0] + " " + p[1] + " → " + r.body()).isEqualTo(403);
            assertThat(r.body()).as(p[0] + " " + p[1]).contains("\"codigo\":\"NO_PERMITIDO\"");
        }

        assertThat(jdbc.queryForObject("select count(*) from compra", Long.class)).isEqualTo(compras);
        assertThat(jdbc.queryForObject("select precio from variante where id = ?", Long.class, repuesto)).isEqualTo(13_000L);
        assertThat(jdbc.queryForObject("select activa from cuenta_pago where id = ?", Boolean.class, cuentaId)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from variante where codigo = 'PAS-CAJERO'", Long.class)).isZero();
        assertThat(admin.get("/api/proveedores").statusCode()).isEqualTo(200);
    }

    @Test
    @Order(13)
    @DisplayName("RF-012: otro cajero no vende en el turno de Inés: 403 TURNO_AJENO con el nombre de quien lo abrió")
    void turnoAjeno() throws Exception {
        cerrarTurnosAbiertos();
        Navegador ines = entrarComo(personas.crear("Inés", Rol.CAJERO));
        Navegador andres = entrarComo(personas.crear("Andrés", Rol.CAJERO));
        UUID repuesto = uno("select id from variante where codigo = 'FA-SESION'");
        assertThat(ines.escribir("POST", "/api/turnos", "{\"fondo\":50000}").statusCode()).isEqualTo(201);
        long ventas = jdbc.queryForObject("select count(*) from venta", Long.class);

        HttpResponse<String> r = andres.escribir("POST", "/api/ventas", """
                {"llave":"%s","renglones":[{"varianteId":"%s","cantidad":1,"precioVisto":13000}],
                 "pagos":[{"forma":"EFECTIVO","monto":13000}]}
                """.formatted(UUID.randomUUID(), repuesto));

        assertThat(r.statusCode()).isEqualTo(403);
        assertThat(r.body()).contains("\"codigo\":\"TURNO_AJENO\"")
                .contains("El turno abierto es de Inés: lo cierra Inés o un administrador");
        assertThat(jdbc.queryForObject("select count(*) from venta", Long.class)).isEqualTo(ventas);
        assertThat(andres.get("/api/turnos/abierto").body()).contains("\"abiertoPor\":{").contains("\"nombre\":\"Inés\"");
        assertThat(andres.get("/api/ventas?turno=abierto").body()).isEqualTo("[]");
    }

    @Test
    @Order(14)
    @DisplayName("RF-015 y RF-017: al desactivar a Inés su sesión abierta se cae en la siguiente petición; al restablecerle la contraseña, la vieja tampoco sirve")
    void desactivarYRestablecer() throws Exception {
        Navegador admin = adentroComoAdministrador();
        Usuario ines = personas.crear("Inés", Rol.CAJERO);
        Navegador suya = entrarComo(ines);
        assertThat(suya.get("/api/inventario").statusCode()).as("antes de desactivarla").isEqualTo(200);

        assertThat(admin.escribir("POST", "/api/usuarios/" + ines.getId() + "/desactivacion", null).statusCode())
                .isEqualTo(200);

        HttpResponse<String> despues = suya.get("/api/inventario");
        assertThat(despues.statusCode()).isEqualTo(401);
        assertThat(despues.body()).contains("\"codigo\":\"SIN_SESION\"");
        assertThat(new Navegador().entrar(ines.getUsuario(), UsuariosDePrueba.CONTRASENA).statusCode()).isEqualTo(401);

        assertThat(admin.escribir("POST", "/api/usuarios/" + ines.getId() + "/activacion", null).statusCode())
                .isEqualTo(200);
        Navegador deVuelta = entrarComo(ines);
        String temporal = admin.escribir("POST", "/api/usuarios/" + ines.getId() + "/restablecimiento", null)
                .body().replaceAll(".*\"contrasenaTemporal\":\"([^\"]+)\".*", "$1");

        assertThat(deVuelta.get("/api/inventario").statusCode()).as("la sesión de antes del restablecimiento").isEqualTo(401);
        assertThat(new Navegador().entrar(ines.getUsuario(), UsuariosDePrueba.CONTRASENA).statusCode())
                .as("la contraseña vieja").isEqualTo(401);
        Navegador conTemporal = new Navegador();
        assertThat(conTemporal.entrar(ines.getUsuario(), temporal).body()).contains("\"debeCambiarContrasena\":true");
        assertThat(conTemporal.get("/api/inventario").statusCode()).as("hasta cambiarla, nada más").isEqualTo(403);
    }

    @Test
    @Order(15)
    @DisplayName("RF-016: dos administradores se quitan el rol a la vez y queda uno; y un cajero no entra a Usuarios")
    void siempreQuedaUnAdministrador() throws Exception {
        jdbc.update("update usuario set rol = 'CAJERO' where rol = 'ADMINISTRADOR' and usuario <> ?", ADMIN);
        Navegador ruben = adentroComoAdministrador();
        HttpResponse<String> creada = ruben.escribir("POST", "/api/usuarios", """
                {"nombre":"Ana","usuario":"ana-%s","rol":"ADMINISTRADOR","contrasena":"temporal-de-ana"}
                """.formatted(UUID.randomUUID().toString().substring(0, 6)));
        assertThat(creada.statusCode()).isEqualTo(201);
        UUID anaId = UUID.fromString(creada.body().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
        String usuarioDeAna = creada.body().replaceAll(".*\"usuario\":\"([^\"]+)\".*", "$1");
        Navegador ana = new Navegador();
        assertThat(ana.entrar(usuarioDeAna, "temporal-de-ana").body()).contains("\"debeCambiarContrasena\":true");
        assertThat(ana.escribir("PUT", "/api/sesion/contrasena",
                "{\"actual\":\"temporal-de-ana\",\"nueva\":\"la-de-ana\"}").statusCode()).isEqualTo(200);
        UUID rubenId = idDe(ADMIN);

        // Cada uno le quita el rol al otro, a la vez: uno gana y el otro recibe la regla.
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> respuestas = new ArrayList<>();
        respuestas.add(hilos.submit(() -> {
            largada.await();
            return ruben.escribir("PUT", "/api/usuarios/" + anaId + "/rol", "{\"rol\":\"CAJERO\"}");
        }));
        respuestas.add(hilos.submit(() -> {
            largada.await();
            return ana.escribir("PUT", "/api/usuarios/" + rubenId + "/rol", "{\"rol\":\"CAJERO\"}");
        }));
        largada.countDown();
        List<Integer> estados = new ArrayList<>();
        List<String> cuerpos = new ArrayList<>();
        for (Future<HttpResponse<String>> r : respuestas) {
            estados.add(r.get().statusCode());
            cuerpos.add(r.get().body());
        }
        hilos.shutdown();

        assertThat(estados).containsExactlyInAnyOrder(200, 422);
        assertThat(cuerpos).anyMatch(c -> c.contains("Tiene que quedar al menos un administrador activo"));
        assertThat(jdbc.queryForObject(
                "select count(*) from usuario where rol = 'ADMINISTRADOR' and activo", Long.class)).isEqualTo(1);

        Navegador cajero = entrarComo(personas.crear("Inés", Rol.CAJERO));
        assertThat(cajero.get("/api/usuarios").statusCode()).isEqualTo(403);
        assertThat(cajero.escribir("POST", "/api/usuarios", """
                {"nombre":"Otro","usuario":"otro","rol":"CAJERO","contrasena":"temporal-1"}
                """).statusCode()).isEqualTo(403);

        // Esta prueba deja a uno de los dos como cajero: se devuelve el administrador de siempre para las que siguen.
        jdbc.update("update usuario set rol = 'ADMINISTRADOR' where usuario = ?", ADMIN);
    }

    @Test
    @Order(16)
    @DisplayName("RF-025: el registro de entradas guarda lo que se escribió y si entró, con la IP y el navegador; el cajero no lo ve")
    void registroDeEntradas() throws Exception {
        Usuario ines = personas.crear("Inés", Rol.CAJERO);
        entrarComo(ines);
        assertThat(new Navegador().entrar("nadie-se-llama-asi", "probando").statusCode()).isEqualTo(401);

        Navegador admin = adentroComoAdministrador();
        String cuerpo = admin.get("/api/usuarios/entradas").body();

        assertThat(cuerpo).contains("\"usuarioEscrito\":\"nadie-se-llama-asi\"").contains("\"exito\":false");
        assertThat(cuerpo).contains("\"usuarioEscrito\":\"" + ines.getUsuario() + "\"").contains("\"exito\":true");
        assertThat(cuerpo).contains("\"nombre\":\"Inés\"").contains("\"ip\":\"127.0.0.1\"");
        assertThat(cuerpo).doesNotContain(UsuariosDePrueba.CONTRASENA).doesNotContain("probando");
        // El intento con un usuario que no existe no inventa a nadie.
        assertThat(cuerpo).contains("{\"usuarioEscrito\":\"nadie-se-llama-asi\",\"quien\":null");

        Navegador cajero = entrarComo(personas.crear("Andrés", Rol.CAJERO));
        assertThat(cajero.get("/api/usuarios/entradas").statusCode()).isEqualTo(403);
    }

    @Test
    @Order(17)
    @DisplayName("Spec 0008: la Cartera la ven los dos roles; el cajero crea y completa clientes, pero corregir y cerrar el fiado le responde 403")
    void laCarteraYLoDelAdministrador() throws Exception {
        Navegador ines = entrarComo(personas.crear("Inés", Rol.CAJERO));
        String cedula = String.valueOf(10_000_000 + (long) (Math.random() * 89_999_999));
        HttpResponse<String> creado = ines.escribir("POST", "/api/clientes",
                "{\"nombre\":\"Juan Pérez\",\"documento\":\"" + cedula + "\"}");
        assertThat(creado.statusCode()).as(creado.body()).isEqualTo(201);
        UUID cliente = UUID.fromString(creado.body().replaceAll("^.*?\"id\":\"([^\"]+)\".*", "$1"));

        assertThat(ines.get("/api/cartera").statusCode()).isEqualTo(200);
        assertThat(ines.get("/api/cartera?vista=HISTORIAL&q=juan").statusCode()).isEqualTo(200);
        assertThat(ines.get("/api/clientes/" + cliente).statusCode()).isEqualTo(200);
        // Completar lo que falta, sí.
        assertThat(ines.escribir("PUT", "/api/clientes/" + cliente,
                "{\"nombre\":\"Juan Pérez\",\"documento\":\"" + cedula + "\",\"celular\":\"3001234567\"}")
                .statusCode()).isEqualTo(200);

        String[][] pedidos = {
                {"PUT", "/api/clientes/" + cliente, "{\"nombre\":\"Juan Pérez\",\"documento\":\"1111111\",\"celular\":\"3001234567\"}"},
                {"POST", "/api/clientes/" + cliente + "/cierre-del-fiado", "{\"motivo\":\"No paga\"}"},
                {"POST", "/api/clientes/" + cliente + "/apertura-del-fiado", null},
        };
        for (String[] p : pedidos) {
            HttpResponse<String> r = ines.escribir(p[0], p[1], p[2]);
            assertThat(r.statusCode()).as(p[0] + " " + p[1] + " → " + r.body()).isEqualTo(403);
            assertThat(r.body()).as(p[0] + " " + p[1]).contains("\"codigo\":\"NO_PERMITIDO\"");
        }
        assertThat(jdbc.queryForObject("select documento_normalizado from cliente where id = ?", String.class, cliente))
                .isEqualTo(cedula);
        assertThat(jdbc.queryForObject("select fiado_cerrado from cliente where id = ?", Boolean.class, cliente)).isFalse();

        // Abonar sí es del cajero: lo que responde es "no debe nada" (422), no "no permitido" (403).
        HttpResponse<String> abono = ines.escribir("POST", "/api/abonos",
                "{\"llave\":\"" + UUID.randomUUID() + "\",\"clienteId\":\"" + cliente
                + "\",\"monto\":1000,\"forma\":\"TRANSFERENCIA\"}");
        assertThat(abono.statusCode()).as(abono.body()).isEqualTo(422);
        // Anular un abono, no: es del administrador (spec 0008, decisión 5).
        HttpResponse<String> anulacion = ines.escribir("POST", "/api/abonos/" + UUID.randomUUID() + "/anulacion",
                "{\"motivo\":\"Me equivoqué\"}");
        assertThat(anulacion.statusCode()).isEqualTo(403);
        assertThat(anulacion.body()).contains("\"codigo\":\"NO_PERMITIDO\"");

        Navegador admin = adentroComoAdministrador();
        assertThat(admin.escribir("POST", "/api/clientes/" + cliente + "/cierre-del-fiado", "{\"motivo\":\"No paga\"}")
                .statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select fiado_cerrado from cliente where id = ?", Boolean.class, cliente)).isTrue();
    }
    @Test
    @Order(18)
    @DisplayName("Spec 0011: el respaldo es del administrador; al cajero le responde 403 tanto verlo como bajarlo")
    void elRespaldoEsDelAdministrador() throws Exception {
        Navegador ines = entrarComo(personas.crear("Inés", Rol.CAJERO));

        HttpResponse<String> verlo = ines.get("/api/respaldos");
        assertThat(verlo.statusCode()).as(verlo.body()).isEqualTo(403);
        assertThat(verlo.body()).contains("\"codigo\":\"NO_PERMITIDO\"");

        // Bajarse la base entera es lo más grave que se puede hacer con una sesión: el cajero no llega ni a empezar.
        HttpResponse<String> bajarla = ines.get("/api/respaldos/archivo");
        assertThat(bajarla.statusCode()).as(bajarla.body()).isEqualTo(403);

        // El administrador sí lo ve. No se le baja una de verdad: eso lo prueba RespaldoIntegracionTest.
        HttpResponse<String> comoAdmin = adentroComoAdministrador().get("/api/respaldos");
        assertThat(comoAdmin.statusCode()).as(comoAdmin.body()).isEqualTo(200);
        assertThat(comoAdmin.body()).contains("\"diasSinBajar\"").contains("\"diasParaAvisar\"");
        // Ya no hay copias en ningún disco nuestro, así que la respuesta no puede hablar de carpetas.
        assertThat(comoAdmin.body()).doesNotContain("carpeta").doesNotContain("segundaCarpeta");
    }

    @Test
    @Order(19)
    @DisplayName("Spec 0010: el correo del cierre es del administrador; al cajero le responde 403, y la llave nunca sale")
    void elCorreoEsDelAdministrador() throws Exception {
        Navegador ines = entrarComo(personas.crear("Inés", Rol.CAJERO));

        for (String[] p : new String[][] {
                {"GET", "/api/correos", null},
                {"PUT", "/api/correos/destinatarios", "{\"correos\":[\"ines@gmail.com\"]}"},
                {"POST", "/api/correos/prueba", null},
        }) {
            HttpResponse<String> r = p[0].equals("GET") ? ines.get(p[1]) : ines.escribir(p[0], p[1], p[2]);
            assertThat(r.statusCode()).as(p[0] + " " + p[1] + " → " + r.body()).isEqualTo(403);
            assertThat(r.body()).contains("\"codigo\":\"NO_PERMITIDO\"");
        }

        HttpResponse<String> comoAdmin = adentroComoAdministrador().get("/api/correos");
        assertThat(comoAdmin.statusCode()).as(comoAdmin.body()).isEqualTo(200);
        assertThat(comoAdmin.body()).contains("\"destinatarios\"").contains("\"listoParaMandar\"");
        // La llave de Brevo no sale por la API ni aunque esté puesta (RF-008).
        assertThat(comoAdmin.body()).doesNotContain("api-key").doesNotContain("xkeysib");
    }

}
