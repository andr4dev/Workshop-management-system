package com.workshopmanagement.rdmotors.correo.infraestructura;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.workshopmanagement.rdmotors.correo.dominio.CorreoArmado;
import com.workshopmanagement.rdmotors.correo.dominio.Destinatarios;
import com.workshopmanagement.rdmotors.correo.dominio.EnvioFallidoException;
import com.workshopmanagement.rdmotors.correo.dominio.puerto.EnviadorDeCorreos;

/**
 * ADAPTADOR — el correo sale por la API HTTP de Brevo, como en el car-wash
 * ({@code CAR-WASH-SYSTEM/backend/…/EmailServiceImpl.java:29,44-47,100-117}): {@code POST /v3/smtp/email} con la llave
 * en la cabecera {@code api-key}.
 *
 * <p><b>Lo que decide todo es si el error se arregla solo.</b> Sin internet, Brevo caído (5xx) o demasiados envíos
 * (429): basta con esperar. Una llave inválida (401) o un remitente sin verificar (400): no, y reintentar cada hora
 * sería insistir contra una pared.
 *
 * <p>La llave viaja en una cabecera y nunca en un mensaje de error: lo que se guarda como error es lo que respondió
 * Brevo, que no la incluye.
 */
@Component
class EnviadorBrevo implements EnviadorDeCorreos {

    private static final int LARGO_DEL_ERROR = 400;

    private final ConfiguracionDeCorreo configuracion;
    private final RestClient cliente;

    EnviadorBrevo(ConfiguracionDeCorreo configuracion) {
        this.configuracion = configuracion;
        // Plazos cortos: un correo que no sale en 20 segundos se reintenta después, sin trabar la tarea.
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofSeconds(20));
        this.cliente = RestClient.builder().requestFactory(fabrica).build();
    }

    @Override
    public boolean estaConfigurado() {
        return loQueFalta() == null;
    }

    @Override
    public String loQueFalta() {
        List<String> falta = new ArrayList<>();
        if (vacio(configuracion.getBrevo().getApiKey())) {
            falta.add("la llave de Brevo (variable de entorno BREVO_API_KEY)");
        }
        if (vacio(configuracion.getRemitente())) {
            falta.add("el remitente (rdmotors.correo.remitente, verificado en Brevo)");
        }
        return falta.isEmpty() ? null : "Falta " + String.join(" y ", falta);
    }

    @Override
    public String remitente() {
        return vacio(configuracion.getRemitente()) ? null
                : configuracion.getRemitenteNombre() + " <" + configuracion.getRemitente() + ">";
    }

    @Override
    public String enviar(Destinatarios para, CorreoArmado correo) {
        PeticionBrevo peticion = new PeticionBrevo(
                new Direccion(configuracion.getRemitente(), configuracion.getRemitenteNombre()),
                para.correos().stream().map(c -> new Direccion(c, null)).toList(),
                correo.asunto(), correo.html(), correo.texto());
        try {
            RespuestaBrevo respuesta = cliente.post()
                    .uri(configuracion.getBrevo().getUrl())
                    .header("api-key", configuracion.getBrevo().getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(peticion)
                    .retrieve()
                    .body(RespuestaBrevo.class);
            return respuesta == null ? null : respuesta.messageId();
        } catch (RestClientResponseException e) {
            HttpStatusCode estado = e.getStatusCode();
            boolean seArreglaSola = estado.is5xxServerError() || estado.value() == 429;
            throw new EnvioFallidoException("Brevo respondió " + estado.value() + ": "
                    + recortar(e.getResponseBodyAsString()), seArreglaSola, e);
        } catch (ResourceAccessException e) {
            // Sin internet, sin DNS, o Brevo no contestó a tiempo: se arregla esperando.
            throw new EnvioFallidoException("No se pudo hablar con Brevo (¿hay internet?): " + e.getMessage(), true, e);
        }
    }

    /** Sin los campos vacíos: Brevo no quiere un {@code "name": null}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PeticionBrevo(Direccion sender, List<Direccion> to, String subject, String htmlContent,
                         String textContent) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Direccion(String email, String name) {
    }

    record RespuestaBrevo(String messageId) {
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static String recortar(String texto) {
        String limpio = texto == null ? "" : texto.strip();
        return limpio.length() <= LARGO_DEL_ERROR ? limpio : limpio.substring(0, LARGO_DEL_ERROR);
    }
}
