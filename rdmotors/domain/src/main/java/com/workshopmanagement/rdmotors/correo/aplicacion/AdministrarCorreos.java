package com.workshopmanagement.rdmotors.correo.aplicacion;

import java.util.List;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.correo.dominio.AjustesDeCorreo;
import com.workshopmanagement.rdmotors.correo.dominio.Correo;
import com.workshopmanagement.rdmotors.correo.dominio.Destinatarios;
import com.workshopmanagement.rdmotors.correo.dominio.puerto.RepositorioAjustesDeCorreo;
import com.workshopmanagement.rdmotors.correo.dominio.puerto.RepositorioCorreos;

/**
 * CASO DE USO — lo que el administrador hace en *Ajustes › Correos* (spec 0010, RF-007 y RF-009): a quién le llega,
 * mandar uno de prueba y reintentar uno que falló. Todo es del administrador: el cajero no decide a quién le llega el
 * resumen de su propio turno.
 */
@Transactional
public class AdministrarCorreos {

    private final RepositorioAjustesDeCorreo ajustes;
    private final RepositorioCorreos correos;
    private final Cartero cartero;
    private final Reloj reloj;

    public AdministrarCorreos(RepositorioAjustesDeCorreo ajustes, RepositorioCorreos correos, Cartero cartero,
                              Reloj reloj) {
        this.ajustes = ajustes;
        this.correos = correos;
        this.cartero = cartero;
        this.reloj = reloj;
    }

    public Destinatarios cambiarDestinatarios(List<String> escritos, Actor actor) {
        actor.exigirAdministrador();
        Destinatarios nuevos = Destinatarios.de(escritos);
        AjustesDeCorreo actuales = ajustes.obtener();
        actuales.cambiarDestinatarios(nuevos, actor.id(), reloj.ahora());
        ajustes.guardar(actuales);
        return nuevos;
    }

    /**
     * Manda uno de prueba <b>ya</b>, dentro de la petición: el administrador quiere saber en el momento si la cuenta
     * de Brevo sirve. Si no salió, el correo queda en la cola como cualquier otro y dice por qué.
     */
    public Correo probar(Actor actor) {
        actor.exigirAdministrador();
        Destinatarios para = ajustes.obtener().destinatarios();
        if (!para.hayAlguno()) {
            throw new ReglaDeNegocioException("Primero escribe a qué correos llega el resumen del cierre");
        }
        Correo prueba = Correo.dePrueba(para, reloj.ahora());
        cartero.intentar(prueba);
        return correos.guardar(prueba);
    }

    /** Uno que falló, después de arreglar lo que faltaba: vuelve a la cola y sale en el próximo minuto. */
    public Correo reintentar(UUID correoId, Actor actor) {
        actor.exigirAdministrador();
        Correo correo = correos.buscarParaModificar(correoId)
                .orElseThrow(() -> new ReglaDeNegocioException("Ese correo no existe"));
        correo.reintentar(reloj.ahora());
        return correos.guardar(correo);
    }
}
