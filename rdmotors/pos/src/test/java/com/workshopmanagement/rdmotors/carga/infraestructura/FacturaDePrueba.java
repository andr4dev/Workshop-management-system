package com.workshopmanagement.rdmotors.carga.infraestructura;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Una factura inventada con el diseño de Jotapartes, fabricada por la prueba (spec 0012, plan §"Dos reglas").
 *
 * <p>Reemplaza a la MAG477 en el repositorio, que es público: los datos son inventados, pero las posiciones de las
 * columnas son las de la factura real, y trae sus tres trampas — la descripción partida, el renglón repetido arriba
 * de la página siguiente, y el cuadro de totales en la última.
 */
final class FacturaDePrueba {

    record Renglon(String codigo, String descripcion, String continuacion, int cantidad, String unidad, long precio,
                   int descuento) {

        long total() {
            return BigDecimal.valueOf(precio).multiply(BigDecimal.valueOf(cantidad))
                    .multiply(BigDecimal.valueOf(100 - descuento))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact();
        }
    }

    /** Las columnas en las mismas x que la MAG477. */
    private static final float X_CODIGO = 14.2f;
    private static final float X_DESCRIPCION = 81.4f;
    private static final float X_UNIDAD = 349.3f;
    private static final float X_DESCUENTO = 462.6f;
    private static final float X_IVA = 558.4f;

    private final List<Renglon> renglones = new ArrayList<>();
    private int porPagina = 13;
    private String nit = "900576528";
    private float corrimientoDeCantidad = 0;
    private boolean conTexto = true;
    private String numero = "MAG999";

    static FacturaDePrueba nueva() {
        return new FacturaDePrueba();
    }

    FacturaDePrueba renglon(Renglon r) {
        renglones.add(r);
        return this;
    }

    FacturaDePrueba porPagina(int n) {
        porPagina = n;
        return this;
    }

    FacturaDePrueba deOtroProveedor() {
        nit = "800123456";
        return this;
    }

    /** Como si Jotapartes cambiara el diseño: la columna CANT. se mueve. */
    FacturaDePrueba conLaCantidadCorrida(float puntos) {
        corrimientoDeCantidad = puntos;
        return this;
    }

    /** Una página sin una sola letra: como un PDF escaneado. */
    FacturaDePrueba escaneada() {
        conTexto = false;
        return this;
    }

    long subtotal() {
        return renglones.stream().mapToLong(Renglon::total).sum();
    }

    int unidades() {
        return renglones.stream().mapToInt(Renglon::cantidad).sum();
    }

    long iva() {
        return BigDecimal.valueOf(subtotal()).multiply(new BigDecimal("0.19"))
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Los 25 renglones de siempre: suficientes para dos páginas y las tres trampas. */
    static FacturaDePrueba deVeinticinco() {
        return deVeinticinco("", "MAG999");
    }

    /**
     * Los mismos 25, con otros códigos y otro número de factura: para las pruebas que confirman, que dejan los
     * repuestos creados en la base que comparten.
     */
    static FacturaDePrueba deVeinticinco(String prefijo, String numero) {
        FacturaDePrueba f = nueva();
        f.numero = numero;
        for (int i = 1; i <= 25; i++) {
            String codigo = prefijo + String.format(Locale.ROOT, "9%02dZ%dK", i, i % 7);
            String continuacion = i == 4 ? "PARA PRUEBA LARGA" : i == 13 ? "CONTINUACION DEL ULTIMO" : null;
            f.renglon(new Renglon(codigo, "REPUESTO DE PRUEBA NUMERO " + i + " INOKI", continuacion,
                    1 + (i * 7) % 40, i % 5 == 0 ? "JGO" : "UND", 1_000L + i * 1_337L, i % 3 == 0 ? 20 : 18));
        }
        return f;
    }

    byte[] pdf() throws IOException {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDType1Font letra = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            if (!conTexto) {
                documento.addPage(new PDPage(PDRectangle.LETTER));
                documento.save(salida);
                return salida.toByteArray();
            }
            int paginas = Math.max(1, (renglones.size() + porPagina - 1) / porPagina);
            for (int p = 0; p < paginas; p++) {
                PDPage pagina = new PDPage(PDRectangle.LETTER);
                documento.addPage(pagina);
                try (PDPageContentStream dibujo = new PDPageContentStream(documento, pagina)) {
                    Lienzo lienzo = new Lienzo(dibujo, letra, pagina.getMediaBox().getHeight());
                    encabezado(lienzo);
                    float y = 395;
                    if (p > 0) {
                        // El último de la página anterior, repetido arriba de esta, sin su total.
                        Renglon anterior = renglones.get(p * porPagina - 1);
                        fila(lienzo, anterior, y, false);
                        if (anterior.continuacion() != null) {
                            lienzo.texto(X_DESCRIPCION, y + 8, anterior.continuacion());
                        }
                        y += anterior.continuacion() == null ? 11 : 19;
                    }
                    int desde = p * porPagina;
                    int hasta = Math.min(renglones.size(), desde + porPagina);
                    for (int i = desde; i < hasta; i++) {
                        Renglon r = renglones.get(i);
                        fila(lienzo, r, y, true);
                        if (r.continuacion() != null) {
                            lienzo.texto(X_DESCRIPCION, y + 8, r.continuacion());
                            y += 8;
                        }
                        y += 11;
                    }
                    if (p == paginas - 1) {
                        totales(lienzo, y + 20);
                    }
                    lienzo.texto(150, 720, "Autorización Numeración de Facturación No. 99999999 de prueba");
                }
            }
            documento.save(salida);
            return salida.toByteArray();
        }
    }

