package com.workshopmanagement.rdmotors.compras.dominio;

import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

import jakarta.persistence.*;
import lombok.Getter;

/** Importadora Jotapartes y los demas. Confirmado que no es el unico, por eso es tabla propia. */
@Entity
@Table(name = "proveedor")
@Getter
public class Proveedor {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Column(name = "nit", length = 40)
    private String nit;

    @Column(name = "telefono", length = 40)
    private String telefono;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected Proveedor() {
        // JPA
    }

    private Proveedor(UUID id, String nombre, String nit, String telefono) {
        this.id = id;
        this.nombre = nombre;
        this.nit = nit;
        this.telefono = telefono;
        this.activo = true;
    }

    public static Proveedor nuevo(String nombre, String nit, String telefono) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaDeNegocioException("El nombre del proveedor es obligatorio");
        }
        return new Proveedor(UUID.randomUUID(), nombre.trim(), nit, telefono);
    }
}
