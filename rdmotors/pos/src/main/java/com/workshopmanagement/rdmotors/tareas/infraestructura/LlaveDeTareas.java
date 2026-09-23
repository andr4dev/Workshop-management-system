package com.workshopmanagement.rdmotors.tareas.infraestructura;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * La llave con que un servicio de afuera puede disparar las tareas del sistema (spec 0011, RF-007).
 *
 * <h2>Sin llave configurada, nada funciona</h2>
 *
 * En el computador del almacén no hay ningún servicio de afuera llamando: el reloj está adentro. Así que si nadie
 * configuró una llave, estas direcciones quedan muertas. <b>Es lo seguro por omisión</b>: una instalación que no
 * necesita la puerta no la deja abierta por olvido.
 *
 * <h2>Por qué se compara así y no con un igual normal</h2>
 *
 * Comparar dos textos con el {@code equals} de siempre para en la primera letra distinta. Eso hace que una llave
 * que empieza bien tarde un poquito más en ser rechazada que una que empieza mal, y con suficientes intentos esa
 * diferencia de microsegundos deja adivinar la llave letra por letra. La comparación de abajo mira todos los bytes
 * siempre, tarde lo que tarde. Es gratis y cierra el camino.
 */
@Component
class LlaveDeTareas {

    private final byte[] esperada;

    LlaveDeTareas(@Value("${rdmotors.tareas.llave:}") String llave) {
        this.esperada = llave.isBlank() ? null : llave.strip().getBytes(StandardCharsets.UTF_8);
    }

    /** {@code true} solo si hay llave configurada y la que llegó es exactamente esa. */
    boolean cuadra(String recibida) {
        if (esperada == null || recibida == null || recibida.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(esperada, recibida.strip().getBytes(StandardCharsets.UTF_8));
    }
}
