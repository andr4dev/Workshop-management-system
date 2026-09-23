package com.workshopmanagement.rdmotors.compras.aplicacion;

import static com.workshopmanagement.rdmotors.compras.aplicacion.EscenarioCompras.unidades;
import static com.workshopmanagement.rdmotors.compras.aplicacion.EscenarioCompras.unidadesConPrecio;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.LineaCompra;
import com.workshopmanagement.rdmotors.inventario.dominio.MovimientoKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.TipoMovimiento;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/**
 * Las reglas de deshacer una compra (spec 0002, fase 4): la cuenta del promedio, el bloqueo por
 * salidas posteriores y la vuelta del precio. Es la fase delicada del plan: si esto queda mal, el
 * costo de un repuesto queda mal para siempre y el margen de todas sus ventas con él.
 */
class InventarioDeCompraTest {

    private EscenarioCompras t;

    @BeforeEach
    void preparar() {
        t = new EscenarioCompras();
    }

    private List<String> revertir(Compra compra, Variante v) {
        return t.inventario.revertirEntrada(t.renglonDe(compra, v), TipoMovimiento.CORRECCION_COMPRA,
                compra.getId(), "prueba", t.usuario.id(), t.reloj.ahora());
    }

    // ── La cuenta ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("revertir la primera de dos compras deja justo lo de la segunda")
    void revertirPrimeraCompra() {
        Variante filtro = t.repuesto("352B59K", 6_000);
        Compra primera = t.comprar(unidades(filtro, 10, 1_000));
        t.comprar(unidades(filtro, 10, 2_000));                     // 20 a $1.500

        revertir(primera, filtro);

        assertThat(filtro.getStock()).isEqualTo(10);
        assertThat(filtro.getCostoPromedio()).isEqualByComparingTo("2000.0000");
    }

    @Test
    @DisplayName("TOLERANCIA: con cifras incómodas el valor del repuesto se desvía menos de $1")
    void toleranciaDeRedondeo() {
        Variante filtro = t.repuesto("352B59K", 6_000);
        Compra primera = t.comprar(ComandoRegistrarCompra.Linea.porTotal(
                filtro.getId(), 15, Dinero.de(200_000), null));    // $13.333,3333 c/u
        t.comprar(unidades(filtro, 10, 20_000));

        revertir(primera, filtro);

        // Lo correcto es 10 a $20.000 = $200.000. Los 4 decimales dejan un residuo al deshacer
        // promedios; lo declarado en el plan es que no llegue a un peso en el valor del repuesto.
        BigDecimal valor = filtro.getCostoPromedio().multiply(BigDecimal.valueOf(filtro.getStock()));
        assertThat(valor.subtract(new BigDecimal("200000")).abs()).isLessThan(BigDecimal.ONE);
    }

    @Test
    @DisplayName("si el stock queda en cero, el costo vuelve al que tenía antes de esa compra")
    void aCeroVuelveAlCostoAnterior() {
        Variante filtro = t.repuesto("352B59K", 6_000);
        t.comprar(unidades(filtro, 5, 1_000));
        t.vender(filtro, 5);                                        // una salida ANTES de la compra
        Compra segunda = t.comprar(unidades(filtro, 10, 2_000));

        assertThat(t.inventario.bloqueados(List.of(t.renglonDe(segunda, filtro))))
                .as("la venta fue antes de la compra: no bloquea").isEmpty();

        revertir(segunda, filtro);

        assertThat(filtro.getStock()).isZero();
        assertThat(filtro.getCostoPromedio()).isEqualByComparingTo("1000.0000");
    }

    @Test
    @DisplayName("a cero y sin compras anteriores, el costo vuelve a desconocido")
    void aCeroSinHistoria() {
        Variante filtro = t.repuesto("352B59K", 6_000);
        Compra unica = t.comprar(unidades(filtro, 10, 2_000));

        revertir(unica, filtro);

        assertThat(filtro.getCostoPromedio()).as("—, nunca $0").isNull();
    }

