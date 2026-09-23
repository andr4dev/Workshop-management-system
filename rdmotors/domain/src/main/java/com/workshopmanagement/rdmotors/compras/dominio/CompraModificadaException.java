package com.workshopmanagement.rdmotors.compras.dominio;

/**
 * La compra cambió entre que la pantalla la cargó y que se mandó la corrección.
 *
 * <p>No extiende {@code ReglaDeNegocioException} a propósito: no es algo que el administrador haya
 * hecho mal, es un conflicto con otra corrección (409). La salida es volver a abrirla con lo nuevo,
 * no pisar lo que hizo el otro.
 */
public class CompraModificadaException extends RuntimeException {

    public CompraModificadaException() {
        super("La compra cambió mientras la corregías. Vuelve a abrirla para ver lo nuevo.");
    }
}
