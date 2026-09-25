package com.workshopmanagement.rdmotors.carga.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Un renglón de la factura mientras se revisa (spec 0012, RF-005 a RF-012).
 *
 * <p><b>Guarda lo leído aunque esté mal</b>: sin código, con una cantidad que no se entiende, con una cuenta que no
 * cuadra. Es la única forma de mostrarlo marcado y dejar que se arregle; lo que no puede es entrar a la compra. Por
 * eso casi todo aquí admite {@code null}, y la carga dice qué falta.
 *
 * <p>Si el código ya existe en el inventario no se guarda aquí: se mira cada vez que se revisa la carga
 * ({@link CargaDeInventario#revisar}). Así, si alguien lo crea a mano mientras la carga espera, el renglón pasa solo a
 * ser reposición, en vez de chocar al confirmar.
 */
@Entity
@Table(name = "renglon_carga")
@Getter
public class RenglonDeCarga {

    /** Lo que admite la ficha del repuesto: más largo se guarda en el borrador, pero no se puede confirmar. */
    static final int LARGO_CODIGO = 60;
    static final int LARGO_MARCA = 80;
    static final int LARGO_NOMBRE = 200;

    /** Lo que admite el borrador: lo que pase de aquí es basura del archivo y se corta. */
    private static final int TOPE_CODIGO = 200;
    private static final int TOPE_DESCRIPCION = 500;
    private static final int TOPE_UNIDAD = 20;
    private static final int TOPE_MARCA = 200;
    private static final BigDecimal TOPE_DESCUENTO = new BigDecimal("999.9999");
    /** Cien mil millones: ningún renglón de una factura de repuestos llega ahí. Más es un número mal leído. */
    private static final BigDecimal TOPE_PLATA = new BigDecimal("100000000000");

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carga_id", nullable = false)
    private CargaDeInventario carga;

    /** El orden de la factura, desde 0. Con él se nombra el renglón al editarlo. */
    @Column(name = "posicion", nullable = false)
    private int posicion;

    /** Dónde está en el archivo: "pág. 26", "fila 14". Para ir al papel. */
    @Column(name = "ubicacion", length = 40)
    private String ubicacion;

    @Column(name = "codigo", length = TOPE_CODIGO)
    private String codigo;

    @Column(name = "descripcion", length = TOPE_DESCRIPCION)
    private String descripcion;

    /** El nombre del repuesto nuevo: la descripción sin la marca del final. */
    @Column(name = "nombre", length = TOPE_DESCRIPCION)
    private String nombre;

    @Column(name = "cantidad")
    private Integer cantidad;

    @Column(name = "unidad", length = TOPE_UNIDAD)
    private String unidad;

    /** Precio de lista, antes del descuento. Solo sirve para comprobar el renglón: el costo sale del total. */
    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "precio_unitario"))
    private Dinero precioUnitario;

    @Column(name = "descuento_pct", precision = 7, scale = 4)
    private BigDecimal descuentoPct;

    /** El renglón entero, todas las unidades, con el descuento y sin IVA. De aquí sale el costo. */
    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "valor_total"))
    private Dinero valorTotal;

    @Column(name = "marca", length = TOPE_MARCA)
    private String marca;

    /** La puso el sistema leyendo la descripción, y nadie la ha mirado todavía. */
    @Column(name = "marca_propuesta", nullable = false)
    private boolean marcaPropuesta;

    @Column(name = "categoria_id")
    private UUID categoriaId;

    @Column(name = "categoria_propuesta", nullable = false)
    private boolean categoriaPropuesta;

    /** El precio de venta que va a quedar: el sugerido, o el que se escribió a mano. */
    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "precio_final"))
    private Dinero precioFinal;

    /** Cambiar los porcentajes no lo toca (RF-007). */
    @Column(name = "ajustado_a_mano", nullable = false)
    private boolean ajustadoAMano;

    /** Fuera de la carga, pero no del archivo (RF-012): su valor sigue contando para comprobar la lectura. */
    @Column(name = "quitado", nullable = false)
    private boolean quitado;

    /** Solo en una reposición: si el repuesto toma el precio de esta carga o conserva el suyo (RF-009). */
    @Column(name = "aplicar_precio_nuevo", nullable = false)
    private boolean aplicarPrecioNuevo;

    protected RenglonDeCarga() {
        // JPA
    }

    /**
     * Un renglón tal como se leyó, con la marca y la categoría que traía el archivo o, si no traía, las que se
     * proponen desde la descripción.
     */
    static RenglonDeCarga leido(RenglonLeido leido, int posicion, ProponedorDeMarca marcas,
                                CategoriasConocidas categorias) {
        RenglonDeCarga r = new RenglonDeCarga();
        r.id = UUID.randomUUID();
        r.posicion = posicion;
        r.ubicacion = cortar(leido.ubicacion(), 40);
        r.codigo = limpiarCodigo(leido.codigo());
        r.descripcion = cortar(limpiarTexto(leido.descripcion()), TOPE_DESCRIPCION);
        r.nombre = r.descripcion;
        r.cantidad = leido.cantidad();
        r.unidad = cortar(limpiarTexto(leido.unidad()), TOPE_UNIDAD);
        r.precioUnitario = plata(leido.precioUnitario());
        r.descuentoPct = descuento(leido.descuentoPct());
        r.valorTotal = plata(leido.valorTotal());

        String marcaLeida = limpiarMarca(leido.marca());
        if (marcaLeida != null) {
            r.marca = marcaLeida;
            r.recalcularNombre();
        } else {
            marcas.proponer(r.descripcion).ifPresent(p -> {
                r.marca = p.marca();
                r.marcaPropuesta = true;
                r.nombre = cortar(p.nombreSinMarca(), TOPE_DESCRIPCION);
            });
        }

        if (leido.categoria() != null && !leido.categoria().isBlank()) {
            // Si el archivo dice una categoría que no existe, queda sin categoría y se pide: no se inventa una.
            r.categoriaId = categorias.porNombre(leido.categoria()).orElse(null);
        } else {
            ProponedorDeCategoria.proponer(r.descripcion).flatMap(categorias::porNombre).ifPresent(id -> {
                r.categoriaId = id;
                r.categoriaPropuesta = true;
            });
        }
        return r;
    }

    void asignarA(CargaDeInventario carga) {
        this.carga = carga;
    }

    // ── Plata ────────────────────────────────────────────────────────────────

    /** Lo que costó cada unidad con IVA, o {@code null} si con lo leído no hay cómo saberlo. */
    public BigDecimal costoPorUnidad(ReglaDePrecio regla) {
        return regla.costoConIvaPorUnidad(valorTotal, cantidad);
    }

    public Dinero sugerido(ReglaDePrecio regla) {
        BigDecimal costo = costoPorUnidad(regla);
        return costo == null ? null : regla.sugerido(costo);
    }

    /** Lo que no se ajustó a mano vuelve a ser el sugerido: tras leerlo, cambiar la regla o corregir la lectura. */
    void recalcular(ReglaDePrecio regla) {
        if (!ajustadoAMano) {
            precioFinal = sugerido(regla);
        }
    }

    void ajustarPrecio(Dinero precio) {
        if (precio == null || precio.esNegativo() || precio.esCero()) {
            throw new ReglaDeNegocioException("El precio de venta tiene que ser mayor que cero");
        }
        precioFinal = precio;
        ajustadoAMano = true;
    }

    void volverAlSugerido(ReglaDePrecio regla) {
        ajustadoAMano = false;
        recalcular(regla);
    }

    /**
     * {@code cantidad × precio − descuento = valor total}, con un peso de tolerancia por redondeo (RF-000). Si falta
     * el precio o la cantidad no hay qué comprobar: una plantilla de Excel no tiene por qué traer el precio de lista.
     *
     * <p>Un descuento de 0,18 se prueba también como 18%: es lo que guarda Excel en una celda con formato de
     * porcentaje. Solo sirve para la comprobación; el costo sale del total, que no depende de él.
     */
    public boolean cuadra() {
        if (precioUnitario == null || cantidad == null || valorTotal == null) {
            return true;
        }
        BigDecimal pct = descuentoPct == null ? BigDecimal.ZERO : descuentoPct;
        if (cuadraCon(pct)) {
            return true;
        }
        return pct.compareTo(BigDecimal.ONE) <= 0 && cuadraCon(pct.movePointRight(2));
    }

    private boolean cuadraCon(BigDecimal pct) {
        return cuentaCon(pct).subtract(valorTotal.valor()).abs().compareTo(BigDecimal.ONE) <= 0;
    }

    private BigDecimal cuentaCon(BigDecimal pct) {
        return precioUnitario.valor().multiply(BigDecimal.valueOf(cantidad))
                .multiply(BigDecimal.valueOf(100).subtract(pct))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }

    /** "8 × $46.993 − 18% da $308.274, pero la factura dice $380.274". */
    String porQueNoCuadra() {
        BigDecimal pct = descuentoPct == null ? BigDecimal.ZERO : descuentoPct;
        String descuento = pct.signum() == 0 ? "" : " − " + pct.stripTrailingZeros().toPlainString()
                .replace('.', ',') + "%";
        return cantidad + " × " + precioUnitario.enPesos() + descuento + " da " + Dinero.de(cuentaCon(pct)).enPesos()
                + ", pero la factura dice " + valorTotal.enPesos();
    }

    // ── Marca, categoría, lectura ────────────────────────────────────────────

    void cambiarMarca(String nueva) {
        String limpia = limpiarMarca(nueva);
        if (limpia == null) {
            throw new ReglaDeNegocioException("Escribe la marca");
        }
        // Aceptar la propuesta es escribirla igual: deja de ser propuesta y el nombre no cambia.
        boolean cambio = !limpia.equals(marca);
        marca = limpia;
        marcaPropuesta = false;
        if (cambio) {
            recalcularNombre();
        }
    }

    void cambiarCategoria(UUID nueva) {
        categoriaId = nueva;
        categoriaPropuesta = false;
    }

    /** Lo que se leyó mal, corregido mirando el papel. El precio sugerido se vuelve a sacar si no se ajustó a mano. */
    void corregirLectura(String nuevoCodigo, String nuevaDescripcion, Integer nuevaCantidad, Dinero nuevoValorTotal,
                         Dinero nuevoPrecioUnitario, BigDecimal nuevoDescuento, ReglaDePrecio regla) {
        codigo = limpiarCodigo(nuevoCodigo);
        String descripcionLimpia = cortar(limpiarTexto(nuevaDescripcion), TOPE_DESCRIPCION);
        if (descripcionLimpia == null ? descripcion != null : !descripcionLimpia.equals(descripcion)) {
            descripcion = descripcionLimpia;
            recalcularNombre();
        }
        cantidad = nuevaCantidad;
        valorTotal = plata(nuevoValorTotal);
        precioUnitario = plata(nuevoPrecioUnitario);
        descuentoPct = descuento(nuevoDescuento);
        recalcular(regla);
    }

    void quitar() {
        quitado = true;
    }

    void restaurar() {
        quitado = false;
    }

    void aplicarPrecioNuevo(boolean aplicar) {
        aplicarPrecioNuevo = aplicar;
    }

    /** La descripción sin la marca, si termina en ella: "BUJIA IRIDIUM NGK" con marca NGK es "BUJIA IRIDIUM". */
    private void recalcularNombre() {
        if (descripcion == null) {
            nombre = null;
            return;
        }
        String sinMarca = descripcion;
        if (marca != null) {
            String fin = " " + marca.toUpperCase(Locale.ROOT);
            if (descripcion.toUpperCase(Locale.ROOT).endsWith(fin)) {
                sinMarca = descripcion.substring(0, descripcion.length() - fin.length()).strip();
            }
        }
        nombre = sinMarca.isEmpty() ? descripcion : sinMarca;
    }

    /** La clave para comparar códigos: como los guarda la ficha del repuesto, en mayúsculas y sin espacios al borde. */
    static String claveDe(String codigo) {
        return codigo == null ? null : codigo.strip().toUpperCase(Locale.ROOT);
    }

    // ── Limpieza de lo leído ─────────────────────────────────────────────────

    private static String limpiarCodigo(String codigo) {
        String limpio = limpiarTexto(codigo);
        return limpio == null ? null : cortar(limpio.toUpperCase(Locale.ROOT), TOPE_CODIGO);
    }

    private static String limpiarMarca(String marca) {
        String limpia = limpiarTexto(marca);
        return limpia == null ? null : cortar(ProponedorDeMarca.normalizar(limpia), TOPE_MARCA);
    }

    private static String limpiarTexto(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.strip().replaceAll("\\s+", " ");
        return limpio.isEmpty() ? null : limpio;
    }

    private static String cortar(String texto, int largo) {
        return texto == null || texto.length() <= largo ? texto : texto.substring(0, largo);
    }

    private static Dinero plata(Dinero valor) {
        return valor == null || valor.valor().abs().compareTo(TOPE_PLATA) >= 0 ? null : valor;
    }

    /** Con los decimales que caben; un descuento de 5000% no es un descuento, es un número mal leído. */
    private static BigDecimal descuento(BigDecimal pct) {
        if (pct == null || pct.signum() < 0 || pct.compareTo(TOPE_DESCUENTO) > 0) {
            return null;
        }
        return pct.setScale(4, RoundingMode.HALF_UP);
    }
}
