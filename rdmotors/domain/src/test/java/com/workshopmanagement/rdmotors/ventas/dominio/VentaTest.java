package com.workshopmanagement.rdmotors.ventas.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.Producto;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/** Las reglas de una venta que se deciden con los números: renglones, descuento y pagos (spec 0003). */
class VentaTest {

    private static final Instant AHORA = Instant.parse("2026-09-14T15:00:00Z");
    private final UUID turno = UUID.randomUUID();
    private final UUID cajero = UUID.randomUUID();

    private final Variante filtro = Variante.nueva(Producto.nuevo("FILTRO ACEITE", Categoria.nueva("PRUEBAS", 99), null),
            "352B59K", "INOKI", Dinero.de(13_000), 2);
    private final Variante pastillas = Variante.nueva(Producto.nuevo("PASTILLAS FRENO", Categoria.nueva("PRUEBAS", 99), null),
            "152RTX2B", "CBI", Dinero.de(12_000), 2);

    private Venta cobrar(List<LineaVenta> lineas, Descuento descuento, PagoVenta... pagos) {
        return Venta.cobrar(1, turno, lineas, descuento, List.of(pagos), cajero, UUID.randomUUID(), AHORA);
    }

    private List<LineaVenta> filtroYPastillas() {
        // 2 × $13.000 + 1 × $12.000 = $38.000
        return List.of(LineaVenta.de(filtro, 2), LineaVenta.de(pastillas, 1));
    }

    // ── Anular (RF-025 a RF-027) ─────────────────────────────────────────────

    @Test
    @DisplayName("anular deja quién, cuándo, en qué turno y por qué, y no cambia el número ni las cifras")
    void anular() {
        Venta venta = cobrar(filtroYPastillas(), null, PagoVenta.transferencia(Dinero.de(38_000)));
        UUID admin = UUID.randomUUID();
        UUID turnoDeHoy = UUID.randomUUID();
        Instant despues = AHORA.plusSeconds(3_600);

        venta.anular("  Cliente se arrepintió  ", admin, turnoDeHoy, despues);

        assertThat(venta.getEstado()).isEqualTo(EstadoVenta.ANULADA);
        assertThat(venta.estaAnulada()).isTrue();
        assertThat(venta.getMotivoAnulacion()).isEqualTo("Cliente se arrepintió");
        assertThat(venta.getAnuladaPorId()).isEqualTo(admin);
        assertThat(venta.getAnuladaEn()).isEqualTo(despues);
        // RF-026: el turno de la anulación es el de hoy; el de la venta no se toca.
        assertThat(venta.getAnuladaEnTurnoId()).isEqualTo(turnoDeHoy);
        assertThat(venta.getTurnoId()).isEqualTo(turno);
        assertThat(venta.getNumero()).isEqualTo(1);
        assertThat(venta.getTotal()).isEqualTo(Dinero.de(38_000));
        assertThat(venta.fotografia()).containsEntry("estado", "ANULADA")
                .containsEntry("motivoAnulacion", "Cliente se arrepintió");
    }

    @Test
    @DisplayName("una anulada no se vuelve a anular; sin motivo o sin turno no se anula, y la venta sigue cobrada")
    void reglasDeAnular() {
        Venta venta = cobrar(filtroYPastillas(), null, PagoVenta.transferencia(Dinero.de(38_000)));

        assertThatThrownBy(() -> venta.anular("   ", cajero, turno, AHORA))
                .isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(() -> venta.anular("se cobró mal", cajero, null, AHORA))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("turno abierto");
        assertThatThrownBy(() -> venta.anular("se cobró mal", null, turno, AHORA))
                .isInstanceOf(ReglaDeNegocioException.class);
        assertThat(venta.getEstado()).isEqualTo(EstadoVenta.COBRADA);
        assertThat(venta.getAnuladaEn()).isNull();

        venta.anular("se cobró mal", cajero, turno, AHORA);
        assertThatThrownBy(() -> venta.anular("otra vez", cajero, turno, AHORA))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessage("Esta venta ya fue anulada");
        assertThat(venta.getMotivoAnulacion()).isEqualTo("se cobró mal");
    }

