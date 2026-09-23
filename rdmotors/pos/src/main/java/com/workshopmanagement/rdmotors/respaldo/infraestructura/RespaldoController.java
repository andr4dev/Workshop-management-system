package com.workshopmanagement.rdmotors.respaldo.infraestructura;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.ActorActual;
import com.workshopmanagement.rdmotors.respaldo.aplicacion.BajarRespaldo;
import com.workshopmanagement.rdmotors.respaldo.aplicacion.ConsultarRespaldos;
import com.workshopmanagement.rdmotors.respaldo.aplicacion.CopiaParaBajar;
import com.workshopmanagement.rdmotors.respaldo.aplicacion.EstadoDelRespaldo;
import com.workshopmanagement.rdmotors.respaldo.dominio.EstadoRespaldo;
import com.workshopmanagement.rdmotors.respaldo.dominio.OrigenRespaldo;
import com.workshopmanagement.rdmotors.respaldo.dominio.Respaldo;

import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — el respaldo (spec 0011, RF-010 y RF-011). Todo es del administrador: lo exige el caso de
 * uso, no este controlador.
 */
@RestController
@RequestMapping("/api/respaldos")
@RequiredArgsConstructor
class RespaldoController {

    private static final Logger LOG = LoggerFactory.getLogger(RespaldoController.class);

    private final ConsultarRespaldos consultarRespaldos;
    private final BajarRespaldo bajarRespaldo;

    @GetMapping
    RespuestaEstado estado(@ActorActual Actor actor) {
        return RespuestaEstado.de(consultarRespaldos.estado(actor));
    }

    /**
     * La copia de toda la base, para llevársela. Tarda lo que tarde el motor: es la única petición del sistema que
     * puede demorar.
     *
     * <h2>Por qué es un GET si además escribe una fila</h2>
     *
     * Un GET que escribe incomoda, y aquí es a propósito: así la dirección se puede <b>abrir sola</b> —desde la
     * barra del navegador— el día que la pantalla no cargue. Si el sistema está roto es justo cuando más falta hace
     * poder llevarse los datos, y esa salida de emergencia no debería depender de que la página funcione.
     *
     * <p>La pantalla, en cambio, la pide con código y se queda con la respuesta: así puede mostrar <i>"no se pudo
     * sacar la copia: …"</i> en vez de dejar que el navegador se lleve una página de error con nombre de respaldo.
     *
     * <p>Lo que hace seguro al GET es que la cookie de la sesión es {@code SameSite=Strict}: una página de otro
     * sitio que ponga esta dirección en una etiqueta no lleva la sesión, y sin sesión de administrador esto no hace
     * nada.
     *
     * <h2>Y por qué el archivo pasa por el disco</h2>
     *
     * Mandar directo lo que va saliendo del motor usaría menos memoria, pero si el motor falla a la mitad el
     * navegador ya recibió media respuesta "correcta": quedaría un archivo truncado que parece un respaldo. Primero
     * se saca entera, se comprueba, y solo entonces se manda.
     */
    @GetMapping("/archivo")
    ResponseEntity<StreamingResponseBody> bajar(@ActorActual Actor actor) {
        CopiaParaBajar copia = bajarRespaldo.ejecutar(actor);
        StreamingResponseBody cuerpo = salida -> {
            try (InputStream entrada = Files.newInputStream(copia.archivo())) {
                entrada.transferTo(salida);
            } finally {
                // Pase lo que pase: el servidor no se queda con copias de la base. En la nube esta carpeta es
                // memoria del propio contenedor, así que dejarla llena lo tumba.
                borrar(copia.archivo());
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + copia.nombre() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(copia.bytes())
                .body(cuerpo);
    }

    private static void borrar(Path archivo) {
        try {
            Files.deleteIfExists(archivo);
        } catch (Exception e) {
            // Ya se mandó: que no se pueda borrar el temporal no es un error para quien lo bajó.
            LOG.warn("No se pudo borrar el temporal del respaldo {}: {}", archivo, e.getMessage());
        }
    }

    /**
     * @param hayQueAvisar  lo que enciende el aviso al entrar
     * @param diasSinBajar  hace cuántos días se bajó la última, o {@code null} si nunca se bajó ninguna
     */
    record RespuestaEstado(List<RespuestaRespaldo> copias, RespuestaRespaldo ultimaBuena, boolean hayQueAvisar,
                           Integer diasSinBajar, int diasParaAvisar) {

        static RespuestaEstado de(EstadoDelRespaldo estado) {
            return new RespuestaEstado(estado.copias().stream().map(RespuestaRespaldo::de).toList(),
                    RespuestaRespaldo.de(estado.ultimaBuena()), estado.hayQueAvisar(), estado.diasSinBajar(),
                    estado.diasParaAvisar());
        }
    }

    record RespuestaRespaldo(UUID id, Instant hechoEn, String archivo, Long bytes, long duracionMs,
                             EstadoRespaldo estado, String error, OrigenRespaldo origen) {

        static RespuestaRespaldo de(Respaldo r) {
            return r == null ? null : new RespuestaRespaldo(r.getId(), r.getHechoEn(), r.getArchivo(), r.getBytes(),
                    r.getDuracionMs(), r.getEstado(), r.getError(), r.getOrigen());
        }
    }
}