    private void encabezado(Lienzo l) throws IOException {
        l.texto(260, 60, "IMPORTADORA JOTAPARTES SAS");
        l.texto(285, 72, "NIT: " + nit + "  3216767465");
        l.texto(470, 90, "Número");
        l.texto(520, 90, numero);
        l.texto(470, 100, "Fecha:");
        l.texto(520, 100, "2026/09/20");
        l.texto(35.4f, 374, "ITEM/");
        l.texto(151.6f, 375.2f, "DESCRIPCION");
        l.texto(315.2f + corrimientoDeCantidad, 374, "CANT.");
        l.texto(355.0f, 375.2f, "UM");
        l.texto(387.0f, 375.2f, "PRECIO");
        l.texto(453.5f, 374, "%");
        l.texto(482.8f, 375.2f, "VALOR TOTAL");
        l.texto(545.7f, 375.2f, "IVA%");
        l.texto(20, 382, "REFERENCIA");
        l.texto(392, 382, "UNIT");
        l.texto(450, 382, "DESCTO");
    }

    private void fila(Lienzo l, Renglon r, float y, boolean conTotal) throws IOException {
        l.texto(X_CODIGO, y, r.codigo());
        l.texto(X_DESCRIPCION, y, r.descripcion());
        String cantidad = String.valueOf(r.cantidad());
        l.texto(341 - 3.9f * cantidad.length(), y, cantidad);
        l.texto(X_UNIDAD, y, r.unidad());
        l.texto(399.8f, y, pesos(r.precio()));
        l.texto(X_DESCUENTO, y, String.valueOf(r.descuento()));
        if (conTotal) {
            l.texto(505.3f, y, pesos(r.total()));
        }
        l.texto(X_IVA, y, "19 %");
    }

    private void totales(Lienzo l, float y) throws IOException {
        long base = subtotal();
        l.texto(60, y, "TOTAL BASE");
        l.texto(150, y, "DESCUENTOS");
        l.texto(240, y, "SUB-TOTAL");
        l.texto(330, y, "IMPUESTOS");
        l.texto(420, y, "RETENCIONES");
        l.texto(520, y, "TOTAL");
        l.texto(60, y + 10, pesos(base));
        l.texto(150, y + 10, "$0");
        l.texto(240, y + 10, pesos(base));
        l.texto(330, y + 10, pesos(iva()));
        l.texto(420, y + 10, "$0");
        l.texto(520, y + 10, pesos(base + iva()));
    }

    /** "$1.234.567", como los escribe la factura. */
    static String pesos(long valor) {
        return "$" + String.format(Locale.ROOT, "%,d", valor).replace(',', '.');
    }

    /** Escribe en coordenadas desde arriba, como las mide el lector. */
    private record Lienzo(PDPageContentStream dibujo, PDType1Font letra, float alto) {

        void texto(float x, float yDesdeArriba, String texto) throws IOException {
            dibujo.beginText();
            dibujo.setFont(letra, 7);
            dibujo.newLineAtOffset(x, alto - yDesdeArriba);
            dibujo.showText(texto);
            dibujo.endText();
        }
    }
}
