package com.workshopmanagement.rdmotors.carga.dominio;

import java.math.BigDecimal;
import java.util.List;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/**
 * La carga mirada contra el inventario de este momento: qué es reposición, qué le falta a cada renglón, si la suma
 * cuadra con la factura y si ya se puede confirmar. Se arma cada vez que se consulta; no se guarda.
 *
 * @param problemas los de la carga entera: si hay alguno, no se confirma
 */
public record Revision(List<RenglonRevisado> renglones, List<Problema> problemas, Totales totales) {

    public boolean sePuedeConfirmar() {
        return problemas.isEmpty();
    }

    /**
     * @param costoPorUnidad lo que costó cada unidad con IVA, con 4 decimales; {@code null} si lo leído no alcanza
     * @param existente      el repuesto que ya tiene ese código: el renglón es una reposición. {@code null} si es
     *                       nuevo
     * @param problemas      los que no dejan confirmar. Un renglón quitado no tiene: no va a entrar
     * @param avisos         los que se muestran y no impiden: vender a pérdida a veces es a propósito
     */
    public record RenglonRevisado(RenglonDeCarga renglon, BigDecimal costoPorUnidad, Dinero sugerido,
                                  Variante existente, List<Problema> problemas, List<Problema> avisos) {

        public boolean esReposicion() {
            return existente != null;
        }
    }

    /**
     * @param sumaLeida        la suma de los valores totales de <b>todos</b> los renglones leídos, quitados incluidos:
     *                         es lo que se compara con la factura para saber si la lectura está completa
     * @param subtotalFactura  el sub-total impreso (o escrito, si vino en Excel); {@code null} si no se sabe
     * @param diferencia       factura menos lo leído: positiva si faltan pesos; {@code null} sin sub-total
     * @param subtotalIncluido la suma de los que van a entrar, sin IVA
     * @param iva              el IVA de lo que va a entrar, como lo calcula la factura
     * @param totalConIva      lo que va a quedar registrado como compra: lo que se pagó
     */
    public record Totales(Dinero sumaLeida, Dinero subtotalFactura, Dinero diferencia, Dinero subtotalIncluido,
                          Dinero iva, Dinero totalConIva, int unidades, int incluidos, int conProblema,
                          int propuestasSinRevisar, int reposiciones, int ajustados, int quitados) {
    }
}
