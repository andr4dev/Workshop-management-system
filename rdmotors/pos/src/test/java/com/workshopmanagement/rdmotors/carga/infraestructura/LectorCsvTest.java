package com.workshopmanagement.rdmotors.carga.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.carga.dominio.FacturaLeida;
import com.workshopmanagement.rdmotors.carga.dominio.FacturaNoReconocidaException;
import com.workshopmanagement.rdmotors.carga.dominio.OrigenCarga;
import com.workshopmanagement.rdmotors.carga.dominio.RenglonLeido;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/** La plantilla guardada como CSV desde un Excel en español (spec 0012, RF-001 y RF-002). */
class LectorCsvTest {

    private final LectorCsv lector = new LectorCsv();

    @Test
    @DisplayName("EL CSV DE UN EXCEL EN ESPAÑOL: punto y coma, y plata con puntos de miles")
    void puntoYComa() {
        String csv = """
                CODIGO;DESCRIPCION;CANTIDAD;VALOR TOTAL;MARCA
                524XRE3IJ;BUJIA IRIDIUM;8;$308.274;NGK
                082T3S;TENSOR CADENILLA;5;39.950;
                """;

        FacturaLeida factura = lector.leer(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(factura.origen()).isEqualTo(OrigenCarga.CSV);
        assertThat(factura.renglones()).hasSize(2);
        RenglonLeido bujia = factura.renglones().getFirst();
        assertThat(bujia.valorTotal()).isEqualTo(Dinero.de(308_274));
        assertThat(bujia.cantidad()).isEqualTo(8);
        assertThat(bujia.marca()).isEqualTo("NGK");
        assertThat(bujia.ubicacion()).isEqualTo("fila 2");
        assertThat(factura.renglones().get(1).valorTotal()).isEqualTo(Dinero.de(39_950));
        assertThat(factura.renglones().get(1).marca()).isNull();
    }

    @Test
    @DisplayName("con coma de separador y comillas: la coma dentro de las comillas es del texto")
    void comaYComillas() {
        String csv = """
                codigo,descripcion,cantidad,valor total
                A1,"PIÑON 14T, ""CAJA"" 428",2,"12.943"
                """;

        RenglonLeido renglon = lector.leer(csv.getBytes(StandardCharsets.UTF_8)).renglones().getFirst();

        assertThat(renglon.descripcion()).isEqualTo("PIÑON 14T, \"CAJA\" 428");
        assertThat(renglon.valorTotal()).isEqualTo(Dinero.de(12_943));
    }

    @Test
    @DisplayName("LA EÑE SOBREVIVE: \"CSV UTF-8\" con su marca, y el \"CSV\" a secas de Excel en Windows-1252")
    void codificaciones() {
        String csv = "CODIGO;DESCRIPCION;CANTIDAD;VALOR TOTAL\r\nA1;PIÑON 14T;1;1000\r\n";
        byte[] conMarca = ("﻿" + csv).getBytes(StandardCharsets.UTF_8);
        byte[] windows = csv.getBytes(Charset.forName("windows-1252"));

        assertThat(lector.leer(conMarca).renglones().getFirst().descripcion()).isEqualTo("PIÑON 14T");
        assertThat(lector.leer(windows).renglones().getFirst().descripcion()).isEqualTo("PIÑON 14T");
        assertThat(lector.leer(conMarca).renglones().getFirst().codigo()).isEqualTo("A1");
    }

    @Test
    @DisplayName("con tabulador, y lo que no se entiende queda vacío para que la pre-carga lo marque — nunca un cero")
    void tabuladorYNoEntendido() {
        String csv = "CODIGO\tDESCRIPCION\tCANTIDAD\tVALOR TOTAL\nA1\tUNO\tdos\tmil pesos\n";

        RenglonLeido renglon = lector.leer(csv.getBytes(StandardCharsets.UTF_8)).renglones().getFirst();

        assertThat(renglon.codigo()).isEqualTo("A1");
        assertThat(renglon.cantidad()).isNull();
        assertThat(renglon.valorTotal()).isNull();
    }

    @Test
    @DisplayName("líneas en blanco y filas de puros separadores no son renglones")
    void vacias() {
        String csv = "\nCODIGO;DESCRIPCION;CANTIDAD;VALOR TOTAL\nA1;UNO;1;100\n;;;\n\nA2;DOS;1;200\n";

        FacturaLeida factura = lector.leer(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(factura.renglones()).extracting(RenglonLeido::codigo).containsExactly("A1", "A2");
    }

    @Test
    @DisplayName("sin la columna del total se dice cuál falta; sin títulos, que no se encontraron")
    void errores() {
        byte[] sinTotal = "CODIGO;DESCRIPCION;CANTIDAD\nA1;UNO;1\n".getBytes(StandardCharsets.UTF_8);
        byte[] sinTitulos = "A1;UNO;1;100\n".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> lector.leer(sinTotal))
                .isInstanceOf(FacturaNoReconocidaException.class).hasMessageContaining("Falta la columna VALOR TOTAL");
        assertThatThrownBy(() -> lector.leer(sinTitulos))
                .isInstanceOf(FacturaNoReconocidaException.class).hasMessageContaining("No encontré los títulos");
    }

    @Test
    @DisplayName("se reconoce por la extensión .csv o .txt")
    void reconoce() {
        assertThat(lector.reconoce("inventario.CSV", new byte[0])).isTrue();
        assertThat(lector.reconoce("inventario.txt", new byte[0])).isTrue();
        assertThat(lector.reconoce("inventario.xlsx", new byte[0])).isFalse();
        assertThat(lector.reconoce(null, new byte[0])).isFalse();
    }
}
