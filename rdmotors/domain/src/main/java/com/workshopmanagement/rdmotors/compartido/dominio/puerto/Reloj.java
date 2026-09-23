package com.workshopmanagement.rdmotors.compartido.dominio.puerto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * PUERTO — de donde sale "ahora".
 *
 * <p>Parece exagerado tener un puerto para leer la hora, hasta que hay que probar un cierre de
 * caja por turno que cruza la medianoche, o un corte de reporte por fecha. Con
 * {@code Instant.now()} incrustado en el dominio, esas pruebas dependen del reloj de la maquina
 * que las corre y fallan de madrugada.
 *
 * <p>Tiene dos implementaciones reales, que es el requisito para ser puerto: el reloj del sistema
 * en produccion, y uno fijo en las pruebas.
 */
public interface Reloj {

    Instant ahora();

    LocalDate hoy();
}
