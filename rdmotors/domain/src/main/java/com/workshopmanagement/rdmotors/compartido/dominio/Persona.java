package com.workshopmanagement.rdmotors.compartido.dominio;

import java.util.Map;
import java.util.UUID;

/**
 * Quién hizo algo, como se muestra: "Atendió: Carolina" (spec 0004). Solo el id y el nombre; el rol y la sesión son
 * del {@link Actor}, que es quien hace las cosas ahora.
 */
public record Persona(UUID id, String nombre) {

    /**
     * La persona de ese id, con su nombre si está en {@code nombres}. {@code null} si no hay id (una venta que nadie
     * ha anulado). Sin nombre no debería pasar: la base exige que cada "quién" sea un usuario (V17).
     */
    public static Persona de(UUID id, Map<UUID, String> nombres) {
        return id == null ? null : new Persona(id, nombres.getOrDefault(id, "—"));
    }
}
