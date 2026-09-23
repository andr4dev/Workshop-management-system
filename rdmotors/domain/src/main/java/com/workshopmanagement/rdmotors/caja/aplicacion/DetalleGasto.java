package com.workshopmanagement.rdmotors.caja.aplicacion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.workshopmanagement.rdmotors.caja.dominio.Gasto;
import com.workshopmanagement.rdmotors.caja.dominio.NaturalezaGasto;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.Persona;

/**
 * Un gasto como se ve en la lista y en el turno (spec 0006, RF-007a).
 *
 * @param turnoId solo en los del cajón
 * @param cuenta  nombre de la cuenta, solo en transferencias
 */
public record DetalleGasto(
        UUID id,
        LocalDate fecha,
        Instant registradoEn,
        Persona registradoPor,
        UUID categoriaId,
        String categoria,
        NaturalezaGasto naturaleza,
        Dinero monto,
        String descripcion,
        boolean delCajon,
        boolean delMes,
        UUID turnoId,
        FormaPago formaPago,
        UUID cuentaId,
        String cuenta,
        Instant anuladoEn,
        Persona anuladoPor,
        String motivoAnulacion) {

    /** @param nombres los de quienes lo registraron y anularon, por id (spec 0004, RF-022) */
    static DetalleGasto de(Gasto g, Map<UUID, String> nombres) {
        return new DetalleGasto(g.getId(), g.getFecha(), g.getRegistradoEn(),
                Persona.de(g.getRegistradoPorId(), nombres),
                g.getCategoria().getId(), g.getCategoria().getNombre(), g.getCategoria().getNaturaleza(),
                g.getMonto(), g.getDescripcion(), g.isDelCajon(), g.isDelMes(), g.getTurnoId(), g.getFormaPago(),
                g.getCuenta() == null ? null : g.getCuenta().getId(),
                g.getCuenta() == null ? null : g.getCuenta().getNombre(),
                g.getAnuladoEn(), Persona.de(g.getAnuladoPorId(), nombres), g.getMotivoAnulacion());
    }
}
