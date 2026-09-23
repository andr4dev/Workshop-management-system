package com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Saca el token de la cookie de sesión (spec 0004, decisión 3): la página no lo maneja, el navegador lo manda solo.
 *
 * <p><b>En las rutas públicas no lo lee.</b> Si lo leyera, una cookie vencida haría fallar la petición antes de llegar
 * a ella, y con una sesión vieja no se podría ni volver a entrar.
 */
final class LectorDeCookie {

    String resolve(HttpServletRequest peticion) {
        if (esPublica(peticion)) {
            return null;
        }
        Cookie[] cookies = peticion.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (TokenDeSesion.COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** Entrar, salir y lo de la instalación: se piden sin sesión, o con una que ya no vale. */
    static boolean esPublica(HttpServletRequest peticion) {
        String ruta = peticion.getRequestURI().substring(peticion.getContextPath().length());
        String metodo = peticion.getMethod();
        return ("/api/sesion".equals(ruta) && ("POST".equals(metodo) || "DELETE".equals(metodo)))
                || ruta.equals("/api/instalacion") || ruta.startsWith("/api/instalacion/");
    }
}
