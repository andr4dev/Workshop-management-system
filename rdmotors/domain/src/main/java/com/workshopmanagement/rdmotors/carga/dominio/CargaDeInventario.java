package com.workshopmanagement.rdmotors.carga.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.workshopmanagement.rdmotors.carga.dominio.Problema.Tipo;
import com.workshopmanagement.rdmotors.carga.dominio.Revision.RenglonRevisado;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Una factura subida, mientras se revisa (spec 0012). Es el borrador de una compra: nada de lo que hay aquí ha tocado
 * el inventario, y nada lo toca hasta confirmar.
 *
 * <h2>Vive en el servidor</h2>
 *
 * Seiscientos renglones no se revisan de una sentada, y el socio y el dueño lo hacen desde equipos distintos
 * (decisión 4). Cada gesto —un precio, una marca, quitar un renglón— se guarda en el momento.
 *
 * <h2>Dos comprobaciones que no se pueden saltar</h2>
 * <ul>
 *   <li><b>Cada renglón cuadra su cuenta</b> (cantidad × precio − descuento = valor total), si trae con qué.</li>
 *   <li><b>La suma de lo leído es el sub-total de la factura.</b> Se suman <i>todos</i> los renglones leídos, también
 *       los quitados: quitar uno es decidir que no entra, no arreglar una lectura. Si falta un renglón, sobra uno o
 *       se leyó mal un número, aquí se ve.</li>
 * </ul>
 */
@Entity
@Table(name = "carga_inventario")
@Getter
public class CargaDeInventario {

    /** Más que esto no es una factura: es un archivo equivocado. La MAG477, que ya es grande, tiene 592. */
    public static final int MAXIMO_RENGLONES = 3_000;
    static final int LARGO_NUMERO_FACTURA = 60;

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoCarga estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen", nullable = false, length = 20)
    private OrigenCarga origen;

    @Column(name = "nombre_archivo", length = 200)
    private String nombreArchivo;

    // ── La compra que va a ser ───────────────────────────────────────────────

    @Column(name = "proveedor_id")
    private UUID proveedorId;

    /** El NIT impreso en la factura, si se leyó: con él se encontró al proveedor. */
    @Column(name = "nit_proveedor", length = 40)
    private String nitProveedor;

    @Column(name = "numero_factura", length = LARGO_NUMERO_FACTURA)
    private String numeroFactura;

    @Column(name = "fecha_factura")
    private LocalDate fechaFactura;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pago", length = 15)
    private FormaPago formaPago;

    @Column(name = "cuenta_id")
    private UUID cuentaId;

    // ── Lo impreso, para comparar ────────────────────────────────────────────

