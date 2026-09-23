package com.workshopmanagement.rdmotors.caja.infraestructura;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.ActorActual;
import com.workshopmanagement.rdmotors.caja.aplicacion.AnularRetiro;
import com.workshopmanagement.rdmotors.caja.aplicacion.RegistrarRetiro;
import com.workshopmanagement.rdmotors.caja.dominio.MovimientoRepetidoException;
import com.workshopmanagement.rdmotors.caja.dominio.Retiro;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioRetiros;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — los retiros del cajón (spec 0006, fase 2). Registrar lleva llave, como un gasto; si
 * es más de lo que debería haber, responde 409 {@code CONFIRMAR_MONTO}.
 */
@RestController
@RequestMapping("/api/retiros")
@RequiredArgsConstructor
class RetiroController {

    private final RegistrarRetiro registrarRetiro;
    private final AnularRetiro anularRetiro;
    private final RepositorioRetiros retiros;

    @PostMapping
    ResponseEntity<RespuestaRetiro> registrar(@Valid @RequestBody PeticionRetiro peticion,
                                              @ActorActual Actor actor) {
        try {
            Retiro retiro = registrarRetiro.ejecutar(peticion.llave(),
                    peticion.monto() == null ? null : Dinero.de(peticion.monto()), peticion.motivo(),
                    peticion.confirmado(), actor);
            return ResponseEntity.status(HttpStatus.CREATED).body(RespuestaRetiro.de(retiro));
        } catch (MovimientoRepetidoException e) {
            return ResponseEntity.ok(RespuestaRetiro.de(retiros.buscarPorLlave(e.getLlave()).orElseThrow()));
        }
    }

    @PostMapping("/{id}/anulacion")
    RespuestaRetiro anular(@PathVariable UUID id, @RequestBody PeticionAnulacion peticion,
                           @ActorActual Actor actor) {
        return RespuestaRetiro.de(anularRetiro.ejecutar(id, peticion.motivo(), actor));
    }

    record PeticionRetiro(@NotNull UUID llave, Long monto, String motivo, boolean confirmado) {
    }

    record PeticionAnulacion(String motivo) {
    }

    record RespuestaRetiro(UUID id, UUID turnoId, long monto, String motivo, Instant registradoEn,
                           UUID registradoPorId, Instant anuladoEn, String motivoAnulacion) {
        static RespuestaRetiro de(Retiro r) {
            return new RespuestaRetiro(r.getId(), r.getTurnoId(), r.getMonto().valor().longValueExact(),
                    r.getMotivo(), r.getRegistradoEn(), r.getRegistradoPorId(), r.getAnuladoEn(),
                    r.getMotivoAnulacion());
        }
    }
}
