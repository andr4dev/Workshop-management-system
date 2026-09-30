package com.workshopmanagement.rdmotors.ventas.aplicacion;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.ventas.dominio.CambioDeAceite;
import com.workshopmanagement.rdmotors.ventas.dominio.Descuento;
import com.workshopmanagement.rdmotors.ventas.dominio.ModoDescuento;
import com.workshopmanagement.rdmotors.ventas.dominio.PagoVenta;

/**
 * Lo que manda la pantalla para cobrar una venta (spec 0003, RF-010 a RF-016).
 *
 * @param llave     nace con la venta en el navegador; la misma en un reintento
 * @param descuento {@code null} si no hubo
 * @param clienteId a nombre de quién, o {@code null}; obligatorio si hay fiado (spec 0008)
 * @param fiado     lo que queda debiendo el cliente, en pesos; 0 si pagó todo
 * @param pagaPrimero en una venta fiada, los repuestos (por variante) que el cajero marcó como pagados al llevárselos,
 *                  en su orden (spec 0016, decisión 2); vacía: lo pagado cubre en el orden de la venta
 */
public record ComandoCobrarVenta(
        UUID llave,
        List<Renglon> renglones,
        ComandoDescuento descuento,
        List<Pago> pagos,
        UUID clienteId,
        long fiado,
        Actor actor,
        List<UUID> pagaPrimero) {

    /** Una venta de contado, sin cliente: la de siempre. */
    public ComandoCobrarVenta(UUID llave, List<Renglon> renglones, ComandoDescuento descuento, List<Pago> pagos,
                              Actor actor) {
        this(llave, renglones, descuento, pagos, null, 0, actor);
    }

    /** Sin marcar qué se paga al llevárselo: lo de antes del spec 0016. */
    public ComandoCobrarVenta(UUID llave, List<Renglon> renglones, ComandoDescuento descuento, List<Pago> pagos,
                              UUID clienteId, long fiado, Actor actor) {
        this(llave, renglones, descuento, pagos, clienteId, fiado, actor, List.of());
    }

    public ComandoCobrarVenta {
        if (llave == null) {
            throw new ReglaDeNegocioException("Al cobro le falta su llave");
        }
        if (actor == null) {
            throw new ReglaDeNegocioException("Falta quién vende");
        }
        if (renglones == null || renglones.isEmpty()) {
            throw new ReglaDeNegocioException("La venta no tiene repuestos");
        }
        if (fiado < 0) {
            throw new ReglaDeNegocioException("Lo fiado no puede ser negativo");
        }
        if (fiado > 0 && clienteId == null) {
            throw new ReglaDeNegocioException("Para fiar hay que decir a quién");
        }
        renglones = List.copyOf(renglones);
        pagos = pagos == null ? List.of() : List.copyOf(pagos);
        pagaPrimero = pagaPrimero == null ? List.of() : List.copyOf(pagaPrimero);
        if (!pagaPrimero.isEmpty() && fiado == 0) {
            throw new ReglaDeNegocioException("Solo se escoge qué se paga ahora cuando se fía");
        }
        for (UUID varianteId : pagaPrimero) {
            if (renglones.stream().noneMatch(r -> varianteId.equals(r.varianteId()))) {
                throw new ReglaDeNegocioException("Lo que se paga ahora tiene que ser de esta venta");
            }
        }
    }

    /** Si queda algo debiendo. */
    public boolean fia() {
        return fiado > 0;
    }

    /**
     * @param precioVisto el precio que mostraba la pantalla al armar la venta. Si el vigente es otro,
     *                    no se cobra (spec 0003, decisión 4 del plan)
     * @param cambio      solo en un repuesto que paga comisión por cambio de aceite: lo que escogió el cajero
     *                    (spec 0015); el precio visto ya lo tiene en cuenta
     * @param cambioPorId si se cambia, quién lo cambió
     */
    public record Renglon(UUID varianteId, int cantidad, long precioVisto, CambioDeAceite cambio, UUID cambioPorId) {

        /** Un repuesto que no paga comisión: el renglón de siempre. */
        public Renglon(UUID varianteId, int cantidad, long precioVisto) {
            this(varianteId, cantidad, precioVisto, null, null);
        }
    }

    /** @param valor pesos si es {@code MONTO}; porcentaje si es {@code PORCENTAJE} */
    public record ComandoDescuento(ModoDescuento modo, BigDecimal valor, String motivo) {

        /** Solo el monto, sin exigir el motivo: para el aviso de pérdida, mientras se escribe. */
        Dinero montoSobre(Dinero subtotal) {
            if (modo == null || valor == null || valor.signum() <= 0) {
                throw new ReglaDeNegocioException("Al descuento le falta cuánto o cómo se capturó");
            }
            return modo == ModoDescuento.PORCENTAJE ? Descuento.montoDelPorcentaje(valor, subtotal) : Dinero.de(valor);
        }

        Descuento sobre(Dinero subtotal) {
            if (modo == null || valor == null) {
                throw new ReglaDeNegocioException("Al descuento le falta cuánto o cómo se capturó");
            }
            return modo == ModoDescuento.PORCENTAJE
                    ? Descuento.porPorcentaje(valor, subtotal, motivo)
                    : Descuento.porMonto(Dinero.de(valor), motivo);
        }
    }

    /** @param recibido solo en efectivo; {@code null} si no se escribió con cuánto pagó */
    public record Pago(FormaPago forma, long monto, Long recibido) {

        PagoVenta aPago() {
            if (forma == null) {
                throw new ReglaDeNegocioException("Cada pago dice si es en efectivo o por transferencia");
            }
            if (forma == FormaPago.TRANSFERENCIA && recibido != null) {
                throw new ReglaDeNegocioException("Lo recibido solo aplica al efectivo");
            }
            return forma == FormaPago.EFECTIVO
                    ? PagoVenta.efectivo(Dinero.de(monto), recibido == null ? null : Dinero.de(recibido))
                    : PagoVenta.transferencia(Dinero.de(monto));
        }
    }
}
