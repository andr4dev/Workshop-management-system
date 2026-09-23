package com.workshopmanagement.rdmotors.caja.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.caja.dominio.CategoriaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.Gasto;
import com.workshopmanagement.rdmotors.caja.dominio.MasDeLoQueDeberiaHaberException;
import com.workshopmanagement.rdmotors.caja.dominio.NaturalezaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.SinTurnoAbiertoException;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;

/** Registrar un gasto, del cajón o por fuera (spec 0006, H1, H10, RF-001 a RF-007). */
class RegistrarGastoTest {

    private final EscenarioCaja tienda = new EscenarioCaja();

    private ComandoRegistrarGasto delCajon(long monto, String descripcion) {
        return ComandoRegistrarGasto.delCajon(UUID.randomUUID(), tienda.fletes.getId(), Dinero.de(monto), descripcion,
                tienda.cajero);
    }

    @Test
    @DisplayName("uno del cajón queda en el turno abierto, en efectivo, con la fecha de hoy, quién y cuándo, y pide el turno bloqueado")
    void delCajon() {
        TurnoCaja turno = tienda.abrir(100_000);
        int bloqueosAntes = tienda.turnos.vecesBloqueadoParaMover;

        Gasto gasto = tienda.registrarGasto.ejecutar(delCajon(15_000, "  Flete Jotapartes FV-9912 "));

        assertThat(gasto.isDelCajon()).isTrue();
        assertThat(gasto.getTurnoId()).isEqualTo(turno.getId());
        assertThat(gasto.getFormaPago()).isEqualTo(FormaPago.EFECTIVO);
        assertThat(gasto.getCuenta()).isNull();
        assertThat(gasto.getFecha()).isEqualTo(tienda.reloj.hoy());
        assertThat(gasto.getDescripcion()).isEqualTo("Flete Jotapartes FV-9912");
        assertThat(gasto.getRegistradoPorId()).isEqualTo(tienda.cajero.id());
        assertThat(gasto.getCategoria()).isSameAs(tienda.fletes);
        assertThat(tienda.gastos.datos).containsKey(gasto.getId());
        assertThat(tienda.turnos.vecesBloqueadoParaMover).isEqualTo(bloqueosAntes + 1);
    }

    @Test
    @DisplayName("uno del cajón sin turno abierto no se registra, y el mensaje ofrece registrarlo por fuera")
    void delCajonSinTurno() {
        assertThatThrownBy(() -> tienda.registrarGasto.ejecutar(delCajon(15_000, "Flete")))
                .isInstanceOf(SinTurnoAbiertoException.class)
                .hasMessageContaining("por fuera del cajón");
        assertThat(tienda.gastos.datos).isEmpty();
    }

