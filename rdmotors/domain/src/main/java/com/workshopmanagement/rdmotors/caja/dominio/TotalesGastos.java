package com.workshopmanagement.rdmotors.caja.dominio;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * Lo gastado con esos filtros (spec 0006, RF-007a): todo, y cuánto de eso salió del cajón. Los anulados
 * no cuentan.
 *
 * <p>{@code delCajon + porFuera = total}, y se calculan por separado en la base para que cuadrar sea algo
 * que se comprueba y no algo que pasa por construcción.
 */
public record TotalesGastos(Dinero total, long gastos, Dinero delCajon, Dinero porFuera) {
}
