package com.workshopmanagement.rdmotors.carga.infraestructura;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.workshopmanagement.rdmotors.carga.dominio.FacturaNoReconocidaException;
import com.workshopmanagement.rdmotors.carga.dominio.RenglonLeido;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.TextoDeBusqueda;

/**
 * Las columnas de la plantilla, que comparten el Excel y el CSV (spec 0012, RF-001).
 *
 * <p>Las columnas se encuentran <b>por su título, no por su posición</b>: "Código", "CODIGO" y "referencia" son la
 * misma, y da igual si alguien movió la marca antes de la cantidad. Lo único obligatorio son las cuatro que hacen
 * falta para que haya un costo: código, descripción, cantidad y valor total.
 */
final class PlantillaDeCarga {

    enum Columna {
        CODIGO(true, "codigo", "referencia", "item", "item/referencia", "ref"),
        DESCRIPCION(true, "descripcion", "nombre"),
        CANTIDAD(true, "cantidad", "cant", "cant."),
        VALOR_TOTAL(true, "valor total", "total", "costo total", "valor"),
        PRECIO_UNITARIO(false, "precio unitario", "precio unit", "precio", "precio de lista"),
        DESCUENTO(false, "descuento", "% descuento", "% descto", "descto", "dcto", "% dcto"),
        UNIDAD(false, "um", "unidad", "unidad de medida"),
        MARCA(false, "marca"),
        CATEGORIA(false, "categoria");

        final boolean obligatoria;
        final List<String> titulos;

        Columna(boolean obligatoria, String... titulos) {
            this.obligatoria = obligatoria;
            this.titulos = List.of(titulos);
        }
    }

    /** Lo que trae una celda: un número de verdad (Excel) o un texto que hay que leer (CSV, o Excel escrito a mano). */
    record Celda(BigDecimal numero, String texto) {

        static Celda deTexto(String texto) {
            return new Celda(null, texto);
        }

        static Celda deNumero(BigDecimal numero) {
            return new Celda(numero, null);
        }

        String comoTexto() {
            if (numero != null) {
                return numero.stripTrailingZeros().toPlainString();
            }
            return texto == null ? null : texto.strip();
        }

        boolean vacia() {
            String t = comoTexto();
            return t == null || t.isEmpty();
        }
    }

    private final Map<Columna, Integer> dondeEsta;

    private PlantillaDeCarga(Map<Columna, Integer> dondeEsta) {
        this.dondeEsta = dondeEsta;
    }

    /**
     * Encuentra los títulos. Si una fila tiene a la vez algo que parece código y algo que parece cantidad, es la de
     * los títulos; si falta alguna obligatoria, se dice cuál.
     */
    static PlantillaDeCarga desdeTitulos(List<Celda> fila) {
        Map<Columna, Integer> dondeEsta = new EnumMap<>(Columna.class);
        for (int i = 0; i < fila.size(); i++) {
            String titulo = normalizar(fila.get(i).comoTexto());
            if (titulo.isEmpty()) {
                continue;
            }
            for (Columna c : Columna.values()) {
                if (!dondeEsta.containsKey(c) && c.titulos.contains(titulo)) {
                    dondeEsta.put(c, i);
                    break;
                }
            }
        }
        List<String> faltan = new ArrayList<>();
        for (Columna c : Columna.values()) {
            if (c.obligatoria && !dondeEsta.containsKey(c)) {
                faltan.add(c.titulos.getFirst().toUpperCase(Locale.ROOT));
            }
        }
        if (!faltan.isEmpty()) {
            throw new FacturaNoReconocidaException((faltan.size() == 1 ? "Falta la columna " : "Faltan las columnas ")
                    + String.join(", ", faltan) + ". Baja la plantilla para ver cómo va.");
        }
        return new PlantillaDeCarga(dondeEsta);
    }

    /**
     * Los títulos de la plantilla que se baja, en el orden del spec (RF-001): las cuatro obligatorias y las dos que
     * ahorran trabajo en la pre-carga. Separados por punto y coma, como los espera Excel en español.
     */
    static String titulosEnCsv() {
        return String.join(";", List.of(Columna.CODIGO, Columna.DESCRIPCION, Columna.CANTIDAD, Columna.VALOR_TOTAL,
                Columna.MARCA, Columna.CATEGORIA).stream()
                .map(c -> c.titulos.getFirst().toUpperCase(Locale.ROOT))
                .toList());
    }

    /** Si la fila parece la de los títulos: tiene algo que dice "código" y algo que dice "cantidad". */
    static boolean pareceTitulos(List<Celda> fila) {
        boolean codigo = false;
        boolean cantidad = false;
        for (Celda celda : fila) {
            String t = normalizar(celda.comoTexto());
            codigo |= Columna.CODIGO.titulos.contains(t);
            cantidad |= Columna.CANTIDAD.titulos.contains(t);
        }
        return codigo && cantidad;
    }

    /** Un renglón. Lo que no se entiende queda en {@code null}, para que la pre-carga lo marque. */
    RenglonLeido renglon(List<Celda> fila, String ubicacion) {
        return new RenglonLeido(
                ubicacion,
                texto(fila, Columna.CODIGO),
                texto(fila, Columna.DESCRIPCION),
                entero(fila, Columna.CANTIDAD),
                texto(fila, Columna.UNIDAD),
                pesos(fila, Columna.PRECIO_UNITARIO),
                numero(fila, Columna.DESCUENTO),
                pesos(fila, Columna.VALOR_TOTAL),
                texto(fila, Columna.MARCA),
                texto(fila, Columna.CATEGORIA));
    }

    /** Una fila sin nada en las columnas obligatorias es una fila vacía, no un renglón malo. */
    boolean vacia(List<Celda> fila) {
        for (Columna c : Columna.values()) {
            if (c.obligatoria && !celda(fila, c).vacia()) {
                return false;
            }
        }
        return true;
    }

    private Celda celda(List<Celda> fila, Columna columna) {
        Integer i = dondeEsta.get(columna);
        return i == null || i >= fila.size() ? Celda.deTexto(null) : fila.get(i);
    }

    private String texto(List<Celda> fila, Columna columna) {
        String t = celda(fila, columna).comoTexto();
        return t == null || t.isEmpty() ? null : t;
    }

    private Integer entero(List<Celda> fila, Columna columna) {
        Celda c = celda(fila, columna);
        if (c.numero() != null) {
            BigDecimal n = c.numero();
            if (n.stripTrailingZeros().scale() > 0) {
                return null;
            }
            try {
                return n.intValueExact();
            } catch (ArithmeticException noCabe) {
                return null;
            }
        }
        return NumerosColombianos.entero(c.texto());
    }

    private Dinero pesos(List<Celda> fila, Columna columna) {
        Celda c = celda(fila, columna);
        return c.numero() != null ? Dinero.de(c.numero()) : NumerosColombianos.pesos(c.texto());
    }

    private BigDecimal numero(List<Celda> fila, Columna columna) {
        Celda c = celda(fila, columna);
        return c.numero() != null ? c.numero() : NumerosColombianos.numero(c.texto());
    }

    private static String normalizar(String titulo) {
        if (titulo == null) {
            return "";
        }
        return TextoDeBusqueda.normalizar(titulo.strip().replaceAll("\\s+", " "));
    }
}
