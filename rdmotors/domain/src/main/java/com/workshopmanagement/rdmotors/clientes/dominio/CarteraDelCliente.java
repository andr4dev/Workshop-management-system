package com.workshopmanagement.rdmotors.clientes.dominio;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * Lo que un cliente debe y lo que ha abonado, con las reglas de cómo se mueve cada peso (spec 0008, decisión 1).
 *
 * <p>No es una entidad: se arma con las deudas y los abonos de un cliente, <b>con el cliente bloqueado</b>, y es la
 * única que mueve lo abonado de una deuda y lo aplicado de un abono. Así las dos cuentas no se separan.
 *
 * <h2>Las reglas</h2>
 * <ul>
 *   <li><b>Lo que debe</b> es la suma de lo pendiente de sus deudas. <b>Lo que tiene a favor</b>, lo que sus abonos
 *       no le aplicaron a ninguna. Nunca las dos a la vez: si tiene a favor y aparece algo que pagar, se paga.</li>
 *   <li><b>Un abono se reparte de la deuda más vieja a la más nueva</b> (por su fecha y, en el mismo día, por cuál
 *       se registró antes), o primero a la que el cliente escoja. A una deuda nunca se le aplica más de lo que le
 *       falta; lo que sobra pasa a la siguiente. Las partes suman el abono al peso.</li>
 *   <li><b>No se recibe más de lo que debe</b> (RF-015).</li>
 *   <li><b>Anular una venta fiada</b> libera lo que se le había abonado: va a las otras deudas, o queda a favor.</li>
 *   <li><b>Anular un abono</b> devuelve lo que pagó: esas deudas vuelven a deberlo.</li>
 * </ul>
 */
public final class CarteraDelCliente {

    /** Lo más viejo primero; en el mismo día, lo que se registró antes. */
    public static final Comparator<Deuda> ORDEN_DE_PAGO = Comparator.comparing(Deuda::getFecha)
            .thenComparing(Deuda::getRegistradaEn)
            .thenComparing(Deuda::getId);

    private static final Comparator<Abono> ORDEN_DE_LLEGADA = Comparator.comparing(Abono::getRecibidoEn)
            .thenComparingLong(Abono::getNumero);

    private final Cliente cliente;
    private final List<Deuda> deudas;
    private final List<Abono> abonos;

    /** Con todas las deudas y todos los abonos del cliente, anulados incluidos. */
    public CarteraDelCliente(Cliente cliente, List<Deuda> deudas, List<Abono> abonos) {
        if (cliente == null) {
            throw new IllegalArgumentException("Una cartera es de un cliente");
        }
        this.cliente = cliente;
        this.deudas = new ArrayList<>(deudas);
        this.abonos = new ArrayList<>(abonos);
        for (Deuda deuda : this.deudas) exigirDelCliente(deuda.getClienteId());
        for (Abono abono : this.abonos) exigirDelCliente(abono.getClienteId());
        this.deudas.sort(ORDEN_DE_PAGO);
        this.abonos.sort(ORDEN_DE_LLEGADA);
    }

    // ── Lectura ──────────────────────────────────────────────────────────────

    public Cliente cliente() {
        return cliente;
    }

    /** En el orden en que se pagan, anuladas incluidas. */
    public List<Deuda> deudas() {
        return List.copyOf(deudas);
    }

    /** En el orden en que llegaron, anulados incluidos. */
    public List<Abono> abonos() {
        return List.copyOf(abonos);
    }

    public Dinero debe() {
        return deudas.stream().map(Deuda::pendiente).reduce(Dinero.CERO, Dinero::mas);
    }

    public Dinero aFavor() {
        return abonos.stream().map(Abono::sinAplicar).reduce(Dinero.CERO, Dinero::mas);
    }

    /** Las que tienen algo por pagar, de la más vieja a la más nueva. */
    public List<Deuda> pendientes() {
        return deudas.stream().filter(Deuda::tienePendiente).toList();
    }

    /** El día de la deuda pendiente más vieja: desde cuándo debe. {@code null} si está al día. */
    public LocalDate desdeCuando() {
        return pendientes().stream().findFirst().map(Deuda::getFecha).orElse(null);
    }

    // ── Fiar ─────────────────────────────────────────────────────────────────

    /**
     * Una deuda nueva: una venta fiada o el saldo del cuaderno. Si el cliente tenía algo a favor, se le aplica.
     * Queda anotado cuánto debe en total después, para el comprobante.
     */
    public void registrarDeuda(Deuda nueva, Instant cuando) {
        exigirDelCliente(nueva.getClienteId());
        if (nueva.esDelCuaderno() && deudas.stream().anyMatch(Deuda::esDelCuaderno)) {
            throw new ReglaDeNegocioException("A " + cliente.getNombre() + " ya se le cargó el saldo del cuaderno");
        }
        deudas.add(nueva);
        deudas.sort(ORDEN_DE_PAGO);
        aplicarLoQueHayAFavor(cuando);
        nueva.anotarDebeDespues(debe());
    }

