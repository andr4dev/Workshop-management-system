package com.workshopmanagement.rdmotors.clientes.aplicacion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.workshopmanagement.rdmotors.clientes.dominio.Abono;
import com.workshopmanagement.rdmotors.clientes.dominio.AplicacionAbono;
import com.workshopmanagement.rdmotors.clientes.dominio.CarteraDelCliente;
import com.workshopmanagement.rdmotors.clientes.dominio.Deuda;
import com.workshopmanagement.rdmotors.clientes.dominio.EstadoDeuda;
import com.workshopmanagement.rdmotors.clientes.dominio.VentaDeLaDeuda;
import com.workshopmanagement.rdmotors.clientes.dominio.OrigenDeuda;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.Persona;

/**
 * La ficha de un cliente en la Cartera (spec 0008, RF-019): sus datos, lo que debe y desde cuándo, cada venta fiada con
 * su estado y los abonos que le aplicaron, y la línea de tiempo de abonos con quién los recibió.
 *
 * <p>Todo sale de {@link CarteraDelCliente}: la ficha no suma por su cuenta. {@code debe} es la suma de lo pendiente de
 * las deudas, al peso.
 *
 * @param deudas de la más reciente a la más vieja, anuladas incluidas
 * @param abonos de la más reciente a la más vieja, anulados incluidos
 */
public record FichaCliente(ClienteEncontrado cliente, Dinero debe, Dinero aFavor, LocalDate desde, Dinero fiadoTotal,
                           Dinero pagadoTotal, List<DeudaDeLaFicha> deudas, List<AbonoDeLaFicha> abonos) {

    /** Lo que un abono le aplicó a una deuda, visto desde la deuda. {@code vigente} en falso: se anuló o se movió. */
    public record ParteDeAbono(UUID abonoId, long numero, Instant recibidoEn, FormaPago forma, Dinero monto,
                               boolean vigente) {
    }

    /**
     * Un producto fiado, una venta fiada entera (las de antes que no se partieron) o el saldo del cuaderno.
     * {@code abonado + pendiente = monto}.
     *
     * @param lineaVentaId el renglón de la venta si es la deuda de un producto (spec 0016); nulo si no
     * @param descripcion  el nombre del producto al fiarlo; nulo si no es de un producto
     * @param venta        lo que se llevó en esa venta; nulo en el saldo del cuaderno
     */
    public record DeudaDeLaFicha(UUID id, OrigenDeuda origen, UUID ventaId, Long numeroVenta, UUID lineaVentaId,
                                 Integer posicion, String descripcion, LocalDate fecha, Dinero monto, Dinero abonado,
                                 Dinero pendiente, EstadoDeuda estado, String motivo, Persona registradaPor,
                                 Instant anuladaEn, List<ParteDeAbono> abonos, VentaDeLaDeuda venta) {
    }

    /** Lo que un abono le aplicó a una deuda, visto desde el abono: "a la venta N.º 41". */
    public record ParteAplicada(UUID deudaId, String deuda, Dinero monto, boolean vigente) {
    }

    /**
     * Un abono, con a qué deudas fue.
     *
     * @param sinAplicar lo que no fue a ninguna: queda a favor del cliente
     */
    public record AbonoDeLaFicha(UUID id, long numero, Instant recibidoEn, Dinero monto, FormaPago forma,
                                 String referencia, String nota, UUID turnoId, Persona recibidoPor, Dinero debeDespues,
                                 Instant anuladoEn, Persona anuladoPor, String motivoAnulacion, Dinero sinAplicar,
                                 List<ParteAplicada> aplicaciones) {
    }

    /**
     * @param nombres los de quienes registraron, recibieron o anularon, por id, de una sola consulta
     * @param ventas  lo que se llevó en cada venta fiada, por id de venta, de una sola consulta
     */
    static FichaCliente de(CarteraDelCliente cartera, Dinero debe, Map<UUID, String> nombres,
                           Map<UUID, VentaDeLaDeuda> ventas) {
        List<Deuda> deudas = cartera.deudas();    // ya vienen en el orden en que se pagan
        List<Abono> abonos = cartera.abonos();
        Map<UUID, Deuda> porId = deudas.stream().collect(java.util.stream.Collectors.toMap(Deuda::getId, d -> d));
        // En el orden en que se pagan: dos aplicaciones del mismo abono nacen en el mismo instante, así que el
        // instante no alcanza para ordenarlas y saldrían como las devuelva la base.
        Map<UUID, Integer> ordenDePago = new java.util.HashMap<>();
        for (int i = 0; i < deudas.size(); i++) {
            ordenDePago.put(deudas.get(i).getId(), i);
        }
        List<DeudaDeLaFicha> fichas = deudas.stream()
                .sorted(CarteraDelCliente.ORDEN_DE_PAGO.reversed())
                .map(d -> new DeudaDeLaFicha(d.getId(), d.getOrigen(), d.getVentaId(), d.getNumeroVenta(),
                        d.getLineaVentaId(), d.getPosicion(), d.getDescripcion(), d.getFecha(),
                        d.getMonto(), d.getAbonado(), d.pendiente(), d.estado(), d.getMotivo(),
                        Persona.de(d.getRegistradaPorId(), nombres), d.getAnuladaEn(),
                        abonos.stream().flatMap(a -> a.getAplicaciones().stream()
                                        .filter(ap -> ap.getDeudaId().equals(d.getId()))
                                        .map(ap -> parteDeAbono(a, ap)))
                                .sorted(Comparator.comparing(ParteDeAbono::recibidoEn))
                                .toList(),
                        d.getVentaId() == null ? null : ventas.get(d.getVentaId())))
                .toList();
        List<AbonoDeLaFicha> recibidos = abonos.stream()
                .sorted(Comparator.comparing(Abono::getRecibidoEn).thenComparingLong(Abono::getNumero).reversed())
                .map(a -> new AbonoDeLaFicha(a.getId(), a.getNumero(), a.getRecibidoEn(), a.getMonto(), a.getForma(),
                        a.getReferencia(), a.getNota(), a.getTurnoId(), Persona.de(a.getRecibidoPorId(), nombres),
                        a.getDebeDespues(), a.getAnuladoEn(), Persona.de(a.getAnuladoPorId(), nombres),
                        a.getMotivoAnulacion(), a.sinAplicar(),
                        a.getAplicaciones().stream()
                                .sorted(Comparator.comparingInt(ap -> ordenDePago.getOrDefault(ap.getDeudaId(), 0)))
                                .map(ap -> new ParteAplicada(ap.getDeudaId(),
                                        porId.containsKey(ap.getDeudaId()) ? porId.get(ap.getDeudaId()).etiqueta() : "—",
                                        ap.getMonto(), ap.estaVigente()))
                                .toList()))
                .toList();
        Dinero fiadoTotal = deudas.stream().filter(d -> !d.estaAnulada()).map(Deuda::getMonto)
                .reduce(Dinero.CERO, Dinero::mas);
        Dinero pagadoTotal = abonos.stream().filter(a -> !a.estaAnulado()).map(Abono::getMonto)
                .reduce(Dinero.CERO, Dinero::mas);
        return new FichaCliente(ClienteEncontrado.de(cartera.cliente(), debe), debe, cartera.aFavor(),
                cartera.desdeCuando(), fiadoTotal, pagadoTotal, fichas, recibidos);
    }

    private static ParteDeAbono parteDeAbono(Abono a, AplicacionAbono ap) {
        return new ParteDeAbono(a.getId(), a.getNumero(), a.getRecibidoEn(), a.getForma(), ap.getMonto(), ap.estaVigente());
    }
}
