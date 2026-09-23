package com.workshopmanagement.rdmotors.usuarios.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;
import com.workshopmanagement.rdmotors.compartido.dominio.Rol;
import com.workshopmanagement.rdmotors.usuarios.dominio.ContrasenaTemporal;
import com.workshopmanagement.rdmotors.usuarios.dominio.CredencialesInvalidasException;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;

/** Administrar usuarios (spec 0004, fase 4): crear, cambiar el rol, desactivar, activar y restablecer. */
class AdministrarUsuariosTest {

    private final Falsos.UsuariosEnMemoria usuarios = new Falsos.UsuariosEnMemoria();
    private final Falsos.ContrasenasFalsas contrasenas = new Falsos.ContrasenasFalsas();
    private final Falsos.AuditoriaEnMemoria auditoria = new Falsos.AuditoriaEnMemoria();
    private final Falsos.RelojFijo reloj = new Falsos.RelojFijo("2026-09-20T13:00:00Z");

    private final CrearUsuario crear = new CrearUsuario(usuarios, contrasenas, auditoria, reloj);
    private final CambiarRol cambiarRol = new CambiarRol(usuarios, auditoria, reloj);
    private final DesactivarUsuario desactivar = new DesactivarUsuario(usuarios, auditoria, reloj);
    private final ActivarUsuario activar = new ActivarUsuario(usuarios, auditoria, reloj);
    private final RestablecerContrasena restablecer = new RestablecerContrasena(usuarios, contrasenas, auditoria, reloj);
    private final RestablecerDesdeLaTienda desdeLaTienda =
            new RestablecerDesdeLaTienda(usuarios, contrasenas, auditoria, reloj);
    private final ConsultarUsuarios consultar = new ConsultarUsuarios(usuarios);
    private final Falsos.EntradasEnMemoria entradas = new Falsos.EntradasEnMemoria();
    private final Entrar entrar = new Entrar(usuarios, contrasenas, entradas, reloj);

    private Usuario sembrar(String usuario, String nombre, Rol rol) {
        return usuarios.sembrar(Usuario.nuevo(usuario, nombre, rol, contrasenas.hash("la-suya"), false, reloj.ahora()));
    }

    private final Usuario ruben = sembrar("ruben", "Rubén", Rol.ADMINISTRADOR);
    private final Actor administrador = ruben.actor();
    private final Actor carolina = ActoresDePrueba.cajero();

    // ── Crear ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-013 y RF-014: el administrador crea a un cajero con contraseña inicial; al entrar tiene que cambiarla, y queda auditado")
    void crearCajero() {
        FichaUsuario creada = crear.ejecutar("Carolina Ruiz", "carolina", Rol.CAJERO, "temporal-1", administrador);

        assertThat(creada.rol()).isEqualTo(Rol.CAJERO);
        assertThat(creada.activo()).isTrue();
        assertThat(creada.debeCambiarContrasena()).isTrue();
        assertThat(entrar.ejecutar("CAROLINA", "temporal-1").debeCambiarContrasena()).isTrue();
        assertThat(auditoria.eventos).singleElement().satisfies(e -> {
            assertThat(e.accion()).isEqualTo(AccionAuditada.CREAR_USUARIO);
            assertThat(e.usuarioId()).isEqualTo(administrador.id());
            assertThat(e.entidadId()).isEqualTo(creada.id());
        });
    }

    @Test
    @DisplayName("un usuario repetido se rechaza con su nombre, sin distinguir mayúsculas ni tildes; y la contraseña corta también")
    void crearRepetido() {
        crear.ejecutar("Carolina Ruiz", "carolina", Rol.CAJERO, "temporal-1", administrador);

        assertThatThrownBy(() -> crear.ejecutar("Otra", "CAROLINA", Rol.CAJERO, "temporal-2", administrador))
                .hasMessage("Ya existe el usuario «carolina»");
        assertThatThrownBy(() -> crear.ejecutar("Inés", "ines", Rol.CAJERO, "corta", administrador))
                .hasMessageContaining("al menos 6");
        assertThat(usuarios.datos).hasSize(2);
    }

