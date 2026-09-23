package com.workshopmanagement.rdmotors.correo.infraestructura;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Lo del correo que va en la configuración del servidor, no en la pantalla (spec 0010, RF-008).
 *
 * <p>La llave de Brevo es un secreto: se lee de la variable de entorno {@code BREVO_API_KEY} y nunca se guarda en la
 * base, ni se devuelve en la API, ni se escribe en un log. El remitente tiene que estar <b>verificado en Brevo</b>
 * (si no, Brevo rechaza el envío y el correo queda fallido con ese mensaje).
 */
@Component
@ConfigurationProperties(prefix = "rdmotors.correo")
@Getter
@Setter
public class ConfiguracionDeCorreo {

    /** La tarea de cada minuto. Apagada en las pruebas: nadie quiere que una prueba mande correos. */
    private boolean habilitado = true;

    /** La dirección que sale como remitente. Vacía: todavía no se configuró. */
    private String remitente = "";

    private String remitenteNombre = "RD MOTORS";

    private Brevo brevo = new Brevo();

    @Getter
    @Setter
    public static class Brevo {

        /** Vacía: todavía no se configuró. Se pone con la variable de entorno BREVO_API_KEY. */
        private String apiKey = "";

        /** La de Brevo; se cambia solo en las pruebas, para hablarle a un servidor de mentira. */
        private String url = "https://api.brevo.com/v3/smtp/email";
    }
}
