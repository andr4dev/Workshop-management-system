package com.workshopmanagement.rdmotors.inventario.aplicacion;

import java.math.BigDecimal;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.Producto;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/**
 * Un repuesto tal como se muestra en una búsqueda o en el inventario.
 *
 * <p>No se devuelve la entidad: se devuelve esto. Así el borde no puede tropezarse con relaciones
 * perezosas fuera de la transacción, y el nombre del concepto llega ya resuelto.
 *
 * <p><b>{@code costoPromedio} puede ser null y eso es información, no un hueco.</b> Significa que
 * el repuesto nunca se ha comprado y su costo no se conoce. Se muestra como "—", nunca como cero:
 * un cero inventado se lee como un hecho y haría que el repuesto reportara 100% de margen (RF-013).
 *
 * <p>Trae {@code categoriaId} y {@code stockMinimo} porque el formulario de corregir ficha arranca
 * con estos datos. Sin ellos arrancaba con categoría vacía y stock mínimo 5, y al guardar borraba la
 * categoría real y pisaba el stock mínimo.
 */
public record RepuestoEncontrado(
        UUID id,
        String codigo,
        String nombre,
        String marcaRepuesto,
        String aplicacion,
        UUID categoriaId,
        String categoria,
        Dinero precio,
        int stock,
        int stockMinimo,
        BigDecimal costoPromedio,
        boolean stockBajo) {

    public static RepuestoEncontrado de(Variante variante) {
        Producto producto = variante.getProducto();
        Categoria categoria = producto.getCategoria();
        return new RepuestoEncontrado(
                variante.getId(),
                variante.getCodigo(),
                producto.getNombre(),
                variante.getMarcaRepuesto(),
                producto.getAplicacionOriginal(),
                categoria == null ? null : categoria.getId(),
                categoria == null ? null : categoria.getNombre(),
                variante.getPrecio(),
                variante.getStock(),
                variante.getStockMinimo(),
                variante.getCostoPromedio(),
                variante.tieneStockBajo());
    }

    /** {@code true} si nunca se ha comprado. El borde lo usa para pintar "—" en vez de una cifra. */
    public boolean costoDesconocido() {
        return costoPromedio == null;
    }

    /** Stock × costo promedio. {@code null} si el costo no se conoce: no se inventa un cero. */
    public BigDecimal valor() {
        return costoPromedio == null ? null : costoPromedio.multiply(BigDecimal.valueOf(stock));
    }
}
