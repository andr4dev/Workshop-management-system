package com.workshopmanagement.rdmotors.reportes.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import com.workshopmanagement.rdmotors.caja.dominio.NaturalezaGasto;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * Los resultados de un período (spec 0007): las cifras grandes, el día por día, los costos y gastos por categoría y
 * los renglones sin costo.
 *
 * <p><b>Un solo cálculo.</b> Todo sale de {@link #calcular}: la pantalla no suma plata, solo ordena. Las partes suman
 * el total al peso, y cada igualdad tiene su prueba:
 * <pre>
 *   renglones − descuentos          = ventas netas
 *   efectivo + transferencia        = ventas netas
 *   ventas netas − costo vendido − costos adicionales = utilidad bruta
 *   utilidad bruta − gastos         = utilidad operativa
 *   Σ filas (+ la de gastos del mes) = las cifras
 *   Σ por categoría                 = costos adicionales y gastos
 *   Σ repuestos y Σ categorías de repuesto = ventas netas (con el descuento repartido)
 * </pre>
 *
 * <p><b>Cuenta lo cobrado</b> (spec 0014): lo de contado el día de la venta, y lo fiado en la parte que se abona, el día
 * en que entra (ver {@link LoCobrado}). Cada peso cobrado trae su parte de los renglones y de su costo. Una venta fiada
 * se cuenta como venta —en el número de ventas, sus unidades y sus renglones— el día que se termina de pagar. Lo fiado
 * que falta no está aquí: está en la cartera del reporte.
 *
 * <p><b>Ingreso y costo miden lo mismo:</b> los dos salen de los mismos cobros de las mismas ventas. Un renglón sin
 * costo no suma $0: se cuenta aparte, y la utilidad queda sobrestimada (RF-007).
 *
 * @param filaGastosDelMes los gastos del mes que van enteros y cuentan en el período (spec 0014, decisión 5): no son
 *                         de ningún día; {@code null} si no hay
 * @param repuestos cada repuesto vendido, de más a menos ventas netas; la pantalla elige el orden (RF-019)
 * @param categoriasDeRepuesto de más a menos ventas netas, con <i>Sin categoría</i> (RF-021)
 */
public record ResultadosDelPeriodo(Periodo periodo, Agrupacion agrupacion, Cifras cifras,
                                   List<PorCategoria> costosPorCategoria, List<PorCategoria> gastosPorCategoria,
                                   GastosDelMes gastosDelMes, SinCosto sinCosto, List<Fila> filas,
                                   Fila filaGastosDelMes, List<Vendidos> repuestos,
                                   List<Vendidos> categoriasDeRepuesto) {

    /** El nombre de la categoría de los repuestos que no tienen. */
    public static final String SIN_CATEGORIA = "Sin categoría";

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    /**
     * Las cifras del período (RF-004 a RF-014).
     *
     * @param ventas las que se completaron en el período: de contado, o fiadas que se terminaron de pagar
     * @param unidades las de esas ventas
     * @param renglones lo cobrado de los renglones antes del descuento
     * @param ventasConDescuento las ventas cuyo descuento está en {@code descuentos}: también una fiada con descuento de
     *                           la que solo entró un abono, que lleva su parte
     * @param ticketPromedio el total de las ventas que se completaron ÷ cuántas, redondeado al peso; {@code null} sin
     *                       ventas
     * @param efectivo lo cobrado en efectivo: de contado y en abonos
     * @param deAbonos la parte de lo cobrado que vino de abonos a ventas fiadas (spec 0014)
     * @param costoVendido solo de los renglones con costo, en la parte cobrada
     * @param margenBruto porcentaje con un decimal; {@code null} sin ventas netas
     */
    public record Cifras(int ventas, int unidades, Dinero renglones, Dinero descuentos, int ventasConDescuento,
                         Dinero ventasNetas, Dinero ticketPromedio, Dinero efectivo, Dinero transferencia,
                         Dinero deAbonos, Dinero costoVendido, Dinero costosAdicionales, Dinero utilidadBruta, BigDecimal margenBruto,
                         Dinero gastos, Dinero utilidadOperativa, BigDecimal margenOperativo) {
    }

    /** Una categoría de costo o de gasto, con los gastos que la forman (spec 0014, RF-008): su monto es la suma. */
    public record PorCategoria(UUID categoriaId, String categoria, Dinero monto, List<GastoCargado> gastos) {
    }

    /**
     * Un gasto en <i>Ver cálculo</i> (spec 0014, RF-008): cuánto es y cuánto carga en el período.
     *
     * @param cargado     lo que suma en el período: entero, o las cuotas de sus días si se reparte
     * @param dias        si se reparte, cuántos días de su mes caen en el período; 0 si no
     * @param diasDelMes  si se reparte, cuántos días tiene su mes; 0 si no
     */
    public record GastoCargado(UUID id, LocalDate fecha, String descripcion, Dinero monto, Dinero cargado, int dias,
                               int diasDelMes) {
    }

    /**
     * @param incluidos lo que cargaron los gastos del mes en el período: repartidos, o enteros
     * @param fuera lo que quedó fuera: gastos del mes que van enteros, de un mes que el período toca pero no cubre
     */
    public record GastosDelMes(Dinero incluidos, Dinero fuera) {
    }

    /**
     * Los renglones vendidos sin costo conocido (RF-007).
     *
     * @param vendido lo que se cobró por ellos, con el descuento de su venta repartido
     */
    public record SinCosto(int renglones, int unidades, Dinero vendido, List<RepuestoSinCosto> repuestos) {
    }

    public record RepuestoSinCosto(UUID varianteId, String codigo, String nombre, String marca, int unidades,
                                   Dinero vendido) {
    }

    /**
     * Lo vendido de un repuesto, o de una categoría de repuestos (RF-019 a RF-021).
     *
     * <p>Las ventas netas llevan el descuento de cada venta repartido entre sus renglones (decisión 3): por eso suman
     * las ventas netas del período. <b>Si algún renglón se vendió sin costo, la utilidad y el margen quedan en
     * blanco</b>: con un costo a medias, un repuesto parecería más rentable de lo que es.
     *
     * @param id el del repuesto (la variante) o el de la categoría; {@code null} en <i>Sin categoría</i>
     * @param codigo y {@code marca}: solo en un repuesto
     * @param costo la suma de los costos conocidos
     * @param utilidad {@code null} si hay renglones sin costo
     * @param margen porcentaje con un decimal; {@code null} sin utilidad o sin ventas netas
     * @param conPerdida se vendió por debajo de su costo: la utilidad es negativa (RF-020)
     */
    public record Vendidos(UUID id, String codigo, String nombre, String marca, String categoria, int renglones,
                           int unidades, Dinero ventasNetas, Dinero costo, int renglonesSinCosto, Dinero utilidad,
                           BigDecimal margen, boolean conPerdida) {
    }

    /** Un día, o una semana, del día por día (RF-017). */
    public record Fila(LocalDate desde, LocalDate hasta, int ventas, Dinero ventasNetas, Dinero costoVendido,
                       Dinero costosAdicionales, Dinero utilidadBruta, Dinero gastos, Dinero utilidadOperativa,
                       int renglonesSinCosto) {
    }

    /**
     * Un renglón en la parte que trae un cobro (spec 0014), con su parte del descuento ya restada.
     *
     * @param costo    su parte del costo; {@code null} si el renglón no tiene costo
     * @param completa el cobro completó la venta: el renglón y sus unidades cuentan ese día
     */
    private record Vendido(RenglonVendido renglon, LocalDate dia, Dinero neto, Dinero costo, boolean completa) {

        boolean tieneCosto() {
            return costo != null;
        }
    }

    /** Lo que un gasto carga en el período. {@code dia} es {@code null} si va entero en la fila de gastos del mes. */
    private record Cargo(GastoDelPeriodo gasto, LocalDate dia, Dinero monto) {
    }

    /** Los cargos de los gastos, y lo que quedó fuera de los que van enteros en su mes. */
    private record Cargos(List<Cargo> cargos, Dinero fuera) {
    }

    /**
     * @param ventas    las ventas de los cobros, no anuladas; pueden ser de antes del período
     * @param renglones los de esas ventas
     * @param cobros    los de esas ventas hasta el final del período, también los de antes (hacen falta para repartir
     *                  por acumulado); solo cuentan los que caen en el período
     */
    public static ResultadosDelPeriodo calcular(Periodo periodo, List<VentaCobrada> ventas,
                                                List<RenglonVendido> renglones, List<CobroDeVenta> cobros,
                                                List<GastoDelPeriodo> gastos, LocalDate hoy) {
        List<LoCobrado.Cobrado> cobrados = LoCobrado.de(ventas, renglones, cobros).stream()
                .filter(c -> periodo.contiene(c.dia()))
                .toList();
        List<LoCobrado.Cobrado> completas = cobrados.stream().filter(LoCobrado.Cobrado::completa).toList();
        List<Vendido> vendidos = cobrados.stream()
                .flatMap(c -> c.pedazos().stream()
                        .map(p -> new Vendido(p.renglon(), c.dia(), p.neto(), p.costo(), c.completa())))
                .toList();
        Cargos deLosGastos = cargosDe(gastos, periodo, hoy);
        List<Cargo> cargos = deLosGastos.cargos();

        Dinero ventasNetas = suma(cobrados, LoCobrado.Cobrado::monto);
        Dinero costosAdicionales = sumaDeCargos(cargos, NaturalezaGasto.COSTO);
        Dinero gastosDelLocal = sumaDeCargos(cargos, NaturalezaGasto.GASTO);
        Dinero costoVendido = suma(vendidos.stream().filter(Vendido::tieneCosto).toList(), Vendido::costo);
        Dinero utilidadBruta = ventasNetas.menos(costoVendido).menos(costosAdicionales);
        Dinero utilidadOperativa = utilidadBruta.menos(gastosDelLocal);
        Dinero totalDeLasCompletas = suma(completas, c -> c.venta().total());

        Cifras cifras = new Cifras(
                completas.size(),
                vendidos.stream().filter(Vendido::completa).mapToInt(v -> v.renglon().cantidad()).sum(),
                suma(cobrados, LoCobrado.Cobrado::bruto),
                suma(cobrados, LoCobrado.Cobrado::descuento),
                // Las mismas ventas cuyo descuento se sumó: un abono a una fiada con descuento lleva su parte.
                (int) cobrados.stream().filter(c -> !c.descuento().esCero()).map(c -> c.venta().id()).distinct().count(),
                ventasNetas,
                completas.isEmpty() ? null
                        : Dinero.de(totalDeLasCompletas.valor()
                                .divide(BigDecimal.valueOf(completas.size()), 0, RoundingMode.HALF_UP)),
                suma(cobrados, LoCobrado.Cobrado::efectivo),
                suma(cobrados, LoCobrado.Cobrado::transferencia),
                suma(cobrados, LoCobrado.Cobrado::deAbonos),
                costoVendido,
                costosAdicionales,
                utilidadBruta,
                margen(utilidadBruta, ventasNetas),
                gastosDelLocal,
                utilidadOperativa,
                margen(utilidadOperativa, ventasNetas));

        Dinero incluidos = suma(cargos.stream().filter(c -> c.gasto().delMes()).toList(), Cargo::monto);
        return new ResultadosDelPeriodo(periodo, periodo.agrupacion(), cifras,
                porCategoria(cargos, NaturalezaGasto.COSTO), porCategoria(cargos, NaturalezaGasto.GASTO),
                new GastosDelMes(incluidos, deLosGastos.fuera()), sinCostoDe(vendidos),
                filasDe(periodo, cobrados, vendidos, cargos), filaDeGastosDelMes(periodo, cargos),
                agrupados(vendidos, r -> r.varianteId(), false), agrupados(vendidos, r -> r.categoriaId(), true));
    }

    /** Lo vendido agrupado por repuesto o por categoría, de más a menos ventas netas. */
    private static List<Vendidos> agrupados(List<Vendido> vendidos, Function<RenglonVendido, UUID> clave,
                                            boolean porCategoria) {
        Map<UUID, List<Vendido>> grupos = new LinkedHashMap<>();
        for (Vendido v : vendidos) {
            grupos.computeIfAbsent(clave.apply(v.renglon()), id -> new ArrayList<>()).add(v);
        }
        return grupos.entrySet().stream()
                .map(grupo -> {
                    List<Vendido> delGrupo = grupo.getValue();
                    RenglonVendido primero = delGrupo.getFirst().renglon();
                    Dinero ventasNetas = suma(delGrupo, Vendido::neto);
                    Dinero costo = suma(delGrupo.stream().filter(Vendido::tieneCosto).toList(), Vendido::costo);
                    int sinCosto = distintos(delGrupo.stream().filter(v -> !v.tieneCosto()).toList()).size();
                    Dinero utilidad = sinCosto > 0 ? null : ventasNetas.menos(costo);
                    String categoria = primero.categoriaId() == null ? SIN_CATEGORIA : primero.categoria();
                    return new Vendidos(grupo.getKey(),
                            porCategoria ? null : primero.codigo(),
                            porCategoria ? categoria : primero.nombre(),
                            porCategoria ? null : primero.marca(),
                            categoria,
                            (int) delGrupo.stream().filter(Vendido::completa).count(),
                            delGrupo.stream().filter(Vendido::completa).mapToInt(v -> v.renglon().cantidad()).sum(),
                            ventasNetas, costo, sinCosto, utilidad,
                            utilidad == null ? null : margen(utilidad, ventasNetas),
                            utilidad != null && utilidad.esNegativo());
                })
                .sorted(Comparator.comparing(Vendidos::ventasNetas).reversed().thenComparing(Vendidos::nombre))
                .toList();
    }

    /** Los renglones, una vez cada uno, aunque vengan en varios cobros. */
    private static List<RenglonVendido> distintos(List<Vendido> vendidos) {
        Map<String, RenglonVendido> unicos = new LinkedHashMap<>();
        for (Vendido v : vendidos) {
            unicos.putIfAbsent(v.renglon().ventaId() + "/" + v.renglon().posicion(), v.renglon());
        }
        return List.copyOf(unicos.values());
    }

    /**
     * Lo que carga cada gasto (RF-008, RF-010, RF-010a). Uno que no es del mes, entero en su fecha. Uno del mes, como
     * lo dice él mismo (spec 0014, decisión 5): repartido en los días del período, o entero si el período cubre su
     * mes; si no lo cubre, queda fuera.
     */
    private static Cargos cargosDe(List<GastoDelPeriodo> gastos, Periodo periodo, LocalDate hoy) {
        List<Cargo> cargos = new ArrayList<>();
        Dinero fuera = Dinero.CERO;
        for (GastoDelPeriodo gasto : gastos) {
            if (!gasto.delMes()) {
                if (periodo.contiene(gasto.fecha())) {
                    cargos.add(new Cargo(gasto, gasto.fecha(), gasto.monto()));
                }
            } else if (!periodo.tocaElMes(gasto.mes())) {
                continue;
            } else if (gasto.repartido()) {
                for (LocalDate dia = gasto.mes().atDay(1); !dia.isAfter(gasto.mes().atEndOfMonth()); dia = dia.plusDays(1)) {
                    if (periodo.contiene(dia)) {
                        cargos.add(new Cargo(gasto, dia, RepartoDelMes.cuota(gasto.monto(), dia)));
                    }
                }
            } else if (periodo.cubreElMes(gasto.mes(), hoy)) {
                cargos.add(new Cargo(gasto, null, gasto.monto()));
            } else {
                fuera = fuera.mas(gasto.monto());
            }
        }
        return new Cargos(cargos, fuera);
    }

    private static List<Fila> filasDe(Periodo periodo, List<LoCobrado.Cobrado> cobrados, List<Vendido> vendidos,
                                      List<Cargo> cargos) {
        List<Periodo> tramos = periodo.tramos();
        Map<LocalDate, Acumulado> porDia = new HashMap<>();
        List<Acumulado> acumulados = new ArrayList<>();
        for (Periodo tramo : tramos) {
            Acumulado acumulado = new Acumulado(tramo.desde(), tramo.hasta());
            acumulados.add(acumulado);
            for (LocalDate dia = tramo.desde(); !dia.isAfter(tramo.hasta()); dia = dia.plusDays(1)) {
                porDia.put(dia, acumulado);
            }
        }
        for (LoCobrado.Cobrado cobrado : cobrados) {
            porDia.get(cobrado.dia()).cobro(cobrado);
        }
        for (Vendido vendido : vendidos) {
            porDia.get(vendido.dia()).vendido(vendido);
        }
        for (Cargo cargo : cargos) {
            if (cargo.dia() != null) {
                porDia.get(cargo.dia()).cargo(cargo);
            }
        }
        return acumulados.stream().map(Acumulado::fila).toList();
    }

    /** Los gastos del mes que van enteros, en su propia fila: no son de ningún día. {@code null} si no hay. */
    private static Fila filaDeGastosDelMes(Periodo periodo, List<Cargo> cargos) {
        List<Cargo> enteros = cargos.stream().filter(c -> c.dia() == null).toList();
        if (enteros.isEmpty()) {
            return null;
        }
        Acumulado acumulado = new Acumulado(periodo.desde(), periodo.hasta());
        enteros.forEach(acumulado::cargo);
        return acumulado.fila();
    }

    /** Cada categoría con sus gastos, de más a menos; los gastos de cada una, por fecha. */
    private static List<PorCategoria> porCategoria(List<Cargo> cargos, NaturalezaGasto naturaleza) {
        Map<UUID, List<Cargo>> porCategoria = new LinkedHashMap<>();
        for (Cargo cargo : cargos) {
            if (cargo.gasto().naturaleza() == naturaleza) {
                porCategoria.computeIfAbsent(cargo.gasto().categoriaId(), id -> new ArrayList<>()).add(cargo);
            }
        }
        return porCategoria.values().stream()
                .map(deLaCategoria -> {
                    GastoDelPeriodo primero = deLaCategoria.getFirst().gasto();
                    Map<UUID, List<Cargo>> porGasto = new LinkedHashMap<>();
                    deLaCategoria.forEach(c -> porGasto.computeIfAbsent(c.gasto().id(), id -> new ArrayList<>()).add(c));
                    List<GastoCargado> gastos = porGasto.values().stream()
                            .map(delGasto -> {
                                GastoDelPeriodo g = delGasto.getFirst().gasto();
                                boolean repartido = g.repartido();
                                return new GastoCargado(g.id(), g.fecha(), g.descripcion(), g.monto(),
                                        suma(delGasto, Cargo::monto), repartido ? delGasto.size() : 0,
                                        repartido ? g.mes().lengthOfMonth() : 0);
                            })
                            .sorted(Comparator.comparing(GastoCargado::fecha).thenComparing(GastoCargado::descripcion))
                            .toList();
                    return new PorCategoria(primero.categoriaId(), primero.categoria(),
                            suma(deLaCategoria, Cargo::monto), gastos);
                })
                .sorted(Comparator.comparing(PorCategoria::monto).reversed().thenComparing(PorCategoria::categoria))
                .toList();
    }

    /** Los renglones sin costo que trajeron algo en el período, cada uno una vez, con lo que se cobró de ellos. */
    private static SinCosto sinCostoDe(List<Vendido> vendidos) {
        List<Vendido> sinCosto = vendidos.stream().filter(v -> !v.tieneCosto()).toList();
        List<RenglonVendido> renglones = distintos(sinCosto);
        Map<UUID, RepuestoSinCosto> porRepuesto = new LinkedHashMap<>();
        for (RenglonVendido r : renglones) {
            Dinero vendido = suma(sinCosto.stream().filter(v -> v.renglon() == r).toList(), Vendido::neto);
            porRepuesto.merge(r.varianteId(),
                    new RepuestoSinCosto(r.varianteId(), r.codigo(), r.nombre(), r.marca(), r.cantidad(), vendido),
                    (a, b) -> new RepuestoSinCosto(a.varianteId(), a.codigo(), a.nombre(), a.marca(),
                            a.unidades() + b.unidades(), a.vendido().mas(b.vendido())));
        }
        return new SinCosto(renglones.size(), renglones.stream().mapToInt(RenglonVendido::cantidad).sum(),
                suma(sinCosto, Vendido::neto),
                porRepuesto.values().stream()
                        .sorted(Comparator.comparing(RepuestoSinCosto::vendido).reversed()
                                .thenComparing(RepuestoSinCosto::codigo))
                        .toList());
    }

    private static BigDecimal margen(Dinero utilidad, Dinero ventasNetas) {
        return ventasNetas.esCero() ? null
                : utilidad.valor().multiply(CIEN).divide(ventasNetas.valor(), 1, RoundingMode.HALF_UP);
    }

    private static <T> Dinero suma(List<T> elementos, Function<T, Dinero> monto) {
        return elementos.stream().map(monto).reduce(Dinero.CERO, Dinero::mas);
    }

    private static Dinero sumaDeCargos(List<Cargo> cargos, NaturalezaGasto naturaleza) {
        return suma(cargos.stream().filter(c -> c.gasto().naturaleza() == naturaleza).toList(), Cargo::monto);
    }

    /** Lo que se va sumando en una fila. */
    private static final class Acumulado {
        private final LocalDate desde;
        private final LocalDate hasta;
        private int ventas;
        private int renglonesSinCosto;
        private Dinero ventasNetas = Dinero.CERO;
        private Dinero costoVendido = Dinero.CERO;
        private Dinero costosAdicionales = Dinero.CERO;
        private Dinero gastos = Dinero.CERO;

        Acumulado(LocalDate desde, LocalDate hasta) {
            this.desde = desde;
            this.hasta = hasta;
        }

        void cobro(LoCobrado.Cobrado cobrado) {
            if (cobrado.completa()) {
                ventas++;
            }
            ventasNetas = ventasNetas.mas(cobrado.monto());
        }

        void vendido(Vendido vendido) {
            if (vendido.tieneCosto()) {
                costoVendido = costoVendido.mas(vendido.costo());
            } else {
                renglonesSinCosto++;
            }
        }

        void cargo(Cargo cargo) {
            if (cargo.gasto().naturaleza() == NaturalezaGasto.COSTO) {
                costosAdicionales = costosAdicionales.mas(cargo.monto());
            } else {
                gastos = gastos.mas(cargo.monto());
            }
        }

        Fila fila() {
            Dinero utilidadBruta = ventasNetas.menos(costoVendido).menos(costosAdicionales);
            return new Fila(desde, hasta, ventas, ventasNetas, costoVendido, costosAdicionales, utilidadBruta, gastos,
                    utilidadBruta.menos(gastos), renglonesSinCosto);
        }
    }
}
