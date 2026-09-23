package com.workshopmanagement.rdmotors.clientes.dominio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import com.workshopmanagement.rdmotors.compartido.dominio.Motivo;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.TextoDeBusqueda;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Alguien que le compra a la tienda (spec 0008). Para el historial basta el nombre; <b>para fiarle hacen falta el
 * nombre, la cédula o NIT y el celular</b> (decisión 2): sin la cédula hay dos "Juan" y no se sabe quién debe, y sin
 * el celular no hay a quién cobrarle.
 *
 * <p><b>La cédula no se repite</b>, y se compara sin puntos, guiones ni espacios: "1.234.567-8" y "12345678" son la
 * misma. La hace única la base, con la forma normalizada.
 *
 * <p><b>No se borra.</b> Al que no paga se le cierra el fiado (RF-005): sigue comprando de contado y abonando.
 */
@Entity
@Table(name = "cliente")
@Getter
public class Cliente {

    public static final String TIPO_AUDITORIA = "CLIENTE";
    static final int LARGO_NOMBRE = 120;
    static final int LARGO_DOCUMENTO = 30;
    static final int LARGO_CELULAR = 30;
    static final int LARGO_DIRECCION = 200;
    static final int LARGO_NOTA = 300;

    /** Letras y números, de 5 a 15: una cédula, un NIT con su dígito, una cédula de extranjería o un pasaporte. */
    private static final Pattern DOCUMENTO_VALIDO = Pattern.compile("[A-Z0-9]{5,15}");
    /** De 7 a 15 dígitos: un celular o un fijo. */
    private static final Pattern CELULAR_VALIDO = Pattern.compile("\\d{7,15}");

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** {@code Long} y no {@code long}: nulo es lo que le dice a Spring Data que el cliente es nuevo. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "nombre", nullable = false, length = LARGO_NOMBRE)
    private String nombre;

    /** Sin mayúsculas ni tildes: así se busca. */
    @Column(name = "nombre_normalizado", nullable = false, length = LARGO_NOMBRE)
    private String nombreNormalizado;

    /** Como se escribió: "1.234.567-8". */
    @Column(name = "documento", length = LARGO_DOCUMENTO)
    private String documento;

    /** Solo letras y números, en mayúsculas: lo que la base no deja repetir. */
    @Column(name = "documento_normalizado", length = LARGO_DOCUMENTO)
    private String documentoNormalizado;

    @Column(name = "celular", length = LARGO_CELULAR)
    private String celular;

    @Column(name = "celular_normalizado", length = 20)
    private String celularNormalizado;

    @Column(name = "direccion", length = LARGO_DIRECCION)
    private String direccion;

    /** "El del taller de la 5". */
    @Column(name = "nota", length = LARGO_NOTA)
    private String nota;

    @Column(name = "fiado_cerrado", nullable = false)
    private boolean fiadoCerrado;

    @Column(name = "motivo_fiado_cerrado", length = Motivo.LARGO_MAXIMO)
    private String motivoFiadoCerrado;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    @Column(name = "creado_por_id", nullable = false, updatable = false)
    private UUID creadoPorId;

    protected Cliente() {
        // JPA
    }

    /** Solo el nombre es obligatorio. Lo demás se puede completar después, incluso si ya se le fió. */
    public static Cliente nuevo(DatosCliente datos, UUID creadoPorId, Instant cuando) {
        if (datos == null) {
            throw new ReglaDeNegocioException("Faltan los datos del cliente");
        }
        if (creadoPorId == null) {
            throw new ReglaDeNegocioException("Falta quién crea el cliente");
        }
        Cliente cliente = new Cliente();
        cliente.id = UUID.randomUUID();
        cliente.creadoPorId = creadoPorId;
        cliente.creadoEn = cuando;
        cliente.poner(datos);
        return cliente;
    }

    // ── Fiar ─────────────────────────────────────────────────────────────────

    /**
     * Los datos que le faltan, en palabras: "la cédula", "el celular". Vacío si no le falta nada.
     *
     * <p><b>No impide fiarle</b> (decisión 2, cambiada el 2026-09-21 por el dueño): con el cliente enfrente, el
     * mostrador no siempre puede pedir la cédula, y frenar la venta por eso era peor que fiar con lo que hay. Es
     * un aviso para completarlos cuando se pueda: sin celular no hay a quién llamarle a cobrar, y sin cédula dos
     * "Juan" son el mismo renglón de la cartera.
     */
    public List<String> datosQueFaltan() {
        List<String> falta = new ArrayList<>();
        if (documento == null) falta.add("la cédula");
        if (celular == null) falta.add("el celular");
        return falta;
    }

    /** Con el fiado abierto. Lo único que impide fiarle es que el administrador se lo haya cerrado (RF-005). */
    public boolean sePuedeFiar() {
        return !fiadoCerrado;
    }

    public void exigirQueSePuedaFiar() {
        if (fiadoCerrado) {
            throw new ReglaDeNegocioException(
                    "A " + nombre + " no se le fía: lo cerró el administrador. Puede pagar de contado");
        }
    }

    /** No se le fía más; lo que debe lo sigue debiendo y puede abonar (RF-005). */
    public void cerrarFiado(String motivo) {
        String limpio = Motivo.exigir(motivo);
        if (fiadoCerrado) {
            throw new ReglaDeNegocioException("A " + nombre + " ya se le había cerrado el fiado");
        }
        this.fiadoCerrado = true;
        this.motivoFiadoCerrado = limpio;
    }

    public void abrirFiado() {
        if (!fiadoCerrado) {
            throw new ReglaDeNegocioException("A " + nombre + " se le puede fiar: el fiado no está cerrado");
        }
        this.fiadoCerrado = false;
        this.motivoFiadoCerrado = null;
    }

    // ── Sus datos ────────────────────────────────────────────────────────────

    /**
     * Si con estos datos cambiaría algo que <b>ya estaba escrito</b>: un nombre distinto, otra cédula, otro celular, o
     * borrar uno. Completar lo que faltaba no cuenta. Lo usa quien decide si hace falta el administrador (RF-004).
     */
    public boolean cambiaLoEscrito(DatosCliente datos) {
        return !nombre.equals(limpiarNombre(datos.nombre()))
                || cambia(documentoNormalizado, normalizarDocumento(datos.documento()))
                || cambia(celularNormalizado, normalizarCelular(datos.celular()))
                || cambia(direccion, limpio(datos.direccion()))
                || cambia(nota, limpio(datos.nota()));
    }

    private static boolean cambia(String escrito, String nuevo) {
        return escrito != null && !Objects.equals(escrito, nuevo);
    }

    /** Reemplaza los datos. Si puede hacerlo quien lo pide lo decide el caso de uso con {@link #cambiaLoEscrito}. */
    public void actualizar(DatosCliente datos) {
        if (datos == null) {
            throw new ReglaDeNegocioException("Faltan los datos del cliente");
        }
        poner(datos);
    }

    private void poner(DatosCliente datos) {
        String nombreLimpio = limpiarNombre(datos.nombre());
        if (nombreLimpio.isEmpty()) {
            throw new ReglaDeNegocioException("Escribe el nombre del cliente");
        }
        exigirLargo(nombreLimpio, LARGO_NOMBRE, "El nombre");
        String documentoLimpio = limpio(datos.documento());
        String documentoNuevo = normalizarDocumento(documentoLimpio);
        if (documentoLimpio != null && (documentoNuevo == null || !DOCUMENTO_VALIDO.matcher(documentoNuevo).matches())) {
            throw new ReglaDeNegocioException("La cédula o NIT no parece válida: de 5 a 15 números o letras");
        }
        exigirLargo(documentoLimpio, LARGO_DOCUMENTO, "La cédula o NIT");
        String celularLimpio = limpio(datos.celular());
        String celularNuevo = normalizarCelular(celularLimpio);
        if (celularLimpio != null && (celularNuevo == null || !CELULAR_VALIDO.matcher(celularNuevo).matches())) {
            throw new ReglaDeNegocioException("El celular tiene que tener de 7 a 15 dígitos");
        }
        exigirLargo(celularLimpio, LARGO_CELULAR, "El celular");
        String direccionLimpia = limpio(datos.direccion());
        exigirLargo(direccionLimpia, LARGO_DIRECCION, "La dirección");
        String notaLimpia = limpio(datos.nota());
        exigirLargo(notaLimpia, LARGO_NOTA, "La nota");

        this.nombre = nombreLimpio;
        this.nombreNormalizado = TextoDeBusqueda.normalizar(nombreLimpio);
        this.documento = documentoNuevo == null ? null : documentoLimpio;
        this.documentoNormalizado = documentoNuevo;
        this.celular = celularNuevo == null ? null : celularLimpio;
        this.celularNormalizado = celularNuevo;
        this.direccion = direccionLimpia;
        this.nota = notaLimpia;
    }

    /** Para la auditoría de una corrección: los datos antes y después. */
    public Map<String, Object> fotografia() {
        Map<String, Object> foto = new LinkedHashMap<>();
        foto.put("nombre", nombre);
        foto.put("documento", documento);
        foto.put("celular", celular);
        foto.put("direccion", direccion);
        foto.put("nota", nota);
        foto.put("fiadoCerrado", fiadoCerrado);
        if (fiadoCerrado) {
            foto.put("motivoFiadoCerrado", motivoFiadoCerrado);
        }
        return foto;
    }

    // ── Cómo se compara ──────────────────────────────────────────────────────

    /** Solo letras y números, en mayúsculas: "1.234.567-8" → "12345678". {@code null} si no queda nada. */
    public static String normalizarDocumento(String documento) {
        if (documento == null) {
            return null;
        }
        String limpio = documento.replaceAll("[^\\p{L}\\p{N}]", "").toUpperCase(Locale.ROOT);
        return limpio.isEmpty() ? null : limpio;
    }

    /** Solo los dígitos: "300 123 4567" → "3001234567". {@code null} si no queda nada. */
    public static String normalizarCelular(String celular) {
        if (celular == null) {
            return null;
        }
        String digitos = celular.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    /** Sin espacios de sobra: "Juan  Pérez " → "Juan Pérez". */
    static String limpiarNombre(String nombre) {
        return nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ");
    }

    private static String limpio(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim().replaceAll("\\s+", " ");
        return limpio.isEmpty() ? null : limpio;
    }

    private static void exigirLargo(String texto, int maximo, String campo) {
        if (texto != null && texto.length() > maximo) {
            throw new ReglaDeNegocioException(campo + " es muy largo: máximo " + maximo + " caracteres");
        }
    }
}
