package com.workshopmanagement.rdmotors.caja.infraestructura;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Persona;
import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.ActorActual;
import com.workshopmanagement.rdmotors.caja.aplicacion.AbrirTurno;
import com.workshopmanagement.rdmotors.caja.aplicacion.CerrarTurno;
import com.workshopmanagement.rdmotors.caja.aplicacion.ConsultarTurnos;
import com.workshopmanagement.rdmotors.caja.aplicacion.DetalleTurno;
import com.workshopmanagement.rdmotors.caja.aplicacion.EscribirObservaciones;
import com.workshopmanagement.rdmotors.caja.dominio.EstadoTurno;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;
import com.workshopmanagement.rdmotors.compras.dominio.EstadoCompra;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;
import com.workshopmanagement.rdmotors.ventas.dominio.EstadoVenta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — el turno de caja (spec 0003, fase 1; spec 0006).
 *
 * <p>Abrir, cerrar y escribir las observaciones pasan por sus casos de uso; consultar el abierto usa el
 * puerto directo, como los listados de proveedores y cuentas.
 *
 * <p>El detalle de un turno abierto trae lo que debería haber en vivo ({@code arqueo}); el de uno cerrado, las
 * cifras que se guardaron al cerrar ({@code cierre}).
 */
@RestController
@RequestMapping("/api/turnos")
@RequiredArgsConstructor
class TurnoController {

    private final AbrirTurno abrirTurno;
    private final CerrarTurno cerrarTurno;
    private final EscribirObservaciones escribirObservaciones;
    private final ConsultarTurnos consultarTurnos;
    private final RepositorioTurnos turnos;
    private final RepositorioUsuarios usuarios;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RespuestaTurno abrir(@Valid @RequestBody PeticionAbrirTurno peticion,
                         @ActorActual Actor actor) {
        return respuesta(abrirTurno.ejecutar(Dinero.de(peticion.fondo()), actor));
    }

