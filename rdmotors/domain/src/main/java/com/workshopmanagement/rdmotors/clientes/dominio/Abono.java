package com.workshopmanagement.rdmotors.clientes.dominio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.Motivo;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Plata que el cliente trae para pagar lo que debe (spec 0008, H4). En efectivo entra al cajón del turno; por
 * transferencia, no.
 *
 * <p><b>No dice a qué venta va</b>: lo reparte {@link CarteraDelCliente}, a lo más viejo primero o primero a la que
 * el cliente diga. Cada parte queda como una {@link AplicacionAbono}, y {@code aplicado + sin aplicar = monto}. Lo
 * que queda sin aplicar es lo que el cliente tiene a favor (plan 0008, decisión 7): pasa cuando se anula una venta
 * que ya estaba abonada y no hay otra que pagar.
 *
 * <p>Lleva llave, como un cobro: un doble clic no abona dos veces.
 */
@Entity
@Table(name = "abono")
@Getter
public class Abono {

    public static final String TIPO_AUDITORIA = "ABONO";
    static final int LARGO_REFERENCIA = 100;
    static final int LARGO_NOTA = 300;

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** {@code Long} y no {@code long}: nulo es lo que le dice a Spring Data que el abono es nuevo. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** El del recibo: corrido, sin huecos, como el de las ventas. */
    @Column(name = "numero", nullable = false, updatable = false)
    private long numero;

