package com.workshopmanagement.rdmotors.carga.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.carga.dominio.FacturaLeida;
import com.workshopmanagement.rdmotors.carga.dominio.FacturaNoReconocidaException;
import com.workshopmanagement.rdmotors.carga.dominio.OrigenCarga;
import com.workshopmanagement.rdmotors.carga.dominio.RenglonLeido;
import com.workshopmanagement.rdmotors.carga.infraestructura.ExcelDePrueba.Formula;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/** La plantilla en Excel (spec 0012, RF-001 y RF-002). */
class LectorExcelTest {

    private final LectorExcel lector = new LectorExcel();

    private static BigDecimal n(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    @DisplayName("LA PLANTILLA: los títulos se encuentran por su nombre, y los números de Excel se toman como números")
    void plantilla() throws Exception {
        byte[] excel = ExcelDePrueba.nuevo()
                .fila("Código", "Descripción", "Cantidad", "Valor total", "Marca", "Categoría")
                .fila("524XRE3IJ", "BUJIA IRIDIUM", n("8"), n("308274"), "NGK", "ELECTRICO")
                .fila("082T3S", "TENSOR CADENILLA", n("5"), n("39950"), null, null)
                .xlsx();

        FacturaLeida factura = lector.leer(excel);

        assertThat(factura.origen()).isEqualTo(OrigenCarga.EXCEL);
        assertThat(factura.subtotalImpreso()).isNull();
        assertThat(factura.renglones()).hasSize(2);
        RenglonLeido bujia = factura.renglones().getFirst();
        assertThat(bujia.codigo()).isEqualTo("524XRE3IJ");
        assertThat(bujia.descripcion()).isEqualTo("BUJIA IRIDIUM");
        assertThat(bujia.cantidad()).isEqualTo(8);
        assertThat(bujia.valorTotal()).isEqualTo(Dinero.de(308_274));
        assertThat(bujia.marca()).isEqualTo("NGK");
        assertThat(bujia.categoria()).isEqualTo("ELECTRICO");
        assertThat(bujia.ubicacion()).isEqualTo("fila 2");
        assertThat(factura.renglones().get(1).marca()).isNull();
    }

    @Test
    @DisplayName("LAS COLUMNAS EN OTRO ORDEN y con otros nombres de uso común se encuentran igual")
    void otroOrden() throws Exception {
        byte[] excel = ExcelDePrueba.nuevo()
                .fila("TOTAL", "UM", "REFERENCIA", "CANT.", "Precio unitario", "% Descto", "NOMBRE")
                .fila(n("39950"), "UND", "082T3S", n("5"), n("9745"), n("18"), "TENSOR CADENILLA")
                .xlsx();

        RenglonLeido renglon = lector.leer(excel).renglones().getFirst();

        assertThat(renglon.codigo()).isEqualTo("082T3S");
        assertThat(renglon.descripcion()).isEqualTo("TENSOR CADENILLA");
        assertThat(renglon.cantidad()).isEqualTo(5);
        assertThat(renglon.unidad()).isEqualTo("UND");
        assertThat(renglon.precioUnitario()).isEqualTo(Dinero.de(9_745));
        assertThat(renglon.descuentoPct()).isEqualByComparingTo("18");
        assertThat(renglon.valorTotal()).isEqualTo(Dinero.de(39_950));
    }

    @Test
    @DisplayName("LA PLATA ESCRITA COMO TEXTO a la colombiana —\"$12.943\"— son doce mil, no doce pesos")
    void plataComoTexto() throws Exception {
        byte[] excel = ExcelDePrueba.nuevo()
                .fila("CODIGO", "DESCRIPCION", "CANTIDAD", "VALOR TOTAL")
                .fila("A1", "UNO", "2", "$12.943")
                .fila("A2", "DOS", n("3"), "1.234.567")
                .xlsx();

        FacturaLeida factura = lector.leer(excel);

        assertThat(factura.renglones().get(0).valorTotal()).isEqualTo(Dinero.de(12_943));
        assertThat(factura.renglones().get(0).cantidad()).isEqualTo(2);
        assertThat(factura.renglones().get(1).valorTotal()).isEqualTo(Dinero.de(1_234_567));
    }

    @Test
    @DisplayName("UNA FÓRMULA se lee por el valor que Excel calculó, y una cantidad con decimales queda marcada")
    void formulaYDecimales() throws Exception {
        byte[] excel = ExcelDePrueba.nuevo()
                .fila("CODIGO", "DESCRIPCION", "CANTIDAD", "VALOR TOTAL")
                .fila("A1", "UNO", n("4"), new Formula("C2*10000", n("40000")))
                .fila("A2", "DOS", n("2.5"), n("1000"))
                .xlsx();

        FacturaLeida factura = lector.leer(excel);

        assertThat(factura.renglones().get(0).valorTotal()).isEqualTo(Dinero.de(40_000));
        assertThat(factura.renglones().get(1).cantidad()).isNull();
    }

    @Test
    @DisplayName("un título arriba de la tabla y filas vacías en medio no estorban")
    void encabezadoYVacias() throws Exception {
        byte[] excel = ExcelDePrueba.nuevo()
                .fila("MERCANCÍA SIN FACTURA — BODEGA")
                .fila()
                .fila("CODIGO", "DESCRIPCION", "CANTIDAD", "VALOR TOTAL")
                .fila("A1", "UNO", n("1"), n("100"))
                .fila(null, null, null, null)
                .fila("A2", "DOS", n("1"), n("200"))
                .xlsx();

        FacturaLeida factura = lector.leer(excel);

        assertThat(factura.renglones()).extracting(RenglonLeido::codigo).containsExactly("A1", "A2");
        assertThat(factura.renglones().get(1).ubicacion()).isEqualTo("fila 6");
    }

    @Test
    @DisplayName("SI FALTA UNA COLUMNA OBLIGATORIA se dice cuál")
    void faltaColumna() throws Exception {
        byte[] excel = ExcelDePrueba.nuevo()
                .fila("CODIGO", "DESCRIPCION", "CANTIDAD", "MARCA")
                .fila("A1", "UNO", n("1"), "NGK")
                .xlsx();

        assertThatThrownBy(() -> lector.leer(excel))
                .isInstanceOf(FacturaNoReconocidaException.class)
                .hasMessageContaining("Falta la columna VALOR TOTAL");
    }

    @Test
    @DisplayName("sin títulos, o con títulos y sin renglones, no hay nada que cargar")
    void sinNada() throws Exception {
        byte[] sinTitulos = ExcelDePrueba.nuevo().fila("A1", "UNO", n("1"), n("100")).xlsx();
        byte[] soloTitulos = ExcelDePrueba.nuevo().fila("CODIGO", "DESCRIPCION", "CANTIDAD", "VALOR TOTAL").xlsx();

        assertThatThrownBy(() -> lector.leer(sinTitulos))
                .isInstanceOf(FacturaNoReconocidaException.class).hasMessageContaining("No encontré los títulos");
        assertThatThrownBy(() -> lector.leer(soloTitulos))
                .isInstanceOf(FacturaNoReconocidaException.class).hasMessageContaining("ningún renglón");
    }

    @Test
    @DisplayName("se reconoce el .xlsx por nombre y contenido; un zip cualquiera dañado dice que no se puede abrir")
    void reconoce() throws Exception {
        byte[] excel = ExcelDePrueba.nuevo().fila("CODIGO").xlsx();
        byte[] zipRoto = {'P', 'K', 3, 4, 0, 0, 0};

        assertThat(lector.reconoce("inventario.XLSX", excel)).isTrue();
        assertThat(lector.reconoce("inventario.xls", excel)).isFalse();
        assertThat(lector.reconoce("inventario.xlsx", "CODIGO;CANTIDAD".getBytes())).isFalse();
        assertThatThrownBy(() -> lector.leer(zipRoto))
                .isInstanceOf(FacturaNoReconocidaException.class).hasMessageContaining("no se puede abrir");
    }
}