    /**
     * 204 y no 404 cuando no hay turno: no tener un turno abierto es un estado normal de la tienda
     * (antes de abrir, o entre turnos), no un error. La pantalla recibe "nada" y ofrece abrirlo.
     */
    @GetMapping("/abierto")
    ResponseEntity<RespuestaTurno> abierto() {
        return turnos.abierto()
                .map(this::respuesta)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Los turnos cerrados, del cierre más reciente al más antiguo (spec 0006, RF-020). */
    @GetMapping
    RespuestaPagina cerrados(@RequestParam(defaultValue = "CERRADO") EstadoTurno estado,
                             @RequestParam(defaultValue = "0") int pagina,
                             @RequestParam(defaultValue = "25") int tamano,
                             @ActorActual Actor actor) {
        if (estado != EstadoTurno.CERRADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se listan los turnos cerrados; el abierto está en /api/turnos/abierto");
        }
        Pagina<TurnoCaja> p = consultarTurnos.cerrados(pagina, tamano, actor);
        Map<UUID, String> nombres = usuarios.nombresDe(p.elementos().stream().map(TurnoCaja::getAbiertoPorId).toList());
        return new RespuestaPagina(p.elementos().stream().map(t -> RespuestaTurno.de(t, nombres)).toList(), p.total(),
                p.numero(),
                p.tamano(), p.totalPaginas());
    }

    @GetMapping("/{id}")
    RespuestaDetalle detalle(@PathVariable UUID id, @ActorActual Actor actor) {
        return consultarTurnos.detalle(id, actor).map(RespuestaDetalle::de)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese turno no existe"));
    }

    /**
     * Cerrar con lo contado (spec 0006, H3). Sin llave: un turno cerrado no se cierra dos veces, así que un
     * reintento responde 409 "ya se cerró" con cuándo, y la pantalla carga ese cierre.
     *
     * <p>Devuelve el turno ya cerrado, leído de nuevo en su propia transacción: con el desglose y todo lo que
     * el comprobante necesita.
     */
    @PostMapping("/{id}/cierre")
    RespuestaDetalle cerrar(@PathVariable UUID id, @RequestBody PeticionCierre peticion,
                            @ActorActual Actor actor) {
        cerrarTurno.ejecutar(id, peticion.contado() == null ? null : Dinero.de(peticion.contado()), actor);
        return detalleDe(id, actor);
    }

    /** Las observaciones de un cierre, una sola vez (decisión 2). */
    @PutMapping("/{id}/observaciones")
    RespuestaDetalle observaciones(@PathVariable UUID id, @RequestBody PeticionObservaciones peticion,
                                   @ActorActual Actor actor) {
        escribirObservaciones.ejecutar(id, peticion.observaciones(), actor);
        return detalleDe(id, actor);
    }

    private RespuestaDetalle detalleDe(UUID id, Actor actor) {
        return consultarTurnos.detalle(id, actor).map(RespuestaDetalle::de).orElseThrow();
    }

    /** Con el nombre de quien lo abrió: la pantalla dice de quién es el turno (spec 0004, RF-012). */
    private RespuestaTurno respuesta(TurnoCaja turno) {
        return RespuestaTurno.de(turno, usuarios.nombresDe(List.of(turno.getAbiertoPorId())));
    }

    // ── Lo que entra ─────────────────────────────────────────────────────────

    record PeticionAbrirTurno(@NotNull @PositiveOrZero Long fondo) {
    }

    /** Lo contado lo exige el dominio, con el mensaje que ve el cajero. */
    record PeticionCierre(Long contado) {
    }

    record PeticionObservaciones(String observaciones) {
    }

    // ── Lo que sale ──────────────────────────────────────────────────────────

    /** {@code esperado}, {@code contado} y {@code diferencia} solo existen en un turno cerrado. */
    /** @param abiertoPor id y nombre de quien lo abrió: de quién es el turno (spec 0004) */
    record RespuestaTurno(UUID id, EstadoTurno estado, long fondo, Instant abiertoEn, Persona abiertoPor,
                          Instant cerradoEn, UUID cerradoPorId, Long esperado, Long contado, Long diferencia,
                          String observaciones) {
        static RespuestaTurno de(TurnoCaja t, Map<UUID, String> nombres) {
            return new RespuestaTurno(t.getId(), t.getEstado(), pesos(t.getFondo()), t.getAbiertoEn(),
                    Persona.de(t.getAbiertoPorId(), nombres), t.getCerradoEn(), t.getCerradoPorId(), pesosONulo(t.getEsperado()),
                    pesosONulo(t.getContado()), pesosONulo(t.getDiferencia()), t.getObservaciones());
        }
    }

    record RespuestaPagina(List<RespuestaTurno> elementos, long total, int numero, int tamano, int totalPaginas) {
    }

    record RespuestaDetalle(UUID id, EstadoTurno estado, long fondo, Instant abiertoEn, Persona abiertoPor,
                            Instant cerradoEn, Persona cerradoPor, RespuestaCierre cierre, RespuestaArqueo arqueo,
                            String observaciones,
                            List<GastoController.RespuestaGasto> gastos, List<RespuestaRetiro> retiros,
                            List<RespuestaCompraDeCaja> compras, List<RespuestaVenta> ventas,
                            List<RespuestaVenta> anuladasDeOtrosTurnos, List<RespuestaAbono> abonos) {

        static RespuestaDetalle de(DetalleTurno d) {
            return new RespuestaDetalle(d.id(), d.estado(), pesos(d.fondo()), d.abiertoEn(), d.abiertoPor(),
                    d.cerradoEn(), d.cerradoPor(), RespuestaCierre.de(d.cierre()), RespuestaArqueo.de(d.arqueo()),
                    d.observaciones(),
                    d.gastos().stream().map(GastoController.RespuestaGasto::de).toList(),
                    d.retiros().stream().map(RespuestaRetiro::de).toList(),
                    d.compras().stream().map(RespuestaCompraDeCaja::de).toList(),
                    d.ventas().stream().map(RespuestaVenta::de).toList(),
                    d.anuladasDeOtrosTurnos().stream().map(RespuestaVenta::de).toList(),
                    d.abonos().stream().map(RespuestaAbono::de).toList());
        }
    }

    /** {@code esperado = fondo + ventasEfectivo + abonosEfectivo − devoluciones − gastos − retiros − compras}. */
    record RespuestaCierre(long ventasEfectivo, long ventasTransferencia, long ventasFiado, long descuentos,
                           long devolucionesEfectivo, long abonosEfectivo, long abonosTransferencia, long gastosCajon,
                           long retiros, long comprasCajon, long esperado, long contado, long diferencia) {

        static RespuestaCierre de(DetalleTurno.Cierre c) {
            return c == null ? null
                    : new RespuestaCierre(pesos(c.ventasEfectivo()), pesos(c.ventasTransferencia()),
                            pesos(c.ventasFiado()), pesos(c.descuentos()), pesos(c.devolucionesEfectivo()),
                            pesos(c.abonosEfectivo()), pesos(c.abonosTransferencia()), pesos(c.gastosCajon()),
                            pesos(c.retiros()), pesos(c.comprasCajon()), pesos(c.esperado()), pesos(c.contado()),
                            pesos(c.diferencia()));
        }
    }

    /** Lo que debería haber ahora en un turno abierto, con cada parte. */
    record RespuestaArqueo(long ventasEfectivo, long ventasTransferencia, long ventasFiado, long descuentos,
                           long devolucionesEfectivo, long abonosEfectivo, long abonosTransferencia, long gastosCajon,
                           long retiros, long comprasCajon, long esperado) {

        static RespuestaArqueo de(DetalleTurno.Arqueo a) {
            return a == null ? null
                    : new RespuestaArqueo(pesos(a.ventasEfectivo()), pesos(a.ventasTransferencia()),
                            pesos(a.ventasFiado()), pesos(a.descuentos()), pesos(a.devolucionesEfectivo()),
                            pesos(a.abonosEfectivo()), pesos(a.abonosTransferencia()), pesos(a.gastosCajon()),
                            pesos(a.retiros()), pesos(a.comprasCajon()), pesos(a.esperado()));
        }
    }

    /** Un abono de un cliente recibido en el turno (spec 0008): el efectivo entra al cajón; la transferencia, no. */
    record RespuestaAbono(UUID id, long numero, UUID clienteId, String cliente, long monto, FormaPago forma,
                          Instant recibidoEn, Persona recibidoPor, Instant anuladoEn, Persona anuladoPor,
                          String motivoAnulacion) {

        static RespuestaAbono de(DetalleTurno.AbonoDelTurno a) {
            return new RespuestaAbono(a.id(), a.numero(), a.clienteId(), a.cliente(), pesos(a.monto()), a.forma(),
                    a.recibidoEn(), a.recibidoPor(), a.anuladoEn(), a.anuladoPor(), a.motivoAnulacion());
        }
    }

    record RespuestaRetiro(UUID id, long monto, String motivo, Instant registradoEn, Persona registradoPor,
                           Instant anuladoEn, Persona anuladoPor, String motivoAnulacion) {

        static RespuestaRetiro de(DetalleTurno.DetalleRetiro r) {
            return new RespuestaRetiro(r.id(), pesos(r.monto()), r.motivo(), r.registradoEn(), r.registradoPor(),
                    r.anuladoEn(), r.anuladoPor(), r.motivoAnulacion());
        }
    }

    record RespuestaCompraDeCaja(UUID id, Instant fechaRegistro, LocalDate fechaDocumento, String proveedor,
                                 String numeroFactura, long total, EstadoCompra estado) {

        static RespuestaCompraDeCaja de(DetalleTurno.CompraDeCaja c) {
            return new RespuestaCompraDeCaja(c.id(), c.fechaRegistro(), c.fechaDocumento(), c.proveedor(),
                    c.numeroFactura(), pesos(c.total()), c.estado());
        }
    }

    record RespuestaVenta(UUID id, long numero, UUID turnoId, Instant cobradaEn, EstadoVenta estado, long total,
                          long descuento, long efectivo, long transferencia, long fiado, Instant anuladaEn,
                          UUID anuladaEnTurnoId, String motivoAnulacion) {

        static RespuestaVenta de(DetalleTurno.VentaDelTurno v) {
            return new RespuestaVenta(v.id(), v.numero(), v.turnoId(), v.cobradaEn(), v.estado(), pesos(v.total()),
                    pesos(v.descuento()), pesos(v.efectivo()), pesos(v.transferencia()), pesos(v.fiado()),
                    v.anuladaEn(), v.anuladaEnTurnoId(), v.motivoAnulacion());
        }
    }

    private static long pesos(Dinero dinero) {
        return dinero.valor().longValueExact();
    }

    private static Long pesosONulo(Dinero dinero) {
        return dinero == null ? null : pesos(dinero);
    }
}
