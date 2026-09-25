package com.workshopmanagement.rdmotors.carga.dominio;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.TextoDeBusqueda;

/**
 * Las categorías activas, para encontrarlas por nombre —el que trae el Excel o el que propone la descripción— y para
 * saber si la que tiene un renglón sigue viva.
 *
 * <p>Por nombre y sin tildes: "Eléctrico" en el Excel es la categoría ELECTRICO. Si la categoría fue renombrada o
 * desactivada, no se encuentra, y el renglón queda sin categoría: mejor vacío que equivocado.
 */
public final class CategoriasConocidas {

    private final Map<UUID, String> nombres;
    private final Map<String, UUID> porNombre = new HashMap<>();

    /** @param activas id y nombre de cada categoría activa */
    public CategoriasConocidas(Map<UUID, String> activas) {
        this.nombres = new LinkedHashMap<>(activas);
        activas.forEach((id, nombre) -> porNombre.put(clave(nombre), id));
    }

    public Optional<UUID> porNombre(String nombre) {
        return nombre == null || nombre.isBlank() ? Optional.empty() : Optional.ofNullable(porNombre.get(clave(nombre)));
    }

    public boolean activa(UUID id) {
        return id != null && nombres.containsKey(id);
    }

    public String nombre(UUID id) {
        return id == null ? null : nombres.get(id);
    }

    private static String clave(String nombre) {
        return TextoDeBusqueda.normalizar(nombre.strip().replaceAll("\\s+", " "));
    }
}
