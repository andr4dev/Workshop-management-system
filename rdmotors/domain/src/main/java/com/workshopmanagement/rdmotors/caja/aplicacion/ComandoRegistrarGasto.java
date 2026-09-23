package com.workshopmanagement.rdmotors.caja.aplicacion;

import java.time.LocalDate;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * Un gasto por registrar (spec 0006, RF-001).
 *
 * @param llave       nace al abrir el formulario: un doble clic o un reintento llega con la misma
 * @param delCajon    si se pagó con plata del cajón del turno abierto
 * @param formaPago   solo si no salió del cajón: efectivo por fuera o transferencia
 * @param cuentaId    solo en transferencias
 * @param fecha       solo si no salió del cajón; uno del cajón es de hoy
 * @param delMes      un gasto de todo el mes, como el arriendo (spec 0007, RF-008a)
 * @param confirmado  el cajero ya confirmó que sí es más de lo que debería haber (decisión 3)
 */
public record ComandoRegistrarGasto(
        UUID llave,
        UUID categoriaId,
        Dinero monto,
        String descripcion,
        boolean delCajon,
        FormaPago formaPago,
        UUID cuentaId,
        LocalDate fecha,
        boolean delMes,
        boolean confirmado,
        Actor actor) {

    public ComandoRegistrarGasto {
        if (llave == null) throw new ReglaDeNegocioException("Al gasto le falta su llave");
        if (actor == null) throw new ReglaDeNegocioException("Falta el usuario que registra");
    }

    public static ComandoRegistrarGasto delCajon(UUID llave, UUID categoriaId, Dinero monto, String descripcion,
                                                 Actor actor) {
        return new ComandoRegistrarGasto(llave, categoriaId, monto, descripcion, true, null, null, null, false, false,
                actor);
    }

    public static ComandoRegistrarGasto porFuera(UUID llave, UUID categoriaId, Dinero monto, String descripcion,
                                                 FormaPago formaPago, UUID cuentaId, LocalDate fecha,
                                                 Actor actor) {
        return new ComandoRegistrarGasto(llave, categoriaId, monto, descripcion, false, formaPago, cuentaId, fecha,
                false, false, actor);
    }

    /** El mismo gasto, después de que el cajero confirmó que sí es más de lo que debería haber. */
    public ComandoRegistrarGasto conConfirmacion() {
        return new ComandoRegistrarGasto(llave, categoriaId, monto, descripcion, delCajon, formaPago, cuentaId,
                fecha, delMes, true, actor);
    }

    /** El mismo gasto, marcado como de todo el mes. */
    public ComandoRegistrarGasto comoDelMes() {
        return new ComandoRegistrarGasto(llave, categoriaId, monto, descripcion, delCajon, formaPago, cuentaId,
                fecha, true, confirmado, actor);
    }
}
