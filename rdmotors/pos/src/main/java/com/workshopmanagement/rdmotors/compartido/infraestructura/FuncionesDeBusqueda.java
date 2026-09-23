package com.workshopmanagement.rdmotors.compartido.infraestructura;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

import com.workshopmanagement.rdmotors.compartido.dominio.TextoDeBusqueda;

/**
 * Registra en las consultas la función {@code sin_tildes(texto)}: minúsculas y sin tildes, con la
 * <b>misma tabla de letras</b> que {@link TextoDeBusqueda#normalizar}. Los buscadores comparan
 * {@code sin_tildes(columna) like :texto} con el texto ya normalizado en Java, y los dos lados quitan
 * tildes igual por construcción.
 *
 * <p>{@code translate} es de Postgres de fábrica: no hace falta instalar {@code unaccent}, que pide
 * permisos de superusuario y además no coincide letra por letra con lo que haría Java.
 *
 * <p>Hibernate la carga por {@code META-INF/services/org.hibernate.boot.model.FunctionContributor}.
 */
public class FuncionesDeBusqueda implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions funciones) {
        funciones.getFunctionRegistry().registerPattern(
                "sin_tildes",
                "translate(lower(?1), '" + TextoDeBusqueda.CON_TILDE + "', '" + TextoDeBusqueda.SIN_TILDE + "')",
                funciones.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.STRING));
    }
}
