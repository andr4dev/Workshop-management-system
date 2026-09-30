package com.workshopmanagement.rdmotors.caja.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.clientes.dominio.Abono;
import com.workshopmanagement.rdmotors.clientes.dominio.CarteraDelCliente;
import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.clientes.dominio.Deuda;
import com.workshopmanagement.rdmotors.clientes.dominio.EstadoDeuda;
import com.workshopmanagement.rdmotors.clientes.dominio.ResumenDeCliente;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ComandoCobrarVenta;
import com.workshopmanagement.rdmotors.ventas.dominio.ModoDescuento;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;

/**
 * Fiar y abonar por producto, de punta a punta en el dominio (spec 0016): la venta deja una deuda por producto, el
 * cajero escoge qué paga al llevárselo, y el abono paga los productos que se marquen.
 */
class FiadoPorProductoTest {

    private final EscenarioCaja tienda = new EscenarioCaja();
    private Cliente juan;
    private Variante motul;
    private Variante filtro;

    @BeforeEach
    void preparar() {
        tienda.abrir(100_000);
        juan = tienda.cliente("Juan Pérez", "1234567");
        motul = tienda.repuesto(65_000);
        filtro = tienda.repuesto(11_000);
    }

    /** MOTUL + filtro fiados a Juan; paga {@code pagaAhora} en efectivo, cubriendo primero lo marcado. */
    private Venta fiarMotulYFiltro(long pagaAhora, ComandoCobrarVenta.ComandoDescuento descuento, UUID... marcados) {
        long total = 76_000 - (descuento == null ? 0 : descuento.valor().longValueExact());
        return tienda.cobrarVenta.ejecutar(new ComandoCobrarVenta(UUID.randomUUID(),
                List.of(new ComandoCobrarVenta.Renglon(motul.getId(), 1, 65_000),
                        new ComandoCobrarVenta.Renglon(filtro.getId(), 1, 11_000)),
                descuento, pagaAhora == 0 ? List.of() : List.of(new ComandoCobrarVenta.Pago(FormaPago.EFECTIVO, pagaAhora, null)),
                juan.getId(), total - pagaAhora, tienda.cajero, List.of(marcados))).venta();
    }

    private List<Deuda> deudasDe(Venta venta) {
        return tienda.deudas.deLaVenta(venta.getId()).stream()
                .sorted(CarteraDelCliente.ORDEN_DE_PAGO).toList();
    }

    private Abono abonar(long monto, UUID... primero) {
        return tienda.registrarAbono.ejecutar(UUID.randomUUID(), juan.getId(), Dinero.de(monto), FormaPago.EFECTIVO,
                null, null, List.of(primero), tienda.cajero);
    }

    private Deuda recargar(Deuda d) {
        return tienda.deudas.datos.get(d.getId());
    }

    @Test
    @DisplayName("fiar MOTUL + filtro: una deuda por producto, con su precio, en el orden de la venta y el mismo 'debe después'")
    void unaDeudaPorProducto() {
        Venta venta = fiarMotulYFiltro(0, null);

        List<Deuda> deudas = deudasDe(venta);
        assertThat(deudas).extracting(Deuda::getMonto).containsExactly(Dinero.de(65_000), Dinero.de(11_000));
        assertThat(deudas).extracting(Deuda::getPosicion).containsExactly(0, 1);
        assertThat(deudas).extracting(Deuda::getDescripcion)
                .containsExactly(motul.getProducto().getNombre(), filtro.getProducto().getNombre());
        assertThat(deudas).extracting(Deuda::getLineaVentaId)
                .containsExactly(venta.getLineas().get(0).getId(), venta.getLineas().get(1).getId());
        assertThat(deudas).extracting(Deuda::getDebeDespues).containsOnly(Dinero.de(76_000));
        assertThat(deudas.getFirst().nombre())
                .isEqualTo("el repuesto " + motul.getProducto().getNombre() + " de la venta N.º " + venta.getNumero());
    }

    @Test
    @DisplayName("paga el filtro al llevárselo, marcado: el MOTUL debe $65.000 y el filtro no queda como deuda")
    void pagaElFiltroAlLlevarselo() {
        Venta venta = fiarMotulYFiltro(11_000, null, filtro.getId());

        assertThat(deudasDe(venta)).singleElement().satisfies(d -> {
            assertThat(d.getLineaVentaId()).isEqualTo(venta.getLineas().getFirst().getId());
            assertThat(d.getMonto()).isEqualTo(Dinero.de(65_000));
        });
    }

    @Test
    @DisplayName("paga $20.000 sin marcar: cubre en el orden de la venta (el MOTUL queda en $45.000, el filtro entero)")
    void pagaSinMarcar() {
        Venta venta = fiarMotulYFiltro(20_000, null);

        assertThat(deudasDe(venta)).extracting(Deuda::getMonto).containsExactly(Dinero.de(45_000), Dinero.de(11_000));
    }

    @Test
    @DisplayName("con $3.000 de descuento: lo que vale cada uno baja en proporción y las deudas suman lo fiado")
    void conDescuento() {
        Venta venta = fiarMotulYFiltro(0,
                new ComandoCobrarVenta.ComandoDescuento(ModoDescuento.MONTO, java.math.BigDecimal.valueOf(3_000), "Cliente frecuente"));

        assertThat(deudasDe(venta)).extracting(Deuda::getMonto).containsExactly(Dinero.de(62_434), Dinero.de(10_566));
        assertThat(venta.getFiado()).isEqualTo(Dinero.de(73_000));
    }

