package com.workshopmanagement.rdmotors.compartido.infraestructura;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;

/**
 * ADAPTADOR — el reloj de verdad. Su gemelo es {@code Falsos.RelojFijo} en las pruebas: ese par es
 * lo que convierte a {@code Reloj} en un puerto legitimo y no en una interfaz decorativa.
 */
@Component
class RelojSistema implements Reloj {

    /** Toda fecha de negocio se interpreta en hora de Colombia, no en la del servidor. */
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    @Override
    public Instant ahora() {
        return Instant.now();
    }

    @Override
    public LocalDate hoy() {
        return LocalDate.now(ZONA);
    }
}
