package com.workshopmanagement.rdmotors.usuarios.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.Rol;
import com.workshopmanagement.rdmotors.usuarios.dominio.CredencialesInvalidasException;
import com.workshopmanagement.rdmotors.usuarios.dominio.DatosDelEquipo;
import com.workshopmanagement.rdmotors.usuarios.dominio.Entrada;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;
import com.workshopmanagement.rdmotors.usuarios.dominio.UsuarioBloqueadoException;
import com.workshopmanagement.rdmotors.usuarios.dominio.YaHayUsuariosException;

/** Entrar, el primer administrador, cambiar la contraseña y la sesión vigente (spec 0004, fase 1). */
class EntrarYSesionTest {

    private final Falsos.UsuariosEnMemoria usuarios = new Falsos.UsuariosEnMemoria();
    private final Falsos.ContrasenasFalsas contrasenas = new Falsos.ContrasenasFalsas();
    private final Falsos.AuditoriaEnMemoria auditoria = new Falsos.AuditoriaEnMemoria();
    private final Falsos.RelojFijo reloj = new Falsos.RelojFijo("2026-09-19T13:00:00Z");

    private final CrearPrimerAdministrador crearPrimero =
            new CrearPrimerAdministrador(usuarios, contrasenas, auditoria, reloj);
    private final Falsos.EntradasEnMemoria entradas = new Falsos.EntradasEnMemoria();
    private final Entrar entrar = new Entrar(usuarios, contrasenas, entradas, reloj);
    private final CambiarContrasena cambiar = new CambiarContrasena(usuarios, contrasenas);
    private final ConsultarSesion consultar = new ConsultarSesion(usuarios);

    private Usuario carolina(String contrasena) {
        return usuarios.sembrar(Usuario.nuevo("carolina", "Carolina", Rol.CAJERO, contrasenas.hash(contrasena),
                false, reloj.ahora()));
    }

    // ── Primer administrador ─────────────────────────────────────────────────

    @Test
    @DisplayName("RF-018: con la base vacía se crea el primer administrador, adentro y sin tener que cambiar la contraseña; queda auditado")
    void primerAdministrador() {
        assertThat(crearPrimero.faltaAdministrador()).isTrue();

        Sesion sesion = crearPrimero.ejecutar("Rubén Díaz", "ruben", "secreta1");

        assertThat(sesion.actor().rol()).isEqualTo(Rol.ADMINISTRADOR);
        assertThat(sesion.actor().nombre()).isEqualTo("Rubén Díaz");
        assertThat(sesion.debeCambiarContrasena()).isFalse();
        assertThat(usuarios.vecesBloqueadoElAlta).as("bajo candado").isEqualTo(1);
        assertThat(usuarios.datos.values()).singleElement()
                .satisfies(u -> assertThat(u.getHash()).isNotEqualTo("secreta1").startsWith("falso:"));
        assertThat(auditoria.eventos).singleElement()
                .satisfies(e -> assertThat(e.accion()).isEqualTo(AccionAuditada.CREAR_USUARIO));
        assertThat(crearPrimero.faltaAdministrador()).isFalse();
    }

    @Test
    @DisplayName("RF-018: con un usuario creado, el primer administrador ya no se puede crear")
    void soloLaPrimeraVez() {
        carolina("secreta1");

        assertThatThrownBy(() -> crearPrimero.ejecutar("Otro", "otro", "secreta1"))
                .isInstanceOf(YaHayUsuariosException.class);
        assertThat(usuarios.datos).hasSize(1);
    }

    @Test
    @DisplayName("el primer administrador con una contraseña corta no se crea")
    void primeroConContrasenaCorta() {
        assertThatThrownBy(() -> crearPrimero.ejecutar("Rubén", "ruben", "123"))
                .isInstanceOf(ReglaDeNegocioException.class);
        assertThat(usuarios.datos).isEmpty();
    }

