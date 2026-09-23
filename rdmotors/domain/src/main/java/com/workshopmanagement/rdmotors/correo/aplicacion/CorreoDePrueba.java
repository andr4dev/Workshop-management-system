package com.workshopmanagement.rdmotors.correo.aplicacion;

import java.time.Instant;

import com.workshopmanagement.rdmotors.correo.dominio.CorreoArmado;

/** El correo que manda el botón *Mandar uno de prueba* (spec 0010, RF-009): si llega, la cuenta de Brevo sirve. */
final class CorreoDePrueba {

    private CorreoDePrueba() {
    }

    static CorreoArmado de(String tienda, String remitente, Instant cuando) {
        String momento = CorreoDelCierre.momento(cuando);
        String asunto = "Prueba de correo · " + tienda;
        String texto = "Si lees esto, los resúmenes del cierre de caja de " + tienda + " van a llegar a este correo.\n\n"
                + "Salió de " + remitente + " el " + momento + ".\n";
        String html = "<!doctype html><html lang=\"es\"><body style=\"font-family:Segoe UI,Roboto,Arial,sans-serif;"
                + "color:#14161a;padding:24px;\"><h2 style=\"margin:0 0 8px;\">" + CorreoDelCierre.esc(tienda)
                + "</h2><p>Si lees esto, los resúmenes del cierre de caja van a llegar a este correo.</p>"
                + "<p style=\"color:#8a919c;font-size:12px;\">Salió de " + CorreoDelCierre.esc(remitente) + " el "
                + CorreoDelCierre.esc(momento) + ".</p></body></html>";
        return new CorreoArmado(asunto, html, texto);
    }
}
