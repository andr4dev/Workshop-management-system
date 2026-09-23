package com.workshopmanagement.rdmotors.compartido.dominio.puerto;

import java.util.List;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;

/**
 * PUERTO — el registro de auditoría. Solo se agrega y se lee: un evento nunca se edita ni se borra.
 *
 * <p>Merece puerto porque la forma de guardarlo es decisión de la infraestructura (JSON en Postgres)
 * y el dominio no puede importar la librería que lo escribe.
 */
public interface RepositorioAuditoria {

    void registrar(EventoAuditoria evento);

    /** Lo que se le hizo a una entidad, del evento más antiguo al más reciente. */
    List<EventoAuditoria> historialDe(String entidadTipo, UUID entidadId);
}
