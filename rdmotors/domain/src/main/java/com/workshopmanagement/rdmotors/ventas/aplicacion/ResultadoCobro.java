package com.workshopmanagement.rdmotors.ventas.aplicacion;

import com.workshopmanagement.rdmotors.ventas.dominio.Venta;

/**
 * La venta cobrada, y si ya existía. Un cobro repetido con la misma llave no es un error: devuelve la
 * misma venta, y el borde lo distingue (201 la nueva, 200 la que ya estaba).
 */
public record ResultadoCobro(Venta venta, boolean repetida) {

    static ResultadoCobro nueva(Venta venta) {
        return new ResultadoCobro(venta, false);
    }

    static ResultadoCobro repetida(Venta venta) {
        return new ResultadoCobro(venta, true);
    }
}
