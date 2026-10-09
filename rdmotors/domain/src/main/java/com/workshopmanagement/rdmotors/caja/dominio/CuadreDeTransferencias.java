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
 * Lo que debería haber en la cuenta de transferencias (el Daviplata de la tienda) en un turno, con cada parte a la
 * vista (2026-10-09). Es el gemelo de {@link ArqueoDeTurno} para la plata que no está en el cajón:
 *
 * <pre>
 *   saldo con que arrancó la cuenta
 * + ventas cobradas por transferencia en el turno
 * + abonos de clientes por transferencia en el turno
 * + efectivo que se pasó del cajón a la cuenta (retiros A_TRANSFERENCIAS)
 * − lo que se devolvió por transferencia de ventas anuladas en el turno
 * − compras pagadas por transferencia, registradas con el turno abierto, vigentes
 * − gastos pagados por transferencia, registrados con el turno abierto, sin anular
 * − retiros que salieron de la cuenta (DE_TRANSFERENCIAS)
 * </pre>
 *
 * <p>Una compra o un gasto por transferencia no tiene turno: cuenta en el turno en que se registró, que es cuando
 * se ve en la caja. Quien llama trae los de ese rato.
 *
 * @param saldoInicial {@code null} si el turno no anotó con cuánto arrancó la cuenta: entonces no hay esperado
 */
public record CuadreDeTransferencias(
        Dinero saldoInicial,
        Dinero ventas,
        Dinero abonos,
        Dinero desdeElCajon,
        Dinero devoluciones,
        Dinero compras,
        Dinero gastos,
        Dinero retiros) {

    /**
     * @param ventasDelTurno          las cobradas en el turno, anuladas o no
     * @param anuladasEnElTurno       las anuladas mientras el turno estaba abierto, sean de este turno o de otro
     * @param abonosDelTurno          los recibidos con este turno abierto, anulados incluidos
     * @param retirosDelTurno         los del turno, anulados incluidos
     * @param comprasPorTransferencia las pagadas por transferencia registradas con el turno abierto, anuladas incluidas
     * @param gastosPorTransferencia  los pagados por transferencia registrados con el turno abierto, anulados incluidos
     */
    public static CuadreDeTransferencias calcular(TurnoCaja turno, List<Venta> ventasDelTurno,
                                                  List<Venta> anuladasEnElTurno, List<Abono> abonosDelTurno,
                                                  List<Retiro> retirosDelTurno, List<Compra> comprasPorTransferencia,
                                                  List<Gasto> gastosPorTransferencia) {
        UUID id = turno.getId();
        Dinero ventas = Dinero.CERO;
        for (Venta venta : ventasDelTurno) {
            if (id.equals(venta.getTurnoId())) {
                ventas = ventas.mas(porTransferencia(venta));
            }
        }
        Dinero devoluciones = Dinero.CERO;
        for (Venta venta : anuladasEnElTurno) {
            if (venta.estaAnulada() && id.equals(venta.getAnuladaEnTurnoId())) {
                devoluciones = devoluciones.mas(porTransferencia(venta));
            }
        }
        Dinero abonos = Dinero.CERO;
        for (Abono abono : abonosDelTurno) {
            if (!abono.estaAnulado() && abono.getForma() == FormaPago.TRANSFERENCIA && id.equals(abono.getTurnoId())) {
                abonos = abonos.mas(abono.getMonto());
            }
        }
        Dinero desdeElCajon = Dinero.CERO;
        Dinero retiros = Dinero.CERO;
        for (Retiro retiro : retirosDelTurno) {
            if (retiro.estaAnulado() || !id.equals(retiro.getTurnoId())) continue;
            if (retiro.getTipo() == TipoRetiro.A_TRANSFERENCIAS) {
                desdeElCajon = desdeElCajon.mas(retiro.getMonto());
            } else if (retiro.getTipo() == TipoRetiro.DE_TRANSFERENCIAS) {
                retiros = retiros.mas(retiro.getMonto());
            }
        }
        Dinero compras = Dinero.CERO;
        for (Compra compra : comprasPorTransferencia) {
            if (compra.getFormaPago() == FormaPago.TRANSFERENCIA && compra.getEstado() == EstadoCompra.VIGENTE) {
                compras = compras.mas(compra.getTotal());
            }
        }
        Dinero gastos = Dinero.CERO;
        for (Gasto gasto : gastosPorTransferencia) {
            if (gasto.getFormaPago() == FormaPago.TRANSFERENCIA && !gasto.estaAnulado()) {
                gastos = gastos.mas(gasto.getMonto());
            }
        }
        return new CuadreDeTransferencias(turno.getSaldoTransferencias(), ventas, abonos, desdeElCajon, devoluciones,
                compras, gastos, retiros);
    }

    private static Dinero porTransferencia(Venta venta) {
        return venta.getPagos().stream()
                .filter(p -> p.getForma() == FormaPago.TRANSFERENCIA)
                .map(PagoVenta::getMonto)
                .reduce(Dinero.CERO, Dinero::mas);
    }

    /** Lo que entró a la cuenta en el turno. */
    public Dinero entradas() {
        return ventas.mas(abonos).mas(desdeElCajon);
    }

    /** Lo que salió de la cuenta en el turno. */
    public Dinero salidas() {
        return devoluciones.mas(compras).mas(gastos).mas(retiros);
    }

    /** Siempre sumando las partes. {@code null} si no se sabe con cuánto arrancó la cuenta. */
    public Dinero esperado() {
        return saldoInicial == null ? null : saldoInicial.mas(entradas()).menos(salidas());
    }
}
