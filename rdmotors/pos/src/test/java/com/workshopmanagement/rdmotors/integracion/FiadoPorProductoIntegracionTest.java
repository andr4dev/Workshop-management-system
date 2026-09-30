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
 * Fiar y abonar por producto de punta a punta (spec 0016): por HTTP contra el servidor de verdad y un Postgres 18
 * propio. Las cifras esperadas están escritas a mano:
 *
 * <pre>
 *  MOTUL $65.000 (costo $40.000) · FILTRO $11.000 (costo $5.000)
 *
 *  Venta 1  MOTUL + FILTRO, fiada a Juan; paga el FILTRO al llevárselo ($11.000 efectivo)  → debe el MOTUL: $65.000
 *  Venta 2  MOTUL + FILTRO, fiada a Juan, sin pagar nada                                  → debe $65.000 y $11.000
 *  Abono A  $11.000 efectivo, marcando el FILTRO de la venta 2                            → el filtro, pagado
 *  Abono B  $30.000 efectivo sin marcar → a lo más viejo: el MOTUL de la venta 1; después se anula
 *  Se anula la venta 2 → sus dos productos; los $11.000 del abono A pasan al MOTUL de la venta 1
 *
 *  Queda: Juan debe $54.000 (65.000 − 11.000) de una venta. El cajón espera 100.000 + 11.000 + 11.000 = 122.000.
 *  Reporte del día: ventas netas 22.000 (lo cobrado de la venta 1); costo 11.579 + 1.447 = 13.026 (en proporción).
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(UsuariosDePrueba.class)
@Testcontainers
class FiadoPorProductoIntegracionTest {

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

