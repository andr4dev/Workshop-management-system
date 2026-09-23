package com.workshopmanagement.rdmotors.caja.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.caja.dominio.TurnoAjenoException;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.correo.aplicacion.AdministrarCorreos;
import com.workshopmanagement.rdmotors.correo.aplicacion.Cartero;
import com.workshopmanagement.rdmotors.correo.aplicacion.MandarCorreosPendientes;
import com.workshopmanagement.rdmotors.correo.dominio.Correo;
import com.workshopmanagement.rdmotors.correo.dominio.CorreoArmado;
import com.workshopmanagement.rdmotors.correo.dominio.EnvioFallidoException;
import com.workshopmanagement.rdmotors.correo.dominio.EstadoCorreo;
import com.workshopmanagement.rdmotors.correo.dominio.PoliticaDeReintentos;
import com.workshopmanagement.rdmotors.correo.dominio.TipoCorreo;

/**
 * El correo del cierre, de punta a punta con la caja (spec 0010): el cierre lo deja por mandar en su mismo commit, la
 * tarea lo manda cuando puede, y lo que llega dice lo mismo que la pantalla de Caja.
 *
 * <p>El turno es el de la tienda del 21 de septiembre: $22.000 en efectivo, $22.000 mixto (15.000 + 7.000), $22.000
 * fiados a Julio, y Julio abonó sus $22.000 en efectivo.
 */
class CorreoDelCierreTest {

    private EscenarioCaja tienda;
    private Falsos.EnviadorFalso brevo;
    private Falsos.RelojManual relojDelCorreo;
    private MandarCorreosPendientes mandar;
    private AdministrarCorreos administrar;

    @BeforeEach
    void preparar() {
        tienda = new EscenarioCaja();
        brevo = new Falsos.EnviadorFalso();
        tienda.ajustesDeCorreo.para("ruben@rdmotors.co", "socio@gmail.com");
    }

    /** El reloj del correo arranca donde quedó el cierre: así se puede decir "3 minutos después". */
    private void conLaTareaDesde(java.time.Instant momento) {
        relojDelCorreo = new Falsos.RelojManual(momento);
        Cartero cartero = new Cartero(brevo, tienda.consultarTurnos::detalleDelCierre, () -> "RD MOTORS",
                new PoliticaDeReintentos(), relojDelCorreo);
        mandar = new MandarCorreosPendientes(tienda.correos, cartero, relojDelCorreo);
        administrar = new AdministrarCorreos(tienda.ajustesDeCorreo, tienda.correos, cartero, relojDelCorreo);
    }

    /** El turno real, cerrado con {@code faltante} pesos menos de lo que debería haber. */
    private TurnoCaja turnoDeJulio(long faltante) {
        tienda.abrir(100_000);
        tienda.venderEnEfectivo(22_000);
        tienda.vender(15_000, 7_000);
        Cliente julio = tienda.cliente("Julio motors", "1082903481");
        tienda.fiar(julio, 22_000, 0);
        tienda.abonar(julio, 22_000);
        long esperado = tienda.calcularArqueo.de(tienda.turnoAbierto()).esperado().valor().longValueExact();
        return tienda.cerrar(esperado - faltante);
    }

    private Correo elCorreo() {
        assertThat(tienda.correos.datos).hasSize(1);
        return tienda.correos.datos.getFirst();
    }

    // ── Encolar al cerrar (RF-001) ──────────────────────────────────────────

    @Test
    @DisplayName("al cerrar queda el resumen POR MANDAR, a los dos correos configurados, 3 minutos después del cierre")
    void alCerrarQuedaPorMandar() {
        TurnoCaja cerrado = turnoDeJulio(0);

        Correo correo = elCorreo();
        assertThat(correo.getTipo()).isEqualTo(TipoCorreo.CIERRE_DE_TURNO);
        assertThat(correo.getTurnoId()).isEqualTo(cerrado.getId());
        assertThat(correo.getEstado()).isEqualTo(EstadoCorreo.POR_MANDAR);
        assertThat(correo.para().correos()).containsExactly("ruben@rdmotors.co", "socio@gmail.com");
        assertThat(correo.getNoAntesDe()).isEqualTo(cerrado.getCerradoEn().plus(Duration.ofMinutes(3)));
    }

