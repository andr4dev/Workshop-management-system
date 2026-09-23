package com.workshopmanagement.rdmotors.caja.dominio;

import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Una categoría de gasto: "Arriendo", "Transporte y fletes", "Publicidad" (spec 0006, RF-002 y RF-002a).
 *
 * <p><b>Es una lista y no texto libre</b>, por la misma razón que las cuentas: con texto libre "flete",
 * "Fletes" y "FLETE" serían tres renglones en el reporte.
 *
 * <p>La naturaleza (costo o gasto) se dice al crearla y no cambia: los gastos ya registrados en ella la
 * heredan, y cambiarla movería plata entre la utilidad bruta y la neta de meses ya reportados.
 *
 * <p>Una categoría con gastos no se borra: se desactiva, y sus gastos la siguen nombrando.
 *
 * <p><b>{@code mensual}</b> dice si sus gastos suelen ser de todo el mes (arriendo, nómina): al registrar uno, la
 * casilla "del mes" sale marcada (spec 0007, RF-008a). Es una sugerencia; cada gasto dice lo suyo.
 */
@Entity
@Table(name = "categoria_gasto")
@Getter
public class CategoriaGasto {

    static final int LARGO_MAXIMO = 80;

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false, length = LARGO_MAXIMO)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "naturaleza", nullable = false, updatable = false, length = 10)
    private NaturalezaGasto naturaleza;

    @Column(name = "activa", nullable = false)
    private boolean activa;

    @Column(name = "mensual", nullable = false)
    private boolean mensual;

    protected CategoriaGasto() {
        // JPA
    }

    private CategoriaGasto(String nombre, NaturalezaGasto naturaleza, boolean mensual) {
        this.id = UUID.randomUUID();
        this.nombre = nombre;
        this.naturaleza = naturaleza;
        this.activa = true;
        this.mensual = mensual;
    }

    public static CategoriaGasto nueva(String nombre, NaturalezaGasto naturaleza) {
        return nueva(nombre, naturaleza, false);
    }

    public static CategoriaGasto nueva(String nombre, NaturalezaGasto naturaleza, boolean mensual) {
        if (naturaleza == null) {
            throw new ReglaDeNegocioException("Di si la categoría es un costo o un gasto");
        }
        return new CategoriaGasto(exigirNombre(nombre), naturaleza, mensual);
    }

    /** Que no choque con otra la revisa quien la renombra: aquí no se ven las demás. */
    public void renombrar(String nuevoNombre) {
        this.nombre = exigirNombre(nuevoNombre);
    }

    public void desactivar() {
        this.activa = false;
    }

    /** Cambiarla no toca los gastos ya registrados: cada uno guardó si era del mes. */
    public void marcarMensual(boolean mensual) {
        this.mensual = mensual;
    }

    /**
     * Quita los espacios de las puntas y deja uno solo entre palabras: "Aseo  y cafetería " y "Aseo y
     * cafetería" son la misma.
     */
    public static String normalizar(String nombre) {
        return nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ");
    }

    private static String exigirNombre(String nombre) {
        String limpio = normalizar(nombre);
        if (limpio.isEmpty()) {
            throw new ReglaDeNegocioException("El nombre de la categoría es obligatorio");
        }
        if (limpio.length() > LARGO_MAXIMO) {
            throw new ReglaDeNegocioException(
                    "El nombre de la categoría es muy largo: máximo " + LARGO_MAXIMO + " caracteres");
        }
        return limpio;
    }
}
