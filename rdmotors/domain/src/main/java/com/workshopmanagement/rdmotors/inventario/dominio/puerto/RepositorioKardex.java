package com.workshopmanagement.rdmotors.inventario.dominio.puerto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.workshopmanagement.rdmotors.inventario.dominio.MovimientoKardex;

/**
 * PUERTO — el kardex solo se agrega y se lee. <b>No hay actualizar ni borrar, y es a proposito:</b>
 * la interfaz misma impide corromper el historial. Un repositorio generico con {@code save} y
 * {@code delete} dejaria esa puerta abierta.
 */
public interface RepositorioKardex {

    void agregar(MovimientoKardex movimiento);

    void agregarTodos(List<MovimientoKardex> movimientos);

    /** Del más antiguo al más reciente, por secuencia. */
    List<MovimientoKardex> historialDe(UUID varianteId);

    Optional<MovimientoKardex> buscar(UUID id);

    /**
     * ¿Algo <b>consumió</b> inventario de este repuesto después de esa secuencia? Ventas y ajustes de
     * salida cuentan; las reversiones de compra no, porque devuelven justo lo que entró.
     *
     * <p><b>Una venta anulada no cuenta si se canceló al peso</b> (spec 0003, RF-030): tiene su reversión y
     * el costo promedio es el mismo al salir y al volver. Si entre la venta y su anulación una compra
     * movió el promedio, sí cuenta: esas unidades volvieron a otro promedio del que salieron, y revertir
     * una compra anterior daría un costo equivocado.
     *
     * <p>Es la pregunta que decide si una compra se puede revertir (spec 0002, RF-019).
     */
    boolean huboSalidasDespuesDe(UUID varianteId, long secuencia);

    /** El último movimiento del repuesto antes de esa secuencia: el estado justo antes de una compra. */
    Optional<MovimientoKardex> ultimoAntesDe(UUID varianteId, long secuencia);
}
