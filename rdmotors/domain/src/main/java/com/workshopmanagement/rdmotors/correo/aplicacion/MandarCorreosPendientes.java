package com.workshopmanagement.rdmotors.correo.aplicacion;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.correo.dominio.Correo;
import com.workshopmanagement.rdmotors.correo.dominio.puerto.RepositorioCorreos;

/**
 * CASO DE USO — la tarea de cada minuto: manda los correos que ya se pueden intentar (spec 0010, RF-003).
 *
 * <p>De a pocos por vuelta: un fin de semana sin internet deja varios correos esperando, y al volver no hace falta
 * mandarlos todos en el mismo minuto. Cada uno se toma con candado, así que dos vueltas a la vez nunca se quedan con
 * el mismo.
 */
@Transactional
public class MandarCorreosPendientes {

    static final int POR_VUELTA = 10;

    private final RepositorioCorreos correos;
    private final Cartero cartero;
    private final Reloj reloj;

    public MandarCorreosPendientes(RepositorioCorreos correos, Cartero cartero, Reloj reloj) {
        this.correos = correos;
        this.cartero = cartero;
        this.reloj = reloj;
    }

    /** @return cuántos se intentaron en esta vuelta */
    public int mandar() {
        List<Correo> listos = correos.porMandar(reloj.ahora(), POR_VUELTA);
        for (Correo correo : listos) {
            cartero.intentar(correo);
            correos.guardar(correo);
        }
        return listos.size();
    }
}
