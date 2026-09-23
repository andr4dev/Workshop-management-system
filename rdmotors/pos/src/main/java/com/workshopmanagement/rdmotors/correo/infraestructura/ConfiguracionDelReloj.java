package com.workshopmanagement.rdmotors.correo.infraestructura;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enciende el reloj de adentro, el que hace correr la tarea de los correos.
 *
 * <p>Vivía al lado del respaldo, que era la primera cosa que el sistema hacía solo. Desde el spec 0011 el respaldo
 * ya no se hace solo —lo baja el administrador cuando quiere— y lo único que queda pasando sin que nadie lo pida
 * son los correos del cierre.
 *
 * <p>Este reloj <b>solo cuenta mientras el servidor esté despierto</b>. En la nube se apaga cuando nadie lo usa, y
 * por eso hay además un reloj afuera que lo llama ({@code tareas/infraestructura}). Los dos hacen lo mismo y
 * repetirlo no manda ningún correo dos veces.
 *
 * <p>Va en su propio archivo, al lado de la tarea, para que se vea de dónde sale: una anotación escondida en la
 * clase de arranque hace que nadie sepa por qué el sistema hace cosas solo.
 */
@Configuration
@EnableScheduling
class ConfiguracionDelReloj {
}
