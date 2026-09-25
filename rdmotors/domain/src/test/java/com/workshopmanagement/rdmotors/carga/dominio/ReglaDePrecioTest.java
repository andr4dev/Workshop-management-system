package com.workshopmanagement.rdmotors.carga.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/** El precio sugerido: primero el IVA, después la ganancia, y hacia arriba (spec 0012, decisiones 1 y 2). */
class ReglaDePrecioTest {

    private static ReglaDePrecio regla(String iva, String ganancia, int redondeo) {
        return new ReglaDePrecio(new BigDecimal(iva), new BigDecimal(ganancia), redondeo);
    }

    /**
     * La bujía iridium de la MAG477: 8 unidades por $308.274. Su parte del IVA es $58.572, así que el renglón con IVA
     * es $366.846 y cada bujía cuesta $45.855,75.
     */
    private static final BigDecimal BUJIA_CON_IVA = new BigDecimal("45855.7500");

    @Test
    @DisplayName("LA BUJÍA DE LA MAG477: costo con IVA $45.855,75 + 45% = $66.491")
    void laBujia() {
        assertThat(regla("19", "45", 1).sugerido(BUJIA_CON_IVA)).isEqualTo(Dinero.de(66_491));
    }

    @Test
    @DisplayName("EL VALOR TOTAL ES DE TODO EL RENGLÓN: la bujía, 8 por $308.274, cuesta $45.855,7575 cada una con IVA")
    void costoPorUnidad() {
        ReglaDePrecio regla = regla("19", "45", 1);

        BigDecimal costo = regla.costoConIvaPorUnidad(Dinero.de(308_274), 8);

        assertThat(costo).isEqualByComparingTo("45855.7575");
        assertThat(costo.scale()).isEqualTo(Dinero.ESCALA_UNITARIA);
        // Sin dividir entre las 8 saldría a $505.569: el error que el spec pone primero.
        assertThat(regla.sugerido(costo)).isEqualTo(Dinero.de(66_491));
    }

    @Test
    @DisplayName("un renglón de 1 unidad y uno de 60; sin cantidad o sin total no hay costo")
    void unoYSesenta() {
        ReglaDePrecio regla = regla("19", "45", 1);

        assertThat(regla.costoConIvaPorUnidad(Dinero.de(6_188), 1)).isEqualByComparingTo("7363.7200");
        // 60 bombillos por $100.000: $1.666,6666… sin IVA, $1.983,3333 con él.
        assertThat(regla.costoConIvaPorUnidad(Dinero.de(100_000), 60)).isEqualByComparingTo("1983.3333");
        assertThat(regla.costoConIvaPorUnidad(null, 8)).isNull();
        assertThat(regla.costoConIvaPorUnidad(Dinero.de(308_274), null)).isNull();
        assertThat(regla.costoConIvaPorUnidad(Dinero.de(308_274), 0)).isNull();
        assertThat(regla.costoConIvaPorUnidad(Dinero.CERO, 8)).isNull();
    }

    @Test
    @DisplayName("sumando los porcentajes (19 + 45 = 64%) saldría $63.196, que deja 37,8% y no 45%: no es la regla")
    void noSeSumanLosPorcentajes() {
        // $38.534,25 sin IVA × 1,64. Lo que saldría si alguien "simplifica" la cuenta.
        Dinero sumandoPorcentajes = Dinero.de(63_196);

        assertThat(regla("19", "45", 1).sugerido(BUJIA_CON_IVA)).isNotEqualTo(sumandoPorcentajes);
    }

    @Test
    @DisplayName("por defecto sube a los $100: la bujía queda en $66.500")
    void redondeaALosCien() {
        assertThat(ReglaDePrecio.porDefecto().sugerido(BUJIA_CON_IVA)).isEqualTo(Dinero.de(66_500));
    }

    @Test
    @DisplayName("SIEMPRE HACIA ARRIBA: $66.401 sube a $66.500, nunca baja a $66.400")
    void haciaArriba() {
        // Redondear al más cercano bajaría la mitad de los precios, y ese peso se pierde en cada venta.
        BigDecimal costo = new BigDecimal("45793.7924");   // × 1,45 = $66.400,989…

        assertThat(regla("19", "45", 100).sugerido(costo)).isEqualTo(Dinero.de(66_500));
        assertThat(regla("19", "45", 1).sugerido(costo)).isEqualTo(Dinero.de(66_401));
    }

    @Test
    @DisplayName("un precio que ya cae justo en el múltiplo no se sube otro escalón")
    void justoEnElMultiplo() {
        // $10.000 × 1,45 = $14.500 exactos.
        assertThat(regla("19", "45", 100).sugerido(new BigDecimal("10000.0000"))).isEqualTo(Dinero.de(14_500));
    }

    @Test
    @DisplayName("el IVA de la factura es el sub-total por el porcentaje, al peso: la MAG477 da $2.798.609")
    void elIvaDeLaFactura() {
        assertThat(regla("19", "45", 1).ivaDe(Dinero.de(14_729_523))).isEqualTo(Dinero.de(2_798_609));
    }

    @Test
    @DisplayName("con IVA en 0% el costo queda sin IVA: la salida si el contador dice que sí se recupera")
    void ivaEnCero() {
        assertThat(regla("0", "45", 1).ivaDe(Dinero.de(14_729_523))).isEqualTo(Dinero.de(0));
    }

    @Test
    @DisplayName("19, 19.0 y 19.00 son el mismo porcentaje")
    void mismoPorcentaje() {
        assertThat(regla("19", "45", 100)).isEqualTo(regla("19.00", "45.0", 100));
    }

    @Test
    @DisplayName("porcentajes imposibles y redondeos sin sentido se rechazan al escribirlos")
    void imposibles() {
        assertThatThrownBy(() -> regla("-1", "45", 100)).isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("IVA");
        assertThatThrownBy(() -> regla("19", "4500", 100)).isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("ganancia");
        assertThatThrownBy(() -> regla("19", "45", 0)).isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("redondeo");
        assertThatThrownBy(() -> regla("19", "45", 100).sugerido(BigDecimal.ZERO))
                .isInstanceOf(ReglaDeNegocioException.class);
    }
}
