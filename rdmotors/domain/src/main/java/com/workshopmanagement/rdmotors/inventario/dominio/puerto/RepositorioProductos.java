package com.workshopmanagement.rdmotors.inventario.dominio.puerto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.workshopmanagement.rdmotors.inventario.dominio.Producto;

/**
 * PUERTO — persistencia del CONCEPTO de repuesto.
 *
 * <p>{@link #buscarPorNombre} existe por una razón de negocio concreta, no por comodidad: es lo que
 * permite <b>reutilizar</b> un concepto en vez de duplicarlo. En la lista real de Jotapartes el
 * mismo filtro de Pulsar NS 200 existe en tres marcas; si cada una creara su propio concepto, la
 * compatibilidad vehicular quedaría escrita tres veces y divergiría, y el cajero que busca
 * "filtro pulsar" recibiría tres resultados sueltos en vez de uno con tres precios.
 *
 * <p>Ver la decisión §4 del spec 0001.
 */
public interface RepositorioProductos {

    Optional<Producto> buscar(UUID id);

    /** Coincidencia parcial, para ofrecerle al administrador conceptos que ya existen. */
    List<Producto> buscarPorNombre(String texto);

    Producto guardar(Producto producto);
}
