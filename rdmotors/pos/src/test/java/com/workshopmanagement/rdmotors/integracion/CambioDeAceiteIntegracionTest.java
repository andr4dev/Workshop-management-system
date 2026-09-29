package com.workshopmanagement.rdmotors.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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

import com.workshopmanagement.rdmotors.caja.aplicacion.PagarComisionDeCambio;
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
 * El cambio de aceite de punta a punta (spec 0015): por HTTP contra el servidor de verdad, como lo usa la pantalla, y
 * un Postgres real y propio (18, el de producción).
 *
 * <p>Un día de mostrador entero: el administrador marca los aceites; la cajera cobra con cambio, sin cambio, con
 * descuento, fiado y en efectivo, anula una, reintenta un cobro y cobra varias a la vez; los clientes abonan; se cierra
 * el turno; al otro turno se anula una venta del anterior. Después se revisa que <b>cada peso cuadre</b>: el arqueo y el
 * cierre, el reporte del día con su <i>Ver cálculo</i>, y en la base, que cada comisión tenga su gasto y ninguno sobre.
 *
 * <p>Las cifras esperadas están escritas a mano, no calculadas con el código que se prueba:
 *
 * <pre>
 *  Repuesto   precio   comisión   costo c/u
 *  MOTUL      65.000     3.000      40.000
 *  KIXX       35.000     3.000      20.000
 *  FILTRO     11.000       —         5.000
 *
 *  Venta  Qué                                   Cómo se pagó             Comisión (sale del cajón)
 *  A      1 MOTUL, lo cambia Andrés             efectivo 65.000          3.000   (se anula al otro turno: se queda)
 *  B      2 KIXX sin cambio + 1 FILTRO          transferencia 75.000     —       (2 × 32.000 + 11.000)
 *  C      2 MOTUL, los cambia Carolina, −10.000 efectivo 120.000         6.000
 *  D      1 MOTUL, lo cambia Andrés             fiado a Juan 65.000      3.000   (Juan abona todo)
 *  G      1 KIXX, lo cambia Andrés              fiado a María 35.000     3.000   (María abona 10.000)
 *  E      1 MOTUL, lo cambia Andrés             efectivo 65.000          3.000   (se anula en el turno: vuelve)
 *  P1-P4  1 KIXX cada una, a la vez, Andrés     efectivo 35.000 c/u      3.000 c/u
 *  Q      1 KIXX, Carolina, mandada dos veces   efectivo 35.000          3.000   (una sola venta)
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(UsuariosDePrueba.class)
@Testcontainers
class CambioDeAceiteIntegracionTest {

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
    @Autowired JdbcTemplate jdbc;

    // ─────────────────────────────────────────────────────────────────────────
    //  Un navegador de prueba: su sesión, sus cookies y el token anti-CSRF
    // ─────────────────────────────────────────────────────────────────────────

    private final class Navegador {
        private final CookieManager cookies = new CookieManager();
        private final HttpClient cliente = HttpClient.newBuilder().cookieHandler(cookies).build();

        HttpRequest peticion(String metodo, String ruta, Object cuerpo) {
            boolean escribe = !"GET".equals(metodo);
            if (escribe && xsrf() == null) {
                // La primera escritura necesita la cookie del token: la da cualquier lectura.
                enviar(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/instalacion")).GET().build());
            }
            HttpRequest.Builder p = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                    .header("Content-Type", "application/json")
                    .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(cuerpo)));
            if (escribe) {
                p.header("X-XSRF-TOKEN", xsrf());
            }
            return p.build();
        }

