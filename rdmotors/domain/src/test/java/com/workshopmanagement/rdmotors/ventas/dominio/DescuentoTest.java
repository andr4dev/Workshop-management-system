package com.workshopmanagement.rdmotors.ventas.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * El descuento se guarda en pesos (spec 0003, RF-015). La tabla de casos es la misma que prueba la
 * pantalla (`frontend/src/utils/venta.test.js`, fase 3): si las dos calcularan distinto, el cajero
 * vería un total y se cobraría otro.
 */
class DescuentoTest {

    @ParameterizedTest(name = "{0}% de ${1} = ${2}")
    @CsvSource({
            "10, 38500, 3850",
            "7.5, 13333, 1000",   // 999,975 → HALF_UP
            "15, 20000, 3000",
            "33.33, 10000, 3333",
            "0.5, 1999, 10",      // 9,995 → 10
            "100, 45000, 45000",
    })
    @DisplayName("del porcentaje al monto, redondeado al peso con HALF_UP")
    void porcentajeAMonto(String porcentaje, long subtotal, long esperado) {
        Descuento d = Descuento.porPorcentaje(new BigDecimal(porcentaje), Dinero.de(subtotal), "cliente frecuente");

        assertThat(d.monto()).isEqualTo(Dinero.de(esperado));
        assertThat(d.modo()).isEqualTo(ModoDescuento.PORCENTAJE);
        assertThat(d.porcentaje()).isEqualByComparingTo(porcentaje);
    }

    @Test
    @DisplayName("por monto queda tal cual, sin porcentaje")
    void porMonto() {
        Descuento d = Descuento.porMonto(Dinero.de(3_000), "  negociación ");

        assertThat(d.monto()).isEqualTo(Dinero.de(3_000));
        assertThat(d.porcentaje()).isNull();
        assertThat(d.motivo()).isEqualTo("negociación");
    }

    @Test
    @DisplayName("sin motivo no hay descuento")
    void sinMotivo() {
        assertThatThrownBy(() -> Descuento.porMonto(Dinero.de(3_000), "  "))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("motivo");
    }

    @Test
    @DisplayName("un porcentaje de 0, negativo, mayor que 100 o con más de 2 decimales se rechaza")
    void porcentajeFueraDeRango() {
        Dinero subtotal = Dinero.de(10_000);
        assertThatThrownBy(() -> Descuento.porPorcentaje(BigDecimal.ZERO, subtotal, "x")).hasMessageContaining("100");
        assertThatThrownBy(() -> Descuento.porPorcentaje(new BigDecimal("-5"), subtotal, "x")).hasMessageContaining("100");
        assertThatThrownBy(() -> Descuento.porPorcentaje(new BigDecimal("100.01"), subtotal, "x")).hasMessageContaining("100");
        assertThatThrownBy(() -> Descuento.porPorcentaje(new BigDecimal("10.555"), subtotal, "x")).hasMessageContaining("2 decimales");
    }

    @Test
    @DisplayName("un descuento de $0 no es un descuento")
    void montoCero() {
        assertThatThrownBy(() -> Descuento.porMonto(Dinero.CERO, "x")).hasMessageContaining("mayor a $0");
        // 0,01% de $10 redondea a $0: tampoco.
        assertThatThrownBy(() -> Descuento.porPorcentaje(new BigDecimal("0.01"), Dinero.de(10), "x"))
                .hasMessageContaining("mayor a $0");
    }
}
