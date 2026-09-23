package com.workshopmanagement.rdmotors.correo.dominio.puerto;

import com.workshopmanagement.rdmotors.correo.dominio.AjustesDeCorreo;

/** PUERTO — a quién le llega el resumen del cierre (spec 0010). Una sola fila. */
public interface RepositorioAjustesDeCorreo {

    AjustesDeCorreo obtener();

    AjustesDeCorreo guardar(AjustesDeCorreo ajustes);
}