    @Test
    @DisplayName("la reversión queda en el kardex apuntando a la entrada que deshace, con su motivo")
    void kardexDeLaReversion() {
        Variante filtro = t.repuesto("352B59K", 6_000);
        Compra compra = t.comprar(unidades(filtro, 10, 2_000));
        LineaCompra renglon = t.renglonDe(compra, filtro);

        revertir(compra, filtro);

        var salida = t.kardex.historialDe(filtro.getId()).getLast();
        assertThat(salida.getTipo()).isEqualTo(TipoMovimiento.CORRECCION_COMPRA);
        assertThat(salida.getCantidadDelta()).isEqualTo(-10);
        assertThat(salida.getMovimientoRevertidoId()).isEqualTo(renglon.getMovimientoEntradaId());
        assertThat(salida.getMotivo()).isEqualTo("prueba");
        assertThat(salida.getSaldoDespues()).isZero();
    }

    // ── El bloqueo (RF-019) ──────────────────────────────────────────────────

    @Test
    @DisplayName("una venta DESPUÉS de la compra la bloquea: no se sabe si esas unidades siguen")
    void ventaPosteriorBloquea() {
        Variante filtro = t.repuesto("352B59K", 6_000);
        Compra compra = t.comprar(unidades(filtro, 10, 2_000));
        t.vender(filtro, 1);

        assertThat(t.inventario.bloqueados(List.of(t.renglonDe(compra, filtro))))
                .containsExactly("352B59K");
    }

    @Test
    @DisplayName("una venta posterior bloquea AUNQUE otra compra ya haya repuesto el stock")
    void ventaPosteriorBloqueaConStockRepuesto() {
        Variante filtro = t.repuesto("352B59K", 6_000);
        Compra primera = t.comprar(unidades(filtro, 10, 1_000));
        t.vender(filtro, 3);
        t.comprar(unidades(filtro, 10, 2_000));                     // hay 17: alcanzaría para sacar 10

        // Con stock de sobra, lo único que impide revertir es saber que hubo una venta en el medio:
        // esas 3 unidades se costearon con el promedio de la primera compra.
        assertThat(t.inventario.bloqueados(List.of(t.renglonDe(primera, filtro))))
                .containsExactly("352B59K");
    }

    // ── La venta anulada (spec 0003, RF-030) ─────────────────────────────────

    @Test
    @DisplayName("RF-030: una venta posterior ANULADA ya no bloquea, y revertir la compra da la cuenta exacta")
    void ventaAnuladaNoBloquea() {
        Variante filtro = t.repuesto("352B59K", 6_000);
        Compra primera = t.comprar(unidades(filtro, 10, 1_000));
        t.comprar(unidades(filtro, 10, 2_000));                     // 20 a $1.500
        MovimientoKardex salida = t.vender(filtro, 3);
        t.anularVenta(filtro, salida);

        assertThat(t.inventario.bloqueados(List.of(t.renglonDe(primera, filtro)))).isEmpty();

        revertir(primera, filtro);
        // Como si la venta nunca hubiera existido: quedan los 10 de la segunda compra, a su costo.
        assertThat(filtro.getStock()).isEqualTo(10);
        assertThat(filtro.getCostoPromedio()).isEqualByComparingTo("2000.0000");
    }

    @Test
    @DisplayName("RF-030: si una compra movió el promedio entre la venta y su anulación, SÍ sigue bloqueando")
    void ventaAnuladaConCompraEnElMedioBloquea() {
        Variante filtro = t.repuesto("352B59K", 6_000);
        Compra primera = t.comprar(unidades(filtro, 10, 1_000));
        MovimientoKardex salida = t.vender(filtro, 3);              // salen 3 a $1.000
        t.comprar(unidades(filtro, 10, 2_000));                     // 17: promedio de 7 a $1.000 y 10 a $2.000
        t.anularVenta(filtro, salida);                              // vuelven 3 al promedio de hoy, no a $1.000

        // Revertir la primera compra daría ~$2.176 en vez de $2.000: esas 3 unidades no volvieron al
        // costo con que salieron. Se bloquea como una venta normal.
        assertThat(t.inventario.bloqueados(List.of(t.renglonDe(primera, filtro))))
                .containsExactly("352B59K");
    }

