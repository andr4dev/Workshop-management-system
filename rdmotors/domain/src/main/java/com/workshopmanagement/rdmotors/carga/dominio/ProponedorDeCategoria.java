package com.workshopmanagement.rdmotors.carga.dominio;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.workshopmanagement.rdmotors.compartido.dominio.TextoDeBusqueda;

/**
 * Propone la categoría de un repuesto por cómo empieza su descripción (spec 0012, RF-011).
 *
 * <p>Las descripciones de un proveedor de repuestos empiezan por lo que la pieza <b>es</b>: <i>"EMPAQUE CULATIN…"</i>,
 * <i>"BALINERA 6301…"</i>, <i>"PASTILLAS FRENO…"</i>. Eso alcanza para acertar la gran mayoría, que es lo que se
 * busca: <b>una propuesta, no una decisión</b>. Cada una queda marcada en la pre-carga y el dueño la cambia si no le
 * sirve — la categoría es para navegar el catálogo, no para encontrar la pieza (ver {@code Categoria}).
 *
 * <p>Gana el comienzo más largo: <i>"FILTRO GASOLINA"</i> se ve antes que <i>"FILTRO"</i>, y <i>"DISCO DE FRENO"</i>
 * antes que <i>"DISCO"</i>.
 *
 * <p>La tabla habla de las 16 categorías sembradas por su nombre. Si el dueño renombra o desactiva una, las
 * propuestas hacia ella simplemente dejan de salir: mejor vacío que equivocado.
 */
public final class ProponedorDeCategoria {

    private static final Map<String, String> POR_COMIENZO = new LinkedHashMap<>();

    static {
        comienzan("MOTOR", "BALANCIN", "ARBOL DE LEVAS", "CILINDRO", "TENSOR CADENILLA", "CADENILLA",
                "KIT PINONES LUBRICACION", "CUNA VOLANTE", "TAPON CARTER", "EJE CAMBIOS", "PEDAL CRANK", "EJE CRANK",
                "PALANCA CAMBIOS", "CENTRO DE CLUTCH", "DISCOS CLUTCH", "DISCO CLUTCH", "DADOS CLUTCH",
                "CLUTCH DE UNA VIA", "MANZANA CLUTCH", "AUTOMATICO");
        comienzan("EMPAQUES Y SELLOS", "EMPAQUE", "KIT EMPAQUES", "RETEN", "SELLO");
        comienzan("CONTROLES Y GUAYAS", "GUAYA", "CARRIL ACELERADOR", "MANIGUETA", "PINON VELOCIMETRO");
        comienzan("ELECTRICO", "COMANDO", "SWICHE", "RELAY", "BOBINA", "ESCOBILLAS", "MOTOR DE ARRANQUE", "PITO",
                "FLASHER", "CAPUCHON BUJIA", "BUJIA", "PORTA FUSIBLE", "SOCKET", "BANDA BATERIA");
        comienzan("ILUMINACION", "FAROLA", "BOMBILLO", "DIRECCIONAL", "TROMPO STOP");
        comienzan("TRANSMISION Y ARRASTRE", "KIT ARRASTRE", "CADENA", "UNION CADENA", "TENSOR CADENA",
                "PORTA SPROCKET", "BUJE PORTA SPROCKET", "KIT PERNOS SPROCKET", "DESLIZADOR CADENA",
                "CORREA TRANSMISION");
        comienzan("CARROCERIA", "DEFENSA", "BASE ESPEJO", "ESPEJO", "REPOSAPIE", "CAUCHO REPOSAPIE");
        comienzan("FRENOS", "BANDAS FRENO", "PASTILLAS FRENO", "BOMBA FRENO", "KIT REPARACION BOMBA FRENO",
                "MANGUERA FRENO", "DISCO DE FRENO", "LEVA FRENO", "PEDAL FRENO", "VARILLA FRENO",
                "KIT REPARACION VARILLA FRENO", "BARRA PORTA BANDAS", "PORTA BANDAS", "CAMPANA TRASERA");
        comienzan("SUSPENSION Y DIRECCION", "KIT TIJERA", "AMORTIGUADOR", "FUELLE TELESCOPICO",
                "JUEGO CUNAS DIRECCION", "CAUCHO BASE DIRECCION", "EJE RUEDA");
        comienzan("RODAMIENTOS Y BUJES", "BALINERA", "CAUCHO BUJE");
        comienzan("FILTROS", "FILTRO");
        comienzan("CARBURACION", "CARBURADOR", "DIAFRAGMA CARBURADOR", "LLAVE GASOLINA");
        comienzan("LLANTAS", "NEUMATICO", "VALVULA LLANTA");
    }

    private static void comienzan(String categoria, String... comienzos) {
        for (String comienzo : comienzos) {
            POR_COMIENZO.put(comienzo, categoria);
        }
    }

    /** Los comienzos, del más largo al más corto. */
    private static final List<String> EN_ORDEN = POR_COMIENZO.keySet().stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();

    private ProponedorDeCategoria() {
    }

    /** El nombre de la categoría sembrada que le corresponde a la descripción, o vacío si no se reconoce. */
    public static Optional<String> proponer(String descripcion) {
        if (descripcion == null || descripcion.isBlank()) {
            return Optional.empty();
        }
        // Sin tildes ni eñes, como busca el resto del sistema: "PIÑON" y "PINON" son la misma pieza.
        String limpia = TextoDeBusqueda.normalizar(descripcion.strip().replaceAll("\\s+", " "))
                .toUpperCase(Locale.ROOT);
        for (String comienzo : EN_ORDEN) {
            if (limpia.equals(comienzo) || limpia.startsWith(comienzo + " ")) {
                return Optional.of(POR_COMIENZO.get(comienzo));
            }
        }
        return Optional.empty();
    }
}
