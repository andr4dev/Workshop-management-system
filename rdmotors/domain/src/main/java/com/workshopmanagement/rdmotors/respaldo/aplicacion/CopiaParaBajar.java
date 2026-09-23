package com.workshopmanagement.rdmotors.respaldo.aplicacion;

import java.nio.file.Path;

import com.workshopmanagement.rdmotors.respaldo.dominio.Respaldo;

/**
 * Una copia recién sacada, esperando a que la manden (spec 0011, RF-010).
 *
 * <p>Vive muy poco: desde que {@link BajarRespaldo} termina hasta que quien la recibe acaba de mandarla al
 * navegador y <b>borra el archivo</b>. Desde aquí no se sabe cuándo acabó de viajar, así que borrarlo no es tarea
 * del caso de uso.
 *
 * @param archivo  dónde quedó, en la carpeta de trabajo del servidor
 * @param nombre   cómo se va a llamar en el computador de quien la baja
 * @param bytes    lo que pesa
 * @param registro la fila que quedó guardada diciendo que se bajó
 */
public record CopiaParaBajar(Path archivo, String nombre, long bytes, Respaldo registro) {
}
