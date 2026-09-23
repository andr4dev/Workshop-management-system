package com.workshopmanagement.rdmotors.ventas.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
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
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.inventario.dominio.MovimientoKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.Producto;
import com.workshopmanagement.rdmotors.inventario.dominio.TipoMovimiento;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ComandoCobrarVenta.Pago;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ComandoCobrarVenta.Renglon;
import com.workshopmanagement.rdmotors.ventas.dominio.EstadoVenta;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;

/**
 * Anular una venta (spec 0003, fase 5). La que más protege es {@link #stockVuelveSinTocarElPromedio()}:
 * el stock vuelve exacto y el costo promedio no se mueve.
 */
class AnularVentaTest {

    private Falsos.VentasEnMemoria ventas;
    private Falsos.TurnosEnMemoria turnos;
    private Falsos.VariantesEnMemoria variantes;
    private Falsos.KardexEnMemoria kardex;
    private Falsos.AuditoriaEnMemoria auditoria;
    private Falsos.RelojFijo reloj;
    private AnularVenta anularVenta;
    private CobrarVenta cobrarVenta;

    private final Actor cajero = ActoresDePrueba.cajero();
    private final Actor administrador = ActoresDePrueba.administrador();
    private Variante filtro;
    private Variante pastillas;


    @BeforeEach
    void preparar() {
        ventas = new Falsos.VentasEnMemoria();
        turnos = new Falsos.TurnosEnMemoria();
        variantes = new Falsos.VariantesEnMemoria();
        kardex = new Falsos.KardexEnMemoria();
        auditoria = new Falsos.AuditoriaEnMemoria();
        reloj = new Falsos.RelojFijo("2026-09-14T15:00:00Z");
        var fiar = new FiarVenta(new Falsos.ClientesEnMemoria(), new Falsos.DeudasEnMemoria(), new Falsos.AbonosEnMemoria());
        cobrarVenta = new CobrarVenta(ventas, turnos, variantes, kardex, auditoria, fiar, reloj);
        anularVenta = new AnularVenta(ventas, turnos, variantes, kardex, auditoria, fiar, reloj);

        turnos.guardar(TurnoCaja.abrir(Dinero.de(100_000), cajero.id(), reloj.ahora()));

        filtro = variantes.sembrar(Variante.nueva(Producto.nuevo("FILTRO ACEITE", Categoria.nueva("PRUEBAS", 99), null),
                "352B59K", "INOKI", Dinero.de(13_000), 2));
        filtro.reponerPorCompra(10, new BigDecimal("8000"));
        pastillas = variantes.sembrar(Variante.nueva(Producto.nuevo("PASTILLAS FRENO", Categoria.nueva("PRUEBAS", 99), null),
                "152RTX2B", "CBI", Dinero.de(12_000), 2));
        pastillas.reponerPorCompra(5, new BigDecimal("7500"));
    }

    /** 2 filtros + 1 pastilla = $38.000 en efectivo. */
    private Venta cobrarFiltroYPastillas() {
        return cobrarVenta.ejecutar(new ComandoCobrarVenta(UUID.randomUUID(),
                List.of(new Renglon(filtro.getId(), 2, 13_000), new Renglon(pastillas.getId(), 1, 12_000)),
                null, List.of(new Pago(FormaPago.EFECTIVO, 38_000, 50_000L)), cajero)).venta();
    }

    @Test
    @DisplayName("el stock de cada repuesto vuelve exacto y el costo promedio NO cambia")
    void stockVuelveSinTocarElPromedio() {
        Venta venta = cobrarFiltroYPastillas();
        assertThat(filtro.getStock()).isEqualTo(8);

        Venta anulada = anularVenta.ejecutar(venta.getId(), "Cliente se arrepintió", administrador);

        assertThat(anulada.getEstado()).isEqualTo(EstadoVenta.ANULADA);
        assertThat(filtro.getStock()).isEqualTo(10);
        assertThat(pastillas.getStock()).isEqualTo(5);
        assertThat(filtro.getCostoPromedio()).isEqualByComparingTo("8000");
        assertThat(pastillas.getCostoPromedio()).isEqualByComparingTo("7500");
        assertThat(ventas.buscar(venta.getId()).orElseThrow().getEstado()).isEqualTo(EstadoVenta.ANULADA);
    }