    // ── Entrar ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-001: entra con su usuario escrito en mayúsculas o con tildes, y guarda la última entrada")
    void entraBien() {
        Usuario c = carolina("secreta1");

        Sesion sesion = entrar.ejecutar("CAROLINA", "secreta1");

        assertThat(sesion.actor().id()).isEqualTo(c.getId());
        assertThat(sesion.usuario()).isEqualTo("carolina");
        assertThat(c.getUltimaEntrada()).isEqualTo(reloj.ahora());
    }

    @Test
    @DisplayName("RF-003: usuario inexistente, contraseña mal y desactivado dan el mismo error; el inexistente también calcula un hash")
    void mismoError() {
        carolina("secreta1");
        int comparacionesAntes = contrasenas.comparaciones;

        assertThatThrownBy(() -> entrar.ejecutar("nadie", "secreta1"))
                .isInstanceOf(CredencialesInvalidasException.class).hasMessage("Usuario o contraseña incorrectos");
        assertThat(contrasenas.comparaciones).as("se compara contra el hash de mentira").isEqualTo(comparacionesAntes + 1);
        assertThatThrownBy(() -> entrar.ejecutar("carolina", "mala-mala"))
                .isInstanceOf(CredencialesInvalidasException.class).hasMessage("Usuario o contraseña incorrectos");
        assertThatThrownBy(() -> entrar.ejecutar(null, null)).isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    @DisplayName("RF-003: cinco intentos fallidos bloquean ese usuario 5 minutos, aun con la contraseña correcta")
    void bloqueo() {
        carolina("secreta1");
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> entrar.ejecutar("carolina", "mala-mala"))
                    .isInstanceOf(CredencialesInvalidasException.class);
        }

        assertThatThrownBy(() -> entrar.ejecutar("carolina", "secreta1"))
                .isInstanceOf(UsuarioBloqueadoException.class)
                .hasMessage("Demasiados intentos. Espera 5 minutos y vuelve a intentarlo");