        HttpResponse<String> enviar(HttpRequest peticion) {
            try {
                return cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }

        CompletableFuture<HttpResponse<String>> enviarSinEsperar(HttpRequest peticion) {
            return cliente.sendAsync(peticion, HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> pedir(String metodo, String ruta, Object cuerpo) {
            return enviar(peticion(metodo, ruta, cuerpo));
        }

        /** Lo pide y exige que salga bien: devuelve el cuerpo leído. */
        JsonNode hacer(String metodo, String ruta, Object cuerpo) {
            HttpResponse<String> r = pedir(metodo, ruta, cuerpo);
            assertThat(r.statusCode()).as(metodo + " " + ruta + " → " + r.body()).isBetween(200, 299);
            return leer(r);
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

    private static JsonNode leer(HttpResponse<String> r) {
        return JSON.readTree(r.body());
    }

    private static Stream<JsonNode> cada(JsonNode arreglo) {
        return IntStream.range(0, arreglo.size()).mapToObj(arreglo::get);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Lo que se manda al cobrar, como lo arma la pantalla
    // ─────────────────────────────────────────────────────────────────────────

    private static Map<String, Object> renglon(Variante v, int cantidad, long precioVisto, String cambio, UUID quien) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("varianteId", v.getId());
        r.put("cantidad", cantidad);
        r.put("precioVisto", precioVisto);
        if (cambio != null) {
            r.put("cambio", cambio);
        }
        if (quien != null) {
            r.put("cambioPorId", quien);
        }
        return r;
    }

    private static Map<String, Object> cobro(UUID llave, List<Map<String, Object>> renglones, List<?> pagos) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("llave", llave);
        c.put("renglones", renglones);
        c.put("pagos", pagos);
        return c;
    }

    private static Map<String, Object> fiado(UUID llave, List<Map<String, Object>> renglones, UUID clienteId, long monto) {
        Map<String, Object> c = cobro(llave, renglones, List.of());
        c.put("clienteId", clienteId);
        c.put("fiado", monto);
        return c;
    }

    private static Map<String, Object> efectivo(long monto) {
        return Map.of("forma", "EFECTIVO", "monto", monto);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Lo que se mira en la base
    // ─────────────────────────────────────────────────────────────────────────

    private int stock(Variante v) {
        return jdbc.queryForObject("select stock from variante where id = ?", Integer.class, v.getId());
    }

    private long ultimoNumeroDeVenta() {
        return jdbc.queryForObject("select ultimo from consecutivo where nombre = 'VENTA'", Long.class);
    }

    private int gastosDeComision() {
        return jdbc.queryForObject("""
                select count(*) from gasto g join categoria_gasto c on c.id = g.categoria_id where c.nombre = ?""",
                Integer.class, PagarComisionDeCambio.CATEGORIA);
    }

    private Map<String, Object> gastoDe(JsonNode venta, int renglon) {
        return jdbc.queryForMap("""
                select g.id, g.monto, g.descripcion, g.del_cajon, g.turno_id, g.anulado_en, g.motivo_anulacion
                from linea_venta l join gasto g on g.id = l.comision_gasto_id where l.id = ?""",
                UUID.fromString(venta.get("renglones").get(renglon).get("lineaId").asString()));
    }

    private Variante repuesto(String nombre, long precio, int cantidad, long costoTotal) {
        var administrador = personas.administrador();
        String codigo = nombre.replaceAll("[^A-Z0-9]", "").substring(0, 6) + "-" + UUID.randomUUID().toString().substring(0, 4);
        Variante nuevo = crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(nombre,
                categorias.activas().getFirst().getId(), null, codigo, "MARCA", Dinero.de(precio), 1), administrador);
        var proveedor = registrarProveedor.ejecutar("Proveedor " + codigo, null, null, administrador);
        registrarCompra.ejecutar(new ComandoRegistrarCompra(proveedor.getId(), reloj.hoy(), null, FormaPago.EFECTIVO,
                null, administrador,
                List.of(ComandoRegistrarCompra.Linea.porTotal(nuevo.getId(), cantidad, Dinero.de(costoTotal), null))));
        return nuevo;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  El día
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("un día de mostrador con cambios de aceite: el arqueo, el cierre, el reporte y la base cuadran al peso")
    void unDiaDeMostrador() {
        Usuario ruben = personas.crear("Rubén", Rol.ADMINISTRADOR);
        Usuario carolina = personas.crear("Carolina", Rol.CAJERO);
        Usuario andres = personas.crear("Andrés", Rol.CAJERO);
        Variante motul = repuesto("MOTUL 7100 10W30", 65_000, 10, 400_000);
        Variante kixx = repuesto("KIXX 20W50", 35_000, 10, 200_000);
        Variante filtro = repuesto("FILTRO DE ACEITE", 11_000, 10, 50_000);
        Navegador admin = entrar(ruben);
        Navegador caja = entrar(carolina);

        // ── El administrador marca los dos aceites; la cajera no puede ─────────────
        assertThat(caja.pedir("PUT", "/api/repuestos/" + motul.getId() + "/comision", Map.of("monto", 3_000))
                .statusCode()).as("la cajera no marca").isEqualTo(403);
        admin.hacer("PUT", "/api/repuestos/" + motul.getId() + "/comision", Map.of("monto", 3_000));
        admin.hacer("PUT", "/api/repuestos/" + kixx.getId() + "/comision", Map.of("monto", 3_000));
        assertThat(cada(caja.ver("/api/ventas/personas")).map(p -> p.get("nombre").asString()))
                .as("a quién se le puede anotar el cambio").contains("Rubén", "Carolina", "Andrés");

        // ── Turno 1, de Carolina ───────────────────────────────────────────────────
        String turno1 = caja.hacer("POST", "/api/turnos", Map.of("fondo", 100_000)).get("id").asString();

        // A: se cambia aquí, lo cambia Andrés. Y el reintento con la misma llave no cobra ni paga dos veces.
        Map<String, Object> cobroA = cobro(UUID.randomUUID(),
                List.of(renglon(motul, 1, 65_000, "SE_CAMBIA", andres.getId())), List.of(efectivo(65_000)));
        HttpResponse<String> primeraA = caja.pedir("POST", "/api/ventas", cobroA);
        assertThat(primeraA.statusCode()).as(primeraA.body()).isEqualTo(201);
        JsonNode ventaA = leer(primeraA);
        JsonNode renglonA = ventaA.get("renglones").get(0);
        assertThat(renglonA.get("cambio").asString()).isEqualTo("SE_CAMBIA");
        assertThat(renglonA.get("cambioPor").get("nombre").asString()).isEqualTo("Andrés");
        assertThat(renglonA.get("comision").asLong()).isEqualTo(3_000);
        assertThat(ventaA.get("total").asLong()).isEqualTo(65_000);
        HttpResponse<String> reintentoA = caja.pedir("POST", "/api/ventas", cobroA);
        assertThat(reintentoA.statusCode()).as("el reintento devuelve la misma").isEqualTo(200);
        assertThat(leer(reintentoA).get("id").asString()).isEqualTo(ventaA.get("id").asString());
        assertThat(gastosDeComision()).as("una sola comisión por la venta A").isEqualTo(1);
        Map<String, Object> gastoA = gastoDe(ventaA, 0);
        assertThat(gastoA.get("descripcion"))
                .isEqualTo("venta N.º " + ventaA.get("numero").asLong() + " · MOTUL 7100 10W30 · Andrés");
        assertThat(gastoA.get("del_cajon")).isEqualTo(true);
        assertThat(gastoA.get("turno_id").toString()).isEqualTo(turno1);

        // ── Lo que no se cobra no mueve nada: ni stock, ni número, ni comisión ──────
        long numeroAntes = ultimoNumeroDeVenta();
        record Rechazo(String que, Map<String, Object> cobro, int estado, String dice) {
        }
        List<Rechazo> rechazos = List.of(
                new Rechazo("sin escoger", cobro(UUID.randomUUID(), List.of(renglon(motul, 1, 65_000, null, null)),
                        List.of(efectivo(65_000))), 422, "se cambia aquí o no"),
                new Rechazo("sin cambio pero al precio con cambio", cobro(UUID.randomUUID(),
                        List.of(renglon(motul, 1, 65_000, "NO_SE_CAMBIA", null)), List.of(efectivo(65_000))), 409,
                        "62000"),
                new Rechazo("se cambia sin decir quién", cobro(UUID.randomUUID(),
                        List.of(renglon(motul, 1, 65_000, "SE_CAMBIA", null)), List.of(efectivo(65_000))), 422,
                        "quién le cambió el aceite"),
                new Rechazo("el filtro no paga comisión", cobro(UUID.randomUUID(),
                        List.of(renglon(filtro, 1, 11_000, "SE_CAMBIA", andres.getId())), List.of(efectivo(11_000))),
                        422, "no paga comisión"),
                // Este falla tarde: con el stock ya descontado y el número ya tomado. Todo tiene que devolverse.
                new Rechazo("quien lo cambió no es de la tienda", cobro(UUID.randomUUID(),
                        List.of(renglon(motul, 1, 65_000, "SE_CAMBIA", UUID.randomUUID())), List.of(efectivo(65_000))),
                        422, "no es alguien activo"));
        for (Rechazo rechazo : rechazos) {
            HttpResponse<String> r = caja.pedir("POST", "/api/ventas", rechazo.cobro());
            assertThat(r.statusCode()).as(rechazo.que() + " → " + r.body()).isEqualTo(rechazo.estado());
            assertThat(r.body()).as(rechazo.que()).contains(rechazo.dice());
        }
        assertThat(stock(motul)).as("ningún rechazo sacó un MOTUL").isEqualTo(9);
        assertThat(stock(filtro)).isEqualTo(10);
        assertThat(ultimoNumeroDeVenta()).as("ningún rechazo gastó un número").isEqualTo(numeroAntes);
        assertThat(gastosDeComision()).as("ningún rechazo pagó comisión").isEqualTo(1);

        // B: dos KIXX que no se cambian aquí (32.000 cada uno) y un filtro, por transferencia. Sin comisión.
        JsonNode ventaB = caja.hacer("POST", "/api/ventas", cobro(UUID.randomUUID(),
                List.of(renglon(kixx, 2, 32_000, "NO_SE_CAMBIA", null), renglon(filtro, 1, 11_000, null, null)),
                List.of(Map.of("forma", "TRANSFERENCIA", "monto", 75_000))));
        assertThat(ventaB.get("numero").asLong()).as("los números siguen seguidos").isEqualTo(numeroAntes + 1);
        assertThat(ventaB.get("total").asLong()).isEqualTo(75_000);
        assertThat(ventaB.get("renglones").get(0).get("precioUnitario").asLong()).isEqualTo(32_000);
        assertThat(ventaB.get("renglones").get(0).get("comision").asLong()).as("lo que se descontó").isEqualTo(6_000);
        assertThat(gastosDeComision()).isEqualTo(1);

        // C: dos MOTUL que cambia la misma Carolina, con 10.000 de descuento. La comisión no se toca: 6.000.
        Map<String, Object> cobroC = cobro(UUID.randomUUID(),
                List.of(renglon(motul, 2, 65_000, "SE_CAMBIA", carolina.getId())),
                List.of(Map.of("forma", "EFECTIVO", "monto", 120_000, "recibido", 150_000)));
        cobroC.put("descuento", Map.of("modo", "MONTO", "valor", 10_000, "motivo", "Cliente frecuente"));
        JsonNode ventaC = caja.hacer("POST", "/api/ventas", cobroC);
        assertThat(ventaC.get("total").asLong()).isEqualTo(120_000);
        assertThat(ventaC.get("cambio").asLong()).isEqualTo(30_000);
        Map<String, Object> gastoC = gastoDe(ventaC, 0);
        assertThat(((java.math.BigDecimal) gastoC.get("monto")).longValueExact()).isEqualTo(6_000);
        assertThat(gastoC.get("descripcion"))
                .isEqualTo("venta N.º " + ventaC.get("numero").asLong() + " · 2 × MOTUL 7100 10W30 · Carolina");

        // D y G: fiados. La comisión sale del cajón al cobrar aunque la plata no haya entrado.
        String juan = caja.hacer("POST", "/api/clientes", Map.of("nombre", "Juan Pérez")).get("id").asString();
        String maria = caja.hacer("POST", "/api/clientes", Map.of("nombre", "María Gómez")).get("id").asString();
        JsonNode ventaD = caja.hacer("POST", "/api/ventas", fiado(UUID.randomUUID(),
                List.of(renglon(motul, 1, 65_000, "SE_CAMBIA", andres.getId())), UUID.fromString(juan), 65_000));
        JsonNode ventaG = caja.hacer("POST", "/api/ventas", fiado(UUID.randomUUID(),
                List.of(renglon(kixx, 1, 35_000, "SE_CAMBIA", andres.getId())), UUID.fromString(maria), 35_000));
        assertThat(ventaD.get("fiado").asLong()).isEqualTo(65_000);
        assertThat(gastoDe(ventaD, 0).get("anulado_en")).isNull();
        assertThat(gastoDe(ventaG, 0).get("anulado_en")).isNull();

        // E: se cobra y se anula en el mismo turno. La comisión vuelve al cajón, con su motivo.
        JsonNode ventaE = caja.hacer("POST", "/api/ventas", cobro(UUID.randomUUID(),
                List.of(renglon(motul, 1, 65_000, "SE_CAMBIA", andres.getId())), List.of(efectivo(65_000))));
        caja.hacer("POST", "/api/ventas/" + ventaE.get("id").asString() + "/anulacion",
                Map.of("motivo", "El cliente se arrepintió"));
        Map<String, Object> gastoE = gastoDe(ventaE, 0);
        assertThat(gastoE.get("anulado_en")).as("la comisión de E volvió").isNotNull();
        assertThat(gastoE.get("motivo_anulacion")).isEqualTo("Se anuló la venta N.º " + ventaE.get("numero").asLong());

        // ── A la vez: cuatro ventas distintas del mismo KIXX y una misma venta mandada dos veces ─────
        List<HttpRequest> aLaVez = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            aLaVez.add(caja.peticion("POST", "/api/ventas", cobro(UUID.randomUUID(),
                    List.of(renglon(kixx, 1, 35_000, "SE_CAMBIA", andres.getId())), List.of(efectivo(35_000)))));
        }
        Map<String, Object> cobroQ = cobro(UUID.randomUUID(),
                List.of(renglon(kixx, 1, 35_000, "SE_CAMBIA", carolina.getId())), List.of(efectivo(35_000)));
        aLaVez.add(caja.peticion("POST", "/api/ventas", cobroQ));
        aLaVez.add(caja.peticion("POST", "/api/ventas", cobroQ));
        List<HttpResponse<String>> respuestas = aLaVez.stream().map(caja::enviarSinEsperar).toList().stream()
                .map(CompletableFuture::join).toList();
        for (HttpResponse<String> r : respuestas) {
            assertThat(r.statusCode()).as(r.body()).isIn(200, 201);
        }
        assertThat(respuestas.subList(0, 4)).allMatch(r -> r.statusCode() == 201);
        assertThat(respuestas.subList(4, 6).stream().map(HttpResponse::statusCode).toList())
                .as("Q: una se cobra y la otra devuelve la misma").containsExactlyInAnyOrder(201, 200);
        assertThat(leer(respuestas.get(4)).get("id").asString()).isEqualTo(leer(respuestas.get(5)).get("id").asString());

        // ── Los clientes abonan: Juan todo en efectivo, María una parte por transferencia ─────────
        caja.hacer("POST", "/api/abonos", Map.of("llave", UUID.randomUUID(), "clienteId", juan, "monto", 65_000,
                "forma", "EFECTIVO"));
        caja.hacer("POST", "/api/abonos", Map.of("llave", UUID.randomUUID(), "clienteId", maria, "monto", 10_000,
                "forma", "TRANSFERENCIA", "referencia", "Nequi 3001234567"));

        // ── El arqueo del turno 1 ──────────────────────────────────────────────────
        // 100.000 de fondo + 425.000 de ventas en efectivo (A, C, E, P1-P4, Q) − 65.000 que se le devolvió a E
        // + 65.000 del abono de Juan − 30.000 de comisiones (todas menos la de E) = 495.000
        JsonNode detalle1 = caja.ver("/api/turnos/" + turno1);
        JsonNode arqueo = detalle1.get("arqueo");
        assertThat(arqueo.get("gastosCajon").asLong()).as("las comisiones que salieron del cajón").isEqualTo(30_000);
        assertThat(arqueo.get("esperado").asLong()).isEqualTo(495_000);
        assertThat(arqueo.get("esperado").asLong()).as("las partes del arqueo suman lo esperado").isEqualTo(
                100_000 + arqueo.get("ventasEfectivo").asLong() + arqueo.get("abonosEfectivo").asLong()
                        - arqueo.get("devolucionesEfectivo").asLong() - arqueo.get("gastosCajon").asLong()
                        - arqueo.get("retiros").asLong() - arqueo.get("comprasCajon").asLong());
        List<JsonNode> comisionesDelTurno = cada(detalle1.get("gastos"))
                .filter(g -> g.get("categoria").asString().equals(PagarComisionDeCambio.CATEGORIA)).toList();
        assertThat(comisionesDelTurno).as("las 10 del turno, con la anulada").hasSize(10)
                .allMatch(g -> g.get("naturaleza").asString().equals("COSTO") && g.get("delCajon").asBoolean());
        assertThat(comisionesDelTurno.stream().filter(g -> g.get("anuladoEn").isNull())
                .mapToLong(g -> g.get("monto").asLong()).sum()).isEqualTo(30_000);

        // ── Carolina cierra con lo que contó: cuadra ────────────────────────────────
        JsonNode cierre = caja.hacer("POST", "/api/turnos/" + turno1 + "/cierre", Map.of("contado", 495_000))
                .get("cierre");
        assertThat(cierre.get("esperado").asLong()).isEqualTo(495_000);
        assertThat(cierre.get("gastosCajon").asLong()).isEqualTo(30_000);
        assertThat(cierre.get("diferencia").asLong()).as("ni sobra ni falta").isZero();

        // ── Turno 2, del administrador: anula A, que es del turno ya cerrado ─────────
        String turno2 = admin.hacer("POST", "/api/turnos", Map.of("fondo", 100_000)).get("id").asString();
        admin.hacer("POST", "/api/ventas/" + ventaA.get("id").asString() + "/anulacion",
                Map.of("motivo", "Se registró dos veces"));
        assertThat(gastoDe(ventaA, 0).get("anulado_en")).as("la comisión de A salió en un turno que ya cuadró")
                .isNull();
        HttpResponse<String> anularGastoA = admin.pedir("POST", "/api/gastos/" + gastoA.get("id") + "/anulacion",
                Map.of("motivo", "Probar"));
        assertThat(anularGastoA.statusCode()).as("un gasto de un turno cerrado no se anula").isEqualTo(422);
        JsonNode arqueo2 = admin.ver("/api/turnos/" + turno2).get("arqueo");
        assertThat(arqueo2.get("devolucionesEfectivo").asLong()).isEqualTo(65_000);
        assertThat(arqueo2.get("gastosCajon").asLong()).isZero();
        assertThat(arqueo2.get("esperado").asLong()).isEqualTo(35_000);

        // ── El reporte del día ─────────────────────────────────────────────────────
        // Ventas netas (lo cobrado, spec 0014): B 75.000 + C 120.000 + D 65.000 + lo abonado de G 10.000
        //   + P 140.000 + Q 35.000 = 445.000. A y E, anuladas, no cuentan.
        // Costo de lo vendido: B 45.000 + C 80.000 + D 40.000 + G 20.000 × 10/35 = 5.714 + P 80.000 + Q 20.000
        //   = 270.714
        // Comisiones (costo adicional): A 3.000 (se queda) + C 6.000 + D 3.000 + G 3.000 + P 12.000 + Q 3.000 = 30.000
        // Utilidad bruta: 445.000 − 270.714 − 30.000 = 144.286
        LocalDate hoy = reloj.hoy();
        JsonNode reporte = admin.ver("/api/reportes/resultados?desde=" + hoy + "&hasta=" + hoy);
        JsonNode cifras = reporte.get("cifras");
        assertThat(cifras.get("ventasNetas").asLong()).isEqualTo(445_000);
        assertThat(cifras.get("costoVendido").asLong()).isEqualTo(270_714);
        assertThat(cifras.get("costosAdicionales").asLong()).as("las comisiones").isEqualTo(30_000);
        assertThat(cifras.get("utilidadBruta").asLong()).isEqualTo(144_286);
        assertThat(cifras.get("gastos").asLong()).isZero();
        assertThat(cifras.get("utilidadOperativa").asLong()).isEqualTo(144_286);
        assertThat(cifras.get("descuentos").asLong()).isEqualTo(10_000);
        // Cómo entró: efectivo C 120.000 + P 140.000 + Q 35.000 + abono de Juan 65.000 = 360.000; transferencia
        // B 75.000 + abono de María 10.000 = 85.000. Los abonos van adentro, y aparte se dice cuánto fueron.
        assertThat(cifras.get("efectivo").asLong()).isEqualTo(360_000);
        assertThat(cifras.get("transferencia").asLong()).isEqualTo(85_000);
        assertThat(cifras.get("efectivo").asLong() + cifras.get("transferencia").asLong())
                .as("cómo entró la plata suma las ventas netas").isEqualTo(445_000);
        assertThat(cifras.get("deAbonos").asLong()).as("de eso, lo que vino de abonos a fiados").isEqualTo(75_000);

        // Ver cálculo: la categoría de las comisiones, con cada gasto vigente y su enlace.
        List<JsonNode> costos = cada(reporte.get("costosPorCategoria"))
                .filter(c -> c.get("categoria").asString().equals(PagarComisionDeCambio.CATEGORIA)).toList();
        assertThat(costos).hasSize(1);
        JsonNode comisiones = costos.getFirst();
        assertThat(comisiones.get("monto").asLong()).isEqualTo(30_000);
        assertThat(cada(comisiones.get("gastos")).mapToLong(g -> g.get("cargado").asLong()).sum()).isEqualTo(30_000);
        Set<String> enElReporte = cada(comisiones.get("gastos")).map(g -> g.get("id").asString())
                .collect(Collectors.toSet());
        Set<String> vigentesEnLaBase = Set.copyOf(jdbc.queryForList("""
                select g.id::text from gasto g join categoria_gasto c on c.id = g.categoria_id
                where c.nombre = ? and g.anulado_en is null""", String.class, PagarComisionDeCambio.CATEGORIA));
        assertThat(enElReporte).as("Ver cálculo lista justo las vigentes: 9, sin la de E").hasSize(9)
                .isEqualTo(vigentesEnLaBase);

        // Las filas del día y los repuestos suman lo mismo que las cifras.
        assertThat(cada(reporte.get("filas")).mapToLong(f -> f.get("ventasNetas").asLong()).sum()).isEqualTo(445_000);
        assertThat(cada(reporte.get("filas")).mapToLong(f -> f.get("costosAdicionales").asLong()).sum())
                .isEqualTo(30_000);
        assertThat(cada(reporte.get("filas")).mapToLong(f -> f.get("utilidadBruta").asLong()).sum())
                .isEqualTo(144_286);
        assertThat(cada(reporte.get("repuestos")).mapToLong(r -> r.get("ventasNetas").asLong()).sum())
                .isEqualTo(445_000);
        assertThat(cada(reporte.get("repuestos")).mapToLong(r -> r.get("costo").asLong()).sum()).isEqualTo(270_714);

        JsonNode control = reporte.get("control");
        assertThat(control.get("ventasAnuladas").asInt()).isEqualTo(2);
        assertThat(control.get("montoAnuladas").asLong()).isEqualTo(130_000);
        assertThat(reporte.get("cartera").get("porCobrar").asLong()).as("lo que le queda debiendo María")
                .isEqualTo(25_000);

        // ── En la base: cada comisión con su renglón, y ninguna de más ────────────────
        assertThat(jdbc.queryForObject("""
                select count(*) from linea_venta l
                join venta v on v.id = l.venta_id
                join gasto g on g.id = l.comision_gasto_id
                where l.cambio = 'SE_CAMBIA'
                  and (g.monto <> l.comision or not g.del_cajon or g.turno_id <> v.turno_id)""", Integer.class))
                .as("cada comisión es del monto del renglón, del cajón y del turno de su venta").isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from gasto g join categoria_gasto c on c.id = g.categoria_id
                where c.nombre = ? and not exists (select 1 from linea_venta l where l.comision_gasto_id = g.id)""",
                Integer.class, PagarComisionDeCambio.CATEGORIA)).as("ninguna comisión sin su renglón").isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from linea_venta l
                join venta v on v.id = l.venta_id
                join gasto g on g.id = l.comision_gasto_id
                where (g.anulado_en is not null) <> (v.estado = 'ANULADA' and v.anulada_en_turno_id = g.turno_id)""",
                Integer.class)).as("anulada solo la de una venta anulada en el mismo turno").isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from linea_venta where cambio = 'NO_SE_CAMBIA' and comision_gasto_id is not null""",
                Integer.class)).as("sin cambio no hay gasto").isZero();
        assertThat(gastosDeComision()).as("10 renglones que se cambiaron, 10 comisiones").isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from venta", Long.class))
                .as("11 ventas y los números de corrido, sin huecos por los rechazos")
                .isEqualTo(11L).isEqualTo(ultimoNumeroDeVenta());
        assertThat(jdbc.queryForObject("select count(*) from venta where llave_idempotencia = ?", Integer.class,
                cobroQ.get("llave"))).as("Q, mandada dos veces, es una sola venta").isEqualTo(1);

        // El stock: MOTUL 10 − (A 1 + C 2 + D 1 + E 1) + lo que volvió de E y A = 7; KIXX 10 − 8 = 2; FILTRO 9.
        assertThat(stock(motul)).isEqualTo(7);
        assertThat(stock(kixx)).isEqualTo(2);
        assertThat(stock(filtro)).isEqualTo(9);
    }
}
