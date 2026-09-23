package com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.Sesion;

/** Llena los parámetros {@link ActorActual} con la sesión que dejó la seguridad (spec 0004, RF-007). */
@Configuration
class ResolutorDelActor implements HandlerMethodArgumentResolver, WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolutores) {
        resolutores.add(this);
    }

    @Override
    public boolean supportsParameter(MethodParameter parametro) {
        Class<?> tipo = parametro.getParameterType();
        return parametro.hasParameterAnnotation(ActorActual.class)
                && (tipo.equals(Actor.class) || tipo.equals(Sesion.class));
    }

    @Override
    public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer modelo, NativeWebRequest peticion,
                                  WebDataBinderFactory binder) {
        Authentication quien = SecurityContextHolder.getContext().getAuthentication();
        if (quien == null || !(quien.getPrincipal() instanceof Sesion sesion)) {
            // No pasa: la seguridad ya exigió la sesión antes de llegar al controlador.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tienes que entrar");
        }
        return parametro.getParameterType().equals(Sesion.class) ? sesion : sesion.actor();
    }
}
