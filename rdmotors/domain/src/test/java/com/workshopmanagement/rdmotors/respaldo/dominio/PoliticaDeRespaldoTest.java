package com.workshopmanagement.rdmotors.respaldo.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/** Las reglas del respaldo (spec 0011, RF-011): cómo se llama la copia y cuándo hay que avisar. */
class PoliticaDeRespaldoTest {

    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    private final PoliticaDeRespaldo politica = new PoliticaDeRespaldo(7, COLOMBIA);

    /** Lunes 21 de septiembre de 2026, 9 a. m. en Colombia. */
    private static final Instant HOY = Instant.parse("2026-09-21T14:00:00Z");

    private static Respaldo bueno(String cuando) {
        return Respaldo.hecho(Instant.parse(cuando), "rdmotors-x.dump", 4_096, 900, OrigenRespaldo.A_MANO, null);
    }

    private static Respaldo fallido(String cuando) {
        return Respaldo.fallido(Instant.parse(cuando), "rdmotors-x.dump", "no encontré pg_dump", 30,
                OrigenRespaldo.A_MANO, null);
    }

    @Test
    @DisplayName("el archivo se llama por el día y la hora de Colombia, no por la del reloj UTC")
    void nombreDeArchivo() {
        // Las 2 a. m. de Colombia son las 7 UTC: el archivo tiene que decir 0200, no 0700.
        assertThat(politica.nombreDeArchivo(Instant.parse("2026-09-21T07:00:00Z")))
                .isEqualTo("rdmotors-2026-09-21-020000.dump");
    }

    @Test
    @DisplayName("DOS COPIAS DEL MISMO MINUTO NO SE PISAN: el nombre lleva los segundos")
    void dosEnElMismoMinuto() {
        // Pasó de verdad: tres copias seguidas dejaron tres filas apuntando a un solo archivo.
        assertThat(politica.nombreDeArchivo(Instant.parse("2026-09-23T16:22:34Z")))
                .isNotEqualTo(politica.nombreDeArchivo(Instant.parse("2026-09-23T16:22:47Z")));
    }

    @Test
    @DisplayName("dice hace cuántos días se bajó la última copia, en días de la tienda")
    void cuantosDiasSinBajar() {
        assertThat(politica.diasSinBajar(List.of(bueno("2026-09-21T07:00:00Z")), HOY)).isZero();
        assertThat(politica.diasSinBajar(List.of(bueno("2026-09-20T07:00:00Z")), HOY)).isEqualTo(1);
        assertThat(politica.diasSinBajar(List.of(bueno("2026-09-01T07:00:00Z")), HOY)).isEqualTo(20);
        // Más de un mes: los meses se cuentan como días, no se pierden.
        assertThat(politica.diasSinBajar(List.of(bueno("2026-07-21T07:00:00Z")), HOY)).isEqualTo(62);
    }

    @Test
    @DisplayName("NUNCA HABER BAJADO NINGUNA NO ES 'HACE MUCHOS DÍAS': es otra cosa, y peor")
    void nuncaSeBajoNinguna() {
        // La pantalla lo dice distinto, y por eso no se puede devolver un número grande: no hay número.
        assertThat(politica.diasSinBajar(List.of(), HOY)).isNull();
        assertThat(politica.diasSinBajar(List.of(fallido("2026-09-21T07:00:00Z")), HOY)).isNull();
        assertThat(politica.hayQueAvisar(List.of(), HOY)).isTrue();
    }

    @Test
    @DisplayName("se avisa a los más de siete días sin bajar una copia; a los siete todavía no")
    void cuandoSeAvisa() {
        assertThat(politica.hayQueAvisar(List.of(bueno("2026-09-20T07:00:00Z")), HOY)).isFalse();
        assertThat(politica.hayQueAvisar(List.of(bueno("2026-09-14T07:00:00Z")), HOY)).as("siete días").isFalse();
        assertThat(politica.hayQueAvisar(List.of(bueno("2026-09-13T07:00:00Z")), HOY)).as("ocho días").isTrue();
    }

    @Test
    @DisplayName("SI EL ÚLTIMO INTENTO FALLÓ SE AVISA, aunque ayer se haya bajado una bien")
    void elUltimoIntentoFallido() {
        // El dueño apretó el botón, no le llegó nada y pudo no darse cuenta. Tiene que verlo al entrar.
        assertThat(politica.hayQueAvisar(
                List.of(bueno("2026-09-20T07:00:00Z"), fallido("2026-09-21T07:00:00Z")), HOY)).isTrue();
    }

    @Test
    @DisplayName("la última buena es la más reciente que salió bien, aunque después haya intentos fallidos")
    void cualEsLaUltimaBuena() {
        Respaldo buena = bueno("2026-09-20T07:00:00Z");

        assertThat(politica.ultimaBuena(List.of(buena, fallido("2026-09-21T07:00:00Z")))).isEqualTo(buena);
        assertThat(politica.ultimaBuena(List.of(fallido("2026-09-21T07:00:00Z")))).isNull();
    }

    @Test
    @DisplayName("avisar el mismo día que se baja no es una política: se rechaza al configurarla")
    void politicaImposible() {
        assertThatThrownBy(() -> new PoliticaDeRespaldo(0, COLOMBIA))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("al menos un día");
        assertThatThrownBy(() -> new PoliticaDeRespaldo(7, null))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("zona");
    }
}
