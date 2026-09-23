package com.workshopmanagement.rdmotors.ventas.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * El aviso de venta a pérdida, ya en el servidor (spec 0004, RF-011). Son los casos que probaba
 * {@code venta.test.js} cuando la cuenta la hacía la pantalla: el mismo filtro de $13.000 que costó $8.000 y las
 * pastillas de $12.000 que costaron $7.500.
 */
class AvisoDePerdidaTest {

    private static AvisoDePerdida.Renglon filtros(int cantidad) {
        return new AvisoDePerdida.Renglon(Dinero.de(13_000), cantidad, new BigDecimal("8000"));
    }

    private static AvisoDePerdida.Renglon pastillas(BigDecimal costo) {
        return new AvisoDePerdida.Renglon(Dinero.de(12_000), 1, costo);
    }

    /** $38.000 de venta: dos filtros y unas pastillas. Costaron $23.500. */
    private static final List<AvisoDePerdida.Renglon> DE_38 = List.of(filtros(2), pastillas(new BigDecimal("7500.0000")));

    @Test
    @DisplayName("sin descuento no hay pérdida: costaron $23.500 y se cobran $38.000")
    void sinPerdida() {
        assertThat(AvisoDePerdida.calcular(DE_38, Dinero.CERO)).isEmpty();
    }

    @Test
    @DisplayName("con $20.000 de descuento se cobran $18.000 por lo que costó $23.500: pierde $5.500")
    void conPerdida() {
        assertThat(AvisoDePerdida.calcular(DE_38, Dinero.de(20_000)))
                .contains(new AvisoDePerdida(Dinero.de(23_500), Dinero.de(18_000), Dinero.de(5_500), 0));
    }

    @Test
    @DisplayName("un repuesto sin costo no puede decir si se vende a pérdida: no hay aviso")
    void sinCostoNoHayAviso() {
        assertThat(AvisoDePerdida.calcular(List.of(pastillas(null)), Dinero.de(11_000))).isEmpty();
    }

    @Test
    @DisplayName("con uno sin costo, el descuento se reparte: por el filtro se cobran $2.600 de los $5.000, y costó $8.000")
    void elDescuentoSeReparte() {
        List<AvisoDePerdida.Renglon> renglones = List.of(filtros(1), pastillas(null));

        assertThat(AvisoDePerdida.calcular(renglones, Dinero.de(20_000)))
                .contains(new AvisoDePerdida(Dinero.de(8_000), Dinero.de(2_600), Dinero.de(5_400), 1));
    }

    @Test
    @DisplayName("el costo promedio con decimales se redondea al peso: 3 × $3.333,3333 son $10.000, no $9.999")
    void costoConDecimales() {
        var tercios = new AvisoDePerdida.Renglon(Dinero.de(3_000), 3, new BigDecimal("3333.3333"));

        assertThat(AvisoDePerdida.calcular(List.of(tercios), Dinero.CERO))
                .contains(new AvisoDePerdida(Dinero.de(10_000), Dinero.de(9_000), Dinero.de(1_000), 0));
    }
}
