package com.workshopmanagement.rdmotors.carga.aplicacion;

import com.workshopmanagement.rdmotors.carga.dominio.CargaDeInventario;
import com.workshopmanagement.rdmotors.carga.dominio.CategoriasConocidas;
import com.workshopmanagement.rdmotors.carga.dominio.Revision;

/**
 * La carga como se ve en la pre-carga: sus datos, revisada contra el inventario de este momento, y los nombres que la
 * pantalla muestra en vez de ids.
 *
 * @param proveedor  el nombre del proveedor elegido, o {@code null}
 * @param cuenta     el nombre de la cuenta elegida, o {@code null}
 * @param categorias las activas, para poner el nombre de la categoría de cada renglón
 */
public record DetalleCarga(CargaDeInventario carga, Revision revision, String proveedor, String cuenta,
                           CategoriasConocidas categorias) {
}
