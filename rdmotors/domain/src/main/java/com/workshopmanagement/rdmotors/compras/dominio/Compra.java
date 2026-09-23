package com.workshopmanagement.rdmotors.compras.dominio;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.Motivo;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Una factura de compra a proveedor.
 *
 * <p><b>Dos fechas, a proposito.</b> {@code fechaDocumento} es la de la factura del proveedor;
 * {@code fechaRegistro} es cuando se capturo en el sistema. Pueden estar a semanas de distancia:
 * llega una factura vieja y se registra hoy. Confundirlas mete <b>compras fantasma en meses ya
 * cerrados</b> — cicatriz literal del car-wash, documentada en su propia skill.
 *
 * <p>El total <b>se suma desde las lineas vigentes</b>, nunca se calcula restando ni se recibe del
 * cliente. Una cifra obtenida por resta hereda todo lo que la resta da por sentado, y cuando la
 * premisa vence el total sigue cuadrando mientras la plata queda mal atribuida.
 *
 * <h2>Corregir no es editar (spec 0002)</h2>
 *
 * Los datos de la factura se corrigen en su lugar: no mueven inventario. Un renglón, en cambio,
 * <b>nunca se sobrescribe</b>: se da de baja y entra uno nuevo en su lugar, y el viejo queda como
 * historia. Anular no borra: marca la compra y deja quién, cuándo y por qué.
 */
@Entity
@Table(name = "compra", indexes = {
        @Index(name = "idx_compra_proveedor", columnList = "proveedor_id, fecha_documento"),
        @Index(name = "idx_compra_fecha_registro", columnList = "fecha_registro")
})
@Getter
public class Compra {

    public static final String TIPO_AUDITORIA = "COMPRA";
    static final int LARGO_MAXIMO_MOTIVO = Motivo.LARGO_MAXIMO;

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Control de concurrencia. La pantalla manda la versión que cargó; si otra corrección se guardó
     * en el medio, no coincide y se rechaza en vez de pisarla. {@code Long} y no {@code long}: nulo
     * es lo que le dice a Spring Data que la compra es nueva.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    /** La de la factura del proveedor. Puede ser vieja. */
    @Column(name = "fecha_documento", nullable = false)
    private LocalDate fechaDocumento;

    /** Cuando se capturo en el sistema. */
    @Column(name = "fecha_registro", nullable = false)
    private Instant fechaRegistro;

    @Column(name = "numero_factura", length = 60)
    private String numeroFactura;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pago", nullable = false, length = 15)
    private FormaPago formaPago;

    /** Solo en transferencias. La base lo exige con un {@code CHECK}, no solo este código. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_id")
    private CuentaPago cuenta;

    /**
     * Se pagó con billetes del cajón (spec 0006, decisión 1): resta de lo que debería haber en el turno
     * {@link #turnoId}. Una compra en efectivo sin marcar la pagó el dueño de su bolsillo o de la caja
     * fuerte, y no toca ningún arqueo.
     */
    @Column(name = "pagada_de_caja", nullable = false)
    private boolean pagadaDeCaja;

    /** Solo en las pagadas con plata del cajón: el turno de cuyo arqueo sale. */
    @Column(name = "turno_id")
    private UUID turnoId;

    @Embedded
    @AttributeOverride(name = "monto", column = @Column(name = "total", nullable = false))
    private Dinero total;

    @Column(name = "registrado_por_id", nullable = false)
    private UUID registradoPorId;

    /**
     * La llave contra el doble registro (spec 0009, RF-009). Nace en la pantalla de compra: un doble clic o un
     * reintento tras un corte llegan con la misma, y se devuelve la compra que ya existe en vez de entrar la
     * mercancía dos veces.
     */
    @Column(name = "llave_idempotencia", nullable = false, updatable = false)
    private UUID llaveIdempotencia;

    /**
     * La última vez que se corrigió o se anuló. Además de informar, garantiza que toda corrección
     * cambie la fila y suba la versión, aunque solo haya cambiado un precio en un renglón.
     */
    @Column(name = "modificada_en")
    private Instant modificadaEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoCompra estado;

    @Column(name = "anulada_en")
    private Instant anuladaEn;

    @Column(name = "anulada_por_id")
    private UUID anuladaPorId;

    @Column(name = "motivo_anulacion", length = LARGO_MAXIMO_MOTIVO)
    private String motivoAnulacion;

    /**
     * Todas: las vigentes y las reemplazadas. Nunca se saca una de la lista: con
     * {@code orphanRemoval} eso la borraría de la base, y el renglón viejo es historia.
     */
    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("posicion")
    private List<LineaCompra> lineas = new ArrayList<>();

