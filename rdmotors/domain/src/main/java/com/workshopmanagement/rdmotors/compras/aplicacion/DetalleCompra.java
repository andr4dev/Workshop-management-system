package com.workshopmanagement.rdmotors.compras.aplicacion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.aplicacion.CambioAuditado;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.Persona;
import com.workshopmanagement.rdmotors.compras.dominio.BusquedaDeRepuesto;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.EstadoCompra;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compras.dominio.LineaCompra;
import com.workshopmanagement.rdmotors.compras.dominio.ModoCaptura;
import com.workshopmanagement.rdmotors.inventario.aplicacion.RepuestoEncontrado;

/**
 * Una factura completa, tal como se abre desde el historial (spec 0002, RF-008).
 *
 * <p>Cada renglón trae <b>dos momentos</b> a propósito: lo que se registró en esa compra (cantidad,
 * costo, el precio que fijó) y cómo está el repuesto hoy. La pantalla de corregir arranca con los
 * dos, y el detalle puede mostrar si el precio de hoy sigue siendo el de esa compra.
 *
 * @param version       la que la pantalla devuelve al corregir o anular
 * @param cuenta        nombre de la cuenta, o {@code null} si se pagó en efectivo
 * @param pagadaDeCaja  si se pagó con plata del cajón, y en qué turno (spec 0006)
 * @param renglones     los vigentes, en su orden
 * @param reemplazados  los que una corrección dio de baja: la historia de lo que decía antes
 * @param correcciones  el rastro de auditoría, del más antiguo al más reciente
 */
public record DetalleCompra(
        UUID id,
        long version,
        EstadoCompra estado,
        UUID proveedorId,
        String proveedor,
        LocalDate fechaDocumento,
        Instant fechaRegistro,
        String numeroFactura,
        FormaPago formaPago,
        UUID cuentaId,
        String cuenta,
        boolean pagadaDeCaja,
        UUID turnoId,
        Dinero total,
        Persona registradoPor,
        Instant modificadaEn,
        Instant anuladaEn,
        Persona anuladaPor,
        String motivoAnulacion,
        List<Renglon> renglones,
        List<Renglon> reemplazados,
        List<CambioAuditado> correcciones) {

    /**
     * @param precioVenta   el precio que fijó esta compra, o {@code null} si no lo cambió
     * @param reemplazadaEn cuándo lo dio de baja una corrección; {@code null} si está vigente
     * @param repuesto      el repuesto como está hoy
     * @param coincide      si su repuesto coincide con la búsqueda con que se abrió el detalle
     *                      (RF-026). Siempre {@code false} en un renglón no vigente: no hizo salir
     *                      la compra en la lista, así que tampoco se resalta
     */
    public record Renglon(
            UUID lineaId,
            int posicion,
            int cantidad,
            ModoCaptura modoCaptura,
            Dinero costoTotal,
            BigDecimal costoUnitario,
            Dinero precioVenta,
            Instant reemplazadaEn,
            RepuestoEncontrado repuesto,
            boolean coincide) {

        static Renglon de(LineaCompra linea, boolean coincide) {
            return new Renglon(linea.getId(), linea.getPosicion(), linea.getCantidad(),
                    linea.getModoCaptura(), linea.getCostoTotal(), linea.getCostoUnitario(),
                    linea.getPrecioVenta(), linea.getReemplazadaEn(),
                    RepuestoEncontrado.de(linea.getVariante()), coincide);
        }
    }

    static DetalleCompra de(Compra compra, List<EventoAuditoria> correcciones, Map<UUID, String> nombres) {
        return de(compra, correcciones, null, nombres);
    }

    static DetalleCompra de(Compra compra, List<EventoAuditoria> correcciones, BusquedaDeRepuesto busqueda,
                            Map<UUID, String> nombres) {
        return new DetalleCompra(
                compra.getId(),
                compra.getVersion() == null ? 0L : compra.getVersion(),
                compra.getEstado(),
                compra.getProveedor().getId(),
                compra.getProveedor().getNombre(),
                compra.getFechaDocumento(),
                compra.getFechaRegistro(),
                compra.getNumeroFactura(),
                compra.getFormaPago(),
                compra.getCuenta() == null ? null : compra.getCuenta().getId(),
                compra.getCuenta() == null ? null : compra.getCuenta().getNombre(),
                compra.isPagadaDeCaja(),
                compra.getTurnoId(),
                compra.getTotal(),
                Persona.de(compra.getRegistradoPorId(), nombres),
                compra.getModificadaEn(),
                compra.getAnuladaEn(),
                Persona.de(compra.getAnuladaPorId(), nombres),
                compra.getMotivoAnulacion(),
                compra.lineasVigentes().stream()
                        .map(l -> Renglon.de(l, busqueda != null && busqueda.coincideCon(l.getVariante())))
                        .toList(),
                compra.getLineas().stream().filter(l -> !l.isVigente()).map(l -> Renglon.de(l, false)).toList(),
                correcciones.stream().map(e -> CambioAuditado.de(e, nombres)).toList());
    }
}
