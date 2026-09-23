package com.workshopmanagement.rdmotors.compartido.aplicacion;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.Persona;

/**
 * Un cambio de la auditoría como se muestra (spec 0004, RF-022): con el <b>nombre</b> de quien lo hizo, no su id.
 * Es lo que se lee en el rastro de correcciones de una compra o de la ficha de un repuesto.
 */
public record CambioAuditado(AccionAuditada accion, Instant ocurridoEn, Persona quien, String motivo,
                             Map<String, Object> antes, Map<String, Object> despues) {

    public static CambioAuditado de(EventoAuditoria evento, Map<UUID, String> nombres) {
        return new CambioAuditado(evento.accion(), evento.ocurridoEn(), Persona.de(evento.usuarioId(), nombres),
                evento.motivo(), evento.antes(), evento.despues());
    }
}
