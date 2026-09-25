package com.workshopmanagement.rdmotors.carga.infraestructura;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Un .xlsx mínimo armado a mano para las pruebas: la biblioteca que se usa solo lee, y el formato es un zip con unos
 * pocos XML. Un {@link BigDecimal} va como celda numérica —lo que Excel guarda cuando se escribe un número—; un
 * {@link String}, como texto; {@code null}, como celda vacía.
 */
final class ExcelDePrueba {

    private final List<List<Object>> filas = new ArrayList<>();

    static ExcelDePrueba nuevo() {
        return new ExcelDePrueba();
    }

    ExcelDePrueba fila(Object... celdas) {
        filas.add(Arrays.asList(celdas));
        return this;
    }

    byte[] xlsx() throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            parte(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml" ContentType="application/xml"/>
                    <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                    <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                    </Types>""");
            parte(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>""");
            parte(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <sheets><sheet name="Carga" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>""");
            parte(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                    </Relationships>""");
            parte(zip, "xl/worksheets/sheet1.xml", hoja());
        }
        return salida.toByteArray();
    }

    private String hoja() {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""");
        for (int f = 0; f < filas.size(); f++) {
            int numero = f + 1;
            xml.append("<row r=\"").append(numero).append("\">");
            List<Object> celdas = filas.get(f);
            for (int c = 0; c < celdas.size(); c++) {
                Object valor = celdas.get(c);
                String ref = (char) ('A' + c) + String.valueOf(numero);
                if (valor instanceof BigDecimal n) {
                    xml.append("<c r=\"").append(ref).append("\"><v>").append(n.toPlainString()).append("</v></c>");
                } else if (valor instanceof Formula formula) {
                    xml.append("<c r=\"").append(ref).append("\"><f>").append(formula.expresion())
                            .append("</f><v>").append(formula.resultado().toPlainString()).append("</v></c>");
                } else if (valor != null) {
                    xml.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t>")
                            .append(escapar(valor.toString())).append("</t></is></c>");
                }
            }
            xml.append("</row>");
        }
        return xml.append("</sheetData></worksheet>").toString();
    }

    /** Una fórmula con el resultado que Excel guardó la última vez que la calculó. */
    record Formula(String expresion, BigDecimal resultado) {
    }

    private static String escapar(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void parte(ZipOutputStream zip, String nombre, String contenido) throws IOException {
        zip.putNextEntry(new ZipEntry(nombre));
        zip.write(contenido.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
