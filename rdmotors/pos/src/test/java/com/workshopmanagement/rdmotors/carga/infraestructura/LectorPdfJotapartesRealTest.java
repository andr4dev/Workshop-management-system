package com.workshopmanagement.rdmotors.carga.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.workshopmanagement.rdmotors.carga.dominio.FacturaLeida;
import com.workshopmanagement.rdmotors.carga.dominio.RenglonLeido;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * La factura MAG477 de verdad (spec 0012, fase 2).
 *
 * <p><b>El archivo no está en el repositorio, y no debe estar</b>: el repositorio es público y la factura trae el
 * nombre, la cédula, la dirección y el teléfono del dueño, además de los precios del proveedor. Esta prueba solo corre
 * si {@code RDMOTORS_FACTURA_REAL} dice dónde está; en cualquier otro equipo se salta. Lo que la reemplaza en el
 * repositorio es {@code LectorPdfJotapartesTest}, con facturas inventadas del mismo diseño.
 */
@EnabledIfEnvironmentVariable(named = "RDMOTORS_FACTURA_REAL", matches = ".+",
        disabledReason = "la factura real no está en el repositorio: RDMOTORS_FACTURA_REAL=<ruta al PDF> para correrla")
class LectorPdfJotapartesRealTest {

    private static FacturaLeida factura;

    @BeforeAll
    static void leer() throws Exception {
        factura = new LectorPdfJotapartes().leer(Files.readAllBytes(Path.of(System.getenv("RDMOTORS_FACTURA_REAL"))));
    }

    private static BigDecimal cuenta(RenglonLeido r) {
        return r.precioUnitario().valor().multiply(BigDecimal.valueOf(r.cantidad()))
                .multiply(BigDecimal.valueOf(100).subtract(r.descuentoPct()))
                .divide(BigDecimal.valueOf(100));
    }

    @Test
    @DisplayName("LOS 592 RENGLONES, Y LOS 592 CUADRAN SU PROPIA CUENTA")
    void todosCuadran() {
        assertThat(factura.renglones()).hasSize(592);
        for (RenglonLeido r : factura.renglones()) {
            assertThat(r.cantidad()).as(r.codigo()).isNotNull();
            assertThat(r.valorTotal()).as(r.codigo()).isNotNull();
            assertThat(cuenta(r).subtract(r.valorTotal().valor()).abs())
                    .as("%s (%s): %d × %s − %s%%", r.codigo(), r.ubicacion(), r.cantidad(), r.precioUnitario(),
                            r.descuentoPct())
                    .isLessThanOrEqualTo(BigDecimal.ONE);
        }
    }

    @Test
    @DisplayName("la suma es el sub-total impreso, y lo impreso se lee: $14.729.523 + $2.798.609 = $17.528.132")
    void loImpreso() {
        long suma = factura.renglones().stream().mapToLong(r -> r.valorTotal().valor().longValueExact()).sum();

        assertThat(suma).isEqualTo(14_729_523);
        assertThat(factura.subtotalImpreso()).isEqualTo(Dinero.de(14_729_523));
        assertThat(factura.ivaImpreso()).isEqualTo(Dinero.de(2_798_609));
        assertThat(factura.totalImpreso()).isEqualTo(Dinero.de(17_528_132));
        assertThat(factura.numeroFactura()).isEqualTo("MAG477");
        assertThat(factura.fecha()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(factura.nitProveedor()).isEqualTo("900576528");
        assertThat(factura.renglones().stream().mapToInt(RenglonLeido::cantidad).sum()).isEqualTo(2_269);
    }

    @Test
    @DisplayName("LOS RENGLONES REPETIDOS ENTRE PÁGINAS CUENTAN UNA VEZ, y su descripción no se duplica")
    void repetidosEntrePaginas() {
        List<String> repetidos = factura.renglones().stream()
                .collect(Collectors.groupingBy(RenglonLeido::codigo, Collectors.counting()))
                .entrySet().stream().filter(e -> e.getValue() > 1).map(e -> e.getKey()).toList();
        assertThat(repetidos).isEmpty();

        var porCodigo = factura.renglones().stream()
                .collect(Collectors.toMap(RenglonLeido::codigo, Function.identity()));
        // El prototipo lo dejaba con "TRAIL-XL200-XR200 INOKI" dos veces.
        assertThat(porCodigo.get("196H17K").descripcion())
                .isEqualTo("JUEGO CUNAS DIRECCION NXR125-XR125L-XLR125-XL125 TRAIL-XL200-XR200 INOKI");
        assertThat(porCodigo.get("203B59ITK").descripcion()).endsWith("CON BASE Y TROMPO INOKI");
        assertThat(porCodigo.get("245B30K").descripcion()).endsWith("PULSAR 180 INOKI");
    }

    @Test
    @DisplayName("las descripciones partidas quedan enteras, y la bujía tiene sus números")
    void partidas() {
        var porCodigo = factura.renglones().stream()
                .collect(Collectors.toMap(RenglonLeido::codigo, Function.identity()));

        assertThat(porCodigo.get("093AKTCLKI").descripcion())
                .isEqualTo("KIT EMPAQUES MEDIO AK150 TT/EVO NE/TTR-AK200 SM/XM");
        assertThat(porCodigo.get("082T3S").valorTotal()).isEqualTo(Dinero.de(39_950));

        RenglonLeido bujia = porCodigo.get("524XRE3IJ");
        assertThat(bujia.cantidad()).isEqualTo(8);
        assertThat(bujia.unidad()).isEqualTo("UND");
        assertThat(bujia.valorTotal()).isEqualTo(Dinero.de(308_274));
        assertThat(bujia.ubicacion()).isEqualTo("pág. 26");
    }
}
