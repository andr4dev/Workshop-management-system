package com.workshopmanagement.rdmotors.carga.infraestructura;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.workshopmanagement.rdmotors.carga.dominio.FacturaLeida;
import com.workshopmanagement.rdmotors.carga.dominio.FacturaNoReconocidaException;
import com.workshopmanagement.rdmotors.carga.dominio.OrigenCarga;
import com.workshopmanagement.rdmotors.carga.dominio.RenglonLeido;
import com.workshopmanagement.rdmotors.carga.dominio.puerto.LectorDeFactura;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * ADAPTADOR — la factura en PDF de Importadora Jotapartes, tal como llega por correo (spec 0012, decisión 3).
 *
 * <h2>Por posición, no por orden del texto</h2>
 *
 * El PDF trae el texto con la posición exacta de cada palabra, y eso es lo que se usa: una palabra pertenece a la
 * columna sobre la que está impresa. Leer el texto "en orden" falla con esta factura, porque cuando una descripción
 * no cabe en un renglón la parte que sobra, el descuento y el total quedan en otra línea, y el orden los revuelve.
 *
 * <h2>Solo el diseño que se conoce</h2>
 *
 * Se reconoce por el NIT de Jotapartes y por los títulos de sus columnas <b>en el lugar donde se midieron</b> (la
 * MAG477, 2026-09-25). Si cualquiera de las dos cosas no está, no se lee: un lector que adivina columnas lee mal sin
 * avisar, y la factura siguiente de otro proveedor tendría los números corridos. Leer mal es peor que no leer.
 *
 * <h2>Las tres trampas que se encontraron con la factura real</h2>
 * <ol>
 *   <li><b>Descripciones partidas</b>: la línea de abajo, sin código, es continuación del renglón de arriba.</li>
 *   <li><b>El renglón repetido entre páginas</b>: el último renglón de una página vuelve a estar, escondido, arriba de
 *       la siguiente. Se reconoce por tener el código del último de la página anterior, y se cuenta una vez — y su
 *       continuación no se agrega dos veces.</li>
 *   <li><b>El cuadro de totales</b> de la última página no es un renglón: la tabla termina donde él empieza.</li>
 * </ol>
 *
 * Lo que lo hace seguro no es este código sino la factura misma: cada renglón se comprueba con su propia cuenta y la
 * suma con el sub-total impreso. Un error de lectura aquí sale marcado en la pre-carga, no pasa callado.
 */
@Component
@Order(1)
class LectorPdfJotapartes implements LectorDeFactura {

    static final String NIT_JOTAPARTES = "900576528";

    /** Dónde empiezan los títulos de las columnas en la MAG477. Van centrados sobre su columna. */
    private static final Map<String, Float> TITULOS = new LinkedHashMap<>();

    static {
        TITULOS.put("DESCRIPCION", 151.6f);
        TITULOS.put("CANT.", 315.2f);
        TITULOS.put("UM", 355.0f);
        TITULOS.put("PRECIO", 387.0f);
        TITULOS.put("VALOR TOTAL", 482.8f);
        TITULOS.put("IVA%", 545.7f);
    }

    /** Cuánto se puede haber corrido un título y seguir siendo el mismo diseño. */
    private static final float TOLERANCIA_TITULO = 8f;

    /** Las palabras de las dos líneas de títulos: todo lo que está a su altura no es un renglón. */
    private static final Set<String> VOCABULARIO_TITULOS = Set.of("ITEM/", "REFERENCIA", "DESCRIPCION", "CANT.", "UM",
            "PRECIO", "UNIT", "%", "DESCTO", "VALOR TOTAL", "IVA%");

    /**
     * Dónde termina cada columna para los VALORES, que no van centrados como los títulos: el código a la izquierda, la
     * cantidad y la plata a la derecha. Medidos en la MAG477; que el diseño no cambió lo garantiza {@link #TITULOS}.
     */
    private enum Columna { CODIGO, DESCRIPCION, CANTIDAD, UNIDAD, PRECIO, DESCUENTO, TOTAL, IVA }