    // ── Abonar ───────────────────────────────────────────────────────────────

    /**
     * Recibe un abono y lo reparte (RF-012).
     *
     * @param primeroA la deuda que el cliente dijo que paga ("esto es de la factura 57"), o {@code null}: a lo más
     *                 viejo. Lo que le sobre a esa, sigue a lo más viejo
     */
    public void abonar(Abono abono, UUID primeroA, Instant cuando) {
        exigirDelCliente(abono.getClienteId());
        if (abonos.contains(abono) || !abono.aplicado().esCero()) {
            throw new IllegalStateException("El abono N.º " + abono.getNumero() + " ya se había repartido");
        }
        Dinero debe = debe();
        if (debe.esCero()) {
            throw new ReglaDeNegocioException(cliente.getNombre() + " no debe nada: no hay qué abonar");
        }
        if (abono.getMonto().esMayorQue(debe)) {
            throw new ReglaDeNegocioException(cliente.getNombre() + " debe " + debe.enPesos()
                    + ": no se le puede recibir más");
        }
        Deuda escogida = primeroA == null ? null : deudas.stream().filter(d -> d.getId().equals(primeroA)).findFirst()
                .orElseThrow(() -> new ReglaDeNegocioException("Esa venta no es una deuda de " + cliente.getNombre()));
        if (escogida != null && !escogida.tienePendiente()) {
            throw new ReglaDeNegocioException(conMayuscula(escogida.nombre())
                    + (escogida.estaAnulada() ? " se anuló: ya no se debe" : " ya está pagada"));
        }
        abonos.add(abono);
        if (escogida != null) {
            abono.aplicarA(escogida, menor(abono.getMonto(), escogida.pendiente()), cuando);
        }
        repartir(abono, cuando);
        abono.anotarDebeDespues(debe());
    }

    // ── Deshacer ─────────────────────────────────────────────────────────────

    /**
     * La venta fiada se anuló (RF-027): la deuda deja de deberse, y lo que se le había abonado pasa a las otras deudas
     * del cliente, de la más vieja a la más nueva. Si no hay otra, queda a favor (decisión 6).
     */
    public void anularDeuda(Deuda deuda, UUID anuladaPorId, Instant cuando) {
        exigirDelCliente(deuda.getClienteId());
        if (!deudas.contains(deuda)) {
            throw new IllegalStateException("Esa deuda no está en la cartera de " + cliente.getNombre());
        }
        for (Abono abono : abonos) {
            abono.liberarDe(deuda, cuando);
        }
        deuda.anular(anuladaPorId, cuando);
        aplicarLoQueHayAFavor(cuando);
    }

    /** Un abono mal registrado (RF-017): lo que pagó vuelve a deberse. */
    public void anularAbono(Abono abono, String motivo, UUID anuladoPorId, Instant cuando) {
        exigirDelCliente(abono.getClienteId());
        if (!abonos.contains(abono)) {
            throw new IllegalStateException("Ese abono no está en la cartera de " + cliente.getNombre());
        }
        if (abono.estaAnulado()) {
            throw new ReglaDeNegocioException("Este abono ya fue anulado");
        }
        for (Deuda deuda : deudas) {
            abono.liberarDe(deuda, cuando);
        }
        abono.anular(motivo, anuladoPorId, cuando);
        aplicarLoQueHayAFavor(cuando);
    }

    // ── Repartir ─────────────────────────────────────────────────────────────

    /** Lo que ningún abono ha aplicado, a lo que se debe: nunca se debe y se tiene a favor a la vez. */
    private void aplicarLoQueHayAFavor(Instant cuando) {
        for (Abono abono : abonos) {
            if (!abono.sinAplicar().esCero()) {
                repartir(abono, cuando);
            }
        }
    }

    /** Lo que le queda sin aplicar al abono, de la deuda más vieja a la más nueva. */
    private void repartir(Abono abono, Instant cuando) {
        for (Deuda deuda : deudas) {
            Dinero queda = abono.sinAplicar();
            if (queda.esCero()) {
                return;
            }
            if (deuda.tienePendiente()) {
                abono.aplicarA(deuda, menor(queda, deuda.pendiente()), cuando);
            }
        }
    }

    private void exigirDelCliente(UUID clienteId) {
        if (!cliente.getId().equals(clienteId)) {
            throw new IllegalArgumentException("Eso es de otro cliente, no de " + cliente.getNombre());
        }
    }

    private static Dinero menor(Dinero a, Dinero b) {
        return a.esMayorQue(b) ? b : a;
    }

    private static String conMayuscula(String texto) {
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
