package com.workshopmanagement.rdmotors.clientes.dominio;

import java.time.Instant;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Cuánto de un abono fue a una deuda (spec 0008, RF-012). <b>No se borra</b>: si el abono o la venta se anulan, se
 * marca anulada, y lo liberado que vuelve a aplicarse a otra deuda es otra aplicación. Así la ficha del cliente puede
 * decir de dónde salió cada peso.
 */
@Entity
@Table(name = "aplicacion_abono")
@Getter
public class AplicacionAbono {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "abono_id", nullable = false, updatable = false)
    private Abono abono;

    @Column(name = "deuda_id", nullable = false, updatable = false)
    private UUID deudaId;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "monto", nullable = false, updatable = false))
    private Dinero monto;

    @Column(name = "aplicada_en", nullable = false, updatable = false)
    private Instant aplicadaEn;

    @Column(name = "anulada_en")
    private Instant anuladaEn;

    protected AplicacionAbono() {
        // JPA
    }

    AplicacionAbono(Abono abono, UUID deudaId, Dinero monto, Instant cuando) {
        this.id = UUID.randomUUID();
        this.abono = abono;
        this.deudaId = deudaId;
        this.monto = monto;
        this.aplicadaEn = cuando;
    }

    public boolean estaVigente() {
        return anuladaEn == null;
    }

    void anular(Instant cuando) {
        this.anuladaEn = cuando;
    }
}
