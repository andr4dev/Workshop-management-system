package com.workshopmanagement.rdmotors.reportes.infraestructura;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.caja.dominio.NaturalezaGasto;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.reportes.dominio.CarteraDelPeriodo;
import com.workshopmanagement.rdmotors.reportes.dominio.Control;
import com.workshopmanagement.rdmotors.reportes.dominio.GastoDelPeriodo;
import com.workshopmanagement.rdmotors.reportes.dominio.Periodo;
import com.workshopmanagement.rdmotors.reportes.dominio.RenglonVendido;
import com.workshopmanagement.rdmotors.reportes.dominio.VentaCobrada;
import com.workshopmanagement.rdmotors.reportes.dominio.puerto.RepositorioReportes;

import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR — lo que leen los reportes, con SQL sobre Postgres (spec 0007).
 *
 * <p>Con JDBC y no con entidades: un año son decenas de miles de filas de solo lectura, y cargar ventas con sus
 * renglones y pagos como entidades sería pagar el mapeo para tirarlo.
 *
 * <p><b>El día se corta en Colombia dentro del SQL</b> ({@code at time zone}), y el período se filtra por los
 * instantes de sus medianoches: así usa {@code idx_venta_cobrada_en}.
 */
@Repository
@RequiredArgsConstructor
class RepositorioReportesJdbc implements RepositorioReportes {

    private static final String ZONA = Periodo.ZONA.getId();

    private final JdbcTemplate jdbc;

    @Override
    public List<VentaCobrada> ventasCobradas(Instant desde, Instant hasta) {
        return jdbc.query("""
                select v.id,
                       (v.cobrada_en at time zone ?)::date as dia,
                       v.total,
                       v.descuento_monto,
                       coalesce(sum(p.monto) filter (where p.forma = 'EFECTIVO'), 0) as efectivo,
                       coalesce(sum(p.monto) filter (where p.forma = 'TRANSFERENCIA'), 0) as transferencia,
                       v.fiado
                from venta v
                left join pago_venta p on p.venta_id = v.id
                where v.estado = 'COBRADA' and v.cobrada_en >= ? and v.cobrada_en < ?
                group by v.id
                order by v.cobrada_en
                """,
                (rs, fila) -> new VentaCobrada(
                        rs.getObject("id", UUID.class),
                        rs.getObject("dia", LocalDate.class),
                        Dinero.de(rs.getBigDecimal("total")),
                        Dinero.de(rs.getBigDecimal("descuento_monto")),
                        Dinero.de(rs.getBigDecimal("efectivo")),
                        Dinero.de(rs.getBigDecimal("transferencia")),
                        Dinero.de(rs.getBigDecimal("fiado"))),
                ZONA, utc(desde), utc(hasta));
    }

    @Override
    public List<RenglonVendido> renglonesVendidos(Instant desde, Instant hasta) {
        // El costo es el de la salida del kardex: el que tenía el repuesto al venderse, no el de hoy.
        return jdbc.query("""
                select l.venta_id, l.posicion, l.variante_id, va.codigo, pr.nombre, va.marca_repuesto,
                       pr.categoria_id, c.nombre as categoria, l.cantidad, l.total, m.costo_total
                from venta v
                join linea_venta l on l.venta_id = v.id
                join variante va on va.id = l.variante_id
                join producto pr on pr.id = va.producto_id
                left join categoria c on c.id = pr.categoria_id
                left join movimiento_kardex m on m.id = l.movimiento_salida_id
                where v.estado = 'COBRADA' and v.cobrada_en >= ? and v.cobrada_en < ?
                """,
                (rs, fila) -> new RenglonVendido(
                        rs.getObject("venta_id", UUID.class),
                        rs.getInt("posicion"),
                        rs.getObject("variante_id", UUID.class),
                        rs.getString("codigo"),
                        rs.getString("nombre"),
                        rs.getString("marca_repuesto"),
                        rs.getObject("categoria_id", UUID.class),
                        rs.getString("categoria"),
                        rs.getInt("cantidad"),
                        Dinero.de(rs.getBigDecimal("total")),
                        dineroONulo(rs.getBigDecimal("costo_total"))),
                utc(desde), utc(hasta));
    }

