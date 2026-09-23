package com.workshopmanagement.rdmotors.inventario.dominio;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

import lombok.Getter;

/**
 * "No se permite vender mas unidades de las que hay" — requisito explicito del spec de negocio,
 * seccion 5.
 *
 * <p>Lleva los numeros porque el cajero tiene un cliente enfrente: "no hay stock" lo obliga a ir
 * a mirar; "quedan 2 y pediste 5" le deja cerrar la venta por 2.
 */
@Getter
public class StockInsuficienteException extends ReglaDeNegocioException {

    private final String codigo;
    private final int disponible;
    private final int solicitado;

    public StockInsuficienteException(String codigo, int disponible, int solicitado) {
        super("Stock insuficiente de %s: quedan %d y se pidieron %d"
                .formatted(codigo, disponible, solicitado));
        this.codigo = codigo;
        this.disponible = disponible;
        this.solicitado = solicitado;
    }
}
