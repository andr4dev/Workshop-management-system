package com.workshopmanagement.rdmotors.usuarios.dominio.puerto;

import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;
import com.workshopmanagement.rdmotors.usuarios.dominio.Entrada;

/** PUERTO — el registro de entradas al sistema (spec 0004, RF-025). Solo se agrega: no se edita ni se borra. */
public interface RepositorioEntradas {

    void registrar(Entrada entrada);

    /** De la más reciente a la más antigua: lo que el administrador revisa. */
    Pagina<Entrada> ultimas(int pagina, int tamano);
}
