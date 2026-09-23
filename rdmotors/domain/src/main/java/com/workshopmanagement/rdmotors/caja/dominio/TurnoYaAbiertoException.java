package com.workshopmanagement.rdmotors.caja.dominio;

import java.time.Instant;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

import lombok.Getter;

/**
 * Se intentó abrir un turno con otro ya abierto. Lleva desde cuándo está abierto y quién lo abrió,
 * para que la pantalla lo diga en vez de solo negarse.
 *
 * <p>Los dos datos pueden venir vacíos: si dos aperturas llegan a la vez, la segunda la rechaza el
 * índice único de la base, y en ese punto la transacción ya no puede leer cuál ganó.
 */
@Getter
public class TurnoYaAbiertoException extends ReglaDeNegocioException {

    private final Instant abiertoEn;
    private final UUID abiertoPorId;

    public TurnoYaAbiertoException(Instant abiertoEn, UUID abiertoPorId) {
        super("Ya hay un turno abierto. Se sigue vendiendo en ese.");
        this.abiertoEn = abiertoEn;
        this.abiertoPorId = abiertoPorId;
    }
}
