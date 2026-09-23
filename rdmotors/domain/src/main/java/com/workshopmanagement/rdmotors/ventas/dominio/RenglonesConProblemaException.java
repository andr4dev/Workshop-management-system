package com.workshopmanagement.rdmotors.ventas.dominio;

import java.util.List;
import java.util.stream.Collectors;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

import lombok.Getter;

/**
 * Uno o más renglones no se pueden cobrar: no existe, está inactivo, no tiene precio, el precio cambió
 * o no hay stock (spec 0003, RF-008 y RF-009).
 *
 * <p><b>Trae todos los problemas, no el primero.</b> Se revisan todos los renglones antes de mover
 * nada: si el cajero corrige uno y al reintentar le sale otro, pierde el tiempo del cliente dos veces.
 */
@Getter
public class RenglonesConProblemaException extends ReglaDeNegocioException {

    private final List<ProblemaDeRenglon> problemas;

    public RenglonesConProblemaException(List<ProblemaDeRenglon> problemas) {
        super(mensaje(problemas));
        this.problemas = List.copyOf(problemas);
    }

    private static String mensaje(List<ProblemaDeRenglon> problemas) {
        return "No se puede cobrar así: " + problemas.stream()
                .map(RenglonesConProblemaException::frase)
                .collect(Collectors.joining("; "));
    }

    private static String frase(ProblemaDeRenglon p) {
        return switch (p.tipo()) {
            case NO_EXISTE -> "un repuesto de la venta ya no existe";
            case INACTIVO -> p.codigo() + " está desactivado";
            case SIN_PRECIO -> p.codigo() + " no tiene precio de venta: fíjalo en su ficha";
            case PRECIO_CAMBIADO -> "el precio de " + p.codigo() + " cambió de "
                    + Dinero.de(p.precioVisto()).enPesos() + " a " + Dinero.de(p.precioActual()).enPesos();
            case SIN_STOCK -> p.disponible() == 0
                    ? "ya no quedan unidades de " + p.codigo()
                    : "de " + p.codigo() + " solo quedan " + p.disponible() + " y se pidieron " + p.pedido();
        };
    }
}
