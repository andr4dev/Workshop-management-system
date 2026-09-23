package com.workshopmanagement.rdmotors.ventas.aplicacion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.clientes.dominio.Deuda;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.Persona;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.ventas.dominio.EstadoVenta;
import com.workshopmanagement.rdmotors.ventas.dominio.LineaVenta;
import com.workshopmanagement.rdmotors.ventas.dominio.ModoDescuento;
import com.workshopmanagement.rdmotors.ventas.dominio.PagoVenta;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;

/**
 * Una venta completa, como la muestra el comprobante (spec 0003, RF-018): los renglones con el
 * repuesto que se vendió, el descuento y cada pago con su cambio.
 *
 * <p>Las cifras son las guardadas en la venta. El comprobante no las recalcula: el total impreso y el
 * del reporte tienen que salir del mismo sitio.
 *
 * @param vendidoPor quien la cobró, con su nombre: el comprobante dice "Atendió: Carolina" (spec 0004, RF-023)
 * @param anuladaPor quien la anuló, con su nombre; {@code null} si sigue cobrada (RF-022)
 * @param cliente    a nombre de quién; {@code null} si no se dijo (spec 0008)
 * @param fiado      lo que quedó debiendo; $0 si pagó todo
 * @param debeDespues cuánto quedó debiendo el cliente en total justo después de esta venta; {@code null} si no se
 *                    fió: el comprobante lo repite igual al reimprimirlo (spec 0008, RF-009)
 */
public record DetalleVenta(
        UUID id,
        long numero,
        EstadoVenta estado,
        UUID turnoId,
        Persona vendidoPor,
        Instant cobradaEn,
        Dinero subtotal,
        Dinero descuento,
        ModoDescuento descuentoModo,
        BigDecimal descuentoPorcentaje,
        String descuentoMotivo,
        Dinero total,
        Dinero cambio,
        List<Renglon> renglones,
        List<Pago> pagos,
        Instant anuladaEn,
        Persona anuladaPor,
        String motivoAnulacion,
        ClienteDeLaVenta cliente,
        Dinero fiado,
        Dinero debeDespues) {

    /** Con la cédula: el comprobante de una venta fiada dice a nombre de quién quedó. */
    public record ClienteDeLaVenta(UUID id, String nombre, String documento) {

        static ClienteDeLaVenta de(Cliente c) {
            return c == null ? null : new ClienteDeLaVenta(c.getId(), c.getNombre(), c.getDocumento());
        }
    }

    public record Renglon(UUID lineaId, int posicion, UUID varianteId, String codigo, String nombre,
                          String marca, int cantidad, Dinero precioUnitario, Dinero total) {

        static Renglon de(LineaVenta l) {
            Variante v = l.getVariante();
            return new Renglon(l.getId(), l.getPosicion(), v.getId(), v.getCodigo(),
                    v.getProducto().getNombre(), v.getMarcaRepuesto(), l.getCantidad(),
                    l.getPrecioUnitario(), l.getTotal());
        }
    }

    /** @param recibido solo en efectivo, si se escribió */
    public record Pago(FormaPago forma, Dinero monto, Dinero recibido, Dinero cambio) {

        static Pago de(PagoVenta p) {
            return new Pago(p.getForma(), p.getMonto(), p.getRecibido(), p.cambio());
        }
    }

    /**
     * @param nombres  los de quienes vendieron, por id: los trae quien consulta, de una vez para toda la lista
     * @param clientes los clientes de las ventas, por id, también de una vez
     * @param deudas   las deudas de las ventas fiadas, por el id de su venta
     */
    static DetalleVenta de(Venta v, Map<UUID, String> nombres, Map<UUID, Cliente> clientes, Map<UUID, Deuda> deudas) {
        Deuda deuda = deudas.get(v.getId());
        return new DetalleVenta(v.getId(), v.getNumero(), v.getEstado(), v.getTurnoId(),
                Persona.de(v.getVendidoPorId(), nombres),
                v.getCobradaEn(), v.getSubtotal(), v.getDescuentoMonto(), v.getDescuentoModo(),
                v.getDescuentoPorcentaje(), v.getDescuentoMotivo(), v.getTotal(), v.cambio(),
                v.getLineas().stream().map(Renglon::de).toList(),
                v.getPagos().stream().map(Pago::de).toList(),
                v.getAnuladaEn(), Persona.de(v.getAnuladaPorId(), nombres), v.getMotivoAnulacion(),
                v.getClienteId() == null ? null : ClienteDeLaVenta.de(clientes.get(v.getClienteId())),
                v.getFiado(), deuda == null ? null : deuda.getDebeDespues());
    }
}
