package com.workshopmanagement.rdmotors.inventario.aplicacion;

import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * Datos para dar de alta un repuesto vendible.
 *
 * <p>El concepto llega de una de dos formas, nunca de las dos: <b>o</b> el id de uno que ya existe
 * (lo normal cuando es una marca nueva de algo que ya se vende), <b>o</b> el nombre para crearlo.
 * Esa bifurcación es la decisión §4 del spec 0001 puesta en el tipo: quien construya este comando
 * tiene que haber decidido antes si reutiliza o crea.
 */
public record ComandoCrearRepuesto(
        UUID productoId,
        String nombreProducto,
        UUID categoriaId,
        String aplicacionOriginal,
        String codigo,
        String marcaRepuesto,
        Dinero precio,
        int stockMinimo) {

    public ComandoCrearRepuesto {
        boolean reutiliza = productoId != null;
        boolean crea = nombreProducto != null && !nombreProducto.isBlank();

        if (reutiliza && crea) {
            throw new ReglaDeNegocioException(
                    "Se recibió un concepto existente y uno nuevo a la vez. Es uno u otro.");
        }
        if (!reutiliza && !crea) {
            throw new ReglaDeNegocioException(
                    "Falta el concepto del repuesto: elige uno existente o escribe su nombre.");
        }
        if (codigo == null || codigo.isBlank()) {
            throw new ReglaDeNegocioException("El código del repuesto es obligatorio");
        }
        if (marcaRepuesto == null || marcaRepuesto.isBlank()) {
            throw new ReglaDeNegocioException("La marca del repuesto es obligatoria");
        }
        if (precio == null) {
            throw new ReglaDeNegocioException("El precio de venta es obligatorio");
        }
        if (stockMinimo < 0) {
            throw new ReglaDeNegocioException("El stock mínimo no puede ser negativo");
        }
    }

    /** Atajo: marca nueva de un concepto que ya existe. */
    public static ComandoCrearRepuesto sobreConceptoExistente(
            UUID productoId, String codigo, String marca, Dinero precio, int stockMinimo) {
        return new ComandoCrearRepuesto(productoId, null, null, null,
                codigo, marca, precio, stockMinimo);
    }

    /** Atajo: repuesto que no se parece a nada de lo que hay. */
    public static ComandoCrearRepuesto conConceptoNuevo(
            String nombreProducto, UUID categoriaId, String aplicacionOriginal,
            String codigo, String marca, Dinero precio, int stockMinimo) {
        return new ComandoCrearRepuesto(null, nombreProducto, categoriaId, aplicacionOriginal,
                codigo, marca, precio, stockMinimo);
    }

    boolean reutilizaConcepto() {
        return productoId != null;
    }
}