    @Test
    @DisplayName("RF-030: de dos ventas posteriores, anular una no alcanza: la otra sigue bloqueando")
    void unaDeDosVentasAnuladas() {
        Variante filtro = t.repuesto("352B59K", 6_000);
        Compra compra = t.comprar(unidades(filtro, 10, 2_000));
        MovimientoKardex primera = t.vender(filtro, 1);
        t.vender(filtro, 2);
        t.anularVenta(filtro, primera);

        assertThat(t.inventario.bloqueados(List.of(t.renglonDe(compra, filtro))))
                .containsExactly("352B59K");
    }

    @Test
    @DisplayName("la reversión de OTRA compra del mismo repuesto no bloquea: devuelve, no consume")
    void reversionDeOtraCompraNoBloquea() {
        Variante filtro = t.repuesto("352B59K", 6_000);
        Compra primera = t.comprar(unidades(filtro, 10, 1_000));
        Compra segunda = t.comprar(unidades(filtro, 10, 2_000));
        revertir(segunda, filtro);

        assertThat(t.inventario.bloqueados(List.of(t.renglonDe(primera, filtro)))).isEmpty();
    }

    // ── El precio (RF-017) ───────────────────────────────────────────────────

    @Test
    @DisplayName("el precio que fijó el renglón vuelve al que había antes")
    void precioVuelve() {
        Variante filtro = t.repuesto("352B59K", 6_000);
        Compra compra = t.comprar(unidadesConPrecio(filtro, 10, 2_000, 9_000));

        assertThat(revertir(compra, filtro)).isEmpty();

        assertThat(filtro.getPrecio()).isEqualTo(Dinero.de(6_000));
    }

    @Test
    @DisplayName("si una compra posterior fijó precio, se deja el vigente y se avisa — aunque sea el mismo")
    void otraCompraPosteriorLoFijo() {
        Variante filtro = t.repuesto("352B59K", 6_000);
        Compra primera = t.comprar(unidadesConPrecio(filtro, 10, 2_000, 9_000));
        t.comprar(unidadesConPrecio(filtro, 10, 2_000, 9_000));    // la segunda lo fija igual

        assertThat(revertir(primera, filtro)).singleElement().asString().contains("cambió después");

        assertThat(filtro.getPrecio()).isEqualTo(Dinero.de(9_000));
    }

    @Test
    @DisplayName("si el precio cambió después por otro lado, se deja el vigente y se avisa")
    void precioCambioDespues() {
        Variante filtro = t.repuesto("352B59K", 6_000);
        Compra compra = t.comprar(unidadesConPrecio(filtro, 10, 2_000, 9_000));
        filtro.fijarPrecio(Dinero.de(11_000));                     // por ejemplo, desde la ficha

        assertThat(revertir(compra, filtro)).singleElement().asString().contains("cambió después");

        assertThat(filtro.getPrecio()).isEqualTo(Dinero.de(11_000));
    }

    @Test
    @DisplayName("un renglón viejo sin precio anterior guardado no inventa uno: deja el vigente y avisa")
    void precioAnteriorDesconocido() {
        Variante filtro = t.repuesto("352B59K", 6_000);
        Compra compra = t.comprar(unidadesConPrecio(filtro, 10, 2_000, 9_000));
        LineaCompra renglon = t.renglonDe(compra, filtro);
        // Así quedan los renglones registrados antes de la migración que guarda el precio anterior.
        renglon.anotarEntrada(renglon.getMovimientoEntradaId(), null);

        assertThat(revertir(compra, filtro)).singleElement().asString().contains("antes de que se guardara");

        assertThat(filtro.getPrecio()).isEqualTo(Dinero.de(9_000));
    }

    @Test
    @DisplayName("registrar guarda con qué movimiento entró cada renglón y qué precio había antes")
    void registrarAnotaLaEntrada() {
        Variante filtro = t.repuesto("352B59K", 6_000);
        Compra compra = t.comprar(unidadesConPrecio(filtro, 10, 2_000, 9_000));
        LineaCompra renglon = t.renglonDe(compra, filtro);

        assertThat(t.kardex.buscar(renglon.getMovimientoEntradaId())).isPresent();
        assertThat(renglon.getPrecioAnterior()).isEqualTo(Dinero.de(6_000));
        assertThat(t.kardex.buscar(UUID.randomUUID())).isEmpty();
    }
}
