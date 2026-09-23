package com.workshopmanagement.rdmotors.correo.aplicacion;

import java.util.List;

import com.workshopmanagement.rdmotors.correo.dominio.Correo;

/**
 * Cómo van los correos, para *Ajustes › Correos* (spec 0010, RF-009).
 *
 * @param destinatarios a quién le llega el resumen del cierre; vacío: está apagado
 * @param listoParaMandar si la llave y el remitente de Brevo están puestos
 * @param loQueFalta      qué falta configurar en el servidor, o {@code null}
 * @param remitente       de quién salen; nunca la llave
 * @param ultimos         los últimos correos, del más nuevo al más viejo
 */
public record EstadoDeLosCorreos(List<String> destinatarios, boolean listoParaMandar, String loQueFalta,
                                 String remitente, List<Correo> ultimos) {
}
