package com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.Sesion;

/**
 * El token de la sesión y la cookie que lo lleva (spec 0004, decisión 3 y RF-005).
 *
 * <p><b>Firmado</b> (HS256) con la clave de la tienda: si alguien lo altera, deja de valer. <b>Vence a las 24 horas</b>.
 * Lleva quién es ({@code sub}) y la versión de su sesión ({@code ver}), que el servidor compara con la de la base en
 * cada petición: así desactivar a alguien lo saca aunque el token no haya vencido.
 *
 * <p><b>La página nunca lo ve</b>: va en una cookie {@code HttpOnly} que el código de la página no puede leer, y que el
 * navegador manda solo. {@code SameSite=Strict}: una página de otro sitio no la manda. Sin {@code Secure} por
 * defecto, porque la tablet entra por {@code http://192.168.x.x}; se enciende con
 * {@code rdmotors.seguridad.cookie-segura=true} cuando haya HTTPS.
 */
@Component
public class TokenDeSesion {

    public static final String COOKIE = "rdmotors_sesion";
    public static final String EMISOR = "rdmotors-tienda";
    public static final String VERSION = "ver";

    private final JwtEncoder codificador;
    private final Reloj reloj;
    private final Duration duracion;
    private final boolean cookieSegura;

    TokenDeSesion(ClaveDelToken clave, Reloj reloj,
                  @Value("${rdmotors.seguridad.duracion-sesion:PT24H}") Duration duracion,
                  @Value("${rdmotors.seguridad.cookie-segura:false}") boolean cookieSegura) {
        this.codificador = NimbusJwtEncoder.withSecretKey(clave.clave()).algorithm(MacAlgorithm.HS256).build();
        this.reloj = reloj;
        this.duracion = duracion;
        this.cookieSegura = cookieSegura;
    }

    public String emitir(Sesion sesion) {
        Instant ahora = reloj.ahora();
        JwtClaimsSet datos = JwtClaimsSet.builder()
                .issuer(EMISOR)
                .subject(sesion.actor().id().toString())
                .claim(VERSION, sesion.versionSesion())
                .issuedAt(ahora)
                .expiresAt(ahora.plus(duracion))
                .build();
        return codificador.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), datos))
                .getTokenValue();
    }

    /** La cookie con el token nuevo: al entrar, al crear el primer administrador y al cambiar la contraseña. */
    public ResponseCookie cookie(Sesion sesion) {
        return base(emitir(sesion)).maxAge(duracion).build();
    }

    /** Salir: la cookie se reemplaza por una vacía que ya venció. */
    public ResponseCookie borrar() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(COOKIE, valor).httpOnly(true).secure(cookieSegura).sameSite("Strict").path("/");
    }
}
