package com.workshopmanagement.rdmotors.reportes.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.workshopmanagement.rdmotors.caja.dominio.NaturalezaGasto;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.reportes.dominio.ResultadosDelPeriodo.Fila;

/**
 * Filas de lectura armadas a mano para probar los reportes (spec 0007): lo que devolvería la base, sin la base.
 */
public class DatosDeReporte {

    /** El lunes de la semana del ejemplo del spec (§2). */
    public static final LocalDate LUNES_14 = LocalDate.of(2026, 9, 14);
    public static final LocalDate DOMINGO_20 = LocalDate.of(2026, 9, 20);

    public final List<VentaCobrada> ventas = new ArrayList<>();
    public final List<RenglonVendido> renglones = new ArrayList<>();
    public final List<GastoDelPeriodo> gastos = new ArrayList<>();
    private final Map<String, UUID> ids = new HashMap<>();

    /** @param categoria de repuesto; {@code null} si no tiene */
    public record Renglon(String codigo, int cantidad, long total, Long costo, String categoria) {

        public Renglon en(String categoria) {
            return new Renglon(codigo, cantidad, total, costo, categoria);
        }
    }

    public static Renglon conCosto(String codigo, int cantidad, long total, long costo) {
        return new Renglon(codigo, cantidad, total, costo, null);
    }

    public static Renglon sinCosto(String codigo, int cantidad, long total) {
        return new Renglon(codigo, cantidad, total, null, null);
    }

    /** Una venta cobrada: el total es la suma de sus renglones menos el descuento, y los pagos tienen que sumarlo. */
    public VentaCobrada venta(LocalDate dia, long descuento, long efectivo, long transferencia, Renglon... deLaVenta) {
        long subtotal = Stream.of(deLaVenta).mapToLong(Renglon::total).sum();
        if (efectivo + transferencia != subtotal - descuento) {
            throw new IllegalArgumentException("Los pagos no suman el total de la venta");
        }
        VentaCobrada venta = new VentaCobrada(UUID.randomUUID(), dia, Dinero.de(subtotal - descuento),
                Dinero.de(descuento), Dinero.de(efectivo), Dinero.de(transferencia));
        ventas.add(venta);
        for (int i = 0; i < deLaVenta.length; i++) {
            Renglon r = deLaVenta[i];
            renglones.add(new RenglonVendido(venta.id(), i, id(r.codigo()), r.codigo(), "Repuesto " + r.codigo(),
                    "Marca", r.categoria() == null ? null : id(r.categoria()), r.categoria(), r.cantidad(),
                    Dinero.de(r.total()),
                    r.costo() == null ? null : Dinero.de(r.costo())));
        }
        return venta;
    }

    /** Una venta con una parte fiada (spec 0008): lo pagado en efectivo más lo fiado suman el total. */
    public VentaCobrada ventaFiada(LocalDate dia, long efectivo, long fiado, Renglon... deLaVenta) {
        long subtotal = Stream.of(deLaVenta).mapToLong(Renglon::total).sum();
        if (efectivo + fiado != subtotal) {
            throw new IllegalArgumentException("Lo pagado y lo fiado no suman el total de la venta");
        }
        VentaCobrada venta = new VentaCobrada(UUID.randomUUID(), dia, Dinero.de(subtotal), Dinero.CERO,
                Dinero.de(efectivo), Dinero.CERO, Dinero.de(fiado));
        ventas.add(venta);
        for (int i = 0; i < deLaVenta.length; i++) {
            Renglon r = deLaVenta[i];
            renglones.add(new RenglonVendido(venta.id(), i, id(r.codigo()), r.codigo(), "Repuesto " + r.codigo(),
                    "Marca", null, null, r.cantidad(), Dinero.de(r.total()),
                    r.costo() == null ? null : Dinero.de(r.costo())));
        }
        return venta;
    }

    public GastoDelPeriodo gasto(LocalDate fecha, String categoria, NaturalezaGasto naturaleza, long monto) {
        return agregar(new GastoDelPeriodo(UUID.randomUUID(), fecha, id(categoria), categoria, naturaleza, false,
                Dinero.de(monto)));
    }

    public GastoDelPeriodo gastoDelMes(LocalDate fecha, String categoria, long monto) {
        return agregar(new GastoDelPeriodo(UUID.randomUUID(), fecha, id(categoria), categoria, NaturalezaGasto.GASTO,
                true, Dinero.de(monto)));
    }

    public UUID id(String nombre) {
        return ids.computeIfAbsent(nombre, n -> UUID.randomUUID());
    }

    private GastoDelPeriodo agregar(GastoDelPeriodo gasto) {
        gastos.add(gasto);
        return gasto;
    }

