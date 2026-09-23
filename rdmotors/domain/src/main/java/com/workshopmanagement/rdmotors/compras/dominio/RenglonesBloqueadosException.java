package com.workshopmanagement.rdmotors.compras.dominio;

import java.util.List;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * Hay renglones que no se pueden revertir porque su repuesto ya tuvo salidas después de la compra
 * (spec 0002, RF-019).
 *
 * <p>Lleva <b>todos</b> los códigos bloqueados, no solo el primero: el administrador tiene que saber
 * de una vez qué renglones no puede tocar, en vez de descubrirlos uno por intento.
 */
public class RenglonesBloqueadosException extends ReglaDeNegocioException {

    private final List<String> codigos;

    public RenglonesBloqueadosException(List<String> codigos) {
        super("No se puede revertir " + String.join(", ", codigos) + ": después de esta compra hubo "
                + "ventas o ajustes de " + (codigos.size() == 1 ? "ese repuesto" : "esos repuestos")
                + ". Con costo promedio no se puede saber si esas unidades siguen en el estante.");
        this.codigos = List.copyOf(codigos);
    }

    public List<String> getCodigos() {
        return codigos;
    }
}
