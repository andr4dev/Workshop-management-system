package com.workshopmanagement.rdmotors.carga.dominio;

import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

import lombok.Getter;

/**
 * Esa factura ya tiene una carga: sin terminar, o confirmada y con su compra vigente. Subirla otra vez y confirmar
 * las dos entraría la mercancía dos veces. Lleva el id de la que ya está, para ofrecer abrirla.
 */
@Getter
public class FacturaYaCargadaException extends ReglaDeNegocioException {

    private final UUID cargaId;

    public FacturaYaCargadaException(String mensaje, UUID cargaId) {
        super(mensaje);
        this.cargaId = cargaId;
    }
}
