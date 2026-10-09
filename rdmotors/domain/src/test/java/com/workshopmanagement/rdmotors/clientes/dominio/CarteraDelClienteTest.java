package com.workshopmanagement.rdmotors.clientes.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * Cómo se mueve cada peso de la cartera de un cliente (spec 0008, decisión 1, RF-012, RF-015, RF-017, RF-027, RF-028
 * y decisión 6). El caso del spec: Juan debe la venta 41 ($50.000) y la 57 ($30.000).
 */
class CarteraDelClienteTest {

    private static final Instant AHORA = Instant.parse("2026-09-21T15:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 9, 21);
    private final UUID cajero = UUID.randomUUID();
    private final UUID turno = UUID.randomUUID();
    private final Cliente juan = Cliente.nuevo(new DatosCliente("Juan Pérez", "12345678", "3001234567", null, null),
            cajero, AHORA);
    private final List<Deuda> deudas = new ArrayList<>();
    private final List<Abono> abonos = new ArrayList<>();
    private long siguienteAbono = 1;
    private Instant reloj = AHORA;

    private Instant luego() {
        reloj = reloj.plusSeconds(60);
        return reloj;
    }

    private CarteraDelCliente cartera() {
        return new CarteraDelCliente(juan, deudas, abonos);
    }

    private Deuda fiar(long numero, LocalDate dia, long monto) {
        CarteraDelCliente cartera = cartera();
        Deuda deuda = Deuda.porVenta(juan.getId(), UUID.randomUUID(), numero, dia, Dinero.de(monto), cajero, luego());
        cartera.registrarDeuda(deuda, reloj);
        deudas.add(deuda);
        return deuda;
    }

    private Abono abonar(long monto, UUID primeroA) {
        return abonarPrimeroA(monto, primeroA == null ? List.of() : List.of(primeroA));
    }

    /** Pagando primero lo escogido, en ese orden (spec 0016). */
    private Abono abonarPrimeroA(long monto, List<UUID> primero) {
        CarteraDelCliente cartera = cartera();
        Abono abono = Abono.recibir(siguienteAbono++, juan.getId(), Dinero.de(monto), FormaPago.EFECTIVO, null, null,
                turno, cajero, UUID.randomUUID(), luego());
        cartera.abonar(abono, primero, reloj);
        abonos.add(abono);
        return abono;
    }

    private static Dinero pesos(long monto) {
        return Dinero.de(monto);
    }

    @Test
    @DisplayName("lo que debe es lo pendiente de sus ventas, y desde cuándo es la fecha de la más vieja pendiente")
    void loQueDebe() {
        fiar(41, LocalDate.of(2026, 9, 12), 50_000);
        Deuda la57 = fiar(57, LocalDate.of(2026, 9, 18), 30_000);

        assertThat(cartera().debe()).isEqualTo(pesos(80_000));
        assertThat(cartera().desdeCuando()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(la57.getDebeDespues()).as("lo que debía justo después de la 57").isEqualTo(pesos(80_000));
        assertThat(cartera().aFavor()).isEqualTo(Dinero.CERO);
    }

    @Test
    @DisplayName("el ejemplo del spec: $60.000 dejan la 41 pagada y la 57 abonada con $20.000 pendientes")
    void aLoMasViejoPrimero() {
        Deuda la41 = fiar(41, LocalDate.of(2026, 9, 12), 50_000);
        Deuda la57 = fiar(57, LocalDate.of(2026, 9, 18), 30_000);

        Abono abono = abonar(60_000, null);

        assertThat(la41.estado()).isEqualTo(EstadoDeuda.PAGADA);
        assertThat(la57.estado()).isEqualTo(EstadoDeuda.ABONADA);
        assertThat(la57.pendiente()).isEqualTo(pesos(20_000));
        assertThat(cartera().debe()).isEqualTo(pesos(20_000));
        assertThat(cartera().desdeCuando()).isEqualTo(LocalDate.of(2026, 9, 18));
        // Las partes suman el abono al peso: $50.000 a la 41 y $10.000 a la 57.
        assertThat(abono.aplicacionesVigentes()).extracting(AplicacionAbono::getMonto)
                .containsExactly(pesos(50_000), pesos(10_000));
        assertThat(abono.aplicado()).isEqualTo(abono.getMonto());
        assertThat(abono.getDebeDespues()).isEqualTo(pesos(20_000));
    }

    @Test
    @DisplayName("el cliente dice a cuál va: la 57 primero, aunque la 41 sea más vieja; lo que sobra sigue a la más vieja")
    void primeroALaEscogida() {
        Deuda la41 = fiar(41, LocalDate.of(2026, 9, 12), 50_000);
        Deuda la57 = fiar(57, LocalDate.of(2026, 9, 18), 30_000);

        abonar(40_000, la57.getId());

        assertThat(la57.estado()).isEqualTo(EstadoDeuda.PAGADA);
        assertThat(la41.getAbonado()).isEqualTo(pesos(10_000));
        assertThat(cartera().debe()).isEqualTo(pesos(40_000));
    }

    @Test
    @DisplayName("el mismo día, se paga primero la que se fió antes")
    void mismoDiaPorOrdenDeRegistro() {
        Deuda primera = fiar(41, HOY, 10_000);
        Deuda segunda = fiar(42, HOY, 10_000);

        abonar(10_000, null);

        assertThat(primera.estado()).isEqualTo(EstadoDeuda.PAGADA);
        assertThat(segunda.estado()).isEqualTo(EstadoDeuda.PENDIENTE);
    }

    @Test
    @DisplayName("no se recibe más de lo que debe, ni a quien no debe nada, ni a una venta ya pagada")
    void loQueNoSeRecibe() {
        Deuda la41 = fiar(41, HOY, 50_000);

        assertThatThrownBy(() -> abonar(50_001, null))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessage("Juan Pérez debe $50.000: no se le puede recibir más");
        abonar(50_000, null);
        assertThatThrownBy(() -> abonar(1_000, null)).hasMessage("Juan Pérez no debe nada: no hay qué abonar");

        fiar(57, HOY, 30_000);
        assertThatThrownBy(() -> abonar(1_000, la41.getId())).hasMessage("La venta N.º 41 ya está pagada");
    }

    @Test
    @DisplayName("anular un abono devuelve lo que pagó: esas ventas vuelven a deberlo")
    void anularUnAbono() {
        Deuda la41 = fiar(41, LocalDate.of(2026, 9, 12), 50_000);
        Deuda la57 = fiar(57, LocalDate.of(2026, 9, 18), 30_000);
        Abono abono = abonar(60_000, null);

        cartera().anularAbono(abono, "Se registró dos veces", cajero, luego());

        assertThat(abono.estaAnulado()).isTrue();
        assertThat(abono.aplicacionesVigentes()).isEmpty();
        assertThat(abono.getAplicaciones()).hasSize(2).allSatisfy(a -> assertThat(a.getAnuladaEn()).isNotNull());
        assertThat(la41.estado()).isEqualTo(EstadoDeuda.PENDIENTE);
        assertThat(la57.estado()).isEqualTo(EstadoDeuda.PENDIENTE);
        assertThat(cartera().debe()).isEqualTo(pesos(80_000));
        assertThatThrownBy(() -> cartera().anularAbono(abono, "otra vez", cajero, reloj))
                .hasMessage("Este abono ya fue anulado");
    }

    @Test
    @DisplayName("anular una venta fiada que ya tenía abonos: lo abonado pasa a la otra que se debe")
    void anularVentaConAbonosPasaALaOtra() {
        Deuda la41 = fiar(41, LocalDate.of(2026, 9, 12), 50_000);
        Deuda la57 = fiar(57, LocalDate.of(2026, 9, 18), 30_000);
        Abono abono = abonar(30_000, null);               // todo a la 41

        cartera().anularDeuda(la41, cajero, luego());

        assertThat(la41.estado()).isEqualTo(EstadoDeuda.ANULADA);
        assertThat(la41.getAbonado()).isEqualTo(Dinero.CERO);
        assertThat(la57.estado()).isEqualTo(EstadoDeuda.PAGADA);
        assertThat(cartera().debe()).isEqualTo(Dinero.CERO);
        assertThat(cartera().aFavor()).isEqualTo(Dinero.CERO);
        // La aplicación a la 41 quedó anulada, y la de la 57 es nueva: la historia no se borra.
        assertThat(abono.getAplicaciones()).hasSize(2);
        assertThat(abono.aplicacionesVigentes()).singleElement()
                .satisfies(a -> assertThat(a.getDeudaId()).isEqualTo(la57.getId()));
    }

    @Test
    @DisplayName("si no hay otra que pagar, lo abonado queda a favor, y el próximo fiado lo usa (decisión 6)")
    void aFavorParaLaProxima() {
        Deuda la41 = fiar(41, HOY, 50_000);
        abonar(30_000, null);

        cartera().anularDeuda(la41, cajero, luego());

        assertThat(cartera().debe()).isEqualTo(Dinero.CERO);
        assertThat(cartera().aFavor()).isEqualTo(pesos(30_000));

        Deuda la60 = fiar(60, HOY, 45_000);

        assertThat(la60.getAbonado()).isEqualTo(pesos(30_000));
        assertThat(cartera().debe()).isEqualTo(pesos(15_000));
        assertThat(cartera().aFavor()).isEqualTo(Dinero.CERO);
        assertThat(la60.getDebeDespues()).as("el comprobante dice lo que queda debiendo").isEqualTo(pesos(15_000));
    }

    @Test
    @DisplayName("el saldo del cuaderno es lo más viejo, se paga primero y se carga una sola vez")
    void saldoDelCuaderno() {
        Deuda la41 = fiar(41, LocalDate.of(2026, 9, 12), 50_000);
        CarteraDelCliente cartera = cartera();
        Deuda cuaderno = Deuda.delCuaderno(juan.getId(), LocalDate.of(2026, 7, 1), pesos(120_000),
                "Lo que debía en el cuaderno", cajero, luego(), HOY);
        cartera.registrarDeuda(cuaderno, reloj);
        deudas.add(cuaderno);

        abonar(100_000, null);

        assertThat(cuaderno.getAbonado()).isEqualTo(pesos(100_000));
        assertThat(la41.getAbonado()).isEqualTo(Dinero.CERO);
        assertThat(cartera().desdeCuando()).isEqualTo(LocalDate.of(2026, 7, 1));

        Deuda otro = Deuda.delCuaderno(juan.getId(), HOY, pesos(1_000), "otra vez", cajero, luego(), HOY);
        assertThatThrownBy(() -> cartera().registrarDeuda(otro, reloj))
                .hasMessage("A Juan Pérez ya se le cargó el saldo del cuaderno");
        assertThatThrownBy(() -> Deuda.delCuaderno(juan.getId(), HOY.plusDays(1), pesos(1_000), "del futuro", cajero,
                AHORA, HOY))
                .hasMessage("La fecha del cuaderno no puede ser después de hoy");
    }

    @Test
    @DisplayName("un abono en efectivo sin turno no existe; la referencia es solo de una transferencia")
    void reglasDelAbono() {
        assertThatThrownBy(() -> Abono.recibir(1, juan.getId(), pesos(10_000), FormaPago.EFECTIVO, null, null, null,
                cajero, UUID.randomUUID(), AHORA))
                .hasMessageContaining("el efectivo no tiene a qué cajón entrar");
        assertThatThrownBy(() -> Abono.recibir(1, juan.getId(), pesos(10_000), FormaPago.EFECTIVO, "123", null, turno,
                cajero, UUID.randomUUID(), AHORA))
                .hasMessage("La referencia solo aplica a una transferencia");

        Abono porTransferencia = Abono.recibir(1, juan.getId(), pesos(10_000), FormaPago.TRANSFERENCIA, " 7788 ",
                null, null, cajero, UUID.randomUUID(), AHORA);
        assertThat(porTransferencia.getReferencia()).isEqualTo("7788");
        assertThat(porTransferencia.entraAlCajon()).isFalse();
    }

    @Test
    @DisplayName("a una deuda no se le aplica más de lo que le falta, ni siquiera pidiéndoselo directo")
    void nuncaMasDeLoQueFalta() {
        Deuda la41 = fiar(41, HOY, 50_000);
        Abono abono = Abono.recibir(9, juan.getId(), pesos(60_000), FormaPago.EFECTIVO, null, null, turno, cajero,
                UUID.randomUUID(), luego());

        assertThatThrownBy(() -> abono.aplicarA(la41, pesos(60_000), reloj))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessage("La venta N.º 41 debe $50.000: no se le pueden aplicar $60.000");
        assertThat(la41.getAbonado()).isEqualTo(Dinero.CERO);
        assertThat(abono.aplicado()).isEqualTo(Dinero.CERO);
    }

    @Test
    @DisplayName("una deuda o un abono de otro cliente no entran en su cartera")
    void soloLoDelCliente() {
        Cliente otro = Cliente.nuevo(new DatosCliente("Ana", null, null, null, null), cajero, AHORA);
        Deuda deOtro = Deuda.porVenta(otro.getId(), UUID.randomUUID(), 9, HOY, pesos(1_000), cajero, AHORA);

        assertThatThrownBy(() -> new CarteraDelCliente(juan, List.of(deOtro), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cartera().registrarDeuda(deOtro, AHORA)).isInstanceOf(IllegalArgumentException.class);
    }

    // ── Mixto (spec 0017) ───────────────────────────────────────────────────

    /** Un abono mixto: dos partes del mismo pago, primero la de efectivo y después la de transferencia. */
    private List<Abono> abonarMixto(long efectivo, long transferencia, List<UUID> primero) {
        CarteraDelCliente cartera = cartera();
        UUID pago = UUID.randomUUID();
        Instant cuando = luego();
        Abono enEfectivo = Abono.recibir(siguienteAbono++, juan.getId(), Dinero.de(efectivo), FormaPago.EFECTIVO, null,
                null, turno, cajero, UUID.randomUUID(), cuando, pago);
        Abono porTransferencia = Abono.recibir(siguienteAbono++, juan.getId(), Dinero.de(transferencia),
                FormaPago.TRANSFERENCIA, null, null, turno, cajero, UUID.randomUUID(), cuando, pago);
        cartera.abonar(List.of(enEfectivo, porTransferencia), primero, cuando);
        abonos.add(enEfectivo);
        abonos.add(porTransferencia);
        return List.of(enEfectivo, porTransferencia);
    }

    @Test
    @DisplayName("MIXTO: $20.000 en efectivo y $30.000 por transferencia pagan la 41 como un solo pago, con el mismo debe después")
    void mixtoPagaComoUno() {
        Deuda la41 = fiar(41, LocalDate.of(2026, 9, 12), 50_000);
        Deuda la57 = fiar(57, LocalDate.of(2026, 9, 18), 30_000);

        List<Abono> partes = abonarMixto(20_000, 30_000, List.of(la41.getId()));

        assertThat(la41.estado()).isEqualTo(EstadoDeuda.PAGADA);
        assertThat(la57.pendiente()).isEqualTo(pesos(30_000));
        assertThat(partes.get(0).aplicacionesVigentes()).extracting(AplicacionAbono::getDeudaId, AplicacionAbono::getMonto)
                .containsExactly(tuple(la41.getId(), pesos(20_000)));
        assertThat(partes.get(1).aplicacionesVigentes()).extracting(AplicacionAbono::getDeudaId, AplicacionAbono::getMonto)
                .containsExactly(tuple(la41.getId(), pesos(30_000)));
        assertThat(partes).extracting(Abono::getDebeDespues).containsOnly(pesos(30_000));
        assertThat(partes).allMatch(Abono::esParteDeUnPago);
    }

    @Test
    @DisplayName("MIXTO marcando dos: el efectivo paga el primero marcado y la transferencia sigue donde quedó (RF-002)")
    void mixtoMarcandoDos() {
        Deuda guaya = fiar(18, LocalDate.of(2026, 9, 12), 26_120);
        Deuda motul = fiar(19, LocalDate.of(2026, 9, 18), 62_880);

        List<Abono> partes = abonarMixto(30_000, 59_000, List.of(motul.getId(), guaya.getId()));

        assertThat(partes.get(0).aplicacionesVigentes()).extracting(AplicacionAbono::getDeudaId, AplicacionAbono::getMonto)
                .containsExactly(tuple(motul.getId(), pesos(30_000)));
        assertThat(partes.get(1).aplicacionesVigentes()).extracting(AplicacionAbono::getDeudaId, AplicacionAbono::getMonto)
                .containsExactly(tuple(motul.getId(), pesos(32_880)), tuple(guaya.getId(), pesos(26_120)));
        assertThat(motul.estado()).isEqualTo(EstadoDeuda.PAGADA);
        assertThat(guaya.estado()).isEqualTo(EstadoDeuda.PAGADA);
        assertThat(cartera().debe()).isEqualTo(Dinero.CERO);
    }

    @Test
    @DisplayName("MIXTO sin marcar: a lo más viejo, y el efectivo primero")
    void mixtoSinMarcar() {
        Deuda la41 = fiar(41, LocalDate.of(2026, 9, 12), 50_000);
        Deuda la57 = fiar(57, LocalDate.of(2026, 9, 18), 30_000);

        List<Abono> partes = abonarMixto(60_000, 10_000, List.of());

        assertThat(partes.get(0).aplicacionesVigentes()).extracting(AplicacionAbono::getDeudaId, AplicacionAbono::getMonto)
                .containsExactly(tuple(la41.getId(), pesos(50_000)), tuple(la57.getId(), pesos(10_000)));
        assertThat(partes.get(1).aplicacionesVigentes()).extracting(AplicacionAbono::getDeudaId, AplicacionAbono::getMonto)
                .containsExactly(tuple(la57.getId(), pesos(10_000)));
        assertThat(cartera().debe()).isEqualTo(pesos(10_000));
    }

    @Test
    @DisplayName("MIXTO: lo que debe se compara con el total de las dos partes")
    void mixtoNoMasDeLoQueDebe() {
        Deuda la41 = fiar(41, LocalDate.of(2026, 9, 12), 50_000);

        assertThatThrownBy(() -> abonarMixto(20_000, 40_000, List.of()))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("no se le puede recibir más");
        assertThat(la41.pendiente()).isEqualTo(pesos(50_000));
    }

    @Test
    @DisplayName("MIXTO: el efectivo es una parte, menos que el total; la llave de la transferencia es siempre la misma")
    void partesDelMixto() {
        assertThat(Abono.partesDelMixto(pesos(50_000), pesos(20_000)))
                .isEqualTo(new Abono.PartesDelMixto(pesos(20_000), pesos(30_000)));
        for (Dinero efectivo : java.util.Arrays.asList(null, Dinero.CERO, pesos(50_000), pesos(60_000))) {
            assertThatThrownBy(() -> Abono.partesDelMixto(pesos(50_000), efectivo))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessage("En mixto, el efectivo es una parte: menos que el total");
        }
        UUID llave = UUID.randomUUID();
        assertThat(Abono.llaveDeLaTransferencia(llave)).isEqualTo(Abono.llaveDeLaTransferencia(llave))
                .isNotEqualTo(llave);
    }

    @Test
    @DisplayName("ANULAR UN MIXTO: las dos partes juntas; lo que había a favor va a lo más viejo, no a lo que liberó la primera")
    void anularMixtoJuntas() {
        Deuda vieja = fiar(41, LocalDate.of(2026, 9, 12), 20_000);
        Deuda nueva = fiar(57, LocalDate.of(2026, 9, 18), 30_000);
        Deuda otra = fiar(60, LocalDate.of(2026, 9, 20), 10_000);
        abonar(10_000, otra.getId());
        // El efectivo paga la 57 (marcada primero) y la transferencia la 41.
        List<Abono> partes = abonarMixto(30_000, 20_000, List.of(nueva.getId(), vieja.getId()));
        // Se anula la 60: lo que se le había abonado queda a favor, porque no hay nada más que deber.
        cartera().anularDeuda(otra, cajero, luego());
        assertThat(cartera().aFavor()).isEqualTo(pesos(10_000));

        cartera().anularAbonos(partes, "Se registró mal", cajero, luego());

        assertThat(partes).allMatch(Abono::estaAnulado);
        assertThat(vieja.pendiente()).as("lo a favor, a lo más viejo").isEqualTo(pesos(10_000));
        assertThat(nueva.pendiente()).isEqualTo(pesos(30_000));
        assertThat(cartera().debe()).isEqualTo(pesos(40_000));
        assertThat(cartera().aFavor()).isEqualTo(Dinero.CERO);
    }
}