    @Test
    @DisplayName("sin destinatarios configurados, cerrar no encola nada: así se apaga")
    void sinDestinatariosNoHayCorreo() {
        tienda.ajustesDeCorreo.para();

        turnoDeJulio(0);

        assertThat(tienda.correos.datos).isEmpty();
    }

    @Test
    @DisplayName("si el cierre no pasa, no queda correo: van en el mismo commit")
    void sinCierreNoHayCorreo() {
        tienda.abrir(100_000);
        var ines = ActoresDePrueba.cajero("Inés");
        tienda.usuarios.sembrar(ines);

        assertThatThrownBy(() -> tienda.cerrarTurno.ejecutar(tienda.turnoAbierto().getId(), Dinero.de(100_000), ines))
                .isInstanceOf(TurnoAjenoException.class);
        assertThat(tienda.correos.datos).isEmpty();
    }

    // ── Mandarlo (RF-002, RF-003, RF-005, RF-006) ───────────────────────────

    @Test
    @DisplayName("LO QUE LLEGA dice lo mismo que la Caja: producido, formas de pago, cartera, el cajón y el faltante")
    void loQueLlega() {
        TurnoCaja cerrado = turnoDeJulio(5_000);
        tienda.escribirObservaciones.ejecutar(cerrado.getId(), "Faltaron $5.000; se revisa mañana con Carolina",
                tienda.cajero);
        conLaTareaDesde(cerrado.getCerradoEn());

        assertThat(mandar.mandar()).as("antes de 3 minutos no sale").isZero();
        relojDelCorreo.avanzar(Duration.ofMinutes(3));
        assertThat(mandar.mandar()).isEqualTo(1);

        CorreoArmado llego = brevo.enviados.getFirst();
        assertThat(brevo.a.getFirst().correos()).containsExactly("ruben@rdmotors.co", "socio@gmail.com");
        assertThat(llego.asunto()).startsWith("Cierre de caja · RD MOTORS · ").endsWith("faltan $ 5.000");
        assertThat(llego.texto())
                .contains("PRODUCIDO DEL TURNO: $ 66.000")
                .containsPattern("Efectivo \\(al cajón\\)\\s+\\$ 37\\.000")
                .containsPattern("Transferencia \\(a la cuenta\\)\\s+\\$ 7\\.000")
                .containsPattern("Fiado \\(a la cartera\\)\\s+\\$ 22\\.000")
                .containsPattern("Abonos en efectivo \\(al cajón\\)\\s+\\$ 22\\.000")
                .containsPattern("Debería haber\\s+\\$ 159\\.000")
                .containsPattern("Contaron\\s+\\$ 154\\.000")
                .contains("FALTAN $ 5.000")
                // Las observaciones se escribieron DESPUÉS de cerrar, y llegan igual (RF-002).
                .contains("Faltaron $5.000; se revisa mañana con Carolina");
        assertThat(llego.html()).contains("$ 66.000").contains("$ 159.000").contains("Faltan $ 5.000");

        Correo correo = elCorreo();
        assertThat(correo.getEstado()).isEqualTo(EstadoCorreo.ENVIADO);
        assertThat(correo.getIdEnBrevo()).isEqualTo("<brevo-1@smtp-relay.mailin.fr>");
        assertThat(mandar.mandar()).as("lo que ya salió no sale otra vez").isZero();
    }

    @Test
    @DisplayName("lo que escribió una persona no puede meter etiquetas en el correo")
    void observacionesSeEscapan() {
        TurnoCaja cerrado = turnoDeJulio(0);
        tienda.escribirObservaciones.ejecutar(cerrado.getId(), "<script>alert(1)</script>", tienda.cajero);
        conLaTareaDesde(cerrado.getCerradoEn().plus(Duration.ofMinutes(3)));

        mandar.mandar();

        assertThat(brevo.enviados.getFirst().html()).doesNotContain("<script>").contains("&lt;script&gt;");
        assertThat(brevo.enviados.getFirst().asunto()).endsWith("cuadra al peso");
    }

