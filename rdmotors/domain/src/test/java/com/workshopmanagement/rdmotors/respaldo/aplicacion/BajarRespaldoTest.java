package com.workshopmanagement.rdmotors.respaldo.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;
import com.workshopmanagement.rdmotors.respaldo.dominio.EstadoRespaldo;
import com.workshopmanagement.rdmotors.respaldo.dominio.OrigenRespaldo;
import com.workshopmanagement.rdmotors.respaldo.dominio.PoliticaDeRespaldo;
import com.workshopmanagement.rdmotors.respaldo.dominio.Respaldo;
import com.workshopmanagement.rdmotors.respaldo.dominio.RespaldoFallidoException;

/**
 * El administrador se lleva una copia del negocio (spec 0011, RF-010).
 *
 * <p>Lo que hay que probar aquí es <b>qué pasa cuando falla</b>: que quien pidió el archivo se entere, que quede
 * escrito que falló, y que no se entregue media copia.
 */
class BajarRespaldoTest {

    private static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");
    private static final Path TRABAJO = Path.of("/tmp/rdmotors");
    /** 9 de la mañana del 23 de septiembre de 2026 en Colombia. */
    private static final String UNA_MANANA = "2026-09-23T14:00:00Z";
    private static final String NOMBRE = "rdmotors-2026-09-23-090000.dump";

    private Falsos.RelojFijo reloj;
    private Falsos.VolcadorFalso volcador;
    private Falsos.ArchivosFalsos archivos;
    private Falsos.RespaldosEnMemoria respaldos;

    @BeforeEach
    void preparar() {
        reloj = new Falsos.RelojFijo(UNA_MANANA);
        volcador = new Falsos.VolcadorFalso();
        archivos = new Falsos.ArchivosFalsos();
        volcador.archivos = archivos;
        respaldos = new Falsos.RespaldosEnMemoria();
    }

    private BajarRespaldo bajar() {
        return new BajarRespaldo(volcador, archivos, respaldos, TRABAJO, new PoliticaDeRespaldo(7, COLOMBIA), reloj);
    }

    @Test
    @DisplayName("la copia queda lista para entregar, con su nombre del día, y la bajada queda registrada")
    void laCopiaQuedaLista() {
        var ruben = ActoresDePrueba.administrador();

        CopiaParaBajar copia = bajar().ejecutar(ruben);

        assertThat(volcador.volcados).containsExactly(TRABAJO.resolve(NOMBRE));
        assertThat(copia.nombre()).isEqualTo(NOMBRE);
        assertThat(copia.bytes()).isEqualTo(4_096);
        assertThat(archivos.existe(copia.archivo())).as("el archivo está ahí esperando a que lo manden").isTrue();

        Respaldo registro = copia.registro();
        assertThat(registro.getEstado()).isEqualTo(EstadoRespaldo.HECHO);
        assertThat(registro.getArchivo()).as("el NOMBRE, no una ruta de un disco que ya no es nuestro")
                .isEqualTo(NOMBRE);
        assertThat(registro.getOrigen()).isEqualTo(OrigenRespaldo.A_MANO);
        assertThat(registro.getPedidoPorId()).isEqualTo(ruben.id());
        assertThat(respaldos.todos()).containsExactly(registro);
    }

    @Test
    @DisplayName("SI FALLA, QUIEN LA PIDIÓ SE ENTERA: no se entrega media copia")
    void siFallaNoSeEntregaNada() {
        // Un archivo truncado que parece un respaldo es la peor de todas las respuestas: el dueño se lo guarda
        // creyendo que tiene su negocio adentro, y se entera el día que hace falta.
        volcador.falla = "pg_dump terminó con error 1: server version mismatch";

        assertThatThrownBy(() -> bajar().ejecutar(ActoresDePrueba.administrador()))
                .isInstanceOf(RespaldoFallidoException.class)
                .hasMessageContaining("server version mismatch");

        assertThat(archivos.borrados).as("lo que quedó a medias se borra")
                .containsExactly(TRABAJO.resolve(NOMBRE));
        assertThat(archivos.existe(TRABAJO.resolve(NOMBRE))).isFalse();
    }

    @Test
    @DisplayName("Y AUNQUE FALLE, LA FILA QUEDA: es lo único que hace que el dueño se entere después")
    void elFalloQuedaEscrito() {
        volcador.falla = "El disco está lleno";

        assertThatThrownBy(() -> bajar().ejecutar(ActoresDePrueba.administrador()))
                .isInstanceOf(RespaldoFallidoException.class);

        // Por esto el caso de uso no abre transacción: si la abriera, al lanzar se desharía justo este registro.
        Respaldo registro = respaldos.todos().getFirst();
        assertThat(registro.getEstado()).isEqualTo(EstadoRespaldo.FALLO);
        assertThat(registro.getError()).contains("El disco está lleno");
        assertThat(registro.getBytes()).isNull();
        assertThat(registro.getArchivo()).isEqualTo(NOMBRE);
    }

    @Test
    @DisplayName("si ni la carpeta de trabajo se puede crear, también queda escrito")
    void siNoHayNiCarpeta() {
        archivos.carpetaQueFalla = TRABAJO;

        assertThatThrownBy(() -> bajar().ejecutar(ActoresDePrueba.administrador()))
                .isInstanceOf(RespaldoFallidoException.class);

        assertThat(respaldos.todos()).hasSize(1);
        assertThat(respaldos.todos().getFirst().getError()).contains("No se pudo crear la carpeta");
        assertThat(volcador.volcados).as("ni se intentó volcar").isEmpty();
    }

    @Test
    @DisplayName("DOS COPIAS EN EL MISMO SEGUNDO no se pisan el archivo: la segunda busca un nombre libre")
    void dosEnElMismoSegundo() {
        // Pasó de verdad: tres clics seguidos dejaron tres filas apuntando a una sola copia.
        var ruben = ActoresDePrueba.administrador();

        CopiaParaBajar primera = bajar().ejecutar(ruben);
        CopiaParaBajar segunda = bajar().ejecutar(ruben);
        CopiaParaBajar tercera = bajar().ejecutar(ruben);

        assertThat(List.of(primera.nombre(), segunda.nombre(), tercera.nombre())).doesNotHaveDuplicates();
        assertThat(segunda.nombre()).isEqualTo("rdmotors-2026-09-23-090000-2.dump");
        assertThat(tercera.nombre()).isEqualTo("rdmotors-2026-09-23-090000-3.dump");
    }

    @Test
    @DisplayName("bajarse la base es del administrador: el cajero no puede, y su intento no deja fila")
    void esDelAdministrador() {
        assertThatThrownBy(() -> bajar().ejecutar(ActoresDePrueba.cajero()))
                .isInstanceOf(NoPermitidoException.class);

        // Que un cajero lo intente no es un respaldo fallido: no llegó a intentarse nada.
        assertThat(respaldos.todos()).isEmpty();
        assertThat(volcador.volcados).isEmpty();
    }
}
