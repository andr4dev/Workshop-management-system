package com.workshopmanagement.rdmotors.caja.dominio;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Un turno de caja: el tiempo durante el que un cajero responde por el cajón (spec 0003, H1).
 *
 * <p><b>Es por turno, no por día</b> (`SPEC_Modelo_Datos.md` §3.6). Puede haber varios en un mismo
 * día y uno puede cruzar la medianoche sin partirse, porque cada venta apunta a su turno por enlace
 * directo y nunca por la fecha. El arqueo tiene que poder señalar a una persona, y un día calendario
 * con dos cajeros no señala a nadie.
 *
 * <p>Arranca con el <b>fondo</b>: la plata con que abre el cajón. Termina con el <b>cierre</b> (spec 0006):
 * el cajero escribe cuánto contó sin haber visto cuánto debería haber, y el turno guarda las dos cifras,
 * la diferencia y de dónde sale cada parte.
 *
 * <p><b>Las cifras del cierre se guardan, no se recalculan.</b> Si mañana se anula una venta de este turno,
 * esa devolución resta en el turno de mañana; el cierre de hoy ya se firmó y dice lo mismo para siempre.
 */
@Entity
@Table(name = "turno_caja")
@Getter
public class TurnoCaja {

    static final int LARGO_OBSERVACIONES = 500;

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "abierto_por_id", nullable = false, updatable = false)
    private UUID abiertoPorId;

    @Column(name = "abierto_en", nullable = false, updatable = false)
    private Instant abiertoEn;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "fondo", nullable = false))
    private Dinero fondo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoTurno estado;

    @Column(name = "cerrado_por_id")
    private UUID cerradoPorId;

    @Column(name = "cerrado_en")
    private Instant cerradoEn;

    // ── El cierre: vacío mientras el turno está abierto ────────────────────────

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "ventas_efectivo"))
    private Dinero ventasEfectivo;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "ventas_transferencia"))
    private Dinero ventasTransferencia;

    /** Lo que se fió en el turno: no entró al cajón (spec 0008). */
    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "ventas_fiado"))
    private Dinero ventasFiado;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "descuentos"))
    private Dinero descuentos;

    /** Lo que los clientes abonaron en efectivo: entra al cajón (spec 0008, RF-013). */
    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "abonos_efectivo"))
    private Dinero abonosEfectivo;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "abonos_transferencia"))
    private Dinero abonosTransferencia;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "devoluciones_efectivo"))
    private Dinero devolucionesEfectivo;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "gastos_cajon"))
    private Dinero gastosCajon;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "retiros"))
    private Dinero retiros;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "compras_cajon"))
    private Dinero comprasCajon;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "esperado"))
    private Dinero esperado;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "contado"))
    private Dinero contado;

    /** {@code contado − esperado}: positiva es sobrante, negativa faltante. */
    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "diferencia"))
    private Dinero diferencia;

    /** Qué pudo pasar, si no cuadró (decisión 2). Se escriben una sola vez. */
    @Column(name = "observaciones", length = LARGO_OBSERVACIONES)
    private String observaciones;

    protected TurnoCaja() {
        // JPA
    }

    private TurnoCaja(Dinero fondo, UUID abiertoPorId, Instant abiertoEn) {
        this.id = UUID.randomUUID();
        this.fondo = fondo;
        this.abiertoPorId = abiertoPorId;
        this.abiertoEn = abiertoEn;
        this.estado = EstadoTurno.ABIERTO;
    }

    /**
     * Abre un turno. Un fondo de $0 vale: hay tiendas que arrancan con el cajón vacío. Uno negativo no
     * existe.
     *
     * <p>Que no haya otro abierto no se puede saber aquí: lo exige quien lo abre, y la base con un
     * índice único.
     */
    public static TurnoCaja abrir(Dinero fondo, UUID usuarioId, Instant cuando) {
        if (fondo == null) {
            throw new ReglaDeNegocioException("Escribe con cuánto arranca el cajón, aunque sea $0");
        }
        if (fondo.esNegativo()) {
            throw new ReglaDeNegocioException("El fondo del cajón no puede ser negativo");
        }
        if (usuarioId == null) {
            throw new ReglaDeNegocioException("Falta quién abre el turno");
        }
        return new TurnoCaja(fondo, usuarioId, cuando);
    }

    /**
     * Quién puede operar en este turno (spec 0004, decisión 2): quien lo abrió, y el administrador, que ayuda en el
     * mostrador o lo cierra si el cajero se fue. Otro cajero no vende, no registra gastos ni retiros, no anula y no
     * cierra en él: el cajero responde por la plata de su turno.
     */
    public void exigirQuePuedaOperar(Actor actor) {
        if (!puedeOperar(actor)) {
            throw new TurnoAjenoException(abiertoPorId);
        }
    }

    /** Si este actor puede mover plata de este cajón: el que lo abrió, o un administrador. */
    public boolean puedeOperar(Actor actor) {
        return actor.esAdministrador() || actor.id().equals(abiertoPorId);
    }

    /** El cajero ve sus turnos; el administrador, todos (spec 0004, §5). */
    public boolean loPuedeVer(Actor actor) {
        return actor.esAdministrador() || actor.id().equals(abiertoPorId);
    }

    public boolean estaAbierto() {
        return estado == EstadoTurno.ABIERTO;
    }

    // ── Cerrar ───────────────────────────────────────────────────────────────

    /** Lo contado tiene que existir y no ser negativo. $0 vale: el cajón puede quedar vacío. */
    public static void exigirContado(Dinero contado) {
        if (contado == null || contado.esNegativo()) {
            throw new ReglaDeNegocioException("Escribe cuánto contaste, aunque sea $0");
        }
    }

    /**
     * Cierra el turno con lo contado (spec 0006, RF-012 y RF-013). Guarda cada parte del arqueo, lo que
     * debería haber, lo contado y la diferencia, tal cual: nada de lo que pase después las cambia.
     *
     * <p>El arqueo lo calcula quien cierra, <b>con el turno bloqueado</b>: así ningún cobro, gasto o retiro
     * puede entrar entre el cálculo y el cierre.
     */
    public void cerrar(ArqueoDeTurno arqueo, Dinero contado, UUID usuarioId, Instant cuando) {
        if (!estaAbierto()) {
            throw new TurnoYaCerradoException(id, cerradoEn, cerradoPorId);
        }
        exigirContado(contado);
        if (usuarioId == null) {
            throw new ReglaDeNegocioException("Falta quién cierra el turno");
        }
        if (arqueo == null || !arqueo.fondo().equals(fondo)) {
            throw new ReglaDeNegocioException("El arqueo no es de este turno");
        }
        this.ventasEfectivo = arqueo.ventasEfectivo();
        this.ventasTransferencia = arqueo.ventasTransferencia();
        this.ventasFiado = arqueo.ventasFiado();
        this.descuentos = arqueo.descuentos();
        this.abonosEfectivo = arqueo.abonosEfectivo();
        this.abonosTransferencia = arqueo.abonosTransferencia();
        this.devolucionesEfectivo = arqueo.devolucionesEfectivo();
        this.gastosCajon = arqueo.gastosCajon();
        this.retiros = arqueo.retiros();
        this.comprasCajon = arqueo.comprasCajon();
        this.esperado = arqueo.esperado();
        this.contado = contado;
        this.diferencia = contado.menos(esperado);
        this.estado = EstadoTurno.CERRADO;
        this.cerradoPorId = usuarioId;
        this.cerradoEn = cuando;
    }

    /** Si el conteo no cuadró con lo que debería haber. */
    public boolean tieneDiferencia() {
        return diferencia != null && !diferencia.esCero();
    }

    /**
     * Qué pudo pasar (decisión 2). Se escriben una sola vez, al cerrar o después desde el historial: una
     * explicación que se puede reescribir no explica nada.
     */
    public void escribirObservaciones(String texto) {
        if (estaAbierto()) {
            throw new ReglaDeNegocioException("Las observaciones se escriben al cerrar el turno");
        }
        if (observaciones != null) {
            throw new ReglaDeNegocioException("Las observaciones de este cierre ya se escribieron: no se cambian");
        }
        String limpio = texto == null ? "" : texto.trim();
        if (limpio.isEmpty()) {
            throw new ReglaDeNegocioException("Escribe qué pudo pasar");
        }
        if (limpio.length() > LARGO_OBSERVACIONES) {
            throw new ReglaDeNegocioException(
                    "Las observaciones son muy largas: máximo " + LARGO_OBSERVACIONES + " caracteres");
        }
        this.observaciones = limpio;
    }

    /** Para la auditoría del cierre con diferencia: las cifras que se firmaron. */
    public Map<String, Object> fotografiaDelCierre() {
        Map<String, Object> foto = new LinkedHashMap<>();
        foto.put("estado", estado.name());
        foto.put("fondo", pesos(fondo));
        foto.put("ventasEfectivo", pesos(ventasEfectivo));
        foto.put("abonosEfectivo", pesos(abonosEfectivo));
        foto.put("devolucionesEfectivo", pesos(devolucionesEfectivo));
        foto.put("gastosCajon", pesos(gastosCajon));
        foto.put("retiros", pesos(retiros));
        foto.put("comprasCajon", pesos(comprasCajon));
        foto.put("esperado", pesos(esperado));
        foto.put("contado", pesos(contado));
        foto.put("diferencia", pesos(diferencia));
        foto.put("cerradoPorId", cerradoPorId);
        return foto;
    }

    private static Long pesos(Dinero dinero) {
        return dinero == null ? null : dinero.valor().longValueExact();
    }
}
