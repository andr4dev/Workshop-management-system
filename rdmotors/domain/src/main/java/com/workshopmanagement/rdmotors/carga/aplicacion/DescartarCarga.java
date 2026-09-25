package com.workshopmanagement.rdmotors.carga.aplicacion;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.carga.dominio.CargaDeInventario;
import com.workshopmanagement.rdmotors.carga.dominio.puerto.RepositorioCargas;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;

/**
 * CASO DE USO — tirar una pre-carga sin confirmarla. No se borra: queda descartada, con quién y cuándo, y la misma
 * factura se puede volver a subir.
 */
@Transactional
public class DescartarCarga {

    private final RepositorioCargas cargas;
    private final Reloj reloj;

    public DescartarCarga(RepositorioCargas cargas, Reloj reloj) {
        this.cargas = cargas;
        this.reloj = reloj;
    }

    public void ejecutar(UUID id, Actor actor) {
        actor.exigirAdministrador();
        CargaDeInventario carga = cargas.buscarParaModificar(id)
                .orElseThrow(() -> new ReglaDeNegocioException("Esa carga no existe"));
        carga.descartar(actor.id(), reloj.ahora());
        cargas.guardar(carga);
    }
}
