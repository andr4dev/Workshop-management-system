package com.workshopmanagement.rdmotors.carga.infraestructura;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.workshopmanagement.rdmotors.carga.dominio.FacturaLeida;
import com.workshopmanagement.rdmotors.carga.dominio.FacturaNoReconocidaException;
import com.workshopmanagement.rdmotors.carga.dominio.OrigenCarga;
import com.workshopmanagement.rdmotors.carga.dominio.RenglonLeido;
import com.workshopmanagement.rdmotors.carga.dominio.puerto.LectorDeFactura;
import com.workshopmanagement.rdmotors.carga.infraestructura.PlantillaDeCarga.Celda;

/**
 * ADAPTADOR — la plantilla guardada como texto separado (spec 0012, RF-001 y RF-002).
 *
 * <p>Dos trampas de los CSV que salen de un Excel en español, y las dos se resuelven solas:
 * <ul>
 *   <li><b>El separador es punto y coma</b>, no coma: la coma ya es el decimal. Se mira cuál de los dos aparece más en
 *       la fila de títulos.</li>
 *   <li><b>La codificación</b>: "CSV UTF-8" trae una marca al principio; el "CSV" a secas de Excel en Windows viene en
 *       Windows-1252, y leído como UTF-8 convierte "PIÑON" en basura. Se prueba UTF-8 en estricto y, si no cuadra,
 *       Windows-1252.</li>
 * </ul>
 */
@Component
@Order(3)
class LectorCsv implements LectorDeFactura {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    @Override
    public boolean reconoce(String nombreArchivo, byte[] contenido) {
        if (nombreArchivo == null) {
            return false;
        }
        String nombre = nombreArchivo.toLowerCase(Locale.ROOT);
        return nombre.endsWith(".csv") || nombre.endsWith(".txt");
    }

    @Override
    public FacturaLeida leer(byte[] contenido) {
        String texto = decodificar(contenido);
        String[] lineas = texto.split("\\r\\n|\\n|\\r", -1);

        PlantillaDeCarga plantilla = null;
        char separador = ';';
        List<RenglonLeido> renglones = new ArrayList<>();
        for (int n = 0; n < lineas.length; n++) {
            String linea = lineas[n];
            if (linea.isBlank()) {
                continue;
            }
            if (plantilla == null) {
                separador = separadorDe(linea);
                List<Celda> celdas = campos(linea, separador);
                if (PlantillaDeCarga.pareceTitulos(celdas)) {
                    plantilla = PlantillaDeCarga.desdeTitulos(celdas);
                }
                continue;
            }
            List<Celda> celdas = campos(linea, separador);
            if (!plantilla.vacia(celdas)) {
                renglones.add(plantilla.renglon(celdas, "fila " + (n + 1)));
            }
        }
        if (plantilla == null) {
            throw new FacturaNoReconocidaException("No encontré los títulos de la plantilla (CODIGO, DESCRIPCION, "
                    + "CANTIDAD, VALOR TOTAL) en el archivo. Baja la plantilla para ver cómo va.");
        }
        if (renglones.isEmpty()) {
            throw new FacturaNoReconocidaException("El archivo tiene los títulos pero ningún renglón debajo.");
        }
        return FacturaLeida.soloRenglones(OrigenCarga.CSV, renglones);
    }

    static String decodificar(byte[] contenido) {
        if (contenido.length >= 3 && (contenido[0] & 0xFF) == 0xEF && (contenido[1] & 0xFF) == 0xBB
                && (contenido[2] & 0xFF) == 0xBF) {
            return new String(contenido, 3, contenido.length - 3, StandardCharsets.UTF_8);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(contenido)).toString();
        } catch (CharacterCodingException noEsUtf8) {
            return new String(contenido, WINDOWS_1252);
        }
    }

    /** El que más aparece fuera de comillas entre punto y coma, coma y tabulador. */
    static char separadorDe(String linea) {
        int puntoYComa = 0;
        int coma = 0;
        int tab = 0;
        boolean entreComillas = false;
        for (char c : linea.toCharArray()) {
            if (c == '"') {
                entreComillas = !entreComillas;
            } else if (!entreComillas) {
                if (c == ';') puntoYComa++;
                else if (c == ',') coma++;
                else if (c == '\t') tab++;
            }
        }
        if (tab > puntoYComa && tab > coma) return '\t';
        return coma > puntoYComa ? ',' : ';';
    }

    /** Los campos de una línea, con comillas y comillas dobladas ({@code "dice ""hola"""}). */
    static List<Celda> campos(String linea, char separador) {
        List<Celda> campos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (entreComillas) {
                if (c == '"' && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                    actual.append('"');
                    i++;
                } else if (c == '"') {
                    entreComillas = false;
                } else {
                    actual.append(c);
                }
            } else if (c == '"') {
                entreComillas = true;
            } else if (c == separador) {
                campos.add(Celda.deTexto(actual.toString()));
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        campos.add(Celda.deTexto(actual.toString()));
        return campos;
    }
}
