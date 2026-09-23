package com.workshopmanagement.rdmotors.inventario.dominio;

import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * Por qué categoría se filtra el inventario (spec 0005): todas, una, o los repuestos que no tienen.
 *
 * <p>"Sin categoría" es un filtro de verdad y no la ausencia de filtro: sirve para encontrar los
 * repuestos creados antes de que la categoría fuera obligatoria y corregirlos desde su ficha (RF-012).
 */
public record FiltroCategoria(Modo modo, UUID categoriaId) {

    public enum Modo { TODAS, UNA, SIN }

    public FiltroCategoria {
        if (modo == null) {
            throw new IllegalArgumentException("Falta el modo del filtro de categoría");
        }
        if ((modo == Modo.UNA) != (categoriaId != null)) {
            throw new IllegalArgumentException("Solo el filtro de UNA categoría lleva id");
        }
    }

    public static FiltroCategoria todas() {
        return new FiltroCategoria(Modo.TODAS, null);
    }

    public static FiltroCategoria de(UUID categoriaId) {
        return new FiltroCategoria(Modo.UNA, categoriaId);
    }

    public static FiltroCategoria sinCategoria() {
        return new FiltroCategoria(Modo.SIN, null);
    }

    /** Lo que llega por URL: una categoría, los sin categoría, o nada. Las dos a la vez no tiene sentido. */
    public static FiltroCategoria desde(UUID categoriaId, boolean sinCategoria) {
        if (categoriaId != null && sinCategoria) {
            throw new ReglaDeNegocioException("Filtra por una categoría o por los que no tienen, no por las dos");
        }
        if (sinCategoria) {
            return sinCategoria();
        }
        return categoriaId == null ? todas() : de(categoriaId);
    }

    /** La regla en memoria. La consulta de Postgres la imita, y la integración verifica que coinciden. */
    public boolean admite(Categoria categoria) {
        return switch (modo) {
            case TODAS -> true;
            case SIN -> categoria == null;
            case UNA -> categoria != null && categoria.getId().equals(categoriaId);
        };
    }
}
