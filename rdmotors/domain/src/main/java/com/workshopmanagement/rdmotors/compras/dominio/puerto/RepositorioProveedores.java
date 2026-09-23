package com.workshopmanagement.rdmotors.compras.dominio.puerto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compras.dominio.Proveedor;

/** PUERTO — persistencia de proveedores. */
public interface RepositorioProveedores {

    Optional<Proveedor> buscar(UUID id);

    List<Proveedor> activos();

    Proveedor guardar(Proveedor proveedor);
}
