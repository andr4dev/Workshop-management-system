package com.workshopmanagement.rdmotors.carga.dominio.puerto;

import com.workshopmanagement.rdmotors.carga.dominio.FacturaLeida;
import com.workshopmanagement.rdmotors.carga.dominio.FacturaNoReconocidaException;

/**
 * PUERTO — sacar los renglones de un archivo (spec 0012, decisión 3).
 *
 * <p>Tres implementaciones desde el primer día: el PDF de Jotapartes, el Excel y el CSV. La pre-carga no sabe de
 * cuál salieron sus renglones, y así un proveedor nuevo es un lector nuevo, no un cambio en la pre-carga.
 */
public interface LectorDeFactura {

    /** Si este lector sabe leer ese archivo. Mira el contenido, no se fía solo del nombre. */
    boolean reconoce(String nombreArchivo, byte[] contenido);

    /** @throws FacturaNoReconocidaException si al leerlo resulta que no tiene el diseño que espera */
    FacturaLeida leer(byte[] contenido);
}
