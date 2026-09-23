package com.workshopmanagement.rdmotors.clientes.dominio;

/**
 * Los datos de un cliente como se escriben en la pantalla (spec 0008, RF-001). Sin limpiar: {@link Cliente} decide
 * qué es obligatorio y cómo se compara.
 *
 * @param documento cédula o NIT, con o sin puntos y guiones
 */
public record DatosCliente(String nombre, String documento, String celular, String direccion, String nota) {
}
