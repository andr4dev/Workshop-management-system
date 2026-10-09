package com.workshopmanagement.rdmotors.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.Rol;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compras.aplicacion.ComandoRegistrarCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarProveedor;
import com.workshopmanagement.rdmotors.inventario.aplicacion.ComandoCrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.aplicacion.CrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioCategorias;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * El abono mixto de punta a punta (spec 0017): por HTTP contra el servidor de verdad y un Postgres 18 propio. Las cifras
 * esperadas están escritas a mano:
 *
 * <pre>
 *  Venta  MOTUL $65.000 + FILTRO $11.000, fiada entera a Juan                        → debe $76.000
 *  Abono  mixto de $70.000: $20.000 en efectivo y $50.000 por transferencia (REF-55),
 *         marcando el FILTRO y después el MOTUL
 *         efectivo:      FILTRO $11.000, MOTUL $9.000
 *         transferencia: MOTUL $50.000                                               → debe $6.000 (del MOTUL)
 *  El cajón espera 100.000 + 20.000 = 120.000; la transferencia no entra.
 *  Reporte del día: ventas netas $70.000, efectivo $20.000, transferencia $50.000, todo de abonos.
 *  Se anula el pago, pedido por la parte de transferencia → las dos partes; vuelve a deber $76.000 y el cajón $100.000.
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(UsuariosDePrueba.class)
@Testcontainers
class AbonoMixtoIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort int puerto;
    @Autowired UsuariosDePrueba personas;
    @Autowired CrearRepuesto crearRepuesto;
    @Autowired RegistrarProveedor registrarProveedor;
    @Autowired RegistrarCompra registrarCompra;
    @Autowired RepositorioCategorias categorias;
    @Autowired Reloj reloj;

    private final class Navegador {
        private final CookieManager cookies = new CookieManager();
        private final HttpClient cliente = HttpClient.newBuilder().cookieHandler(cookies).build();

        HttpResponse<String> pedir(String metodo, String ruta, Object cuerpo) {
            try {
                boolean escribe = !"GET".equals(metodo);
                if (escribe && xsrf() == null) {
                    cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/instalacion"))
                            .GET().build(), HttpResponse.BodyHandlers.ofString());
                }
                HttpRequest.Builder p = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                        .header("Content-Type", "application/json")
                        .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(cuerpo)));
                if (escribe) {
                    p.header("X-XSRF-TOKEN", xsrf());
                }
                return cliente.send(p.build(), HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }

        JsonNode hacer(String metodo, String ruta, Object cuerpo) {
            HttpResponse<String> r = pedir(metodo, ruta, cuerpo);
            assertThat(r.statusCode()).as(metodo + " " + ruta + " → " + r.body()).isBetween(200, 299);
            return JSON.readTree(r.body());
        }

        JsonNode ver(String ruta) {
            return hacer("GET", ruta, null);
        }

        private String xsrf() {
            return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN"))
                    .map(HttpCookie::getValue).findFirst().orElse(null);
        }
    }

    private Navegador entrar(Usuario quien) {
        Navegador n = new Navegador();
        n.hacer("POST", "/api/sesion", Map.of("usuario", quien.getUsuario(), "contrasena", UsuariosDePrueba.CONTRASENA));
        return n;
    }

    private static Stream<JsonNode> cada(JsonNode arreglo) {
        return IntStream.range(0, arreglo.size()).mapToObj(arreglo::get);
    }

    private Variante repuesto(String nombre, long precio, long costoTotal) {
        var administrador = personas.administrador();
        String codigo = nombre.replaceAll("[^A-Z0-9]", "").substring(0, 5) + "-" + UUID.randomUUID().toString().substring(0, 4);
        Variante nuevo = crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(nombre,
                categorias.activas().getFirst().getId(), null, codigo, "MARCA", Dinero.de(precio), 1), administrador);
        var proveedor = registrarProveedor.ejecutar("Proveedor " + codigo, null, null, administrador);
        registrarCompra.ejecutar(new ComandoRegistrarCompra(proveedor.getId(), reloj.hoy(), null, FormaPago.EFECTIVO,
                null, administrador,
                List.of(ComandoRegistrarCompra.Linea.porTotal(nuevo.getId(), 10, Dinero.de(costoTotal), null))));
        return nuevo;
    }

    private static Map<String, Object> mixto(UUID llave, String clienteId, long total, Long efectivo,
                                             List<String> primero) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("llave", llave);
        c.put("clienteId", clienteId);
        c.put("monto", total);
        c.put("forma", "MIXTO");
        c.put("efectivo", efectivo);
        c.put("referencia", "REF-55");
        c.put("primero", primero);
        return c;
    }

    @Test
    @DisplayName("un abono mixto de punta a punta: el recibo del pago, la ficha, el cajón, el reporte, repetirlo y anularlo")
    void dePuntaAPunta() {
        Usuario ruben = personas.crear("Rubén", Rol.ADMINISTRADOR);
        Usuario carolina = personas.crear("Carolina", Rol.CAJERO);
        Variante motul = repuesto("MOTUL 7100 10W30", 65_000, 400_000);
        Variante filtro = repuesto("FILTRO DE ACEITE", 11_000, 50_000);
        Navegador admin = entrar(ruben);
        Navegador caja = entrar(carolina);
        String turno = caja.hacer("POST", "/api/turnos", Map.of("fondo", 100_000)).get("id").asString();
        String juan = caja.hacer("POST", "/api/clientes", Map.of("nombre", "Juan Pérez")).get("id").asString();

        Map<String, Object> venta = new LinkedHashMap<>();
        venta.put("llave", UUID.randomUUID());
        venta.put("renglones", List.of(Map.of("varianteId", motul.getId(), "cantidad", 1, "precioVisto", 65_000),
                Map.of("varianteId", filtro.getId(), "cantidad", 1, "precioVisto", 11_000)));
        venta.put("pagos", List.of());
        venta.put("clienteId", juan);
        venta.put("fiado", 76_000);
        long numeroVenta = caja.hacer("POST", "/api/ventas", venta).get("numero").asLong();
        List<JsonNode> deudas = cada(caja.ver("/api/clientes/" + juan).get("deudas")).toList();
        String deudaMotul = deudas.stream().filter(d -> d.get("descripcion").asString().startsWith("MOTUL"))
                .findFirst().orElseThrow().get("id").asString();
        String deudaFiltro = deudas.stream().filter(d -> d.get("descripcion").asString().startsWith("FILTRO"))
                .findFirst().orElseThrow().get("id").asString();

        // Lo que no es mixto no se recibe: sin la parte en efectivo, o con todo en efectivo.
        assertThat(caja.pedir("POST", "/api/abonos", mixto(UUID.randomUUID(), juan, 70_000, null, List.of()))
                .statusCode()).isEqualTo(422);
        var todoEnEfectivo = caja.pedir("POST", "/api/abonos", mixto(UUID.randomUUID(), juan, 70_000, 70_000L, List.of()));
        assertThat(todoEnEfectivo.statusCode()).isEqualTo(422);
        assertThat(todoEnEfectivo.body()).contains("En mixto, el efectivo es una parte");

        // El abono mixto, marcando el filtro y después el MOTUL.
        UUID llave = UUID.randomUUID();
        JsonNode recibo = caja.hacer("POST", "/api/abonos",
                mixto(llave, juan, 70_000, 20_000L, List.of(deudaFiltro, deudaMotul)));
        assertThat(recibo.get("forma").asString()).isEqualTo("MIXTO");
        assertThat(recibo.get("monto").asLong()).isEqualTo(70_000);
        assertThat(recibo.get("pagoId").isNull()).isFalse();
        List<JsonNode> partes = cada(recibo.get("partes")).toList();
        assertThat(partes).extracting(p -> p.get("forma").asString() + " " + p.get("monto").asLong())
                .containsExactly("EFECTIVO 20000", "TRANSFERENCIA 50000");
        assertThat(partes.get(1).get("referencia").asString()).isEqualTo("REF-55");
        assertThat(partes.get(1).get("numero").asLong()).isEqualTo(partes.get(0).get("numero").asLong() + 1);
        assertThat(recibo.get("numero").asLong()).isEqualTo(partes.get(0).get("numero").asLong());
        assertThat(cada(recibo.get("aplicaciones")).map(a -> a.get("deuda").asString() + " " + a.get("monto").asLong()))
                .as("lo aplicado por las dos partes, sumado por producto")
                .containsExactly("MOTUL 7100 10W30 (venta N.º " + numeroVenta + ") 59000",
                        "FILTRO DE ACEITE (venta N.º " + numeroVenta + ") 11000");
        assertThat(recibo.get("debeDespues").asLong()).isEqualTo(6_000);
        assertThat(recibo.get("sinAplicar").asLong()).isZero();

        // Pedido por la parte de transferencia, el mismo recibo; y repetir el envío no abona dos veces.
        String transferencia = partes.get(1).get("id").asString();
        assertThat(caja.ver("/api/abonos/" + transferencia).get("id").asString()).isEqualTo(recibo.get("id").asString());
        JsonNode repetido = caja.hacer("POST", "/api/abonos",
                mixto(llave, juan, 70_000, 20_000L, List.of(deudaFiltro, deudaMotul)));
        assertThat(repetido.get("id").asString()).isEqualTo(recibo.get("id").asString());

        // La ficha: dos abonos del mismo pago; el filtro pagado y el MOTUL con sus dos partes.
        JsonNode ficha = caja.ver("/api/clientes/" + juan);
        assertThat(ficha.get("debe").asLong()).isEqualTo(6_000);
        assertThat(cada(ficha.get("abonos")).map(a -> a.get("pagoId").asString()).toList())
                .containsExactly(recibo.get("pagoId").asString(), recibo.get("pagoId").asString());
        JsonNode motulEnLaFicha = cada(ficha.get("deudas")).filter(d -> d.get("id").asString().equals(deudaMotul))
                .findFirst().orElseThrow();
        assertThat(motulEnLaFicha.get("pendiente").asLong()).isEqualTo(6_000);
        assertThat(cada(motulEnLaFicha.get("abonos")).map(p -> p.get("forma").asString() + " " + p.get("monto").asLong()))
                .containsExactlyInAnyOrder("EFECTIVO 9000", "TRANSFERENCIA 50000");

        // El cajón: solo el efectivo.
        JsonNode arqueo = caja.ver("/api/turnos/" + turno).get("arqueo");
        assertThat(arqueo.get("abonosEfectivo").asLong()).isEqualTo(20_000);
        assertThat(arqueo.get("abonosTransferencia").asLong()).isEqualTo(50_000);
        assertThat(arqueo.get("esperado").asLong()).isEqualTo(120_000);

        // El reporte del día: lo cobrado, por forma (spec 0014: el fiado cuenta cuando se cobra).
        LocalDate hoy = reloj.hoy();
        JsonNode cifras = admin.ver("/api/reportes/resultados?desde=" + hoy + "&hasta=" + hoy).get("cifras");
        assertThat(cifras.get("ventasNetas").asLong()).isEqualTo(70_000);
        assertThat(cifras.get("efectivo").asLong()).isEqualTo(20_000);
        assertThat(cifras.get("transferencia").asLong()).isEqualTo(50_000);
        assertThat(cifras.get("deAbonos").asLong()).isEqualTo(70_000);

        // Anular, pedido por la parte de transferencia: se anula el pago entero.
        JsonNode anulado = admin.hacer("POST", "/api/abonos/" + transferencia + "/anulacion",
                Map.of("motivo", "Se registró mal"));
        assertThat(anulado.get("anuladoEn").isNull()).isFalse();
        JsonNode fichaDespues = caja.ver("/api/clientes/" + juan);
        assertThat(fichaDespues.get("debe").asLong()).isEqualTo(76_000);
        assertThat(cada(fichaDespues.get("abonos")).map(a -> a.get("anuladoEn").isNull()).toList())
                .containsExactly(false, false);
        assertThat(caja.ver("/api/turnos/" + turno).get("arqueo").get("esperado").asLong()).isEqualTo(100_000);
        assertThat(admin.ver("/api/reportes/resultados?desde=" + hoy + "&hasta=" + hoy).get("cifras")
                .get("ventasNetas").asLong()).isZero();
    }
}
