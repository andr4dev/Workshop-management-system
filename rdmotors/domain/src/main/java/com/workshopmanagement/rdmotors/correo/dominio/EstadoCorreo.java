package com.workshopmanagement.rdmotors.correo.dominio;

/** En qué va un correo (spec 0010). */
public enum EstadoCorreo {
    /** Espera su turno para salir: recién encolado, o reintentando porque no hubo internet. */
    POR_MANDAR,
    /** Brevo lo aceptó. */
    ENVIADO,
    /** Brevo dijo que así no se puede mandar (llave, remitente, dirección): no se insiste solo. */
    FALLO
}
