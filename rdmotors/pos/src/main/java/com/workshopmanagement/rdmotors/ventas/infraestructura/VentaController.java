package com.workshopmanagement.rdmotors.ventas.infraestructura;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.ActorActual;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.Persona;
import com.workshopmanagement.rdmotors.ventas.aplicacion.AnularVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.CobrarVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ComandoCobrarVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ConsultarVentas;
import com.workshopmanagement.rdmotors.ventas.aplicacion.DetalleVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ResultadoCobro;
import com.workshopmanagement.rdmotors.ventas.aplicacion.RevisarPerdida;
import com.workshopmanagement.rdmotors.ventas.dominio.AvisoDePerdida;
import com.workshopmanagement.rdmotors.ventas.dominio.EstadoVenta;
import com.workshopmanagement.rdmotors.ventas.dominio.ModoDescuento;
import com.workshopmanagement.rdmotors.ventas.dominio.VentaRepetidaException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — la venta de mostrador (spec 0003).
 *
 * <p>Cobrar es {@code POST} y <b>SUMA</b> (stock, dinero): por eso lleva llave. Repetido con la misma
 * llave no es un error: responde 200 con la venta que ya existe; una nueva responde 201.
 *
 * <p>Con {@code clienteId} y {@code fiado}, una parte o todo queda debiendo (spec 0008). La respuesta dice a nombre de
 * quién y cuánto debía después, para el comprobante.
 */
@RestController
@RequestMapping("/api/ventas")
@RequiredArgsConstructor
class VentaController {

    private final CobrarVenta cobrarVenta;
    private final AnularVenta anularVenta;
    private final ConsultarVentas consultarVentas;
    private final RevisarPerdida revisarPerdida;

    @PostMapping
    ResponseEntity<RespuestaVenta> cobrar(@Valid @RequestBody PeticionCobro peticion,
                                          @ActorActual Actor actor) {
        try {
            ResultadoCobro resultado = cobrarVenta.ejecutar(peticion.aComando(actor));
            // Se relee en su propia transacción: la respuesta muestra lo que quedó guardado.
            DetalleVenta detalle = consultarVentas.detalle(resultado.venta().getId(), actor).orElseThrow();
            return ResponseEntity.status(resultado.repetida() ? HttpStatus.OK : HttpStatus.CREATED)
                    .body(RespuestaVenta.de(detalle));
        } catch (VentaRepetidaException e) {
            // Dos cobros con la misma llave a la vez: el otro ganó y la venta ya está guardada.
            return ResponseEntity.ok(RespuestaVenta.de(consultarVentas.porLlave(e.getLlave(), actor).orElseThrow()));
        }
    }

    /** Las ventas del turno abierto, de la más reciente a la más antigua. Hoy solo existe {@code turno=abierto}. */
    @GetMapping
    List<RespuestaVenta> delTurno(@RequestParam String turno, @ActorActual Actor actor) {
        if (!"abierto".equals(turno)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se consultan las ventas del turno abierto");
        }
        return consultarVentas.delTurnoAbierto(actor).stream().map(RespuestaVenta::de).toList();
    }

    @GetMapping("/numero/{numero}")
    RespuestaVenta porNumero(@PathVariable long numero, @ActorActual Actor actor) {
        return consultarVentas.porNumero(numero, actor).map(RespuestaVenta::de)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay una venta con ese número"));
    }

    @GetMapping("/{id}")
    RespuestaVenta detalle(@PathVariable UUID id, @ActorActual Actor actor) {
        return consultarVentas.detalle(id, actor).map(RespuestaVenta::de)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Esa venta no existe"));
    }

    /**
     * Anular (spec 0003, H8). Sin llave: una venta anulada no se anula dos veces, así que repetirlo
     * responde 422 "ya fue anulada" en vez de devolver el stock otra vez.
     */
    @PostMapping("/{id}/anulacion")
    RespuestaVenta anular(@PathVariable UUID id, @RequestBody PeticionAnulacion peticion,
                          @ActorActual Actor actor) {
        anularVenta.ejecutar(id, peticion.motivo(), actor);
        // Se relee en su propia transacción: la respuesta muestra lo que quedó guardado.
        return consultarVentas.detalle(id, actor).map(RespuestaVenta::de).orElseThrow();
    }

    /**
     * ¿La venta que se está armando queda a pérdida? (spec 0004, RF-011). La pantalla lo pregunta al cambiar los
     * renglones o el descuento. El cajero solo sabe si queda por debajo del costo; el administrador, las cifras.
     * Sin validar lo que llega: una venta a medio armar no es un error, a lo sumo no hay aviso.
     */
    @PostMapping("/aviso-de-perdida")
    Object avisoDePerdida(@RequestBody PeticionAviso peticion, @ActorActual Actor actor) {
        List<ComandoCobrarVenta.Renglon> renglones = peticion.renglones() == null ? List.of()
                : peticion.renglones().stream()
                        .map(r -> new ComandoCobrarVenta.Renglon(r.varianteId(), r.cantidad(), r.precioVisto()))
                        .toList();
        PeticionDescuento d = peticion.descuento();
        var perdida = revisarPerdida.ejecutar(renglones,
                d == null ? null : new ComandoCobrarVenta.ComandoDescuento(d.modo(), d.valor(), d.motivo()));
        if (perdida.isEmpty()) {
            return new RespuestaAviso(false, 0);
        }
        AvisoDePerdida p = perdida.get();
        return actor.rol().veCostos()
                ? new RespuestaAvisoConCifras(true, p.sinCosto(), pesos(p.costo()), pesos(p.cobrado()),
                        pesos(p.diferencia()))
                : new RespuestaAviso(true, p.sinCosto());
    }

