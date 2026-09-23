package com.workshopmanagement.rdmotors.respaldo.aplicacion;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.respaldo.dominio.OrigenRespaldo;
import com.workshopmanagement.rdmotors.respaldo.dominio.PoliticaDeRespaldo;
import com.workshopmanagement.rdmotors.respaldo.dominio.Respaldo;
import com.workshopmanagement.rdmotors.respaldo.dominio.RespaldoFallidoException;
import com.workshopmanagement.rdmotors.respaldo.dominio.puerto.Archivos;
import com.workshopmanagement.rdmotors.respaldo.dominio.puerto.RepositorioRespaldos;
import com.workshopmanagement.rdmotors.respaldo.dominio.puerto.Volcador;

/**
 * CASO DE USO — el administrador se lleva una copia del negocio (spec 0011, RF-010).
 *
 * <h2>Qué cambió, y por qué</h2>
 *
 * Hasta el spec 0009 esto sacaba una copia de madrugada y la dejaba en una carpeta del computador de la tienda. En
 * la nube el disco del servidor es prestado y se borra en cada reinicio: la pantalla habría mostrado catorce copias
 * sin que existiera ninguna, que es peor que no tener respaldo — nadie busca lo que cree tener.
 *
 * <p>Ahora el archivo <b>solo pasa por el servidor</b>: se saca, se entrega a quien lo pidió y se borra. De la
 * historia del proveedor de la base se encarga el proveedor; de que el dueño pueda llevarse sus datos, esto.
 *
 * <h2>Si falla, la fila se guarda igual</h2>
 *
 * Y para eso <b>esto no abre una transacción</b>. Si la abriera, al fallar el volcado la transacción se deshace y se
 * perdería justo el registro de que falló — que es lo único que hace que el dueño se entere. Cada guardado abre la
 * suya. De paso se evita algo peor: tener una conexión de la base tomada durante todo el volcado, que puede tardar
 * minutos, con un pozo de tres conexiones.
 *
 * <p>Lo que <b>sí</b> cambia respecto al respaldo de madrugada: aquí la excepción <b>sale hacia afuera</b>. Antes no
 * podía salir, porque habría frenado la tarea programada y nadie se habría enterado; ahora hay una persona
 * esperando un archivo, y tiene que saber que no va a llegar. <b>Media copia no se entrega nunca</b>: un archivo
 * truncado que parece un respaldo es la peor de todas las respuestas.
 */
public class BajarRespaldo {

    private final Volcador volcador;
    private final Archivos archivos;
    private final RepositorioRespaldos respaldos;
    private final Path carpetaDeTrabajo;
    private final PoliticaDeRespaldo politica;
    private final Reloj reloj;

    public BajarRespaldo(Volcador volcador, Archivos archivos, RepositorioRespaldos respaldos, Path carpetaDeTrabajo,
                         PoliticaDeRespaldo politica, Reloj reloj) {
        this.volcador = volcador;
        this.archivos = archivos;
        this.respaldos = respaldos;
        this.carpetaDeTrabajo = carpetaDeTrabajo;
        this.politica = politica;
        this.reloj = reloj;
    }

    /**
     * Saca la copia y la deja lista para entregar. <b>Quien la recibe tiene que borrar el archivo</b> cuando termine
     * de mandarlo: aquí no se sabe cuándo acabó de viajar.
     *
     * @throws RespaldoFallidoException si no se pudo sacar; la fila con el motivo queda guardada
     */
    public CopiaParaBajar ejecutar(Actor actor) {
        actor.exigirAdministrador();
        Instant empezo = reloj.ahora();
        // Dos nombres distintos, y la diferencia importa:
        //
        //   nombre   el que ve el dueño en su computador: rdmotors-2026-09-23-164246.dump
        //   destino  dónde se trabaja aquí adentro, que nadie ve nunca
        //
        // Antes eran el mismo, y dos descargas en el mismo segundo elegían el mismo archivo: una pisaba a la otra,
        // o la primera en terminar lo borraba mientras la segunda todavía lo estaba mandando, y al dueño le llegaba
        // una copia truncada que parecía buena. El azar del identificador hace imposible que dos coincidan, y sin
        // preguntar antes si el archivo existe — preguntar y crear después deja el hueco por el que se colaban.
        String nombre = politica.nombreDeArchivo(empezo);
        Path destino = carpetaDeTrabajo.resolve(UUID.randomUUID() + ".dump");
        try {
            archivos.asegurarCarpeta(carpetaDeTrabajo);
            long bytes = volcador.volcar(destino);
            Respaldo respaldo = respaldos.guardar(Respaldo.hecho(empezo, nombre, bytes, tardo(empezo),
                    OrigenRespaldo.A_MANO, actor.id()));
            return new CopiaParaBajar(destino, nombre, bytes, respaldo);
        } catch (RuntimeException e) {
            // Lo que quedó a medias no sirve y confunde a quien mire la carpeta.
            archivos.borrar(destino);
            String motivo = mensajeDe(e);
            respaldos.guardar(Respaldo.fallido(empezo, nombre, motivo, tardo(empezo), OrigenRespaldo.A_MANO,
                    actor.id()));
            throw e instanceof RespaldoFallidoException fallo ? fallo : new RespaldoFallidoException(motivo, e);
        }
    }

    private long tardo(Instant empezo) {
        return reloj.ahora().toEpochMilli() - empezo.toEpochMilli();
    }

    private static String mensajeDe(RuntimeException e) {
        String mensaje = e.getMessage();
        return mensaje == null || mensaje.isBlank() ? e.getClass().getSimpleName() : mensaje;
    }
}
