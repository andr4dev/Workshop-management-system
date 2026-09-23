package com.workshopmanagement.rdmotors.usuarios.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.Rol;

/** Un usuario y sus reglas (spec 0004, RF-001 a RF-003). */
class UsuarioTest {

    private static final Instant AHORA = Instant.parse("2026-09-19T13:00:00Z");

    private static Usuario carolina() {
        return Usuario.nuevo(" Carolina ", "Carolina Gómez", Rol.CAJERO, "hash", true, AHORA);
    }

    @Test
    @DisplayName("RF-001: el usuario se compara sin mayúsculas ni tildes, y guarda como se escribió")
    void usuarioNormalizado() {
        Usuario ines = Usuario.nuevo("Inés.Ruiz", "Inés Ruiz", Rol.CAJERO, "hash", true, AHORA);

        assertThat(carolina().getUsuario()).isEqualTo("Carolina");
        assertThat(carolina().getUsuarioNormalizado()).isEqualTo("carolina");
        assertThat(ines.getUsuarioNormalizado()).isEqualTo("ines.ruiz");
        assertThat(Usuario.normalizar("INÉS.RUIZ ")).isEqualTo("ines.ruiz");
    }

    @Test
    @DisplayName("un usuario con espacios, muy corto o sin nombre no se crea")
    void datosValidos() {
        assertThatThrownBy(() -> Usuario.nuevo("carolina gomez", "Carolina", Rol.CAJERO, "h", true, AHORA))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessageContaining("sin espacios");
        assertThatThrownBy(() -> Usuario.nuevo("cg", "Carolina", Rol.CAJERO, "h", true, AHORA))
                .isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(() -> Usuario.nuevo("carolina", "  ", Rol.CAJERO, "h", true, AHORA))
                .hasMessage("Escribe el nombre: es el que sale en el comprobante");
        assertThatThrownBy(() -> Usuario.nuevo("carolina", "Carolina", null, "h", true, AHORA))
                .hasMessage("Elige el rol");
    }

    @Test
    @DisplayName("RF-002: la contraseña tiene al menos 6 caracteres y hasta 64")
    void contrasenaValida() {
        assertThatThrownBy(() -> Usuario.exigirContrasenaValida("12345"))
                .hasMessage("La contraseña tiene que tener al menos 6 caracteres");
        assertThatThrownBy(() -> Usuario.exigirContrasenaValida(null)).isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(() -> Usuario.exigirContrasenaValida("x".repeat(65)))
                .hasMessage("La contraseña puede tener hasta 64 caracteres");
        Usuario.exigirContrasenaValida("123456");
    }

    @Test
    @DisplayName("RF-003: al quinto intento fallido espera 5 minutos; pasada la espera, vuelve a tener cinco")
    void cincoIntentos() {
        Usuario u = carolina();
        for (int i = 0; i < 4; i++) {
            u.registrarIntentoFallido(AHORA);
            assertThat(u.estaBloqueado(AHORA)).as("intento %d", i + 1).isFalse();
        }
        u.registrarIntentoFallido(AHORA);

        assertThat(u.estaBloqueado(AHORA)).isTrue();
        assertThat(u.estaBloqueado(AHORA.plusSeconds(299))).isTrue();
        assertThat(u.estaBloqueado(AHORA.plusSeconds(300))).isFalse();
        assertThat(u.getIntentosFallidos()).isZero();
    }

    @Test
    @DisplayName("entrar bien olvida los intentos fallidos y guarda la última entrada")
    void entroBien() {
        Usuario u = carolina();
        u.registrarIntentoFallido(AHORA);
        u.registrarIntentoFallido(AHORA);

        u.entroBien(AHORA.plusSeconds(60));

        assertThat(u.getIntentosFallidos()).isZero();
        assertThat(u.getUltimaEntrada()).isEqualTo(AHORA.plusSeconds(60));
    }

    @Test
    @DisplayName("cambiar la contraseña sube la versión de la sesión y ya no pide cambiarla")
    void cambiarContrasena() {
        Usuario u = carolina();
        long antes = u.getVersionSesion();

        u.cambiarContrasena("otro-hash");

        assertThat(u.getVersionSesion()).isEqualTo(antes + 1);
        assertThat(u.isDebeCambiarContrasena()).isFalse();
        assertThat(u.getHash()).isEqualTo("otro-hash");
    }

    @Test
    @DisplayName("la fotografía para la auditoría nunca lleva el hash")
    void fotografiaSinHash() {
        assertThat(carolina().fotografia()).doesNotContainKey("hash").doesNotContainValue("hash")
                .containsEntry("rol", "CAJERO");
    }

    @Test
    @DisplayName("el actor: el cajero no ve costos y no pasa lo del administrador; el administrador sí")
    void actor() {
        Actor cajero = carolina().actor();
        Actor admin = Usuario.nuevo("ruben", "Rubén", Rol.ADMINISTRADOR, "h", false, AHORA).actor();

        assertThat(cajero.nombre()).isEqualTo("Carolina Gómez");
        assertThat(cajero.rol().veCostos()).isFalse();
        assertThatThrownBy(cajero::exigirAdministrador).isInstanceOf(NoPermitidoException.class)
                .hasMessage("No permitido: es del administrador");
        assertThat(admin.rol().veCostos()).isTrue();
        admin.exigirAdministrador();
    }
}
