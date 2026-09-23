package com.workshopmanagement.rdmotors.caja.dominio;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.Motivo;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Un gasto del negocio: el flete, el almuerzo, el arriendo (spec 0006, H1 y H10).
 *
 * <p><b>Dos clases de gasto, y la diferencia es de dónde salió la plata:</b>
 * <ul>
 *   <li><b>Del cajón</b>: se pagó con los billetes del turno abierto. Queda en ese turno y resta de lo
 *       que debería haber al cerrarlo.</li>
 *   <li><b>Por fuera</b>: el arriendo por Nequi, la luz que paga el dueño de su bolsillo. No toca ningún
 *       arqueo, pero es gasto del negocio: sin él, la utilidad neta sale inflada (decisión 5).</li>
 * </ul>
 *
 * <p><b>Del mes</b> (spec 0007, RF-008a): el arriendo, la nómina. No cambia la caja; cambia cómo lo lee el
 * reporte de resultados, que lo reparte entre los días de su mes o lo muestra solo en el mes. El mes es el de
 * su fecha.
 *
 * <p><b>No se edita.</b> Si quedó mal, se anula con motivo y se registra de nuevo: el rastro queda solo.
 */
@Entity
@Table(name = "gasto")
@Getter
public class Gasto {

    public static final String TIPO_AUDITORIA = "GASTO";
    static final int LARGO_DESCRIPCION = 300;

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** {@code Long} y no {@code long}: nulo es lo que le dice a Spring Data que el gasto es nuevo. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false, updatable = false)
    private CategoriaGasto categoria;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "monto", nullable = false, updatable = false))
    private Dinero monto;

    @Column(name = "descripcion", nullable = false, updatable = false, length = LARGO_DESCRIPCION)
    private String descripcion;

    @Column(name = "del_cajon", nullable = false, updatable = false)
    private boolean delCajon;

    @Column(name = "del_mes", nullable = false, updatable = false)
    private boolean delMes;

    /** Solo en los del cajón: el turno de cuyo arqueo sale. */
    @Column(name = "turno_id", updatable = false)
    private UUID turnoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pago", nullable = false, updatable = false, length = 15)
    private FormaPago formaPago;

    /** Solo en transferencias: desde qué cuenta salió. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_id", updatable = false)
    private CuentaPago cuenta;

    /** El día del gasto. En uno del cajón es el día en que se registró. */
    @Column(name = "fecha", nullable = false, updatable = false)
    private LocalDate fecha;

    @Column(name = "registrado_por_id", nullable = false, updatable = false)
    private UUID registradoPorId;

    @Column(name = "registrado_en", nullable = false, updatable = false)
    private Instant registradoEn;

    /** La llave contra el doble clic: nace al abrir el formulario, y un reintento llega con la misma. */
    @Column(name = "llave_idempotencia", nullable = false, updatable = false)
    private UUID llaveIdempotencia;

    @Column(name = "anulado_en")
    private Instant anuladoEn;

    @Column(name = "anulado_por_id")
    private UUID anuladoPorId;

    @Column(name = "motivo_anulacion", length = Motivo.LARGO_MAXIMO)
    private String motivoAnulacion;

    protected Gasto() {
        // JPA
    }

    /**
     * Un gasto pagado con plata del cajón. Que el turno esté abierto lo exige quien lo registra, con el
     * turno bloqueado: aquí no se puede saber.
     */
    public static Gasto delCajon(CategoriaGasto categoria, Dinero monto, String descripcion, boolean delMes,
                                 UUID turnoId, LocalDate hoy, UUID usuarioId, UUID llave, Instant cuando) {
        if (turnoId == null) {
            throw new SinTurnoAbiertoException(
                    "No hay un turno abierto. Ábrelo en Vender, o registra el gasto como pagado por fuera del cajón.");
        }
        Gasto gasto = nuevo(categoria, monto, descripcion, hoy, usuarioId, llave, cuando);
        gasto.delMes = delMes;
        gasto.delCajon = true;
        gasto.turnoId = turnoId;
        gasto.formaPago = FormaPago.EFECTIVO;
        return gasto;
    }

    /**
     * Un gasto que no salió del cajón (decisión 5): en efectivo por fuera, o por transferencia desde una
     * cuenta, como en las compras.
     *
     * @param fecha el día en que se pagó; no puede ser después de {@code hoy}
     */
    public static Gasto porFuera(CategoriaGasto categoria, Dinero monto, String descripcion, boolean delMes,
                                 FormaPago formaPago, CuentaPago cuenta, LocalDate fecha, LocalDate hoy,
                                 UUID usuarioId, UUID llave, Instant cuando) {
        if (formaPago == null) {
            throw new ReglaDeNegocioException("Di cómo se pagó: en efectivo o por transferencia");
        }
        if (formaPago == FormaPago.TRANSFERENCIA && cuenta == null) {
            throw new ReglaDeNegocioException("Una transferencia necesita la cuenta desde la que salió");
        }
        if (formaPago == FormaPago.EFECTIVO && cuenta != null) {
            throw new ReglaDeNegocioException("Un gasto en efectivo no lleva cuenta");
        }
        if (cuenta != null && !cuenta.isActiva()) {
            throw new ReglaDeNegocioException("La cuenta " + cuenta.getNombre() + " está desactivada: elige otra");
        }
        if (fecha == null) {
            throw new ReglaDeNegocioException("Falta la fecha del gasto");
        }
        if (fecha.isAfter(hoy)) {
            throw new ReglaDeNegocioException("La fecha del gasto no puede ser después de hoy");
        }
        Gasto gasto = nuevo(categoria, monto, descripcion, fecha, usuarioId, llave, cuando);
        gasto.delMes = delMes;
        gasto.delCajon = false;
        gasto.formaPago = formaPago;
        gasto.cuenta = cuenta;
        return gasto;
    }

    private static Gasto nuevo(CategoriaGasto categoria, Dinero monto, String descripcion, LocalDate fecha,
                               UUID usuarioId, UUID llave, Instant cuando) {
        if (categoria == null) {
            throw new ReglaDeNegocioException("Elige la categoría del gasto");
        }
        if (!categoria.isActiva()) {
            throw new ReglaDeNegocioException("La categoría «" + categoria.getNombre() + "» está desactivada: elige otra");
        }
        if (monto == null || monto.esCero() || monto.esNegativo()) {
            throw new ReglaDeNegocioException("El monto del gasto tiene que ser mayor a $0");
        }
        String limpia = descripcion == null ? "" : descripcion.trim();
        if (limpia.isEmpty()) {
            throw new ReglaDeNegocioException("Escribe en qué se gastó: queda con el gasto");
        }
        if (limpia.length() > LARGO_DESCRIPCION) {
            throw new ReglaDeNegocioException("La descripción es muy larga: máximo " + LARGO_DESCRIPCION + " caracteres");
        }
        if (usuarioId == null || llave == null) {
            throw new ReglaDeNegocioException("Al gasto le falta quién lo registra o su llave");
        }
        Gasto gasto = new Gasto();
        gasto.id = UUID.randomUUID();
        gasto.categoria = categoria;
        gasto.monto = monto;
        gasto.descripcion = limpia;
        gasto.fecha = fecha;
        gasto.registradoPorId = usuarioId;
        gasto.registradoEn = cuando;
        gasto.llaveIdempotencia = llave;
        return gasto;
    }

    /**
     * Anula el gasto con su motivo (spec 0006, RF-006). Que el turno de un gasto del cajón siga abierto lo
     * exige quien anula, con el turno bloqueado.
     */
    public void anular(String motivo, UUID usuarioId, Instant cuando) {
        if (estaAnulado()) {
            throw new ReglaDeNegocioException("Este gasto ya fue anulado");
        }
        String limpio = Motivo.exigir(motivo);
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

    /** Resta de lo que debería haber en su turno: del cajón y sin anular. */
    public boolean restaDelCajon() {
        return delCajon && !estaAnulado();
    }

    /** Para la auditoría de la anulación. */
    public Map<String, Object> fotografia() {
        Map<String, Object> foto = new LinkedHashMap<>();
        foto.put("categoria", categoria.getNombre());
        foto.put("monto", monto.valor().longValueExact());
        foto.put("descripcion", descripcion);
        foto.put("delCajon", delCajon);
        foto.put("delMes", delMes);
        foto.put("turnoId", turnoId);
        foto.put("formaPago", formaPago.name());
        foto.put("cuenta", cuenta == null ? null : cuenta.getNombre());
        foto.put("fecha", fecha.toString());
        foto.put("anulado", estaAnulado());
        if (estaAnulado()) {
            foto.put("motivoAnulacion", motivoAnulacion);
        }
        return foto;
    }
}
