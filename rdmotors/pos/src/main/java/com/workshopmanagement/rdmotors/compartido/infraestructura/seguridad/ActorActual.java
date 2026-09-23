package com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * En un parámetro de controlador: la persona que hizo la petición, con su rol, sacada de la sesión (spec 0004,
 * RF-007). Reemplaza al encabezado {@code X-Usuario-Id} que mandaba el navegador: <b>el "quién" lo decide el
 * servidor</b>.
 *
 * <p>Va en un parámetro de tipo {@link com.workshopmanagement.rdmotors.compartido.dominio.Actor}, o de tipo
 * {@link com.workshopmanagement.rdmotors.usuarios.aplicacion.Sesion} si hace falta la sesión entera.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ActorActual {
}