    protected Compra() {
        // JPA
    }

    private Compra(Proveedor proveedor, LocalDate fechaDocumento, Instant fechaRegistro,
                   String numeroFactura, FormaPago formaPago, CuentaPago cuenta,
                   UUID registradoPorId, UUID llave) {
        this.id = UUID.randomUUID();
        this.llaveIdempotencia = llave;
        this.proveedor = proveedor;
        this.fechaDocumento = fechaDocumento;
        this.fechaRegistro = fechaRegistro;
        this.numeroFactura = numeroFactura;
        this.formaPago = formaPago;
        this.cuenta = cuenta;
        this.registradoPorId = registradoPorId;
        this.total = Dinero.CERO;
        this.estado = EstadoCompra.VIGENTE;
    }

    /** Sin llave y sin plata del cajón: la forma de todas antes de los specs 0006 y 0009. */
    public static Compra registrar(Proveedor proveedor, LocalDate fechaDocumento,
                                   Instant fechaRegistro, String numeroFactura,
                                   FormaPago formaPago, CuentaPago cuenta,
                                   UUID registradoPorId, List<LineaCompra> lineas) {
        return registrar(proveedor, fechaDocumento, fechaRegistro, numeroFactura, formaPago, cuenta, null,
                registradoPorId, UUID.randomUUID(), lineas);
    }

    /**
     * @param turnoDeCajaId el turno abierto si se pagó con plata del cajón; {@code null} si no. Que esté
     *                      abierto lo exige quien registra, con el turno bloqueado
     * @param llave         la llave contra el doble registro (spec 0009, RF-009); nunca {@code null}
     */
    public static Compra registrar(Proveedor proveedor, LocalDate fechaDocumento,
                                   Instant fechaRegistro, String numeroFactura,
                                   FormaPago formaPago, CuentaPago cuenta, UUID turnoDeCajaId,
                                   UUID registradoPorId, UUID llave, List<LineaCompra> lineas) {
        if (llave == null) {
            throw new ReglaDeNegocioException("A la compra le falta su llave contra el doble registro");
        }
        exigirCabecera(proveedor, fechaDocumento);
        exigirPagoCoherente(formaPago, cuenta);
        if (cuenta != null && !cuenta.isActiva()) {
            throw cuentaDesactivada(cuenta);
        }
        if (turnoDeCajaId != null && formaPago != FormaPago.EFECTIVO) {
            throw new ReglaDeNegocioException("Solo una compra en efectivo se paga con plata del cajón");
        }
        if (lineas == null || lineas.isEmpty()) {
            throw new ReglaDeNegocioException("La compra necesita al menos un renglon");
        }
        Compra compra = new Compra(proveedor, fechaDocumento, fechaRegistro, numeroFactura,
                formaPago, cuenta, registradoPorId, llave);
        compra.pagadaDeCaja = turnoDeCajaId != null;
        compra.turnoId = turnoDeCajaId;
        for (LineaCompra linea : lineas) {
            linea.asignarA(compra, compra.lineas.size());
            compra.lineas.add(linea);
        }
        compra.total = compra.sumarLineasVigentes();
        return compra;
    }

    // ── Corregir ─────────────────────────────────────────────────────────────

    /**
     * La pantalla cargó la compra en {@code versionEsperada}. Si ya no es la actual, otra corrección
     * se guardó en el medio. Una compra que nunca se ha guardado cuenta como versión 0, igual que en
     * la base.
     */
    public void exigirVersion(long versionEsperada) {
        long actual = version == null ? 0L : version;
        if (actual != versionEsperada) {
            throw new CompraModificadaException();
        }
    }

    public void exigirVigente() {
        if (estado == EstadoCompra.ANULADA) {
            throw new ReglaDeNegocioException("Esta compra ya fue anulada: no se puede corregir ni anular otra vez");
        }
    }