    private static Columna columnaDe(float x) {
        if (x < 78) return Columna.CODIGO;
        if (x < 310) return Columna.DESCRIPCION;
        if (x < 347) return Columna.CANTIDAD;
        if (x < 385) return Columna.UNIDAD;
        if (x < 450) return Columna.PRECIO;
        if (x < 480) return Columna.DESCUENTO;
        if (x < 540) return Columna.TOTAL;
        return Columna.IVA;
    }

    private static final Pattern CODIGO = Pattern.compile("[0-9A-Z][0-9A-Z\\-]*");
    private static final Pattern PLATA = Pattern.compile("\\$[\\d.]+");
    private static final Pattern NUMERO_FACTURA = Pattern.compile("[A-Z]{2,5}\\d{1,8}");
    private static final Pattern FECHA = Pattern.compile("\\d{4}/\\d{2}/\\d{2}");

    /** Separación entre letras de una misma palabra frente a la separación entre columnas, en puntos. */
    private static final float ESPACIO_ENTRE_PALABRAS = 1.0f;
    private static final float ESPACIO_ENTRE_COLUMNAS = 5.0f;
    private static final float MISMA_LINEA = 2.5f;

    @Override
    public boolean reconoce(String nombreArchivo, byte[] contenido) {
        return contenido.length >= 5 && contenido[0] == '%' && contenido[1] == 'P' && contenido[2] == 'D'
                && contenido[3] == 'F' && contenido[4] == '-';
    }

    @Override
    public FacturaLeida leer(byte[] contenido) {
        List<List<Fragmento>> paginas = fragmentos(contenido);
        if (paginas.stream().allMatch(List::isEmpty)) {
            throw new FacturaNoReconocidaException("Este PDF no trae texto: parece una foto escaneada. Pásalo a la "
                    + "plantilla de Excel.");
        }
        boolean esDeJotapartes = paginas.stream().flatMap(List::stream)
                .anyMatch(f -> f.texto().replace(".", "").contains(NIT_JOTAPARTES));
        if (!esDeJotapartes) {
            throw new FacturaNoReconocidaException("No reconozco el diseño de esta factura: por ahora solo sé leer las "
                    + "de Importadora Jotapartes. Pásala a la plantilla de Excel.");
        }

        List<RenglonLeido> renglones = new ArrayList<>();
        Borrador actual = null;
        boolean saltandoRepetido = false;
        boolean algunaTabla = false;
        for (int n = 0; n < paginas.size(); n++) {
            List<Fragmento> pagina = paginas.get(n);
            Optional<Float> inicio = inicioDeTabla(pagina, n + 1);
            if (inicio.isEmpty()) {
                continue;
            }
            algunaTabla = true;
            float fin = finDeTabla(pagina);
            boolean primeraDeLaPagina = true;
            for (List<Fragmento> linea : lineas(pagina, inicio.get(), fin)) {
                Map<Columna, String> celdas = celdas(linea);
                String codigo = celdas.get(Columna.CODIGO);
                boolean esRenglon = codigo != null && CODIGO.matcher(codigo).matches()
                        && celdas.containsKey(Columna.CANTIDAD);
                if (esRenglon) {
                    if (primeraDeLaPagina && actual != null && actual.codigo.equals(codigo)) {
                        // El último de la página anterior, repetido y escondido arriba de esta.
                        saltandoRepetido = true;
                    } else {
                        if (actual != null) {
                            renglones.add(actual.aRenglon());
                        }
                        actual = new Borrador(codigo, "pág. " + (n + 1), celdas);
                        saltandoRepetido = false;
                    }
                    primeraDeLaPagina = false;
                } else if (actual != null) {
                    actual.continuar(celdas, saltandoRepetido);
                }
            }
        }
        if (!algunaTabla) {
            throw new FacturaNoReconocidaException("Esta factura de Jotapartes no tiene la tabla de productos donde la "
                    + "espero. Pásala a la plantilla de Excel.");
        }
        if (actual != null) {
            renglones.add(actual.aRenglon());
        }

        List<Fragmento> primera = paginas.getFirst();
        Map<String, Dinero> totales = totales(paginas);
        return new FacturaLeida(OrigenCarga.PDF_JOTAPARTES, renglones, totales.get("SUB-TOTAL"),
                totales.get("IMPUESTOS"), totales.get("TOTAL"), NIT_JOTAPARTES, numeroFactura(primera),
                fecha(primera));
    }

