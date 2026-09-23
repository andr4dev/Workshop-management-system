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
 *   efectivo + transferencia + fiado = ventas netas
 *   ventas netas − costo vendido − costos adicionales = utilidad bruta
 *   utilidad bruta − gastos         = utilidad operativa
 *   Σ filas (+ la de gastos del mes) = las cifras
 *   Σ por categoría                 = costos adicionales y gastos
 *   Σ repuestos y Σ categorías de repuesto = ventas netas (con el descuento repartido)
 * </pre>
 *
 * <p><b>Lo fiado es venta el día que se vende</b> (spec 0008): baja el stock y deja su costo ese día, así que su
 * ganancia también es de ese día. Lo que después se abona es un cobro, no otra venta.
 *
 * <p><b>Ingreso y costo miden lo mismo:</b> los dos salen de los mismos renglones de las mismas ventas. Un renglón sin
 * costo no suma $0: se cuenta aparte, y la utilidad queda sobrestimada (RF-007).
 *
 * @param filaGastosDelMes solo con {@link ModoGastosDelMes#SOLO_EN_EL_MES} y algún gasto del mes que cuente: los
 *                         gastos del mes enteros, que no son de ningún día; {@code null} si no
 * @param repuestos cada repuesto vendido, de más a menos ventas netas; la pantalla elige el orden (RF-019)
 * @param categoriasDeRepuesto de más a menos ventas netas, con <i>Sin categoría</i> (RF-021)
 */
public record ResultadosDelPeriodo(Periodo periodo, Agrupacion agrupacion, ModoGastosDelMes modo, Cifras cifras,
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
     * @param renglones la suma de los renglones antes del descuento
     * @param ticketPromedio ventas netas ÷ ventas, redondeado al peso; {@code null} sin ventas
     * @param fiado lo vendido que quedó debiendo el cliente (spec 0008)
     * @param costoVendido solo de los renglones con costo
     * @param margenBruto porcentaje con un decimal; {@code null} sin ventas netas
     */
    public record Cifras(int ventas, int unidades, Dinero renglones, Dinero descuentos, int ventasConDescuento,
                         Dinero ventasNetas, Dinero ticketPromedio, Dinero efectivo, Dinero transferencia,
                         Dinero fiado, Dinero costoVendido, Dinero costosAdicionales, Dinero utilidadBruta, BigDecimal margenBruto,
                         Dinero gastos, Dinero utilidadOperativa, BigDecimal margenOperativo) {
    }

    public record PorCategoria(UUID categoriaId, String categoria, Dinero monto) {
    }

    /**
     * @param incluidos lo que cargaron los gastos del mes en el período: repartidos, o enteros
     * @param fuera lo que quedó fuera en {@link ModoGastosDelMes#SOLO_EN_EL_MES}: gastos del mes de un mes que el
     *              período toca pero no cubre
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

    /** Un renglón con su parte del descuento ya restada. */
    private record Vendido(RenglonVendido renglon, LocalDate dia, Dinero neto) {
    }

    /** Lo que un gasto carga en el período. {@code dia} es {@code null} si va entero en la fila de gastos del mes. */
    private record Cargo(GastoDelPeriodo gasto, LocalDate dia, Dinero monto) {
    }

    /** Los cargos de los gastos, y lo que quedó fuera en "solo en el mes". */
    private record Cargos(List<Cargo> cargos, Dinero fuera) {
    }

    public static ResultadosDelPeriodo calcular(Periodo periodo, List<VentaCobrada> ventas,
                                                List<RenglonVendido> renglones, List<GastoDelPeriodo> gastos,
                                                ModoGastosDelMes modo, LocalDate hoy) {
        for (VentaCobrada venta : ventas) {
            if (!periodo.contiene(venta.dia())) {
                throw new IllegalArgumentException("La venta " + venta.id() + " es del " + venta.dia()
                        + ", fuera del período " + periodo);
            }
        }
        List<Vendido> vendidos = conDescuentoRepartido(ventas, renglones);
        Cargos deLosGastos = cargosDe(gastos, periodo, modo, hoy);
        List<Cargo> cargos = deLosGastos.cargos();

        Dinero ventasNetas = suma(ventas, VentaCobrada::total);
        Dinero costosAdicionales = sumaDeCargos(cargos, NaturalezaGasto.COSTO);
        Dinero gastosDelLocal = sumaDeCargos(cargos, NaturalezaGasto.GASTO);
        Dinero costoVendido = suma(vendidos.stream().filter(v -> v.renglon().tieneCosto()).toList(),
                v -> v.renglon().costo());
        Dinero utilidadBruta = ventasNetas.menos(costoVendido).menos(costosAdicionales);
        Dinero utilidadOperativa = utilidadBruta.menos(gastosDelLocal);

        Cifras cifras = new Cifras(
                ventas.size(),
                renglones.stream().mapToInt(RenglonVendido::cantidad).sum(),
                suma(renglones, RenglonVendido::total),
                suma(ventas, VentaCobrada::descuento),
                (int) ventas.stream().filter(v -> !v.descuento().esCero()).count(),
                ventasNetas,
                ventas.isEmpty() ? null
                        : Dinero.de(ventasNetas.valor().divide(BigDecimal.valueOf(ventas.size()), 0, RoundingMode.HALF_UP)),
                suma(ventas, VentaCobrada::efectivo),
                suma(ventas, VentaCobrada::transferencia),
                suma(ventas, VentaCobrada::fiado),
                costoVendido,
                costosAdicionales,
                utilidadBruta,
                margen(utilidadBruta, ventasNetas),
                gastosDelLocal,
                utilidadOperativa,
                margen(utilidadOperativa, ventasNetas));

        Dinero incluidos = suma(cargos.stream().filter(c -> c.gasto().delMes()).toList(), Cargo::monto);
        return new ResultadosDelPeriodo(periodo, periodo.agrupacion(), modo, cifras,
                porCategoria(cargos, NaturalezaGasto.COSTO), porCategoria(cargos, NaturalezaGasto.GASTO),
                new GastosDelMes(incluidos, deLosGastos.fuera()), sinCostoDe(vendidos),
                filasDe(periodo, ventas, vendidos, cargos), filaDeGastosDelMes(periodo, modo, cargos),
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
                    Dinero costo = suma(delGrupo.stream().filter(v -> v.renglon().tieneCosto()).toList(),
                            v -> v.renglon().costo());
                    int sinCosto = (int) delGrupo.stream().filter(v -> !v.renglon().tieneCosto()).count();
                    Dinero utilidad = sinCosto > 0 ? null : ventasNetas.menos(costo);
                    String categoria = primero.categoriaId() == null ? SIN_CATEGORIA : primero.categoria();
                    return new Vendidos(grupo.getKey(),
                            porCategoria ? null : primero.codigo(),
                            porCategoria ? categoria : primero.nombre(),
                            porCategoria ? null : primero.marca(),
                            categoria,
                            delGrupo.size(),
                            delGrupo.stream().mapToInt(v -> v.renglon().cantidad()).sum(),
                            ventasNetas, costo, sinCosto, utilidad,
                            utilidad == null ? null : margen(utilidad, ventasNetas),
                            utilidad != null && utilidad.esNegativo());
                })
                .sorted(Comparator.comparing(Vendidos::ventasNetas).reversed().thenComparing(Vendidos::nombre))
                .toList();
    }

    /** Cada renglón con el descuento de su venta repartido (decisión 3), en el orden de las ventas y sus posiciones. */
    private static List<Vendido> conDescuentoRepartido(List<VentaCobrada> ventas, List<RenglonVendido> renglones) {
        Map<UUID, List<RenglonVendido>> porVenta = new HashMap<>();
        for (RenglonVendido renglon : renglones) {
            porVenta.computeIfAbsent(renglon.ventaId(), id -> new ArrayList<>()).add(renglon);
        }
        List<Vendido> vendidos = new ArrayList<>();
        for (VentaCobrada venta : ventas) {
            List<RenglonVendido> deLaVenta = porVenta.remove(venta.id());
            if (deLaVenta == null) {
                continue;
            }
            deLaVenta.sort(Comparator.comparingInt(RenglonVendido::posicion));
            List<Dinero> netos = RepartoDeDescuento.netos(deLaVenta.stream().map(RenglonVendido::total).toList(),
                    venta.descuento());
            for (int i = 0; i < deLaVenta.size(); i++) {
                vendidos.add(new Vendido(deLaVenta.get(i), venta.dia(), netos.get(i)));
            }
        }
        if (!porVenta.isEmpty()) {
            throw new IllegalArgumentException("Hay renglones de ventas que no están en el período: " + porVenta.keySet());
        }
        return vendidos;
    }

    /**
     * Lo que carga cada gasto (RF-008, RF-010, RF-010a). Uno que no es del mes, entero en su fecha. Uno del mes,
     * repartido en los días del período, o entero si el período cubre su mes; si no lo cubre, queda fuera.
     */
    private static Cargos cargosDe(List<GastoDelPeriodo> gastos, Periodo periodo, ModoGastosDelMes modo,
                                   LocalDate hoy) {
        List<Cargo> cargos = new ArrayList<>();
        Dinero fuera = Dinero.CERO;
        for (GastoDelPeriodo gasto : gastos) {
            if (!gasto.delMes()) {
                if (periodo.contiene(gasto.fecha())) {
                    cargos.add(new Cargo(gasto, gasto.fecha(), gasto.monto()));
                }
            } else if (!periodo.tocaElMes(gasto.mes())) {
                continue;
            } else if (modo == ModoGastosDelMes.REPARTIDOS) {
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

    private static List<Fila> filasDe(Periodo periodo, List<VentaCobrada> ventas, List<Vendido> vendidos,
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
        for (VentaCobrada venta : ventas) {
            porDia.get(venta.dia()).venta(venta);
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

    private static Fila filaDeGastosDelMes(Periodo periodo, ModoGastosDelMes modo, List<Cargo> cargos) {
        List<Cargo> enteros = cargos.stream().filter(c -> c.dia() == null).toList();
        if (modo != ModoGastosDelMes.SOLO_EN_EL_MES || enteros.isEmpty()) {
            return null;
        }
        Acumulado acumulado = new Acumulado(periodo.desde(), periodo.hasta());
        enteros.forEach(acumulado::cargo);
        return acumulado.fila();
    }

    private static List<PorCategoria> porCategoria(List<Cargo> cargos, NaturalezaGasto naturaleza) {
        Map<UUID, PorCategoria> porId = new LinkedHashMap<>();
        for (Cargo cargo : cargos) {
            GastoDelPeriodo gasto = cargo.gasto();
            if (gasto.naturaleza() == naturaleza) {
                porId.merge(gasto.categoriaId(), new PorCategoria(gasto.categoriaId(), gasto.categoria(), cargo.monto()),
                        (a, b) -> new PorCategoria(a.categoriaId(), a.categoria(), a.monto().mas(b.monto())));
            }
        }
        return porId.values().stream()
                .sorted(Comparator.comparing(PorCategoria::monto).reversed().thenComparing(PorCategoria::categoria))
                .toList();
    }

    private static SinCosto sinCostoDe(List<Vendido> vendidos) {
        List<Vendido> sinCosto = vendidos.stream().filter(v -> !v.renglon().tieneCosto()).toList();
        Map<UUID, RepuestoSinCosto> porRepuesto = new LinkedHashMap<>();
        for (Vendido v : sinCosto) {
            RenglonVendido r = v.renglon();
            porRepuesto.merge(r.varianteId(),
                    new RepuestoSinCosto(r.varianteId(), r.codigo(), r.nombre(), r.marca(), r.cantidad(), v.neto()),
                    (a, b) -> new RepuestoSinCosto(a.varianteId(), a.codigo(), a.nombre(), a.marca(),
                            a.unidades() + b.unidades(), a.vendido().mas(b.vendido())));
        }
        return new SinCosto(sinCosto.size(), sinCosto.stream().mapToInt(v -> v.renglon().cantidad()).sum(),
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

        void venta(VentaCobrada venta) {
            ventas++;
            ventasNetas = ventasNetas.mas(venta.total());
        }

        void vendido(Vendido vendido) {
            if (vendido.renglon().tieneCosto()) {
                costoVendido = costoVendido.mas(vendido.renglon().costo());
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
