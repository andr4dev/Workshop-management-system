package com.workshopmanagement.rdmotors.integracion;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Rol;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.Contrasenas;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;

/**
 * Las personas de las pruebas contra Postgres (spec 0004). Son usuarios de verdad, guardados en la base: desde la
 * V17 la base no acepta un "quién" que no sea un usuario.
 *
 * <p>Un administrador y dos cajeros, creados la primera vez que se piden y reusados en toda la clase (cada clase
 * tiene su propia base). Se trae con {@code @Import(UsuariosDePrueba.class)}; no lleva {@code @Component} para que
 * el escaneo de Spring no lo registre dos veces.
 */
public class UsuariosDePrueba {

    /** La de todos: las pruebas que entran por HTTP la necesitan. */
    public static final String CONTRASENA = "clave-de-prueba";

    private final RepositorioUsuarios usuarios;
    private final Contrasenas contrasenas;
    private Actor administrador;
    private Actor cajero;
    private Actor otroCajero;

    public UsuariosDePrueba(RepositorioUsuarios usuarios, Contrasenas contrasenas) {
        this.usuarios = usuarios;
        this.contrasenas = contrasenas;
    }

    public synchronized Actor administrador() {
        if (administrador == null) {
            administrador = crear("Rubén", Rol.ADMINISTRADOR).actor();
        }
        return administrador;
    }

    public synchronized Actor cajero() {
        if (cajero == null) {
            cajero = crear("Carolina", Rol.CAJERO).actor();
        }
        return cajero;
    }

    public synchronized Actor otroCajero() {
        if (otroCajero == null) {
            otroCajero = crear("Andrés", Rol.CAJERO).actor();
        }
        return otroCajero;
    }

    /** Uno nuevo cada vez, con {@link #CONTRASENA}. Su usuario lleva un sufijo al azar: no choca con otro. */
    public Usuario crear(String nombre, Rol rol) {
        String usuario = nombre.toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID().toString().substring(0, 6);
        return usuarios.guardar(Usuario.nuevo(usuario, nombre, rol, contrasenas.hash(CONTRASENA), false,
                Instant.now()));
    }
}
