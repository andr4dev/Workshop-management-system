package com.workshopmanagement.rdmotors.compras.infraestructura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCuentas;

import lombok.RequiredArgsConstructor;

/** ADAPTADOR — cuentas de pago sobre Postgres. */
@Repository
@RequiredArgsConstructor
class RepositorioCuentasJpa implements RepositorioCuentas {

    private final CuentasSpringData jpa;

    @Override
    public Optional<CuentaPago> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<CuentaPago> buscarPorNombre(String nombre) {
        return jpa.buscarPorNombre(nombre);
    }

    @Override
    public List<CuentaPago> activas() {
        return jpa.findByActivaTrueOrderByNombreAsc();
    }

    @Override
    public CuentaPago guardar(CuentaPago cuenta) {
        return jpa.save(cuenta);
    }
}

interface CuentasSpringData extends JpaRepository<CuentaPago, UUID> {

    /** Misma comparación que el índice único {@code ux_cuenta_pago_nombre}: {@code lower(nombre)}. */
    @Query("select c from CuentaPago c where lower(c.nombre) = lower(:nombre)")
    Optional<CuentaPago> buscarPorNombre(@Param("nombre") String nombre);

    List<CuentaPago> findByActivaTrueOrderByNombreAsc();
}
