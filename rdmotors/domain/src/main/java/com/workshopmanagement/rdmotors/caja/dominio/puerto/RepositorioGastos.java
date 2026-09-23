package com.workshopmanagement.rdmotors.caja.dominio.puerto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.workshopmanagement.rdmotors.caja.dominio.FiltroGastos;
import com.workshopmanagement.rdmotors.caja.dominio.Gasto;
import com.workshopmanagement.rdmotors.caja.dominio.TotalesGastos;
import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;

/** PUERTO — los gastos del negocio, del cajón y por fuera. */
public interface RepositorioGastos {

    /**
     * Si la base ya tiene un gasto con la misma llave (dos envíos a la vez), lanza
     * {@link com.workshopmanagement.rdmotors.caja.dominio.MovimientoRepetidoException}.
     */
    Gasto guardar(Gasto gasto);

    Optional<Gasto> buscar(UUID id);

    Optional<Gasto> buscarPorLlave(UUID llave);

    /** Con intención de anularlo: el adaptador toma bloqueo de fila. */
    Optional<Gasto> buscarParaModificar(UUID id);

    /** Del más reciente al más antiguo, con los anulados. */
    Pagina<Gasto> listar(FiltroGastos filtro, int pagina, int tamano);

    /** Los mismos filtros de la lista, sin los anulados. */
    TotalesGastos totales(FiltroGastos filtro);

    /** Los pagados con plata del cajón en ese turno, anulados incluidos, en el orden en que se registraron. */
    List<Gasto> delCajonEnTurno(UUID turnoId);
}
