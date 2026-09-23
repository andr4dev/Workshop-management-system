package com.workshopmanagement.rdmotors.inventario.dominio;

import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * El CONCEPTO de repuesto: "FILTRO ACEITE". No se vende directamente — se vende una
 * {@link Variante} suya.
 *
 * <p>La compatibilidad vehicular cuelga de aqui y no de la variante: un filtro sirve para una
 * Pulsar NS 200 sin importar si lo fabrico INOKI o FACTORY. Colgarla de la variante obligaria a
 * repetir la misma lista de modelos en cada marca, y esas copias divergen.
 *
 * <p>{@code aplicacionOriginal} guarda el texto crudo del proveedor, verbatim y sin
 * sobrescribir nunca: es la fuente para auditar el emparejador de modelos y para resolver a mano
 * lo que no cruce. En el catalogo de Jotapartes se ve asi:
 * {@code "PULSAR NS 200/FI/AS 200-DUKE 200/3"}.
 *
 * <p><b>Todo concepto nuevo nace con categoría, y corregirlo no la quita</b> (spec 0005, RF-009 y
 * RF-011). Es como se encuentra en el catálogo del mostrador: un repuesto sin categoría solo aparece
 * escribiendo. Los conceptos creados antes de esta regla pueden no tenerla —la columna admite nulos y
 * JPA no pasa por {@link #nuevo}—, pero al corregirlos hay que escogerla.
 */
@Entity
@Table(name = "producto")
@Getter
public class Producto {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    /** Texto crudo del proveedor. Nunca se sobrescribe con el resultado del parseo. */
    @Column(name = "aplicacion_original", length = 500)
    private String aplicacionOriginal;

    /** Cubre los ~593 registros marcados VARIAS o UNIVERSAL en el catalogo. */
    @Column(name = "es_universal", nullable = false)
    private boolean esUniversal;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected Producto() {
        // JPA
    }

    private Producto(UUID id, String nombre, Categoria categoria, String aplicacionOriginal) {
        this.id = id;
        this.nombre = nombre;
        this.categoria = categoria;
        this.aplicacionOriginal = aplicacionOriginal;
        this.esUniversal = false;
        this.activo = true;
    }

    /** El mensaje, uno solo, para crear y para corregir. */
    public static final String SIN_CATEGORIA =
            "Escoge la categoría: es como se encuentra en el catálogo del mostrador";

    public static Producto nuevo(String nombre, Categoria categoria, String aplicacionOriginal) {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaDeNegocioException("El nombre del producto es obligatorio");
        }
        exigirCategoria(categoria);
        return new Producto(UUID.randomUUID(), nombre.trim(), categoria, aplicacionOriginal);
    }

    private static void exigirCategoria(Categoria categoria) {
        if (categoria == null) {
            throw new ReglaDeNegocioException(SIN_CATEGORIA);
        }
    }

    /**
     * Corrige el concepto.
     *
     * <p><b>Esto afecta a TODAS las marcas que cuelgan de el.</b> Es lo correcto —es el mismo
     * repuesto, con un nombre mejor escrito— pero quien lo llame tiene que avisarlo: el
     * administrador cree estar corrigiendo una fila y esta corrigiendo tres.
     */
    public void corregir(String nuevoNombre, Categoria nuevaCategoria, String nuevaAplicacion) {
        if (nuevoNombre == null || nuevoNombre.isBlank()) {
            throw new ReglaDeNegocioException("El nombre del producto es obligatorio");
        }
        exigirCategoria(nuevaCategoria);
        this.nombre = nuevoNombre.trim();
        this.categoria = nuevaCategoria;
        this.aplicacionOriginal = nuevaAplicacion;
    }
}