    @Override
    public List<GastoDelPeriodo> gastos(Periodo periodo) {
        return jdbc.query("""
                select g.id, g.fecha, g.categoria_id, c.nombre, c.naturaleza, g.del_mes, g.monto
                from gasto g
                join categoria_gasto c on c.id = g.categoria_id
                where g.anulado_en is null
                  and ((not g.del_mes and g.fecha between ? and ?)
                       or (g.del_mes and g.fecha between ? and ?))
                order by g.fecha, g.registrado_en
                """,
                (rs, fila) -> new GastoDelPeriodo(
                        rs.getObject("id", UUID.class),
                        rs.getObject("fecha", LocalDate.class),
                        rs.getObject("categoria_id", UUID.class),
                        rs.getString("nombre"),
                        NaturalezaGasto.valueOf(rs.getString("naturaleza")),
                        rs.getBoolean("del_mes"),
                        Dinero.de(rs.getBigDecimal("monto"))),
                periodo.desde(), periodo.hasta(),
                YearMonth.from(periodo.desde()).atDay(1), YearMonth.from(periodo.hasta()).atEndOfMonth());
    }

    @Override
    public Control.VentasAnuladas ventasAnuladas(Instant desde, Instant hasta) {
        return jdbc.queryForObject("""
                select count(*) as cuantas, coalesce(sum(total), 0) as monto
                from venta
                where estado = 'ANULADA' and cobrada_en >= ? and cobrada_en < ?
                """,
                (rs, fila) -> new Control.VentasAnuladas(rs.getInt("cuantas"), Dinero.de(rs.getBigDecimal("monto"))),
                utc(desde), utc(hasta));
    }

    @Override
    public List<Control.TurnoCerrado> turnosCerrados(Instant desde, Instant hasta) {
        return jdbc.query("""
                select id, abierto_en, cerrado_en, esperado, contado, diferencia
                from turno_caja
                where estado = 'CERRADO' and cerrado_en >= ? and cerrado_en < ?
                order by cerrado_en
                """,
                (rs, fila) -> new Control.TurnoCerrado(
                        rs.getObject("id", UUID.class),
                        rs.getObject("abierto_en", OffsetDateTime.class).toInstant(),
                        rs.getObject("cerrado_en", OffsetDateTime.class).toInstant(),
                        Dinero.de(rs.getBigDecimal("esperado")),
                        Dinero.de(rs.getBigDecimal("contado")),
                        Dinero.de(rs.getBigDecimal("diferencia"))),
                utc(desde), utc(hasta));
    }

    /**
     * Los abonos del período y lo que deben hoy los clientes (spec 0008, RF-024). Las mismas cuentas que la Cartera:
     * un abono anulado no entró, y lo por cobrar es lo fiado menos lo abonado de las deudas sin anular.
     */
    @Override
    public CarteraDelPeriodo cartera(Instant desde, Instant hasta) {
        return jdbc.queryForObject("""
                select coalesce((select sum(monto) from abono
                                  where anulado_en is null and forma = 'EFECTIVO'
                                    and recibido_en >= ? and recibido_en < ?), 0) as efectivo,
                       coalesce((select sum(monto) from abono
                                  where anulado_en is null and forma = 'TRANSFERENCIA'
                                    and recibido_en >= ? and recibido_en < ?), 0) as transferencia,
                       coalesce((select sum(monto - abonado) from deuda
                                  where anulada_en is null and monto > abonado), 0) as por_cobrar,
                       (select count(distinct cliente_id) from deuda
                         where anulada_en is null and monto > abonado) as clientes
                """,
                (rs, fila) -> new CarteraDelPeriodo(
                        Dinero.de(rs.getBigDecimal("efectivo")),
                        Dinero.de(rs.getBigDecimal("transferencia")),
                        Dinero.de(rs.getBigDecimal("por_cobrar")),
                        rs.getInt("clientes")),
                utc(desde), utc(hasta), utc(desde), utc(hasta));
    }

    private static OffsetDateTime utc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private static Dinero dineroONulo(BigDecimal valor) {
        return valor == null ? null : Dinero.de(valor);
    }
}
