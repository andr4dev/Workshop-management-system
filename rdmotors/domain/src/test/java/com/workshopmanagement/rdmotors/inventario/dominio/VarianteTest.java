package com.workshopmanagement.rdmotors.inventario.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * Las reglas de inventario, probadas <b>sin base de datos y sin Spring</b>. Se construye el objeto
 * y se le llama el metodo: eso es todo.
 */
class VarianteTest {

    private Variante filtroInoki() {
        Producto filtro = Producto.nuevo("FILTRO ACEITE", Categoria.nueva("PRUEBAS", 99), "PULSAR NS 200/FI/AS 200-DUKE 200");
        return Variante.nueva(filtro, "352B59K", "INOKI", Dinero.de(6_000), 5);
    }

    @Test
    @DisplayName("nace sin stock y SIN costo — null, no cero")
    void naceSinCosto() {
        Variante v = filtroInoki();
        assertThat(v.getStock()).isZero();
        // null y no cero a proposito: un cero inventado reportaria 100% de margen.
        assertThat(v.getCostoPromedio()).isNull();
        assertThat(v.utilidadUnitaria()).isNull();
    }

    @Test
    @DisplayName("no se puede vender mas de lo que hay")
    void noSeVendeSinStock() {
        Variante v = filtroInoki();
        v.reponerPorCompra(2, new BigDecimal("2977"));

        assertThatThrownBy(() -> v.descontar(5))
                .isInstanceOf(StockInsuficienteException.class)
                .hasMessageContaining("quedan 2")
                .hasMessageContaining("se pidieron 5");

        // y el stock no se movio
        assertThat(v.getStock()).isEqualTo(2);
    }

    @Test
    @DisplayName("costo promedio ponderado: 10 a $1.000 + 10 a $2.000 = $1.500")
    void promedioPonderado() {
        Variante v = filtroInoki();

        v.reponerPorCompra(10, new BigDecimal("1000"));
        assertThat(v.getCostoPromedio()).isEqualByComparingTo("1000.0000");

        v.reponerPorCompra(10, new BigDecimal("2000"));
        assertThat(v.getStock()).isEqualTo(20);
        assertThat(v.getCostoPromedio()).isEqualByComparingTo("1500.0000");  // ni 1000 ni 2000
    }

    @Test
    @DisplayName("vender NO cambia el costo promedio")
    void venderNoTocaElPromedio() {
        Variante v = filtroInoki();
        v.reponerPorCompra(10, new BigDecimal("1500"));

        v.descontar(4);

        assertThat(v.getStock()).isEqualTo(6);
        assertThat(v.getCostoPromedio()).isEqualByComparingTo("1500.0000");
    }

    @Test
    @DisplayName("una reversion devuelve unidades sin tocar el promedio")
    void reversionNoTocaElPromedio() {
        Variante v = filtroInoki();
        v.reponerPorCompra(10, new BigDecimal("1500"));
        v.descontar(3);

        v.reponerPorReversion(3);

        assertThat(v.getStock()).isEqualTo(10);
        assertThat(v.getCostoPromedio()).isEqualByComparingTo("1500.0000");
    }

    @Test
    @DisplayName("costo cero se rechaza: reportaria 100% de utilidad")
    void rechazaCostoCero() {
        Variante v = filtroInoki();
        assertThatThrownBy(() -> v.reponerPorCompra(10, BigDecimal.ZERO))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("100% de utilidad");
    }

    @Test
    @DisplayName("el promedio conserva los 4 decimales del costo unitario del lote")
    void conservaDecimalesDelLote() {
        Variante v = filtroInoki();
        // 15 unidades por $200.000
        BigDecimal unitario = Dinero.de(200_000).dividirEntre(15);
        v.reponerPorCompra(15, unitario);

        assertThat(v.getCostoPromedio()).isEqualByComparingTo("13333.3333");
        // el valor total del inventario reconstruye la factura al peso
        assertThat(Dinero.desdeUnitario(v.getCostoPromedio(), 15)).isEqualTo(Dinero.de(200_000));
    }

    @Test
    @DisplayName("alerta de stock bajo")
    void stockBajo() {
        Variante v = filtroInoki();      // stockMinimo = 5
        v.reponerPorCompra(5, new BigDecimal("2977"));
        assertThat(v.tieneStockBajo()).isTrue();

        v.reponerPorCompra(10, new BigDecimal("2977"));
        assertThat(v.tieneStockBajo()).isFalse();
    }

    // ── Revertir la entrada de una compra (spec 0002, fase 4) ────────────────

    @Test
    @DisplayName("revertir una compra deja el promedio como si nunca hubiera entrado")
    void revertirEsExacto() {
        Variante v = filtroInoki();
        v.reponerPorCompra(10, new BigDecimal("1000"));
        v.reponerPorCompra(10, new BigDecimal("2000"));       // 20 a $1.500

        v.revertirEntradaDeCompra(10, new BigDecimal("1000.0000"), null);

        assertThat(v.getStock()).isEqualTo(10);
        assertThat(v.getCostoPromedio()).isEqualByComparingTo("2000.0000");
    }

    @Test
    @DisplayName("si el stock queda en cero, el costo vuelve al que tenía antes de esa compra")
    void aCeroVuelveAlPromedioAnterior() {
        Variante v = filtroInoki();
        v.reponerPorCompra(10, new BigDecimal("3000"));

        v.revertirEntradaDeCompra(10, new BigDecimal("3000"), new BigDecimal("1000"));

        assertThat(v.getStock()).isZero();
        assertThat(v.getCostoPromedio()).isEqualByComparingTo("1000.0000");
    }

    @Test
    @DisplayName("a cero y nunca comprado antes: el costo vuelve a desconocido, no a cero")
    void aCeroSinHistoriaEsDesconocido() {
        Variante v = filtroInoki();
        v.reponerPorCompra(10, new BigDecimal("3000"));

        v.revertirEntradaDeCompra(10, new BigDecimal("3000"), null);

        assertThat(v.getCostoPromedio()).as("—, nunca $0").isNull();
    }

    @Test
    @DisplayName("no se revierte más de lo que hay: parte de esa compra ya salió")
    void noRevierteMasDeLoQueHay() {
        Variante v = filtroInoki();
        v.reponerPorCompra(10, new BigDecimal("1000"));
        v.descontar(4);

        assertThatThrownBy(() -> v.revertirEntradaDeCompra(10, new BigDecimal("1000"), null))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("solo hay 6");
        assertThat(v.getStock()).isEqualTo(6);
    }

    @Test
    @DisplayName("un promedio que quedaría en cero o negativo se rechaza en vez de guardarse")
    void promedioNoPositivoSeRechaza() {
        Variante v = filtroInoki();
        v.reponerPorCompra(10, new BigDecimal("1000"));
        v.reponerPorCompra(10, new BigDecimal("1000"));

        // Un costo de salida que no corresponde a lo que entró: el valor que queda sería negativo.
        assertThatThrownBy(() -> v.revertirEntradaDeCompra(10, new BigDecimal("5000"), null))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("cero o negativo");
        assertThat(v.getStock()).as("no se aplicó nada").isEqualTo(20);
    }
}

