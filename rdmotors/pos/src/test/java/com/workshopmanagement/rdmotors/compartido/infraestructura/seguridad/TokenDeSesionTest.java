package com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Rol;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.Sesion;

/**
 * La cookie que lleva la sesión (spec 0004, RF-005; spec 0011, RF-003).
 *
 * <p>La tienda y la nube necesitan cookies distintas y por eso hay una propiedad: en el almacén la tablet entra por
 * {@code http://192.168.x.x}, que no es HTTPS, así que exigirlo dejaría a todos por fuera; por internet es al
 * revés, una cookie de sesión que viaje sin HTTPS se puede robar en el camino.
 */
class TokenDeSesionTest {

    private static final String CLAVE = "cmRtb3RvcnMtcHJ1ZWJhcy1jbGF2ZS1kZS0yNTYtYml0cyEh";

    @TempDir Path carpeta;

    private static final Reloj RELOJ = new Reloj() {
        @Override
        public Instant ahora() {
            return Instant.parse("2026-09-23T14:00:00Z");
        }

        @Override
        public LocalDate hoy() {
            return LocalDate.parse("2026-09-23");
        }
    };

    private static final Sesion SESION = new Sesion(
            new Actor(UUID.randomUUID(), "Rubén", Rol.ADMINISTRADOR), "ruben", 3, false);

    private TokenDeSesion token(boolean cookieSegura) throws Exception {
        ClaveDelToken clave = new ClaveDelToken(CLAVE, carpeta.resolve("clave").toString(), false);
        return new TokenDeSesion(clave, RELOJ, Duration.ofHours(24), cookieSegura);
    }

    @Test
    @DisplayName("EN LA NUBE LA COOKIE EXIGE HTTPS")
    void enLaNube() throws Exception {
        assertThat(token(true).cookie(SESION).isSecure()).isTrue();
    }

    @Test
    @DisplayName("en el almacén no lo exige: la tablet entra por http a la red local y quedaría por fuera")
    void enElAlmacen() throws Exception {
        assertThat(token(false).cookie(SESION).isSecure()).isFalse();
    }

    @Test
    @DisplayName("lo demás no cambia entre los dos: la página nunca la lee y otro sitio no la manda")
    void loQueNoCambia() throws Exception {
        for (boolean segura : new boolean[] { true, false }) {
            var cookie = token(segura).cookie(SESION);

            assertThat(cookie.isHttpOnly()).as("la página no puede leerla").isTrue();
            assertThat(cookie.getSameSite()).as("una página de otro sitio no la manda").isEqualTo("Strict");
            assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofHours(24));
            assertThat(cookie.getName()).isEqualTo(TokenDeSesion.COOKIE);
        }
    }

    @Test
    @DisplayName("al salir, la cookie se reemplaza por una vacía que ya venció, y también con HTTPS")
    void alSalir() throws Exception {
        var cookie = token(true).borrar();

        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isZero();
        assertThat(cookie.isSecure()).isTrue();
    }
}
