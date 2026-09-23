package com.workshopmanagement.rdmotors.respaldo.dominio.puerto;

import java.nio.file.Path;

/**
 * PUERTO — el disco, dicho en lo poquísimo que el respaldo necesita desde el spec 0011: una carpeta donde dejar el
 * archivo mientras se baja, y poder borrarlo.
 *
 * <p>Antes hacía más —copiar a la memoria USB, mirar si estaba puesta— porque la copia se quedaba guardada en el
 * computador de la tienda. Ahora el archivo <b>solo pasa por aquí de camino al navegador</b>: se saca, se entrega y
 * se borra. Lo que se guarda es el registro de que se bajó, y eso vive en la base.
 *
 * <p><b>No tiene un "¿existe este archivo?"</b> a propósito: preguntarlo y crear el archivo después deja un hueco
 * por el que se cuelan dos descargas simultáneas. Cada una se lleva su propia ruta al azar y el problema no existe.
 *
 * <p>Lo cumplen el disco de verdad y el falso de las pruebas, que guarda las rutas en un mapa y puede fallar a
 * propósito.
 */
public interface Archivos {

    /** Crea la carpeta si no existe. Falla con {@code RespaldoFallidoException} si no se puede. */
    void asegurarCarpeta(Path carpeta);

    /** Borra el archivo si está. No falla si ya no existe: que alguien lo borrara a mano no es un error. */
    void borrar(Path archivo);
}
