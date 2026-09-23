package com.workshopmanagement.rdmotors.caja.dominio;

import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;

import lombok.Getter;

/**
 * Un cajero quiso operar en un turno que abrió otra persona (spec 0004, decisión 2): vender, anular, registrar un
 * gasto o un retiro, cerrarlo o escribirle observaciones. El cajero responde por la plata de su turno, así que en
 * el de otro no mueve nada; el administrador sí puede.
 *
 * <p>Lleva quién lo abrió para que la respuesta diga su nombre, que el turno no conoce: lo pone el adaptador.
 */
@Getter
public class TurnoAjenoException extends NoPermitidoException {

    public static final String CODIGO = "TURNO_AJENO";

    private final UUID abiertoPorId;

    public TurnoAjenoException(UUID abiertoPorId) {
        super(mensajePara(null));
        this.abiertoPorId = abiertoPorId;
    }

    /** Sin suponer si es "él" o "ella": el sistema no lo sabe. */
    public static String mensajePara(String nombre) {
        return nombre == null
                ? "El turno abierto es de otra persona: lo cierra quien lo abrió o un administrador"
                : "El turno abierto es de " + nombre + ": lo cierra " + nombre + " o un administrador";
    }
}