    /**
     * Corrige proveedor, fecha, número y forma de pago. <b>No mueve inventario</b>: ninguno de esos
     * datos cambia cuántas unidades entraron ni a qué costo.
     *
     * <p>Una cuenta desactivada solo se acepta si ya era la de esta compra: corregir el número de
     * factura no puede obligar a cambiar una cuenta que se dio de baja después.
     *
     * @return {@code true} si algo cambió
     */
    public boolean corregirDatos(Proveedor nuevoProveedor, LocalDate nuevaFecha, String nuevoNumero,
                                 FormaPago nuevaForma, CuentaPago nuevaCuenta) {
        exigirVigente();
        exigirCabecera(nuevoProveedor, nuevaFecha);
        exigirPagoCoherente(nuevaForma, nuevaCuenta);
        boolean mismaCuenta = idDe(nuevaCuenta) == null ? idDe(cuenta) == null
                : idDe(nuevaCuenta).equals(idDe(cuenta));
        if (nuevaCuenta != null && !nuevaCuenta.isActiva() && !mismaCuenta) {
            throw cuentaDesactivada(nuevaCuenta);
        }
        String numeroLimpio = nuevoNumero == null || nuevoNumero.isBlank() ? null : nuevoNumero.trim();

        boolean cambio = !nuevoProveedor.getId().equals(proveedor.getId())
                || !nuevaFecha.equals(fechaDocumento)
                || !Objects.equals(numeroLimpio, numeroFactura)
                || nuevaForma != formaPago
                || !mismaCuenta;
        this.proveedor = nuevoProveedor;
        this.fechaDocumento = nuevaFecha;
        this.numeroFactura = numeroLimpio;
        this.formaPago = nuevaForma;
        this.cuenta = nuevaCuenta;
        return cambio;
    }

    /**
     * Marca o desmarca que se pagó con plata del cajón (spec 0006, RF-008 y RF-010). Se llama después de
     * {@link #corregirDatos}, con la forma de pago ya corregida.
     *
     * <p><b>Si el turno en que se pagó ya se cerró, ni la marca ni la forma de pago cambian</b> (plan 0006,
     * decisión 8): su arqueo se firmó con esa compra adentro, y moverla dejaría ese cierre descuadrado sin
     * que nadie lo vea. Mientras el turno siga abierto, sí: el cierre tomará lo que quede.
     *
     * @param turnoAbiertoId el turno abierto ahora, bloqueado por quien corrige; {@code null} si no hay
     * @return {@code true} si la marca cambió
     */
    public boolean corregirPagoDeCaja(boolean deCaja, UUID turnoAbiertoId) {
        exigirVigente();
        if (pagadaDeCaja) {
            boolean suTurnoSigueAbierto = turnoId.equals(turnoAbiertoId);
            if (!suTurnoSigueAbierto) {
                if (!deCaja || formaPago != FormaPago.EFECTIVO) {
                    throw new ReglaDeNegocioException("Esta compra se pagó con plata del cajón en un turno que ya se "
                            + "cerró: no se puede cambiar su forma de pago ni desmarcarla. Ese arqueo ya se firmó.");
                }
                return false;
            }
            if (!deCaja) {
                this.pagadaDeCaja = false;
                this.turnoId = null;
                return true;
            }
            if (formaPago != FormaPago.EFECTIVO) {
                throw new ReglaDeNegocioException("Una compra pagada con plata del cajón es en efectivo. "
                        + "Desmarca «con plata del cajón» para pasarla a transferencia.");
            }
            return false;
        }
        if (!deCaja) {
            return false;
        }
        if (formaPago != FormaPago.EFECTIVO) {
            throw new ReglaDeNegocioException("Solo una compra en efectivo se paga con plata del cajón");
        }
        if (turnoAbiertoId == null) {
            throw new ReglaDeNegocioException("No hay un turno abierto: la compra no se puede marcar «con plata del "
                    + "cajón». Abre el turno en Vender o desmárcala.");
        }
        this.pagadaDeCaja = true;
        this.turnoId = turnoAbiertoId;
        return true;
    }

    /** Un renglón nuevo en el lugar de uno vigente. El viejo queda como historia. */
    public void reemplazarLinea(LineaCompra vieja, LineaCompra nueva, Instant cuando) {
        exigirVigente();
        exigirPropia(vieja);
        vieja.darDeBaja(cuando);
        nueva.asignarA(this, vieja.getPosicion());
        lineas.add(nueva);
    }

    public void quitarLinea(LineaCompra vieja, Instant cuando) {
        exigirVigente();
        exigirPropia(vieja);
        vieja.darDeBaja(cuando);
    }

    /** Un renglón que la factura tenía y no se capturó: va al final. */
    public void agregarLinea(LineaCompra nueva) {
        exigirVigente();
        int siguiente = lineas.stream().mapToInt(LineaCompra::getPosicion).max().orElse(-1) + 1;
        nueva.asignarA(this, siguiente);
        lineas.add(nueva);
    }

    /**
     * Cierra una corrección: el total vuelve a sumarse desde los renglones vigentes. Una corrección
     * no puede dejar la compra sin renglones (spec 0002, RF-020): para eso está anular.
     */
    public void cerrarCorreccion(Instant cuando) {
        if (lineasVigentes().isEmpty()) {
            throw new ReglaDeNegocioException(
                    "Una corrección no puede dejar la compra sin renglones. Si no debió registrarse, anúlala.");
        }
        this.total = sumarLineasVigentes();
        this.modificadaEn = cuando;
    }

