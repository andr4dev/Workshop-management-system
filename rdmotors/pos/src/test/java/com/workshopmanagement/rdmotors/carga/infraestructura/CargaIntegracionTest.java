package com.workshopmanagement.rdmotors.carga.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.workshopmanagement.rdmotors.compartido.dominio.Rol;
import com.workshopmanagement.rdmotors.compras.aplicacion.AnularCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarProveedor;
import com.workshopmanagement.rdmotors.integracion.UsuariosDePrueba;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * La pre-carga de punta a punta, por HTTP y contra un Postgres de verdad (spec 0012, fase 3): se sube la factura
 * sintética, se edita, y lo editado sigue ahí al abrirla desde otra sesión. Nunca contra la base de QA.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(UsuariosDePrueba.class)
@Testcontainers
class CargaIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort int puerto;
    @Autowired UsuariosDePrueba personas;
    @Autowired RegistrarProveedor registrarProveedor;
    @Autowired JdbcTemplate jdbc;
    @Autowired AnularCompra anularCompra;

    private Usuario dueno;

    @BeforeEach
    void preparar() {
        dueno = personas.crear("Dueño", Rol.ADMINISTRADOR);
        if (jdbc.queryForObject("select count(*) from proveedor where nit like '900%'", Integer.class) == 0) {
            registrarProveedor.ejecutar("Importadora Jotapartes", "900.576.528-1", null, personas.administrador());
        }
        // Cada prueba sube la factura de nuevo: las que quedaron en borrador se descartan antes.
        jdbc.update("update carga_inventario set estado = 'DESCARTADA', cerrada_en = now(), cerrada_por_id = "
                + "creada_por_id where estado = 'BORRADOR'");
    }

    /** Un navegador de prueba: guarda las cookies y manda el token anti-CSRF en lo que escribe. */
    private final class Navegador {
        private final CookieManager cookies = new CookieManager();
        private final HttpClient cliente = HttpClient.newBuilder().cookieHandler(cookies).build();

        Navegador entrar(Usuario quien) throws Exception {
            HttpResponse<String> r = json("POST", "/api/sesion", "{\"usuario\":\"" + quien.getUsuario()
                    + "\",\"contrasena\":\"" + UsuariosDePrueba.CONTRASENA + "\"}");
            assertThat(r.statusCode()).isEqualTo(200);
            return this;
        }

        HttpResponse<String> get(String ruta) throws IOException, InterruptedException {
            return cliente.send(pedido(ruta).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<byte[]> bajar(String ruta) throws IOException, InterruptedException {
            return cliente.send(pedido(ruta).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        }

        HttpResponse<String> json(String metodo, String ruta, String cuerpo) throws IOException, InterruptedException {
            HttpRequest.Builder p = conCsrf(pedido(ruta)).header("Content-Type", "application/json")
                    .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(cuerpo));
            return cliente.send(p.build(), HttpResponse.BodyHandlers.ofString());
        }

        /** Como lo manda el navegador: {@code multipart/form-data}, con el archivo en el campo "archivo". */
        HttpResponse<String> subir(String nombre, byte[] contenido) throws IOException, InterruptedException {
            String limite = "----rdmotors" + UUID.randomUUID();
            ByteArrayOutputStream cuerpo = new ByteArrayOutputStream();
            cuerpo.write(("--" + limite + "\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\""
                    + nombre + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            cuerpo.write(contenido);
            cuerpo.write(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpRequest.Builder p = conCsrf(pedido("/api/cargas"))
                    .header("Content-Type", "multipart/form-data; boundary=" + limite)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo.toByteArray()));
            return cliente.send(p.build(), HttpResponse.BodyHandlers.ofString());
        }

        private HttpRequest.Builder pedido(String ruta) {
            return HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta));
        }

        private HttpRequest.Builder conCsrf(HttpRequest.Builder p) throws IOException, InterruptedException {
            if (cookie("XSRF-TOKEN") == null) {
                get("/api/instalacion");
            }
            return p.header("X-XSRF-TOKEN", cookie("XSRF-TOKEN"));
        }

        private String cookie(String nombre) {
            return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals(nombre))
                    .map(HttpCookie::getValue).findFirst().orElse(null);
        }
    }

    private static JsonNode leer(HttpResponse<String> r) {
        return JSON.readTree(r.body());
    }

    @Test
    @DisplayName("SE SUBE, SE EDITA, Y DESDE OTRA SESIÓN LO EDITADO SIGUE AHÍ; el inventario no se tocó")
    void subirEditarYReleer() throws Exception {
        FacturaDePrueba inventada = FacturaDePrueba.deVeinticinco();
        Navegador celular = new Navegador().entrar(dueno);

        HttpResponse<String> subida = celular.subir("mag999.pdf", inventada.pdf());

        assertThat(subida.statusCode()).as(subida.body()).isEqualTo(201);
        JsonNode carga = leer(subida);
        String id = carga.get("id").asString();
        assertThat(carga.get("renglones").size()).isEqualTo(25);
        assertThat(carga.get("proveedor").asString()).isEqualTo("Importadora Jotapartes");
        assertThat(carga.get("numeroFactura").asString()).isEqualTo("MAG999");
        assertThat(carga.get("subtotalFactura").asLong()).isEqualTo(inventada.subtotal());
        assertThat(carga.get("totales").get("diferencia").asLong()).isZero();
        assertThat(carga.get("estado").asString()).isEqualTo("BORRADOR");

        assertThat(celular.json("PUT", "/api/cargas/" + id + "/regla",
                "{\"ivaPct\":19,\"gananciaPct\":50,\"redondeo\":100}").statusCode()).isEqualTo(200);
        HttpResponse<String> ajuste = celular.json("PUT", "/api/cargas/" + id + "/renglones/0/precio",
                "{\"precio\":68500}");
        assertThat(ajuste.statusCode()).as(ajuste.body()).isEqualTo(200);

        Navegador computador = new Navegador().entrar(dueno);
        JsonNode otraVez = leer(computador.get("/api/cargas/" + id));
        assertThat(otraVez.get("gananciaPct").asInt()).isEqualTo(50);
        assertThat(otraVez.get("renglones").get(0).get("precioFinal").asLong()).isEqualTo(68_500);
        assertThat(otraVez.get("renglones").get(0).get("ajustadoAMano").asBoolean()).isTrue();

        // Nada de esto es inventario todavía. (La base se comparte con las pruebas que sí confirman, con otros códigos.)
        assertThat(jdbc.queryForObject("select count(*) from variante where codigo like '9%'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from compra where numero_factura = 'MAG999'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from renglon_carga where carga_id = ?::uuid",
                Integer.class, id)).isEqualTo(25);
    }

    @Test
    @DisplayName("LA MISMA FACTURA DOS VECES: 409 con el id de la que ya está; descartada, se vuelve a subir")
    void mismaFactura() throws Exception {
        Navegador n = new Navegador().entrar(dueno);
        byte[] pdf = FacturaDePrueba.deVeinticinco().pdf();
        String primera = leer(n.subir("mag999.pdf", pdf)).get("id").asString();

        HttpResponse<String> otra = n.subir("mag999-copia.pdf", pdf);

        assertThat(otra.statusCode()).isEqualTo(409);
        assertThat(leer(otra).get("cargaId").asString()).isEqualTo(primera);

        assertThat(n.json("POST", "/api/cargas/" + primera + "/descarte", null).statusCode()).isEqualTo(204);
        assertThat(n.json("PUT", "/api/cargas/" + primera + "/renglones/0/precio", "{\"precio\":1000}")
                .statusCode()).isEqualTo(422);
        assertThat(n.subir("mag999.pdf", pdf).statusCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("UN PDF DE OTRO PROVEEDOR: 422 diciendo que no se reconoce, y no queda nada guardado")
    void otroProveedor() throws Exception {
        Navegador n = new Navegador().entrar(dueno);
        int antes = jdbc.queryForObject("select count(*) from carga_inventario", Integer.class);

        HttpResponse<String> r = n.subir("otra.pdf", FacturaDePrueba.deVeinticinco().deOtroProveedor().pdf());

        assertThat(r.statusCode()).isEqualTo(422);
        assertThat(leer(r).get("mensaje").asString()).contains("solo sé leer las de Importadora Jotapartes");
        assertThat(jdbc.queryForObject("select count(*) from carga_inventario", Integer.class)).isEqualTo(antes);
    }

    @Test
    @DisplayName("EL CAJERO NO VE NI SUBE CARGAS: 403 (RF-019)")
    void cajero() throws Exception {
        Navegador cajero = new Navegador().entrar(personas.crear("Caja", Rol.CAJERO));

        assertThat(cajero.get("/api/cargas").statusCode()).isEqualTo(403);
        assertThat(cajero.subir("mag999.pdf", FacturaDePrueba.deVeinticinco().pdf()).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("la plantilla se baja con sus títulos, en CSV que Excel en español abre sin preguntar")
    void plantilla() throws Exception {
        HttpResponse<byte[]> r = new Navegador().entrar(dueno).bajar("/api/cargas/plantilla");

        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.headers().firstValue("Content-Disposition").orElse(""))
                .contains("plantilla-carga-rdmotors.csv");
        byte[] cuerpo = r.body();
        assertThat(cuerpo[0] & 0xFF).isEqualTo(0xEF);
        assertThat(new String(cuerpo, 3, cuerpo.length - 3, StandardCharsets.UTF_8))
                .isEqualTo("CODIGO;DESCRIPCION;CANTIDAD;VALOR TOTAL;MARCA;CATEGORIA\r\n");
    }

    /** Sube la factura y le pone lo que le falta para confirmar: la categoría a todos, y los datos de la compra. */
    private String listaParaConfirmar(Navegador n, FacturaDePrueba inventada, String numero) throws Exception {
        HttpResponse<String> subida = n.subir(numero + ".pdf", inventada.pdf());
        assertThat(subida.statusCode()).as(subida.body()).isEqualTo(201);
        String id = leer(subida).get("id").asString();
        UUID motor = jdbc.queryForObject("select id from categoria where nombre = 'MOTOR'", UUID.class);
        UUID proveedor = jdbc.queryForObject("select id from proveedor where nit = '900.576.528-1'", UUID.class);
        assertThat(n.json("PUT", "/api/cargas/" + id + "/categoria",
                "{\"losQueNoTienen\":true,\"categoriaId\":\"" + motor + "\"}").statusCode()).isEqualTo(200);
        // Los dos de descripción partida no terminan en la marca: la pre-carga la pide, y se les pone a los dos.
        assertThat(n.json("PUT", "/api/cargas/" + id + "/marca",
                "{\"losQueNoTienen\":true,\"marca\":\"INOKI\"}").statusCode()).isEqualTo(200);
        HttpResponse<String> datos = n.json("PUT", "/api/cargas/" + id + "/datos", "{\"proveedorId\":\"" + proveedor
                + "\",\"numeroFactura\":\"" + numero + "\",\"fechaFactura\":\"2026-09-20\",\"formaPago\":\"EFECTIVO\"}");
        assertThat(datos.statusCode()).as(datos.body()).isEqualTo(200);
        assertThat(leer(datos).get("sePuedeConfirmar").asBoolean()).as(datos.body()).isTrue();
        return id;
    }

    @Test
    @DisplayName("CONFIRMAR: una compra por sub-total + IVA, los 25 con su stock, su costo y su kardex; anularla deja todo como estaba")
    void confirmar() throws Exception {
        FacturaDePrueba inventada = FacturaDePrueba.deVeinticinco("C", "MAG1001");
        Navegador n = new Navegador().entrar(dueno);
        String id = listaParaConfirmar(n, inventada, "MAG1001");

        HttpResponse<String> r = n.json("POST", "/api/cargas/" + id + "/confirmacion", "{\"reposicionesVistas\":0}");

        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        JsonNode hecha = leer(r);
        UUID compra = UUID.fromString(hecha.get("compraId").asString());
        long pagado = inventada.subtotal() + inventada.iva();
        assertThat(hecha.get("total").asLong()).isEqualTo(pagado);
        assertThat(jdbc.queryForObject("select total from compra where id = ?", Long.class, compra)).isEqualTo(pagado);
        assertThat(jdbc.queryForObject("select sum(costo_total) from linea_compra where compra_id = ?", Long.class,
                compra)).isEqualTo(pagado);
        assertThat(jdbc.queryForObject("select count(*) from linea_compra where compra_id = ?", Integer.class,
                compra)).isEqualTo(25);
        assertThat(jdbc.queryForObject("select sum(stock) from variante where codigo like 'C9%'", Integer.class))
                .isEqualTo(inventada.unidades());
        assertThat(jdbc.queryForObject("""
                select count(*) from movimiento_kardex m join variante v on v.id = m.variante_id
                where v.codigo like 'C9%'
                """, Integer.class)).isEqualTo(25);
        // El costo promedio es lo que se pagó por el renglón, con su IVA, entre las unidades.
        assertThat(jdbc.queryForObject("""
                select v.costo_promedio = round(l.costo_total / l.cantidad, 4)
                from variante v join linea_compra l on l.variante_id = v.id
                where v.codigo = 'C901Z1K'
                """, Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select estado from carga_inventario where id = ?::uuid", String.class, id))
                .isEqualTo("CONFIRMADA");

        // Confirmar otra vez devuelve la misma compra.
        assertThat(leer(n.json("POST", "/api/cargas/" + id + "/confirmacion", "{\"reposicionesVistas\":0}"))
                .get("compraId").asString()).isEqualTo(compra.toString());

        // Se anula como cualquier compra (RF-018), y el inventario vuelve a como estaba.
        long version = jdbc.queryForObject("select version from compra where id = ?", Long.class, compra);
        anularCompra.ejecutar(compra, version, "Prueba: se cargó para ver que se puede anular",
                personas.administrador());
        assertThat(jdbc.queryForObject("select sum(stock) from variante where codigo like 'C9%'", Integer.class))
                .isZero();
        // Y con su compra anulada, la misma factura se puede volver a subir.
        assertThat(n.subir("MAG1001.pdf", inventada.pdf()).statusCode()).isEqualTo(201);
    }

    @Test
    @DisplayName("DOS CONFIRMACIONES A LA VEZ, desde dos equipos: una sola compra, y el stock no se duplica")
    void dosALaVez() throws Exception {
        FacturaDePrueba inventada = FacturaDePrueba.deVeinticinco("D", "MAG1002");
        Navegador celular = new Navegador().entrar(dueno);
        Navegador computador = new Navegador().entrar(dueno);
        String id = listaParaConfirmar(celular, inventada, "MAG1002");
        String ruta = "/api/cargas/" + id + "/confirmacion";
        computador.get("/api/instalacion");

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        Future<HttpResponse<String>> uno = hilos.submit(() -> {
            salida.await();
            return celular.json("POST", ruta, "{\"reposicionesVistas\":0}");
        });
        Future<HttpResponse<String>> otro = hilos.submit(() -> {
            salida.await();
            return computador.json("POST", ruta, "{\"reposicionesVistas\":0}");
        });
        salida.countDown();
        HttpResponse<String> a = uno.get();
        HttpResponse<String> b = otro.get();
        hilos.shutdown();

        assertThat(a.statusCode()).as(a.body()).isEqualTo(200);
        assertThat(b.statusCode()).as(b.body()).isEqualTo(200);
        assertThat(leer(a).get("compraId").asString()).isEqualTo(leer(b).get("compraId").asString());
        assertThat(jdbc.queryForObject("select count(*) from compra where llave_idempotencia = ?::uuid",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select sum(stock) from variante where codigo like 'D9%'", Integer.class))
                .isEqualTo(inventada.unidades());
    }

    @Test
    @DisplayName("la lista trae los borradores con cuántos renglones tienen")
    void lista() throws Exception {
        Navegador n = new Navegador().entrar(dueno);
        String id = leer(n.subir("mag999.pdf", FacturaDePrueba.deVeinticinco().pdf())).get("id").asString();

        JsonNode lista = leer(n.get("/api/cargas"));

        assertThat(lista.get(0).get("id").asString()).isEqualTo(id);
        assertThat(lista.get(0).get("renglones").asInt()).isEqualTo(25);
        assertThat(lista.get(0).get("estado").asString()).isEqualTo("BORRADOR");
    }
}