    @Test
    @DisplayName("SIN INTERNET no se pierde: queda por mandar, con el error, y se reintenta al minuto")
    void sinInternet() {
        TurnoCaja cerrado = turnoDeJulio(0);
        conLaTareaDesde(cerrado.getCerradoEn().plus(Duration.ofMinutes(3)));
        brevo.falla = new EnvioFallidoException("No se pudo hablar con Brevo: api.brevo.com", true);

        mandar.mandar();

        Correo correo = elCorreo();
        assertThat(correo.getEstado()).isEqualTo(EstadoCorreo.POR_MANDAR);
        assertThat(correo.getIntentos()).isEqualTo(1);
        assertThat(correo.getUltimoError()).contains("api.brevo.com");

        // Vuelve internet: al minuto siguiente sale.
        brevo.falla = null;
        relojDelCorreo.avanzar(Duration.ofMinutes(1));
        mandar.mandar();
        assertThat(correo.getEstado()).isEqualTo(EstadoCorreo.ENVIADO);
        assertThat(correo.getIntentos()).isEqualTo(2);
    }

    @Test
    @DisplayName("una llave inválida deja el correo FALLÓ, con lo que dijo Brevo, y no se insiste solo")
    void llaveInvalida() {
        TurnoCaja cerrado = turnoDeJulio(0);
        conLaTareaDesde(cerrado.getCerradoEn().plus(Duration.ofMinutes(3)));
        brevo.falla = new EnvioFallidoException("Brevo respondió 401: Key not found", false);

        mandar.mandar();
        relojDelCorreo.avanzar(Duration.ofHours(5));
        mandar.mandar();

        Correo correo = elCorreo();
        assertThat(correo.getEstado()).isEqualTo(EstadoCorreo.FALLO);
        assertThat(correo.getIntentos()).as("un solo intento: no se insiste contra una pared").isEqualTo(1);
        assertThat(correo.getUltimoError()).contains("401");
    }

    @Test
    @DisplayName("sin la llave de Brevo configurada, espera y dice qué falta, sin gastar intentos")
    void sinConfigurar() {
        TurnoCaja cerrado = turnoDeJulio(0);
        conLaTareaDesde(cerrado.getCerradoEn().plus(Duration.ofMinutes(3)));
        brevo.configurado = false;

        mandar.mandar();

        Correo correo = elCorreo();
        assertThat(correo.getEstado()).isEqualTo(EstadoCorreo.POR_MANDAR);
        assertThat(correo.getIntentos()).isZero();
        assertThat(correo.getUltimoError()).contains("BREVO_API_KEY");
        assertThat(brevo.enviados).isEmpty();
    }

    // ── Ajustes › Correos (RF-007, RF-009) ──────────────────────────────────

    @Test
    @DisplayName("el de prueba sale en el momento; el administrador reintenta el que falló; el cajero no toca nada")
    void administrar() {
        conLaTareaDesde(java.time.Instant.parse("2026-09-22T15:00:00Z"));

        Correo prueba = administrar.probar(tienda.administrador);
        assertThat(prueba.getEstado()).isEqualTo(EstadoCorreo.ENVIADO);
        assertThat(brevo.enviados.getFirst().asunto()).isEqualTo("Prueba de correo · RD MOTORS");

        brevo.falla = new EnvioFallidoException("Brevo respondió 400: sender not valid", false);
        Correo fallida = administrar.probar(tienda.administrador);
        assertThat(fallida.getEstado()).isEqualTo(EstadoCorreo.FALLO);
        brevo.falla = null;
        administrar.reintentar(fallida.getId(), tienda.administrador);
        mandar.mandar();
        assertThat(fallida.getEstado()).isEqualTo(EstadoCorreo.ENVIADO);

        assertThatThrownBy(() -> administrar.probar(tienda.cajero)).isInstanceOf(NoPermitidoException.class);
        assertThatThrownBy(() -> administrar.cambiarDestinatarios(List.of("otro@x.co"), tienda.cajero))
                .isInstanceOf(NoPermitidoException.class);
    }

    @Test
    @DisplayName("los destinatarios se cambian limpios; y sin ninguno, la prueba dice qué hacer primero")
    void destinatarios() {
        conLaTareaDesde(java.time.Instant.parse("2026-09-22T15:00:00Z"));

        administrar.cambiarDestinatarios(List.of(" Socio@Gmail.com ", "socio@gmail.com"), tienda.administrador);
        assertThat(tienda.ajustesDeCorreo.obtener().destinatarios().correos()).containsExactly("socio@gmail.com");

        administrar.cambiarDestinatarios(List.of(), tienda.administrador);
        assertThatThrownBy(() -> administrar.probar(tienda.administrador))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("Primero escribe a qué correos llega");
    }
}
