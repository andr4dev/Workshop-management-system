package com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * De dónde sale la clave que firma las sesiones (spec 0011, decisión 3).
 *
 * <p>Lo que se prueba aquí es lo que pasa <b>al arrancar</b>: en el almacén la clave se puede inventar, en la nube
 * no. Confundir los dos casos saca al dueño del sistema cada dos horas sin decirle por qué.
 */
class ClaveDelTokenTest {

    private static final String CLAVE = "cmRtb3RvcnMtcHJ1ZWJhcy1jbGF2ZS1kZS0yNTYtYml0cyEh";

    @TempDir Path carpeta;

    private Path archivo() {
        return carpeta.resolve("clave-token");
    }

    @Test
    @DisplayName("EN LA NUBE, SIN CLAVE NO ARRANCA, y el mensaje dice cuál variable falta")
    void enLaNubeSinClaveNoArranca() {
        assertThatThrownBy(() -> new ClaveDelToken("", archivo().toString(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ClaveDelToken.VARIABLE)
                .as("tiene que decir por qué importa, no solo que falta")
                .hasMessageContaining("sacaría a todos los usuarios");

        assertThat(Files.exists(archivo())).as("y NO se inventa una a escondidas").isFalse();
    }

    @Test
    @DisplayName("en la nube, con la clave puesta, arranca")
    void enLaNubeConClave() throws Exception {
        ClaveDelToken clave = new ClaveDelToken(CLAVE, archivo().toString(), true);

        assertThat(clave.clave().getEncoded()).hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    @DisplayName("en el almacén, sin clave, se inventa una y LA MISMA SIRVE AL SIGUIENTE ARRANQUE")
    void enElAlmacenSeInventaYSeGuarda() throws Exception {
        // Esto es lo que hace que un reinicio del computador de la tienda no saque a nadie. Es correcto allá, y es
        // exactamente lo que no sirve en la nube, donde el archivo no sobrevive.
        ClaveDelToken primera = new ClaveDelToken("", archivo().toString(), false);
        ClaveDelToken segunda = new ClaveDelToken("", archivo().toString(), false);

        assertThat(Files.exists(archivo())).isTrue();
        assertThat(segunda.clave().getEncoded()).isEqualTo(primera.clave().getEncoded());
    }

    @Test
    @DisplayName("una clave corta no sirve para firmar y se rechaza al arrancar")
    void claveCorta() {
        assertThatThrownBy(() -> new ClaveDelToken("Y29ydGE=", archivo().toString(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }
}
