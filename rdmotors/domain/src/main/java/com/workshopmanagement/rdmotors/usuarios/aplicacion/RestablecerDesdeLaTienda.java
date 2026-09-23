package com.workshopmanagement.rdmotors.usuarios.aplicacion;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.Rol;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;
import com.workshopmanagement.rdmotors.usuarios.dominio.ContrasenaTemporal;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.Contrasenas;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;

/**
 * CASO DE USO — el único administrador olvidó su contraseña (spec 0004, RF-019). No hay "olvidé mi contraseña" por
 * correo: el sistema funciona sin internet. Se corre en el computador de la tienda, al arrancar el servidor con
 * {@code --rdmotors.restablecer-administrador=<usuario>}: quien tiene ese computador tiene la tienda.
 *
 * <p>Queda como inicial, con sus sesiones cerradas, y activo aunque alguien lo hubiera desactivado por la base. En
 * la auditoría queda a su propio nombre: lo hizo él, en el computador de la tienda.
 */
@Transactional
public class RestablecerDesdeLaTienda {

    public static final String MOTIVO = "Desde el computador de la tienda";

    private final RepositorioUsuarios usuarios;
    private final Contrasenas contrasenas;
    private final RepositorioAuditoria auditoria;
    private final Reloj reloj;

    public RestablecerDesdeLaTienda(RepositorioUsuarios usuarios, Contrasenas contrasenas,
                                    RepositorioAuditoria auditoria, Reloj reloj) {
        this.usuarios = usuarios;
        this.contrasenas = contrasenas;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /** @return la contraseña temporal, para mostrarla en la consola */
    public String ejecutar(String usuario) {
        Usuario administrador = usuarios.buscarPorUsuarioParaModificar(usuario)
                .orElseThrow(() -> new ReglaDeNegocioException("No existe el usuario «" + usuario + "»"));
        if (administrador.getRol() != Rol.ADMINISTRADOR) {
            throw new ReglaDeNegocioException("«" + administrador.getUsuario() + "» no es administrador: "
                    + "a un cajero se la restablece un administrador, desde Usuarios");
        }
        String temporal = ContrasenaTemporal.nueva();
        administrador.restablecerContrasena(contrasenas.hash(temporal));
        administrador.activar();
        usuarios.guardar(administrador);
        auditoria.registrar(EventoAuditoria.nuevo(reloj.ahora(), administrador.getId(),
                AccionAuditada.RESTABLECER_CONTRASENA, Usuario.TIPO_AUDITORIA, administrador.getId(), null,
                administrador.fotografia(), MOTIVO));
        return temporal;
    }
}
