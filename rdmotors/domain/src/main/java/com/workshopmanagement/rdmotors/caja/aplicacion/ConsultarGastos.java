package com.workshopmanagement.rdmotors.caja.aplicacion;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.caja.dominio.FiltroGastos;
import com.workshopmanagement.rdmotors.caja.dominio.Gasto;
import com.workshopmanagement.rdmotors.caja.dominio.TotalesGastos;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioGastos;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;
import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;

/**
 * CASO DE USO — ver los gastos (spec 0006, RF-007a). Lleva caso de uso aunque solo lea: mapear un gasto lee su
 * categoría y su cuenta, que tienen que cargarse dentro de la transacción.
 */
@Transactional(readOnly = true)
public class ConsultarGastos {

    private final RepositorioGastos gastos;
    private final RepositorioTurnos turnos;
    private final RepositorioUsuarios usuarios;

    public ConsultarGastos(RepositorioGastos gastos, RepositorioTurnos turnos, RepositorioUsuarios usuarios) {
        this.gastos = gastos;
        this.turnos = turnos;
        this.usuarios = usuarios;
    }

    /** Los nombres de quienes registraron y anularon esos gastos, en una sola consulta (spec 0004, RF-022). */
    private Map<UUID, String> nombresDe(List<Gasto> lista) {
        return usuarios.nombresDe(lista.stream()
                .flatMap(g -> Stream.of(g.getRegistradoPorId(), g.getAnuladoPorId()))
                .filter(Objects::nonNull)
                .toList());
    }

    /** La lista y los totales son un reporte: del administrador (spec 0004, §5). */
    public Pagina<DetalleGasto> listar(FiltroGastos filtro, int pagina, int tamano, Actor actor) {
        actor.exigirAdministrador();
        Pagina<Gasto> pagina1 = gastos.listar(filtro, pagina, tamano);
        Map<UUID, String> nombres = nombresDe(pagina1.elementos());
        return pagina1.mapear(g -> DetalleGasto.de(g, nombres));
    }

    public TotalesGastos totales(FiltroGastos filtro, Actor actor) {
        actor.exigirAdministrador();
        return gastos.totales(filtro);
    }

    public Optional<DetalleGasto> detalle(UUID id, Actor actor) {
        return gastos.buscar(id).map(gasto -> visible(gasto, actor));
    }

    /** El que quedó guardado con esa llave: lo que se responde a un registro repetido. */
    public Optional<DetalleGasto> porLlave(UUID llave, Actor actor) {
        return gastos.buscarPorLlave(llave).map(gasto -> visible(gasto, actor));
    }

    /** Un gasto suelto: el administrador, cualquiera; el cajero, uno del cajón de un turno suyo. */
    private DetalleGasto visible(Gasto gasto, Actor actor) {
        boolean deUnTurnoSuyo = gasto.isDelCajon()
                && turnos.buscar(gasto.getTurnoId()).map(t -> t.loPuedeVer(actor)).orElse(false);
        if (!actor.esAdministrador() && !deUnTurnoSuyo) {
            throw new NoPermitidoException();
        }
        return DetalleGasto.de(gasto, nombresDe(List.of(gasto)));
    }
}
