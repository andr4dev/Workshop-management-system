package com.workshopmanagement.rdmotors.compartido.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DineroTest {

    @Test
    @DisplayName("el peso no tiene centavos: todo monto se redondea a entero")
    void redondeaAEntero() {
        assertThat(Dinero.de(new BigDecimal("13333.49")).valor()).isEqualByComparingTo("13333");
        assertThat(Dinero.de(new BigDecimal("13333.50")).valor()).isEqualByComparingTo("13334");
    }

    @Test
    @DisplayName("LA PRUEBA DE LOS $5: repartir un total no puede perder plata")
    void repartirUnTotalNoPierdePlata() {
        // 15 unidades por $200.000
        Dinero total = Dinero.de(200_000);
        int cantidad = 15;

        // Asi es como se pierden los $5: redondear el unitario a entero.
        BigDecimal unitarioMal = total.valor().divide(BigDecimal.valueOf(cantidad), 0, RoundingMode.HALF_UP);
        BigDecimal reconstruidoMal = unitarioMal.multiply(BigDecimal.valueOf(cantidad));
        assertThat(unitarioMal).isEqualByComparingTo("13333");
        assertThat(reconstruidoMal).isEqualByComparingTo("199995");   // faltan $5 contra la factura

        // Asi es como NO se pierden: 4 decimales.
        BigDecimal unitarioBien = total.dividirEntre(cantidad);
        assertThat(unitarioBien).isEqualByComparingTo("13333.3333");

        Dinero reconstruidoBien = Dinero.desdeUnitario(unitarioBien, cantidad);
        assertThat(reconstruidoBien).isEqualTo(total);               // cuadra al peso
    }

    @Test
    @DisplayName("division exacta: 20 unidades por $200.000 dan $10.000 c/u")
    void divisionExacta() {
        assertThat(Dinero.de(200_000).dividirEntre(20)).isEqualByComparingTo("10000.0000");
    }

    @Test
    @DisplayName("un caso peor: 7 unidades por $100.000 tambien cuadra")
    void divisionConDecimalPeriodico() {
        Dinero total = Dinero.de(100_000);
        BigDecimal unitario = total.dividirEntre(7);
        assertThat(unitario).isEqualByComparingTo("14285.7143");
        assertThat(Dinero.desdeUnitario(unitario, 7)).isEqualTo(total);
    }

    @Test
    void sumaYResta() {
        assertThat(Dinero.de(18_000).mas(Dinero.de(2_000))).isEqualTo(Dinero.de(20_000));
        assertThat(Dinero.de(18_000).menos(Dinero.de(2_000))).isEqualTo(Dinero.de(16_000));
        assertThat(Dinero.de(18_000).por(3)).isEqualTo(Dinero.de(54_000));
    }

    @Test
    void noSePuedeDividirEntreCero() {
        assertThatThrownBy(() -> Dinero.de(1_000).dividirEntre(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
