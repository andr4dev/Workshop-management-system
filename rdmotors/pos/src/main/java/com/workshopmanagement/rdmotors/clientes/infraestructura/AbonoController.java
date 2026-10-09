package com.workshopmanagement.rdmotors.clientes.infraestructura;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *
 * <p><b>Mixto</b> (spec 0017): {@code forma: MIXTO} con el total en {@code monto} y la parte en {@code efectivo}. Son dos
 * abonos que nacen juntos, y el recibo es del pago: pedido por cualquiera de las dos partes, trae las dos.
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
            if (peticion.forma() == ComoPaga.MIXTO) {
                List<Abono> partes = registrarAbono.ejecutarMixto(peticion.llave(), peticion.clienteId(),
                        Dinero.de(peticion.monto()), peticion.efectivo() == null ? null : Dinero.de(peticion.efectivo()),
                        peticion.referencia(), peticion.nota(), peticion.loQuePagaPrimero(), actor);
                return ResponseEntity.status(HttpStatus.CREATED).body(recibo(partes.getFirst().getId()));
            }
            Abono abono = registrarAbono.ejecutar(peticion.llave(), peticion.clienteId(), Dinero.de(peticion.monto()),
                    peticion.forma().forma(), peticion.referencia(), peticion.nota(), peticion.loQuePagaPrimero(),
                    actor);
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

    /** Cómo paga el abono: una forma, o las dos (spec 0017). {@link FormaPago} sigue teniendo solo dos. */
    enum ComoPaga {
        EFECTIVO, TRANSFERENCIA, MIXTO;

        FormaPago forma() {
            return FormaPago.valueOf(name());
        }
    }

    /**
     * @param monto    en un mixto, el total
     * @param efectivo solo en un mixto: la parte en efectivo; lo demás llegó por transferencia
     * @param primeroA la venta que el cliente dijo que paga; sin ella, a lo más viejo (RF-012). La manda la pantalla
     *                 de antes del spec 0016
     * @param primero  los productos (o ventas) que el cliente dijo que paga, en ese orden (spec 0016)
     */
    record PeticionAbono(@NotNull UUID llave, @NotNull UUID clienteId, @Positive long monto, @NotNull ComoPaga forma,
                         Long efectivo, String referencia, String nota, UUID primeroA, List<UUID> primero) {

        List<UUID> loQuePagaPrimero() {
            if (primero != null && !primero.isEmpty()) {
                return primero;
            }
            return primeroA == null ? List.of() : List.of(primeroA);
        }
    }

    record PeticionAnulacion(String motivo) {
    }

    // ── Lo que sale ──────────────────────────────────────────────────────────

    /**
     * El recibo del abono: cuánto, cómo, a qué ventas se aplicó y cuánto sigue debiendo (RF-016). De un mixto
     * (spec 0017), del pago entero: el total, {@code forma: MIXTO}, sus dos partes y lo aplicado por las dos, sumado por
     * deuda.
     *
     * @param numero      el de la primera parte
     * @param debeDespues lo que debía justo después de este abono: al reimprimirlo dice lo mismo
     * @param debeAhora   lo que debe hoy
     * @param partes      una en un abono de una forma; en un mixto, la de efectivo y la de transferencia
     */
    record RespuestaAbono(UUID id, long numero, RespuestaCliente cliente, long monto, String forma,
                          String referencia, String nota, UUID turnoId, Instant recibidoEn, Persona recibidoPor,
                          long debeDespues, long debeAhora, long sinAplicar, List<RespuestaAplicacion> aplicaciones,
                          Instant anuladoEn, Persona anuladoPor, String motivoAnulacion, UUID pagoId,
                          List<RespuestaParte> partes) {

        static RespuestaAbono de(ConsultarCartera.ReciboDeAbono r) {
            List<FichaCliente.AbonoDeLaFicha> partes = r.partes();
            FichaCliente.AbonoDeLaFicha a = partes.getFirst();
            return new RespuestaAbono(a.id(), a.numero(), RespuestaCliente.de(r.cliente()),
                    partes.stream().mapToLong(p -> pesos(p.monto())).sum(),
                    partes.size() > 1 ? "MIXTO" : a.forma().name(),
                    partes.stream().map(FichaCliente.AbonoDeLaFicha::referencia).filter(x -> x != null).findFirst()
                            .orElse(null),
                    a.nota(), a.turnoId(), a.recibidoEn(), a.recibidoPor(),
                    pesos(partes.getLast().debeDespues()), pesos(r.debeAhora()),
                    partes.stream().mapToLong(p -> pesos(p.sinAplicar())).sum(),
                    lineasDelRecibo(partes),
                    a.anuladoEn(), a.anuladoPor(), a.motivoAnulacion(), a.pagoId(),
                    partes.stream().map(p -> new RespuestaParte(p.id(), p.numero(), p.forma(), pesos(p.monto()),
                            p.referencia())).toList());
        }
    }

    /**
     * Lo aplicado, como lo dice el recibo. Un abono de una forma, cada aplicación como se hizo: igual que siempre, y un
     * recibo de antes se reimprime idéntico. Un mixto, lo de sus dos partes sumado por deuda, en el orden de la ficha
     * (el de pago de las deudas): el cliente pagó una vez cada producto.
     */
    static List<RespuestaAplicacion> lineasDelRecibo(List<FichaCliente.AbonoDeLaFicha> partes) {
        if (partes.size() == 1) {
            return partes.getFirst().aplicaciones().stream().filter(FichaCliente.ParteAplicada::vigente)
                    .map(p -> new RespuestaAplicacion(p.deudaId(), p.deuda(), pesos(p.monto()))).toList();
        }
        Map<UUID, RespuestaAplicacion> aplicado = new LinkedHashMap<>();
        for (FichaCliente.AbonoDeLaFicha parte : partes) {
            for (FichaCliente.ParteAplicada p : parte.aplicaciones()) {
                if (p.vigente()) {
                    aplicado.merge(p.deudaId(), new RespuestaAplicacion(p.deudaId(), p.deuda(), pesos(p.monto())),
                            (x, y) -> new RespuestaAplicacion(x.deudaId(), x.deuda(), x.monto() + y.monto()));
                }
            }
        }
        return List.copyOf(aplicado.values());
    }

    /** Una parte del pago: en un mixto, la de efectivo o la de transferencia, cada una con su número. */
    record RespuestaParte(UUID id, long numero, FormaPago forma, long monto, String referencia) {
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
