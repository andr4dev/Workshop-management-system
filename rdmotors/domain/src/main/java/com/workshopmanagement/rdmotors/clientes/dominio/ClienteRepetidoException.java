package com.workshopmanagement.rdmotors.clientes.dominio;

import java.util.UUID;

import lombok.Getter;

/**
 * Ya hay un cliente con esa cédula (spec 0008, RF-001). No es un error para el cajero: la pantalla usa al que ya
 * existe en vez de crear otro, así la deuda de una persona no queda partida en dos.
 */
@Getter
public class ClienteRepetidoException extends RuntimeException {

    public static final String CODIGO = "CLIENTE_REPETIDO";

    private final UUID existenteId;

    public ClienteRepetidoException(UUID existenteId, String nombreDelExistente) {
        super("Esa cédula es de " + nombreDelExistente);
        this.existenteId = existenteId;
    }
}
