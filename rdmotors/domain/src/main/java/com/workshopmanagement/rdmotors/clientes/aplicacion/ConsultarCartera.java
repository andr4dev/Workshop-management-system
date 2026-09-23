package com.workshopmanagement.rdmotors.clientes.aplicacion;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.clientes.dominio.Abono;
import com.workshopmanagement.rdmotors.clientes.dominio.CarteraDelCliente;
import com.workshopmanagement.rdmotors.clientes.dominio.FiltroCartera;
import com.workshopmanagement.rdmotors.clientes.dominio.ResumenDeCliente;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.ConsultasDeCartera;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioAbonos;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioClientes;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioDeudas;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;

/**
 * CASO DE USO — la Cartera (spec 0008, H3 y H5): quién debe, cuánto y desde cuándo, y la ficha de cada cliente.
 *
 * <p>La ven los dos roles (decisión 5): el cajero recibe los abonos y tiene que saber qué debe cada uno. No trae
 * costos.
 */
@Transactional(readOnly = true)
public class ConsultarCartera {

    private final ConsultasDeCartera consultas;
    private final RepositorioClientes clientes;
    private final RepositorioDeudas deudas;
    private final RepositorioAbonos abonos;
    private final RepositorioUsuarios usuarios;

    public ConsultarCartera(ConsultasDeCartera consultas, RepositorioClientes clientes, RepositorioDeudas deudas,
                            RepositorioAbonos abonos, RepositorioUsuarios usuarios) {
        this.consultas = consultas;
        this.clientes = clientes;
        this.deudas = deudas;
        this.abonos = abonos;
        this.usuarios = usuarios;
    }

    /**
     * La lista: los que deben, del que más al que menos; o el historial completo, del último movimiento al más viejo.
     *
     * @return con cuántos deben y el total por cobrar <b>de los que salen en la lista</b>
     */
    public Cartera lista(FiltroCartera filtro) {
        List<ResumenDeCliente> filas = consultas.resumen(filtro).stream()
                .sorted(filtro.vista() == FiltroCartera.Vista.DEBEN
                        ? ResumenDeCliente.DEL_QUE_MAS_DEBE : ResumenDeCliente.DEL_ULTIMO_MOVIMIENTO)
                .toList();
        List<ResumenDeCliente> queDeben = filas.stream().filter(ResumenDeCliente::debeAlgo).toList();
        return new Cartera(filas, queDeben.size(),
                queDeben.stream().map(ResumenDeCliente::debe).reduce(Dinero.CERO, Dinero::mas));
    }

    public Optional<FichaCliente> ficha(UUID clienteId) {
        return clientes.buscar(clienteId).map(cliente -> {
            CarteraDelCliente cartera = new CarteraDelCliente(cliente, deudas.delCliente(clienteId),
                    abonos.delCliente(clienteId));
            Map<UUID, String> nombres = usuarios.nombresDe(Stream.concat(
                            cartera.deudas().stream().flatMap(d -> Stream.of(d.getRegistradaPorId(), d.getAnuladaPorId())),
                            cartera.abonos().stream().flatMap(a -> Stream.of(a.getRecibidoPorId(), a.getAnuladoPorId())))
                    .filter(Objects::nonNull).distinct().toList());
            return FichaCliente.de(cartera, cartera.debe(), nombres);
        });
    }

    /** El recibo de un abono (RF-016): el cliente, el abono con a qué ventas fue, y lo que debe hoy. */
    public Optional<ReciboDeAbono> recibo(UUID abonoId) {
        return abonos.buscar(abonoId).flatMap(abono -> ficha(abono.getClienteId()).map(f -> new ReciboDeAbono(
                f.cliente(),
                f.abonos().stream().filter(a -> a.id().equals(abonoId)).findFirst().orElseThrow(),
                f.debe())));
    }

    /** El abono que quedó con esa llave: lo que se responde a un abono repetido. */
    public Optional<UUID> abonoDeLaLlave(UUID llave) {
        return abonos.buscarPorLlave(llave).map(Abono::getId);
    }

    /** La lista de la Cartera, con lo de arriba: cuántos deben y cuánto hay por cobrar. */
    public record Cartera(List<ResumenDeCliente> clientes, int deben, Dinero porCobrar) {
    }

    /** @param debeAhora lo que debe hoy, que puede no ser lo que debía justo después del abono */
    public record ReciboDeAbono(ClienteEncontrado cliente, FichaCliente.AbonoDeLaFicha abono, Dinero debeAhora) {
    }
}
