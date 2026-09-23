package com.workshopmanagement.rdmotors.correo.dominio;

import java.time.Duration;
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
 * Un correo por mandar, o ya mandado (spec 0010).
 *
 * <p><b>Por qué hay una fila y no un envío directo, como en el car-wash.</b> Allá el servidor vive en la nube y
 * siempre tiene internet. Aquí vive en la tienda: si el correo saliera en el instante del cierre y en ese momento
 * no hubiera internet, se perdería en silencio. La fila se escribe en la misma transacción del cierre, y una tarea
 * la manda cuando pueda, reintentando.
 *
 * <p><b>No guarda el cuerpo.</b> Se arma al mandarlo, con las cifras que se firmaron al cerrar: así lleva también
 * las observaciones que se escriben justo después de cerrar (RF-002).
 */
@Entity
@Table(name = "correo")
@Getter
public class Correo {

    public static final int LARGO_ERROR = 1000;

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoCorreo tipo;

    /** El turno del que es el resumen; {@code null} en uno de prueba. */
    @Column(name = "turno_id")
    private UUID turnoId;

    /** A quién va, separados por comas: los que había configurados al encolarlo. */
    @Column(name = "destinatarios", nullable = false, length = 700)
    private String destinatarios;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoCorreo estado;

    @Column(name = "intentos", nullable = false)
    private int intentos;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    /** No se intenta antes de esto: el primer intento espera las observaciones; los siguientes, a internet. */
    @Column(name = "no_antes_de", nullable = false)
    private Instant noAntesDe;

    @Column(name = "ultimo_intento_en")
    private Instant ultimoIntentoEn;

    /** Lo que dijo Brevo, o por qué no se pudo hablar con Brevo. {@code null} si nunca falló. */
    @Column(name = "ultimo_error", length = LARGO_ERROR)
    private String ultimoError;

    @Column(name = "enviado_en")
    private Instant enviadoEn;

    /** El id que Brevo le puso al correo: con él se rastrea en su panel si alguien dice que no le llegó. */
    @Column(name = "id_en_brevo", length = 200)
    private String idEnBrevo;

    protected Correo() {
        // JPA
    }

    private Correo(TipoCorreo tipo, UUID turnoId, Destinatarios para, Instant cuando, Instant noAntesDe) {
        if (!para.hayAlguno()) {
            throw new IllegalArgumentException("Un correo sin destinatarios no se encola");
        }
        this.id = UUID.randomUUID();
        this.tipo = tipo;
        this.turnoId = turnoId;
        this.destinatarios = para.comoTexto();
        this.estado = EstadoCorreo.POR_MANDAR;
        this.creadoEn = cuando;
        this.noAntesDe = noAntesDe;
    }

    /**
     * El resumen de un turno recién cerrado. Sale {@code espera} después del cierre, para que alcance a llevar las
     * observaciones que se escriben en la misma ventana (RF-002).
     */
    public static Correo delCierre(UUID turnoId, Destinatarios para, Instant cerradoEn, Duration espera) {
        if (turnoId == null) {
            throw new IllegalArgumentException("El correo del cierre necesita su turno");
        }
        return new Correo(TipoCorreo.CIERRE_DE_TURNO, turnoId, para, cerradoEn, cerradoEn.plus(espera));
    }

    /** Uno de prueba: sale ya. */
    public static Correo dePrueba(Destinatarios para, Instant cuando) {
        return new Correo(TipoCorreo.PRUEBA, null, para, cuando, cuando);
    }

    public Destinatarios para() {
        return Destinatarios.desdeTexto(destinatarios);
    }

    public boolean sePuedeIntentar(Instant ahora) {
        return estado == EstadoCorreo.POR_MANDAR && !ahora.isBefore(noAntesDe);
    }

    /** Brevo lo aceptó. */
    public void seEnvio(String idEnBrevo, Instant cuando) {
        exigirPorMandar();
        this.intentos++;
        this.ultimoIntentoEn = cuando;
        this.estado = EstadoCorreo.ENVIADO;
        this.enviadoEn = cuando;
        this.idEnBrevo = idEnBrevo;
        this.ultimoError = null;
    }

    /**
     * No salió. Si se arregla sola (sin internet, Brevo caído) vuelve a intentarse más tarde, cada vez esperando más;
     * si no (llave inválida, remitente sin verificar), queda {@link EstadoCorreo#FALLO} hasta que alguien lo reintente.
     */
    public void noSalio(String error, boolean seArreglaSola, PoliticaDeReintentos politica, Instant cuando) {
        exigirPorMandar();
        this.intentos++;
        this.ultimoIntentoEn = cuando;
        this.ultimoError = recortar(error);
        if (seArreglaSola) {
            this.noAntesDe = cuando.plus(politica.esperaTras(intentos));
        } else {
            this.estado = EstadoCorreo.FALLO;
        }
    }

    /**
     * No se pudo ni intentar porque falta configurar Brevo: no cuenta como intento, pero queda dicho por qué espera.
     */
    public void esperaConfiguracion(String motivo, Instant cuando, Duration hastaVolverAMirar) {
        exigirPorMandar();
        this.ultimoError = recortar(motivo);
        this.noAntesDe = cuando.plus(hastaVolverAMirar);
    }

    /** El administrador ya arregló lo que faltaba: vuelve a la cola y sale en el próximo minuto (RF-009). */
    public void reintentar(Instant cuando) {
        if (estado == EstadoCorreo.ENVIADO) {
            throw new com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException(
                    "Ese correo ya salió: no se manda otra vez");
        }
        this.estado = EstadoCorreo.POR_MANDAR;
        this.noAntesDe = cuando;
    }

    private void exigirPorMandar() {
        if (estado != EstadoCorreo.POR_MANDAR) {
            throw new IllegalStateException("El correo " + id + " no está por mandar: está " + estado);
        }
    }

    private static String recortar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.strip();
        return limpio.length() <= LARGO_ERROR ? limpio : limpio.substring(0, LARGO_ERROR);
    }
}