    // ── Lo que entra ─────────────────────────────────────────────────────────

    /** El motivo lo exige el dominio, con su mensaje. */
    record PeticionAnulacion(String motivo) {
    }

    /**
     * @param clienteId a nombre de quién; obligatorio si hay fiado
     * @param fiado     lo que queda debiendo, en pesos; sin decirlo, $0
     */
    record PeticionCobro(@NotNull UUID llave,
                         @NotEmpty List<@Valid @NotNull PeticionRenglon> renglones,
                         @Valid PeticionDescuento descuento,
                         List<@Valid @NotNull PeticionPago> pagos,
                         UUID clienteId,
                         @PositiveOrZero Long fiado) {

        ComandoCobrarVenta aComando(Actor actor) {
            return new ComandoCobrarVenta(llave,
                    renglones.stream().map(r -> new ComandoCobrarVenta.Renglon(r.varianteId(), r.cantidad(), r.precioVisto())).toList(),
                    descuento == null ? null
                            : new ComandoCobrarVenta.ComandoDescuento(descuento.modo(), descuento.valor(), descuento.motivo()),
                    pagos == null ? List.of()
                            : pagos.stream().map(p -> new ComandoCobrarVenta.Pago(p.forma(), p.monto(), p.recibido())).toList(),
                    clienteId, fiado == null ? 0 : fiado, actor);
        }
    }

    record PeticionRenglon(@NotNull UUID varianteId, @Positive int cantidad, @PositiveOrZero long precioVisto) {
    }

    record PeticionAviso(List<PeticionRenglon> renglones, PeticionDescuento descuento) {
    }

    /** @param valor pesos si es {@code MONTO}; porcentaje si es {@code PORCENTAJE} */
    record PeticionDescuento(@NotNull ModoDescuento modo, @NotNull @Positive BigDecimal valor, String motivo) {
    }

    record PeticionPago(@NotNull FormaPago forma, @Positive long monto, @Positive Long recibido) {
    }

    // ── Lo que sale ──────────────────────────────────────────────────────────

    /**
     * @param vendidoPor  id y nombre de quien la cobró: el comprobante dice "Atendió" (spec 0004)
     * @param cliente     a nombre de quién, con su cédula; {@code null} si no se dijo (spec 0008)
     * @param fiado       lo que quedó debiendo; 0 si pagó todo
     * @param debeDespues cuánto debía el cliente en total justo después; {@code null} si no se fió
     */
    record RespuestaVenta(UUID id, long numero, EstadoVenta estado, UUID turnoId, Persona vendidoPor,
                          Instant cobradaEn, long subtotal, long descuento, ModoDescuento descuentoModo,
                          BigDecimal descuentoPorcentaje, String descuentoMotivo, long total, long cambio,
                          List<RespuestaRenglon> renglones, List<RespuestaPago> pagos,
                          Instant anuladaEn, String motivoAnulacion, RespuestaCliente cliente, long fiado,
                          Long debeDespues) {

        static RespuestaVenta de(DetalleVenta d) {
            return new RespuestaVenta(d.id(), d.numero(), d.estado(), d.turnoId(), d.vendidoPor(), d.cobradaEn(),
                    pesos(d.subtotal()), pesos(d.descuento()), d.descuentoModo(), d.descuentoPorcentaje(),
                    d.descuentoMotivo(), pesos(d.total()), pesos(d.cambio()),
                    d.renglones().stream().map(RespuestaRenglon::de).toList(),
                    d.pagos().stream().map(RespuestaPago::de).toList(),
                    d.anuladaEn(), d.motivoAnulacion(),
                    d.cliente() == null ? null
                            : new RespuestaCliente(d.cliente().id(), d.cliente().nombre(), d.cliente().documento()),
                    pesos(d.fiado()), d.debeDespues() == null ? null : pesos(d.debeDespues()));
        }
    }

    record RespuestaCliente(UUID id, String nombre, String documento) {
    }

    record RespuestaRenglon(UUID lineaId, int posicion, UUID varianteId, String codigo, String nombre, String marca,
                            int cantidad, long precioUnitario, long total) {

        static RespuestaRenglon de(DetalleVenta.Renglon r) {
            return new RespuestaRenglon(r.lineaId(), r.posicion(), r.varianteId(), r.codigo(), r.nombre(), r.marca(),
                    r.cantidad(), pesos(r.precioUnitario()), pesos(r.total()));
        }
    }

    /** @param recibido {@code null} si no se escribió con cuánto pagó */
    record RespuestaPago(FormaPago forma, long monto, Long recibido, long cambio) {

        static RespuestaPago de(DetalleVenta.Pago p) {
            return new RespuestaPago(p.forma(), pesos(p.monto()),
                    p.recibido() == null ? null : pesos(p.recibido()), pesos(p.cambio()));
        }
    }

    /**
     * El aviso al cajero: si la venta queda por debajo de lo que costaron los repuestos, sin decir cuánto.
     *
     * @param sinCosto cuántos repuestos no entraron a la cuenta por no tener costo
     */
    record RespuestaAviso(boolean bajoCosto, int sinCosto) {
    }

    /** El aviso al administrador, con las cifras (spec 0004, RF-011). */
    record RespuestaAvisoConCifras(boolean bajoCosto, int sinCosto, long costo, long cobrado, long diferencia) {
    }

    private static long pesos(Dinero dinero) {
        return dinero.valor().longValueExact();
    }
}
