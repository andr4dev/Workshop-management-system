package com.workshopmanagement.rdmotors.caja.aplicacion;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.caja.dominio.Gasto;
import com.workshopmanagement.rdmotors.caja.dominio.Retiro;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioGastos;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioRetiros;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.clientes.dominio.Abono;
import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioAbonos;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioClientes;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;
import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCompras;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;
import com.workshopmanagement.rdmotors.ventas.dominio.puerto.RepositorioVentas;

/**
 * CASO DE USO — ver un turno y los turnos cerrados (spec 0006, RF-020 y RF-023).
 *
 * <p>El mismo detalle sirve para el turno abierto (la sección Caja, con lo que debería haber en vivo) y para uno
 * cerrado (su historial y su comprobante, con las cifras que se guardaron).
 */
@Transactional(readOnly = true)
public class ConsultarTurnos {

    private final RepositorioTurnos turnos;
    private final RepositorioGastos gastos;
    private final RepositorioRetiros retiros;
    private final RepositorioCompras compras;
    private final RepositorioVentas ventas;
    private final CalcularArqueo arqueo;
    private final RepositorioUsuarios usuarios;
    private final RepositorioAbonos abonos;
    private final RepositorioClientes clientes;

    public ConsultarTurnos(RepositorioTurnos turnos, RepositorioGastos gastos, RepositorioRetiros retiros,
                           RepositorioCompras compras, RepositorioVentas ventas, CalcularArqueo arqueo,
                           RepositorioUsuarios usuarios, RepositorioAbonos abonos, RepositorioClientes clientes) {
        this.turnos = turnos;
        this.gastos = gastos;
        this.retiros = retiros;
        this.compras = compras;
        this.ventas = ventas;
        this.arqueo = arqueo;
        this.usuarios = usuarios;
        this.abonos = abonos;
        this.clientes = clientes;
    }

    /** El administrador ve todos; el cajero, los que abrió él (spec 0004, §5). */
    public Pagina<TurnoCaja> cerrados(int pagina, int tamano, Actor actor) {
        return actor.esAdministrador() ? turnos.cerrados(pagina, tamano)
                : turnos.cerradosDe(actor.id(), pagina, tamano);
    }

    public Optional<DetalleTurno> detalle(UUID turnoId, Actor actor) {
        return turnos.buscar(turnoId).map(turno -> {
            if (!turno.loPuedeVer(actor)) {
                throw new NoPermitidoException("No permitido: ese turno es de otra persona");
            }
            return detalleDe(turno);
        });
    }

    /**
     * El detalle de un turno para el correo del cierre (spec 0010): lo lee el sistema, no una persona, así que no hay a
     * quién pedirle permiso. Solo se usa para armar el resumen que el administrador ya decidió a quién le llega.
     */
    public Optional<DetalleTurno> detalleDelCierre(UUID turnoId) {
        return turnos.buscar(turnoId).map(this::detalleDe);
    }

    private DetalleTurno detalleDe(TurnoCaja turno) {
        UUID id = turno.getId();
        List<Gasto> gastosDelCajon = gastos.delCajonEnTurno(id);
        List<Retiro> retirosDelTurno = retiros.delTurno(id);
        List<Abono> abonosDelTurno = abonos.delTurno(id);
        Map<UUID, String> deQuien = clientes.deIds(abonosDelTurno.stream().map(Abono::getClienteId).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(Cliente::getId, Cliente::getNombre));
        // Los nombres de todos los que aparecen en el turno, en una sola consulta (spec 0004, RF-022).
        Map<UUID, String> nombres = usuarios.nombresDe(Stream.of(
                        Stream.of(turno.getAbiertoPorId(), turno.getCerradoPorId()),
                        gastosDelCajon.stream().flatMap(g -> Stream.of(g.getRegistradoPorId(), g.getAnuladoPorId())),
                        retirosDelTurno.stream().flatMap(r -> Stream.of(r.getRegistradoPorId(), r.getAnuladoPorId())),
                        abonosDelTurno.stream().flatMap(a -> Stream.of(a.getRecibidoPorId(), a.getAnuladoPorId())))
                .flatMap(s -> s).filter(Objects::nonNull).toList());
        return DetalleTurno.de(turno, turno.estaAbierto() ? arqueo.de(turno) : null,
                gastosDelCajon.stream().map(g -> DetalleGasto.de(g, nombres)).toList(),
                retirosDelTurno.stream().map(r -> DetalleTurno.DetalleRetiro.de(r, nombres)).toList(),
                compras.deCajaEnTurno(id).stream().map(DetalleTurno.CompraDeCaja::de).toList(),
                ventas.delTurno(id).stream().map(DetalleTurno.VentaDelTurno::de).toList(),
                ventas.anuladasEnTurno(id).stream()
                        .filter(v -> !id.equals(v.getTurnoId()))
                        .map(DetalleTurno.VentaDelTurno::de)
                        .toList(),
                abonosDelTurno.stream()
                        .map(a -> DetalleTurno.AbonoDelTurno.de(a, deQuien.getOrDefault(a.getClienteId(), "—"), nombres))
                        .toList(),
                nombres);
    }
}
