package com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * La clave con que el servidor firma los tokens de sesión (spec 0004, decisión 3). Solo la tiene el servidor de la
 * tienda: sin ella nadie puede fabricar ni alargar un token.
 *
 * <p>Sale de {@code rdmotors.seguridad.clave-token} (256 bits en base64). Si no está configurada, la primera vez se
 * genera al azar y se guarda en {@code ~/.rdmotors/clave-token}, para que las sesiones sobrevivan a un reinicio. Si
 * ese archivo se pierde, lo único que pasa es que todos vuelven a entrar.
 *
 * <h2>En la nube, la clave es obligatoria</h2>
 *
 * Inventarla y guardarla en un archivo está bien en un computador que siempre es el mismo. En la nube ese archivo
 * <b>se borra en cada reinicio</b>, y en el plan gratis los reinicios son varios al día: cada uno inventaría una
 * clave nueva y <b>sacaría a todo el mundo</b>, sin ningún mensaje que explicara por qué. El dueño estaría cargando
 * inventario desde el celular y se encontraría en la pantalla de entrar cada dos horas.
 *
 * <p>Por eso {@code rdmotors.seguridad.clave-obligatoria} —encendida en el perfil {@code nube} (spec 0011,
 * decisión 3)— hace que el sistema <b>no arranque</b> si falta. Un sistema que arranca mal y falla raro tres horas
 * después cuesta mucho más que uno que no arranca y dice qué le falta.
 */
@Component
public class ClaveDelToken {

    private static final Logger log = LoggerFactory.getLogger(ClaveDelToken.class);
    private static final int BYTES_MINIMOS = 32;

    /** La variable de entorno que la trae en la nube, según {@code application-nube.properties}. */
    static final String VARIABLE = "RDMOTORS_CLAVE_SESIONES";

    private final SecretKey clave;

    ClaveDelToken(@Value("${rdmotors.seguridad.clave-token:}") String configurada,
                  @Value("${rdmotors.seguridad.archivo-clave:${user.home}/.rdmotors/clave-token}") String archivo,
                  @Value("${rdmotors.seguridad.clave-obligatoria:false}") boolean obligatoria)
            throws IOException {
        if (configurada.isBlank() && obligatoria) {
            throw new IllegalStateException("Falta la clave que firma las sesiones. Sin ella, cada reinicio del "
                    + "servidor inventaría una nueva y sacaría a todos los usuarios. Pon la variable de entorno "
                    + VARIABLE + " con 256 bits en base64; se genera una vez con: openssl rand -base64 32");
        }
        byte[] bytes = configurada.isBlank() ? leerOCrear(Path.of(archivo))
                : Base64.getDecoder().decode(configurada.strip());
        if (bytes.length < BYTES_MINIMOS) {
            throw new IllegalStateException("La clave del token tiene que tener al menos 256 bits");
        }
        this.clave = new SecretKeySpec(bytes, "HmacSHA256");
    }

    public SecretKey clave() {
        return clave;
    }

    private static byte[] leerOCrear(Path archivo) throws IOException {
        if (Files.exists(archivo)) {
            return Base64.getDecoder().decode(Files.readString(archivo, StandardCharsets.US_ASCII).strip());
        }
        byte[] nueva = new byte[BYTES_MINIMOS];
        new SecureRandom().nextBytes(nueva);
        Files.createDirectories(archivo.getParent());
        Files.writeString(archivo, Base64.getEncoder().encodeToString(nueva), StandardCharsets.US_ASCII);
        log.info("Se creó la clave de los tokens de sesión en {}", archivo);
        return nueva;
    }
}
