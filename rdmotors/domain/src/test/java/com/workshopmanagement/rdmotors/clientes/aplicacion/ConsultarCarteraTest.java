package com.workshopmanagement.rdmotors.clientes.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.clientes.dominio.Abono;
import com.workshopmanagement.rdmotors.clientes.dominio.CarteraDelCliente;
import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.clientes.dominio.DatosCliente;
import com.workshopmanagement.rdmotors.clientes.dominio.Deuda;
import com.workshopmanagement.rdmotors.clientes.dominio.EstadoDeuda;
import com.workshopmanagement.rdmotors.clientes.dominio.FiltroCartera;
import com.workshopmanagement.rdmotors.clientes.dominio.OrigenDeuda;
import com.workshopmanagement.rdmotors.clientes.dominio.ResumenDeCliente;
import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;

/** La Cartera: quién debe, desde cuándo, el historial, la ficha, y cerrar el fiado (spec 0008, H3, H5 y H11). */
class ConsultarCarteraTest {

    private final Falsos.RelojFijo reloj = new Falsos.RelojFijo("2026-09-21T15:00:00Z");
    private final Falsos.ClientesEnMemoria clientes = new Falsos.ClientesEnMemoria();
    private final Falsos.DeudasEnMemoria deudas = new Falsos.DeudasEnMemoria();
    private final Falsos.AbonosEnMemoria abonos = new Falsos.AbonosEnMemoria();
    private final Falsos.UsuariosEnMemoria usuarios = new Falsos.UsuariosEnMemoria();
    private final Falsos.AuditoriaEnMemoria auditoria = new Falsos.AuditoriaEnMemoria();
    private final ConsultarCartera consultar = new ConsultarCartera(new Falsos.CarteraEnMemoria(clientes, deudas, abonos),
            clientes, deudas, abonos, usuarios);
    private final CambiarFiado cambiarFiado = new CambiarFiado(clientes, auditoria, reloj);
    private final Actor cajero = usuarios.sembrar(ActoresDePrueba.cajero());
    private final Actor administrador = usuarios.sembrar(ActoresDePrueba.administrador());
    private Instant momento = Instant.parse("2026-09-01T15:00:00Z");

    private Instant luego() {
        momento = momento.plusSeconds(60);
        return momento;
    }

    private Cliente cliente(String nombre, String cedula) {
        return clientes.sembrar(Cliente.nuevo(new DatosCliente(nombre, cedula, "300" + cedula, null, null), cajero.id(),
                luego()));
    }

    private CarteraDelCliente carteraDe(Cliente c) {
        return new CarteraDelCliente(c, deudas.delCliente(c.getId()), abonos.delCliente(c.getId()));
    }

    private Deuda fiar(Cliente c, long numero, LocalDate dia, long monto) {
        Deuda deuda = Deuda.porVenta(c.getId(), UUID.randomUUID(), numero, dia, Dinero.de(monto), cajero.id(), luego());
        carteraDe(c).registrarDeuda(deuda, momento);
        return deudas.guardar(deuda);
    }

    private Abono abonar(Cliente c, long monto) {
        Abono abono = Abono.recibir(abonos.siguienteNumero(), c.getId(), Dinero.de(monto), FormaPago.EFECTIVO, null,
                null, UUID.randomUUID(), cajero.id(), UUID.randomUUID(), luego());
        carteraDe(c).abonar(abono, null, momento);
        return abonos.guardar(abono);
    }

