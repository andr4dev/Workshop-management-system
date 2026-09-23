package com.workshopmanagement.rdmotors.caja.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.caja.dominio.EstadoTurno;
import com.workshopmanagement.rdmotors.caja.dominio.SinTurnoAbiertoException;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoYaCerradoException;
import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;

/**
 * Cerrar el turno con lo contado (spec 0006, H3, RF-012 a RF-018). La que más protege es
 * {@link #elEjemploDelSpec()}: $88.400 esperados, $87.000 contados, faltan $1.400, y queda el evento.
 */
class CerrarTurnoTest {

    private final EscenarioCaja tienda = new EscenarioCaja();

    @Test
    @DisplayName("EL EJEMPLO DEL SPEC: debería haber $88.400, se contaron $87.000, faltan $1.400; las partes quedan guardadas")
    void elEjemploDelSpec() {
        tienda.diaDelEjemplo();

        TurnoCaja cerrado = tienda.cerrar(87_000);

        assertThat(cerrado.getEstado()).isEqualTo(EstadoTurno.CERRADO);
        assertThat(cerrado.getEsperado()).isEqualTo(Dinero.de(88_400));
        assertThat(cerrado.getContado()).isEqualTo(Dinero.de(87_000));
        assertThat(cerrado.getDiferencia()).isEqualTo(Dinero.de(-1_400));
        assertThat(cerrado.getVentasEfectivo()).isEqualTo(Dinero.de(223_400));
        assertThat(cerrado.getVentasTransferencia()).isEqualTo(Dinero.de(111_000));
        assertThat(cerrado.getDevolucionesEfectivo()).isEqualTo(Dinero.de(70_000));
        assertThat(cerrado.getGastosCajon()).isEqualTo(Dinero.de(15_000));
        assertThat(cerrado.getRetiros()).isEqualTo(Dinero.de(100_000));
        assertThat(cerrado.getComprasCajon()).isEqualTo(Dinero.de(50_000));
        assertThat(cerrado.getCerradoPorId()).isEqualTo(tienda.cajero.id());
        assertThat(cerrado.getCerradoEn()).isNotNull();
        assertThat(tienda.turnos.vecesBloqueadoParaCerrar).isEqualTo(2);
    }

    @Test
    @DisplayName("con diferencia queda un evento de auditoría con lo que debería haber, lo contado, la diferencia y quién cerró")
    void conDiferenciaDejaEvento() {
        TurnoCaja turno = tienda.diaDelEjemplo();

        tienda.cerrar(87_000);

        assertThat(tienda.auditoria.historialDe(CerrarTurno.TIPO_AUDITORIA, turno.getId())).singleElement()
                .satisfies((EventoAuditoria e) -> {
                    assertThat(e.accion()).isEqualTo(AccionAuditada.CERRAR_CAJA_CON_DIFERENCIA);
                    assertThat(e.usuarioId()).isEqualTo(tienda.cajero.id());
                    assertThat(e.despues()).containsEntry("esperado", 88_400L).containsEntry("contado", 87_000L)
                            .containsEntry("diferencia", -1_400L).containsEntry("cerradoPorId", tienda.cajero.id());
                    assertThat(e.antes()).containsEntry("estado", "ABIERTO");
                });
    }

    @Test
    @DisplayName("si cuadra no deja evento; un sobrante también es diferencia")
    void cuadraSinEvento() {
        TurnoCaja cuadra = tienda.abrir(100_000);
        tienda.venderEnEfectivo(20_000);
        tienda.cerrar(120_000);
        TurnoCaja sobra = tienda.abrir(50_000);
        TurnoCaja conSobrante = tienda.cerrar(50_500);

        assertThat(tienda.auditoria.historialDe(CerrarTurno.TIPO_AUDITORIA, cuadra.getId())).isEmpty();
        assertThat(conSobrante.getDiferencia()).isEqualTo(Dinero.de(500));
        assertThat(tienda.auditoria.historialDe(CerrarTurno.TIPO_AUDITORIA, sobra.getId())).hasSize(1);
    }