    @Test
    @DisplayName("uno por fuera se registra sin turno: el arriendo por transferencia, con su cuenta y su fecha, sin turno")
    void porFueraSinTurno() {
        Gasto arriendo = tienda.registrarGasto.ejecutar(ComandoRegistrarGasto.porFuera(UUID.randomUUID(),
                tienda.arriendo.getId(), Dinero.de(800_000), "Arriendo de septiembre", FormaPago.TRANSFERENCIA,
                tienda.nequi.getId(), LocalDate.of(2026, 9, 1), tienda.administrador));

        assertThat(arriendo.isDelCajon()).isFalse();
        assertThat(arriendo.getTurnoId()).isNull();
        assertThat(arriendo.getCuenta()).isSameAs(tienda.nequi);
        assertThat(arriendo.getFecha()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(tienda.turnos.vecesBloqueadoParaMover).isZero();
    }

    @Test
    @DisplayName("por fuera: la transferencia exige cuenta, el efectivo no la lleva, la cuenta tiene que estar activa y la fecha no es futura")
    void reglasDelPagoPorFuera() {
        UUID arriendo = tienda.arriendo.getId();
        Dinero monto = Dinero.de(800_000);
        LocalDate hoy = tienda.reloj.hoy();

        assertThatThrownBy(() -> tienda.registrarGasto.ejecutar(ComandoRegistrarGasto.porFuera(UUID.randomUUID(),
                arriendo, monto, "Arriendo", FormaPago.TRANSFERENCIA, null, hoy, tienda.administrador)))
                .hasMessage("Una transferencia necesita la cuenta desde la que salió");
        assertThatThrownBy(() -> tienda.registrarGasto.ejecutar(ComandoRegistrarGasto.porFuera(UUID.randomUUID(),
                arriendo, monto, "Arriendo", FormaPago.EFECTIVO, tienda.nequi.getId(), hoy, tienda.administrador)))
                .hasMessage("Un gasto en efectivo no lleva cuenta");
        assertThatThrownBy(() -> tienda.registrarGasto.ejecutar(ComandoRegistrarGasto.porFuera(UUID.randomUUID(),
                arriendo, monto, "Arriendo", FormaPago.EFECTIVO, null, hoy.plusDays(1), tienda.administrador)))
                .hasMessage("La fecha del gasto no puede ser después de hoy");
        CuentaPago vieja = tienda.cuentas.sembrar(CuentaPago.nueva("Banco viejo"));
        vieja.desactivar();
        assertThatThrownBy(() -> tienda.registrarGasto.ejecutar(ComandoRegistrarGasto.porFuera(UUID.randomUUID(),
                arriendo, monto, "Arriendo", FormaPago.TRANSFERENCIA, vieja.getId(), hoy, tienda.administrador)))
                .hasMessageContaining("desactivada");
        assertThat(tienda.gastos.datos).isEmpty();
    }

    @Test
    @DisplayName("la misma llave dos veces (doble clic) deja un solo gasto y devuelve el mismo")
    void mismaLlave() {
        tienda.abrir(100_000);
        ComandoRegistrarGasto dobleClic = delCajon(15_000, "Flete");

        Gasto primero = tienda.registrarGasto.ejecutar(dobleClic);
        Gasto segundo = tienda.registrarGasto.ejecutar(dobleClic);

        assertThat(segundo.getId()).isEqualTo(primero.getId());
        assertThat(tienda.gastos.datos).hasSize(1);
    }

    @Test
    @DisplayName("sin categoría, sin descripción, con monto $0 o con una categoría desactivada no se registra")
    void datosObligatorios() {
        tienda.abrir(100_000);
        CategoriaGasto vieja = tienda.categorias.sembrar(CategoriaGasto.nueva("Publicidad", NaturalezaGasto.GASTO));
        vieja.desactivar();

        assertThatThrownBy(() -> tienda.registrarGasto.ejecutar(ComandoRegistrarGasto.delCajon(UUID.randomUUID(), null,
                Dinero.de(1_000), "Flete", tienda.cajero))).hasMessage("Elige la categoría del gasto");
        assertThatThrownBy(() -> tienda.registrarGasto.ejecutar(delCajon(1_000, "   ")))
                .hasMessage("Escribe en qué se gastó: queda con el gasto");
        assertThatThrownBy(() -> tienda.registrarGasto.ejecutar(delCajon(0, "Flete")))
                .hasMessage("El monto del gasto tiene que ser mayor a $0");
        assertThatThrownBy(() -> tienda.registrarGasto.ejecutar(ComandoRegistrarGasto.delCajon(UUID.randomUUID(),
                vieja.getId(), Dinero.de(1_000), "Volantes", tienda.cajero)))
                .hasMessage("La categoría «Publicidad» está desactivada: elige otra");
        assertThat(tienda.gastos.datos).isEmpty();
    }

    @Test
    @DisplayName("DECISIÓN 3: más de lo que debería haber pide confirmar, sin decir la cifra; confirmado, se registra")
    void masDeLoQueDeberiaHaber() {
        tienda.abrir(100_000);
        tienda.venderEnEfectivo(20_000);
        ComandoRegistrarGasto ceroDeMas = delCajon(150_000, "Flete");

        assertThatThrownBy(() -> tienda.registrarGasto.ejecutar(ceroDeMas))
                .isInstanceOf(MasDeLoQueDeberiaHaberException.class)
                .hasMessage("Es más de lo que debería haber en el cajón. ¿Seguro?")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContainPattern("\\d"));
        assertThat(tienda.gastos.datos).isEmpty();

        Gasto confirmado = tienda.registrarGasto.ejecutar(ceroDeMas.conConfirmacion());

        assertThat(confirmado.getMonto()).isEqualTo(Dinero.de(150_000));
        assertThat(tienda.calcularArqueo.de(tienda.turnoAbierto()).esperado()).isEqualTo(Dinero.de(-30_000));
    }

    @Test
    @DisplayName("exactamente lo que debería haber no pide confirmar; un gasto por fuera nunca")
    void igualNoPideConfirmar() {
        tienda.abrir(100_000);

        assertThat(tienda.registrarGasto.ejecutar(delCajon(100_000, "Todo el cajón")).getMonto())
                .isEqualTo(Dinero.de(100_000));
        assertThat(tienda.arriendoPorNequi(5_000_000).getMonto()).isEqualTo(Dinero.de(5_000_000));
    }

    @Test
    @DisplayName("SPEC 0007: un gasto se registra del mes, del cajón o por fuera; si no se dice, es de su día")
    void delMes() {
        tienda.abrir(1_000_000);

        Gasto delCajon = tienda.registrarGasto.ejecutar(delCajon(800_000, "Arriendo en efectivo").comoDelMes());
        Gasto porFuera = tienda.registrarGasto.ejecutar(ComandoRegistrarGasto.porFuera(UUID.randomUUID(),
                tienda.arriendo.getId(), Dinero.de(800_000), "Arriendo por Nequi", FormaPago.TRANSFERENCIA,
                tienda.nequi.getId(), tienda.reloj.hoy(), tienda.administrador).comoDelMes());
        Gasto delDia = tienda.registrarGasto.ejecutar(delCajon(15_000, "Flete"));

        assertThat(delCajon.isDelMes()).isTrue();
        assertThat(porFuera.isDelMes()).isTrue();
        assertThat(delDia.isDelMes()).isFalse();
        assertThat(delCajon.fotografia()).containsEntry("delMes", true);
    }

    @Test
    @DisplayName("una categoría que no existe no se usa")
    void categoriaInexistente() {
        tienda.abrir(100_000);
        assertThatThrownBy(() -> tienda.registrarGasto.ejecutar(ComandoRegistrarGasto.delCajon(UUID.randomUUID(),
                UUID.randomUUID(), Dinero.de(1_000), "Flete", tienda.cajero)))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessage("La categoría no existe");
    }
}
