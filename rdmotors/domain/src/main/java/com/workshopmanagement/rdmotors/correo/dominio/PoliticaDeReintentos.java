package com.workshopmanagement.rdmotors.correo.dominio;

import java.time.Duration;
import java.util.List;

/**
 * Cuánto esperar antes de volver a intentar un correo que no salió (spec 0010, RF-003): 1, 5, 15 y 30 minutos, y
 * después cada hora, sin rendirse.
 *
 * <p>Las primeras esperas son cortas porque lo normal es un corte breve de internet. Después, cada hora: una tienda
 * puede pasar un fin de semana sin internet, y el correo del viernes tiene que llegar el lunes igual.
 */
public final class PoliticaDeReintentos {

    private static final List<Duration> ESPERAS = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(30));
    private static final Duration DESPUES = Duration.ofHours(1);

    /** @param intentos cuántos intentos van, contando el que acaba de fallar (1 o más) */
    public Duration esperaTras(int intentos) {
        if (intentos < 1) {
            throw new IllegalArgumentException("Se espera después de un intento, no antes: " + intentos);
        }
        return intentos <= ESPERAS.size() ? ESPERAS.get(intentos - 1) : DESPUES;
    }
}
