package com.workshopmanagement.rdmotors.ventas.dominio;

import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Un renglón de una venta.
 *
 * <p><b>El precio es una foto, no una referencia</b> (`SPEC_Modelo_Datos.md` §3.5). Si mañana sube el
 * precio del repuesto, el comprobante de ayer no puede cambiar: por eso se copia al cobrar.
 *
 * <p>Guarda con qué movimiento de kardex salió del inventario. Es lo que permite, al anular, que la
 * reversión apunte a su salida, y que la regla de compras sepa que esa salida se deshizo (RF-030).
 */
@Entity
@Table(name = "linea_venta")
@Getter
public class LineaVenta {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    /** El orden en que el cajero agregó los repuestos: así sale en el comprobante. */
    @Column(name = "posicion", nullable = false)
    private int posicion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variante_id", nullable = false)
    private Variante variante;

    @Column(name = "cantidad", nullable = false)
    private int cantidad;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "precio_unitario", nullable = false))
    private Dinero precioUnitario;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "total", nullable = false))
    private Dinero total;

    @Column(name = "movimiento_salida_id")
    private UUID movimientoSalidaId;

    /** Solo en un repuesto que paga comisión por cambio (spec 0015): lo que escogió el cajero. */
    @Enumerated(EnumType.STRING)
    @Column(name = "cambio", length = 15)
    private CambioDeAceite cambio;

    /** Si se cambió: quién hizo el cambio y se lleva la comisión. */
    @Column(name = "cambio_por_id")
    private UUID cambioPorId;

    /** La comisión del repuesto al cobrar × la cantidad: la que se pagó del cajón, o la que se descontó del precio. */
    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "comision"))
    private Dinero comision;

    /** El gasto del cajón con que se pagó la comisión, si se cambió. */
    @Column(name = "comision_gasto_id")
    private UUID comisionGastoId;

    protected LineaVenta() {
        // JPA
    }

    private LineaVenta(Variante variante, int cantidad, CambioDeAceite cambio, UUID cambioPorId) {
        this.id = UUID.randomUUID();
        this.variante = variante;
        this.cantidad = cantidad;
        this.precioUnitario = precioSegun(variante, cambio);
        // Se construye multiplicando, no se recibe: el total de un renglón no puede discrepar de su
        // precio por su cantidad.
        this.total = this.precioUnitario.por(cantidad);
        this.cambio = cambio;
        this.cambioPorId = cambio == CambioDeAceite.SE_CAMBIA ? cambioPorId : null;
        this.comision = cambio == null ? null : variante.getComisionCambio().por(cantidad);
    }

    /** Al precio de venta que tiene el repuesto en este momento. Un repuesto que paga comisión no entra así. */
    public static LineaVenta de(Variante variante, int cantidad) {
        return de(variante, cantidad, null, null);
    }

    /**
     * Al precio de venta que tiene el repuesto en este momento, según lo que se escogió si paga comisión por cambio de
     * aceite (spec 0015, decisión 3): <i>no se cambia</i> baja la comisión del precio.
     *
     * @param cambio      obligatorio si el repuesto paga comisión; nulo si no
     * @param cambioPorId si se cambia, quién lo cambió
     */
    public static LineaVenta de(Variante variante, int cantidad, CambioDeAceite cambio, UUID cambioPorId) {
        if (variante == null) {
            throw new ReglaDeNegocioException("El renglón no dice qué repuesto se vende");
        }
        if (cantidad <= 0) {
            throw new ReglaDeNegocioException("La cantidad de " + variante.getCodigo() + " tiene que ser mayor a 0");
        }
        String nombre = variante.getProducto().getNombre();
        if (variante.pagaComisionDeCambio() && cambio == null) {
            throw new ReglaDeNegocioException("Escoge si el " + nombre + " se cambia aquí o no");
        }
        if (!variante.pagaComisionDeCambio() && cambio != null) {
            throw new ReglaDeNegocioException("El " + nombre + " no paga comisión por cambio: no se escoge si se cambia");
        }
        if (cambio == CambioDeAceite.SE_CAMBIA && cambioPorId == null) {
            throw new ReglaDeNegocioException("Di quién le cambió el aceite al " + nombre);
        }
        if (cambio == CambioDeAceite.NO_SE_CAMBIA && !variante.getPrecio().esMayorQue(variante.getComisionCambio())) {
            throw new ReglaDeNegocioException("La comisión del " + nombre + " no cabe en su precio");
        }
        return new LineaVenta(variante, cantidad, cambio, cambioPorId);
    }

    /**
     * El precio por unidad según lo que se escogió: el del repuesto, o sin la comisión si <i>no se cambia</i>. Es el
     * que el cobro compara con el que vio la pantalla.
     */
    public static Dinero precioSegun(Variante variante, CambioDeAceite cambio) {
        return cambio == CambioDeAceite.NO_SE_CAMBIA && variante.pagaComisionDeCambio()
                ? variante.getPrecio().menos(variante.getComisionCambio())
                : variante.getPrecio();
    }

    /** Si se cambió aquí y la comisión sale del cajón. */
    public boolean seCambia() {
        return cambio == CambioDeAceite.SE_CAMBIA;
    }

    /** El gasto con que se pagó la comisión. Se anota una vez, al cobrar. */
    public void anotarGastoDeComision(UUID gastoId) {
        if (!seCambia()) {
            throw new IllegalStateException("El renglón no se cambió: no paga comisión");
        }
        if (this.comisionGastoId != null) {
            throw new IllegalStateException("El renglón ya tiene el gasto de su comisión");
        }
        this.comisionGastoId = gastoId;
    }

    void asignarA(Venta venta, int posicion) {
        this.venta = venta;
        this.posicion = posicion;
    }

    /** Con qué movimiento de kardex salió. Se anota una vez, al cobrar. */
    public void anotarSalida(UUID movimientoId) {
        if (this.movimientoSalidaId != null) {
            throw new IllegalStateException("El renglón ya tiene su movimiento de salida");
        }
        this.movimientoSalidaId = movimientoId;
    }
}
