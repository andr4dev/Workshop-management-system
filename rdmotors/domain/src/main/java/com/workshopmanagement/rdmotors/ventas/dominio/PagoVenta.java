package com.workshopmanagement.rdmotors.ventas.dominio;

import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Una parte del cobro de una venta: en efectivo o por transferencia (spec 0003, RF-010). Una venta
 * mixta tiene dos.
 *
 * <p>{@code monto} es lo que se aplica a la venta. En efectivo, {@code recibido} es el billete que
 * entregó el cliente: con él sale el cambio del comprobante. Al cajón entra el monto, no lo recibido;
 * el cambio sale del mismo cajón en el mismo momento.
 */
@Entity
@Table(name = "pago_venta")
@Getter
public class PagoVenta {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma", nullable = false, length = 15)
    private FormaPago forma;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "monto", nullable = false))
    private Dinero monto;

    /** Solo en efectivo, y solo si se escribió. Nunca menor que el monto. */
    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "recibido"))
    private Dinero recibido;

    protected PagoVenta() {
        // JPA
    }

    private PagoVenta(FormaPago forma, Dinero monto, Dinero recibido) {
        this.id = UUID.randomUUID();
        this.forma = forma;
        this.monto = monto;
        this.recibido = recibido;
    }

    /**
     * @param recibido con cuánto pagó el cliente; {@code null} si no se escribió (pagó exacto o no se
     *                 contó)
     */
    public static PagoVenta efectivo(Dinero monto, Dinero recibido) {
        exigirMonto(monto);
        if (recibido != null && monto.esMayorQue(recibido)) {
            throw new ReglaDeNegocioException("Lo recibido en efectivo (" + recibido.enPesos()
                    + ") no alcanza para la parte en efectivo (" + monto.enPesos() + ")");
        }
        return new PagoVenta(FormaPago.EFECTIVO, monto, recibido);
    }

    public static PagoVenta transferencia(Dinero monto) {
        exigirMonto(monto);
        return new PagoVenta(FormaPago.TRANSFERENCIA, monto, null);
    }

    private static void exigirMonto(Dinero monto) {
        if (monto == null || monto.esNegativo() || monto.esCero()) {
            throw new ReglaDeNegocioException("Cada pago tiene que ser mayor a $0");
        }
    }

    void asignarA(Venta venta) {
        this.venta = venta;
    }

    /** Lo que se le devuelve al cliente. $0 si no se escribió lo recibido o si pagó exacto. */
    public Dinero cambio() {
        return recibido == null ? Dinero.CERO : recibido.menos(monto);
    }
}
