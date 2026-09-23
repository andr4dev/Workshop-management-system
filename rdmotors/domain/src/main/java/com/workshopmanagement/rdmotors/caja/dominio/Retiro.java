package com.workshopmanagement.rdmotors.caja.dominio;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.Motivo;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Plata que sale del cajón sin ser un gasto: el dueño se lleva $100.000 a mediodía (spec 0006, H2).
 *
 * <p><b>No es un gasto</b> y no cuenta como gasto en ningún reporte: no es plata que se gastó, es plata
 * que cambió de bolsillo. Por eso vive en su propia tabla y no en la de gastos con un tipo.
 *
 * <p>Siempre es del turno abierto, y siempre lleva motivo: un retiro sin razón es la forma más fácil de
 * tapar un faltante.
 */
@Entity
@Table(name = "retiro_caja")
@Getter
public class Retiro {

    public static final String TIPO_AUDITORIA = "RETIRO";

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "turno_id", nullable = false, updatable = false)
    private UUID turnoId;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "monto", nullable = false, updatable = false))
    private Dinero monto;

    /** Quién se la llevó o para qué. */
    @Column(name = "motivo", nullable = false, updatable = false, length = Motivo.LARGO_MAXIMO)
    private String motivo;

    @Column(name = "registrado_por_id", nullable = false, updatable = false)
    private UUID registradoPorId;

    @Column(name = "registrado_en", nullable = false, updatable = false)
    private Instant registradoEn;

    @Column(name = "llave_idempotencia", nullable = false, updatable = false)
    private UUID llaveIdempotencia;

    @Column(name = "anulado_en")
    private Instant anuladoEn;

    @Column(name = "anulado_por_id")
    private UUID anuladoPorId;

    @Column(name = "motivo_anulacion", length = Motivo.LARGO_MAXIMO)
    private String motivoAnulacion;

    protected Retiro() {
        // JPA
    }

    public static Retiro registrar(UUID turnoId, Dinero monto, String motivo, UUID usuarioId, UUID llave,
                                   Instant cuando) {
        if (turnoId == null) {
            throw new SinTurnoAbiertoException("No hay un turno abierto. Ábrelo en Vender para registrar un retiro.");
        }
        if (monto == null || monto.esCero() || monto.esNegativo()) {
            throw new ReglaDeNegocioException("El monto del retiro tiene que ser mayor a $0");
        }
        String limpio = motivo == null ? "" : motivo.trim();
        if (limpio.isEmpty()) {
            throw new ReglaDeNegocioException("Escribe el motivo del retiro: quién se la llevó o para qué");
        }
        if (limpio.length() > Motivo.LARGO_MAXIMO) {
            throw new ReglaDeNegocioException("El motivo es muy largo: máximo " + Motivo.LARGO_MAXIMO + " caracteres");
        }
        if (usuarioId == null || llave == null) {
            throw new ReglaDeNegocioException("Al retiro le falta quién lo registra o su llave");
        }
        Retiro retiro = new Retiro();
        retiro.id = UUID.randomUUID();
        retiro.turnoId = turnoId;
        retiro.monto = monto;
        retiro.motivo = limpio;
        retiro.registradoPorId = usuarioId;
        retiro.registradoEn = cuando;
        retiro.llaveIdempotencia = llave;
        return retiro;
    }

    /** Que su turno siga abierto lo exige quien anula, con el turno bloqueado. */
    public void anular(String motivoAnulacion, UUID usuarioId, Instant cuando) {
        if (estaAnulado()) {
            throw new ReglaDeNegocioException("Este retiro ya fue anulado");
        }
        String limpio = Motivo.exigir(motivoAnulacion);
        if (usuarioId == null) {
            throw new ReglaDeNegocioException("Falta el usuario que anula");
        }
        this.motivoAnulacion = limpio;
        this.anuladoPorId = usuarioId;
        this.anuladoEn = cuando;
    }

    public boolean estaAnulado() {
        return anuladoEn != null;
    }

    public Map<String, Object> fotografia() {
        Map<String, Object> foto = new LinkedHashMap<>();
        foto.put("monto", monto.valor().longValueExact());
        foto.put("motivo", motivo);
        foto.put("turnoId", turnoId);
        foto.put("anulado", estaAnulado());
        if (estaAnulado()) {
            foto.put("motivoAnulacion", motivoAnulacion);
        }
        return foto;
    }
}
