package com.workshopmanagement.rdmotors.caja.aplicacion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.workshopmanagement.rdmotors.caja.dominio.ArqueoDeTurno;
import com.workshopmanagement.rdmotors.caja.dominio.CuadreDeTransferencias;
import com.workshopmanagement.rdmotors.caja.dominio.Gasto;
import com.workshopmanagement.rdmotors.caja.dominio.MasDeLoQueDeberiaHaberException;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioGastos;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioRetiros;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioAbonos;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCompras;
import com.workshopmanagement.rdmotors.ventas.dominio.puerto.RepositorioVentas;

/**
 * Lo que debería haber en el cajón de un turno (spec 0006, RF-011). Un solo sitio lo calcula: el cierre y la
 * confirmación de un gasto o retiro grande (decisión 3) no pueden tener cada uno su cuenta.
 *
 * <p>Trae las filas del turno de cada módulo y deja que {@link ArqueoDeTurno} decida qué suma y qué resta.
 *
 * <p><b>No abre transacción</b>: lo llaman casos de uso que la traen. El cierre y la confirmación lo calculan con
 * el turno bloqueado, y solo así la cifra vale hasta el final de su transacción; la sección Caja lo muestra en
 * vivo, como referencia (cambio del 2026-09-16: el arqueo dejó de ser a ciegas).
 */
public class CalcularArqueo {

    private final RepositorioVentas ventas;
    private final RepositorioGastos gastos;
    private final RepositorioRetiros retiros;
    private final RepositorioCompras compras;
    private final RepositorioAbonos abonos;

    public CalcularArqueo(RepositorioVentas ventas, RepositorioGastos gastos, RepositorioRetiros retiros,
                          RepositorioCompras compras, RepositorioAbonos abonos) {
        this.ventas = ventas;
        this.gastos = gastos;
        this.retiros = retiros;
        this.compras = compras;
        this.abonos = abonos;
    }

    public ArqueoDeTurno de(TurnoCaja turno) {
        UUID id = turno.getId();
        return ArqueoDeTurno.calcular(turno, ventas.delTurno(id), ventas.anuladasEnTurno(id),
                gastos.delCajonEnTurno(id), retiros.delTurno(id), compras.deCajaEnTurno(id), abonos.delTurno(id));
    }

    /**
     * Lo que debería haber en la cuenta de transferencias del turno (2026-10-09). Las compras y los gastos por
     * transferencia son los que se registraron mientras el turno estuvo abierto.
     */
    public CuadreDeTransferencias transferenciasDe(TurnoCaja turno) {
        UUID id = turno.getId();
        Instant hasta = turno.getCerradoEn();
        return CuadreDeTransferencias.calcular(turno, ventas.delTurno(id), ventas.anuladasEnTurno(id),
                abonos.delTurno(id), retiros.delTurno(id),
                compras.porTransferenciaEntre(turno.getAbiertoEn(), hasta),
                gastos.porTransferenciaEntre(turno.getAbiertoEn(), hasta));
    }

    /** Las compras por transferencia del turno, para ver de dónde salió la plata de la cuenta. */
    public List<Compra> comprasPorTransferenciaDe(TurnoCaja turno) {
        return compras.porTransferenciaEntre(turno.getAbiertoEn(), turno.getCerradoEn());
    }

    /** Los gastos por transferencia del turno, para ver de dónde salió la plata de la cuenta. */
    public List<Gasto> gastosPorTransferenciaDe(TurnoCaja turno) {
        return gastos.porTransferenciaEntre(turno.getAbiertoEn(), turno.getCerradoEn());
    }

    /**
     * Si el monto que va a salir del cajón es mayor que lo que debería haber, pide confirmar (decisión 3).
     * Avisa, no bloquea: con {@code confirmado} pasa.
     */
    public void exigirConfirmacionSiSupera(TurnoCaja turno, Dinero monto, boolean confirmado) {
        if (!confirmado && monto != null && monto.esMayorQue(de(turno).esperado())) {
            throw new MasDeLoQueDeberiaHaberException();
        }
    }
}
