package com.workshopmanagement.rdmotors.respaldo.aplicacion;

import java.time.Instant;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.respaldo.dominio.PoliticaDeRespaldo;
import com.workshopmanagement.rdmotors.respaldo.dominio.Respaldo;
import com.workshopmanagement.rdmotors.respaldo.dominio.puerto.RepositorioRespaldos;

/**
 * CASO DE USO — cómo va el respaldo (spec 0011, RF-011).
 *
 * <p>Es del administrador: al cajero no le sirve de nada y no puede hacer nada con eso.
 */
@Transactional(readOnly = true)
public class ConsultarRespaldos {

    private final RepositorioRespaldos respaldos;
    private final PoliticaDeRespaldo politica;
    private final Reloj reloj;

    public ConsultarRespaldos(RepositorioRespaldos respaldos, PoliticaDeRespaldo politica, Reloj reloj) {
        this.respaldos = respaldos;
        this.politica = politica;
        this.reloj = reloj;
    }

    /** Las veces que se bajó una copia, de la más reciente a la más vieja, con el aviso de arriba. */
    public EstadoDelRespaldo estado(Actor actor) {
        actor.exigirAdministrador();
        List<Respaldo> todos = respaldos.todos();
        Instant ahora = reloj.ahora();
        return new EstadoDelRespaldo(todos, politica.ultimaBuena(todos), politica.hayQueAvisar(todos, ahora),
                politica.diasSinBajar(todos, ahora), politica.diasSinBajarParaAvisar());
    }
}
