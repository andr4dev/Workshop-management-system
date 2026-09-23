package com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad;

import java.io.IOException;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lee la sesión de la cookie en cada petición (spec 0004, decisión 3): verifica el token con el decodificador de
 * Spring (firma, vencimiento, emisor) y lo convierte en la sesión de una persona con {@link ConvertidorDeSesion}.
 *
 * <p><b>Por qué un filtro propio y no {@code oauth2ResourceServer()} de Spring:</b> ese configurador supone que el
 * token viaja en el encabezado {@code Authorization} y, por eso, <b>apaga la protección CSRF</b> en toda petición que
 * traiga token. Aquí el token viaja en una cookie que el navegador manda solo, y esa exención dejaría pasar justo el
 * ataque que la protección CSRF tiene que parar (lo encontró {@code SeguridadIntegracionTest.csrf}). Con este filtro,
 * la cookie de sesión y el token anti-CSRF se exigen los dos.
 *
 * <p>Un token alterado, vencido, de otra tienda o de una sesión que ya no vale responde 401 en el acto.
 */
final class FiltroDeSesion extends OncePerRequestFilter {

    private final LectorDeCookie lector = new LectorDeCookie();
    private final JwtDecoder decodificador;
    private final ConvertidorDeSesion convertidor;
    private final AuthenticationEntryPoint sinSesion;

    FiltroDeSesion(JwtDecoder decodificador, ConvertidorDeSesion convertidor, AuthenticationEntryPoint sinSesion) {
        this.decodificador = decodificador;
        this.convertidor = convertidor;
        this.sinSesion = sinSesion;
    }

    /**
     * También en el segundo tramo de una respuesta que se manda por partes (spec 0011, RF-010).
     *
     * <p>Bajar la copia de la base entrega el archivo <b>mientras se lee</b>, y eso hace que el servidor atienda la
     * petición en dos tramos: el que decide qué responder y el que va mandando los bytes. Por omisión, un filtro
     * como este solo corre en el primero — <b>pero el guardia de permisos de Spring corre en los dos</b>, y en el
     * segundo se encontraba sin sesión y negaba el acceso. La respuesta ya iba en camino, así que el archivo
     * llegaba bien y el error solo salía en el registro: de los que se descubren tarde y mal.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String token = lector.resolve(peticion);
        if (token != null) {
            try {
                Jwt verificado = decodificador.decode(token);
                AbstractAuthenticationToken sesion = convertidor.convert(verificado);
                SecurityContext contexto = SecurityContextHolder.createEmptyContext();
                contexto.setAuthentication(sesion);
                SecurityContextHolder.setContext(contexto);
            } catch (JwtException | AuthenticationException e) {
                SecurityContextHolder.clearContext();
                sinSesion.commence(peticion, respuesta, new BadCredentialsException("La sesión no vale", e));
                return;
            }
        }
        cadena.doFilter(peticion, respuesta);
    }
}
