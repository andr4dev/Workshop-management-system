package com.workshopmanagement.rdmotors.ventas.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.caja.dominio.SinTurnoAbiertoException;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.clientes.aplicacion.FiarVenta;
import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.inventario.dominio.MovimientoKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.Producto;
import com.workshopmanagement.rdmotors.inventario.dominio.TipoMovimiento;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ComandoCobrarVenta.ComandoDescuento;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ComandoCobrarVenta.Pago;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ComandoCobrarVenta.Renglon;
import com.workshopmanagement.rdmotors.ventas.dominio.ModoDescuento;
import com.workshopmanagement.rdmotors.ventas.dominio.ProblemaDeRenglon;
import com.workshopmanagement.rdmotors.ventas.dominio.RenglonesConProblemaException;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;

/**
 * Cobrar una venta (spec 0003, fase 2). Las dos que más protegen: {@link #stockYKardex()} —el stock
 * baja y el costo promedio NO se mueve— y {@link #problemasListadosSinAplicarNada()}, que falla con
 * todos los renglones y deja el inventario intacto.
 */
class CobrarVentaTest {

    private Falsos.VentasEnMemoria ventas;
    private Falsos.TurnosEnMemoria turnos;
    private Falsos.VariantesEnMemoria variantes;
    private Falsos.KardexEnMemoria kardex;
    private Falsos.AuditoriaEnMemoria auditoria;
    private CobrarVenta cobrarVenta;

    private final Actor cajero = ActoresDePrueba.cajero();
    private Variante filtro;
    private Variante pastillas;

    @BeforeEach
    void preparar() {
        ventas = new Falsos.VentasEnMemoria();
        turnos = new Falsos.TurnosEnMemoria();
        variantes = new Falsos.VariantesEnMemoria();
        kardex = new Falsos.KardexEnMemoria();
        auditoria = new Falsos.AuditoriaEnMemoria();
        Falsos.RelojFijo reloj = new Falsos.RelojFijo("2026-09-14T15:00:00Z");
        cobrarVenta = new CobrarVenta(ventas, turnos, variantes, kardex, auditoria,
                new FiarVenta(new Falsos.ClientesEnMemoria(), new Falsos.DeudasEnMemoria(), new Falsos.AbonosEnMemoria()), reloj);

        turnos.guardar(TurnoCaja.abrir(Dinero.de(100_000), cajero.id(), reloj.ahora()));

        // 10 filtros a $8.000 de costo, se venden a $13.000; 5 pastillas a $7.500, se venden a $12.000.
        filtro = variantes.sembrar(Variante.nueva(Producto.nuevo("FILTRO ACEITE", Categoria.nueva("PRUEBAS", 99), null),
                "352B59K", "INOKI", Dinero.de(13_000), 2));
        filtro.reponerPorCompra(10, new BigDecimal("8000"));
        pastillas = variantes.sembrar(Variante.nueva(Producto.nuevo("PASTILLAS FRENO", Categoria.nueva("PRUEBAS", 99), null),
                "152RTX2B", "CBI", Dinero.de(12_000), 2));
        pastillas.reponerPorCompra(5, new BigDecimal("7500"));
    }

    /** 2 filtros + 1 pastilla = $38.000, con los precios que muestra la pantalla. */
    private List<Renglon> filtroYPastillas() {
        return List.of(new Renglon(filtro.getId(), 2, 13_000), new Renglon(pastillas.getId(), 1, 12_000));
    }

    private ComandoCobrarVenta comando(List<Renglon> renglones, ComandoDescuento descuento, Pago... pagos) {
        return new ComandoCobrarVenta(UUID.randomUUID(), renglones, descuento, List.of(pagos), cajero);
    }

    private static Pago efectivo(long monto, Long recibido) {
        return new Pago(FormaPago.EFECTIVO, monto, recibido);
    }

