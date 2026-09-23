package com.workshopmanagement.rdmotors.compras.aplicacion;

import java.util.List;

import com.workshopmanagement.rdmotors.compras.dominio.Compra;

/**
 * La compra como quedó, y lo que el administrador tiene que saber aunque todo haya salido bien.
 *
 * @param avisos por ejemplo, "el precio de 352B59K se deja en $25.000: cambió después de esa
 *               compra". No son errores: la corrección se aplicó.
 */
public record ResultadoCorreccion(Compra compra, List<String> avisos) {

    public ResultadoCorreccion {
        avisos = List.copyOf(avisos);
    }
}
