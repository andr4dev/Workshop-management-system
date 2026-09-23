package com.workshopmanagement.rdmotors.correo.dominio;

/**
 * Un correo listo para salir: el asunto y su cuerpo en HTML y en texto plano (spec 0010, RF-005).
 *
 * <p>El texto plano no es un adorno: los clientes de correo que no muestran HTML, y los filtros de spam, lo leen.
 */
public record CorreoArmado(String asunto, String html, String texto) {
}
