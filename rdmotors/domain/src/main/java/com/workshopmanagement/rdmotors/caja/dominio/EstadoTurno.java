package com.workshopmanagement.rdmotors.caja.dominio;

/** Un turno de caja está abierto mientras se vende en él. Solo puede haber uno abierto. */
public enum EstadoTurno {
    ABIERTO,
    /** Llega con el cierre y el arqueo de la rebanada 3. */
    CERRADO
}
