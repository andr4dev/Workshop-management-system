package com.workshopmanagement.rdmotors.carga.aplicacion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.carga.dominio.ResumenCarga;
import com.workshopmanagement.rdmotors.carga.dominio.puerto.RepositorioCargas;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCuentas;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioProveedores;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioCategorias;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;

/**
 * CASO DE USO — ver las cargas y abrir una (spec 0012, H7): la retoma donde se dejó, desde cualquier equipo.
 *
 * <p>Del administrador: la pre-carga está llena de costos (RF-019).
 */
@Transactional(readOnly = true)
public class ConsultarCarga {

    /** Las cerradas que se ven debajo de los borradores: las de las últimas semanas, no la historia entera. */
    static final int CERRADAS_EN_LA_LISTA = 10;

    private final RepositorioCargas cargas;
    private final ArmadoDelDetalle detalle;

    public ConsultarCarga(RepositorioCargas cargas, RepositorioVariantes variantes, RepositorioCategorias categorias,
                          RepositorioProveedores proveedores, RepositorioCuentas cuentas) {
        this.cargas = cargas;
        this.detalle = new ArmadoDelDetalle(variantes, categorias, proveedores, cuentas);
    }

    public Optional<DetalleCarga> detalle(UUID id, Actor actor) {
        actor.exigirAdministrador();
        return cargas.buscar(id).map(detalle::armar);
    }

    public List<ResumenCarga> recientes(Actor actor) {
        actor.exigirAdministrador();
        return cargas.recientes(CERRADAS_EN_LA_LISTA);
    }
}
