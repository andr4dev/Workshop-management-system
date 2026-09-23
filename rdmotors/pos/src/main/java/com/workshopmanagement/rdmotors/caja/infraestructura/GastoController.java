package com.workshopmanagement.rdmotors.caja.infraestructura;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Persona;
import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.ActorActual;
import com.workshopmanagement.rdmotors.caja.aplicacion.AnularGasto;
import com.workshopmanagement.rdmotors.caja.aplicacion.ComandoRegistrarGasto;
import com.workshopmanagement.rdmotors.caja.aplicacion.ConsultarGastos;
import com.workshopmanagement.rdmotors.caja.aplicacion.DetalleGasto;
import com.workshopmanagement.rdmotors.caja.aplicacion.RegistrarGasto;
import com.workshopmanagement.rdmotors.caja.dominio.FiltroGastos;
import com.workshopmanagement.rdmotors.caja.dominio.MovimientoRepetidoException;
import com.workshopmanagement.rdmotors.caja.dominio.NaturalezaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.TotalesGastos;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — los gastos (spec 0006, fase 1).
 *
 * <p>Registrar <b>SUMA</b> plata que sale: lleva llave. Repetido con la misma llave responde el gasto que ya
 * existe. Si es más de lo que debería haber en el cajón, responde 409 {@code CONFIRMAR_MONTO} y la pantalla
 * reenvía con {@code confirmado}.
 */
@RestController
@RequestMapping("/api/gastos")
@RequiredArgsConstructor
class GastoController {

    private final RegistrarGasto registrarGasto;
    private final AnularGasto anularGasto;
    private final ConsultarGastos consultarGastos;

    @PostMapping
    ResponseEntity<RespuestaGasto> registrar(@Valid @RequestBody PeticionGasto peticion,
                                             @ActorActual Actor actor) {
        try {
            var gasto = registrarGasto.ejecutar(peticion.aComando(actor));
            // Se relee en su propia transacción: la respuesta muestra lo que quedó guardado.
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(RespuestaGasto.de(consultarGastos.detalle(gasto.getId(), actor).orElseThrow()));
        } catch (MovimientoRepetidoException e) {
            // Dos envíos con la misma llave a la vez: el otro ganó y el gasto ya está guardado.
            return ResponseEntity.ok(RespuestaGasto.de(consultarGastos.porLlave(e.getLlave(), actor).orElseThrow()));
        }
    }

    /** Del más reciente al más antiguo, con los anulados. Las fechas son las del gasto. */
    @GetMapping
    RespuestaPagina listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) UUID categoriaId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "25") int tamano,
            @ActorActual Actor actor) {
        Pagina<DetalleGasto> p = consultarGastos.listar(new FiltroGastos(desde, hasta, categoriaId), pagina, tamano,
                actor);
        return new RespuestaPagina(p.elementos().stream().map(RespuestaGasto::de).toList(), p.total(), p.numero(),
                p.tamano(), p.totalPaginas());
    }

    /** Los mismos filtros de la lista, sin página. Los anulados no suman. */
    @GetMapping("/totales")
    RespuestaTotales totales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) UUID categoriaId,
            @ActorActual Actor actor) {
        TotalesGastos t = consultarGastos.totales(new FiltroGastos(desde, hasta, categoriaId), actor);
        return new RespuestaTotales(pesos(t.total()), t.gastos(), pesos(t.delCajon()), pesos(t.porFuera()));
    }

    @GetMapping("/{id}")
    RespuestaGasto detalle(@PathVariable UUID id, @ActorActual Actor actor) {
        return consultarGastos.detalle(id, actor).map(RespuestaGasto::de)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese gasto no existe"));
    }

    /** Sin llave: un gasto anulado no se anula dos veces, repetirlo responde 422 "ya fue anulado". */
    @PostMapping("/{id}/anulacion")
    RespuestaGasto anular(@PathVariable UUID id, @RequestBody PeticionAnulacion peticion,
                          @ActorActual Actor actor) {
        anularGasto.ejecutar(id, peticion.motivo(), actor);
        return consultarGastos.detalle(id, actor).map(RespuestaGasto::de).orElseThrow();
    }

    // ── Lo que entra ─────────────────────────────────────────────────────────

    /**
     * {@code formaPago}, {@code cuentaId} y {@code fecha} solo si no salió del cajón. Que el resto cuadre lo
     * exige el dominio, con el mensaje que ve el cajero.
     */
    record PeticionGasto(@NotNull UUID llave, UUID categoriaId, Long monto, String descripcion, boolean delCajon,
                         FormaPago formaPago, UUID cuentaId, LocalDate fecha, boolean delMes, boolean confirmado) {

        ComandoRegistrarGasto aComando(Actor actor) {
            return new ComandoRegistrarGasto(llave, categoriaId, monto == null ? null : Dinero.de(monto), descripcion,
                    delCajon, formaPago, cuentaId, fecha, delMes, confirmado, actor);
        }
    }

    record PeticionAnulacion(String motivo) {
    }

    // ── Lo que sale ──────────────────────────────────────────────────────────

    /** @param registradoPor y {@code anuladoPor}: id y nombre de quién (spec 0004, RF-022) */
    record RespuestaGasto(UUID id, LocalDate fecha, Instant registradoEn, Persona registradoPor, UUID categoriaId,
                          String categoria, NaturalezaGasto naturaleza, long monto, String descripcion,
                          boolean delCajon, boolean delMes, UUID turnoId, FormaPago formaPago, UUID cuentaId, String cuenta,
                          Instant anuladoEn, Persona anuladoPor, String motivoAnulacion) {

        static RespuestaGasto de(DetalleGasto g) {
            return new RespuestaGasto(g.id(), g.fecha(), g.registradoEn(), g.registradoPor(), g.categoriaId(),
                    g.categoria(), g.naturaleza(), pesos(g.monto()), g.descripcion(), g.delCajon(), g.delMes(), g.turnoId(),
                    g.formaPago(), g.cuentaId(), g.cuenta(), g.anuladoEn(), g.anuladoPor(), g.motivoAnulacion());
        }
    }

    record RespuestaPagina(List<RespuestaGasto> elementos, long total, int numero, int tamano, int totalPaginas) {
    }

    /** {@code delCajon + porFuera = total}: salen de sumas separadas, así que cuadrar se puede comprobar. */
    record RespuestaTotales(long total, long gastos, long delCajon, long porFuera) {
    }

    private static long pesos(Dinero dinero) {
        return dinero.valor().longValueExact();
    }
}
