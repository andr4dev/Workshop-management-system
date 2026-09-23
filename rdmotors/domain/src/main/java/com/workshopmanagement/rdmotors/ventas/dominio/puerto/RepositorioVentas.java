package com.workshopmanagement.rdmotors.ventas.dominio.puerto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;

import com.workshopmanagement.rdmotors.ventas.dominio.Venta;

/** PUERTO — las ventas y su numeración. */
public interface RepositorioVentas {

    /**
     * El siguiente número de comprobante, <b>dentro de la transacción del cobro</b>.
     *
     * <p>Si el cobro falla y la transacción se deshace, el número vuelve a estar libre: la serie no
     * puede tener huecos, porque un hueco se lee como una venta borrada. Mientras la transacción no
     * termina, ningún otro cobro puede pedir número.
     */
    long siguienteNumero();

    /**
     * Si la base ya tiene una venta con la misma llave (dos cobros a la vez), lanza
     * {@link com.workshopmanagement.rdmotors.ventas.dominio.VentaRepetidaException}.
     */
    Venta guardar(Venta venta);

    Optional<Venta> buscar(UUID id);

    Optional<Venta> buscarPorLlave(UUID llave);

    Optional<Venta> buscarPorNumero(long numero);

    /** Con intención de modificarla (anular): el adaptador toma bloqueo de fila. */
    Optional<Venta> buscarParaModificar(UUID id);

    /** Las cobradas en el turno, anuladas o no, de la más reciente a la más antigua. */
    List<Venta> delTurno(UUID turnoId);

    /** Las de ese cliente, fiadas o de contado, de la más reciente a la más antigua (spec 0008, RF-023). */
    Pagina<Venta> delCliente(UUID clienteId, int pagina, int tamano);

    /**
     * Las anuladas mientras ese turno estaba abierto, sean de ese turno o de uno anterior: su efectivo salió
     * del cajón de ese turno (spec 0006, RF-011).
     */
    List<Venta> anuladasEnTurno(UUID turnoId);
}
