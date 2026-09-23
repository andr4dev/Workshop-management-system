package com.workshopmanagement.rdmotors.inventario.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Lo que efectivamente se vende: un repuesto de una marca concreta, con su codigo, su precio y
 * su stock.
 *
 * <p><b>Producto vs Variante.</b> "FILTRO ACEITE para Pulsar NS 200" es el producto (el concepto).
 * Que exista en marca IMPORTADO a $2.779, INOKI a $2.977 y FACTORY a $6.362 son tres variantes.
 * Validado contra la lista real de Jotapartes: el mas caro cuesta 229% del mas barato.
 *
 * <p><b>Stock, costo y precio viven AQUI, nunca en el producto.</b> Ponerlos en el producto romperia
 * el kardex: no se sabria de cual de las tres marcas salio la unidad que se vendio.
 *
 * <p><b>Y las reglas viven adentro, no en un servicio que empuja setters.</b> No hay
 * {@code setStock()}. El stock cambia por {@link #descontar} o {@link #reponerPorCompra}, y esos
 * metodos traen la validacion pegada. Asi es imposible que otro caso de uso mueva inventario
 * saltandose la regla — que es exactamente lo que pasa cuando la logica vive afuera.
 */
@Entity
@Table(name = "variante", indexes = {
        @Index(name = "idx_variante_producto", columnList = "producto_id"),
        @Index(name = "idx_variante_codigo_barras", columnList = "codigo_barras")
})
@Getter
public class Variante {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    /** Codigo interno de RD Motors. Unico. Es lo que teclea el cajero. */
    @Column(name = "codigo", nullable = false, unique = true, length = 60)
    private String codigo;

    /** Codigo de barras. Queda para el futuro; el campo va puesto para que sea aditivo. */
    @Column(name = "codigo_barras", length = 60)
    private String codigoBarras;

    /** INOKI, T.K.R.J, IMPORTADO... 55 valores distintos en el catalogo de Jotapartes. */
    @Column(name = "marca_repuesto", nullable = false, length = 80)
    private String marcaRepuesto;

    /** Precio de venta. Final, IVA incluido, sin desglose. */
    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "precio", nullable = false))
    private Dinero precio;

    @Column(name = "stock", nullable = false)
    private int stock;

    @Column(name = "stock_minimo", nullable = false)
    private int stockMinimo;

    /**
     * Costo promedio ponderado vigente, con 4 decimales.
     * {@code null} = nunca se ha comprado, no se conoce el costo.
     *
     * <p>Es null y no cero a proposito: un cero inventado haria que el producto reportara
     * <b>100% de margen</b> en los reportes. Null se reporta como "—".
     */
    @Column(name = "costo_promedio", precision = 14, scale = 4)
    private BigDecimal costoPromedio;

    @Column(name = "activa", nullable = false)
    private boolean activa;

    protected Variante() {
        // JPA
    }

    private Variante(UUID id, Producto producto, String codigo, String marcaRepuesto, Dinero precio,
                     int stockMinimo) {
        this.id = id;
        this.producto = producto;
        this.codigo = codigo;
        this.marcaRepuesto = marcaRepuesto;
        this.precio = precio;
        this.stock = 0;
        this.stockMinimo = stockMinimo;
        this.costoPromedio = null;
        this.activa = true;
    }

    /**
     * Nace sin stock y sin costo: el producto se crea en el momento de la primera compra, y es esa
     * compra la que le pone stock, costo y precio. No se importan catalogos completos de
     * proveedor — RD Motors solo adquiere ciertas referencias.
     */
    public static Variante nueva(Producto producto, String codigo, String marcaRepuesto,
                                 Dinero precio, int stockMinimo) {
        if (codigo == null || codigo.isBlank()) {
            throw new ReglaDeNegocioException("El codigo del repuesto es obligatorio");
        }
        if (precio == null || precio.esNegativo()) {
            throw new ReglaDeNegocioException("El precio no puede ser negativo");
        }
        return new Variante(UUID.randomUUID(), producto, codigo.trim().toUpperCase(),
                marcaRepuesto, precio, stockMinimo);
    }

    /**
     * Saca unidades del inventario. <b>Aqui vive "no se vende sin stock".</b>
     *
     * <p>No toca el costo promedio: vender no cambia lo que costo comprar. Esa es la regla del
     * costeo por promedio ponderado y romperla descuadra todos los margenes historicos.
     */
    public void descontar(int cantidad) {
        exigirCantidadPositiva(cantidad);
        if (cantidad > stock) {
            throw new StockInsuficienteException(codigo, stock, cantidad);
        }
        this.stock -= cantidad;
    }

    /**
     * Entra mercancia comprada y <b>recalcula el costo promedio ponderado</b>.
     *
     * <pre>
     *   promedioNuevo = (stock x promedioActual + cantidad x costoUnitario)
     *                   / (stock + cantidad)
     * </pre>
     *
     * <p>Ejemplo: hay 10 unidades a $1.000 de promedio y entran 10 a $2.000 → el promedio queda en
     * $1.500. Ni el costo viejo ni el nuevo: el ponderado.
     *
     * @param costoUnitario con decimales (ver {@link Dinero#dividirEntre}). Redondear esto a
     *                      entero es exactamente como se pierden pesos en el camino.
     */
    public void reponerPorCompra(int cantidad, BigDecimal costoUnitario) {
        exigirCantidadPositiva(cantidad);
        if (costoUnitario == null || costoUnitario.signum() <= 0) {
            throw new ReglaDeNegocioException(
                    "El costo debe ser mayor a cero. Sin costo real, el producto reportaria "
                            + "100% de utilidad.");
        }

        if (costoPromedio == null) {
            // Nunca se habia comprado. El stock previo (si lo hay) tiene costo desconocido; se
            // adopta el costo entrante para todo. Dejarlo en cero seria peor: reportaria margen
            // del 100%.
            this.costoPromedio = costoUnitario.setScale(Dinero.ESCALA_UNITARIA, RoundingMode.HALF_UP);
        } else {
            BigDecimal valorActual = costoPromedio.multiply(BigDecimal.valueOf(stock));
            BigDecimal valorEntrante = costoUnitario.multiply(BigDecimal.valueOf(cantidad));
            int stockNuevo = stock + cantidad;
            this.costoPromedio = valorActual.add(valorEntrante)
                    .divide(BigDecimal.valueOf(stockNuevo), Dinero.ESCALA_UNITARIA, RoundingMode.HALF_UP);
        }
        this.stock += cantidad;
    }

    /**
     * Saca las unidades que metió un renglón de compra y <b>le quita su costo al promedio</b>.
     *
     * <pre>
     *   promedioNuevo = (stock x promedioActual - cantidad x costoUnitario)
     *                   / (stock - cantidad)
     * </pre>
     *
     * <p>Ejemplo: 10 a $1.000 y luego 10 a $2.000 dejan 20 a $1.500. Revertir la primera compra
     * deja 10 a $2.000: exactamente lo que habría si nunca hubiera entrado.
     *
     * <p><b>Solo es exacto si nada salió del inventario después de esa compra.</b> Con costo
     * promedio no se sabe si las unidades vendidas eran de esa compra o de otra. Esa condición la
     * exige quien llama (spec 0002, RF-019); aquí se rechaza lo que ya es imposible —sacar más de lo
     * que hay, o un promedio que quedaría en cero o negativo.
     *
     * @param promedioAntesDeLaCompra el promedio que tenía el repuesto antes de esa compra. Solo se
     *        usa si el stock queda en cero: entonces no hay unidades de las cuales promediar, y el
     *        costo vuelve a lo que era — {@code null} si nunca se había comprado, que es "—".
     */
    public void revertirEntradaDeCompra(int cantidad, BigDecimal costoUnitario,
                                        BigDecimal promedioAntesDeLaCompra) {
        exigirCantidadPositiva(cantidad);
        if (costoUnitario == null || costoUnitario.signum() <= 0) {
            throw new ReglaDeNegocioException("El costo de lo que se revierte debe ser mayor a cero");
        }
        if (cantidad > stock) {
            throw new ReglaDeNegocioException("No se puede revertir " + cantidad + " unidades de "
                    + codigo + ": solo hay " + stock + ". Parte de esa compra ya salió del inventario.");
        }

        int resto = stock - cantidad;
        if (resto == 0) {
            this.costoPromedio = promedioAntesDeLaCompra == null ? null
                    : promedioAntesDeLaCompra.setScale(Dinero.ESCALA_UNITARIA, RoundingMode.HALF_UP);
        } else {
            if (costoPromedio == null) {
                throw new ReglaDeNegocioException(
                        "No se puede revertir la compra de " + codigo + ": su costo promedio no se conoce");
            }
            BigDecimal valorActual = costoPromedio.multiply(BigDecimal.valueOf(stock));
            BigDecimal valorQueSale = costoUnitario.multiply(BigDecimal.valueOf(cantidad));
            BigDecimal nuevo = valorActual.subtract(valorQueSale)
                    .divide(BigDecimal.valueOf(resto), Dinero.ESCALA_UNITARIA, RoundingMode.HALF_UP);
            if (nuevo.signum() <= 0) {
                throw new ReglaDeNegocioException("Revertir la compra dejaría el costo promedio de "
                        + codigo + " en cero o negativo: el inventario que queda no alcanza a cubrirlo.");
            }
            this.costoPromedio = nuevo;
        }
        this.stock = resto;
    }

    /** Devuelve unidades sin tocar el promedio: una reversion refleja el promedio vigente real. */
    public void reponerPorReversion(int cantidad) {
        exigirCantidadPositiva(cantidad);
        this.stock += cantidad;
    }

    /**
     * Cambia el precio de venta. Se llama desde la compra cuando el usuario escribe un precio
     * nuevo; si lo deja vacio, este metodo no se invoca y el precio queda como estaba —
     * reponer stock no debe reescribir en silencio un precio que nadie quiso cambiar.
     */
    public void fijarPrecio(Dinero nuevoPrecio) {
        if (nuevoPrecio == null || nuevoPrecio.esNegativo()) {
            throw new ReglaDeNegocioException("El precio no puede ser negativo");
        }
        this.precio = nuevoPrecio;
    }

    /**
     * Corrige la ficha de un repuesto mal creado.
     *
     * <p><b>Fijate en lo que NO se puede tocar desde aqui: el stock y el costo promedio.</b> No es
     * un olvido — es la frontera. Esos dos son el resultado del kardex, y escribirlos a mano
     * rompe el libro mayor: quedaria un saldo que ningun movimiento explica. Se corrigen con un
     * ajuste de inventario, que deja su propio movimiento y su motivo.
     *
     * <p>Que este metodo simplemente no los acepte es la forma mas barata de que nadie lo intente.
     */
    public void corregirFicha(String nuevoCodigo, String nuevaMarca, Dinero nuevoPrecio,
                              int nuevoStockMinimo) {
        if (nuevoCodigo == null || nuevoCodigo.isBlank()) {
            throw new ReglaDeNegocioException("El codigo del repuesto es obligatorio");
        }
        if (nuevaMarca == null || nuevaMarca.isBlank()) {
            throw new ReglaDeNegocioException("La marca del repuesto es obligatoria");
        }
        if (nuevoStockMinimo < 0) {
            throw new ReglaDeNegocioException("El stock minimo no puede ser negativo");
        }
        this.codigo = nuevoCodigo.trim().toUpperCase();
        this.marcaRepuesto = nuevaMarca.trim();
        this.stockMinimo = nuevoStockMinimo;
        fijarPrecio(nuevoPrecio);
    }

    /**
     * La ficha tal como se ve en el antes y el después de corregirla (spec 0002, H7). Solo lo que
     * la ficha deja corregir: stock y costo no están porque no se corrigen desde aquí.
     */
    public Map<String, Object> fotografiaDeFicha() {
        Map<String, Object> foto = new LinkedHashMap<>();
        foto.put("codigo", codigo);
        foto.put("nombre", producto.getNombre());
        foto.put("categoria", producto.getCategoria() == null ? null : producto.getCategoria().getNombre());
        foto.put("aplicacion", producto.getAplicacionOriginal());
        foto.put("marca", marcaRepuesto);
        foto.put("precio", precio.valor().longValueExact());
        foto.put("stockMinimo", stockMinimo);
        return foto;
    }

    public boolean tieneStockBajo() {
        return stock <= stockMinimo;
    }

    /** Utilidad por unidad al precio actual. {@code null} si no se conoce el costo. */
    public BigDecimal utilidadUnitaria() {
        if (costoPromedio == null) return null;
        return precio.valor().subtract(costoPromedio);
    }

    private void exigirCantidadPositiva(int cantidad) {
        if (cantidad <= 0) {
            throw new ReglaDeNegocioException("La cantidad debe ser mayor a cero: " + cantidad);
        }
    }
}
