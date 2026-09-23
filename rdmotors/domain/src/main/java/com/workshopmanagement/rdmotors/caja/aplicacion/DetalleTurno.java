package com.workshopmanagement.rdmotors.caja.aplicacion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.workshopmanagement.rdmotors.caja.dominio.ArqueoDeTurno;
import com.workshopmanagement.rdmotors.caja.dominio.EstadoTurno;
import com.workshopmanagement.rdmotors.caja.dominio.Retiro;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.clientes.dominio.Abono;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.Persona;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.EstadoCompra;
import com.workshopmanagement.rdmotors.ventas.dominio.EstadoVenta;
import com.workshopmanagement.rdmotors.ventas.dominio.PagoVenta;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;

/**
 * Un turno con todo lo que pasó en él (spec 0006, RF-020 y RF-023): lo que salió del cajón, las ventas, y las
 * de otros turnos que se anularon en este.
 *
 * <p><b>Abierto</b>, trae {@code arqueo}: lo que debería haber en este momento y de dónde sale, calculado al
 * pedirlo (cambio del 2026-09-16: se ve en vivo en la sección Caja). {@code cierre} va en {@code null}.
 *
 * <p><b>Cerrado</b>, trae {@code cierre}: las cifras que se guardaron al cerrar, no unas recalculadas. {@code arqueo}
 * va en {@code null}: un cierre ya se firmó y no se vuelve a calcular.
 */
