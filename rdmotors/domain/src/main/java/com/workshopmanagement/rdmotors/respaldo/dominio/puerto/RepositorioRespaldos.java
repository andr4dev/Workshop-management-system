package com.workshopmanagement.rdmotors.respaldo.dominio.puerto;

import java.util.List;

import com.workshopmanagement.rdmotors.respaldo.dominio.Respaldo;

/** PUERTO — el registro de las copias que se han hecho (spec 0009, H2). */
public interface RepositorioRespaldos {

    Respaldo guardar(Respaldo respaldo);

    /** Todas las que existen, de la más reciente a la más vieja. Son pocas: una por día. */
    List<Respaldo> todos();
}
