package com.workshopmanagement.rdmotors.caja.dominio;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * Un gasto del cajón o un retiro es mayor que lo que debería haber en el cajón en ese momento (spec 0006,
 * decisión 3). No se bloquea: casi siempre es un cero de más, pero si hubo un sobrante real la plata sí
 * está. Se pide confirmar.
 *
 * <p>El mensaje no lleva la cifra: la sección Caja ya la muestra en vivo, al lado del botón.
 */
public class MasDeLoQueDeberiaHaberException extends ReglaDeNegocioException {

    public static final String CODIGO = "CONFIRMAR_MONTO";

    public MasDeLoQueDeberiaHaberException() {
        super("Es más de lo que debería haber en el cajón. ¿Seguro?");
    }
}
