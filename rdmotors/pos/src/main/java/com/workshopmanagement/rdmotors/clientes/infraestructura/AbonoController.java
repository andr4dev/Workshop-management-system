package com.workshopmanagement.rdmotors.clientes.infraestructura;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.workshopmanagement.rdmotors.caja.dominio.MovimientoRepetidoException;
import com.workshopmanagement.rdmotors.clientes.aplicacion.AnularAbono;
import com.workshopmanagement.rdmotors.clientes.aplicacion.ConsultarCartera;
import com.workshopmanagement.rdmotors.clientes.aplicacion.FichaCliente;
import com.workshopmanagement.rdmotors.clientes.aplicacion.RegistrarAbono;
import com.workshopmanagement.rdmotors.clientes.dominio.Abono;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.Persona;
import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.ActorActual;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — los abonos de los clientes (spec 0008, H4).
 *
 * <p>Abonar es {@code POST} y <b>SUMA</b> (baja la deuda, y en efectivo entra al cajón): por eso lleva llave. Repetido
 * con la misma llave no es un error: responde 200 con el abono que ya existe; uno nuevo responde 201.
 *
 * <p>La respuesta trae lo que necesita el recibo: a qué ventas se aplicó y cuánto quedó debiendo el cliente.
 */
@RestController
@RequestMapping("/api/abonos")
@RequiredArgsConstructor
class AbonoController {

    private final RegistrarAbono registrarAbono;
    private final AnularAbono anularAbono;
    private final ConsultarCartera consultarCartera;

    @PostMapping
    ResponseEntity<RespuestaAbono> abonar(@Valid @RequestBody PeticionAbono peticion, @ActorActual Actor actor) {
        try {
            Abono abono = registrarAbono.ejecutar(peticion.llave(), peticion.clienteId(), Dinero.de(peticion.monto()),
                    peticion.forma(), peticion.referencia(), peticion.nota(), peticion.primeroA(), actor);
            return ResponseEntity.status(HttpStatus.CREATED).body(recibo(abono.getId()));
        } catch (MovimientoRepetidoException e) {
            // Dos abonos con la misma llave a la vez: el otro ganó y el abono ya está guardado.
            return ResponseEntity.ok(recibo(porLlave(e.getLlave())));
        }
    }

    /** Para reimprimir el recibo. */
    @GetMapping("/{id}")
    RespuestaAbono ver(@PathVariable UUID id) {
        return recibo(id);
    }

    /** Del administrador; en efectivo, solo mientras su turno siga abierto (RF-017). */
    @PostMapping("/{id}/anulacion")
    RespuestaAbono anular(@PathVariable UUID id, @RequestBody PeticionAnulacion peticion, @ActorActual Actor actor) {
        anularAbono.ejecutar(id, peticion.motivo(), actor);
        return recibo(id);
    }

    /** Se relee en su propia transacción: la respuesta muestra lo que quedó guardado. */
    private RespuestaAbono recibo(UUID abonoId) {
        return consultarCartera.recibo(abonoId).map(RespuestaAbono::de)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ese abono no existe"));
    }

    private UUID porLlave(UUID llave) {
        return consultarCartera.abonoDeLaLlave(llave)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Ese abono ya se registró"));
    }

    // ── Lo que entra ─────────────────────────────────────────────────────────

    /**
     * @param primeroA la venta que el cliente dijo que paga; sin ella, a lo más viejo (RF-012)
     */
    record PeticionAbono(@NotNull UUID llave, @NotNull UUID clienteId, @Positive long monto, @NotNull FormaPago forma,
                         String referencia, String nota, UUID primeroA) {
    }

    record PeticionAnulacion(String motivo) {
    }

    // ── Lo que sale ──────────────────────────────────────────────────────────

    /**
     * El recibo del abono: cuánto, cómo, a qué ventas se aplicó y cuánto sigue debiendo (RF-016).
     *
     * @param debeDespues lo que debía justo después de este abono: al reimprimirlo dice lo mismo
     * @param debeAhora   lo que debe hoy
     */
    record RespuestaAbono(UUID id, long numero, RespuestaCliente cliente, long monto, FormaPago forma,
                          String referencia, String nota, UUID turnoId, Instant recibidoEn, Persona recibidoPor,
                          long debeDespues, long debeAhora, long sinAplicar, List<RespuestaAplicacion> aplicaciones,
                          Instant anuladoEn, Persona anuladoPor, String motivoAnulacion) {

        static RespuestaAbono de(ConsultarCartera.ReciboDeAbono r) {
            FichaCliente.AbonoDeLaFicha a = r.abono();
            return new RespuestaAbono(a.id(), a.numero(), RespuestaCliente.de(r.cliente()), pesos(a.monto()),
                    a.forma(), a.referencia(), a.nota(), a.turnoId(), a.recibidoEn(), a.recibidoPor(),
                    pesos(a.debeDespues()), pesos(r.debeAhora()), pesos(a.sinAplicar()),
                    a.aplicaciones().stream().filter(FichaCliente.ParteAplicada::vigente)
                            .map(p -> new RespuestaAplicacion(p.deudaId(), p.deuda(), pesos(p.monto()))).toList(),
                    a.anuladoEn(), a.anuladoPor(), a.motivoAnulacion());
        }
    }

    record RespuestaAplicacion(UUID deudaId, String deuda, long monto) {
    }

    record RespuestaCliente(UUID id, String nombre, String documento, String celular, long debe) {

        static RespuestaCliente de(com.workshopmanagement.rdmotors.clientes.aplicacion.ClienteEncontrado c) {
            return new RespuestaCliente(c.id(), c.nombre(), c.documento(), c.celular(), pesos(c.debe()));
        }
    }

    private static long pesos(Dinero dinero) {
        return dinero.valor().longValueExact();
    }
}