        Entrar cincoMinutosDespues = new Entrar(usuarios, contrasenas, entradas, new Falsos.RelojFijo("2026-09-19T13:05:00Z"));
        assertThat(cincoMinutosDespues.ejecutar("carolina", "secreta1").actor().nombre()).isEqualTo("Carolina");
    }

    // ── Cambiar la contraseña ────────────────────────────────────────────────

    @Test
    @DisplayName("RF-017: cambia la suya dando la actual; la sesión vieja deja de valer y la nueva sí")
    void cambiarContrasena() {
        Usuario c = carolina("secreta1");
        Sesion vieja = entrar.ejecutar("carolina", "secreta1");

        Sesion nueva = cambiar.ejecutar(vieja.actor(), "secreta1", "otra-clave");

        assertThat(consultar.vigente(c.getId(), vieja.versionSesion())).isEmpty();
        assertThat(consultar.vigente(c.getId(), nueva.versionSesion())).isPresent();
        assertThat(entrar.ejecutar("carolina", "otra-clave").actor().id()).isEqualTo(c.getId());
    }

    @Test
    @DisplayName("RF-017: con la actual mal, corta o igual a la actual, no la cambia")
    void cambiarContrasenaReglas() {
        carolina("secreta1");
        var actor = entrar.ejecutar("carolina", "secreta1").actor();

        assertThatThrownBy(() -> cambiar.ejecutar(actor, "mala-mala", "otra-clave"))
                .hasMessage("La contraseña actual no es esa");
        assertThatThrownBy(() -> cambiar.ejecutar(actor, "secreta1", "123"))
                .hasMessage("La contraseña tiene que tener al menos 6 caracteres");
        assertThatThrownBy(() -> cambiar.ejecutar(actor, "secreta1", "secreta1"))
                .hasMessage("La contraseña nueva tiene que ser distinta de la actual");
        assertThat(entrar.ejecutar("carolina", "secreta1")).isNotNull();
    }

    @Test
    @DisplayName("RF-014: una contraseña inicial pide cambiarse; al cambiarla, ya no")
    void contrasenaInicial() {
        usuarios.sembrar(Usuario.nuevo("carolina", "Carolina", Rol.CAJERO, contrasenas.hash("temporal1"), true,
                reloj.ahora()));

        Sesion sesion = entrar.ejecutar("carolina", "temporal1");
        assertThat(sesion.debeCambiarContrasena()).isTrue();

        assertThat(cambiar.ejecutar(sesion.actor(), "temporal1", "la-mia-1").debeCambiarContrasena()).isFalse();
    }

    // ── La sesión vigente ────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-015: una sesión deja de valer si el usuario no existe o la versión del token ya no es la suya")
    void sesionVigente() {
        Usuario c = carolina("secreta1");

        assertThat(consultar.vigente(c.getId(), c.getVersionSesion())).hasValueSatisfying(
                s -> assertThat(s.actor().rol()).isEqualTo(Rol.CAJERO));
        assertThat(consultar.vigente(c.getId(), c.getVersionSesion() + 1)).isEmpty();
        assertThat(consultar.vigente(java.util.UUID.randomUUID(), 1)).isEmpty();
    }

    // ── El registro de entradas (RF-025) ─────────────────────────────────────

    @Test
    @DisplayName("RF-025: cada intento queda en el registro —entre o no—, con el usuario escrito y desde qué equipo, y nunca la contraseña")
    void registroDeEntradas() {
        carolina("la-de-carolina");
        DatosDelEquipo equipo = new DatosDelEquipo("192.168.1.50", "Chrome en Windows");

        entrar.ejecutar("carolina", "la-de-carolina", equipo);
        assertThatThrownBy(() -> entrar.ejecutar("carolina", "no-es-esa", equipo))
                .isInstanceOf(CredencialesInvalidasException.class);
        assertThatThrownBy(() -> entrar.ejecutar("nadie-se-llama-asi", "probando", equipo))
                .isInstanceOf(CredencialesInvalidasException.class);

        assertThat(entradas.datos).hasSize(3);
        assertThat(entradas.datos).extracting(Entrada::isExito).containsExactly(true, false, false);
        assertThat(entradas.datos).extracting(Entrada::getUsuarioEscrito)
                .containsExactly("carolina", "carolina", "nadie-se-llama-asi");
        assertThat(entradas.datos.get(2).getUsuarioId()).as("nadie se llama así: no hay a quién apuntar").isNull();
        assertThat(entradas.datos).allSatisfy(e -> {
            assertThat(e.getIp()).isEqualTo("192.168.1.50");
            assertThat(e.getNavegador()).isEqualTo("Chrome en Windows");
        });
        assertThat(entradas.datos.toString()).doesNotContain("la-de-carolina").doesNotContain("no-es-esa");
    }

    @Test
    @DisplayName("el administrador ve las entradas, de la más reciente a la más antigua; el cajero no")
    void quienVeLasEntradas() {
        Usuario ella = carolina("la-de-carolina");
        entrar.ejecutar("carolina", "la-de-carolina");
        ConsultarEntradas consultarEntradas = new ConsultarEntradas(entradas, usuarios);
        Actor administrador = usuarios.sembrar(Usuario.nuevo("ruben", "Rubén", Rol.ADMINISTRADOR,
                contrasenas.hash("la-de-ruben"), false, reloj.ahora())).actor();

        var pagina = consultarEntradas.ultimas(0, 25, administrador);

        assertThat(pagina.elementos()).singleElement().satisfies(e -> {
            assertThat(e.usuarioEscrito()).isEqualTo("carolina");
            assertThat(e.quien().nombre()).isEqualTo(ella.getNombre());
            assertThat(e.exito()).isTrue();
        });
        assertThatThrownBy(() -> consultarEntradas.ultimas(0, 25, ella.actor()))
                .isInstanceOf(NoPermitidoException.class);
    }
}
