package com.workshopmanagement.rdmotors.tareas.infraestructura;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.workshopmanagement.rdmotors.correo.aplicacion.MandarCorreosPendientes;

import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — el reloj, cuando el reloj está afuera (spec 0011, decisión 2).
 *
 * <h2>Por qué el reloj se sale del servidor</h2>
 *
 * Hasta ahora las cosas que pasan solas las contaba el propio servidor, con su reloj adentro. Eso funciona mientras
 * el servidor esté prendido. En la nube gratis <b>se apaga cuando nadie lo usa</b>, y un servidor apagado no cuenta
 * las horas: a las 2 de la mañana no hay nadie ahí. Así que el reloj pasa a estar afuera —un servicio gratuito que
 * llama— y estas direcciones son por donde llama.
 *
 * <p>La tarea de cada minuto <b>se queda igual</b>: cuando el servidor está despierto, manda sola. Las dos hacen lo
 * mismo y hacerlo dos veces no duplica nada (cada correo se toma con candado y guarda su estado), así que se puede
 * tener cinturón y tirantes sin pagar nada por ello.
 *
 * <h2>Este paquete no tiene dominio, y está bien</h2>
 *
 * No hay reglas de negocio aquí: el negocio no sabe qué hora es, se la dicen. Lo único que vive en esta carpeta es
 * la puerta y su llave.
 */
@RestController
@RequestMapping("/api/tareas")
@RequiredArgsConstructor
class TareasController {

    private static final Logger LOG = LoggerFactory.getLogger(TareasController.class);

    /** El encabezado donde viaja la llave. */
    static final String CABECERA = "X-RDMOTORS-LLAVE";

    private final LlaveDeTareas llaves;
    private final MandarCorreosPendientes mandar;

    /** Saca los correos que ya se pueden intentar. Repetirla no manda nada dos veces (spec 0011, RF-008). */
    @PostMapping("/correos")
    ResponseEntity<RespuestaTarea> correos(@RequestHeader(value = CABECERA, required = false) String llave) {
        if (!llaves.cuadra(llave)) {
            return noExiste();
        }
        int intentados = mandar.mandar();
        if (intentados > 0) {
            LOG.info("Correos: {} intentados, disparado desde afuera", intentados);
        }
        return ResponseEntity.ok(new RespuestaTarea(intentados));
    }

    /**
     * Sin llave, o con la equivocada, la respuesta es <b>"no existe"</b>, no "no autorizado", y va vacía.
     *
     * <p>"No autorizado" le confirma a quien esté probando que ahí sí hay algo y que solo le falta la llave; le dice
     * dónde seguir insistiendo. "No existe" no le dice nada. Cuesta lo mismo escribirlo.
     */
    private static ResponseEntity<RespuestaTarea> noExiste() {
        return ResponseEntity.notFound().build();
    }

    /** @param intentados cuántos correos se intentaron en esta vuelta */
    record RespuestaTarea(int intentados) {
    }
}