    /** El sub-total de la factura, antes del IVA. Del PDF si se leyó; escrito a mano si vino en Excel (RF-004). */
    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "subtotal_factura"))
    private Dinero subtotalFactura;

    /** Se leyó de la factura: entonces no se escribe a mano, o la comprobación de la suma no comprobaría nada. */
    @Column(name = "subtotal_leido", nullable = false)
    private boolean subtotalLeido;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "iva_impreso"))
    private Dinero ivaImpreso;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "total_impreso"))
    private Dinero totalImpreso;

    // ── La regla del precio ──────────────────────────────────────────────────

    @Column(name = "iva_pct", nullable = false, precision = 8, scale = 4)
    private BigDecimal ivaPct;

    @Column(name = "ganancia_pct", nullable = false, precision = 8, scale = 4)
    private BigDecimal gananciaPct;

    @Column(name = "redondeo", nullable = false)
    private int redondeo;

    // ── Quién y cuándo ───────────────────────────────────────────────────────

    @Column(name = "creada_por_id", nullable = false)
    private UUID creadaPorId;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    /** El último gesto guardado. Además de informar, hace que cada gesto suba la versión de la carga. */
    @Column(name = "modificada_en", nullable = false)
    private Instant modificadaEn;

    @Column(name = "cerrada_por_id")
    private UUID cerradaPorId;

    /** Cuándo se confirmó o se descartó. */
    @Column(name = "cerrada_en")
    private Instant cerradaEn;

    /** La compra que resultó de confirmarla. */
    @Column(name = "compra_id")
    private UUID compraId;

    @OneToMany(mappedBy = "carga", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("posicion")
    private List<RenglonDeCarga> renglones = new ArrayList<>();

    protected CargaDeInventario() {
        // JPA
    }

    /**
     * La carga recién leída: cada renglón con su precio sugerido, y con la marca y la categoría que traía el archivo
     * o las que se proponen desde la descripción. Arranca con IVA 19%, ganancia 45% y redondeo a $100.
     *
     * @param proveedorId el que tiene el NIT impreso, si se encontró; si no, se elige en la pre-carga
     */
    public static CargaDeInventario desde(FacturaLeida factura, String nombreArchivo, UUID proveedorId,
                                          ProponedorDeMarca marcas, CategoriasConocidas categorias,
                                          UUID creadaPorId, Instant ahora) {
        if (factura.renglones().isEmpty()) {
            throw new ReglaDeNegocioException("El archivo no trae ningún renglón para cargar");
        }
        if (factura.renglones().size() > MAXIMO_RENGLONES) {
            throw new ReglaDeNegocioException("El archivo trae " + factura.renglones().size() + " renglones, y una "
                    + "carga admite hasta " + MAXIMO_RENGLONES + ". Pártelo en dos.");
        }
        CargaDeInventario carga = new CargaDeInventario();
        carga.id = UUID.randomUUID();
        carga.estado = EstadoCarga.BORRADOR;
        carga.origen = factura.origen();
        carga.nombreArchivo = nombreArchivo == null ? null
                : nombreArchivo.length() > 200 ? nombreArchivo.substring(0, 200) : nombreArchivo;
        carga.proveedorId = proveedorId;
        carga.nitProveedor = factura.nitProveedor();
        carga.numeroFactura = limpiarNumero(factura.numeroFactura());
        carga.fechaFactura = factura.fecha();
        carga.subtotalFactura = factura.subtotalImpreso();
        carga.subtotalLeido = factura.subtotalImpreso() != null;
        carga.ivaImpreso = factura.ivaImpreso();
        carga.totalImpreso = factura.totalImpreso();
        ReglaDePrecio regla = ReglaDePrecio.porDefecto();
        carga.fijarRegla(regla);
        carga.creadaPorId = creadaPorId;
        carga.creadaEn = ahora;
        carga.modificadaEn = ahora;
        for (RenglonLeido leido : factura.renglones()) {
            RenglonDeCarga renglon = RenglonDeCarga.leido(leido, carga.renglones.size(), marcas, categorias);
            renglon.asignarA(carga);
            renglon.recalcular(regla);
            carga.renglones.add(renglon);
        }
        return carga;
    }

    public ReglaDePrecio regla() {
        return new ReglaDePrecio(ivaPct, gananciaPct, redondeo);
    }

    public List<RenglonDeCarga> getRenglones() {
        return Collections.unmodifiableList(renglones);
    }

    /** Los códigos de todos los renglones, como los guarda la ficha: para buscar de una vez cuáles ya existen. */
    public Set<String> codigos() {
        Set<String> codigos = new LinkedHashSet<>();
        for (RenglonDeCarga r : renglones) {
            if (r.getCodigo() != null) {
                codigos.add(RenglonDeCarga.claveDe(r.getCodigo()));
            }
        }
        return codigos;
    }

    // ── Editar: todo exige que siga en borrador ──────────────────────────────

    /**
     * Los datos de la compra que va a ser. Que el proveedor y la cuenta existan lo mira quien llama; que la forma de
     * pago y la cuenta cuadren, aquí.
     */
    public void cambiarDatos(UUID nuevoProveedorId, String nuevoNumero, LocalDate nuevaFecha, FormaPago nuevaForma,
                             UUID nuevaCuentaId) {
        exigirBorrador();
        if (nuevaForma == FormaPago.EFECTIVO && nuevaCuentaId != null) {
            throw new ReglaDeNegocioException("Una compra en efectivo no lleva cuenta");
        }
        String numero = limpiarNumero(nuevoNumero);
        if (numero != null && numero.length() > LARGO_NUMERO_FACTURA) {
            throw new ReglaDeNegocioException("El número de factura admite hasta " + LARGO_NUMERO_FACTURA
                    + " caracteres");
        }
        this.proveedorId = nuevoProveedorId;
        this.numeroFactura = numero;
        this.fechaFactura = nuevaFecha;
        this.formaPago = nuevaForma;
        this.cuentaId = nuevaCuentaId;
    }

    /** El sub-total de la factura en papel, cuando vino en Excel y no hay de dónde leerlo (RF-004). */
    public void fijarSubtotal(Dinero subtotal) {
        exigirBorrador();
        if (subtotalLeido) {
            throw new ReglaDeNegocioException("El sub-total se leyó de la factura: no se escribe a mano, o comparar "
                    + "contra él no comprobaría nada");
        }
        if (subtotal != null && (subtotal.esNegativo() || subtotal.esCero())) {
            throw new ReglaDeNegocioException("El sub-total tiene que ser mayor que cero");
        }
        this.subtotalFactura = subtotal;
    }

    /** Cambia el IVA, la ganancia o el redondeo, y con ellos los precios que no se tocaron a mano (RF-007). */
    public void cambiarRegla(ReglaDePrecio nueva) {
        exigirBorrador();
        fijarRegla(nueva);
        for (RenglonDeCarga r : renglones) {
            r.recalcular(nueva);
        }
    }

    public void ajustarPrecio(int posicion, Dinero precio) {
        exigirBorrador();
        renglon(posicion).ajustarPrecio(precio);
    }

    public void volverAlSugerido(int posicion) {
        exigirBorrador();
        renglon(posicion).volverAlSugerido(regla());
    }

    public void cambiarMarca(Collection<Integer> posiciones, String marca) {
        exigirBorrador();
        for (RenglonDeCarga r : renglones(posiciones)) {
            r.cambiarMarca(marca);
        }
    }

    /** "Ponles NACIONAL a todos los que no tienen": en la MAG477 son 176. @return a cuántos se les puso */
    public int cambiarMarcaDeLosQueNoTienen(String marca) {
        exigirBorrador();
        int cuantos = 0;
        for (RenglonDeCarga r : renglones) {
            if (!r.isQuitado() && r.getMarca() == null) {
                r.cambiarMarca(marca);
                cuantos++;
            }
        }
        return cuantos;
    }

    /** Que la categoría exista y esté activa lo mira quien llama. */
    public void cambiarCategoria(Collection<Integer> posiciones, UUID categoriaId) {
        exigirBorrador();
        exigirCategoria(categoriaId);
        for (RenglonDeCarga r : renglones(posiciones)) {
            r.cambiarCategoria(categoriaId);
        }
    }

    /** @return a cuántos se les puso */
    public int cambiarCategoriaDeLosQueNoTienen(UUID categoriaId) {
        exigirBorrador();
        exigirCategoria(categoriaId);
        int cuantos = 0;
        for (RenglonDeCarga r : renglones) {
            if (!r.isQuitado() && r.getCategoriaId() == null) {
                r.cambiarCategoria(categoriaId);
                cuantos++;
            }
        }
        return cuantos;
    }

    public void corregirLectura(int posicion, String codigo, String descripcion, Integer cantidad, Dinero valorTotal,
                                Dinero precioUnitario, BigDecimal descuentoPct) {
        exigirBorrador();
        renglon(posicion).corregirLectura(codigo, descripcion, cantidad, valorTotal, precioUnitario, descuentoPct,
                regla());
    }

    public void quitar(int posicion) {
        exigirBorrador();
        renglon(posicion).quitar();
    }

    public void restaurar(int posicion) {
        exigirBorrador();
        renglon(posicion).restaurar();
    }

    /** En una reposición: si el repuesto toma el precio de esta carga o conserva el que tiene (RF-009). */
    public void aplicarPrecioNuevo(int posicion, boolean aplicar) {
        exigirBorrador();
        renglon(posicion).aplicarPrecioNuevo(aplicar);
    }

    /** Deja constancia del gesto. Lo llama quien edita, una vez por gesto. */
    public void registrarCambio(Instant cuando) {
        this.modificadaEn = cuando;
    }

    /**
     * Queda confirmada con la compra que dejó. La compra la registra quien confirma, en la misma transacción: o
     * entran las dos cosas, o ninguna.
     */
    public void confirmar(UUID compra, UUID quien, Instant cuando) {
        exigirBorrador();
        if (compra == null) {
            throw new ReglaDeNegocioException("Una carga se confirma con la compra que dejó");
        }
        this.estado = EstadoCarga.CONFIRMADA;
        this.compraId = compra;
        this.cerradaPorId = quien;
        this.cerradaEn = cuando;
        this.modificadaEn = cuando;
    }

    public void descartar(UUID quien, Instant cuando) {
        exigirBorrador();
        this.estado = EstadoCarga.DESCARTADA;
        this.cerradaPorId = quien;
        this.cerradaEn = cuando;
        this.modificadaEn = cuando;
    }

    public void exigirBorrador() {
        if (estado != EstadoCarga.BORRADOR) {
            throw new ReglaDeNegocioException(estado == EstadoCarga.CONFIRMADA
                    ? "Esta carga ya se confirmó: lo que haya que cambiar se corrige en la compra"
                    : "Esta carga se descartó: ya no se puede editar");
        }
    }

    // ── Revisar ──────────────────────────────────────────────────────────────

    /**
     * La carga contra el inventario de este momento.
     *
     * @param existentes los repuestos que ya tienen alguno de los {@link #codigos()}, por código
     * @param categorias las activas hoy
     */
    public Revision revisar(Map<String, Variante> existentes, CategoriasConocidas categorias) {
        ReglaDePrecio regla = regla();
        Map<String, List<RenglonDeCarga>> porCodigo = new LinkedHashMap<>();
        for (RenglonDeCarga r : renglones) {
            if (!r.isQuitado() && r.getCodigo() != null) {
                porCodigo.computeIfAbsent(RenglonDeCarga.claveDe(r.getCodigo()), k -> new ArrayList<>()).add(r);
            }
        }

        List<RenglonRevisado> revisados = new ArrayList<>();
        Dinero sumaLeida = Dinero.CERO;
        Dinero subtotalIncluido = Dinero.CERO;
        int unidades = 0;
        int incluidos = 0;
        int conProblema = 0;
        int propuestas = 0;
        int reposiciones = 0;
        int ajustados = 0;
        int quitados = 0;
        for (RenglonDeCarga r : renglones) {
            Variante existente = r.getCodigo() == null ? null : existentes.get(RenglonDeCarga.claveDe(r.getCodigo()));
            BigDecimal costo = r.costoPorUnidad(regla);
            Dinero sugerido = costo == null ? null : regla.sugerido(costo);
            if (r.getValorTotal() != null) {
                sumaLeida = sumaLeida.mas(r.getValorTotal());
            }
            if (r.isQuitado()) {
                quitados++;
                revisados.add(new RenglonRevisado(r, costo, sugerido, existente, List.of(), List.of()));
                continue;
            }
            List<Problema> problemas = problemasDe(r, existente, porCodigo, categorias);
            revisados.add(new RenglonRevisado(r, costo, sugerido, existente, problemas, avisosDe(r, existente, costo)));
            incluidos++;
            if (r.getValorTotal() != null) {
                subtotalIncluido = subtotalIncluido.mas(r.getValorTotal());
            }
            if (r.getCantidad() != null && r.getCantidad() > 0) {
                unidades += r.getCantidad();
            }
            if (!problemas.isEmpty()) conProblema++;
            if (existente != null) reposiciones++;
            else if (r.isMarcaPropuesta() || r.isCategoriaPropuesta()) propuestas++;
            if (r.isAjustadoAMano()) ajustados++;
        }

        Dinero diferencia = subtotalFactura == null ? null : subtotalFactura.menos(sumaLeida);
        Dinero iva = regla.ivaDe(subtotalIncluido);
        var totales = new Revision.Totales(sumaLeida, subtotalFactura, diferencia, subtotalIncluido, iva,
                subtotalIncluido.mas(iva), unidades, incluidos, conProblema, propuestas, reposiciones, ajustados,
                quitados);
        return new Revision(revisados, problemasDeLaCarga(totales), totales);
    }

    private static List<Problema> problemasDe(RenglonDeCarga r, Variante existente,
                                              Map<String, List<RenglonDeCarga>> porCodigo,
                                              CategoriasConocidas categorias) {
        List<Problema> problemas = new ArrayList<>();
        if (r.getCodigo() == null) {
            problemas.add(new Problema(Tipo.SIN_CODIGO, "Falta el código"));
        } else if (r.getCodigo().length() > RenglonDeCarga.LARGO_CODIGO) {
            problemas.add(new Problema(Tipo.CODIGO_LARGO,
                    "El código pasa de " + RenglonDeCarga.LARGO_CODIGO + " caracteres"));
        }
        if (r.getCantidad() == null) {
            problemas.add(new Problema(Tipo.CANTIDAD_INVALIDA, "La cantidad no se entiende"));
        } else if (r.getCantidad() <= 0) {
            problemas.add(new Problema(Tipo.CANTIDAD_INVALIDA, "La cantidad tiene que ser mayor que cero"));
        }
        if (r.getValorTotal() == null) {
            problemas.add(new Problema(Tipo.TOTAL_INVALIDO, "El valor total no se entiende"));
        } else if (r.getValorTotal().esNegativo() || r.getValorTotal().esCero()) {
            problemas.add(new Problema(Tipo.TOTAL_INVALIDO, "El valor total tiene que ser mayor que cero"));
        }
        if (!r.cuadra()) {
            problemas.add(new Problema(Tipo.NO_CUADRA, r.porQueNoCuadra()));
        }
        if (r.getCodigo() != null) {
            List<RenglonDeCarga> mismos = porCodigo.get(RenglonDeCarga.claveDe(r.getCodigo()));
            if (mismos != null && mismos.size() > 1) {
                List<String> otros = mismos.stream().filter(o -> o != r)
                        .map(o -> o.getUbicacion() == null ? "renglón " + (o.getPosicion() + 1) : o.getUbicacion())
                        .toList();
                problemas.add(new Problema(Tipo.CODIGO_REPETIDO, "Este código está dos veces (también en "
                        + String.join(", ", otros) + "). No se suman solos: quita el que sobra o corrígelo"));
            }
        }

        if (existente != null) {
            // Una reposición no pide marca ni categoría: las tiene el repuesto que ya existe.
            if (r.isAplicarPrecioNuevo() && r.getPrecioFinal() == null) {
                problemas.add(new Problema(Tipo.SIN_PRECIO, "Falta el precio de venta nuevo"));
            }
            return problemas;
        }

        if (r.getNombre() == null) {
            problemas.add(new Problema(Tipo.SIN_DESCRIPCION, "Falta la descripción: es el nombre del repuesto nuevo"));
        } else if (r.getNombre().length() > RenglonDeCarga.LARGO_NOMBRE) {
            problemas.add(new Problema(Tipo.NOMBRE_LARGO,
                    "La descripción pasa de " + RenglonDeCarga.LARGO_NOMBRE + " caracteres"));
        }
        if (r.getMarca() == null) {
            problemas.add(new Problema(Tipo.SIN_MARCA, "Falta la marca"));
        } else if (r.getMarca().length() > RenglonDeCarga.LARGO_MARCA) {
            problemas.add(new Problema(Tipo.MARCA_LARGA,
                    "La marca pasa de " + RenglonDeCarga.LARGO_MARCA + " caracteres"));
        }
        if (r.getCategoriaId() == null) {
            problemas.add(new Problema(Tipo.SIN_CATEGORIA, "Falta la categoría"));
        } else if (!categorias.activa(r.getCategoriaId())) {
            problemas.add(new Problema(Tipo.SIN_CATEGORIA, "Esa categoría ya no existe o se desactivó: elige otra"));
        }
        if (r.getPrecioFinal() == null) {
            problemas.add(new Problema(Tipo.SIN_PRECIO, "Falta el precio de venta"));
        }
        return problemas;
    }

    /** Vender por debajo de lo que costó se avisa y no se impide: a veces es a propósito (§6). */
    private static List<Problema> avisosDe(RenglonDeCarga r, Variante existente, BigDecimal costo) {
        if (costo == null) {
            return List.of();
        }
        boolean conservaSuPrecio = existente != null && !r.isAplicarPrecioNuevo();
        Dinero precio = conservaSuPrecio ? existente.getPrecio() : r.getPrecioFinal();
        if (precio == null || precio.valor().compareTo(costo) >= 0) {
            return List.of();
        }
        String perdida = Dinero.de(costo.subtract(precio.valor())).enPesos();
        return List.of(new Problema(Tipo.PRECIO_BAJO_COSTO, conservaSuPrecio
                ? "Con este costo, el precio que ya tiene (" + precio.enPesos() + ") deja pérdida: " + perdida
                        + " por unidad"
                : "Vendes a pérdida: " + perdida + " por unidad"));
    }

    private List<Problema> problemasDeLaCarga(Revision.Totales totales) {
        List<Problema> problemas = new ArrayList<>();
        if (proveedorId == null) {
            problemas.add(new Problema(Tipo.SIN_PROVEEDOR, "Falta el proveedor"));
        }
        if (fechaFactura == null) {
            problemas.add(new Problema(Tipo.SIN_FECHA, "Falta la fecha de la factura"));
        }
        if (formaPago == null) {
            problemas.add(new Problema(Tipo.SIN_FORMA_PAGO, "Falta la forma de pago: efectivo o transferencia"));
        } else if (formaPago == FormaPago.TRANSFERENCIA && cuentaId == null) {
            problemas.add(new Problema(Tipo.SIN_CUENTA, "Una transferencia necesita la cuenta de la que salió"));
        }
        if (subtotalFactura == null) {
            problemas.add(new Problema(Tipo.SIN_SUBTOTAL,
                    "Falta el sub-total de la factura: el de abajo, antes del IVA"));
        } else if (!totales.diferencia().esCero()) {
            Dinero diferencia = totales.diferencia();
            boolean faltan = !diferencia.esNegativo();
            Dinero cuanto = faltan ? diferencia : Dinero.CERO.menos(diferencia);
            problemas.add(new Problema(Tipo.SUMA_NO_CUADRA, (faltan ? "Faltan " : "Sobran ") + cuanto.enPesos()
                    + ": el archivo suma " + totales.sumaLeida().enPesos() + " y la factura dice "
                    + subtotalFactura.enPesos()));
        }
        if (totales.incluidos() == 0) {
            problemas.add(new Problema(Tipo.SIN_RENGLONES, "No queda ningún renglón para cargar"));
        }
        if (totales.conProblema() > 0) {
            problemas.add(new Problema(Tipo.RENGLONES_CON_PROBLEMA, totales.conProblema() == 1
                    ? "1 renglón tiene problemas" : totales.conProblema() + " renglones tienen problemas"));
        }
        return problemas;
    }

    // ── Internas ─────────────────────────────────────────────────────────────

    private void fijarRegla(ReglaDePrecio regla) {
        this.ivaPct = regla.ivaPct();
        this.gananciaPct = regla.gananciaPct();
        this.redondeo = regla.redondeo();
    }

    private RenglonDeCarga renglon(int posicion) {
        if (posicion < 0 || posicion >= renglones.size() || renglones.get(posicion).getPosicion() != posicion) {
            return renglones.stream().filter(r -> r.getPosicion() == posicion).findFirst()
                    .orElseThrow(() -> new ReglaDeNegocioException("Ese renglón no está en la carga"));
        }
        return renglones.get(posicion);
    }

    private List<RenglonDeCarga> renglones(Collection<Integer> posiciones) {
        if (posiciones == null || posiciones.isEmpty()) {
            throw new ReglaDeNegocioException("Elige al menos un renglón");
        }
        return new LinkedHashSet<>(posiciones).stream().map(this::renglon).toList();
    }

    private static void exigirCategoria(UUID categoriaId) {
        if (categoriaId == null) {
            throw new ReglaDeNegocioException("Elige la categoría");
        }
    }

    private static String limpiarNumero(String numero) {
        if (numero == null || numero.isBlank()) {
            return null;
        }
        return numero.strip();
    }
}
