package com.workshopmanagement.rdmotors.compras.dominio;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.TextoDeBusqueda;
import com.workshopmanagement.rdmotors.inventario.dominio.Producto;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/**
 * Lo que se escribe al buscar las compras de un repuesto (spec 0002, RF-025 y RF-026): parte del
 * código, el nombre, la marca o la aplicación, sin distinguir mayúsculas ni tildes. Los mismos campos
 * y la misma comparación ({@link TextoDeBusqueda}) que el inventario.
 *
 * <p><b>La regla de "este repuesto coincide" vive aquí y en ningún otro sitio del dominio.</b> La
 * usan el detalle, para resaltar renglones, y el doble en memoria, para filtrar. La consulta de
 * Postgres la imita, y la integración verifica que la lista y el detalle están de acuerdo: sin eso,
 * podría salir una factura en la lista y no tener nada resaltado al abrirla.
 */
public record BusquedaDeRepuesto(String texto) {

    /** Un nombre y su aplicación caben de sobra. Más que esto no es una búsqueda, es un error. */
    public static final int LARGO_MAXIMO = 80;

    public BusquedaDeRepuesto {
        if (texto == null || texto.isBlank()) {
            throw new ReglaDeNegocioException("Escribe qué repuesto buscar");
        }
        texto = texto.trim();
        if (texto.length() > LARGO_MAXIMO) {
            throw new ReglaDeNegocioException(
                    "La búsqueda de repuesto admite hasta " + LARGO_MAXIMO + " caracteres");
        }
    }

    /** {@code null} si no hay nada que buscar: un campo vacío es "sin filtro", no un error. */
    public static BusquedaDeRepuesto de(String texto) {
        return texto == null || texto.isBlank() ? null : new BusquedaDeRepuesto(texto);
    }

    /** En minúsculas y sin tildes: es el texto contra el que compara la consulta de Postgres. */
    public String normalizado() {
        return TextoDeBusqueda.normalizar(texto);
    }

    public boolean coincideCon(Variante variante) {
        Producto producto = variante.getProducto();
        return contiene(variante.getCodigo())
                || contiene(producto.getNombre())
                || contiene(variante.getMarcaRepuesto())
                || contiene(producto.getAplicacionOriginal());
    }

    private boolean contiene(String campo) {
        return TextoDeBusqueda.contiene(campo, texto);
    }
}
