package com.workshopmanagement.rdmotors.ventas.aplicacion;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.clientes.dominio.Deuda;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioClientes;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioDeudas;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;
import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;
import com.workshopmanagement.rdmotors.ventas.dominio.puerto.RepositorioVentas;

/**
 * CASO DE USO — ver ventas (spec 0003, RF-024).
 *
 * <p>Lleva caso de uso aunque solo lea, por la misma regla que el historial de compras: <b>mapea</b>
 * la venta a su detalle, y eso lee relaciones (renglones, repuestos, pagos) que tienen que cargarse
 * dentro de la transacción.
 *
 * <p>Cada venta sale con el nombre de quien la cobró (spec 0004, RF-023). Los nombres se piden una vez por
 * respuesta, no una por venta: las ventas de un turno largo son muchas.
 */
@Transactional(readOnly = true)
public class ConsultarVentas {

    private final RepositorioVentas ventas;
    private final RepositorioTurnos turnos;
    private final RepositorioUsuarios usuarios;
    private final RepositorioClientes clientes;
    private final RepositorioDeudas deudas;

    public ConsultarVentas(RepositorioVentas ventas, RepositorioTurnos turnos, RepositorioUsuarios usuarios,
                           RepositorioClientes clientes, RepositorioDeudas deudas) {
        this.ventas = ventas;
        this.turnos = turnos;
        this.usuarios = usuarios;
        this.clientes = clientes;
        this.deudas = deudas;
    }

    public Optional<DetalleVenta> detalle(UUID id, Actor actor) {
        return ventas.buscar(id).map(venta -> detalleDe(venta, actor));
    }

    public Optional<DetalleVenta> porNumero(long numero, Actor actor) {
        return ventas.buscarPorNumero(numero).map(venta -> detalleDe(venta, actor));
    }

    /** La que quedó guardada con esa llave: lo que se responde a un cobro repetido. */
    public Optional<DetalleVenta> porLlave(UUID llave, Actor actor) {
        return ventas.buscarPorLlave(llave).map(venta -> detalleDe(venta, actor));
    }

    /**
     * Las del turno abierto, de la más reciente a la más antigua. Sin turno abierto, ninguna; y un cajero tampoco ve
     * las de un turno ajeno (spec 0004, §5).
     */
    public List<DetalleVenta> delTurnoAbierto(Actor actor) {
        return turnos.abierto().filter(turno -> turno.loPuedeVer(actor))
                .map(turno -> detallesDe(ventas.delTurno(turno.getId()))).orElse(List.of());
    }

    /**
     * Las compras de un cliente (spec 0008, RF-023): fiadas o de contado, de la más reciente a la más antigua.
     *
     * <p>No se filtran por turno: es el historial del cliente, no el de un cajero, y no trae costos.
     */
    public Pagina<DetalleVenta> delCliente(UUID clienteId, int pagina, int tamano) {
        Pagina<Venta> encontradas = ventas.delCliente(clienteId, Math.max(pagina, 0), Math.clamp(tamano, 1, 100));
        List<DetalleVenta> detalles = detallesDe(encontradas.elementos());
        return new Pagina<>(detalles, encontradas.total(), encontradas.numero(), encontradas.tamano());
    }

    /** Una venta suelta: el administrador, cualquiera; el cajero, una de un turno suyo. */
    private DetalleVenta detalleDe(Venta venta, Actor actor) {
        boolean deUnTurnoSuyo = turnos.buscar(venta.getTurnoId()).map(t -> t.loPuedeVer(actor)).orElse(false);
        if (!deUnTurnoSuyo) {
            throw new NoPermitidoException("No permitido: esa venta es de un turno de otra persona");
        }
        return detallesDe(List.of(venta)).getFirst();
    }

    /** Los nombres, los clientes y las deudas de toda la lista, con una consulta de cada uno. */
    private List<DetalleVenta> detallesDe(List<Venta> lista) {
        Map<UUID, String> nombres = usuarios.nombresDe(lista.stream()
                .flatMap(v -> Stream.of(v.getVendidoPorId(), v.getAnuladaPorId()))
                .filter(Objects::nonNull)
                .toList());
        List<UUID> clienteIds = lista.stream().map(Venta::getClienteId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, Cliente> porCliente = clienteIds.isEmpty() ? Map.of()
                : clientes.deIds(clienteIds).stream().collect(Collectors.toMap(Cliente::getId, c -> c));
        List<UUID> fiadas = lista.stream().filter(Venta::tieneFiado).map(Venta::getId).toList();
        Map<UUID, Deuda> porVenta = fiadas.isEmpty() ? Map.of()
                : deudas.deLasVentas(fiadas).stream().collect(Collectors.toMap(Deuda::getVentaId, d -> d));
        return lista.stream().map(v -> DetalleVenta.de(v, nombres, porCliente, porVenta)).toList();
    }
}