    @Test
    @DisplayName("las partes suman el total: renglones = subtotal, subtotal − descuento = total, pagos = total")
    void partesSumanElTotal() {
        Venta venta = cobrar(filtroYPastillas(), Descuento.porMonto(Dinero.de(3_000), "negociación"),
                PagoVenta.efectivo(Dinero.de(35_000), Dinero.de(50_000)));

        assertThat(venta.getSubtotal()).isEqualTo(Dinero.de(38_000));
        assertThat(venta.getDescuentoMonto()).isEqualTo(Dinero.de(3_000));
        assertThat(venta.getTotal()).isEqualTo(Dinero.de(35_000));
        assertThat(venta.getLineas()).extracting(LineaVenta::getPosicion).containsExactly(0, 1);
        assertThat(venta.getLineas().get(0).getTotal()).isEqualTo(Dinero.de(26_000));
        assertThat(venta.getEstado()).isEqualTo(EstadoVenta.COBRADA);
        assertThat(venta.cambio()).isEqualTo(Dinero.de(15_000));
    }

    @Test
    @DisplayName("el precio queda como foto: si el repuesto sube después, el renglón no cambia")
    void precioComoFoto() {
        Venta venta = cobrar(filtroYPastillas(), null, PagoVenta.transferencia(Dinero.de(38_000)));

        filtro.fijarPrecio(Dinero.de(20_000));

        assertThat(venta.getLineas().get(0).getPrecioUnitario()).isEqualTo(Dinero.de(13_000));
        assertThat(venta.getTotal()).isEqualTo(Dinero.de(38_000));
    }

    @Test
    @DisplayName("mixto: $20.000 en efectivo y $18.000 por transferencia cuadran con $38.000")
    void mixto() {
        Venta venta = cobrar(filtroYPastillas(), null,
                PagoVenta.efectivo(Dinero.de(20_000), Dinero.de(20_000)),
                PagoVenta.transferencia(Dinero.de(18_000)));

        assertThat(venta.getPagos()).extracting(PagoVenta::getForma)
                .containsExactly(FormaPago.EFECTIVO, FormaPago.TRANSFERENCIA);
        assertThat(venta.cambio()).isEqualTo(Dinero.CERO);
    }

