package com.workshopmanagement.rdmotors.compras.aplicacion;

import java.util.List;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compras.dominio.LineaCompra;
import com.workshopmanagement.rdmotors.compras.dominio.ResumenCompra;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/**
 * Una factura de la lista del historial con lo que trae, para distinguirla sin abrirla:
 * <ul>
 *   <li>sin buscar, sus primeros renglones (spec 0002, RF-028): "FILTRO DE ACEITE INOKI × 25 · PASTILLAS
 *       FRENO CBI × 4 · y 1 más";</li>
 *   <li>buscando un repuesto, <b>cuáles de sus renglones coinciden</b> (RF-027): buscando "aceite", "FILTRO
 *       DE ACEITE INOKI × 10".</li>
 * </ul>
 *
 * @param primeros los primeros {@value #MAXIMO_PRIMEROS} renglones vigentes, en el orden de la factura. Cuántos
 *                 tiene en total lo dice {@link ResumenCompra#renglones()}
 * @param coinciden los renglones vigentes que coinciden, en el orden de la factura. Vacío si no se
 *                  buscó un repuesto
 */
public record FilaHistorial(ResumenCompra resumen, List<RenglonQueCoincide> primeros,
                            List<RenglonQueCoincide> coinciden) {

    /** Cuántos renglones se muestran debajo de una factura sin buscar; del resto se dice cuántos son. */
    public static final int MAXIMO_PRIMEROS = 3;

    public FilaHistorial {
        primeros = List.copyOf(primeros);
        coinciden = List.copyOf(coinciden);
    }

    /**
     * Un renglón en la lista: lo justo para reconocer el repuesto. Trae código y aplicación para que la pantalla
     * pueda mostrar por qué salió cuando lo buscado no está en el nombre ni en la marca.
     */
    public record RenglonQueCoincide(UUID lineaId, String codigo, String nombre, String marca,
                                     String aplicacion, int cantidad) {

        static RenglonQueCoincide de(LineaCompra linea) {
            Variante v = linea.getVariante();
            return new RenglonQueCoincide(linea.getId(), v.getCodigo(), v.getProducto().getNombre(),
                    v.getMarcaRepuesto(), v.getProducto().getAplicacionOriginal(), linea.getCantidad());
        }
    }
}
