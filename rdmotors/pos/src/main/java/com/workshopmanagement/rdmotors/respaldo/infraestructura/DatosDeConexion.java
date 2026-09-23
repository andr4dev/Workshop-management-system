package com.workshopmanagement.rdmotors.respaldo.infraestructura;

import java.net.URI;

import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.stereotype.Component;

import com.workshopmanagement.rdmotors.respaldo.dominio.RespaldoFallidoException;

/**
 * A qué base se conecta {@code pg_dump}: exactamente la misma a la que está conectada la aplicación.
 *
 * <p><b>Se pregunta por la conexión de verdad</b> ({@code JdbcConnectionDetails}), no por las propiedades del
 * archivo. Parece lo mismo y no lo es: en las pruebas la base la pone el contenedor, y leer
 * {@code spring.datasource.url} devolvía la de la tienda — el respaldo terminaba copiando <b>otra base</b>. En
 * producción las dos coinciden, así que no se pierde nada y se gana no poder equivocarse.
 */
@Component
class DatosDeConexion {

    private final String host;
    private final int puerto;
    private final String base;
    private final String usuario;
    private final String contrasena;

    DatosDeConexion(JdbcConnectionDetails conexion) {
        String url = conexion.getJdbcUrl();
        String usuario = conexion.getUsername();
        String contrasena = conexion.getPassword();
        // jdbc:postgresql://localhost:5433/rdmotors → se le quita el "jdbc:" para que URI sepa leerlo.
        if (!url.startsWith("jdbc:postgresql://")) {
            throw new RespaldoFallidoException("El respaldo solo sabe copiar Postgres, y la base es: " + url);
        }
        URI uri = URI.create(url.substring("jdbc:".length()));
        this.host = uri.getHost() == null ? "localhost" : uri.getHost();
        this.puerto = uri.getPort() == -1 ? 5432 : uri.getPort();
        String camino = uri.getPath() == null ? "" : uri.getPath();
        int interrogante = camino.indexOf('?');
        this.base = (interrogante >= 0 ? camino.substring(0, interrogante) : camino).replaceFirst("^/", "");
        this.usuario = usuario;
        this.contrasena = contrasena;
    }

    String host() {
        return host;
    }

    int puerto() {
        return puerto;
    }

    String base() {
        return base;
    }

    String usuario() {
        return usuario;
    }

    String contrasena() {
        return contrasena;
    }
}
