package com.workshopmanagement.rdmotors.reportes.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/** El fiado del período y lo que queda por cobrar (spec 0008, RF-024). */
class CarteraDelPeriodoTest {

    @Test
    @DisplayName("lo cobrado es lo que entró por abonos: efectivo más transferencia, ni un peso más")
    void cobradoEsLaSumaDeSusPartes() {
        CarteraDelPeriodo cartera = new CarteraDelPeriodo(Dinero.de(120_000), Dinero.de(80_000), Dinero.de(450_000), 6);

        assertThat(cartera.cobrado()).isEqualTo(Dinero.de(200_000));
    }

    @Test
    @DisplayName("un período sin abonos no cobró nada, aunque haya plata por cobrar")
    void sinAbonosNoSeCobroNada() {
        CarteraDelPeriodo cartera = new CarteraDelPeriodo(Dinero.CERO, Dinero.CERO, Dinero.de(450_000), 6);

        assertThat(cartera.cobrado()).isEqualTo(Dinero.CERO);
        assertThat(cartera.porCobrar()).isEqualTo(Dinero.de(450_000));
    }

    @Test
    @DisplayName("sin fiado en el negocio, la cartera está vacía y no dice cifras inventadas")
    void vacia() {
        assertThat(CarteraDelPeriodo.VACIA.cobrado()).isEqualTo(Dinero.CERO);
        assertThat(CarteraDelPeriodo.VACIA.porCobrar()).isEqualTo(Dinero.CERO);
        assertThat(CarteraDelPeriodo.VACIA.clientesQueDeben()).isZero();
    }
}
