package com.workshopmanagement.rdmotors.compartido.dominio;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Una acción sensible, con quién, cuándo, por qué, y el estado a cada lado del cambio.
 *
 * <p><b>No es una entidad JPA a propósito.</b> {@code antes} y {@code despues} son mapas de valores
 * simples —texto, números, listas— que el adaptador guarda como JSON. El dominio no sabe de JSON:
 * lo arma como datos y la infraestructura decide cómo escribirlo.
 *
 * <p>Límite declarado del modelo de datos: el JSON es para que lo lea una persona, no para hacer
 * reportes. Los reportes siguen leyendo las columnas concretas de compras y kardex.
 *
 * <p><b>Hasta la rebanada 2, {@code usuarioId} es el usuario provisional</b> que manda el navegador.
 * Hasta que exista el login, "quién" no prueba nada.
 *
 * @param motivo obligatorio en las acciones sobre compras; puede faltar al corregir una ficha
 */
public record EventoAuditoria(
        UUID id,
        Instant ocurridoEn,
        UUID usuarioId,
        AccionAuditada accion,
        String entidadTipo,
        UUID entidadId,
        Map<String, Object> antes,
        Map<String, Object> despues,
        String motivo) {

    public EventoAuditoria {
        if (id == null || ocurridoEn == null || usuarioId == null || accion == null
                || entidadTipo == null || entidadId == null) {
            throw new IllegalArgumentException("Un evento de auditoría necesita quién, cuándo, qué y sobre qué");
        }
        // Copias que aceptan nulos: "la cuenta era null" es un dato que hay que poder guardar.
        antes = antes == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(antes));
        despues = despues == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(despues));
    }

    public static EventoAuditoria nuevo(Instant ocurridoEn, UUID usuarioId, AccionAuditada accion,
                                        String entidadTipo, UUID entidadId,
                                        Map<String, Object> antes, Map<String, Object> despues,
                                        String motivo) {
        return new EventoAuditoria(UUID.randomUUID(), ocurridoEn, usuarioId, accion, entidadTipo,
                entidadId, antes, despues, motivo);
    }
}
