package com.workshopmanagement.rdmotors.clientes.infraestructura;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.workshopmanagement.rdmotors.clientes.aplicacion.ActualizarCliente;
import com.workshopmanagement.rdmotors.clientes.aplicacion.BuscarClientes;
import com.workshopmanagement.rdmotors.clientes.aplicacion.CambiarFiado;
import com.workshopmanagement.rdmotors.clientes.aplicacion.CargarSaldoDelCuaderno;
import com.workshopmanagement.rdmotors.clientes.aplicacion.ClienteEncontrado;
import com.workshopmanagement.rdmotors.clientes.aplicacion.ConsultarCartera;
import com.workshopmanagement.rdmotors.clientes.aplicacion.CrearCliente;
import com.workshopmanagement.rdmotors.clientes.aplicacion.FichaCliente;
import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.clientes.dominio.ClienteRepetidoException;
import com.workshopmanagement.rdmotors.clientes.dominio.DatosCliente;
import com.workshopmanagement.rdmotors.clientes.dominio.EstadoDeuda;
import com.workshopmanagement.rdmotors.clientes.dominio.OrigenDeuda;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.Persona;
import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.ActorActual;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ConsultarVentas;
import com.workshopmanagement.rdmotors.ventas.aplicacion.DetalleVenta;
import com.workshopmanagement.rdmotors.ventas.dominio.EstadoVenta;

import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — los clientes (spec 0008).
 *
 * <p>Crear un cliente con una cédula que ya existe no es un error para el cajero: responde <b>409
 * {@code CLIENTE_REPETIDO} con el cliente que ya tiene esa cédula</b>, y la pantalla lo usa. Así no nace un segundo
 * "Juan" con la deuda partida en dos.
 *
 * <p>Cambiar los datos es {@code PUT}: reemplaza todos, así repetirlo deja el mismo estado. Qué puede cambiar el
 * cajero y qué es del administrador lo decide el caso de uso.
 */
@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
class ClienteController {

    private final BuscarClientes buscarClientes;
    private final CrearCliente crearCliente;
    private final ActualizarCliente actualizarCliente;
    private final ConsultarCartera consultarCartera;
    private final CambiarFiado cambiarFiado;
    private final CargarSaldoDelCuaderno cargarSaldoDelCuaderno;
    private final ConsultarVentas consultarVentas;

    /** Por nombre, cédula o celular, sin tildes ni mayúsculas. Sin texto, nada. */
    @GetMapping
    List<RespuestaCliente> buscar(@RequestParam(defaultValue = "") String q) {
        return buscarClientes.porTexto(q).stream().map(RespuestaCliente::de).toList();
    }

