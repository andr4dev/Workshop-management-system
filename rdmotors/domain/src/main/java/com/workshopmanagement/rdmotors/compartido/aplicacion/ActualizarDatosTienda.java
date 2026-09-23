package com.workshopmanagement.rdmotors.compartido.aplicacion;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.DatosTienda;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioDatosTienda;

/**
 * CASO DE USO — cambiar los datos de la tienda que salen en el comprobante (spec 0003, RF-033).
 *
 * <p>Es un reemplazo completo, como el {@code PUT} que lo expone: mandar dos veces lo mismo deja el
 * mismo estado, así que reintentar tras un corte es seguro sin llave.
 */
@Transactional
public class ActualizarDatosTienda {

    private final RepositorioDatosTienda tienda;

    public ActualizarDatosTienda(RepositorioDatosTienda tienda) {
        this.tienda = tienda;
    }

    public DatosTienda ejecutar(ComandoDatosTienda comando, Actor actor) {
        actor.exigirAdministrador();
        DatosTienda datos = tienda.actuales();
        datos.actualizar(comando.nombreComercial(), comando.nit(), comando.direccion(), comando.telefono(),
                comando.mensajePie());
        return tienda.guardar(datos);
    }

    /** Todos los campos: el que llega vacío queda vacío. */
    public record ComandoDatosTienda(String nombreComercial, String nit, String direccion, String telefono,
                                     String mensajePie) {
    }
}
