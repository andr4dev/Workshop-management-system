package com.workshopmanagement.rdmotors.correo.dominio.puerto;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.workshopmanagement.rdmotors.correo.dominio.Correo;

/** PUERTO — la cola de correos (spec 0010). */
public interface RepositorioCorreos {

    Correo guardar(Correo correo);

    /**
     * Los que ya se pueden intentar, del más viejo al más nuevo, <b>tomados con candado</b>: si dos tareas corrieran a
     * la vez, cada una se queda con los suyos y ninguno sale dos veces por eso.
     */
    List<Correo> porMandar(Instant ahora, int cuantos);

    /** Los últimos, del más nuevo al más viejo, para la pantalla. */
    List<Correo> ultimos(int cuantos);

    Optional<Correo> buscarParaModificar(UUID id);
}
