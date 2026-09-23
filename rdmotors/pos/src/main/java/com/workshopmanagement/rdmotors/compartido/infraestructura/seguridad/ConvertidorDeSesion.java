package com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad;

import java.util.List;
import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import com.workshopmanagement.rdmotors.usuarios.aplicacion.ConsultarSesion;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.Sesion;

import lombok.RequiredArgsConstructor;

/**
 * De un token ya verificado (firma, vencimiento, emisor) a la sesión de una persona (spec 0004, RF-015).
 *
 * <p>La firma dice que el token lo emitió este servidor; no dice si la persona sigue activa. Por eso se consulta la
 * base en cada petición: si la desactivaron o le restablecieron la contraseña, la versión ya no coincide y el token
 * deja de valer aunque no haya vencido.
 *
 * <p>Quien todavía tiene que cambiar su contraseña recibe solo {@value #CONTRASENA_TEMPORAL}: puede ver quién es,
 * cambiarla y salir; nada más (RF-014).
 */
@Component
@RequiredArgsConstructor
class ConvertidorDeSesion implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String SESION_COMPLETA = "SESION_COMPLETA";
    static final String CONTRASENA_TEMPORAL = "CONTRASENA_TEMPORAL";

    private final ConsultarSesion consultarSesion;

    @Override
    public AbstractAuthenticationToken convert(Jwt token) {
        UUID usuarioId;
        long version;
        try {
            usuarioId = UUID.fromString(token.getSubject());
            version = ((Number) token.getClaim(TokenDeSesion.VERSION)).longValue();
        } catch (RuntimeException e) {
            throw new InvalidBearerTokenException("La sesión no es válida");
        }
        Sesion sesion = consultarSesion.vigente(usuarioId, version)
                .orElseThrow(() -> new InvalidBearerTokenException("La sesión ya no vale: entra de nuevo"));
        return new SesionAutenticada(sesion, token);
    }

    /** La autenticación de Spring con la sesión adentro: el principal es la {@link Sesion}. */
    static final class SesionAutenticada extends AbstractAuthenticationToken {

        private final Sesion sesion;
        private final transient Jwt token;

        SesionAutenticada(Sesion sesion, Jwt token) {
            super(List.of(new SimpleGrantedAuthority(
                    sesion.debeCambiarContrasena() ? CONTRASENA_TEMPORAL : SESION_COMPLETA)));
            this.sesion = sesion;
            this.token = token;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return token;
        }

        @Override
        public Sesion getPrincipal() {
            return sesion;
        }
    }
}
