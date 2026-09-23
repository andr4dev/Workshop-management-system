package com.workshopmanagement.rdmotors.compras.infraestructura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compras.dominio.EstadoCompra;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * LECTURA — de qué factura salió cada movimiento de compra del kardex.
 *
 * <p>El kardex guarda {@code origen_tipo} y {@code origen_id}, no el nombre del proveedor. Para que
 * la ficha diga "JOTAPARTES · FV-4521" en vez de un id, alguien tiene que ir a buscar la compra.
 *
 * <p><b>No es un puerto y no pasa por el dominio.</b> Es solo lectura para pintar una pantalla: no
 * hay regla que proteger ni una segunda implementación que nombrar. Vive en {@code compras} porque
 * las tablas son de compras. Así inventario no aprende cómo está hecha una compra: pide
 * descripciones por id y recibe texto.
 */
@Component
@Transactional(readOnly = true)
public class DocumentosDeCompra {

    @PersistenceContext
    private EntityManager em;

    /** Lo que se muestra de una compra al lado de sus movimientos. */
    public record Documento(String proveedor, String factura, LocalDate fechaDocumento,
                            boolean anulada) {
    }

    /**
     * @param compras          por id de compra
     * @param precioPorEntrada el precio que fijó el renglón de cada movimiento de entrada; no está
     *                         la entrada si ese renglón no cambió el precio
     */
    public record Descripcion(Map<UUID, Documento> compras, Map<UUID, Long> precioPorEntrada) {
    }

    /**
     * Dos consultas para todo el kardex, no una por movimiento.
     *
     * <p><b>El precio se pide por movimiento de entrada, no por compra.</b> Después de una corrección
     * la misma factura tiene varias versiones del mismo renglón, cada una con su entrada. Si un
     * cambio de precio dejó dos versiones con la misma entrada, manda la vigente.
     */
    public Descripcion describir(Collection<UUID> compraIds, Collection<UUID> movimientosDeEntrada) {
        Map<UUID, Documento> documentos = new HashMap<>();
        if (!compraIds.isEmpty()) {
            em.createQuery("""
                            select c.id, pr.nombre, c.numeroFactura, c.fechaDocumento, c.estado
                            from Compra c
                            join c.proveedor pr
                            where c.id in :ids
                            """, Object[].class)
                    .setParameter("ids", compraIds)
                    .getResultList()
                    .forEach(fila -> documentos.put((UUID) fila[0], new Documento(
                            (String) fila[1], (String) fila[2], (LocalDate) fila[3],
                            fila[4] == EstadoCompra.ANULADA)));
        }

        Map<UUID, Long> precios = new HashMap<>();
        if (!movimientosDeEntrada.isEmpty()) {
            Map<UUID, Boolean> vieneDeVigente = new HashMap<>();
            em.createQuery("""
                            select l.movimientoEntradaId, l.precioVenta.monto, l.vigente
                            from LineaCompra l
                            where l.movimientoEntradaId in :entradas
                            """, Object[].class)
                    .setParameter("entradas", movimientosDeEntrada)
                    .getResultList()
                    .forEach(fila -> {
                        UUID entrada = (UUID) fila[0];
                        boolean vigente = (Boolean) fila[2];
                        if (Boolean.TRUE.equals(vieneDeVigente.get(entrada)) && !vigente) {
                            return;   // ya está la versión vigente: una vieja no la pisa
                        }
                        vieneDeVigente.put(entrada, vigente);
                        if (fila[1] == null) {
                            precios.remove(entrada);
                        } else {
                            precios.put(entrada, ((BigDecimal) fila[1]).longValueExact());
                        }
                    });
        }
        return new Descripcion(documentos, precios);
    }
}