    @Test
    @DisplayName("cerrar dos veces el mismo turno: el segundo dice que ya se cerró, con cuándo, y las cifras no cambian")
    void yaCerrado() {
        TurnoCaja turno = tienda.abrir(100_000);
        TurnoCaja primero = tienda.cerrarTurno.ejecutar(turno.getId(), Dinero.de(100_000), tienda.cajero);

        assertThatThrownBy(() -> tienda.cerrarTurno.ejecutar(turno.getId(), Dinero.de(90_000), ActoresDePrueba.administrador()))
                .isInstanceOfSatisfying(TurnoYaCerradoException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo("Este turno ya se cerró");
                    assertThat(e.getCerradoEn()).isEqualTo(primero.getCerradoEn());
                    assertThat(e.getCerradoPorId()).isEqualTo(tienda.cajero.id());
                });
        assertThat(tienda.turnos.buscar(turno.getId()).orElseThrow().getContado()).isEqualTo(Dinero.de(100_000));
    }

    @Test
    @DisplayName("lo contado vacío o negativo no cierra; $0 sí")
    void contado() {
        TurnoCaja turno = tienda.abrir(0);

        assertThatThrownBy(() -> tienda.cerrarTurno.ejecutar(turno.getId(), null, tienda.cajero))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessage("Escribe cuánto contaste, aunque sea $0");
        assertThatThrownBy(() -> tienda.cerrarTurno.ejecutar(turno.getId(), Dinero.de(-1), tienda.cajero))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessage("Escribe cuánto contaste, aunque sea $0");
        assertThat(tienda.turnoAbierto().getId()).isEqualTo(turno.getId());

        assertThat(tienda.cerrar(0).getDiferencia()).isEqualTo(Dinero.CERO);
    }

    @Test
    @DisplayName("cerrado el turno no se vende, no se anula, no se registran gastos del cajón, retiros ni compras de caja; un gasto por fuera sí")
    void cerradoNoMuevePlata() {
        tienda.abrir(100_000);
        Venta venta = tienda.venderEnEfectivo(10_000);
        tienda.cerrar(110_000);

        assertThatThrownBy(() -> tienda.venderEnEfectivo(5_000)).isInstanceOf(SinTurnoAbiertoException.class);
        assertThatThrownBy(() -> tienda.anular(venta)).isInstanceOf(SinTurnoAbiertoException.class);
        assertThatThrownBy(() -> tienda.gastoDelCajon(1_000)).isInstanceOf(SinTurnoAbiertoException.class);
        assertThatThrownBy(() -> tienda.retiro(1_000)).isInstanceOf(SinTurnoAbiertoException.class);
        assertThatThrownBy(() -> tienda.compra(1_000, true)).isInstanceOf(SinTurnoAbiertoException.class);
        assertThat(tienda.arriendoPorNequi(800_000).isDelCajon()).isFalse();
    }

    @Test
    @DisplayName("anular mañana una venta de un turno cerrado no cambia sus cifras: resta en el turno de mañana")
    void cierreNoCambiaDespues() {
        TurnoCaja ayer = tienda.abrir(100_000);
        Venta venta = tienda.venderEnEfectivo(40_000);
        tienda.cerrar(140_000);

        tienda.abrir(100_000);
        tienda.anular(venta);

        TurnoCaja releido = tienda.turnos.buscar(ayer.getId()).orElseThrow();
        assertThat(releido.getVentasEfectivo()).isEqualTo(Dinero.de(40_000));
        assertThat(releido.getEsperado()).isEqualTo(Dinero.de(140_000));
        assertThat(tienda.calcularArqueo.de(tienda.turnoAbierto()).esperado()).isEqualTo(Dinero.de(60_000));
    }

    @Test
    @DisplayName("las observaciones se escriben una sola vez, con el turno cerrado y con texto")
    void observaciones() {
        TurnoCaja turno = tienda.abrir(100_000);
        assertThatThrownBy(() -> tienda.escribirObservaciones.ejecutar(turno.getId(), "nada", tienda.cajero))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessageContaining("al cerrar");
        tienda.cerrar(98_000);

        assertThatThrownBy(() -> tienda.escribirObservaciones.ejecutar(turno.getId(), "   ", tienda.cajero))
                .isInstanceOf(ReglaDeNegocioException.class);
        TurnoCaja conNota = tienda.escribirObservaciones.ejecutar(turno.getId(), "  Se dio mal un cambio de $2.000  ", tienda.cajero);
        assertThat(conNota.getObservaciones()).isEqualTo("Se dio mal un cambio de $2.000");

        assertThatThrownBy(() -> tienda.escribirObservaciones.ejecutar(turno.getId(), "otra cosa", tienda.cajero))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessageContaining("ya se escribieron");
        assertThat(tienda.turnos.buscar(turno.getId()).orElseThrow().getObservaciones())
                .isEqualTo("Se dio mal un cambio de $2.000");
    }

    @Test
    @DisplayName("un turno que no existe no se cierra")
    void noExiste() {
        assertThatThrownBy(() -> tienda.cerrarTurno.ejecutar(UUID.randomUUID(), Dinero.CERO, tienda.cajero))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessage("El turno no existe");
    }
}
