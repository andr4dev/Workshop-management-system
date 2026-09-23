package com.workshopmanagement.rdmotors.respaldo.infraestructura;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Lo poco que se puede cambiar del respaldo sin tocar código (spec 0011): dónde está {@code pg_dump}, en qué
 * carpeta trabajar mientras se saca la copia, y a los cuántos días avisar.
 *
 * <p>Antes tenía el doble de opciones —la carpeta de las copias, la memoria USB, cuántos días guardarlas, a qué
 * hora correr, dónde estaba la clave de las sesiones— porque la copia se quedaba en el computador de la tienda.
 * Ahora <b>el archivo se va con quien lo baja</b> y no hay nada que configurar sobre dónde guardarlo.
 */
@Component
@ConfigurationProperties(prefix = "rdmotors.respaldo")
@Getter
@Setter
public class ConfiguracionDeRespaldo {

    /**
     * Dónde se deja el archivo mientras viaja al navegador. Se borra en cuanto termina de mandarse.
     *
     * <p>La carpeta temporal del sistema sirve tal cual. <b>En la nube esa carpeta es memoria del propio
     * servidor</b>, así que lo que pese la copia se descuenta de la memoria del contenedor mientras dura la
     * descarga: por eso se borra enseguida y por eso el contenedor necesita 512 MB.
     */
    private String carpetaDeTrabajo = System.getProperty("java.io.tmpdir") + "/rdmotors-respaldos";

    /** A los cuántos días sin que nadie baje una copia se le avisa al administrador. */
    private int diasSinBajarParaAvisar = 7;

    /**
     * El programa que saca la copia. Tiene que ser de la <b>misma versión mayor</b> del motor: un cliente 16 contra
     * un servidor 17 se niega, y eso se descubre el día que hace falta la copia.
     *
     * <p>En Windows es la ruta completa; dentro del contenedor basta {@code pg_dump}, que está en el camino.
     */
    private String pgDump = "C:/Program Files/PostgreSQL/17/bin/pg_dump.exe";
}