    @Test
    @DisplayName("marcar sin fiar, o marcar algo que no está en la venta: no se cobra y no se mueve nada")
    void marcarLoQueNoCabe() {
        assertThatThrownBy(() -> new ComandoCobrarVenta(UUID.randomUUID(),
                List.of(new ComandoCobrarVenta.Renglon(motul.getId(), 1, 65_000)), null,
                List.of(new ComandoCobrarVenta.Pago(FormaPago.EFECTIVO, 65_000, null)), null, 0, tienda.cajero,
                List.of(motul.getId())))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessage("Solo se escoge qué se paga ahora cuando se fía");
        assertThatThrownBy(() -> fiarMotulYFiltro(11_000, null, UUID.randomUUID()))
                .hasMessage("Lo que se paga ahora tiene que ser de esta venta");
        assertThat(tienda.deudas.datos).isEmpty();
        assertThat(motul.getStock()).isEqualTo(100);
    }

    @Test
    @DisplayName("abonar marcando el filtro lo paga; el MOTUL sigue pendiente")
    void abonarMarcando() {
        List<Deuda> deudas = deudasDe(fiarMotulYFiltro(0, null));
        Deuda deMotul = deudas.get(0);
        Deuda deFiltro = deudas.get(1);

        abonar(11_000, deFiltro.getId());

        assertThat(recargar(deFiltro).estado()).isEqualTo(EstadoDeuda.PAGADA);
        assertThat(recargar(deMotul).estado()).isEqualTo(EstadoDeuda.PENDIENTE);
    }

    @Test
    @DisplayName("abonar menos de lo marcado deja el último a medias; más, lo que sobra va a lo más viejo")
    void menosYMasDeLoMarcado() {
        Venta vieja = tienda.fiar(juan, 30_000, 0);
        List<Deuda> deudas = deudasDe(fiarMotulYFiltro(0, null));
        Deuda deMotul = deudas.get(0);
        Deuda deFiltro = deudas.get(1);

        abonar(20_000, deFiltro.getId(), deMotul.getId());
        assertThat(recargar(deFiltro).estado()).isEqualTo(EstadoDeuda.PAGADA);
        assertThat(recargar(deMotul).getAbonado()).isEqualTo(Dinero.de(9_000));
        assertThat(recargar(deudasDe(vieja).getFirst()).getAbonado()).as("la vieja no se tocó").isEqualTo(Dinero.CERO);

        abonar(66_000, deMotul.getId());
        assertThat(recargar(deMotul).estado()).isEqualTo(EstadoDeuda.PAGADA);
        assertThat(recargar(deudasDe(vieja).getFirst()).getAbonado()).as("lo que sobró").isEqualTo(Dinero.de(10_000));
    }

    @Test
    @DisplayName("sin marcar, a lo más viejo producto por producto; un producto pagado no se puede volver a marcar")
    void sinMarcarYYaPagado() {
        List<Deuda> deudas = deudasDe(fiarMotulYFiltro(0, null));

        abonar(70_000);
        assertThat(recargar(deudas.get(0)).estado()).isEqualTo(EstadoDeuda.PAGADA);
        assertThat(recargar(deudas.get(1)).getAbonado()).isEqualTo(Dinero.de(5_000));

        assertThatThrownBy(() -> abonar(1_000, deudas.get(0).getId()))
                .hasMessage("El repuesto " + motul.getProducto().getNombre() + " de la venta N.º "
                        + deudas.get(0).getNumeroVenta() + " ya está pagado");
        assertThatThrownBy(() -> abonar(1_000, UUID.randomUUID())).hasMessage("Eso no es una deuda de Juan Pérez");
    }

    @Test
    @DisplayName("ANULAR la venta anula sus dos productos, y lo abonado va a la otra venta sin pasar por el hermano")
    void anularLaVenta() {
        Venta venta = fiarMotulYFiltro(0, null);
        List<Deuda> deudas = deudasDe(venta);
        Venta nueva = tienda.fiar(juan, 30_000, 0);
        // Abonado al MOTUL, el primero: anulándolos de a uno, lo liberado del MOTUL caería en el filtro, su hermano.
        Abono abono = abonar(20_000, deudas.get(0).getId());

        tienda.anular(venta);

        assertThat(deudasDe(venta)).allMatch(Deuda::estaAnulada);
        assertThat(recargar(deudasDe(nueva).getFirst()).getAbonado()).as("lo del MOTUL pasó a la otra venta")
                .isEqualTo(Dinero.de(20_000));
        assertThat(tienda.abonos.datos.get(abono.getId()).getAplicaciones())
                .noneMatch(ap -> ap.getDeudaId().equals(deudas.get(1).getId()));
    }

    @Test
    @DisplayName("la lista de la Cartera cuenta la venta una vez, no sus dos productos")
    void laListaCuentaVentas() {
        fiarMotulYFiltro(0, null);

        ResumenDeCliente fila = ResumenDeCliente.de(new CarteraDelCliente(juan,
                tienda.deudas.delCliente(juan.getId()), tienda.abonos.delCliente(juan.getId())));
        assertThat(fila.pendientes()).isEqualTo(1);
        assertThat(fila.debe()).isEqualTo(Dinero.de(76_000));
    }
}