    @Column(name = "cliente_id", nullable = false, updatable = false)
    private UUID clienteId;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "monto", nullable = false, updatable = false))
    private Dinero monto;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma", nullable = false, updatable = false, length = 15)
    private FormaPago forma;

    /** Solo por transferencia, si se escribió: el número de la transacción. */
    @Column(name = "referencia", updatable = false, length = LARGO_REFERENCIA)
    private String referencia;

    @Column(name = "nota", updatable = false, length = LARGO_NOTA)
    private String nota;

    /** El turno abierto al recibirlo. En efectivo siempre hay uno: es el cajón al que entró. */
    @Column(name = "turno_id", updatable = false)
    private UUID turnoId;

    @Column(name = "recibido_por_id", nullable = false, updatable = false)
    private UUID recibidoPorId;

    @Column(name = "recibido_en", nullable = false, updatable = false)
    private Instant recibidoEn;

    @Column(name = "llave_idempotencia", nullable = false, updatable = false)
    private UUID llaveIdempotencia;

    /** Cuánto quedó debiendo el cliente justo después: el recibo lo repite al reimprimirlo. */
    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "debe_despues", nullable = false, updatable = false))
    private Dinero debeDespues;

    @Column(name = "anulado_en")
    private Instant anuladoEn;

    @Column(name = "anulado_por_id")
    private UUID anuladoPorId;

    @Column(name = "motivo_anulacion", length = Motivo.LARGO_MAXIMO)
    private String motivoAnulacion;

    @OneToMany(mappedBy = "abono", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("aplicadaEn")
    private List<AplicacionAbono> aplicaciones = new ArrayList<>();

    protected Abono() {
        // JPA
    }

    /**
     * @param turnoId el turno abierto, o {@code null} si no hay; en efectivo es obligatorio: sin turno no hay cajón
     */
    public static Abono recibir(long numero, UUID clienteId, Dinero monto, FormaPago forma, String referencia,
                                String nota, UUID turnoId, UUID recibidoPorId, UUID llave, Instant cuando) {
        if (clienteId == null) {
            throw new ReglaDeNegocioException("Un abono es de un cliente");
        }
        if (monto == null || monto.esCero() || monto.esNegativo()) {
            throw new ReglaDeNegocioException("El abono tiene que ser mayor a $0");
        }
        if (forma == null) {
            throw new ReglaDeNegocioException("Di si el abono es en efectivo o por transferencia");
        }
        if (forma == FormaPago.EFECTIVO && turnoId == null) {
            throw new ReglaDeNegocioException("No hay un turno abierto: el efectivo no tiene a qué cajón entrar. "
                    + "Ábrelo en Vender, o recíbelo por transferencia");
        }
        String referenciaLimpia = limpio(referencia);
        if (referenciaLimpia != null && forma != FormaPago.TRANSFERENCIA) {
            throw new ReglaDeNegocioException("La referencia solo aplica a una transferencia");
        }
        exigirLargo(referenciaLimpia, LARGO_REFERENCIA, "La referencia");
        String notaLimpia = limpio(nota);
        exigirLargo(notaLimpia, LARGO_NOTA, "La nota");
        if (recibidoPorId == null || llave == null) {
            throw new ReglaDeNegocioException("Al abono le falta quién lo recibe o su llave");
        }
        Abono abono = new Abono();
        abono.id = UUID.randomUUID();
        abono.numero = numero;
        abono.clienteId = clienteId;
        abono.monto = monto;
        abono.forma = forma;
        abono.referencia = referenciaLimpia;
        abono.nota = notaLimpia;
        abono.turnoId = turnoId;
        abono.recibidoPorId = recibidoPorId;
        abono.llaveIdempotencia = llave;
        abono.recibidoEn = cuando;
        return abono;
    }

    // ── Lectura ──────────────────────────────────────────────────────────────

    public boolean estaAnulado() {
        return anuladoEn != null;
    }

    /** Lo que suma al esperado del cajón de su turno. */
    public boolean entraAlCajon() {
        return !estaAnulado() && forma.entraAlCajon();
    }

    public List<AplicacionAbono> getAplicaciones() {
        return Collections.unmodifiableList(aplicaciones);
    }

    public List<AplicacionAbono> aplicacionesVigentes() {
        return aplicaciones.stream().filter(AplicacionAbono::estaVigente).toList();
    }

    /** La parte que ya fue a alguna deuda. */
    public Dinero aplicado() {
        return aplicacionesVigentes().stream().map(AplicacionAbono::getMonto).reduce(Dinero.CERO, Dinero::mas);
    }

    /** Lo que no fue a ninguna deuda: el saldo a favor que deja este abono. $0 si está anulado. */
    public Dinero sinAplicar() {
        return estaAnulado() ? Dinero.CERO : monto.menos(aplicado());
    }

    // ── Lo que mueve la cartera ──────────────────────────────────────────────

    void aplicarA(Deuda deuda, Dinero parte, Instant cuando) {
        if (estaAnulado()) {
            throw new ReglaDeNegocioException("Ese abono está anulado");
        }
        if (parte.esMayorQue(sinAplicar())) {
            throw new IllegalStateException("Al abono N.º " + numero + " le quedan " + sinAplicar().enPesos()
                    + " sin aplicar, no " + parte.enPesos());
        }
        deuda.aplicar(parte);
        aplicaciones.add(new AplicacionAbono(this, deuda.getId(), parte, cuando));
    }

    /** Anula lo que este abono le había aplicado a esa deuda y lo devuelve: queda sin aplicar. */
    Dinero liberarDe(Deuda deuda, Instant cuando) {
        Dinero liberado = Dinero.CERO;
        for (AplicacionAbono aplicacion : aplicacionesVigentes()) {
            if (aplicacion.getDeudaId().equals(deuda.getId())) {
                deuda.quitar(aplicacion.getMonto());
                aplicacion.anular(cuando);
                liberado = liberado.mas(aplicacion.getMonto());
            }
        }
        return liberado;
    }

    /** Las aplicaciones se anulan con él; quien lo anula devuelve esas partes a sus deudas antes. */
    void anular(String motivo, UUID anuladoPorId, Instant cuando) {
        if (estaAnulado()) {
            throw new ReglaDeNegocioException("Este abono ya fue anulado");
        }
        String limpio = Motivo.exigir(motivo);
        if (anuladoPorId == null) {
            throw new ReglaDeNegocioException("Falta el usuario que anula");
        }
        if (!aplicacionesVigentes().isEmpty()) {
            throw new IllegalStateException("Antes de anular el abono N.º " + numero + " hay que devolver lo aplicado");
        }
        this.motivoAnulacion = limpio;
        this.anuladoPorId = anuladoPorId;
        this.anuladoEn = cuando;
    }

    void anotarDebeDespues(Dinero debe) {
        this.debeDespues = debe;
    }

    /** Para la auditoría de la anulación. */
    public Map<String, Object> fotografia() {
        Map<String, Object> foto = new LinkedHashMap<>();
        foto.put("numero", numero);
        foto.put("monto", monto.valor().longValueExact());
        foto.put("forma", forma.name());
        foto.put("turnoId", turnoId);
        foto.put("anulado", estaAnulado());
        if (estaAnulado()) {
            foto.put("motivoAnulacion", motivoAnulacion);
        }
        return foto;
    }

    private static String limpio(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private static void exigirLargo(String texto, int maximo, String campo) {
        if (texto != null && texto.length() > maximo) {
            throw new ReglaDeNegocioException(campo + " es muy larga: máximo " + maximo + " caracteres");
        }
    }
}
