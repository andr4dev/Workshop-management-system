package com.workshopmanagement.rdmotors.caja.dominio.puerto;

import java.util.Optional;
import java.util.UUID;

import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;

/**
 * PUERTO — los turnos de caja.
 *
 * <h2>El turno pone en fila la plata y el cierre (spec 0006, RF-017)</h2>
 *
 * Todo lo que mueve plata de un turno —cobrar, anular una venta, un gasto del cajón, un retiro, una compra
 * de caja— pide el turno con {@link #abiertoParaMover()} <b>antes que cualquier otro bloqueo</b>. Cerrar lo
 * pide con {@link #buscarParaCerrar}. Varios movimientos pasan a la vez; el cierre espera a que terminen, y
 * los que llegan mientras cierra esperan al cierre y encuentran el turno cerrado. Así nada queda en un
 * turno cerrado sin contar en su arqueo.
 */
public interface RepositorioTurnos {

    /** El turno abierto, si lo hay, sin bloquearlo. Nunca hay más de uno: la base lo garantiza. */
    Optional<TurnoCaja> abierto();

    /**
     * El turno abierto, para meterle plata o sacársela: se comparte con otros movimientos y excluye al
     * cierre hasta el final de la transacción. Si un cierre estaba en curso, espera y no lo encuentra.
     */
    Optional<TurnoCaja> abiertoParaMover();

    /** Con intención de cerrarlo o de escribirle las observaciones: espera a los movimientos en curso. */
    Optional<TurnoCaja> buscarParaCerrar(UUID id);

    Optional<TurnoCaja> buscar(UUID id);

    /** Los cerrados, del cierre más reciente al más antiguo. */
    Pagina<TurnoCaja> cerrados(int pagina, int tamano);

    /** Los cerrados que abrió esa persona, en el mismo orden: lo que ve un cajero (spec 0004, §5). */
    Pagina<TurnoCaja> cerradosDe(UUID abiertoPorId, int pagina, int tamano);

    /**
     * Si al guardar la base encuentra otro turno abierto (dos aperturas a la vez), lanza
     * {@link com.workshopmanagement.rdmotors.caja.dominio.TurnoYaAbiertoException}.
     */
    TurnoCaja guardar(TurnoCaja turno);
}
