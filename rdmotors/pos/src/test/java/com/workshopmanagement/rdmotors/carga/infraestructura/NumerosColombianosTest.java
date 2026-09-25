package com.workshopmanagement.rdmotors.carga.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/** Los números como los escribe Colombia (spec 0012, RF-002). */
class NumerosColombianosTest {

    @ParameterizedTest(name = "\"{0}\" son {1} pesos")
    @DisplayName("LA PLATA: el punto separa miles; $12.943 son doce mil novecientos cuarenta y tres")
    @CsvSource(delimiter = '|', value = {
            "$12.943        | 12943",
            "12943          | 12943",
            "$1.234.567     | 1234567",
            "$ 308.274      | 308274",
            "12,943         | 12943",
            "12.943,50      | 12944",
            "12.943,49      | 12943",
            "COP 5.000      | 5000",
            "0              | 0",
            "-1.500         | -1500",
    })
    void pesos(String texto, long esperado) {
        assertThat(NumerosColombianos.pesos(texto)).isEqualTo(Dinero.de(esperado));
    }

    @Test
    @DisplayName("el espacio que no se parte, que Excel pone entre el signo y el número, no estorba")
    void espacioQueNoSeParte() {
        assertThat(NumerosColombianos.pesos("$\u00A0308.274")).isEqualTo(Dinero.de(308_274));
    }

    @ParameterizedTest(name = "\"{0}\" es {1}")
    @DisplayName("UN NÚMERO CON DECIMALES: la coma es el decimal; con los dos, el último manda")
    @CsvSource(delimiter = '|', value = {
            "18          | 18",
            "18,5        | 18.5",
            "18.5        | 18.5",
            "18 %        | 18",
            "1.234,5     | 1234.5",
            "1,234.5     | 1234.5",
            "1.234       | 1234",
            "1.234.567,8 | 1234567.8",
    })
    void numero(String texto, String esperado) {
        assertThat(NumerosColombianos.numero(texto)).isEqualByComparingTo(esperado);
    }

    @ParameterizedTest(name = "\"{0}\" es la cantidad {1}")
    @DisplayName("UNA CANTIDAD es un entero")
    @CsvSource(delimiter = '|', value = {"8 | 8", "1.200 | 1200", "8,0 | 8"})
    void entero(String texto, int esperado) {
        assertThat(NumerosColombianos.entero(texto)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "\"{0}\" no se entiende")
    @DisplayName("LO QUE NO SE ENTIENDE ES NULL, nunca un cero")
    @NullSource
    @ValueSource(strings = {"", "  ", "$", "doce mil", "12a", "1.2.3,4,5", "--5"})
    void noSeEntiende(String texto) {
        assertThat(NumerosColombianos.pesos(texto)).isNull();
        assertThat(NumerosColombianos.numero(texto)).isNull();
        assertThat(NumerosColombianos.entero(texto)).isNull();
    }

    @ParameterizedTest(name = "\"{0}\" no es una cantidad")
    @DisplayName("una cantidad con decimales, o que no cabe, no es una cantidad")
    @ValueSource(strings = {"2,5", "0.5", "99999999999"})
    void noEsCantidad(String texto) {
        assertThat(NumerosColombianos.entero(texto)).isNull();
    }
}
