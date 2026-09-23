package com.workshopmanagement.rdmotors.correo.aplicacion;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.correo.dominio.puerto.EnviadorDeCorreos;
import com.workshopmanagement.rdmotors.correo.dominio.puerto.RepositorioAjustesDeCorreo;
import com.workshopmanagement.rdmotors.correo.dominio.puerto.RepositorioCorreos;

/** CASO DE USO — cómo van los correos (spec 0010, RF-009). Del administrador. */
@Transactional(readOnly = true)
public class ConsultarCorreos {

    static final int CUANTOS = 20;

    private final RepositorioAjustesDeCorreo ajustes;
    private final RepositorioCorreos correos;
    private final EnviadorDeCorreos enviador;

    public ConsultarCorreos(RepositorioAjustesDeCorreo ajustes, RepositorioCorreos correos, EnviadorDeCorreos enviador) {
        this.ajustes = ajustes;
        this.correos = correos;
        this.enviador = enviador;
    }

    public EstadoDeLosCorreos estado(Actor actor) {
        actor.exigirAdministrador();
        return new EstadoDeLosCorreos(ajustes.obtener().destinatarios().correos(), enviador.estaConfigurado(),
                enviador.loQueFalta(), enviador.remitente(), correos.ultimos(CUANTOS));
    }
}
