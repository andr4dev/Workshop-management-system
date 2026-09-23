package com.workshopmanagement.rdmotors.compartido.dominio.puerto;

import com.workshopmanagement.rdmotors.compartido.dominio.DatosTienda;

/** PUERTO — los datos de la tienda. Una sola fila, que siempre existe. */
public interface RepositorioDatosTienda {

    DatosTienda actuales();

    DatosTienda guardar(DatosTienda datos);
}
