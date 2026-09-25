package com.workshopmanagement.rdmotors.carga.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * Renglones y facturas con los números de la MAG477 (spec 0012), para las pruebas del borrador y de sus casos de uso.
 * Cada renglón cuadra su propia cuenta, como en la factura de verdad.
 */
public final class FacturasDePrueba {

    public static final String NIT_JOTAPARTES = "900576528";

    public static final UUID ELECTRICO = UUID.fromString("00000000-0000-0000-0000-00000000e1ec");
    public static final UUID EMPAQUES = UUID.fromString("00000000-0000-0000-0000-0000000e0a0e");
    public static final UUID MOTOR = UUID.fromString("00000000-0000-0000-0000-0000000a0707");
    public static final UUID FRENOS = UUID.fromString("00000000-0000-0000-0000-0000000f7e40");

    private FacturasDePrueba() {
    }

    /** 8 × $46.993 − 18% = $308.274. Costo con IVA $45.855,7575; sugerido $66.491, o $66.500 redondeado. */
    public static RenglonLeido bujia() {
        return renglon("pág. 26", "524XRE3IJ", "BUJIA IRIDIUM CR7HIX NGK", 8, "UND", 46_993, "18", 308_274);
    }

    /** Sin marca al final: la pre-carga la tiene que pedir. 1 × $7.280 − 15% = $6.188. */
    public static RenglonLeido kitEmpaques() {
        return renglon("pág. 3", "093AKTCLKI", "KIT EMPAQUES MEDIO AK150 TT/EVO NE/TTR-AK200 SM/XM", 1, "KIT", 7_280,
                "15", 6_188);
    }

    /** 5 × $9.744 − 18% = $39.950. */
    public static RenglonLeido tensor() {
        return renglon("pág. 2", "082T3S", "TENSOR CADENILLA CB110 INOKI", 5, "UND", 9_744, "18", 39_950);
    }

    public static RenglonLeido renglon(String ubicacion, String codigo, String descripcion, Integer cantidad,
                                       String unidad, long precio, String descuento, long total) {
        return new RenglonLeido(ubicacion, codigo, descripcion, cantidad, unidad, Dinero.de(precio),
                new BigDecimal(descuento), Dinero.de(total), null, null);
    }

    /** Un renglón de plantilla de Excel: sin precio de lista ni descuento, con marca y categoría escritas. */
    public static RenglonLeido deExcel(String fila, String codigo, String descripcion, Integer cantidad, Long total,
                                       String marca, String categoria) {
        return new RenglonLeido(fila, codigo, descripcion, cantidad, null, null, null,
                total == null ? null : Dinero.de(total), marca, categoria);
    }

    /** Un PDF de Jotapartes: el sub-total impreso es la suma de lo que trae, y el IVA el 19% de él. */
    public static FacturaLeida jotapartes(RenglonLeido... renglones) {
        long subtotal = Arrays.stream(renglones).mapToLong(r -> r.valorTotal().valor().longValueExact()).sum();
        return jotapartes(subtotal, renglones);
    }

    /** Con un sub-total impreso que la prueba escoge: para ver qué pasa cuando no cuadra. */
    public static FacturaLeida jotapartes(long subtotalImpreso, RenglonLeido... renglones) {
        Dinero iva = ReglaDePrecio.porDefecto().ivaDe(Dinero.de(subtotalImpreso));
        return new FacturaLeida(OrigenCarga.PDF_JOTAPARTES, List.of(renglones), Dinero.de(subtotalImpreso), iva,
                Dinero.de(subtotalImpreso).mas(iva), NIT_JOTAPARTES, "MAG477", LocalDate.of(2026, 8, 31));
    }

    public static FacturaLeida excel(RenglonLeido... renglones) {
        return FacturaLeida.soloRenglones(OrigenCarga.EXCEL, List.of(renglones));
    }

    /** Las categorías sembradas que usan estos renglones. */
    public static CategoriasConocidas categorias() {
        Map<UUID, String> activas = new LinkedHashMap<>();
        activas.put(ELECTRICO, "ELÉCTRICO");
        activas.put(EMPAQUES, "EMPAQUES Y SELLOS");
        activas.put(MOTOR, "MOTOR");
        activas.put(FRENOS, "FRENOS");
        return new CategoriasConocidas(activas);
    }

    public static ProponedorDeMarca marcas() {
        return new ProponedorDeMarca(List.of());
    }
}
