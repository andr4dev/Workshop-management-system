package com.workshopmanagement.rdmotors.caja.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.caja.dominio.TurnoAjenoException;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.clientes.dominio.Abono;
import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.clientes.dominio.EstadoDeuda;
import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * Los abonos y el cajón (spec 0008, H4, RF-011 a RF-014 y RF-017): el efectivo entra al esperado, la transferencia no,
 * y lo que se firma al cerrar queda con su parte de abonos.
 */
class AbonosEnElCajonTest {

    private final EscenarioCaja tienda = new EscenarioCaja();

    private Cliente conDeuda(long fiado) {
        Cliente juan = tienda.cliente("Juan Pérez", "1234567");
        tienda.fiar(juan, fiado, 0);
        return juan;
    }

    @Test
    @DisplayName("el abono en efectivo sube lo que debería haber en el cajón y baja lo que debe el cliente")
    void elAbonoEnEfectivoEntraAlCajon() {
        tienda.abrir(100_000);
        Cliente juan = conDeuda(50_000);

        Abono abono = tienda.abonar(juan, 30_000);

        assertThat(abono.getNumero()).isEqualTo(1);
        assertThat(abono.getTurnoId()).isEqualTo(tienda.turnoAbierto().getId());
        assertThat(abono.getDebeDespues()).isEqualTo(Dinero.de(20_000));
        var arqueo = tienda.calcularArqueo.de(tienda.turnoAbierto());
        assertThat(arqueo.abonosEfectivo()).isEqualTo(Dinero.de(30_000));
        assertThat(arqueo.ventasFiado()).isEqualTo(Dinero.de(50_000));
        assertThat(arqueo.esperado()).isEqualTo(Dinero.de(130_000));
        assertThat(tienda.deudas.debeDe(java.util.List.of(juan.getId()))).containsEntry(juan.getId(), Dinero.de(20_000));
    }

    @Test
    @DisplayName("el abono por transferencia no entra al cajón, pero sí baja la deuda")
    void porTransferenciaNoEntraAlCajon() {
        tienda.abrir(100_000);
        Cliente juan = conDeuda(50_000);

        tienda.abonarPorTransferencia(juan, 20_000);

        var arqueo = tienda.calcularArqueo.de(tienda.turnoAbierto());
        assertThat(arqueo.abonosTransferencia()).isEqualTo(Dinero.de(20_000));
        assertThat(arqueo.abonosEfectivo()).isEqualTo(Dinero.CERO);
        assertThat(arqueo.esperado()).isEqualTo(Dinero.de(100_000));
    }

    @Test
    @DisplayName("sin turno abierto no se recibe efectivo; por transferencia sí")
    void sinTurnoNoHayEfectivo() {
        Cliente juan = tienda.cliente("Juan Pérez", "1234567");
        tienda.abrir(100_000);
        tienda.fiar(juan, 50_000, 0);
        tienda.cerrar(100_000);

        assertThatThrownBy(() -> tienda.abonar(juan, 10_000))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("no tiene a qué cajón entrar");

        Abono porTransferencia = tienda.abonarPorTransferencia(juan, 10_000);
        assertThat(porTransferencia.getTurnoId()).isNull();
        assertThat(tienda.deudas.debeDe(java.util.List.of(juan.getId()))).containsEntry(juan.getId(), Dinero.de(40_000));
    }

    @Test
    @DisplayName("con el turno abierto de otro cajero: el efectivo no entra, la transferencia se recibe igual")
    void turnoDeOtroCajero() {
        tienda.abrir(100_000);
        Cliente juan = conDeuda(50_000);
        Actor ines = tienda.usuarios.sembrar(ActoresDePrueba.cajero("Inés"));

        // El efectivo entra a un cajón que no es el suyo: no.
        assertThatThrownBy(() -> tienda.registrarAbono.ejecutar(UUID.randomUUID(), juan.getId(), Dinero.de(10_000),
                FormaPago.EFECTIVO, null, null, null, ines)).isInstanceOf(TurnoAjenoException.class);

        // La transferencia no toca el cajón: se recibe, y no se le cuelga al turno de Carolina.
        Abono porTransferencia = tienda.registrarAbono.ejecutar(UUID.randomUUID(), juan.getId(), Dinero.de(10_000),
                FormaPago.TRANSFERENCIA, "REF-9", null, null, ines);

        assertThat(porTransferencia.getTurnoId()).isNull();
        assertThat(tienda.calcularArqueo.de(tienda.turnoAbierto()).abonosTransferencia()).isEqualTo(Dinero.CERO);
        assertThat(tienda.deudas.debeDe(java.util.List.of(juan.getId()))).containsEntry(juan.getId(), Dinero.de(40_000));
    }

