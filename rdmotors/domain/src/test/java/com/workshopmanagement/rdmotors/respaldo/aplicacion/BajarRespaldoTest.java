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

        assertThat(volcador.volcados).hasSize(1);
        assertThat(copia.archivo().getParent()).as("se trabaja en la carpeta de paso").isEqualTo(TRABAJO);
        assertThat(copia.archivo()).isEqualTo(volcador.volcados.getFirst());
        assertThat(copia.nombre()).as("pero al dueño le llega con la fecha, no con la ruta interna")
                .isEqualTo(NOMBRE);
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
                .containsExactly(volcador.volcados.getFirst());
        assertThat(archivos.existe(volcador.volcados.getFirst())).isFalse();
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
    @DisplayName("DOS COPIAS EN EL MISMO SEGUNDO TRABAJAN EN ARCHIVOS DISTINTOS, aunque se llamen igual")
    void dosEnElMismoSegundo() {
        // La primera versión de esto buscaba un nombre libre: miraba si el archivo existía y lo creaba después. Dos
        // descargas a la vez pasaban juntas por ese hueco, elegían la misma ruta, y una pisaba a la otra — o la
        // primera en terminar la borraba mientras la segunda seguía mandándola, y al dueño le llegaba media copia
        // con pinta de buena. Se reprodujo con cuatro descargas simultáneas en RespaldoIntegracionTest.
        var ruben = ActoresDePrueba.administrador();

        CopiaParaBajar primera = bajar().ejecutar(ruben);
        CopiaParaBajar segunda = bajar().ejecutar(ruben);
        CopiaParaBajar tercera = bajar().ejecutar(ruben);

        // Lo que de verdad protege al dueño: nunca dos trabajando sobre el mismo archivo.
        assertThat(List.of(primera.archivo(), segunda.archivo(), tercera.archivo())).doesNotHaveDuplicates();

        // El nombre que él ve sí se repite, y está bien: son tres copias del mismo segundo, con los mismos datos.
        // El navegador las guarda como (1) y (2); inventarle sufijos al nombre no le resuelve nada a nadie.
        assertThat(primera.nombre()).isEqualTo(NOMBRE);
        assertThat(segunda.nombre()).isEqualTo(NOMBRE);
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
