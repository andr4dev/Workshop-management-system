package com.workshopmanagement.rdmotors.compras.dominio;

import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Una cuenta desde la que se hacen transferencias: "Bancolombia ahorros ···4521", "Nequi del dueño".
 *
 * <p><b>Es una lista y no texto libre</b> (spec 0002, decisión 2). Con texto libre, "bancolombia",
 * "Bancolombia" y "BANCOLOMBIA AHORROS" terminan siendo tres cuentas en el reporte y los totales
 * por cuenta dejan de cuadrar con el extracto.
 *
 * <p><b>Se guarda un nombre reconocible, no el número completo.</b> Para el reporte basta con
 * reconocerla, y un número de cuenta completo no tiene por qué vivir en esta base.
 *
 * <p>Una cuenta que ya no se usa se desactiva, no se borra: las compras que la usaron la siguen
 * nombrando.
 */
@Entity
@Table(name = "cuenta_pago")
@Getter
public class CuentaPago {

    static final int LARGO_MAXIMO = 80;

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false, length = LARGO_MAXIMO)
    private String nombre;

    @Column(name = "activa", nullable = false)
    private boolean activa;

    protected CuentaPago() {
        // JPA
    }

    private CuentaPago(UUID id, String nombre) {
        this.id = id;
        this.nombre = nombre;
        this.activa = true;
    }

    public static CuentaPago nueva(String nombre) {
        String limpio = normalizar(nombre);
        if (limpio.isEmpty()) {
            throw new ReglaDeNegocioException("El nombre de la cuenta es obligatorio");
        }
        if (limpio.length() > LARGO_MAXIMO) {
            throw new ReglaDeNegocioException(
                    "El nombre de la cuenta es muy largo: máximo " + LARGO_MAXIMO + " caracteres");
        }
        return new CuentaPago(UUID.randomUUID(), limpio);
    }

    /**
     * Quita los espacios de las puntas y deja uno solo entre palabras. Es lo que hace que
     * "Nequi  del dueño " y "Nequi del dueño" se reconozcan como la misma cuenta.
     */
    public static String normalizar(String nombre) {
        return nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ");
    }

    public void desactivar() {
        this.activa = false;
    }
}
