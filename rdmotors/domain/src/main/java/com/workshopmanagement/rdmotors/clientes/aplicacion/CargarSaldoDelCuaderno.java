package com.workshopmanagement.rdmotors.clientes.aplicacion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.clientes.dominio.CarteraDelCliente;
import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.clientes.dominio.Deuda;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioAbonos;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioClientes;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioDeudas;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;

/**
 * CASO DE USO — lo que el cliente ya debía en el cuaderno antes del sistema (spec 0008, H10 y RF-028).
 *
 * <p>Sin esto, el primer día el sistema diría que nadie debe nada. Se carga <b>una sola vez por cliente</b>, con la
 * fecha desde la que lo debe —así se paga primero, por ser lo más viejo— y su motivo. Es del administrador y queda en
 * la auditoría: es una deuda que nadie puede cruzar con una venta.
 */
@Transactional
public class CargarSaldoDelCuaderno {

    private final RepositorioClientes clientes;
    private final RepositorioDeudas deudas;
    private final RepositorioAbonos abonos;
    private final RepositorioAuditoria auditoria;
    private final Reloj reloj;

    public CargarSaldoDelCuaderno(RepositorioClientes clientes, RepositorioDeudas deudas, RepositorioAbonos abonos,
                                  RepositorioAuditoria auditoria, Reloj reloj) {
        this.clientes = clientes;
        this.deudas = deudas;
        this.abonos = abonos;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /** @param fecha desde cuándo lo debe, según el cuaderno; no puede ser después de hoy */
    public Deuda ejecutar(UUID clienteId, Dinero monto, LocalDate fecha, String motivo, Actor actor) {
        if (actor == null) {
            throw new ReglaDeNegocioException("Falta quién carga el saldo");
        }
        actor.exigirAdministrador();
        Cliente cliente = clientes.buscarParaModificar(clienteId)
                .orElseThrow(() -> new ReglaDeNegocioException("Ese cliente no existe"));
        Instant ahora = reloj.ahora();
        Deuda saldo = Deuda.delCuaderno(cliente.getId(), fecha, monto, motivo, actor.id(), ahora, reloj.hoy());
        CarteraDelCliente cartera = new CarteraDelCliente(cliente, deudas.delCliente(clienteId),
                abonos.delCliente(clienteId));
        cartera.registrarDeuda(saldo, ahora);

        auditoria.registrar(EventoAuditoria.nuevo(ahora, actor.id(), AccionAuditada.CARGAR_SALDO_CUADERNO,
                Cliente.TIPO_AUDITORIA, clienteId, null,
                java.util.Map.of("monto", monto.valor().longValueExact(), "fecha", fecha.toString()), motivo));
        Deuda guardada = deudas.guardar(saldo);
        cartera.abonos().forEach(abonos::guardar);
        return guardada;
    }
}