    // ── Anular ───────────────────────────────────────────────────────────────

    /**
     * La marca como anulada. <b>El inventario lo revierte el caso de uso antes de llamar esto</b>:
     * la compra no sabe mover stock, y no debe poder quedar anulada con el inventario intacto.
     */
    public void anular(String motivo, UUID usuarioId, Instant cuando) {
        exigirVigente();
        this.estado = EstadoCompra.ANULADA;
        this.motivoAnulacion = Motivo.exigir(motivo);
        this.anuladaPorId = usuarioId;
        this.anuladaEn = cuando;
        this.modificadaEn = cuando;
    }

    // ── Lectura ──────────────────────────────────────────────────────────────

    public List<LineaCompra> getLineas() {
        return Collections.unmodifiableList(lineas);
    }

    /** Los renglones que hoy forman la factura, en su orden. */
    public List<LineaCompra> lineasVigentes() {
        return lineas.stream()
                .filter(LineaCompra::isVigente)
                .sorted(Comparator.comparingInt(LineaCompra::getPosicion))
                .toList();
    }

    /**
     * La compra como se ve en el antes y el después de una corrección o una anulación. Solo valores
     * simples: la infraestructura la guarda como JSON sin tener que conocer las entidades.
     */
    public Map<String, Object> fotografia() {
        Map<String, Object> foto = new LinkedHashMap<>();
        foto.put("estado", estado.name());
        foto.put("proveedor", proveedor.getNombre());
        foto.put("fechaDocumento", fechaDocumento.toString());
        foto.put("numeroFactura", numeroFactura);
        foto.put("formaPago", formaPago.name());
        foto.put("cuenta", cuenta == null ? null : cuenta.getNombre());
        foto.put("pagadaDeCaja", pagadaDeCaja);
        foto.put("total", total.valor().longValueExact());
        foto.put("renglones", lineasVigentes().stream().map(LineaCompra::fotografia).toList());
        return foto;
    }

    // ── Reglas internas ──────────────────────────────────────────────────────

    private static void exigirCabecera(Proveedor proveedor, LocalDate fechaDocumento) {
        if (proveedor == null) {
            throw new ReglaDeNegocioException("La compra necesita un proveedor");
        }
        if (fechaDocumento == null) {
            throw new ReglaDeNegocioException("La fecha de la factura es obligatoria");
        }
    }

    /**
     * Una transferencia dice desde qué cuenta salió; el efectivo no sale de ninguna.
     *
     * <p>Una cuenta en una compra en efectivo no se ignora en silencio: es un dato contradictorio,
     * y guardarlo haría que esa compra apareciera en el total de una cuenta de la que no salió
     * nada.
     */
    private static void exigirPagoCoherente(FormaPago formaPago, CuentaPago cuenta) {
        if (formaPago == null) {
            throw new ReglaDeNegocioException("Falta la forma de pago: efectivo o transferencia");
        }
        if (formaPago == FormaPago.TRANSFERENCIA && cuenta == null) {
            throw new ReglaDeNegocioException("Una transferencia necesita la cuenta desde la que salió");
        }
        if (formaPago == FormaPago.EFECTIVO && cuenta != null) {
            throw new ReglaDeNegocioException("Una compra en efectivo no lleva cuenta");
        }
    }

    private static ReglaDeNegocioException cuentaDesactivada(CuentaPago cuenta) {
        return new ReglaDeNegocioException(
                "La cuenta " + cuenta.getNombre() + " está desactivada: elige otra");
    }

    private static UUID idDe(CuentaPago cuenta) {
        return cuenta == null ? null : cuenta.getId();
    }

    private void exigirPropia(LineaCompra linea) {
        // Por id y no por identidad: en JPA la referencia a la compra puede ser un proxy.
        if (linea.getCompra() == null || !id.equals(linea.getCompra().getId()) || !linea.isVigente()) {
            throw new ReglaDeNegocioException("Ese renglón no es de esta compra o ya fue reemplazado");
        }
    }

    /**
     * Suma exacta de los totales de linea vigentes.
     *
     * <p>Cada linea guarda su total en pesos enteros, asi que esta suma es exacta y el total de la
     * compra cuadra al peso con la factura del proveedor — incluso cuando los unitarios tienen
     * decimales infinitos.
     */
    private Dinero sumarLineasVigentes() {
        Dinero acumulado = Dinero.CERO;
        for (LineaCompra linea : lineas) {
            if (linea.isVigente()) {
                acumulado = acumulado.mas(linea.getCostoTotal());
            }
        }
        return acumulado;
    }
}
