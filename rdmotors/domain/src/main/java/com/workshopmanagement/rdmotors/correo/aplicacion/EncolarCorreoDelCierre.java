package com.workshopmanagement.rdmotors.correo.aplicacion;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.workshopmanagement.rdmotors.correo.dominio.Correo;
import com.workshopmanagement.rdmotors.correo.dominio.Destinatarios;
import com.workshopmanagement.rdmotors.correo.dominio.puerto.RepositorioAjustesDeCorreo;
import com.workshopmanagement.rdmotors.correo.dominio.puerto.RepositorioCorreos;

/**
 * Lo que el cierre de caja le pide al correo (spec 0010, RF-001): dejar el resumen por mandar.
 *
 * <p><b>No abre transacción</b>, como {@code FiarVenta}: lo llama {@code CerrarTurno}, dentro de la suya. Si el cierre
 * falla, el correo no existe; si el cierre queda, el correo también. Mandarlo es otra cosa, y es de la tarea.
 */
public class EncolarCorreoDelCierre {

    /** Lo que se espera antes del primer intento: lo que tarda escribir las observaciones de un cierre (RF-002). */
    public static final Duration ESPERA_POR_LAS_OBSERVACIONES = Duration.ofMinutes(3);

    private final RepositorioAjustesDeCorreo ajustes;
    private final RepositorioCorreos correos;

    public EncolarCorreoDelCierre(RepositorioAjustesDeCorreo ajustes, RepositorioCorreos correos) {
        this.ajustes = ajustes;
        this.correos = correos;
    }

    /** Sin destinatarios configurados no se encola nada: así se apaga el correo del cierre (RF-007). */
    public void alCerrar(UUID turnoId, Instant cerradoEn) {
        Destinatarios para = ajustes.obtener().destinatarios();
        if (para.hayAlguno()) {
            correos.guardar(Correo.delCierre(turnoId, para, cerradoEn, ESPERA_POR_LAS_OBSERVACIONES));
        }
    }
}
