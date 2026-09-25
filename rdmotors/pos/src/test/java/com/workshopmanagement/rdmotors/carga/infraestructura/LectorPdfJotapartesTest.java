package com.workshopmanagement.rdmotors.carga.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.carga.dominio.FacturaLeida;
import com.workshopmanagement.rdmotors.carga.dominio.FacturaNoReconocidaException;
import com.workshopmanagement.rdmotors.carga.dominio.OrigenCarga;
import com.workshopmanagement.rdmotors.carga.dominio.RenglonLeido;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * El lector de Jotapartes con facturas inventadas del mismo diseño (spec 0012, fase 2). La MAG477 de verdad la prueba
 * {@link LectorPdfJotapartesRealTest}, que solo corre donde está el archivo.
 */
class LectorPdfJotapartesTest {

    private final LectorPdfJotapartes lector = new LectorPdfJotapartes();

    private static Map<String, RenglonLeido> porCodigo(FacturaLeida factura) {
        return factura.renglones().stream().collect(Collectors.toMap(RenglonLeido::codigo, Function.identity()));
    }

    @Test
    @DisplayName("LOS 25 RENGLONES DE DOS PÁGINAS: cada uno una vez, en orden, con su cuenta cuadrada")
    void veinticinco() throws Exception {
        FacturaDePrueba inventada = FacturaDePrueba.deVeinticinco();

        FacturaLeida factura = lector.leer(inventada.pdf());

        assertThat(factura.origen()).isEqualTo(OrigenCarga.PDF_JOTAPARTES);
        assertThat(factura.renglones()).hasSize(25);
        assertThat(factura.renglones()).extracting(RenglonLeido::codigo).doesNotHaveDuplicates()
                .startsWith("901Z1K", "902Z2K").endsWith("925Z4K");
        for (RenglonLeido r : factura.renglones()) {
            BigDecimal cuenta = r.precioUnitario().valor().multiply(BigDecimal.valueOf(r.cantidad()))
                    .multiply(BigDecimal.valueOf(100).subtract(r.descuentoPct())).divide(BigDecimal.valueOf(100));
            assertThat(cuenta.subtract(r.valorTotal().valor()).abs()).as(r.codigo())
                    .isLessThanOrEqualTo(BigDecimal.ONE);
        }
    }

    @Test
    @DisplayName("un renglón leído trae todo: código, descripción, cantidad, unidad, precio, descuento y total")
    void unRenglon() throws Exception {
        FacturaLeida factura = lector.leer(FacturaDePrueba.deVeinticinco().pdf());

        // El 5: cantidad 1 + 35 = 36, JGO, $7.685 con 18 %.
        RenglonLeido quinto = porCodigo(factura).get("905Z5K");
        assertThat(quinto.descripcion()).isEqualTo("REPUESTO DE PRUEBA NUMERO 5 INOKI");
        assertThat(quinto.cantidad()).isEqualTo(36);
        assertThat(quinto.unidad()).isEqualTo("JGO");
        assertThat(quinto.precioUnitario()).isEqualTo(Dinero.de(7_685));
        assertThat(quinto.descuentoPct()).isEqualByComparingTo("18");
        assertThat(quinto.valorTotal()).isEqualTo(Dinero.de(226_861));
        assertThat(quinto.ubicacion()).isEqualTo("pág. 1");
        assertThat(quinto.marca()).isNull();
        assertThat(quinto.categoria()).isNull();
    }

    @Test
    @DisplayName("LA DESCRIPCIÓN PARTIDA EN DOS LÍNEAS queda entera")
    void partida() throws Exception {
        RenglonLeido cuarto = porCodigo(lector.leer(FacturaDePrueba.deVeinticinco().pdf())).get("904Z4K");

        assertThat(cuarto.descripcion()).isEqualTo("REPUESTO DE PRUEBA NUMERO 4 INOKI PARA PRUEBA LARGA");
        assertThat(cuarto.valorTotal()).isNotNull();
    }