    /** Calcula con las ventas del período y sus renglones, como las devolvería la base; los gastos van todos. */
    public ResultadosDelPeriodo calcular(LocalDate desde, LocalDate hasta, ModoGastosDelMes modo, LocalDate hoy) {
        Periodo periodo = new Periodo(desde, hasta);
        List<VentaCobrada> delPeriodo = ventas.stream().filter(v -> periodo.contiene(v.dia())).toList();
        Set<UUID> ids = delPeriodo.stream().map(VentaCobrada::id).collect(Collectors.toSet());
        return ResultadosDelPeriodo.calcular(periodo, delPeriodo,
                renglones.stream().filter(r -> ids.contains(r.ventaId())).toList(), gastos, modo, hoy);
    }

    /**
     * La semana del lunes 14 al domingo 20 de septiembre (spec 0007, §2): 48 ventas y 132 unidades; renglones por
     * $1.250.000 con $30.000 de descuentos; $820.000 en efectivo y $400.000 por transferencia; costo $780.000; un
     * flete de mercancía de $20.000 como costo, y $100.000 de gastos del local.
     */
    public static DatosDeReporte semanaDelEjemplo() {
        DatosDeReporte datos = new DatosDeReporte();
        for (int i = 0; i < 48; i++) {
            LocalDate dia = LUNES_14.plusDays(i % 7);
            if (i < 2) {
                datos.venta(dia, 0, 0, 50_000, conCosto("CAJA-ACEITE", 20, 50_000, 45_000));
            } else if (i < 14) {
                datos.venta(dia, 0, 0, 25_000, conCosto("FILTRO-AIRE", 2, 25_000, 15_000));
            } else if (i < 20) {
                datos.venta(dia, 5_000, 20_000, 0, conCosto("FILTRO-AIRE", 2, 25_000, 15_000));
            } else {
                datos.venta(dia, 0, 25_000, 0, conCosto("PASTILLAS", 2, 25_000, 15_000));
            }
        }
        datos.gasto(LUNES_14.plusDays(1), "Fletes de mercancía", NaturalezaGasto.COSTO, 20_000);
        datos.gasto(LUNES_14, "Alimentación", NaturalezaGasto.GASTO, 20_000);
        datos.gasto(LUNES_14.plusDays(2), "Alimentación", NaturalezaGasto.GASTO, 20_000);
        datos.gasto(LUNES_14.plusDays(4), "Alimentación", NaturalezaGasto.GASTO, 20_000);
        datos.gasto(LUNES_14.plusDays(3), "Aseo y cafetería", NaturalezaGasto.GASTO, 25_000);
        datos.gasto(LUNES_14.plusDays(5), "Papelería", NaturalezaGasto.GASTO, 15_000);
        // El lunes siguiente: fuera de la semana.
        datos.gasto(DOMINGO_20.plusDays(1), "Alimentación", NaturalezaGasto.GASTO, 99_000);
        return datos;
    }

    /** Las filas del día por día, más la de gastos del mes si hay, suman las cifras al peso (RF-017). */
    public static void lasFilasSuman(ResultadosDelPeriodo r) {
        List<Fila> filas = new ArrayList<>(r.filas());
        if (r.filaGastosDelMes() != null) {
            filas.add(r.filaGastosDelMes());
        }
        ResultadosDelPeriodo.Cifras c = r.cifras();
        assertThat(filas.stream().mapToInt(Fila::ventas).sum()).as("ventas").isEqualTo(c.ventas());
        assertThat(suma(filas, Fila::ventasNetas)).as("ventas netas").isEqualTo(c.ventasNetas());
        assertThat(suma(filas, Fila::costoVendido)).as("costo vendido").isEqualTo(c.costoVendido());
        assertThat(suma(filas, Fila::costosAdicionales)).as("costos adicionales").isEqualTo(c.costosAdicionales());
        assertThat(suma(filas, Fila::utilidadBruta)).as("utilidad bruta").isEqualTo(c.utilidadBruta());
        assertThat(suma(filas, Fila::gastos)).as("gastos").isEqualTo(c.gastos());
        assertThat(suma(filas, Fila::utilidadOperativa)).as("utilidad operativa").isEqualTo(c.utilidadOperativa());
        assertThat(filas.stream().mapToInt(Fila::renglonesSinCosto).sum()).as("sin costo")
                .isEqualTo(r.sinCosto().renglones());
    }

    public static <T> Dinero suma(List<T> elementos, Function<T, Dinero> monto) {
        return elementos.stream().map(monto).reduce(Dinero.CERO, Dinero::mas);
    }
}