    @Test
    @DisplayName("RF-010: un cajero no crea usuarios, no cambia roles, no desactiva, no activa, no restablece ni ve la lista")
    void elCajeroNo() {
        Usuario ines = sembrar("ines", "Inés", Rol.CAJERO);

        assertThatThrownBy(() -> crear.ejecutar("Otro", "otro", Rol.CAJERO, "temporal-1", carolina))
                .isInstanceOf(NoPermitidoException.class);
        assertThatThrownBy(() -> cambiarRol.ejecutar(ines.getId(), Rol.ADMINISTRADOR, carolina))
                .isInstanceOf(NoPermitidoException.class);
        assertThatThrownBy(() -> desactivar.ejecutar(ines.getId(), carolina)).isInstanceOf(NoPermitidoException.class);
        assertThatThrownBy(() -> activar.ejecutar(ines.getId(), carolina)).isInstanceOf(NoPermitidoException.class);
        assertThatThrownBy(() -> restablecer.ejecutar(ines.getId(), carolina)).isInstanceOf(NoPermitidoException.class);
        assertThatThrownBy(() -> consultar.todos(carolina)).isInstanceOf(NoPermitidoException.class);
        assertThat(ines.getRol()).isEqualTo(Rol.CAJERO);
        assertThat(ines.isActivo()).isTrue();
        assertThat(auditoria.eventos).isEmpty();
    }

    // ── El último administrador (RF-016) ─────────────────────────────────────

    @Test
    @DisplayName("RF-016: al único administrador no se le quita el rol ni se le desactiva, y nadie se desactiva a sí mismo")
    void elUltimoAdministrador() {
        Usuario ines = sembrar("ines", "Inés", Rol.CAJERO);

        assertThatThrownBy(() -> cambiarRol.ejecutar(ruben.getId(), Rol.CAJERO, administrador))
                .hasMessage(CambiarRol.ULTIMO_ADMINISTRADOR);
        assertThatThrownBy(() -> desactivar.ejecutar(ruben.getId(), administrador))
                .hasMessage(DesactivarUsuario.A_SI_MISMO);
        assertThat(ruben.getRol()).isEqualTo(Rol.ADMINISTRADOR);
        assertThat(ruben.isActivo()).isTrue();

        // Con otro administrador activo, ya se puede: la tienda no se queda sin ninguno.
        cambiarRol.ejecutar(ines.getId(), Rol.ADMINISTRADOR, administrador);
        Actor inesAdministradora = ines.actor();
        assertThat(cambiarRol.ejecutar(ruben.getId(), Rol.CAJERO, inesAdministradora).rol()).isEqualTo(Rol.CAJERO);

        // Ahora Inés es la única administradora: no se desactiva a sí misma...
        assertThatThrownBy(() -> desactivar.ejecutar(ines.getId(), inesAdministradora))
                .hasMessage(DesactivarUsuario.A_SI_MISMO);
        // ...pero a otro administrador, que no es el último, sí lo desactiva.
        Actor beto = sembrar("beto", "Beto", Rol.ADMINISTRADOR).actor();
        assertThat(desactivar.ejecutar(beto.id(), inesAdministradora).activo()).isFalse();
        // Y aunque Beto alcanzara a pedirlo con su sesión vieja, Inés es la única que queda activa.
        assertThatThrownBy(() -> desactivar.ejecutar(ines.getId(), beto))
                .hasMessage(CambiarRol.ULTIMO_ADMINISTRADOR);
        assertThat(usuarios.vecesBloqueadosLosAdministradores).isPositive();
    }

    @Test
    @DisplayName("la cuenta del último administrador se hace bajo candado: dos cambios a la vez no dejan la tienda sin ninguno")
    void bajoCandado() {
        Usuario ines = sembrar("ines", "Inés", Rol.CAJERO);
        int antes = usuarios.vecesBloqueadosLosAdministradores;

        cambiarRol.ejecutar(ines.getId(), Rol.ADMINISTRADOR, administrador);
        desactivar.ejecutar(ines.getId(), administrador);

        assertThat(usuarios.vecesBloqueadosLosAdministradores).isEqualTo(antes + 2);
    }

    // ── Desactivar y activar (RF-015) ────────────────────────────────────────

