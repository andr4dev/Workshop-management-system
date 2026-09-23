package com.workshopmanagement.rdmotors.usuarios.infraestructura;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.Contrasenas;

/**
 * ADAPTADOR — el hash de las contraseñas con Spring Security (spec 0004, RF-002).
 *
 * <p>El codificador delegado guarda el algoritmo en el hash ({@code {bcrypt}…}): BCrypt hoy, lento a propósito, y
 * si mañana se cambia de algoritmo, los hashes viejos se siguen leyendo.
 */
@Component
class ContrasenasSpring implements Contrasenas {

    private final PasswordEncoder codificador = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Override
    public String hash(String contrasena) {
        return codificador.encode(contrasena);
    }

    @Override
    public boolean coincide(String contrasena, String hash) {
        return codificador.matches(contrasena, hash);
    }
}
