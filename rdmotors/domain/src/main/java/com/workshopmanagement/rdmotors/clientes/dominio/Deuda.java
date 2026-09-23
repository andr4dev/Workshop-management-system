package com.workshopmanagement.rdmotors.clientes.dominio;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.Motivo;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Lo que un cliente debe por una venta fiada, o lo que ya debía en el cuaderno (spec 0008, decisión 1).
 *
 * <p><b>Las partes suman lo fiado:</b> {@code abonado + pendiente = monto}. Lo abonado es la suma de lo que los
 * abonos le aplicaron; lo mueve {@link CarteraDelCliente}, nunca quien la tenga a mano, porque cada peso que entra o
 * sale de una deuda sale o entra de un abono.
 *
 * <p>Una venta anulada anula su deuda: lo que tenía abonado se libera antes (va a las otras deudas o queda a favor),
 * así que una deuda anulada no tiene nada abonado ni pendiente.
 */
@Entity
@Table(name = "deuda")
@Getter
public class Deuda {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** {@code Long} y no {@code long}: nulo es lo que le dice a Spring Data que la deuda es nueva. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "cliente_id", nullable = false, updatable = false)
    private UUID clienteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen", nullable = false, updatable = false, length = 10)
    private OrigenDeuda origen;

    @Column(name = "venta_id", updatable = false)
    private UUID ventaId;

    @Column(name = "numero_venta", updatable = false)
    private Long numeroVenta;

    /** El día de Colombia en que nació: lo más viejo se paga primero. */
    @Column(name = "fecha", nullable = false, updatable = false)
    private LocalDate fecha;

    @Column(name = "registrada_en", nullable = false, updatable = false)
    private Instant registradaEn;

    @Column(name = "registrada_por_id", nullable = false, updatable = false)
    private UUID registradaPorId;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "monto", nullable = false, updatable = false))
    private Dinero monto;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "abonado", nullable = false))
    private Dinero abonado;

    /** Solo la del cuaderno: de dónde sale la cifra. */
    @Column(name = "motivo", updatable = false, length = Motivo.LARGO_MAXIMO)
    private String motivo;

    /** Cuánto debía el cliente en total justo después: el comprobante lo repite al reimprimirlo. */
    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "debe_despues", nullable = false, updatable = false))
    private Dinero debeDespues;

    @Column(name = "anulada_en")
    private Instant anuladaEn;

    @Column(name = "anulada_por_id")
    private UUID anuladaPorId;

    protected Deuda() {
        // JPA
    }

    private Deuda(UUID clienteId, OrigenDeuda origen, LocalDate fecha, Dinero monto, UUID registradaPorId,
                  Instant cuando) {
        if (clienteId == null) {
            throw new ReglaDeNegocioException("Una deuda es de un cliente");
        }
        if (monto == null || monto.esCero() || monto.esNegativo()) {
            throw new ReglaDeNegocioException("Lo que se debe tiene que ser mayor a $0");
        }
        if (fecha == null || registradaPorId == null) {
            throw new ReglaDeNegocioException("A la deuda le falta su fecha o quién la registró");
        }
        this.id = UUID.randomUUID();
        this.clienteId = clienteId;
        this.origen = origen;
        this.fecha = fecha;
        this.monto = monto;
        this.abonado = Dinero.CERO;
        this.registradaPorId = registradaPorId;
        this.registradaEn = cuando;
    }

    /** Lo que quedó fiado de una venta. {@code fecha} es el día de Colombia en que se cobró. */
    public static Deuda porVenta(UUID clienteId, UUID ventaId, long numeroVenta, LocalDate fecha, Dinero fiado,
                                 UUID registradaPorId, Instant cuando) {
        if (ventaId == null) {
            throw new ReglaDeNegocioException("La deuda de una venta dice cuál venta");
        }
        Deuda deuda = new Deuda(clienteId, OrigenDeuda.VENTA, fecha, fiado, registradaPorId, cuando);
        deuda.ventaId = ventaId;
        deuda.numeroVenta = numeroVenta;
        return deuda;
    }

    /**
     * Lo que el cliente ya debía en el cuaderno antes del sistema (RF-028).
     *
     * @param fecha desde cuándo lo debe, según el cuaderno: no puede ser después de hoy
     */
    public static Deuda delCuaderno(UUID clienteId, LocalDate fecha, Dinero monto, String motivo,
                                    UUID registradaPorId, Instant cuando, LocalDate hoy) {
        String motivoLimpio = Motivo.exigir(motivo);
        if (fecha != null && fecha.isAfter(hoy)) {
            throw new ReglaDeNegocioException("La fecha del cuaderno no puede ser después de hoy");
        }
        Deuda deuda = new Deuda(clienteId, OrigenDeuda.CUADERNO, fecha, monto, registradaPorId, cuando);
        deuda.motivo = motivoLimpio;
        return deuda;
    }

    // ── Lectura ──────────────────────────────────────────────────────────────

    /** Lo que falta por pagar. $0 si está pagada o anulada. */
    public Dinero pendiente() {
        return estaAnulada() ? Dinero.CERO : monto.menos(abonado);
    }

    public boolean tienePendiente() {
        return !pendiente().esCero();
    }

    public boolean estaAnulada() {
        return anuladaEn != null;
    }

    public EstadoDeuda estado() {
        if (estaAnulada()) return EstadoDeuda.ANULADA;
        if (abonado.esCero()) return EstadoDeuda.PENDIENTE;
        return abonado.equals(monto) ? EstadoDeuda.PAGADA : EstadoDeuda.ABONADA;
    }

    public boolean esDelCuaderno() {
        return origen == OrigenDeuda.CUADERNO;
    }

    // ── Lo que mueve la cartera ──────────────────────────────────────────────

    /** Una parte de un abono. Nunca más de lo que le falta. */
    void aplicar(Dinero parte) {
        if (estaAnulada()) {
            throw new ReglaDeNegocioException("Esa deuda se anuló: ya no se le abona");
        }
        if (parte.esCero() || parte.esNegativo() || parte.esMayorQue(pendiente())) {
            throw new ReglaDeNegocioException(conMayuscula(nombre()) + " debe " + pendiente().enPesos()
                    + ": no se le pueden aplicar " + parte.enPesos());
        }
        abonado = abonado.mas(parte);
    }

    /** Se anuló el abono que había pagado esa parte, o la deuda misma: vuelve a deberse. */
    void quitar(Dinero parte) {
        if (parte.esNegativo() || parte.esMayorQue(abonado)) {
            throw new IllegalStateException(conMayuscula(nombre()) + " no tiene " + parte.enPesos() + " abonados");
        }
        abonado = abonado.menos(parte);
    }

    /** Lo que tenía abonado ya se liberó: una anulada no tiene ni abonado ni pendiente. */
    void anular(UUID anuladaPorId, Instant cuando) {
        if (estaAnulada()) {
            throw new ReglaDeNegocioException("Esa deuda ya estaba anulada");
        }
        if (!abonado.esCero()) {
            throw new IllegalStateException("Antes de anular " + nombre() + " hay que liberar lo abonado");
        }
        if (anuladaPorId == null) {
            throw new ReglaDeNegocioException("Falta quién anula");
        }
        this.anuladaEn = cuando;
        this.anuladaPorId = anuladaPorId;
    }

    void anotarDebeDespues(Dinero debe) {
        this.debeDespues = debe;
    }

    /** "la venta N.º 41" o "el saldo del cuaderno", para los mensajes. */
    public String nombre() {
        return esDelCuaderno() ? "el saldo del cuaderno" : "la venta N.º " + numeroVenta;
    }

    private static String conMayuscula(String texto) {
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
