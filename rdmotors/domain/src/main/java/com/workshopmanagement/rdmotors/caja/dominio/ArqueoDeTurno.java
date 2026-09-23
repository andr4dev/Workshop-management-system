package com.workshopmanagement.rdmotors.caja.dominio;

import java.util.List;
import java.util.UUID;

import com.workshopmanagement.rdmotors.clientes.dominio.Abono;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.EstadoCompra;
import com.workshopmanagement.rdmotors.ventas.dominio.PagoVenta;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;

/**
 * Lo que debería haber en el cajón de un turno, con cada parte a la vista (spec 0006, RF-011):
 *
 * <pre>
 *   fondo
 * + efectivo de las ventas cobradas en el turno          (lo pagado en efectivo, no lo recibido)
 * + abonos de clientes recibidos en efectivo en el turno  (spec 0008, RF-013)
 * − efectivo devuelto por las ventas anuladas en el turno (sean de este turno o de uno anterior)
 * − gastos del turno pagados con plata del cajón, sin anular
 * − retiros del turno, sin anular
 * − compras pagadas con plata del cajón en el turno, vigentes
 * </pre>
 *
 * <p><b>Cada venta cuenta en dos turnos, y a cada uno le toca su mitad.</b> La cobrada suma su efectivo en
 * el turno en que se cobró, aunque después se anule. La anulación resta ese efectivo en el turno en que se
 * devolvió. Así una venta de ayer anulada hoy no cambia el cierre de ayer, que ya se firmó, y resta hoy,
 * que es cuando salen los billetes.
 *
 * <p>Las ventas por transferencia, los descuentos, <b>lo que quedó fiado</b> y los abonos por transferencia no tocan
 * el cajón: van aparte, para el comprobante.
 *
 * <p>Las reglas viven aquí y no en las consultas: los puertos solo traen las filas del turno, y qué suma y
 * qué resta se prueba sin base de datos. Por la misma razón {@link #calcular} vuelve a filtrar por el
 * turno: una fila de otro turno que se cuele no mueve el arqueo.
 */
public record ArqueoDeTurno(
        Dinero fondo,
        Dinero ventasEfectivo,
        Dinero ventasTransferencia,
        Dinero ventasFiado,
        Dinero descuentos,
        Dinero devolucionesEfectivo,
        Dinero abonosEfectivo,
        Dinero abonosTransferencia,
        Dinero gastosCajon,
        Dinero retiros,
        Dinero comprasCajon) {

    /**
     * @param ventasDelTurno    las cobradas en el turno, anuladas o no
     * @param anuladasEnElTurno las anuladas mientras el turno estaba abierto, sean de este turno o de otro
     * @param gastosDelTurno    los del cajón en el turno, anulados incluidos
     * @param retirosDelTurno   los del turno, anulados incluidos
     * @param comprasDeCaja     las pagadas con plata del cajón en el turno, anuladas incluidas
     * @param abonosDelTurno    los recibidos con este turno abierto, anulados incluidos (spec 0008)
     */
    public static ArqueoDeTurno calcular(TurnoCaja turno, List<Venta> ventasDelTurno, List<Venta> anuladasEnElTurno,
                                         List<Gasto> gastosDelTurno, List<Retiro> retirosDelTurno,
                                         List<Compra> comprasDeCaja, List<Abono> abonosDelTurno) {
        UUID id = turno.getId();

        Dinero ventasEfectivo = Dinero.CERO;
        Dinero ventasTransferencia = Dinero.CERO;
        Dinero ventasFiado = Dinero.CERO;
        Dinero descuentos = Dinero.CERO;
        for (Venta venta : ventasDelTurno) {
            if (!id.equals(venta.getTurnoId())) continue;
            ventasEfectivo = ventasEfectivo.mas(pagadoEn(venta, FormaPago.EFECTIVO));
            ventasTransferencia = ventasTransferencia.mas(pagadoEn(venta, FormaPago.TRANSFERENCIA));
            ventasFiado = ventasFiado.mas(venta.getFiado());
            descuentos = descuentos.mas(venta.getDescuentoMonto());
        }

        // Un abono anulado no entró: el arqueo lo deja fuera, como un gasto anulado.
        Dinero abonosEfectivo = Dinero.CERO;
        Dinero abonosTransferencia = Dinero.CERO;
        for (Abono abono : abonosDelTurno) {
            if (abono.estaAnulado() || !id.equals(abono.getTurnoId())) continue;
            if (abono.getForma() == FormaPago.EFECTIVO) {
                abonosEfectivo = abonosEfectivo.mas(abono.getMonto());
            } else {
                abonosTransferencia = abonosTransferencia.mas(abono.getMonto());
            }
        }

        // De una mixta anulada vuelve en efectivo solo su parte en efectivo: la de transferencia se
        // devuelve por transferencia y no sale del cajón.
        Dinero devoluciones = Dinero.CERO;
        for (Venta venta : anuladasEnElTurno) {
            if (venta.estaAnulada() && id.equals(venta.getAnuladaEnTurnoId())) {
                devoluciones = devoluciones.mas(pagadoEn(venta, FormaPago.EFECTIVO));
            }
        }

        Dinero gastos = Dinero.CERO;
        for (Gasto gasto : gastosDelTurno) {
            if (gasto.restaDelCajon() && id.equals(gasto.getTurnoId())) {
                gastos = gastos.mas(gasto.getMonto());
            }
        }

        Dinero retiros = Dinero.CERO;
        for (Retiro retiro : retirosDelTurno) {
            if (!retiro.estaAnulado() && id.equals(retiro.getTurnoId())) {
                retiros = retiros.mas(retiro.getMonto());
            }
        }

        Dinero compras = Dinero.CERO;
        for (Compra compra : comprasDeCaja) {
            if (compra.isPagadaDeCaja() && compra.getEstado() == EstadoCompra.VIGENTE && id.equals(compra.getTurnoId())) {
                compras = compras.mas(compra.getTotal());
            }
        }

        return new ArqueoDeTurno(turno.getFondo(), ventasEfectivo, ventasTransferencia, ventasFiado, descuentos,
                devoluciones, abonosEfectivo, abonosTransferencia, gastos, retiros, compras);
    }

    private static Dinero pagadoEn(Venta venta, FormaPago forma) {
        return venta.getPagos().stream()
                .filter(p -> p.getForma() == forma)
                .map(PagoVenta::getMonto)
                .reduce(Dinero.CERO, Dinero::mas);
    }

    /** Siempre sumando las partes: si una cambia, lo que debería haber cambia con ella. */
    public Dinero esperado() {
        return fondo.mas(ventasEfectivo).mas(abonosEfectivo)
                .menos(devolucionesEfectivo)
                .menos(gastosCajon)
                .menos(retiros)
                .menos(comprasCajon);
    }
}