    @Test
    @DisplayName("cobra: el stock baja la cantidad exacta, el kardex registra la venta al costo promedio, y el promedio NO cambia")
    void stockYKardex() {
        ResultadoCobro resultado = cobrarVenta.ejecutar(comando(filtroYPastillas(), null, efectivo(38_000, 50_000L)));

        Venta venta = resultado.venta();
        assertThat(resultado.repetida()).isFalse();
        assertThat(venta.getNumero()).isEqualTo(1);
        assertThat(venta.getTotal()).isEqualTo(Dinero.de(38_000));
        assertThat(venta.cambio()).isEqualTo(Dinero.de(12_000));
        assertThat(venta.getTurnoId()).isEqualTo(turnos.abierto().orElseThrow().getId());

        assertThat(filtro.getStock()).isEqualTo(8);
        assertThat(pastillas.getStock()).isEqualTo(4);
        // Vender no cambia lo que costó comprar.
        assertThat(filtro.getCostoPromedio()).isEqualByComparingTo("8000");

        assertThat(kardex.movimientos).hasSize(2).allSatisfy(m -> {
            assertThat(m.getTipo()).isEqualTo(TipoMovimiento.VENTA);
            assertThat(m.getOrigenId()).isEqualTo(venta.getId());
        });
        MovimientoKardex salidaFiltro = kardex.movimientos.stream()
                .filter(m -> m.getVariante() == filtro).findFirst().orElseThrow();
        assertThat(salidaFiltro.getCantidadDelta()).isEqualTo(-2);
        assertThat(salidaFiltro.getCostoTotal()).isEqualTo(Dinero.de(16_000));
        assertThat(salidaFiltro.getSaldoDespues()).isEqualTo(8);
        assertThat(venta.getLineas().get(0).getMovimientoSalidaId()).isEqualTo(salidaFiltro.getId());
        assertThat(ventas.datos).containsValue(venta);
    }

