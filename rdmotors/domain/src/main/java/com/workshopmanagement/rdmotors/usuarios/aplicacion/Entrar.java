package com.workshopmanagement.rdmotors.usuarios.aplicacion;

import java.time.Instant;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.usuarios.dominio.CredencialesInvalidasException;
import com.workshopmanagement.rdmotors.usuarios.dominio.DatosDelEquipo;
import com.workshopmanagement.rdmotors.usuarios.dominio.Entrada;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;
import com.workshopmanagement.rdmotors.usuarios.dominio.UsuarioBloqueadoException;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.Contrasenas;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioEntradas;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;

/**
 * CASO DE USO — entrar con usuario y contraseña (spec 0004, H1, RF-001 a RF-003).
 *
 * <p><b>El mismo error</b> para un usuario que no existe, una contraseña mal y un usuario desactivado: no le dice a
 * nadie qué usuarios existen. Y para que tampoco se note por lo que tarda, un usuario que no existe se compara contra
 * un hash de mentira: calcular el hash es lo lento, y se calcula siempre.
 *
 * <p>Los intentos fallidos se guardan <b>aunque la respuesta sea un error</b>: por eso esas dos excepciones no
 * deshacen la transacción.
 */
@Transactional(noRollbackFor = {CredencialesInvalidasException.class, UsuarioBloqueadoException.class})
public class Entrar {

    private final RepositorioUsuarios usuarios;
    private final Contrasenas contrasenas;
    private final RepositorioEntradas entradas;
    private final Reloj reloj;
    private final String hashDeMentira;

    public Entrar(RepositorioUsuarios usuarios, Contrasenas contrasenas, RepositorioEntradas entradas, Reloj reloj) {
        this.usuarios = usuarios;
        this.contrasenas = contrasenas;
        this.entradas = entradas;
        this.reloj = reloj;
        this.hashDeMentira = contrasenas.hash("rdmotors-nadie-tiene-esta-contrasena");
    }

    public Sesion ejecutar(String usuario, String contrasena) {
        return ejecutar(usuario, contrasena, DatosDelEquipo.NINGUNO);
    }

    /**
     * @param equipo desde dónde se intentó: queda en el registro de entradas (RF-025), entre o no. Como el intento
     *               fallido tampoco se deshace ({@code noRollbackFor}), el registro queda igual
     */
    public Sesion ejecutar(String usuario, String contrasena, DatosDelEquipo equipo) {
        Instant ahora = reloj.ahora();
        Optional<Usuario> encontrado = usuario == null ? Optional.empty()
                : usuarios.buscarPorUsuarioParaModificar(usuario);
        if (encontrado.isEmpty()) {
            contrasenas.coincide(contrasena == null ? "" : contrasena, hashDeMentira);
            entradas.registrar(Entrada.fallida(null, usuario == null ? "" : usuario, ahora, equipo));
            throw new CredencialesInvalidasException();
        }
        Usuario quien = encontrado.get();
        if (quien.estaBloqueado(ahora)) {
            entradas.registrar(Entrada.fallida(quien.getId(), usuario, ahora, equipo));
            throw new UsuarioBloqueadoException();
        }
        boolean coincide = contrasena != null && contrasenas.coincide(contrasena, quien.getHash());
        if (!coincide || !quien.isActivo()) {
            if (quien.isActivo()) {
                quien.registrarIntentoFallido(ahora);
                usuarios.guardar(quien);
            }
            entradas.registrar(Entrada.fallida(quien.getId(), usuario, ahora, equipo));
            throw new CredencialesInvalidasException();
        }
        quien.entroBien(ahora);
        usuarios.guardar(quien);
        entradas.registrar(Entrada.exitosa(quien.getId(), usuario, ahora, equipo));
        return Sesion.de(quien);
    }
}
