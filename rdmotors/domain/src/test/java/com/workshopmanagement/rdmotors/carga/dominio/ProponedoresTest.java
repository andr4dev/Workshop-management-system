package com.workshopmanagement.rdmotors.carga.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Marca y categoría propuestas desde la descripción (spec 0012, RF-010 y RF-011). */
class ProponedoresTest {

    @Nested
    @DisplayName("la marca")
    class Marca {

        private final ProponedorDeMarca proponedor = new ProponedorDeMarca(List.of());

        @ParameterizedTest(name = "«{0}» → {1} · «{2}»")
        @CsvSource(delimiter = '|', value = {
                "PALANCA CAMBIOS BOXER 100 INOKI     | INOKI      | PALANCA CAMBIOS BOXER 100",
                "BUJIA CR7HSA NGK                    | NGK        | BUJIA CR7HSA",
                "BUJIA CR7HSA KANUNI                 | KANUNI     | BUJIA CR7HSA",
                "RETEN 14-22-5 CAMBIOS CBF150 ARX    | ARX        | RETEN 14-22-5 CAMBIOS CBF150",
                "BANDAS FRENO TRAS. CG125 TITAN JAPAN| JAPAN      | BANDAS FRENO TRAS. CG125 TITAN",
                "BOBINA STATOR FZ 16 2.0-SZR 150 LEO | LEO        | BOBINA STATOR FZ 16 2.0-SZR 150",
        })
        void reconoce(String descripcion, String marca, String nombre) {
            assertThat(proponedor.proponer(descripcion))
                    .contains(new ProponedorDeMarca.Propuesta(marca, nombre));
        }

        @Test
        @DisplayName("«KOYO JAPON» gana sobre «JAPON»: la marca más larga primero")
        void laMasLarga() {
            assertThat(proponedor.proponer("BALINERA 6304 C3 KOYO JAPON"))
                    .contains(new ProponedorDeMarca.Propuesta("KOYO JAPON", "BALINERA 6304 C3"));
        }

        @Test
        @DisplayName("«NAL.» se guarda como NACIONAL, que en el mostrador se entiende")
        void nacional() {
            assertThat(proponedor.proponer("GUAYA VELOCIMETRO CB110 NAL."))
                    .contains(new ProponedorDeMarca.Propuesta("NACIONAL", "GUAYA VELOCIMETRO CB110"));
        }

        @Test
        @DisplayName("NO ADIVINA: si no termina en una marca conocida, no propone — «CB110» o «HALOGENO» no son marcas")
        void noAdivina() {
            assertThat(proponedor.proponer("EMPAQUE CULATA LAMINA ACERADA CB110")).isEmpty();
            assertThat(proponedor.proponer("BOMBILLO FAROLA P43T 12V 35/35W HALOGENO")).isEmpty();
            assertThat(proponedor.proponer("")).isEmpty();
        }

        @Test
        @DisplayName("una marca no se encuentra dentro de otra palabra: «LEONES» no es LEO")
        void palabraCompleta() {
            assertThat(proponedor.proponer("CALCOMANIA LEONES")).isEmpty();
        }

        @Test
        @DisplayName("también reconoce las marcas que ya están en uso en el sistema")
        void lasEnUso() {
            ProponedorDeMarca conLasDelSistema = new ProponedorDeMarca(List.of("Yamaha Original"));

            assertThat(conLasDelSistema.proponer("FILTRO ACEITE CRYPTON  yamaha original "))
                    .contains(new ProponedorDeMarca.Propuesta("YAMAHA ORIGINAL", "FILTRO ACEITE CRYPTON"));
        }
    }

    @Nested
    @DisplayName("la categoría")
    class Categoria {

        @ParameterizedTest(name = "«{0}» → {1}")
        @CsvSource(delimiter = '|', value = {
                "EMPAQUE CULATIN CBF125/150-CB150 INVICTA  | EMPAQUES Y SELLOS",
                "KIT EMPAQUES MEDIO AK150 EVO              | EMPAQUES Y SELLOS",
                "RETEN 17-29-5 PIÑON SALIDA ECO            | EMPAQUES Y SELLOS",
                "BALINERA 6301 2RS INOKI                   | RODAMIENTOS Y BUJES",
                "PASTILLAS FRENO DEL. AK110/125            | FRENOS",
                "DISCO DE FRENO DELANTERO FZ16 INOKI       | FRENOS",
                "DISCO CLUTCH XRE300 NIRIN                 | MOTOR",
                "FILTRO GASOLINA CARTON REDONDO UNIVERSAL  | FILTROS",
                "GUAYA CLUTCH XR150 L NAL.                 | CONTROLES Y GUAYAS",
                "PIÑON VELOCIMETRO CBF150 INOKI            | CONTROLES Y GUAYAS",
                "CUÑA VOLANTE 16MM DT125/175 JAPAN         | MOTOR",
                "CAPUCHON BUJIA XR150L INOKI               | ELECTRICO",
                "BOMBILLO STOP 12V 21/5W                   | ILUMINACION",
                "KIT ARRASTRE 428H X 132 40T/14T FZ 16     | TRANSMISION Y ARRASTRE",
                "NEUMATICO 110/70-17 TR4 INOKI             | LLANTAS",
                "KIT TIJERA TEFLON FZ16                    | SUSPENSION Y DIRECCION",
                "CARBURADOR AK110 INOKI                    | CARBURACION",
        })
        void propone(String descripcion, String categoria) {
            assertThat(ProponedorDeCategoria.proponer(descripcion)).contains(categoria);
        }

        @Test
        @DisplayName("gana el comienzo más largo: «MOTOR DE ARRANQUE» es eléctrico aunque diga MOTOR")
        void elMasLargo() {
            assertThat(ProponedorDeCategoria.proponer("MOTOR DE ARRANQUE CBF150 INOKI")).contains("ELECTRICO");
        }

        @Test
        @DisplayName("si no la reconoce no propone nada, y un comienzo tiene que ser palabra entera")
        void noReconoce() {
            assertThat(ProponedorDeCategoria.proponer("ACCESORIO RARO SIN FAMILIA")).isEmpty();
            assertThat(ProponedorDeCategoria.proponer("EMPAQUETADURA ESPECIAL")).as("no es «EMPAQUE»").isEmpty();
            assertThat(ProponedorDeCategoria.proponer(null)).isEmpty();
        }
    }
}
