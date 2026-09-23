package com.workshopmanagement.rdmotors.usuarios.dominio.puerto;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;

/** PUERTO — las personas que entran al sistema (spec 0004). */
public interface RepositorioUsuarios {

    Optional<Usuario> buscar(UUID id);

    /** Sin mayúsculas ni tildes: "Carolina" encuentra a "carolina". */
    Optional<Usuario> buscarPorUsuario(String usuario);

    /**
     * Para cambiarlo: dos intentos de entrar a la vez con la contraseña mal cuentan como dos, no como uno.
     */
    Optional<Usuario> buscarPorUsuarioParaModificar(String usuario);

    Optional<Usuario> buscarParaModificar(UUID id);

    boolean hayUsuarios();

    /**
     * El candado del primer administrador: dos pantallas que lo crean a la vez no pueden dejar dos. Se suelta al
     * terminar la transacción.
     */
    void bloquearAltaDelPrimero();

    /**
     * El candado de los cambios que pueden quitar un administrador (pasarlo a cajero, desactivarlo): dos a la vez no
     * pueden dejar cero administradores activos (RF-016). Se suelta al terminar la transacción.
     */
    void bloquearCambiosDeAdministradores();

    long administradoresActivos();

    /** Todos, activos y desactivados, por nombre: la lista de Usuarios del administrador. */
    List<Usuario> todos();

    Usuario guardar(Usuario usuario);

    /**
     * El nombre de cada una de esas personas, en una sola consulta: lo que muestra "Atendió: Carolina". Una
     * respuesta con muchas ventas pregunta una vez, no una por venta.
     */
    Map<UUID, String> nombresDe(Collection<UUID> ids);
}
