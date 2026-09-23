package com.workshopmanagement.rdmotors.correo.dominio.puerto;

import com.workshopmanagement.rdmotors.correo.dominio.CorreoArmado;
import com.workshopmanagement.rdmotors.correo.dominio.Destinatarios;

/**
 * PUERTO — quien saca el correo a internet (spec 0010).
 *
 * <p>Lo cumplen Brevo (su API HTTP, como el car-wash) y el falso de las pruebas. Si mañana se cambia de proveedor,
 * cambia el adaptador; el correo, la cola y los reintentos no se enteran.
 */
public interface EnviadorDeCorreos {

    /**
     * Si está todo lo necesario para mandar: la llave y el remitente. Sin eso no tiene sentido ni intentarlo.
     */
    boolean estaConfigurado();

    /** Qué falta configurar, en palabras, o {@code null} si no falta nada. */
    String loQueFalta();

    /** El remitente con el que salen, para mostrarlo: nunca la llave. */
    String remitente();

    /**
     * Manda el correo.
     *
     * @return el id que el proveedor le dio, para rastrearlo en su panel
     * @throws com.workshopmanagement.rdmotors.correo.dominio.EnvioFallidoException si no salió, diciendo si se
     *         arregla sola (sin internet) o no (llave inválida)
     */
    String enviar(Destinatarios para, CorreoArmado correo);
}
