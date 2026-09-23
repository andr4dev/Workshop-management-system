package com.workshopmanagement.rdmotors.correo.infraestructura;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.ActorActual;
import com.workshopmanagement.rdmotors.correo.aplicacion.AdministrarCorreos;
import com.workshopmanagement.rdmotors.correo.aplicacion.ConsultarCorreos;
import com.workshopmanagement.rdmotors.correo.aplicacion.EstadoDeLosCorreos;
import com.workshopmanagement.rdmotors.correo.dominio.Correo;
import com.workshopmanagement.rdmotors.correo.dominio.EstadoCorreo;
import com.workshopmanagement.rdmotors.correo.dominio.TipoCorreo;

import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — *Ajustes › Correos* (spec 0010, RF-007 y RF-009). Todo es del administrador: el caso de uso
 * lo exige.
 *
 * <p>Nunca devuelve la llave de Brevo: solo si está puesta y de quién salen los correos (RF-008).
 */
@RestController
@RequestMapping("/api/correos")
@RequiredArgsConstructor
class CorreoController {

    private final ConsultarCorreos consultarCorreos;
    private final AdministrarCorreos administrarCorreos;

    @GetMapping
    RespuestaEstado estado(@ActorActual Actor actor) {
        return RespuestaEstado.de(consultarCorreos.estado(actor));
    }

    @PutMapping("/destinatarios")
    List<String> destinatarios(@RequestBody PeticionDestinatarios peticion, @ActorActual Actor actor) {
        return administrarCorreos.cambiarDestinatarios(peticion.correos(), actor).correos();
    }

    /** Sale en el momento: si no salió, la respuesta dice por qué, sin ser un error de la petición. */
    @PostMapping("/prueba")
    RespuestaCorreo prueba(@ActorActual Actor actor) {
        return RespuestaCorreo.de(administrarCorreos.probar(actor));
    }

    @PostMapping("/{id}/reintento")
    RespuestaCorreo reintentar(@PathVariable UUID id, @ActorActual Actor actor) {
        return RespuestaCorreo.de(administrarCorreos.reintentar(id, actor));
    }

    record PeticionDestinatarios(List<String> correos) {
    }

    /**
     * @param listoParaMandar si la llave y el remitente están puestos
     * @param loQueFalta      qué falta configurar en el servidor, o {@code null}
     */
    record RespuestaEstado(List<String> destinatarios, boolean listoParaMandar, String loQueFalta, String remitente,
                           List<RespuestaCorreo> ultimos) {

        static RespuestaEstado de(EstadoDeLosCorreos e) {
            return new RespuestaEstado(e.destinatarios(), e.listoParaMandar(), e.loQueFalta(), e.remitente(),
                    e.ultimos().stream().map(RespuestaCorreo::de).toList());
        }
    }

    record RespuestaCorreo(UUID id, TipoCorreo tipo, UUID turnoId, List<String> destinatarios, EstadoCorreo estado,
                           int intentos, Instant creadoEn, Instant noAntesDe, Instant ultimoIntentoEn,
                           String ultimoError, Instant enviadoEn) {

        static RespuestaCorreo de(Correo c) {
            return new RespuestaCorreo(c.getId(), c.getTipo(), c.getTurnoId(), c.para().correos(), c.getEstado(),
                    c.getIntentos(), c.getCreadoEn(), c.getNoAntesDe(), c.getUltimoIntentoEn(), c.getUltimoError(),
                    c.getEnviadoEn());
        }
    }
}
