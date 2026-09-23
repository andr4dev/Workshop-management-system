package com.workshopmanagement.rdmotors.respaldo.dominio.puerto;

import java.nio.file.Path;

/**
 * PUERTO — quien saca la copia de la base.
 *
 * <p>El dominio no sabe que existe {@code pg_dump}, ni Postgres. Pide una copia en un archivo y recibe cuánto
 * pesó. Hoy lo cumplen dos: el adaptador que llama a {@code pg_dump} en la tienda y el falso de las pruebas; el
 * día que exista la nube (spec 0009, H6) será el tercero.
 */
public interface Volcador {

    /**
     * Saca la copia completa de la base en ese archivo.
     *
     * @return cuánto pesó el archivo, en bytes
     * @throws com.workshopmanagement.rdmotors.respaldo.dominio.RespaldoFallidoException si no se pudo
     */
    long volcar(Path destino);
}
