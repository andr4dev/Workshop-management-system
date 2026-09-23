package com.workshopmanagement.rdmotors.usuarios.aplicacion;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;

/** CASO DE USO — la lista de Usuarios del administrador (spec 0004, RF-024): rol, si está activo y su última entrada. */
@Transactional(readOnly = true)
public class ConsultarUsuarios {

    private final RepositorioUsuarios usuarios;

    public ConsultarUsuarios(RepositorioUsuarios usuarios) {
        this.usuarios = usuarios;
    }

    public List<FichaUsuario> todos(Actor actor) {
        actor.exigirAdministrador();
        return usuarios.todos().stream().map(FichaUsuario::de).toList();
    }
}