    private static Map<String, Object> fiado(Variante motul, Variante filtro, String clienteId, long pagaAhora,
                                             long fiado, List<UUID> pagaPrimero) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("llave", UUID.randomUUID());
        c.put("renglones", List.of(Map.of("varianteId", motul.getId(), "cantidad", 1, "precioVisto", 65_000),
                Map.of("varianteId", filtro.getId(), "cantidad", 1, "precioVisto", 11_000)));
        c.put("pagos", pagaAhora == 0 ? List.of() : List.of(Map.of("forma", "EFECTIVO", "monto", pagaAhora)));
        c.put("clienteId", clienteId);
        c.put("fiado", fiado);
        c.put("pagaPrimero", pagaPrimero);
        return c;
    }

    @Test
    @DisplayName("un fiado por producto de punta a punta: la ficha, los abonos, las anulaciones, el cajón y el reporte cuadran")
    void dePuntaAPunta() {
        Usuario ruben = personas.crear("Rubén", Rol.ADMINISTRADOR);
        Usuario carolina = personas.crear("Carolina", Rol.CAJERO);
        Variante motul = repuesto("MOTUL 7100 10W30", 65_000, 400_000);
        Variante filtro = repuesto("FILTRO DE ACEITE", 11_000, 50_000);
        Navegador admin = entrar(ruben);
        Navegador caja = entrar(carolina);
        String turno = caja.hacer("POST", "/api/turnos", Map.of("fondo", 100_000)).get("id").asString();
        String juan = caja.hacer("POST", "/api/clientes", Map.of("nombre", "Juan Pérez")).get("id").asString();

        // Venta 1: paga el filtro al llevárselo; queda debiendo el MOTUL entero.
        JsonNode venta1 = caja.hacer("POST", "/api/ventas", fiado(motul, filtro, juan, 11_000, 65_000,
                List.of(filtro.getId())));
        // Venta 2: todo fiado; dos deudas.
        JsonNode venta2 = caja.hacer("POST", "/api/ventas", fiado(motul, filtro, juan, 0, 76_000, List.of()));
        // Marcar como pagado algo sin fiar no se cobra.
        Map<String, Object> contado = fiado(motul, filtro, null, 76_000, 0, List.of(filtro.getId()));
        assertThat(caja.pedir("POST", "/api/ventas", contado).statusCode()).isEqualTo(422);

        JsonNode ficha = caja.ver("/api/clientes/" + juan);
        List<JsonNode> deudas = cada(ficha.get("deudas")).toList();
        assertThat(deudas).extracting(d -> d.get("descripcion").asString() + " " + d.get("monto").asLong())
                .containsExactlyInAnyOrder("MOTUL 7100 10W30 65000", "MOTUL 7100 10W30 65000", "FILTRO DE ACEITE 11000");
        String filtroDeLa2 = deudas.stream()
                .filter(d -> d.get("ventaId").asString().equals(venta2.get("id").asString())
                        && d.get("descripcion").asString().startsWith("FILTRO"))
                .findFirst().orElseThrow().get("id").asString();
        assertThat(cada(deudas.stream().filter(d -> d.get("ventaId").asString().equals(venta1.get("id").asString()))
                .findFirst().orElseThrow().get("venta").get("renglones")).count())
                .as("la venta 1 trae sus dos renglones, aunque solo el MOTUL es deuda").isEqualTo(2);
        assertThat(ficha.get("debe").asLong()).isEqualTo(141_000);

        // Abono A: marcando el filtro de la venta 2.
        JsonNode abonoA = caja.hacer("POST", "/api/abonos", Map.of("llave", UUID.randomUUID(), "clienteId", juan,
                "monto", 11_000, "forma", "EFECTIVO", "primero", List.of(filtroDeLa2)));
        assertThat(cada(abonoA.get("aplicaciones")).map(a -> a.get("deuda").asString()).toList())
                .containsExactly("FILTRO DE ACEITE (venta N.º " + venta2.get("numero").asLong() + ")");

        // Abono B: sin marcar va al MOTUL de la venta 1 (lo más viejo); después se anula.
        JsonNode abonoB = caja.hacer("POST", "/api/abonos", Map.of("llave", UUID.randomUUID(), "clienteId", juan,
                "monto", 30_000, "forma", "EFECTIVO"));
        assertThat(cada(abonoB.get("aplicaciones")).map(a -> a.get("deuda").asString()).toList())
                .containsExactly("MOTUL 7100 10W30 (venta N.º " + venta1.get("numero").asLong() + ")");
        admin.hacer("POST", "/api/abonos/" + abonoB.get("id").asString() + "/anulacion", Map.of("motivo", "Se equivocó"));

        // Se anula la venta 2: sus dos productos; lo del filtro pasa al MOTUL de la venta 1.
        caja.hacer("POST", "/api/ventas/" + venta2.get("id").asString() + "/anulacion", Map.of("motivo", "Otro cliente"));
        JsonNode despues = caja.ver("/api/clientes/" + juan);
        assertThat(despues.get("debe").asLong()).isEqualTo(54_000);
        assertThat(cada(despues.get("deudas"))
                .filter(d -> d.get("ventaId").asString().equals(venta2.get("id").asString()))
                .map(d -> d.get("estado").asString()).toList()).containsExactly("ANULADA", "ANULADA");
        assertThat(cada(despues.get("deudas"))
                .filter(d -> d.get("ventaId").asString().equals(venta1.get("id").asString()))
                .map(d -> d.get("abonado").asLong()).toList()).containsExactly(11_000L);

        // La lista de la Cartera: una venta pendiente, $54.000.
        JsonNode fila = cada(caja.ver("/api/cartera?vista=DEBEN&q=Juan").get("clientes"))
                .filter(c -> c.get("id").asString().equals(juan)).findFirst().orElseThrow();
        assertThat(fila.get("pendientes").asInt()).isEqualTo(1);
        assertThat(fila.get("debe").asLong()).isEqualTo(54_000);

        // El cajón: fondo + lo pagado al llevárselo + el abono A (el B se anuló).
        assertThat(caja.ver("/api/turnos/" + turno).get("arqueo").get("esperado").asLong()).isEqualTo(122_000);

        // El reporte del día: lo cobrado de la venta 1, con su costo en proporción (spec 0014: no cambia).
        LocalDate hoy = reloj.hoy();
        JsonNode cifras = admin.ver("/api/reportes/resultados?desde=" + hoy + "&hasta=" + hoy).get("cifras");
        assertThat(cifras.get("ventasNetas").asLong()).isEqualTo(22_000);
        assertThat(cifras.get("deAbonos").asLong()).isEqualTo(11_000);
        assertThat(cifras.get("costoVendido").asLong()).isEqualTo(13_026);
    }
}
