package com.workshopmanagement.rdmotors.compras.aplicacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compras.dominio.LineaCompra;
import com.workshopmanagement.rdmotors.compras.dominio.ModoCaptura;
import com.workshopmanagement.rdmotors.inventario.aplicacion.ComandoCrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/**
 * Lo que el caso de uso necesita para registrar una compra. Es una bolsa de datos a proposito:
 * los DTO de los bordes SI pueden ser anemicos — la regla de tener comportamiento aplica al
 * dominio, no a lo que entra y sale.
 *
 * <p>Nota que no aparece por ningun lado un {@code HttpServletRequest} ni un JSON: el caso de uso
 * no sabe si esto llego por REST, por la cola offline o desde una prueba.
 */
public record ComandoRegistrarCompra(
        UUID proveedorId,
        LocalDate fechaDocumento,
        String numeroFactura,
        FormaPago formaPago,
        UUID cuentaId,
        Actor actor,
        List<Linea> lineas,
        boolean pagadaDeCaja,
        UUID llave) {

    /** Una compra que no se pagó con plata del cajón: la forma de todas antes del spec 0006. */
    public ComandoRegistrarCompra(UUID proveedorId, LocalDate fechaDocumento, String numeroFactura,
                                  FormaPago formaPago, UUID cuentaId, Actor actor, List<Linea> lineas) {
        this(proveedorId, fechaDocumento, numeroFactura, formaPago, cuentaId, actor, lineas, false);
    }

    /** Sin llave: la forma de todas antes del spec 0009. Se le pone una propia, distinta cada vez. */
    public ComandoRegistrarCompra(UUID proveedorId, LocalDate fechaDocumento, String numeroFactura,
                                  FormaPago formaPago, UUID cuentaId, Actor actor, List<Linea> lineas,
                                  boolean pagadaDeCaja) {
        this(proveedorId, fechaDocumento, numeroFactura, formaPago, cuentaId, actor, lineas, pagadaDeCaja,
                UUID.randomUUID());
    }

    /**
     * <b>La forma de pago no tiene valor por defecto</b>, ni aquí ni en la pantalla. Un "efectivo"
     * puesto por omisión se registra sin que nadie lo haya decidido, y el reporte lo cree.
     *
     * <p>Que la cuenta cuadre con la forma de pago no se valida aquí: lo exige {@code Compra}, que
     * es la dueña de la regla y la aplica también al corregir.
     */
    public ComandoRegistrarCompra {
        if (proveedorId == null) throw new ReglaDeNegocioException("Falta el proveedor");
        if (fechaDocumento == null) throw new ReglaDeNegocioException("Falta la fecha de la factura");
        if (actor == null) throw new ReglaDeNegocioException("Falta el usuario que registra");
        if (lineas == null || lineas.isEmpty()) {
            throw new ReglaDeNegocioException("La compra necesita al menos un renglon");
        }
    }

    /**
     * Un renglon de la factura.
     *
     * <p>El repuesto llega de una de dos formas, nunca de las dos: <b>o</b> el id de uno que ya
     * existe, <b>o</b> los datos para crearlo sin salir de la compra. Ese segundo camino es la
     * respuesta a que el cliente fije precios a mano: el repuesto nace cuando la mercancia llega,
     * uno a la vez, en el momento en que importa.
     *
     * <p>Segun {@code modo} se llena {@code costoTotal} o {@code costoUnitario} — el otro lo
     * deduce el dominio.
     *
     * @param precioVenta {@code null} = no tocar el precio del repuesto. En un repuesto nuevo va
     *                    siempre null: su precio viaja dentro de {@code repuestoNuevo}.
     */
    public record Linea(
            UUID varianteId,
            ComandoCrearRepuesto repuestoNuevo,
            int cantidad,
            ModoCaptura modo,
            Dinero costoTotal,
            BigDecimal costoUnitario,
            Dinero precioVenta) {

        public Linea {
            boolean existente = varianteId != null;
            boolean nuevo = repuestoNuevo != null;

            if (existente && nuevo) {
                throw new ReglaDeNegocioException(
                        "El renglon trae un repuesto existente y uno nuevo a la vez. Es uno u otro.");
            }
            if (!existente && !nuevo) {
                throw new ReglaDeNegocioException(
                        "El renglon no dice que repuesto es: elige uno existente o crea el nuevo.");
            }
            if (nuevo && precioVenta != null) {
                // Evita dos fuentes para el mismo dato. El precio de un repuesto nuevo va donde
                // el usuario lo escribio: en el formulario de creacion.
                throw new ReglaDeNegocioException(
                        "El precio de venta de un repuesto nuevo va en sus datos de creacion, "
                                + "no en el renglon de la compra.");
            }
        }

        // ── Repuesto que YA existe ───────────────────────────────────────────
        // Las firmas de estas dos no cambian nunca: son las que usan las pruebas que ya pasaban.

        /** Modo lote: "me llegaron 15 y pague $200.000". */
        public static Linea porTotal(UUID varianteId, int cantidad, Dinero total, Dinero precioVenta) {
            return new Linea(varianteId, null, cantidad, ModoCaptura.TOTAL, total, null, precioVenta);
        }

        /** Modo unitario: "me llegaron 20 a $10.000 cada uno". */
        public static Linea porUnitario(UUID varianteId, int cantidad, BigDecimal unitario,
                                        Dinero precioVenta) {
            return new Linea(varianteId, null, cantidad, ModoCaptura.UNITARIO, null, unitario,
                    precioVenta);
        }

        // ── Repuesto que se crea aqui mismo ──────────────────────────────────

        /** El repuesto no existia: nace con esta compra, y su precio viene en {@code nuevo}. */
        public static Linea porTotalCreando(ComandoCrearRepuesto nuevo, int cantidad, Dinero total) {
            return new Linea(null, nuevo, cantidad, ModoCaptura.TOTAL, total, null, null);
        }

        /** Igual que {@link #porTotalCreando}, capturando el costo por unidad. */
        public static Linea porUnitarioCreando(ComandoCrearRepuesto nuevo, int cantidad,
                                               BigDecimal unitario) {
            return new Linea(null, nuevo, cantidad, ModoCaptura.UNITARIO, null, unitario, null);
        }

        public boolean creaRepuesto() {
            return repuestoNuevo != null;
        }

        LineaCompra aLineaDe(Variante variante) {
            return switch (modo) {
                case TOTAL -> LineaCompra.porTotal(variante, cantidad, costoTotal, precioVenta);
                case UNITARIO -> LineaCompra.porUnitario(variante, cantidad, costoUnitario, precioVenta);
            };
        }
    }
}
