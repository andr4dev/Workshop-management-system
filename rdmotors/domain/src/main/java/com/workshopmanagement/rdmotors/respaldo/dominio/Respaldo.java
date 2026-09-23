package com.workshopmanagement.rdmotors.respaldo.dominio;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Una copia de la base (spec 0009, H2; replanteada por el spec 0011). Es el registro de que se bajó —o de que
 * falló—, <b>no la copia misma</b>: el archivo queda en el equipo de quien lo bajó, y de ahí en adelante el sistema
 * no sabe nada de él.
 *
 * <p><b>Por qué se guarda también lo que falló.</b> Un respaldo que falla en silencio es peor que no tenerlo: el
 * dueño cree que está cubierto. La fila con su error es lo que hace que el administrador lo vea al entrar
 * (RF-006).
 *
 * <p><b>Por qué la fila sobrevive al archivo.</b> El sistema no puede saber si el archivo bajado todavía existe en
 * el computador del dueño. Lo único que puede decir con certeza es <b>cuándo fue la última vez que se bajó uno</b>,
 * y eso es lo que enciende el aviso de que hace mucho no se respalda (spec 0011, RF-011).
 */
@Entity
@Table(name = "respaldo")
@Getter
public class Respaldo {

    public static final int LARGO_RUTA = 400;
    public static final int LARGO_ERROR = 1000;

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "hecho_en", nullable = false)
    private Instant hechoEn;

    /** El nombre del archivo que se bajó. Las filas de antes de la V25 guardan la ruta del disco de la tienda. */
    @Column(name = "archivo", nullable = false, length = LARGO_RUTA)
    private String archivo;

    /** Lo que pesó. {@code null} si falló. */
    @Column(name = "bytes")
    private Long bytes;

    @Column(name = "duracion_ms", nullable = false)
    private long duracionMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoRespaldo estado;

    /** Lo que dijo el motor cuando falló, recortado. {@code null} si salió bien. */
    @Column(name = "error", length = LARGO_ERROR)
    private String error;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen", nullable = false, length = 12)
    private OrigenRespaldo origen;

    /** Quién lo pidió, si fue a mano. {@code null} en el automático: no lo pidió nadie. */
    @Column(name = "pedido_por_id")
    private UUID pedidoPorId;

    protected Respaldo() {
        // JPA
    }

    private Respaldo(Instant hechoEn, String archivo, long duracionMs, OrigenRespaldo origen, UUID pedidoPorId) {
        this.id = UUID.randomUUID();
        this.hechoEn = hechoEn;
        this.archivo = archivo;
        this.duracionMs = duracionMs;
        this.origen = origen;
        this.pedidoPorId = pedidoPorId;
    }

    public static Respaldo hecho(Instant cuando, String archivo, long bytes, long duracionMs, OrigenRespaldo origen,
                                 UUID pedidoPorId) {
        Respaldo respaldo = new Respaldo(cuando, archivo, duracionMs, origen, pedidoPorId);
        respaldo.estado = EstadoRespaldo.HECHO;
        respaldo.bytes = bytes;
        return respaldo;
    }

    public static Respaldo fallido(Instant cuando, String archivo, String error, long duracionMs,
                                   OrigenRespaldo origen, UUID pedidoPorId) {
        Respaldo respaldo = new Respaldo(cuando, archivo, duracionMs, origen, pedidoPorId);
        respaldo.estado = EstadoRespaldo.FALLO;
        respaldo.error = recortar(error, LARGO_ERROR);
        return respaldo;
    }

    public boolean salioBien() {
        return estado == EstadoRespaldo.HECHO;
    }

    private static String recortar(String texto, int largo) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.strip();
        return limpio.length() <= largo ? limpio : limpio.substring(0, largo);
    }
}