    @Test
    @DisplayName("cada salida queda con su REVERSION en el kardex: apunta a ella, con su mismo costo y el motivo")
    void reversionEnlazadaASuSalida() {
        Venta venta = cobrarFiltroYPastillas();
        MovimientoKardex salidaFiltro = kardex.movimientos.stream()
                .filter(m -> m.getVariante() == filtro).findFirst().orElseThrow();

        anularVenta.ejecutar(venta.getId(), "Cliente se arrepintió", administrador);

        List<MovimientoKardex> reversiones = kardex.movimientos.stream()
                .filter(m -> m.getTipo() == TipoMovimiento.REVERSION).toList();
        assertThat(reversiones).hasSize(2);
        MovimientoKardex delFiltro = reversiones.stream()
                .filter(m -> m.getVariante() == filtro).findFirst().orElseThrow();
        assertThat(delFiltro.getMovimientoRevertidoId()).isEqualTo(salidaFiltro.getId());
        assertThat(delFiltro.getCantidadDelta()).isEqualTo(2);
        assertThat(delFiltro.getCostoTotal()).isEqualTo(salidaFiltro.getCostoTotal());
        assertThat(delFiltro.getCostoUnitario()).isEqualByComparingTo(salidaFiltro.getCostoUnitario());
        assertThat(delFiltro.getSaldoDespues()).isEqualTo(10);
        assertThat(delFiltro.getCostoPromedioDespues()).isEqualByComparingTo("8000");
        assertThat(delFiltro.getOrigenId()).isEqualTo(venta.getId());
        assertThat(delFiltro.getMotivo()).isEqualTo("Cliente se arrepintió");
        assertThat(delFiltro.getRegistradoPorId()).isEqualTo(administrador.id());
    }

    @Test
    @DisplayName("queda el evento ANULAR_VENTA con quién, el motivo, y la venta antes y después")
    void eventoDeAuditoria() {
        Venta venta = cobrarFiltroYPastillas();

        anularVenta.ejecutar(venta.getId(), "Se cobró dos veces", administrador);

        EventoAuditoria evento = auditoria.eventos.stream()
                .filter(e -> e.accion() == AccionAuditada.ANULAR_VENTA).findFirst().orElseThrow();
        assertThat(evento.usuarioId()).isEqualTo(administrador.id());
        assertThat(evento.entidadId()).isEqualTo(venta.getId());
        assertThat(evento.motivo()).isEqualTo("Se cobró dos veces");
        assertThat(evento.antes()).containsEntry("estado", "COBRADA");
        assertThat(evento.despues()).containsEntry("estado", "ANULADA");
    }

    @Test
    @DisplayName("RF-026: una venta de un turno anterior se anula en el turno de hoy, sin tocar el viejo")
    void turnoAnterior() {
        Venta venta = cobrarFiltroYPastillas();
        UUID turnoDeLaVenta = venta.getTurnoId();
        // Se cierra el turno a mano (el cierre con arqueo es de la rebanada 3) y se abre otro.
        turnos.datos.clear();
        TurnoCaja hoy = turnos.guardar(TurnoCaja.abrir(Dinero.de(50_000), cajero.id(), reloj.ahora()));

        Venta anulada = anularVenta.ejecutar(venta.getId(), "Devolución al día siguiente", administrador);

        assertThat(anulada.getTurnoId()).isEqualTo(turnoDeLaVenta);
        assertThat(anulada.getAnuladaEnTurnoId()).isEqualTo(hoy.getId()).isNotEqualTo(turnoDeLaVenta);
    }

    @Test
    @DisplayName("bloquea los repuestos en orden de id, el mismo de cobrar: no se traba con un cobro")
    void ordenDeBloqueo() {
        // Los renglones a propósito al revés del orden de id: si se bloquearan en el orden de la venta,
        // la prueba lo vería siempre, no la mitad de las veces.
        Variante mayor = filtro.getId().compareTo(pastillas.getId()) > 0 ? filtro : pastillas;
        Variante menor = mayor == filtro ? pastillas : filtro;
        Venta venta = cobrarVenta.ejecutar(new ComandoCobrarVenta(UUID.randomUUID(),
                List.of(new Renglon(mayor.getId(), 1, mayor.getPrecio().valor().longValueExact()),
                        new Renglon(menor.getId(), 1, menor.getPrecio().valor().longValueExact())),
                null, List.of(new Pago(FormaPago.TRANSFERENCIA, 25_000, null)), cajero)).venta();
        variantes.ordenDeBloqueo.clear();

        anularVenta.ejecutar(venta.getId(), "prueba", administrador);

        assertThat(variantes.ordenDeBloqueo).containsExactly(menor.getId(), mayor.getId());
    }

