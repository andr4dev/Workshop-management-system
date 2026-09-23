package com.workshopmanagement.rdmotors.clientes.dominio.puerto;

import java.util.List;

import com.workshopmanagement.rdmotors.clientes.dominio.FiltroCartera;
import com.workshopmanagement.rdmotors.clientes.dominio.ResumenDeCliente;

/**
 * PUERTO — lo que lee la lista de la Cartera (spec 0008): una fila por cliente con sus cuentas ya hechas. Solo lectura.
 *
 * <p>Aparte de los repositorios porque suma deudas, abonos y aplicaciones de todos los clientes a la vez: en la base
 * es una consulta; cargar las carteras una por una sería leer toda la historia para pintar una lista.
 */
public interface ConsultasDeCartera {

    /** Sin orden: el orden lo decide quien la muestra ({@link ResumenDeCliente#DEL_QUE_MAS_DEBE}). */
    List<ResumenDeCliente> resumen(FiltroCartera filtro);
}
