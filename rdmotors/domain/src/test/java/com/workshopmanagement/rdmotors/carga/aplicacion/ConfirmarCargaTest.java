package com.workshopmanagement.rdmotors.carga.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.carga.aplicacion.ConfirmarCarga.ResultadoConfirmacion;
import com.workshopmanagement.rdmotors.carga.dominio.CargaCambioException;
import com.workshopmanagement.rdmotors.carga.dominio.CargaDeInventario;
import com.workshopmanagement.rdmotors.carga.dominio.EstadoCarga;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.LineaCompra;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/** Confirmar: todo entra como una compra, exacta, una sola vez (spec 0012, H4, RF-015 a RF-018). */
class ConfirmarCargaTest {

    private final EscenarioCargas tienda = new EscenarioCargas();

    private Compra compraDe(ResultadoConfirmacion r) {
        return tienda.compras.buscar(r.compraId()).orElseThrow();
    }

    private LineaCompra lineaDe(Compra compra, String codigo) {
        return compra.lineasVigentes().stream().filter(l -> l.getVariante().getCodigo().equals(codigo)).findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("LA COMPRA QUEDA POR EXACTAMENTE SUB-TOTAL + IVA: $354.412 + $67.338 = $421.750, lo que se pagó")
    void exacta() {
        UUID id = tienda.laMagLista().carga().getId();

        ResultadoConfirmacion r = tienda.confirmar.ejecutar(id, 0, tienda.dueno);

        Compra compra = compraDe(r);
        assertThat(compra.getTotal()).isEqualTo(Dinero.de(421_750));
        assertThat(r.total()).isEqualTo(Dinero.de(421_750));
        assertThat(r.renglones()).isEqualTo(3);
        assertThat(r.unidades()).isEqualTo(14);
        // Cada renglón con su parte del IVA; los pesos que sobran fueron a los de mayor resto.
        assertThat(lineaDe(compra, "524XRE3IJ").getCostoTotal()).isEqualTo(Dinero.de(366_846));
        assertThat(lineaDe(compra, "093AKTCLKI").getCostoTotal()).isEqualTo(Dinero.de(7_364));
        assertThat(lineaDe(compra, "082T3S").getCostoTotal()).isEqualTo(Dinero.de(47_540));
        assertThat(compra.getProveedor().getId()).isEqualTo(tienda.jotapartes.getId());
        assertThat(compra.getNumeroFactura()).isEqualTo("MAG477");
        assertThat(compra.getLlaveIdempotencia()).isEqualTo(id);
    }

    @Test
    @DisplayName("LOS NUEVOS NACEN con su precio final, su marca, su categoría y el nombre sin la marca; el costo con IVA")
    void losNuevos() {
        UUID id = tienda.laMagLista().carga().getId();

        tienda.confirmar.ejecutar(id, 0, tienda.dueno);

        Variante bujia = tienda.variantes.buscarPorCodigo("524XRE3IJ").orElseThrow();
        assertThat(bujia.getPrecio()).isEqualTo(Dinero.de(66_500));
        assertThat(bujia.getMarcaRepuesto()).isEqualTo("NGK");
        assertThat(bujia.getProducto().getNombre()).isEqualTo("BUJIA IRIDIUM CR7HIX");
        assertThat(bujia.getProducto().getCategoria().getId()).isEqualTo(tienda.electrico.getId());
        assertThat(bujia.getStock()).isEqualTo(8);
        assertThat(bujia.getStockMinimo()).isZero();
        // ($308.274 + $58.572 de IVA) ÷ 8.
        assertThat(bujia.getCostoPromedio()).isEqualByComparingTo("45855.7500");
        assertThat(tienda.kardex.historialDe(bujia.getId())).hasSize(1);
        assertThat(tienda.variantes.buscarPorCodigo("093AKTCLKI").orElseThrow().getMarcaRepuesto())
                .isEqualTo("NACIONAL");
    }

    @Test
    @DisplayName("LA REPOSICIÓN SUMA STOCK y conserva su precio, salvo que se elija el nuevo; no crea otro repuesto")
    void reposicion() {
        Variante kit = tienda.yaExiste("093AKTCLKI", 9_000, 4);
        Variante tensor = tienda.yaExiste("082T3S", 12_000, 1);
        UUID id = tienda.laMagLista().carga().getId();
        tienda.editar.aplicarPrecioNuevo(id, 2, true, tienda.dueno);

        tienda.confirmar.ejecutar(id, 2, tienda.dueno);

        assertThat(kit.getStock()).isEqualTo(5);
        assertThat(kit.getPrecio()).isEqualTo(Dinero.de(9_000));
        assertThat(tensor.getStock()).isEqualTo(6);
        // $39.950 × 1,19 ÷ 5 = $9.508,10, más 45% = $13.786,75 → $13.800.
        assertThat(tensor.getPrecio()).isEqualTo(Dinero.de(13_800));
        assertThat(tienda.variantes.buscarPorCodigos(java.util.List.of("093AKTCLKI", "082T3S"))).hasSize(2);
    }

    @Test
    @DisplayName("CONFIRMAR DOS VECES deja una sola compra: la segunda devuelve la que ya entró")
    void dosVeces() {
        UUID id = tienda.laMagLista().carga().getId();

        ResultadoConfirmacion primera = tienda.confirmar.ejecutar(id, 0, tienda.dueno);
        ResultadoConfirmacion segunda = tienda.confirmar.ejecutar(id, 0, tienda.dueno);

        assertThat(segunda.compraId()).isEqualTo(primera.compraId());
        assertThat(segunda.total()).isEqualTo(Dinero.de(421_750));
        assertThat(tienda.variantes.buscarPorCodigo("524XRE3IJ").orElseThrow().getStock()).isEqualTo(8);
        CargaDeInventario carga = tienda.cargas.buscar(id).orElseThrow();
        assertThat(carga.getEstado()).isEqualTo(EstadoCarga.CONFIRMADA);
        assertThat(carga.getCompraId()).isEqualTo(primera.compraId());
        assertThat(carga.getCerradaPorId()).isEqualTo(tienda.dueno.id());
    }

    @Test
    @DisplayName("SI ALGUIEN CREÓ UNO DE ESOS CÓDIGOS A MANO MIENTRAS TANTO, se avisa antes de registrar nada")
    void creadoAMano() {
        UUID id = tienda.laMagLista().carga().getId();
        Variante hechaAMano = tienda.yaExiste("524XRE3IJ", 70_000, 2);

        assertThatThrownBy(() -> tienda.confirmar.ejecutar(id, 0, tienda.dueno))
                .isInstanceOf(CargaCambioException.class)
                .hasMessageContaining("alguien creó a mano uno de estos códigos: ahora es reposición");
        assertThat(tienda.compras.buscarPorLlave(id)).isEmpty();
        assertThat(hechaAMano.getStock()).isEqualTo(2);

        // Con la pre-carga vista de nuevo, entra como reposición: suma, no duplica.
        tienda.confirmar.ejecutar(id, 1, tienda.dueno);
        assertThat(hechaAMano.getStock()).isEqualTo(10);
        assertThat(hechaAMano.getPrecio()).isEqualTo(Dinero.de(70_000));
    }

    @Test
    @DisplayName("CON PROBLEMAS NO SE CONFIRMA, y se dice cuáles; no entra nada")
    void conProblemas() {
        UUID id = tienda.subirLaMag().carga().getId();

        assertThatThrownBy(() -> tienda.confirmar.ejecutar(id, 0, tienda.dueno))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("Todavía no se puede confirmar")
                .hasMessageContaining("Falta la forma de pago")
                .hasMessageContaining("1 renglón tiene problemas");
        assertThat(tienda.compras.buscarPorLlave(id)).isEmpty();
        assertThat(tienda.variantes.buscarPorCodigo("524XRE3IJ")).isEmpty();
        assertThat(tienda.cargas.buscar(id).orElseThrow().getEstado()).isEqualTo(EstadoCarga.BORRADOR);
    }

    @Test
    @DisplayName("los renglones quitados no entran, y la compra lleva el IVA de lo que sí entró")
    void quitados() {
        UUID id = tienda.laMagLista().carga().getId();
        tienda.editar.quitar(id, 2, true, tienda.dueno);

        ResultadoConfirmacion r = tienda.confirmar.ejecutar(id, 0, tienda.dueno);

        // $314.462 + 19% ($59.748).
        assertThat(r.total()).isEqualTo(Dinero.de(374_210));
        assertThat(r.renglones()).isEqualTo(2);
        assertThat(tienda.variantes.buscarPorCodigo("082T3S")).isEmpty();
    }

    @Test
    @DisplayName("el cajero no confirma, y lo descartado tampoco")
    void quienYCuando() {
        UUID id = tienda.laMagLista().carga().getId();

        assertThatThrownBy(() -> tienda.confirmar.ejecutar(id, 0, tienda.cajero))
                .isInstanceOf(NoPermitidoException.class);

        tienda.descartar.ejecutar(id, tienda.dueno);
        assertThatThrownBy(() -> tienda.confirmar.ejecutar(id, 0, tienda.dueno))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessageContaining("se descartó");
    }
}
