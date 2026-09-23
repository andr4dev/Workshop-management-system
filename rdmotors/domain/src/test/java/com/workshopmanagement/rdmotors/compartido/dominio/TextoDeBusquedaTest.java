package com.workshopmanagement.rdmotors.compartido.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Buscar sin mayúsculas ni tildes: la regla que comparten todos los buscadores y la base. */
class TextoDeBusquedaTest {

    @Test
    @DisplayName("quita tildes, diéresis y eñe, en minúsculas y mayúsculas")
    void quitaTildes() {
        assertThat(TextoDeBusqueda.normalizar("BUJÍA ÑANDÚ Pingüino Canción")).isEqualTo("bujia nandu pinguino cancion");
        assertThat(TextoDeBusqueda.normalizar("áéíóú ÁÉÍÓÚ ñÑ çÇ")).isEqualTo("aeiou aeiou nn cc");
    }

    @Test
    @DisplayName("lo que no es letra con tilde queda igual")
    void loDemasIgual() {
        assertThat(TextoDeBusqueda.normalizar("352B59K-A / AS 200 (FI) 100%")).isEqualTo("352b59k-a / as 200 (fi) 100%");
        assertThat(TextoDeBusqueda.normalizar(null)).isNull();
    }

    @Test
    @DisplayName("contiene funciona en los dos sentidos: con tilde encuentra sin tilde, y al revés")
    void enLosDosSentidos() {
        assertThat(TextoDeBusqueda.contiene("BUJÍA NGK", "bujia")).isTrue();
        assertThat(TextoDeBusqueda.contiene("BUJIA NGK", "bují")).isTrue();
        assertThat(TextoDeBusqueda.contiene("CADENA", "bujia")).isFalse();
        assertThat(TextoDeBusqueda.contiene(null, "bujia")).isFalse();
    }

    @Test
    @DisplayName("las dos listas emparejan letra por letra, como translate() en la base")
    void listasDelMismoLargo() {
        assertThat(TextoDeBusqueda.CON_TILDE).hasSameSizeAs(TextoDeBusqueda.SIN_TILDE);
        // Ninguna letra sin tilde se "traduce": la base las deja iguales y Java también.
        assertThat(TextoDeBusqueda.SIN_TILDE.chars().noneMatch(c -> TextoDeBusqueda.CON_TILDE.indexOf(c) >= 0)).isTrue();
    }
}
