package com.workshopmanagement.rdmotors.compartido;

import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Rol;

/**
 * Quién hace las cosas en las pruebas del dominio (spec 0004). Cada llamada es una persona distinta: dos cajeros de
 * prueba no son el mismo aunque se llamen igual.
 */
public final class ActoresDePrueba {

    private ActoresDePrueba() {
    }

    public static Actor cajero() {
        return cajero("Carolina");
    }

    public static Actor cajero(String nombre) {
        return new Actor(UUID.randomUUID(), nombre, Rol.CAJERO);
    }

    public static Actor administrador() {
        return new Actor(UUID.randomUUID(), "Rubén", Rol.ADMINISTRADOR);
    }
}
