package com.workshopmanagement.rdmotors.inventario.dominio;

/**
 * Qué se pide del inventario: lo que ve el administrador en *Inventario* y el cajero en el catálogo del
 * mostrador (spec 0005). Es un solo listado con filtros, para que el texto sin tildes y las páginas no
 * se escriban dos veces.
 *
 * @param texto           vacío = sin filtro; nunca {@code null}
 * @param soloStockBajo   solo los que están en o bajo su mínimo
 * @param categoria       nunca {@code null}: sin filtro es {@link FiltroCategoria#todas()}
 * @param conStockPrimero primero los que hay, al final los agotados. Es el orden del catálogo: lo que se
 *                        puede vender va arriba, y lo que no hay se ve igual, para decir "no, pero tengo"
 */
public record ConsultaInventario(String texto, boolean soloStockBajo, FiltroCategoria categoria,
                                 boolean conStockPrimero) {

    public ConsultaInventario {
        texto = texto == null ? "" : texto.trim();
        categoria = categoria == null ? FiltroCategoria.todas() : categoria;
    }

    /** El inventario de siempre: por texto y stock bajo, todas las categorías, por nombre. */
    public static ConsultaInventario de(String texto, boolean soloStockBajo) {
        return new ConsultaInventario(texto, soloStockBajo, FiltroCategoria.todas(), false);
    }
}
