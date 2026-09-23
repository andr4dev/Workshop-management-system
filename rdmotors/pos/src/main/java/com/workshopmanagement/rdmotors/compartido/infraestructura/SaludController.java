package com.workshopmanagement.rdmotors.compartido.infraestructura;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — ¿está vivo? (spec 0011, RF-006).
 *
 * <h2>Para qué existe</h2>
 *
 * En la nube gratis el servidor se apaga cuando nadie lo usa, y el primero que llega después espera a que prenda.
 * Para que ese primero no sea un cliente en el mostrador, un servicio de afuera toca esta dirección <b>cada cinco
 * minutos en horario de almacén</b>: mientras le lleguen visitas, el servidor no se duerme.
 *
 * <h2>Por qué no dice nada</h2>
 *
 * Esta es la única dirección del sistema que está abierta a internet entero, sin sesión, y que alguien va a estar
 * llamando todo el día. Así que responde <b>sí o no</b> y nada más: ni la versión que corre, ni el nombre de la
 * base, ni cuántas ventas hay. Cada dato que se filtre por aquí es un dato gratis para quien esté mirando.
 *
 * <p>Incluido el error: cuando la base no responde, <b>no se devuelve lo que dijo el motor</b> —ahí va el host y a
 * veces el usuario—, solo que no está disponible.
 *
 * <h2>Y por qué pregunta por la base</h2>
 *
 * Responder "vivo" porque el servidor contestó no sirve de nada: un sistema con la base caída no puede vender. Lo
 * que se comprueba es lo que hace falta para trabajar.
 */
@RestController
@RequiredArgsConstructor
class SaludController {

    private final JdbcTemplate jdbc;

    @GetMapping("/api/salud")
    ResponseEntity<Map<String, Boolean>> salud() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("ok", false));
        }
    }
}