    @Test
    @DisplayName("si una compra movió el promedio antes de anular, la reversión lleva el costo con que SALIÓ y el promedio sigue sin moverse")
    void reversionConElCostoDeLaSalida() {
        Venta venta = cobrarFiltroYPastillas();                    // salen 2 filtros a $8.000
        filtro.reponerPorCompra(12, new BigDecimal("11000"));       // 8 a $8.000 + 12 a $11.000 → $9.800
        BigDecimal promedioDeHoy = filtro.getCostoPromedio();

        anularVenta.ejecutar(venta.getId(), "devolución", administrador);

        MovimientoKardex reversion = kardex.movimientos.stream()
                .filter(m -> m.getTipo() == TipoMovimiento.REVERSION && m.getVariante() == filtro)
                .findFirst().orElseThrow();
        // Con el costo de la salida, la venta y su reversión se cancelan al peso en el margen.
        assertThat(reversion.getCostoTotal()).isEqualTo(Dinero.de(16_000));
        assertThat(filtro.getCostoPromedio()).isEqualByComparingTo(promedioDeHoy);
        assertThat(filtro.getStock()).isEqualTo(22);
    }

    @Test
    @DisplayName("una anulada no se vuelve a anular: el stock no vuelve dos veces")
    void yaAnulada() {
        Venta venta = cobrarFiltroYPastillas();
        anularVenta.ejecutar(venta.getId(), "primera", administrador);
        int movimientos = kardex.movimientos.size();

        assertThatThrownBy(() -> anularVenta.ejecutar(venta.getId(), "segunda", administrador))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessage("Esta venta ya fue anulada");

        assertThat(filtro.getStock()).isEqualTo(10);
        assertThat(kardex.movimientos).hasSize(movimientos);
    }

    @Test
    @DisplayName("sin motivo, sin turno abierto o si la venta no existe, no se anula nada")
    void sinAnularNada() {
        Venta venta = cobrarFiltroYPastillas();
        int movimientos = kardex.movimientos.size();

        assertThatThrownBy(() -> anularVenta.ejecutar(venta.getId(), "  ", administrador))
                .isInstanceOf(ReglaDeNegocioException.class);
        assertThatThrownBy(() -> anularVenta.ejecutar(UUID.randomUUID(), "no existe", administrador))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessage("La venta no existe");

        turnos.datos.clear();
        assertThatThrownBy(() -> anularVenta.ejecutar(venta.getId(), "sin turno", administrador))
                .isInstanceOf(SinTurnoAbiertoException.class);

        assertThat(filtro.getStock()).isEqualTo(8);
        assertThat(kardex.movimientos).hasSize(movimientos);
        assertThat(ventas.buscar(venta.getId()).orElseThrow().getEstado()).isEqualTo(EstadoVenta.COBRADA);
        assertThat(auditoria.eventos).noneMatch(e -> e.accion() == AccionAuditada.ANULAR_VENTA);
    }

    @Test
    @DisplayName("la reversión de una venta solo deshace un movimiento VENTA del mismo repuesto")
    void reversionSoloDeVentas() {
        MovimientoKardex compra = MovimientoKardex.porCompra(filtro, 1, new BigDecimal("8000"), Dinero.de(8_000),
                UUID.randomUUID(), cajero.id(), reloj.ahora());
        MovimientoKardex ventaDePastillas = MovimientoKardex.porVenta(pastillas, 1, UUID.randomUUID(), cajero.id(), reloj.ahora());

        assertThatThrownBy(() -> MovimientoKardex.porReversionDeVenta(filtro, compra, UUID.randomUUID(), "x", cajero.id(), reloj.ahora()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MovimientoKardex.porReversionDeVenta(filtro, ventaDePastillas, UUID.randomUUID(), "x", cajero.id(), reloj.ahora()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("SPEC 0006: anular pide el turno bloqueado para mover plata, así el cierre no se calcula en medio")
    void pideElTurnoBloqueado() {
        Venta venta = cobrarFiltroYPastillas();
        int antes = turnos.vecesBloqueadoParaMover;

        anularVenta.ejecutar(venta.getId(), "Cliente se arrepintió", administrador);

        assertThat(turnos.vecesBloqueadoParaMover).isEqualTo(antes + 1);
    }
}
