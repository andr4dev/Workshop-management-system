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

    protected LineaVenta() {
        // JPA
    }

    private LineaVenta(Variante variante, int cantidad) {
        this.id = UUID.randomUUID();
        this.variante = variante;
        this.cantidad = cantidad;
        this.precioUnitario = variante.getPrecio();
        // Se construye multiplicando, no se recibe: el total de un renglón no puede discrepar de su
        // precio por su cantidad.
        this.total = variante.getPrecio().por(cantidad);
    }

    /** Al precio de venta que tiene el repuesto en este momento. */
    public static LineaVenta de(Variante variante, int cantidad) {
        if (variante == null) {
            throw new ReglaDeNegocioException("El renglón no dice qué repuesto se vende");
        }
        if (cantidad <= 0) {
            throw new ReglaDeNegocioException("La cantidad de " + variante.getCodigo() + " tiene que ser mayor a 0");
        }
        return new LineaVenta(variante, cantidad);
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
