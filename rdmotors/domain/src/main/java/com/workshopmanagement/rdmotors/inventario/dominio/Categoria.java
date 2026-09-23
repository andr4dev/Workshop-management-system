package com.workshopmanagement.rdmotors.inventario.dominio;

import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Categoria de repuesto, por sistema del vehiculo: MOTOR, FRENOS, ELECTRICO, CONTROLES Y GUAYAS...
 *
 * <p><b>Es tabla y no enum a proposito:</b> el cliente crea, renombra, reordena y desactiva
 * categorias desde la aplicacion, sin despliegue. Las 16 iniciales son semilla, no lista fija —
 * y eso es lo que hace honesto el plan de "revisar despues de un mes de uso real": revisar no
 * puede requerir un programador.
 *
 * <p>La categoria <b>no sirve para encontrar una pieza</b> — eso lo hace la busqueda por codigo,
 * nombre o modelo. Sirve para navegar y para los reportes. Por eso no necesita precision
 * quirurgica, solo estar balanceada.
 */
@Entity
@Table(name = "categoria")
@Getter
public class Categoria {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false, unique = true, length = 80)
    private String nombre;

    @Column(name = "orden", nullable = false)
    private int orden;

    @Column(name = "activa", nullable = false)
    private boolean activa;

    protected Categoria() {
        // JPA
    }

    private Categoria(UUID id, String nombre, int orden) {
        this.id = id;
        this.nombre = nombre;
        this.orden = orden;
        this.activa = true;
    }

    public static Categoria nueva(String nombre, int orden) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaDeNegocioException("El nombre de la categoria es obligatorio");
        }
        return new Categoria(UUID.randomUUID(), nombre.trim(), orden);
    }

    public void renombrar(String nuevoNombre) {
        if (nuevoNombre == null || nuevoNombre.isBlank()) {
            throw new ReglaDeNegocioException("El nombre de la categoria es obligatorio");
        }
        this.nombre = nuevoNombre.trim();
    }

    /**
     * Desactivar no reclasifica nada: los productos que ya la tienen la conservan y los reportes
     * historicos siguen cuadrando. Por eso se desactiva en vez de borrar — borrar dejaria
     * productos huerfanos y huecos en la historia.
     */
    public void desactivar() {
        this.activa = false;
    }
}
