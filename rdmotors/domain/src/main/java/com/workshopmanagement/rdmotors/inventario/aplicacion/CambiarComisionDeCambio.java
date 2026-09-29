package com.workshopmanagement.rdmotors.inventario.aplicacion;

import java.util.Map;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;

/**
 * CASO DE USO — si un repuesto paga comisión por cambio de aceite, y cuánto (spec 0015, RF-001).
 *
 * <p>Es del administrador y queda en la auditoría como una corrección de la ficha, con el antes y el después: es lo
 * que decide cuánto se le debe a quien cambia el aceite. Va aparte de {@link ActualizarRepuesto} para que marcar un
 * aceite no obligue a reenviar la ficha entera.
 *
 * <p>Cambiarlo no toca las comisiones que ya se registraron: cada una guarda el monto de su cobro.
 */
@Transactional
public class CambiarComisionDeCambio {

    private final RepositorioVariantes variantes;
    private final RepositorioAuditoria auditoria;
    private final Reloj reloj;

    public CambiarComisionDeCambio(RepositorioVariantes variantes, RepositorioAuditoria auditoria, Reloj reloj) {
        this.variantes = variantes;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /** @param monto por cada cambio; {@code null} para que deje de pagar */
    public Variante ejecutar(UUID varianteId, Dinero monto, Actor actor) {
        actor.exigirAdministrador();
        Variante variante = variantes.buscarParaModificar(varianteId)
                .orElseThrow(() -> new ReglaDeNegocioException("El repuesto no existe"));
        Map<String, Object> antes = variante.fotografiaDeFicha();
        variante.cambiarComisionDeCambio(monto);
        Map<String, Object> despues = variante.fotografiaDeFicha();
        if (!antes.equals(despues)) {
            auditoria.registrar(EventoAuditoria.nuevo(reloj.ahora(), actor.id(), AccionAuditada.CORREGIR_REPUESTO,
                    ActualizarRepuesto.TIPO_AUDITORIA, variante.getId(), antes, despues, null));
        }
        return variantes.guardar(variante);
    }
}
