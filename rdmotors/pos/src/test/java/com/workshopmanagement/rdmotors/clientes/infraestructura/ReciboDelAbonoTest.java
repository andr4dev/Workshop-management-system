package com.workshopmanagement.rdmotors.clientes.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.clientes.aplicacion.FichaCliente.AbonoDeLaFicha;
import com.workshopmanagement.rdmotors.clientes.aplicacion.FichaCliente.ParteAplicada;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;

/**
 * Lo aplicado en el recibo (spec 0017): un abono de una forma se reimprime como siempre, aplicación por aplicación; un
 * mixto suma lo de sus dos partes por producto.
 */
class ReciboDelAbonoTest {

    private static final Instant AHORA = Instant.parse("2026-10-09T19:00:00Z");
    private final UUID motul = UUID.randomUUID();
    private final UUID lubricante = UUID.randomUUID();

    private AbonoDeLaFicha abono(long numero, FormaPago forma, long monto, UUID pago, ParteAplicada... aplicaciones) {
        return new AbonoDeLaFicha(UUID.randomUUID(), numero, AHORA, Dinero.de(monto), forma, null, null, null, null,
                Dinero.CERO, null, null, null, Dinero.CERO, List.of(aplicaciones), pago);
    }

    private ParteAplicada a(UUID deuda, String nombre, long monto) {
        return new ParteAplicada(deuda, nombre, Dinero.de(monto), true);
    }

    @Test
    @DisplayName("un abono de antes con dos aplicaciones a la misma venta se reimprime igual: dos renglones, no uno")
    void abonoDeUnaFormaComoSiempre() {
        // El abono N.º 3 de producción: lo de la venta 8 se aplicó en dos veces al anularse la venta 6.
        AbonoDeLaFicha n3 = abono(3, FormaPago.EFECTIVO, 101_000, null,
                a(motul, "MOTUL 5100 15W50 (venta N.º 8)", 46_000), a(motul, "MOTUL 5100 15W50 (venta N.º 8)", 44_000),
                a(lubricante, "LUBRICA CADENA C4 MOTUL (venta N.º 9)", 11_000));

        assertThat(AbonoController.lineasDelRecibo(List.of(n3)))
                .extracting(AbonoController.RespuestaAplicacion::monto).containsExactly(46_000L, 44_000L, 11_000L);
    }

    @Test
    @DisplayName("un mixto suma lo de sus dos partes por producto: el MOTUL una vez, con lo del efectivo y la transferencia")
    void mixtoSumadoPorProducto() {
        UUID pago = UUID.randomUUID();
        AbonoDeLaFicha efectivo = abono(7, FormaPago.EFECTIVO, 20_000, pago,
                a(motul, "MOTUL", 9_000), a(lubricante, "LUBRICANTE", 11_000));
        AbonoDeLaFicha transferencia = abono(8, FormaPago.TRANSFERENCIA, 50_000, pago, a(motul, "MOTUL", 50_000));

        assertThat(AbonoController.lineasDelRecibo(List.of(efectivo, transferencia)))
                .extracting(p -> p.deuda() + " " + p.monto()).containsExactly("MOTUL 59000", "LUBRICANTE 11000");
    }
}
