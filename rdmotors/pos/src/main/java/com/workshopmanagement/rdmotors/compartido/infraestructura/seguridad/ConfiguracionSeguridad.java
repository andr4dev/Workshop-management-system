package com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad;

import java.io.IOException;
import java.time.Instant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Quién entra y qué exige cada ruta (spec 0004, fase 1).
 *
 * <ul>
 *   <li><b>Sin sesión no se ve ni se hace nada</b> (RF-004): toda la API exige haber entrado, salvo entrar, salir y
 *       crear el primer administrador.</li>
 *   <li><b>La sesión es el token firmado de la cookie</b> ({@link TokenDeSesion}), que lee {@link FiltroDeSesion};
 *       sin sesiones en el servidor.</li>
 *   <li><b>Quien debe cambiar su contraseña</b> solo puede ver quién es, cambiarla y salir (RF-014).</li>
 *   <li><b>Contra peticiones falsificadas</b>: como la cookie viaja sola, las escrituras llevan además el token
 *       anti-CSRF, que la página lee de la cookie {@code XSRF-TOKEN} y manda en {@code X-XSRF-TOKEN}.</li>
 * </ul>
 *
 * <p>Los roles <b>no</b> se ponen aquí: lo que es del administrador lo bloquea cada caso de uso (spec 0004, RF-010),
 * para que haya un solo sitio que mirar.
 */
@Configuration
class ConfiguracionSeguridad {

    @Bean
    JwtDecoder decodificadorDeSesion(ClaveDelToken clave) {
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(clave.clave())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Firma, vencimiento (con un minuto de tolerancia de reloj) y que lo haya emitido esta tienda.
        decodificador.setJwtValidator(JwtValidators.createDefaultWithIssuer(TokenDeSesion.EMISOR));
        return decodificador;
    }

    @Bean
    SecurityFilterChain seguridad(HttpSecurity http, JwtDecoder decodificador, ConvertidorDeSesion convertidor)
            throws Exception {
        http
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Las tareas quedan fuera de la revisión anti-CSRF: quien las llama es una máquina sin navegador
                // y sin cookie, así que no puede tener ese token. Lo que la protege es su llave (spec 0011, RF-007).
                .csrf(c -> c.spa().ignoringRequestMatchers("/api/tareas/**"))
                // La cookie XSRF-TOKEN sale desde la primera respuesta, para que la página pueda escribir enseguida.
                .addFilterAfter(new CargarTokenCsrf(), CsrfFilter.class)
                // La sesión de la cookie, con un filtro propio: ver FiltroDeSesion (por qué no oauth2ResourceServer).
                .addFilterAfter(new FiltroDeSesion(decodificador, convertidor, ConfiguracionSeguridad::sinSesion),
                        CsrfFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/sesion").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/sesion").permitAll()
                        .requestMatchers("/api/instalacion", "/api/instalacion/**").permitAll()
                        // ¿Está vivo? y el reloj de afuera (spec 0011, RF-006 y RF-007). Públicas a propósito: las
                        // llama una máquina que no puede entrar. La de tareas se defiende con su llave, no con la
                        // sesión; la de salud no tiene nada que defender porque no dice nada.
                        .requestMatchers(HttpMethod.GET, "/api/salud").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/tareas/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/sesion").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/sesion/contrasena").authenticated()
                        .requestMatchers("/api/**").hasAuthority(ConvertidorDeSesion.SESION_COMPLETA)
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(ConfiguracionSeguridad::sinSesion)
                        .accessDeniedHandler(noPermitido()))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /** 401 en JSON, con el código que la pantalla usa para llevar a *Entrar*. */
    private static void sinSesion(HttpServletRequest peticion, HttpServletResponse respuesta,
                                  AuthenticationException e) throws IOException {
        escribir(respuesta, HttpServletResponse.SC_UNAUTHORIZED, "SIN_SESION", "Tienes que entrar");
    }

    /** 403 en JSON: el token anti-CSRF, la contraseña por cambiar o algo que el rol no puede. */
    private static AccessDeniedHandler noPermitido() {
        return (peticion, respuesta, e) -> {
            if (e instanceof CsrfException) {
                escribir(respuesta, HttpServletResponse.SC_FORBIDDEN, "CSRF",
                        "La página necesita recargarse. Vuelve a intentarlo");
                return;
            }
            Authentication quien = SecurityContextHolder.getContext().getAuthentication();
            boolean temporal = quien != null && quien.getAuthorities().stream()
                    .anyMatch(a -> ConvertidorDeSesion.CONTRASENA_TEMPORAL.equals(a.getAuthority()));
            if (temporal) {
                escribir(respuesta, HttpServletResponse.SC_FORBIDDEN, "DEBE_CAMBIAR_CONTRASENA",
                        "Primero cambia tu contraseña");
            } else {
                escribir(respuesta, HttpServletResponse.SC_FORBIDDEN, "NO_PERMITIDO",
                        "No permitido: es del administrador");
            }
        };
    }

    /** Los mensajes son fijos y sin comillas: se escriben a mano, sin pasar por el convertidor de JSON. */
    private static void escribir(HttpServletResponse respuesta, int estado, String codigo, String mensaje)
            throws IOException {
        respuesta.setStatus(estado);
        respuesta.setContentType("application/json;charset=UTF-8");
        respuesta.getWriter().write("{\"mensaje\":\"" + mensaje + "\",\"codigo\":\"" + codigo
                + "\",\"momento\":\"" + Instant.now() + "\"}");
    }

    /**
     * El token anti-CSRF se genera cuando alguien lo pide. Pedirlo en cada petición hace que la cookie
     * {@code XSRF-TOKEN} llegue con la primera respuesta, antes de la primera escritura.
     */
    private static final class CargarTokenCsrf extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                        FilterChain cadena) throws ServletException, IOException {
            CsrfToken token = (CsrfToken) peticion.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            cadena.doFilter(peticion, respuesta);
        }
    }
}