    // ── Los renglones ────────────────────────────────────────────────────────────

    /** Un renglón mientras se arma: la primera línea trae el código, las siguientes lo pueden completar. */
    private static final class Borrador {
        final String codigo;
        final String ubicacion;
        final StringBuilder descripcion = new StringBuilder();
        String cantidad;
        String unidad;
        String precio;
        String descuento;
        String total;

        Borrador(String codigo, String ubicacion, Map<Columna, String> celdas) {
            this.codigo = codigo;
            this.ubicacion = ubicacion;
            agregar(celdas.get(Columna.DESCRIPCION));
            cantidad = celdas.get(Columna.CANTIDAD);
            unidad = celdas.get(Columna.UNIDAD);
            precio = celdas.get(Columna.PRECIO);
            descuento = celdas.get(Columna.DESCUENTO);
            total = celdas.get(Columna.TOTAL);
        }

        /**
         * Una línea sin código: más descripción, o el descuento y el total que quedaron un poco más abajo. Si viene
         * detrás del renglón repetido, la descripción solo se agrega si la página anterior no la traía ya.
         */
        void continuar(Map<Columna, String> celdas, boolean detrasDeRepetido) {
            String mas = celdas.get(Columna.DESCRIPCION);
            if (mas != null && !(detrasDeRepetido && descripcion.toString().contains(mas))) {
                agregar(mas);
            }
            if (cantidad == null) cantidad = celdas.get(Columna.CANTIDAD);
            if (unidad == null) unidad = celdas.get(Columna.UNIDAD);
            if (precio == null) precio = celdas.get(Columna.PRECIO);
            if (descuento == null) descuento = celdas.get(Columna.DESCUENTO);
            if (total == null) total = celdas.get(Columna.TOTAL);
        }

        private void agregar(String texto) {
            if (texto == null || texto.isBlank()) {
                return;
            }
            if (!descripcion.isEmpty()) {
                descripcion.append(' ');
            }
            descripcion.append(texto.strip());
        }

        RenglonLeido aRenglon() {
            return new RenglonLeido(ubicacion, codigo, descripcion.toString(), NumerosColombianos.entero(cantidad),
                    unidad, NumerosColombianos.pesos(precio), NumerosColombianos.numero(descuento),
                    NumerosColombianos.pesos(total), null, null);
        }
    }

    private static Map<Columna, String> celdas(List<Fragmento> linea) {
        Map<Columna, String> celdas = new EnumMap<>(Columna.class);
        for (Fragmento f : linea) {
            celdas.merge(columnaDe(f.x()), f.texto(), (a, b) -> a + " " + b);
        }
        return celdas;
    }

    /**
     * Dónde empieza la tabla de esta página: debajo de sus dos líneas de títulos. Vacío si la página no tiene tabla (la
     * de firmas, al final). Si la tiene pero los títulos no están donde se midieron, el diseño cambió y no se lee.
     */
    private static Optional<Float> inicioDeTabla(List<Fragmento> pagina, int numero) {
        Optional<Fragmento> descripcion = pagina.stream().filter(f -> f.texto().equals("DESCRIPCION")).findFirst();
        if (descripcion.isEmpty()) {
            return Optional.empty();
        }
        float y = descripcion.get().y();
        for (Map.Entry<String, Float> titulo : TITULOS.entrySet()) {
            boolean enSuLugar = pagina.stream().anyMatch(f -> f.texto().equals(titulo.getKey())
                    && Math.abs(f.y() - y) < 4 && Math.abs(f.x() - titulo.getValue()) <= TOLERANCIA_TITULO);
            if (!enSuLugar) {
                throw new FacturaNoReconocidaException("Esta factura de Jotapartes tiene un diseño distinto al que "
                        + "conozco (la columna " + titulo.getKey() + " de la página " + numero + " no está donde "
                        + "espero). No la leo a ciegas: pásala a la plantilla de Excel.");
            }
        }
        float abajo = pagina.stream()
                .filter(f -> VOCABULARIO_TITULOS.contains(f.texto()) && f.y() >= y - 4 && f.y() <= y + 15)
                .map(Fragmento::y).max(Float::compare).orElse(y);
        return Optional.of(abajo + 1);
    }

