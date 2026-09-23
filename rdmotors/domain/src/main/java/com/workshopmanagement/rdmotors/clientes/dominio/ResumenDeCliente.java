package com.workshopmanagement.rdmotors.clientes.dominio;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * FILA DE LECTURA — un cliente en la lista de la Cartera (spec 0008, RF-018 y RF-021): cuánto debe, desde cuándo y lo
 * que ha tenido fiado y pagado. Las mismas cuentas que {@link CarteraDelCliente}, hechas por la base para toda la
 * lista de una vez.
 *
 * @param pendientes    cuántas deudas tienen algo por pagar
 * @param desde         el día de la deuda pendiente más vieja; {@code null} si está al día
 * @param fiadoTotal    lo que se le ha fiado en total, sin lo anulado ("prestado histórico" del car‑wash)
 * @param pagadoTotal   lo que ha abonado en total, sin lo anulado
 * @param ultimoMovimiento el último fiado o abono
 */
public record ResumenDeCliente(UUID clienteId, String nombre, String documento, String celular, boolean fiadoCerrado,
                               Dinero debe, Dinero aFavor, int pendientes, LocalDate desde, Dinero fiadoTotal,
                               Dinero pagadoTotal, Instant ultimoMovimiento) {

    /** Del que más debe al que menos; a igual deuda, el que debe desde antes. */
    public static final Comparator<ResumenDeCliente> DEL_QUE_MAS_DEBE = Comparator
            .comparing(ResumenDeCliente::debe).reversed()
            .thenComparing(ResumenDeCliente::desde, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ResumenDeCliente::nombre);

    /** El historial, del último movimiento al más viejo. */
    public static final Comparator<ResumenDeCliente> DEL_ULTIMO_MOVIMIENTO = Comparator
            .comparing(ResumenDeCliente::ultimoMovimiento, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(ResumenDeCliente::nombre);

    /** El de una cartera ya armada: las mismas cuentas, para quien no tiene la base (los falsos de las pruebas). */
    public static ResumenDeCliente de(CarteraDelCliente cartera) {
        Cliente cliente = cartera.cliente();
        return new ResumenDeCliente(cliente.getId(), cliente.getNombre(), cliente.getDocumento(), cliente.getCelular(),
                cliente.isFiadoCerrado(), cartera.debe(), cartera.aFavor(), cartera.pendientes().size(),
                cartera.desdeCuando(),
                cartera.deudas().stream().filter(d -> !d.estaAnulada()).map(Deuda::getMonto)
                        .reduce(Dinero.CERO, Dinero::mas),
                cartera.abonos().stream().filter(a -> !a.estaAnulado()).map(Abono::getMonto)
                        .reduce(Dinero.CERO, Dinero::mas),
                Stream.concat(cartera.deudas().stream().map(Deuda::getRegistradaEn),
                        cartera.abonos().stream().map(Abono::getRecibidoEn)).max(Comparator.naturalOrder()).orElse(null));
    }

    public boolean debeAlgo() {
        return !debe.esCero();
    }
}