    @PostMapping
    ResponseEntity<?> crear(@RequestBody PeticionCliente peticion, @ActorActual Actor actor) {
        try {
            Cliente creado = crearCliente.ejecutar(peticion.aDatos(), actor);
            return ResponseEntity.status(HttpStatus.CREATED).body(respuestaDe(creado.getId()));
        } catch (ClienteRepetidoException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorClienteRepetido(e.getMessage(),
                    ClienteRepetidoException.CODIGO, respuestaDe(e.getExistenteId()), Instant.now()));
        }
    }

    @PutMapping("/{id}")
    ResponseEntity<?> actualizar(@PathVariable UUID id, @RequestBody PeticionCliente peticion,
                                 @ActorActual Actor actor) {
        try {
            actualizarCliente.ejecutar(id, peticion.aDatos(), actor);
            return ResponseEntity.ok(respuestaDe(id));
        } catch (ClienteRepetidoException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorClienteRepetido(e.getMessage(),
                    ClienteRepetidoException.CODIGO, respuestaDe(e.getExistenteId()), Instant.now()));
        }
    }

    /** La ficha de la Cartera (spec 0008, RF-019): datos, lo que debe, cada venta fiada y cada abono. */
    @GetMapping("/{id}")
    RespuestaFicha ficha(@PathVariable UUID id) {
        return consultarCartera.ficha(id).map(RespuestaFicha::de)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese cliente no existe"));
    }

    /** Las compras del cliente, fiadas o de contado (RF-023). */
    @GetMapping("/{id}/ventas")
    RespuestaPaginaDeVentas ventas(@PathVariable UUID id, @RequestParam(defaultValue = "0") int pagina,
                                   @RequestParam(defaultValue = "25") int tamano) {
        var p = consultarVentas.delCliente(id, pagina, tamano);
        return new RespuestaPaginaDeVentas(p.elementos().stream().map(RespuestaCompra::de).toList(), p.total(),
                p.numero(), p.tamano(), p.totalPaginas());
    }

    /** Lo que ya debía en el cuaderno, una sola vez (RF-028). Del administrador. */
    @PostMapping("/{id}/saldo-del-cuaderno")
    RespuestaFicha saldoDelCuaderno(@PathVariable UUID id, @RequestBody PeticionSaldo peticion,
                                    @ActorActual Actor actor) {
        cargarSaldoDelCuaderno.ejecutar(id, peticion.monto() == null ? null : Dinero.de(peticion.monto()),
                peticion.fecha(), peticion.motivo(), actor);
        return ficha(id);
    }

    /** No se le fía más (RF-005). Del administrador; el motivo lo exige el dominio. */
    @PostMapping("/{id}/cierre-del-fiado")
    RespuestaFicha cerrarFiado(@PathVariable UUID id, @RequestBody PeticionMotivo peticion, @ActorActual Actor actor) {
        cambiarFiado.cerrar(id, peticion.motivo(), actor);
        return ficha(id);
    }

    @PostMapping("/{id}/apertura-del-fiado")
    RespuestaFicha abrirFiado(@PathVariable UUID id, @ActorActual Actor actor) {
        cambiarFiado.abrir(id, actor);
        return ficha(id);
    }

    /** Se relee en su propia transacción: con lo que debe, como lo muestra el cobro. */
    private RespuestaCliente respuestaDe(UUID id) {
        return buscarClientes.porId(id).map(RespuestaCliente::de).orElseThrow();
    }

    // ── Lo que entra ─────────────────────────────────────────────────────────

    /** Lo que es obligatorio y cómo se limpia lo decide el dominio, con el mensaje que ve el cajero. */
    record PeticionCliente(String nombre, String documento, String celular, String direccion, String nota) {

        DatosCliente aDatos() {
            return new DatosCliente(nombre, documento, celular, direccion, nota);
        }
    }

    record PeticionMotivo(String motivo) {
    }

    /** El monto, la fecha y el motivo los exige el dominio, con el mensaje que ve el administrador. */
    record PeticionSaldo(Long monto, LocalDate fecha, String motivo) {
    }

    // ── Lo que sale ──────────────────────────────────────────────────────────

    /**
     * @param desde    el día de la deuda pendiente más vieja; {@code null} si está al día
     * @param deudas   de la más reciente a la más vieja
     * @param abonos   de la más reciente a la más vieja
     */
    record RespuestaFicha(RespuestaCliente cliente, long debe, long aFavor, LocalDate desde, long fiadoTotal,
                          long pagadoTotal, List<RespuestaDeuda> deudas, List<RespuestaAbono> abonos) {

        static RespuestaFicha de(FichaCliente f) {
            return new RespuestaFicha(RespuestaCliente.de(f.cliente()), pesos(f.debe()), pesos(f.aFavor()), f.desde(),
                    pesos(f.fiadoTotal()), pesos(f.pagadoTotal()),
                    f.deudas().stream().map(RespuestaDeuda::de).toList(),
                    f.abonos().stream().map(RespuestaAbono::de).toList());
        }
    }

    /** {@code abonado + pendiente = monto}. */
    record RespuestaDeuda(UUID id, OrigenDeuda origen, UUID ventaId, Long numeroVenta, LocalDate fecha, long monto,
                          long abonado, long pendiente, EstadoDeuda estado, String motivo, Persona registradaPor,
                          Instant anuladaEn, List<RespuestaParteDeAbono> abonos) {

        static RespuestaDeuda de(FichaCliente.DeudaDeLaFicha d) {
            return new RespuestaDeuda(d.id(), d.origen(), d.ventaId(), d.numeroVenta(), d.fecha(), pesos(d.monto()),
                    pesos(d.abonado()), pesos(d.pendiente()), d.estado(), d.motivo(), d.registradaPor(), d.anuladaEn(),
                    d.abonos().stream().map(p -> new RespuestaParteDeAbono(p.abonoId(), p.numero(), p.recibidoEn(),
                            p.forma(), pesos(p.monto()), p.vigente())).toList());
        }
    }

    /** {@code vigente} en falso: esa parte se anuló, o se movió a otra venta al anularse esta. */
    record RespuestaParteDeAbono(UUID abonoId, long numero, Instant recibidoEn, FormaPago forma, long monto,
                                 boolean vigente) {
    }

    /** @param sinAplicar lo que no fue a ninguna deuda: queda a favor del cliente */
    record RespuestaAbono(UUID id, long numero, Instant recibidoEn, long monto, FormaPago forma, String referencia,
                          String nota, UUID turnoId, Persona recibidoPor, long debeDespues, Instant anuladoEn,
                          Persona anuladoPor, String motivoAnulacion, long sinAplicar,
                          List<RespuestaParteAplicada> aplicaciones) {

        static RespuestaAbono de(FichaCliente.AbonoDeLaFicha a) {
            return new RespuestaAbono(a.id(), a.numero(), a.recibidoEn(), pesos(a.monto()), a.forma(), a.referencia(),
                    a.nota(), a.turnoId(), a.recibidoPor(), pesos(a.debeDespues()), a.anuladoEn(), a.anuladoPor(),
                    a.motivoAnulacion(), pesos(a.sinAplicar()),
                    a.aplicaciones().stream().map(p -> new RespuestaParteAplicada(p.deudaId(), p.deuda(),
                            pesos(p.monto()), p.vigente())).toList());
        }
    }

    record RespuestaParteAplicada(UUID deudaId, String deuda, long monto, boolean vigente) {
    }

    private static long pesos(Dinero dinero) {
        return dinero.valor().longValueExact();
    }

    /**
     * @param datosQueFaltan "la cédula", "el celular": lo que conviene completar; no impide fiarle
     * @param sePuedeFiar   con el fiado abierto
     */
    record RespuestaCliente(UUID id, String nombre, String documento, String celular, String direccion, String nota,
                            boolean fiadoCerrado, String motivoFiadoCerrado, long debe, List<String> datosQueFaltan,
                            boolean sePuedeFiar) {

        static RespuestaCliente de(ClienteEncontrado c) {
            return new RespuestaCliente(c.id(), c.nombre(), c.documento(), c.celular(), c.direccion(), c.nota(),
                    c.fiadoCerrado(), c.motivoFiadoCerrado(), c.debe().valor().longValueExact(), c.datosQueFaltan(),
                    !c.fiadoCerrado());
        }
    }

    /** 409: la cédula ya es de otro cliente; {@code cliente} es ese, para usarlo en vez de crear otro. */
    record ErrorClienteRepetido(String mensaje, String codigo, RespuestaCliente cliente, Instant momento) {
    }

    /** Una compra del cliente, como se ve en su ficha. */
    record RespuestaCompra(UUID id, long numero, Instant cobradaEn, long total, long fiado, EstadoVenta estado) {

        static RespuestaCompra de(DetalleVenta v) {
            return new RespuestaCompra(v.id(), v.numero(), v.cobradaEn(), pesos(v.total()), pesos(v.fiado()),
                    v.estado());
        }
    }

    record RespuestaPaginaDeVentas(List<RespuestaCompra> elementos, long total, int numero, int tamano,
                                   int totalPaginas) {
    }
}