    /** Dónde termina: en el pie de la autorización de la DIAN, o antes, en el cuadro de totales. */
    private static float finDeTabla(List<Fragmento> pagina) {
        float fin = Float.MAX_VALUE;
        for (Fragmento f : pagina) {
            if (f.texto().startsWith("Autorizaci") || f.texto().equals("TOTAL BASE")) {
                fin = Math.min(fin, f.y());
            }
        }
        return fin - 1;
    }

    /** Las líneas entre el inicio y el fin, de arriba abajo; cada una con sus fragmentos de izquierda a derecha. */
    private static List<List<Fragmento>> lineas(List<Fragmento> pagina, float inicio, float fin) {
        List<Fragmento> cuerpo = pagina.stream()
                .filter(f -> f.y() > inicio && f.y() < fin)
                .sorted(Comparator.comparing(Fragmento::y).thenComparing(Fragmento::x))
                .toList();
        List<List<Fragmento>> lineas = new ArrayList<>();
        float yLinea = Float.NaN;
        for (Fragmento f : cuerpo) {
            if (lineas.isEmpty() || Math.abs(f.y() - yLinea) > MISMA_LINEA) {
                lineas.add(new ArrayList<>());
                yLinea = f.y();
            }
            lineas.getLast().add(f);
        }
        lineas.forEach(l -> l.sort(Comparator.comparing(Fragmento::x)));
        return lineas;
    }

    // ── Lo impreso fuera de la tabla ─────────────────────────────────────────────

    /** Sub-total, impuestos y total: cada título con la cifra que tiene justo debajo. */
    private static Map<String, Dinero> totales(List<List<Fragmento>> paginas) {
        Map<String, Dinero> totales = new LinkedHashMap<>();
        for (List<Fragmento> pagina : paginas) {
            Optional<Fragmento> subtotal = pagina.stream().filter(f -> f.texto().equals("SUB-TOTAL")).findFirst();
            if (subtotal.isEmpty()) {
                continue;
            }
            float y = subtotal.get().y();
            List<Fragmento> cifras = pagina.stream()
                    .filter(f -> PLATA.matcher(f.texto()).matches() && f.y() > y + 2 && f.y() < y + 16)
                    .toList();
            for (String titulo : List.of("SUB-TOTAL", "IMPUESTOS", "TOTAL")) {
                pagina.stream()
                        .filter(f -> f.texto().equals(titulo) && Math.abs(f.y() - y) < 3)
                        .findFirst()
                        .flatMap(t -> cifras.stream().min(Comparator.comparing(c -> Math.abs(c.x() - t.x()))))
                        .map(c -> NumerosColombianos.pesos(c.texto()))
                        .ifPresent(valor -> totales.put(titulo, valor));
            }
        }
        return totales;
    }

    /** "MAG477": lo que está a la derecha de "Número", en la misma línea. */
    private static String numeroFactura(List<Fragmento> primera) {
        return primera.stream().filter(f -> f.texto().startsWith("Número")).findFirst()
                .flatMap(etiqueta -> primera.stream()
                        .filter(f -> Math.abs(f.y() - etiqueta.y()) < 3 && f.x() > etiqueta.x())
                        .filter(f -> NUMERO_FACTURA.matcher(f.texto()).matches())
                        .min(Comparator.comparing(Fragmento::x)))
                .map(Fragmento::texto)
                .orElse(null);
    }

