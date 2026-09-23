package com.workshopmanagement.rdmotors.clientes.dominio;

import java.time.LocalDate;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * Qué clientes muestra la Cartera (spec 0008, RF-018, RF-021 y RF-022).
 *
 * @param vista     {@link Vista#DEBEN} los que deben hoy; {@link Vista#HISTORIAL} todos los que alguna vez tuvieron
 *                  fiado, también los que están al día
 * @param texto     nombre, cédula o celular; vacío o {@code null}, todos
 * @param modoFecha si se filtra por período, si es por la <b>fecha de la venta</b> ("lo que se fió esta semana") o por
 *                  la <b>fecha del abono</b> ("lo que se cobró hoy")
 * @param desde     el primer día del período, o {@code null}: todo
 * @param hasta     el último día, incluido
 */
public record FiltroCartera(Vista vista, String texto, ModoFecha modoFecha, LocalDate desde, LocalDate hasta) {

    public enum Vista {
        DEBEN,
        HISTORIAL
    }

    public enum ModoFecha {
        VENTA,
        ABONO
    }

    public FiltroCartera {
        vista = vista == null ? Vista.DEBEN : vista;
        texto = texto == null || texto.isBlank() ? null : texto.trim();
        modoFecha = modoFecha == null ? ModoFecha.VENTA : modoFecha;
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new ReglaDeNegocioException("El período está al revés: revisa las fechas");
        }
    }

    public FiltroCartera(Vista vista, String texto) {
        this(vista, texto, ModoFecha.VENTA, null, null);
    }

    public static FiltroCartera losQueDeben() {
        return new FiltroCartera(Vista.DEBEN, null);
    }

    /** Si hay período que filtrar. */
    public boolean tienePeriodo() {
        return desde != null && hasta != null;
    }
}