    @Test
    @DisplayName("el mismo abono dos veces (misma llave) entra una sola vez")
    void llaveRepetida() {
        tienda.abrir(100_000);
        Cliente juan = conDeuda(50_000);
        UUID llave = UUID.randomUUID();

        Abono primero = tienda.registrarAbono.ejecutar(llave, juan.getId(), Dinero.de(10_000), FormaPago.EFECTIVO,
                null, null, null, tienda.cajero);
        Abono segundo = tienda.registrarAbono.ejecutar(llave, juan.getId(), Dinero.de(10_000), FormaPago.EFECTIVO,
                null, null, null, tienda.cajero);

        assertThat(segundo.getId()).isEqualTo(primero.getId());
        assertThat(tienda.abonos.datos).hasSize(1);
        assertThat(tienda.calcularArqueo.de(tienda.turnoAbierto()).esperado()).isEqualTo(Dinero.de(110_000));
    }

    @Test
    @DisplayName("al cerrar, la parte de abonos queda firmada y las partes suman lo que debería haber")
    void elCierreGuardaLosAbonos() {
        tienda.abrir(100_000);
        Cliente juan = conDeuda(50_000);
        tienda.abonar(juan, 30_000);
        tienda.venderEnEfectivo(20_000);

        TurnoCaja cerrado = tienda.cerrar(150_000);

        assertThat(cerrado.getAbonosEfectivo()).isEqualTo(Dinero.de(30_000));
        assertThat(cerrado.getVentasFiado()).isEqualTo(Dinero.de(50_000));
        assertThat(cerrado.getEsperado()).isEqualTo(Dinero.de(150_000));
        assertThat(cerrado.getDiferencia()).isEqualTo(Dinero.CERO);
        assertThat(cerrado.getFondo().mas(cerrado.getVentasEfectivo()).mas(cerrado.getAbonosEfectivo())
                .menos(cerrado.getDevolucionesEfectivo()).menos(cerrado.getGastosCajon()).menos(cerrado.getRetiros())
                .menos(cerrado.getComprasCajon())).isEqualTo(cerrado.getEsperado());
        assertThat(cerrado.fotografiaDelCierre()).containsEntry("abonosEfectivo", 30_000L);
    }

    @Test
    @DisplayName("anular un abono es del administrador, devuelve la deuda y lo saca del cajón; con su turno cerrado, no se puede")
    void anularUnAbono() {
        tienda.abrir(100_000);
        Cliente juan = conDeuda(50_000);
        Abono abono = tienda.abonar(juan, 30_000);

        assertThatThrownBy(() -> tienda.anularAbono.ejecutar(abono.getId(), "Se registró dos veces", tienda.cajero))
                .isInstanceOf(NoPermitidoException.class);

        tienda.anularAbono.ejecutar(abono.getId(), "Se registró dos veces", tienda.administrador);

        assertThat(abono.estaAnulado()).isTrue();
        assertThat(tienda.calcularArqueo.de(tienda.turnoAbierto()).esperado()).isEqualTo(Dinero.de(100_000));
        assertThat(tienda.deudas.deLaVenta(tienda.ventas.datos.values().iterator().next().getId()).orElseThrow()
                .estado()).isEqualTo(EstadoDeuda.PENDIENTE);
        assertThat(tienda.auditoria.eventos).anySatisfy(e -> {
            assertThat(e.accion()).isEqualTo(AccionAuditada.ANULAR_ABONO);
            assertThat(e.motivo()).isEqualTo("Se registró dos veces");
        });
    }

    @Test
    @DisplayName("un abono de un turno ya cerrado no se anula: ese arqueo ya se firmó")
    void abonoDeUnTurnoCerrado() {
        tienda.abrir(100_000);
        Cliente juan = conDeuda(50_000);
        Abono abono = tienda.abonar(juan, 30_000);
        tienda.cerrar(130_000);
        tienda.abrir(0);

        assertThatThrownBy(() -> tienda.anularAbono.ejecutar(abono.getId(), "Me equivoqué", tienda.administrador))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessage(com.workshopmanagement.rdmotors.clientes.aplicacion.AnularAbono.TURNO_CERRADO);
        assertThat(abono.estaAnulado()).isFalse();
    }

    @Test
    @DisplayName("el turno dice cuántos abonos entraron y de quién")
    void elTurnoListaSusAbonos() {
        tienda.abrir(100_000);
        Cliente juan = conDeuda(50_000);
        tienda.abonar(juan, 30_000);

        DetalleTurno detalle = tienda.consultarTurnos.detalle(tienda.turnoAbierto().getId(), tienda.cajero)
                .orElseThrow();

        assertThat(detalle.abonos()).singleElement().satisfies(a -> {
            assertThat(a.cliente()).isEqualTo("Juan Pérez");
            assertThat(a.monto()).isEqualTo(Dinero.de(30_000));
            assertThat(a.recibidoPor().nombre()).isEqualTo(tienda.cajero.nombre());
        });
        assertThat(detalle.arqueo().abonosEfectivo()).isEqualTo(Dinero.de(30_000));
    }
}