    @Test
    @DisplayName("los números corren 1, 2, 3")
    void numerosCorridos() {
        for (int i = 0; i < 3; i++) {
            cobrarVenta.ejecutar(comando(List.of(new Renglon(filtro.getId(), 1, 13_000)), null, efectivo(13_000, null)));
        }
        assertThat(ventas.datos.values()).extracting(Venta::getNumero).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("LOS PROBLEMAS SE LISTAN TODOS y no se aplica nada: precio cambiado y sin stock en renglones distintos")
    void problemasListadosSinAplicarNada() {
        filtro.fijarPrecio(Dinero.de(14_000));   // entró una compra con precio nuevo mientras se armaba la venta

        assertThatThrownBy(() -> cobrarVenta.ejecutar(comando(List.of(
                new Renglon(filtro.getId(), 2, 13_000),
                new Renglon(pastillas.getId(), 6, 12_000)), null, efectivo(98_000, null))))
                .isInstanceOfSatisfying(RenglonesConProblemaException.class, e -> {
                    assertThat(e.getProblemas()).extracting(ProblemaDeRenglon::codigo, ProblemaDeRenglon::tipo)
                            .containsExactly(
                                    org.assertj.core.groups.Tuple.tuple("352B59K", ProblemaDeRenglon.Tipo.PRECIO_CAMBIADO),
                                    org.assertj.core.groups.Tuple.tuple("152RTX2B", ProblemaDeRenglon.Tipo.SIN_STOCK));
                    assertThat(e.getProblemas().get(0).precioActual()).isEqualTo(14_000L);
                    assertThat(e.getProblemas().get(1).disponible()).isEqualTo(5);
                    assertThat(e.getMessage()).contains("$13.000 a $14.000").contains("solo quedan 5");
                });

        assertThat(filtro.getStock()).isEqualTo(10);
        assertThat(pastillas.getStock()).isEqualTo(5);
        assertThat(kardex.movimientos).isEmpty();
        assertThat(ventas.datos).isEmpty();
    }

    @Test
    @DisplayName("un repuesto con precio $0, inactivo o inexistente no se cobra")
    void noVendibles() throws Exception {
        filtro.fijarPrecio(Dinero.CERO);
        Field activa = Variante.class.getDeclaredField("activa");
        activa.setAccessible(true);
        activa.set(pastillas, false);
        UUID fantasma = UUID.randomUUID();

        assertThatThrownBy(() -> cobrarVenta.ejecutar(comando(List.of(
                new Renglon(filtro.getId(), 1, 0),
                new Renglon(pastillas.getId(), 1, 12_000),
                new Renglon(fantasma, 1, 5_000)), null, efectivo(17_000, null))))
                .isInstanceOfSatisfying(RenglonesConProblemaException.class, e ->
                        assertThat(e.getProblemas()).extracting(ProblemaDeRenglon::tipo).containsExactly(
                                ProblemaDeRenglon.Tipo.SIN_PRECIO, ProblemaDeRenglon.Tipo.INACTIVO,
                                ProblemaDeRenglon.Tipo.NO_EXISTE));
    }

    @Test
    @DisplayName("sin turno abierto no se vende")
    void sinTurno() {
        turnos.datos.clear();

        assertThatThrownBy(() -> cobrarVenta.ejecutar(comando(filtroYPastillas(), null, efectivo(38_000, null))))
                .isInstanceOf(SinTurnoAbiertoException.class);
        assertThat(filtro.getStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("LA MISMA LLAVE devuelve la misma venta y no descuenta stock dos veces")
    void mismaLlave() {
        ComandoCobrarVenta mismo = comando(filtroYPastillas(), null, efectivo(38_000, null));

        ResultadoCobro primero = cobrarVenta.ejecutar(mismo);
        ResultadoCobro segundo = cobrarVenta.ejecutar(mismo);

        assertThat(segundo.repetida()).isTrue();
        assertThat(segundo.venta().getId()).isEqualTo(primero.venta().getId());
        assertThat(filtro.getStock()).isEqualTo(8);
        assertThat(kardex.movimientos).hasSize(2);
        assertThat(ventas.datos).hasSize(1);
    }

    @Test
    @DisplayName("bloquea los repuestos en orden de id, no en el orden de la venta")
    void bloqueaEnOrden() {
        List<Renglon> alReves = filtroYPastillas().stream()
                .sorted(Comparator.comparing(Renglon::varianteId).reversed())
                .toList();

        cobrarVenta.ejecutar(comando(alReves, null, efectivo(38_000, null)));

        assertThat(variantes.ordenDeBloqueo).isSorted();
        // Y el comprobante conserva el orden en que el cajero agregó los renglones.
        assertThat(ventas.datos.values().iterator().next().getLineas())
                .extracting(l -> l.getVariante().getId())
                .containsExactlyElementsOf(alReves.stream().map(Renglon::varianteId).toList());
    }

    @Test
    @DisplayName("un descuento del 10% guarda $3.800, el total queda en $34.200, y deja el evento con quién y por qué")
    void descuentoAuditado() {
        Venta venta = cobrarVenta.ejecutar(comando(filtroYPastillas(),
                new ComandoDescuento(ModoDescuento.PORCENTAJE, new BigDecimal("10"), "cliente frecuente"),
                efectivo(34_200, null))).venta();

        assertThat(venta.getDescuentoMonto()).isEqualTo(Dinero.de(3_800));
        assertThat(venta.getTotal()).isEqualTo(Dinero.de(34_200));
        assertThat(auditoria.eventos).singleElement().satisfies(e -> {
            assertThat(e.accion()).isEqualTo(AccionAuditada.APLICAR_DESCUENTO);
            assertThat(e.entidadId()).isEqualTo(venta.getId());
            assertThat(e.usuarioId()).isEqualTo(cajero.id());
            assertThat(e.motivo()).isEqualTo("cliente frecuente");
            assertThat(e.despues()).containsEntry("total", 34_200L);
        });
    }

    @Test
    @DisplayName("sin descuento no hay evento de auditoría")
    void sinDescuentoSinEvento() {
        cobrarVenta.ejecutar(comando(filtroYPastillas(), null, new Pago(FormaPago.TRANSFERENCIA, 38_000, null)));

        assertThat(auditoria.eventos).isEmpty();
    }

    @Test
    @DisplayName("el mismo repuesto dos veces se rechaza antes de bloquear nada")
    void repuestoRepetido() {
        assertThatThrownBy(() -> cobrarVenta.ejecutar(comando(List.of(
                new Renglon(filtro.getId(), 1, 13_000), new Renglon(filtro.getId(), 1, 13_000)),
                null, efectivo(26_000, null))))
                .hasMessageContaining("dos veces");
        assertThat(variantes.ordenDeBloqueo).isEmpty();
    }

    @Test
    @DisplayName("SPEC 0006: cobrar pide el turno bloqueado para mover plata, así el cierre no se calcula en medio")
    void pideElTurnoBloqueado() {
        cobrarVenta.ejecutar(comando(filtroYPastillas(), null, efectivo(38_000, null)));

        assertThat(turnos.vecesBloqueadoParaMover).isEqualTo(1);
    }
}
