package com.workshopmanagement.rdmotors.clientes.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/** Qué clientes muestra la Cartera (spec 0008, RF-018, RF-021 y RF-022). */
class FiltroCarteraTest {

    private static final LocalDate PRIMERO = LocalDate.of(2026, 9, 1);
    private static final LocalDate VEINTIUNO = LocalDate.of(2026, 9, 21);

    @Test
    @DisplayName("sin decir nada se ven los que deben, por fecha de la venta y sin período")
    void porDefecto() {
        FiltroCartera filtro = new FiltroCartera(null, null, null, null, null);

        assertThat(filtro.vista()).isEqualTo(FiltroCartera.Vista.DEBEN);
        assertThat(filtro.modoFecha()).isEqualTo(FiltroCartera.ModoFecha.VENTA);
        assertThat(filtro.tienePeriodo()).isFalse();
        assertThat(FiltroCartera.losQueDeben()).isEqualTo(filtro);
    }

    @Test
    @DisplayName("buscar nada es no buscar: los espacios no filtran a nadie")
    void textoEnBlanco() {
        assertThat(new FiltroCartera(FiltroCartera.Vista.HISTORIAL, "   ").texto()).isNull();
        assertThat(new FiltroCartera(FiltroCartera.Vista.HISTORIAL, "  Juan  ").texto()).isEqualTo("Juan");
    }

    @Test
    @DisplayName("un período es desde y hasta: con media fecha no se filtra por fecha")
    void mediaFechaNoEsPeriodo() {
        assertThat(new FiltroCartera(null, null, FiltroCartera.ModoFecha.ABONO, PRIMERO, null).tienePeriodo()).isFalse();
        assertThat(new FiltroCartera(null, null, FiltroCartera.ModoFecha.ABONO, null, VEINTIUNO).tienePeriodo()).isFalse();
        assertThat(new FiltroCartera(null, null, FiltroCartera.ModoFecha.ABONO, PRIMERO, VEINTIUNO).tienePeriodo()).isTrue();
    }

    @Test
    @DisplayName("un período al revés no se consulta: se dice por qué")
    void alReves() {
        assertThatThrownBy(() -> new FiltroCartera(null, null, FiltroCartera.ModoFecha.VENTA, VEINTIUNO, PRIMERO))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessage("El período está al revés: revisa las fechas");
    }

    @Test
    @DisplayName("un solo día es un período válido: lo que se fió hoy")
    void unSoloDia() {
        assertThat(new FiltroCartera(null, null, FiltroCartera.ModoFecha.VENTA, VEINTIUNO, VEINTIUNO).tienePeriodo())
                .isTrue();
    }
}
