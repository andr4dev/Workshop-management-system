package com.workshopmanagement.rdmotors.clientes.aplicacion;

import java.util.Map;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.clientes.dominio.ClienteRepetidoException;
import com.workshopmanagement.rdmotors.clientes.dominio.DatosCliente;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioClientes;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;

/**
 * CASO DE USO — los datos de un cliente (spec 0008, RF-004 y decisión 5).
 *
 * <p>El cajero <b>completa</b> lo que falta —la cédula y el celular para poder fiarle—. Cambiar un dato que ya estaba
 * escrito es <b>corregirlo</b>, y eso es del administrador: la cédula de alguien que debe no la cambia cualquiera. La
 * corrección queda en la auditoría con el antes y el después.
 */
@Transactional
public class ActualizarCliente {

    public static final String CORREGIR_ES_DEL_ADMINISTRADOR =
            "No permitido: corregir un dato que ya está escrito es del administrador. Puedes completar los que faltan";

    private final RepositorioClientes clientes;
    private final RepositorioAuditoria auditoria;
    private final Reloj reloj;

    public ActualizarCliente(RepositorioClientes clientes, RepositorioAuditoria auditoria, Reloj reloj) {
        this.clientes = clientes;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public Cliente ejecutar(UUID clienteId, DatosCliente datos, Actor actor) {
        if (actor == null) {
            throw new ReglaDeNegocioException("Falta quién cambia los datos");
        }
        if (datos == null) {
            throw new ReglaDeNegocioException("Faltan los datos del cliente");
        }
        Cliente cliente = clientes.buscarParaModificar(clienteId)
                .orElseThrow(() -> new ReglaDeNegocioException("Ese cliente no existe"));
        boolean corrige = cliente.cambiaLoEscrito(datos);
        if (corrige && !actor.esAdministrador()) {
            throw new NoPermitidoException(CORREGIR_ES_DEL_ADMINISTRADOR);
        }
        // Antes de cambiar nada: la consulta no debe ver este cliente a medio cambiar.
        if (Cliente.normalizarDocumento(datos.documento()) != null) {
            clientes.buscarPorDocumento(datos.documento())
                    .filter(otro -> !otro.getId().equals(clienteId))
                    .ifPresent(otro -> {
                        throw new ClienteRepetidoException(otro.getId(), otro.getNombre());
                    });
        }
        Map<String, Object> antes = cliente.fotografia();
        cliente.actualizar(datos);
        if (corrige) {
            auditoria.registrar(EventoAuditoria.nuevo(reloj.ahora(), actor.id(), AccionAuditada.CORREGIR_CLIENTE,
                    Cliente.TIPO_AUDITORIA, clienteId, antes, cliente.fotografia(), null));
        }
        return clientes.guardar(cliente);
    }
}
