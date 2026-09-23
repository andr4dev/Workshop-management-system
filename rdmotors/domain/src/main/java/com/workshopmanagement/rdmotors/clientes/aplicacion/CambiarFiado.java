package com.workshopmanagement.rdmotors.clientes.aplicacion;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioClientes;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;

/**
 * CASO DE USO — cerrarle o abrirle el fiado a un cliente (spec 0008, H11 y RF-005). Sin cupo, es la manera de frenar
 * a quien no paga: sigue comprando de contado y abonando, pero no se le fía más.
 *
 * <p>Del administrador, y queda en la auditoría: quién, cuándo y, al cerrarlo, por qué.
 */
@Transactional
public class CambiarFiado {

    private final RepositorioClientes clientes;
    private final RepositorioAuditoria auditoria;
    private final Reloj reloj;

    public CambiarFiado(RepositorioClientes clientes, RepositorioAuditoria auditoria, Reloj reloj) {
        this.clientes = clientes;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public Cliente cerrar(UUID clienteId, String motivo, Actor actor) {
        return cambiar(clienteId, actor, AccionAuditada.CERRAR_FIADO, motivo, cliente -> cliente.cerrarFiado(motivo));
    }

    public Cliente abrir(UUID clienteId, Actor actor) {
        return cambiar(clienteId, actor, AccionAuditada.ABRIR_FIADO, null, Cliente::abrirFiado);
    }

    private Cliente cambiar(UUID clienteId, Actor actor, AccionAuditada accion, String motivo,
                            java.util.function.Consumer<Cliente> cambio) {
        if (actor == null) {
            throw new ReglaDeNegocioException("Falta quién lo cambia");
        }
        actor.exigirAdministrador();
        Cliente cliente = clientes.buscarParaModificar(clienteId)
                .orElseThrow(() -> new ReglaDeNegocioException("Ese cliente no existe"));
        Map<String, Object> antes = cliente.fotografia();
        cambio.accept(cliente);
        Instant ahora = reloj.ahora();
        auditoria.registrar(EventoAuditoria.nuevo(ahora, actor.id(), accion, Cliente.TIPO_AUDITORIA, clienteId, antes,
                cliente.fotografia(), cliente.getMotivoFiadoCerrado() == null ? motivo : cliente.getMotivoFiadoCerrado()));
        return clientes.guardar(cliente);
    }
}