    @Test
    @DisplayName("RF-015: quien queda desactivado no entra y su sesión abierta deja de valer; al activarlo vuelve con su misma contraseña")
    void desactivarYActivar() {
        Usuario ines = sembrar("ines", "Inés", Rol.CAJERO);
        Sesion sesion = entrar.ejecutar("ines", "la-suya");
        ConsultarSesion consultarSesion = new ConsultarSesion(usuarios);
        assertThat(consultarSesion.vigente(ines.getId(), sesion.versionSesion())).isPresent();

        desactivar.ejecutar(ines.getId(), administrador);

        assertThat(consultarSesion.vigente(ines.getId(), sesion.versionSesion())).isEmpty();
        assertThatThrownBy(() -> entrar.ejecutar("ines", "la-suya"))
                .isInstanceOf(CredencialesInvalidasException.class);
        assertThat(auditoria.eventos).extracting(e -> e.accion()).contains(AccionAuditada.DESACTIVAR_USUARIO);

        activar.ejecutar(ines.getId(), administrador);

        assertThat(consultarSesion.vigente(ines.getId(), sesion.versionSesion()))
                .as("la cookie que tenía antes de que la desactivaran no revive al reactivarla").isEmpty();
        assertThat(entrar.ejecutar("ines", "la-suya").actor().id()).isEqualTo(ines.getId());
        assertThat(auditoria.eventos).extracting(e -> e.accion()).contains(AccionAuditada.ACTIVAR_USUARIO);
    }

    @Test
    @DisplayName("desactivar a uno ya desactivado no es un error y no vuelve a quedar en la auditoría")
    void desactivarDosVeces() {
        Usuario ines = sembrar("ines", "Inés", Rol.CAJERO);
        desactivar.ejecutar(ines.getId(), administrador);
        int eventos = auditoria.eventos.size();

        assertThat(desactivar.ejecutar(ines.getId(), administrador).activo()).isFalse();

        assertThat(auditoria.eventos).hasSize(eventos);
    }

    // ── Restablecer (RF-017 y RF-019) ────────────────────────────────────────

    @Test
    @DisplayName("RF-017: restablecer deja una contraseña temporal que se dicta una vez; la vieja no sirve y al entrar hay que cambiarla")
    void restablecerContrasena() {
        Usuario ines = sembrar("ines", "Inés", Rol.CAJERO);
        Sesion vieja = entrar.ejecutar("ines", "la-suya");

        String temporal = restablecer.ejecutar(ines.getId(), administrador);

        assertThat(temporal).hasSize(ContrasenaTemporal.LARGO);
        assertThat(new ConsultarSesion(usuarios).vigente(ines.getId(), vieja.versionSesion())).isEmpty();
        assertThatThrownBy(() -> entrar.ejecutar("ines", "la-suya"))
                .isInstanceOf(CredencialesInvalidasException.class);
        assertThat(entrar.ejecutar("ines", temporal).debeCambiarContrasena()).isTrue();
        assertThat(auditoria.eventos).extracting(e -> e.accion()).contains(AccionAuditada.RESTABLECER_CONTRASENA);
    }

    @Test
    @DisplayName("RF-019: desde el computador de la tienda se le restablece al administrador; a un cajero, no")
    void restablecerDesdeLaTienda() {
        sembrar("ines", "Inés", Rol.CAJERO);

        String temporal = desdeLaTienda.ejecutar("RUBEN");

        assertThat(entrar.ejecutar("ruben", temporal).debeCambiarContrasena()).isTrue();
        assertThat(auditoria.eventos).singleElement()
                .satisfies(e -> assertThat(e.motivo()).isEqualTo(RestablecerDesdeLaTienda.MOTIVO));
        assertThatThrownBy(() -> desdeLaTienda.ejecutar("ines")).hasMessageContaining("no es administrador");
        assertThatThrownBy(() -> desdeLaTienda.ejecutar("nadie")).hasMessageContaining("No existe el usuario");
    }

    @Test
    @DisplayName("RF-024: la lista trae a todos, por nombre, con su rol, si están activos y su última entrada; nunca el hash")
    void laLista() {
        crear.ejecutar("Ana", "ana", Rol.CAJERO, "temporal-1", administrador);
        entrar.ejecutar("ruben", "la-suya");

        var lista = consultar.todos(administrador);

        assertThat(lista).extracting(FichaUsuario::nombre).containsExactly("Ana", "Rubén");
        assertThat(lista).extracting(FichaUsuario::usuario).containsExactly("ana", "ruben");
        assertThat(lista.get(0).ultimaEntrada()).isNull();
        assertThat(lista.get(1).ultimaEntrada()).isEqualTo(reloj.ahora());
        assertThat(lista.toString()).doesNotContain("falso:");
    }
}