    @Test
    @DisplayName("si los pagos no suman el total, no se cobra, y dice cuánto falta o sobra")
    void pagosQueNoCuadran() {
        assertThatThrownBy(() -> cobrar(filtroYPastillas(), null, PagoVenta.efectivo(Dinero.de(30_000), null)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("faltan $8.000");
        assertThatThrownBy(() -> cobrar(filtroYPastillas(), null, PagoVenta.transferencia(Dinero.de(40_000))))
                .hasMessageContaining("sobran $2.000");
        assertThatThrownBy(() -> cobrar(filtroYPastillas(), null))
                .hasMessageContaining("faltan $38.000");
    }

    @Test
    @DisplayName("lo recibido en efectivo no puede ser menor que la parte en efectivo")
    void recibidoMenor() {
        assertThatThrownBy(() -> PagoVenta.efectivo(Dinero.de(38_000), Dinero.de(20_000)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("no alcanza");
    }

    @Test
    @DisplayName("dos pagos de la misma forma se rechazan: se suman en uno")
    void dosPagosIguales() {
        assertThatThrownBy(() -> cobrar(filtroYPastillas(), null,
                PagoVenta.efectivo(Dinero.de(19_000), null), PagoVenta.efectivo(Dinero.de(19_000), null)))
                .hasMessageContaining("dos pagos en efectivo");
    }

    @Test
    @DisplayName("un descuento mayor que el total se rechaza; uno del 100% deja la venta en $0 sin pagos")
    void descuentoTopado() {
        assertThatThrownBy(() -> cobrar(filtroYPastillas(), Descuento.porMonto(Dinero.de(38_001), "x"),
                PagoVenta.efectivo(Dinero.de(1), null)))
                .hasMessageContaining("no puede ser mayor");

        Venta gratis = cobrar(filtroYPastillas(),
                Descuento.porPorcentaje(new BigDecimal("100"), Dinero.de(38_000), "garantía"));
        assertThat(gratis.getTotal()).isEqualTo(Dinero.CERO);
        assertThat(gratis.getPagos()).isEmpty();
    }

    @Test
    @DisplayName("sin repuestos, o con el mismo repuesto dos veces, no hay venta")
    void renglonesInvalidos() {
        assertThatThrownBy(() -> cobrar(List.of(), null)).hasMessageContaining("no tiene repuestos");
        assertThatThrownBy(() -> cobrar(List.of(LineaVenta.de(filtro, 1), LineaVenta.de(filtro, 1)), null,
                PagoVenta.efectivo(Dinero.de(26_000), null)))
                .hasMessageContaining("aparece dos veces");
        assertThatThrownBy(() -> LineaVenta.de(filtro, 0)).hasMessageContaining("mayor a 0");
    }

    @Test
    @DisplayName("la auditoría del descuento guarda cómo iba la venta y cómo quedó")
    void fotografiasDelDescuento() {
        Venta venta = cobrar(filtroYPastillas(),
                Descuento.porPorcentaje(new BigDecimal("10"), Dinero.de(38_000), "cliente frecuente"),
                PagoVenta.efectivo(Dinero.de(34_200), null));

        assertThat(venta.fotografiaSinDescuento()).containsEntry("total", 38_000L);
        assertThat(venta.fotografiaConDescuento())
                .containsEntry("descuento", 3_800L)
                .containsEntry("modo", "PORCENTAJE")
                .containsEntry("total", 34_200L);
    }

    // ── Fiar (spec 0008, RF-006) ─────────────────────────────────────────────

    private Venta fiada(UUID cliente, long fiado, PagoVenta... pagos) {
        return Venta.cobrar(1, turno, filtroYPastillas(), null, List.of(pagos), cliente, Dinero.de(fiado), cajero,
                UUID.randomUUID(), AHORA);
    }

    @Test
    @DisplayName("fiar una parte: $20.000 en efectivo y $18.000 fiados suman los $38.000 de la venta")
    void fiarUnaParte() {
        UUID juan = UUID.randomUUID();
        Venta venta = fiada(juan, 18_000, PagoVenta.efectivo(Dinero.de(20_000), null));

        assertThat(venta.getTotal()).isEqualTo(Dinero.de(38_000));
        assertThat(venta.getFiado()).isEqualTo(Dinero.de(18_000));
        assertThat(venta.tieneFiado()).isTrue();
        assertThat(venta.getClienteId()).isEqualTo(juan);
        assertThat(venta.fotografia()).containsEntry("fiado", 18_000L).containsEntry("clienteId", juan);
    }

    @Test
    @DisplayName("fiar todo: la venta no tiene pagos y lo fiado es el total")
    void fiarTodo() {
        Venta venta = fiada(UUID.randomUUID(), 38_000);

        assertThat(venta.getPagos()).isEmpty();
        assertThat(venta.getFiado()).isEqualTo(Dinero.de(38_000));
    }

    @Test
    @DisplayName("lo pagado y lo fiado tienen que dar el total al peso, y el mensaje dice cuánto falta o sobra")
    void pagadoMasFiadoDaElTotal() {
        UUID juan = UUID.randomUUID();
        assertThatThrownBy(() -> fiada(juan, 10_000, PagoVenta.efectivo(Dinero.de(20_000), null)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessage("Lo pagado ($20.000) y lo fiado ($10.000) suman $30.000: faltan $8.000 para el total de $38.000");
        assertThatThrownBy(() -> fiada(juan, 30_000, PagoVenta.efectivo(Dinero.de(20_000), null)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("sobran $12.000");
    }

    @Test
    @DisplayName("sin decir a quién no se fía; lo fiado no es negativo; una venta de contado a nombre de alguien vale")
    void fiarExigeCliente() {
        assertThatThrownBy(() -> fiada(null, 38_000))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("a quién");
        assertThatThrownBy(() -> fiada(UUID.randomUUID(), -1_000, PagoVenta.efectivo(Dinero.de(39_000), null)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("negativo");

        UUID juan = UUID.randomUUID();
        Venta deContado = fiada(juan, 0, PagoVenta.transferencia(Dinero.de(38_000)));
        assertThat(deContado.tieneFiado()).isFalse();
        assertThat(deContado.getClienteId()).isEqualTo(juan);
    }

    @Test
    @DisplayName("una venta de siempre queda sin cliente y con $0 fiado")
    void deContadoSinCliente() {
        Venta venta = cobrar(filtroYPastillas(), null, PagoVenta.transferencia(Dinero.de(38_000)));

        assertThat(venta.getFiado()).isEqualTo(Dinero.CERO);
        assertThat(venta.getClienteId()).isNull();
        assertThat(venta.fotografia()).doesNotContainKey("fiado");
    }
}
