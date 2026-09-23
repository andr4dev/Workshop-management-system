package com.workshopmanagement.rdmotors.compras.dominio.puerto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;

/** PUERTO — persistencia de las cuentas desde las que se transfiere. */
public interface RepositorioCuentas {

    Optional<CuentaPago> buscar(UUID id);

    /** Sin distinguir mayúsculas: "nequi del dueño" encuentra a "Nequi del dueño". */
    Optional<CuentaPago> buscarPorNombre(String nombre);

    List<CuentaPago> activas();

    CuentaPago guardar(CuentaPago cuenta);
}
