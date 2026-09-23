package com.workshopmanagement.rdmotors.respaldo.aplicacion;

import java.util.List;

import com.workshopmanagement.rdmotors.respaldo.dominio.Respaldo;

/**
 * Cómo va el respaldo, para la pantalla del administrador (spec 0011, RF-011).
 *
 * <p>Ya no dice cuántas copias hay guardadas ni cuánto ocupan: <b>no hay copias guardadas</b>. Cada archivo se fue
 * con quien lo bajó, y desde aquí no se puede saber si todavía lo tiene. Lo único que el sistema sabe de verdad es
 * cuándo fue la última vez que se llevó una, y eso es lo que muestra.
 *
 * @param copias         todas las veces que se intentó, de la más reciente a la más vieja
 * @param ultimaBuena    la última que se bajó bien, o {@code null} si nunca se bajó ninguna
 * @param hayQueAvisar   si el administrador tiene que verlo al entrar
 * @param diasSinBajar   hace cuántos días se bajó la última, o {@code null} si nunca se bajó ninguna
 * @param diasParaAvisar a los cuántos días sin bajar una se enciende el aviso
 */
public record EstadoDelRespaldo(List<Respaldo> copias, Respaldo ultimaBuena, boolean hayQueAvisar,
                                Integer diasSinBajar, int diasParaAvisar) {
}
