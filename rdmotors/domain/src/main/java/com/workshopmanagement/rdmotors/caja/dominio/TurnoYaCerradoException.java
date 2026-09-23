package com.workshopmanagement.rdmotors.caja.dominio;

import java.time.Instant;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

import lombok.Getter;

/**
 * Se intentó cerrar un turno que ya estaba cerrado: un doble clic o dos pestañas (spec 0006, RF-018).
 * Lleva cuándo y quién lo cerró, para que la pantalla muestre ese cierre en vez de solo negarse.
 */
@Getter
public class TurnoYaCerradoException extends ReglaDeNegocioException {

    private final UUID turnoId;
    private final Instant cerradoEn;
    private final UUID cerradoPorId;

    public TurnoYaCerradoException(UUID turnoId, Instant cerradoEn, UUID cerradoPorId) {
        super("Este turno ya se cerró");
        this.turnoId = turnoId;
        this.cerradoEn = cerradoEn;
        this.cerradoPorId = cerradoPorId;
    }
}