    private static LocalDate fecha(List<Fragmento> primera) {
        return primera.stream().map(Fragmento::texto).filter(t -> FECHA.matcher(t).matches()).findFirst()
                .map(t -> {
                    try {
                        return LocalDate.parse(t, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
                    } catch (DateTimeParseException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    // ── Sacar el texto con su posición ───────────────────────────────────────────

    /**
     * Un pedazo de texto y dónde está. {@code y} se mide desde arriba de la página, así que crece hacia abajo.
     */
    record Fragmento(float x, float y, String texto) {
    }

    /**
     * Las letras de cada página con su posición, sin que PDFBox las reordene ni descarte las repetidas. Cada página se
     * agrupa en fragmentos apenas termina y sus letras se sueltan: guardadas las 32 páginas de la MAG477 a la vez, las
     * letras solas pasaban de 32 MB, y en la nube la aplicación entera tiene 128.
     */
    private static final class Recolector extends PDFTextStripper {

        final List<List<Fragmento>> paginas = new ArrayList<>();
        private final List<TextPosition> letras = new ArrayList<>();

        @Override
        protected void startPage(PDPage page) {
            letras.clear();
        }

        @Override
        protected void processTextPosition(TextPosition letra) {
            letras.add(letra);
        }

        @Override
        protected void endPage(PDPage page) {
            paginas.add(agrupar(letras));
            letras.clear();
        }
    }

    static List<List<Fragmento>> fragmentos(byte[] contenido) {
        try (PDDocument documento = Loader.loadPDF(contenido)) {
            Recolector recolector = new Recolector();
            recolector.getText(documento);
            return recolector.paginas;
        } catch (IOException e) {
            throw new FacturaNoReconocidaException("Ese PDF no se puede abrir: puede estar dañado o protegido con "
                    + "contraseña.");
        }
    }

    /**
     * Las letras sueltas en fragmentos: misma línea y sin un hueco de columna en medio. Un hueco del tamaño de un
     * espacio entre palabras se vuelve un espacio, porque hay PDFs que no dibujan el espacio como letra.
     */
    private static List<Fragmento> agrupar(List<TextPosition> letras) {
        List<TextPosition> ordenadas = new ArrayList<>(letras);
        ordenadas.sort(Comparator.comparing(TextPosition::getYDirAdj).thenComparing(TextPosition::getXDirAdj));

        List<List<TextPosition>> lineas = new ArrayList<>();
        float yLinea = Float.NaN;
        for (TextPosition letra : ordenadas) {
            if (lineas.isEmpty() || Math.abs(letra.getYDirAdj() - yLinea) > MISMA_LINEA) {
                lineas.add(new ArrayList<>());
                yLinea = letra.getYDirAdj();
            }
            lineas.getLast().add(letra);
        }

        List<Fragmento> fragmentos = new ArrayList<>();
        for (List<TextPosition> linea : lineas) {
            linea.sort(Comparator.comparing(TextPosition::getXDirAdj));
            StringBuilder texto = new StringBuilder();
            float x = 0;
            float y = 0;
            float finAnterior = Float.NaN;
            for (TextPosition letra : linea) {
                float hueco = Float.isNaN(finAnterior) ? 0 : letra.getXDirAdj() - finAnterior;
                if (!texto.isEmpty() && hueco > ESPACIO_ENTRE_COLUMNAS) {
                    agregar(fragmentos, x, y, texto);
                    texto.setLength(0);
                }
                if (texto.isEmpty()) {
                    x = letra.getXDirAdj();
                    y = letra.getYDirAdj();
                } else if (hueco > ESPACIO_ENTRE_PALABRAS && texto.charAt(texto.length() - 1) != ' ') {
                    texto.append(' ');
                }
                texto.append(letra.getUnicode());
                finAnterior = letra.getXDirAdj() + letra.getWidthDirAdj();
            }
            agregar(fragmentos, x, y, texto);
        }
        return fragmentos;
    }

    private static void agregar(List<Fragmento> fragmentos, float x, float y, StringBuilder texto) {
        String limpio = texto.toString().replaceAll("\\s+", " ").strip();
        if (!limpio.isEmpty()) {
            fragmentos.add(new Fragmento(x, y, limpio));
        }
    }

}