public record DetalleTurno(
        UUID id,
        EstadoTurno estado,
        Dinero fondo,
        Instant abiertoEn,
        Persona abiertoPor,
        Instant cerradoEn,
        Persona cerradoPor,
        Cierre cierre,
        Arqueo arqueo,
        String observaciones,
        List<DetalleGasto> gastos,
        List<DetalleRetiro> retiros,
        List<CompraDeCaja> compras,
        List<VentaDelTurno> ventas,
        List<VentaDelTurno> anuladasDeOtrosTurnos,
        List<AbonoDelTurno> abonos) {

    /** Las cifras firmadas al cerrar. Las partes suman {@code esperado}; {@code diferencia = contado − esperado}. */
    public record Cierre(Dinero ventasEfectivo, Dinero ventasTransferencia, Dinero ventasFiado, Dinero descuentos,
                         Dinero devolucionesEfectivo, Dinero abonosEfectivo, Dinero abonosTransferencia,
                         Dinero gastosCajon, Dinero retiros, Dinero comprasCajon, Dinero esperado, Dinero contado,
                         Dinero diferencia) {

        static Cierre de(TurnoCaja t) {
            return t.estaAbierto() ? null
                    : new Cierre(t.getVentasEfectivo(), t.getVentasTransferencia(), t.getVentasFiado(),
                            t.getDescuentos(), t.getDevolucionesEfectivo(), t.getAbonosEfectivo(),
                            t.getAbonosTransferencia(), t.getGastosCajon(), t.getRetiros(), t.getComprasCajon(),
                            t.getEsperado(), t.getContado(), t.getDiferencia());
        }
    }

    /** Lo que debería haber AHORA en el cajón de un turno abierto, con cada parte. Las partes suman {@code esperado}. */
    public record Arqueo(Dinero ventasEfectivo, Dinero ventasTransferencia, Dinero ventasFiado, Dinero descuentos,
                         Dinero devolucionesEfectivo, Dinero abonosEfectivo, Dinero abonosTransferencia,
                         Dinero gastosCajon, Dinero retiros, Dinero comprasCajon, Dinero esperado) {

        static Arqueo de(ArqueoDeTurno a) {
            return a == null ? null
                    : new Arqueo(a.ventasEfectivo(), a.ventasTransferencia(), a.ventasFiado(), a.descuentos(),
                            a.devolucionesEfectivo(), a.abonosEfectivo(), a.abonosTransferencia(), a.gastosCajon(),
                            a.retiros(), a.comprasCajon(), a.esperado());
        }
    }

    /**
     * Un abono de un cliente recibido en el turno (spec 0008): en efectivo entra al cajón; por transferencia, no.
     *
     * @param cliente a nombre de quién: el arqueo tiene que poder señalar de dónde salió cada peso
     */
    public record AbonoDelTurno(UUID id, long numero, UUID clienteId, String cliente, Dinero monto, FormaPago forma,
                                Instant recibidoEn, Persona recibidoPor, Instant anuladoEn, Persona anuladoPor,
                                String motivoAnulacion) {

        static AbonoDelTurno de(Abono a, String cliente, Map<UUID, String> nombres) {
            return new AbonoDelTurno(a.getId(), a.getNumero(), a.getClienteId(), cliente, a.getMonto(), a.getForma(),
                    a.getRecibidoEn(), Persona.de(a.getRecibidoPorId(), nombres), a.getAnuladoEn(),
                    Persona.de(a.getAnuladoPorId(), nombres), a.getMotivoAnulacion());
        }
    }

    public record DetalleRetiro(UUID id, Dinero monto, String motivo, Instant registradoEn, Persona registradoPor,
                                Instant anuladoEn, Persona anuladoPor, String motivoAnulacion) {

        static DetalleRetiro de(Retiro r, Map<UUID, String> nombres) {
            return new DetalleRetiro(r.getId(), r.getMonto(), r.getMotivo(), r.getRegistradoEn(),
                    Persona.de(r.getRegistradoPorId(), nombres), r.getAnuladoEn(),
                    Persona.de(r.getAnuladoPorId(), nombres), r.getMotivoAnulacion());
        }
    }

    public record CompraDeCaja(UUID id, Instant fechaRegistro, LocalDate fechaDocumento, String proveedor,
                               String numeroFactura, Dinero total, EstadoCompra estado) {

        static CompraDeCaja de(Compra c) {
            return new CompraDeCaja(c.getId(), c.getFechaRegistro(), c.getFechaDocumento(),
                    c.getProveedor().getNombre(), c.getNumeroFactura(), c.getTotal(), c.getEstado());
        }
    }

    /**
     * Una venta con lo que puso en cada lado: el efectivo entra al cajón, la transferencia y lo fiado no.
     *
     * @param fiado lo que quedó debiendo el cliente (spec 0008)
     */
    public record VentaDelTurno(UUID id, long numero, UUID turnoId, Instant cobradaEn, EstadoVenta estado,
                                Dinero total, Dinero descuento, Dinero efectivo, Dinero transferencia, Dinero fiado,
                                Instant anuladaEn, UUID anuladaEnTurnoId, String motivoAnulacion) {

        static VentaDelTurno de(Venta v) {
            return new VentaDelTurno(v.getId(), v.getNumero(), v.getTurnoId(), v.getCobradaEn(), v.getEstado(),
                    v.getTotal(), v.getDescuentoMonto(), pagadoEn(v, FormaPago.EFECTIVO),
                    pagadoEn(v, FormaPago.TRANSFERENCIA), v.getFiado(), v.getAnuladaEn(), v.getAnuladaEnTurnoId(),
                    v.getMotivoAnulacion());
        }

        private static Dinero pagadoEn(Venta v, FormaPago forma) {
            return v.getPagos().stream().filter(p -> p.getForma() == forma).map(PagoVenta::getMonto)
                    .reduce(Dinero.CERO, Dinero::mas);
        }
    }

    /** @param enVivo el arqueo de un turno abierto; {@code null} si está cerrado */
    static DetalleTurno de(TurnoCaja t, ArqueoDeTurno enVivo, List<DetalleGasto> gastos, List<DetalleRetiro> retiros,
                           List<CompraDeCaja> compras, List<VentaDelTurno> ventas,
                           List<VentaDelTurno> anuladasDeOtrosTurnos, List<AbonoDelTurno> abonos,
                           Map<UUID, String> nombres) {
        return new DetalleTurno(t.getId(), t.getEstado(), t.getFondo(), t.getAbiertoEn(),
                Persona.de(t.getAbiertoPorId(), nombres), t.getCerradoEn(), Persona.de(t.getCerradoPorId(), nombres),
                Cierre.de(t), Arqueo.de(enVivo), t.getObservaciones(), gastos,
                retiros, compras, ventas, anuladasDeOtrosTurnos, abonos);
    }
}
