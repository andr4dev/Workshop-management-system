package com.workshopmanagement.rdmotors.correo.infraestructura;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.workshopmanagement.rdmotors.correo.aplicacion.MandarCorreosPendientes;

/**
 * ADAPTADOR DE ENTRADA — el reloj de los correos (spec 0010, RF-003): cada minuto, manda los que ya se pueden.
 *
 * <p>Corre en su propio hilo: el mostrador nunca espera un correo. Y lo que falle queda escrito en cada correo, así
 * que esta tarea sigue viva para el minuto siguiente.
 */
@Component
@ConditionalOnProperty(name = "rdmotors.correo.habilitado", havingValue = "true", matchIfMissing = true)
class TareaDeCorreos {

    private static final Logger LOG = LoggerFactory.getLogger(TareaDeCorreos.class);

    private final MandarCorreosPendientes mandar;

    TareaDeCorreos(MandarCorreosPendientes mandar) {
        this.mandar = mandar;
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT30S")
    void cadaMinuto() {
        try {
            int intentados = mandar.mandar();
            if (intentados > 0) {
                LOG.info("Correos: {} intentados en esta vuelta", intentados);
            }
        } catch (RuntimeException e) {
            // La base no respondió, por ejemplo: se intenta en el minuto siguiente.
            LOG.warn("Correos: la vuelta no pudo correr: {}", e.getMessage());
        }
    }
}