    @Test
    @DisplayName("EL RENGLÓN REPETIDO ARRIBA DE LA PÁGINA 2 cuenta una vez, y su continuación no se pega dos veces")
    void repetido() throws Exception {
        FacturaLeida factura = lector.leer(FacturaDePrueba.deVeinticinco().pdf());
        Map<String, RenglonLeido> porCodigo = porCodigo(factura);

        RenglonLeido treceavo = porCodigo.get("913Z6K");
        assertThat(treceavo.descripcion()).isEqualTo("REPUESTO DE PRUEBA NUMERO 13 INOKI CONTINUACION DEL ULTIMO");
        assertThat(treceavo.ubicacion()).isEqualTo("pág. 1");
        assertThat(porCodigo.get("914Z0K").ubicacion()).isEqualTo("pág. 2");
    }

    @Test
    @DisplayName("el cuadro de totales no es un renglón, y se lee: sub-total, impuestos, total, número y fecha")
    void loImpreso() throws Exception {
        FacturaDePrueba inventada = FacturaDePrueba.deVeinticinco();

        FacturaLeida factura = lector.leer(inventada.pdf());

        long suma = factura.renglones().stream().mapToLong(r -> r.valorTotal().valor().longValueExact()).sum();
        assertThat(suma).isEqualTo(inventada.subtotal());
        assertThat(factura.subtotalImpreso()).isEqualTo(Dinero.de(inventada.subtotal()));
        assertThat(factura.ivaImpreso()).isEqualTo(Dinero.de(inventada.iva()));
        assertThat(factura.totalImpreso()).isEqualTo(Dinero.de(inventada.subtotal() + inventada.iva()));
        assertThat(factura.numeroFactura()).isEqualTo("MAG999");
        assertThat(factura.fecha()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(factura.nitProveedor()).isEqualTo("900576528");
    }

    @Test
    @DisplayName("una factura de una sola página no busca repetidos que no hay")
    void unaPagina() throws Exception {
        FacturaDePrueba inventada = FacturaDePrueba.deVeinticinco().porPagina(40);

        FacturaLeida factura = lector.leer(inventada.pdf());

        assertThat(factura.renglones()).hasSize(25);
        assertThat(factura.subtotalImpreso()).isEqualTo(Dinero.de(inventada.subtotal()));
    }

    @Test
    @DisplayName("LA FACTURA DE OTRO PROVEEDOR no se lee: se pide la plantilla")
    void otroProveedor() {
        assertThatThrownBy(() -> lector.leer(FacturaDePrueba.deVeinticinco().deOtroProveedor().pdf()))
                .isInstanceOf(FacturaNoReconocidaException.class)
                .hasMessageContaining("solo sé leer las de Importadora Jotapartes");
    }

    @Test
    @DisplayName("SI JOTAPARTES CAMBIA EL DISEÑO no se lee a ciegas: dice qué columna se movió")
    void disenoCambiado() {
        assertThatThrownBy(() -> lector.leer(FacturaDePrueba.deVeinticinco().conLaCantidadCorrida(30).pdf()))
                .isInstanceOf(FacturaNoReconocidaException.class)
                .hasMessageContaining("la columna CANT. de la página 1");
    }

    @Test
    @DisplayName("un PDF sin texto —escaneado— se reconoce como tal")
    void escaneado() {
        assertThatThrownBy(() -> lector.leer(FacturaDePrueba.nueva().escaneada().pdf()))
                .isInstanceOf(FacturaNoReconocidaException.class)
                .hasMessageContaining("parece una foto escaneada");
    }

    @Test
    @DisplayName("un PDF dañado no revienta: dice que no se puede abrir")
    void danado() {
        byte[] danado = "%PDF-1.7 esto no es un pdf".getBytes(StandardCharsets.US_ASCII);

        assertThat(lector.reconoce("factura.pdf", danado)).isTrue();
        assertThatThrownBy(() -> lector.leer(danado))
                .isInstanceOf(FacturaNoReconocidaException.class)
                .hasMessageContaining("no se puede abrir");
    }

    @Test
    @DisplayName("se reconoce por el contenido, no por el nombre: un Excel que se llama .pdf no es suyo")
    void reconoce() throws Exception {
        assertThat(lector.reconoce("cualquier-nombre", FacturaDePrueba.deVeinticinco().pdf())).isTrue();
        assertThat(lector.reconoce("factura.pdf", new byte[] {'P', 'K', 3, 4, 0})).isFalse();
        assertThat(lector.reconoce("factura.pdf", new byte[0])).isFalse();
    }
}