    @Test
    @DisplayName("los que deben, del que más al que menos, con cuántos deben y el total por cobrar")
    void losQueDeben() {
        Cliente juan = cliente("Juan Pérez", "1234567");
        Cliente ana = cliente("Ana Gómez", "7654321");
        Cliente pedro = cliente("Pedro Ruiz", "5555555");
        fiar(juan, 41, LocalDate.of(2026, 9, 12), 50_000);
        fiar(ana, 42, LocalDate.of(2026, 9, 10), 80_000);
        fiar(pedro, 43, LocalDate.of(2026, 9, 11), 30_000);
        abonar(pedro, 30_000);

        ConsultarCartera.Cartera cartera = consultar.lista(FiltroCartera.losQueDeben());

        assertThat(cartera.clientes()).extracting(ResumenDeCliente::nombre).containsExactly("Ana Gómez", "Juan Pérez");
        assertThat(cartera.deben()).isEqualTo(2);
        assertThat(cartera.porCobrar()).isEqualTo(Dinero.de(130_000));
        assertThat(cartera.clientes().getFirst().desde()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(cartera.clientes().getFirst().pendientes()).isEqualTo(1);
    }

    @Test
    @DisplayName("el historial trae también a los que ya pagaron, con lo fiado y lo pagado en total")
    void historialCompleto() {
        Cliente pedro = cliente("Pedro Ruiz", "5555555");
        cliente("Sin fiado", "9999999");
        fiar(pedro, 43, LocalDate.of(2026, 9, 11), 30_000);
        abonar(pedro, 30_000);

        ConsultarCartera.Cartera cartera = consultar.lista(new FiltroCartera(FiltroCartera.Vista.HISTORIAL, null));

        assertThat(cartera.clientes()).singleElement().satisfies(r -> {
            assertThat(r.nombre()).isEqualTo("Pedro Ruiz");
            assertThat(r.debe()).isEqualTo(Dinero.CERO);
            assertThat(r.fiadoTotal()).isEqualTo(Dinero.de(30_000));
            assertThat(r.pagadoTotal()).isEqualTo(Dinero.de(30_000));
            assertThat(r.desde()).isNull();
        });
        assertThat(cartera.deben()).isZero();
        assertThat(consultar.lista(new FiltroCartera(FiltroCartera.Vista.HISTORIAL, "5555555")).clientes())
                .hasSize(1);
    }

    @Test
    @DisplayName("la ficha: cada venta fiada con su estado y sus abonos, la más reciente primero, y quién los recibió")
    void laFicha() {
        Cliente juan = cliente("Juan Pérez", "1234567");
        Deuda la41 = fiar(juan, 41, LocalDate.of(2026, 9, 12), 50_000);
        fiar(juan, 57, LocalDate.of(2026, 9, 18), 30_000);
        Abono abono = abonar(juan, 60_000);

        FichaCliente ficha = consultar.ficha(juan.getId()).orElseThrow();

        assertThat(ficha.debe()).isEqualTo(Dinero.de(20_000));
        assertThat(ficha.desde()).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(ficha.deudas()).extracting(FichaCliente.DeudaDeLaFicha::numeroVenta).containsExactly(57L, 41L);
        assertThat(ficha.deudas()).extracting(FichaCliente.DeudaDeLaFicha::estado)
                .containsExactly(EstadoDeuda.ABONADA, EstadoDeuda.PAGADA);
        assertThat(ficha.deudas().get(1).abonos()).singleElement().satisfies(p -> {
            assertThat(p.numero()).isEqualTo(abono.getNumero());
            assertThat(p.monto()).isEqualTo(Dinero.de(50_000));
        });
        assertThat(ficha.abonos()).singleElement().satisfies(a -> {
            assertThat(a.recibidoPor().nombre()).isEqualTo(cajero.nombre());
            assertThat(a.aplicaciones()).extracting(FichaCliente.ParteAplicada::deuda)
                    .containsExactly("la venta N.º 41", "la venta N.º 57");
        });
        assertThat(ficha.fiadoTotal()).isEqualTo(Dinero.de(80_000));
        assertThat(ficha.pagadoTotal()).isEqualTo(Dinero.de(60_000));
        assertThat(la41.getId()).isEqualTo(ficha.deudas().get(1).id());
    }

    @Test
    @DisplayName("el saldo del cuaderno es del administrador, se carga una sola vez y se paga primero por ser lo más viejo")
    void saldoDelCuaderno() {
        CargarSaldoDelCuaderno cargar = new CargarSaldoDelCuaderno(clientes, deudas, abonos, auditoria, reloj);
        Cliente juan = cliente("Juan Pérez", "1234567");
        fiar(juan, 41, LocalDate.of(2026, 9, 12), 50_000);

        assertThatThrownBy(() -> cargar.ejecutar(juan.getId(), Dinero.de(120_000), LocalDate.of(2026, 7, 1),
                "Lo del cuaderno", cajero)).isInstanceOf(NoPermitidoException.class);

        cargar.ejecutar(juan.getId(), Dinero.de(120_000), LocalDate.of(2026, 7, 1), "Lo del cuaderno", administrador);

        FichaCliente ficha = consultar.ficha(juan.getId()).orElseThrow();
        assertThat(ficha.debe()).isEqualTo(Dinero.de(170_000));
        assertThat(ficha.desde()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(auditoria.eventos).anySatisfy(e -> {
            assertThat(e.accion()).isEqualTo(AccionAuditada.CARGAR_SALDO_CUADERNO);
            assertThat(e.motivo()).isEqualTo("Lo del cuaderno");
        });

        abonar(juan, 120_000);
        assertThat(consultar.ficha(juan.getId()).orElseThrow().deudas())
                .filteredOn(d -> d.origen() == OrigenDeuda.CUADERNO).singleElement()
                .satisfies(d -> assertThat(d.estado()).isEqualTo(EstadoDeuda.PAGADA));

        assertThatThrownBy(() -> cargar.ejecutar(juan.getId(), Dinero.de(1_000), LocalDate.of(2026, 7, 1), "otra vez",
                administrador)).hasMessageContaining("ya se le cargó el saldo del cuaderno");
    }

    @Test
    @DisplayName("cerrar y abrir el fiado es del administrador y queda en la auditoría con el motivo")
    void cerrarElFiado() {
        Cliente juan = cliente("Juan Pérez", "1234567");

        assertThatThrownBy(() -> cambiarFiado.cerrar(juan.getId(), "No paga", cajero))
                .isInstanceOf(NoPermitidoException.class);
        assertThat(juan.isFiadoCerrado()).isFalse();

        cambiarFiado.cerrar(juan.getId(), "No paga desde julio", administrador);
        assertThat(juan.isFiadoCerrado()).isTrue();
        assertThat(auditoria.eventos).singleElement().satisfies(e -> {
            assertThat(e.accion()).isEqualTo(AccionAuditada.CERRAR_FIADO);
            assertThat(e.motivo()).isEqualTo("No paga desde julio");
        });

        assertThatThrownBy(() -> cambiarFiado.abrir(juan.getId(), cajero)).isInstanceOf(NoPermitidoException.class);
        cambiarFiado.abrir(juan.getId(), administrador);
        assertThat(juan.isFiadoCerrado()).isFalse();
        assertThat(auditoria.eventos).extracting(e -> e.accion())
                .containsExactly(AccionAuditada.CERRAR_FIADO, AccionAuditada.ABRIR_FIADO);
    }
}
