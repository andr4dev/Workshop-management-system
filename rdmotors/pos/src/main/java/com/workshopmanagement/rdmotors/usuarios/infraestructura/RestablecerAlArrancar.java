package com.workshopmanagement.rdmotors.usuarios.infraestructura;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.RestablecerDesdeLaTienda;

/**
 * El único administrador olvidó su contraseña (spec 0004, RF-019). Con el servidor apagado, en el computador de la
 * tienda se arranca con {@code --rdmotors.restablecer-administrador=<usuario>}: le pone una contraseña temporal, la
 * muestra en esta consola y se apaga. Al entrar con ella, la cambia. El paso a paso está en {@code docs/DESARROLLO.md}.
 *
 * <p>Sin la propiedad, esta clase ni se crea: el arranque de todos los días no pasa por aquí.
 */
@Component
@ConditionalOnProperty(name = "rdmotors.restablecer-administrador")
class RestablecerAlArrancar implements ApplicationRunner {

    private final RestablecerDesdeLaTienda restablecer;
    private final ConfigurableApplicationContext contexto;
    private final String usuario;

    RestablecerAlArrancar(RestablecerDesdeLaTienda restablecer, ConfigurableApplicationContext contexto,
                          @Value("${rdmotors.restablecer-administrador}") String usuario) {
        this.restablecer = restablecer;
        this.contexto = contexto;
        this.usuario = usuario;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        int salida = 0;
        try {
            String temporal = restablecer.ejecutar(usuario);
            System.out.println("""

                    ════════════════════════════════════════════════════════════
                      RD MOTORS · contraseña restablecida
                      Usuario:             %s
                      Contraseña temporal: %s
                      Entra con ella: el sistema te pide cambiarla de una vez.
                    ════════════════════════════════════════════════════════════
                    """.formatted(usuario, temporal));
        } catch (ReglaDeNegocioException e) {
            System.out.println("\nNo se restableció: " + e.getMessage() + "\n");
            salida = 1;
        }
        int codigo = salida;
        System.exit(SpringApplication.exit(contexto, () -> codigo));
    }
}
