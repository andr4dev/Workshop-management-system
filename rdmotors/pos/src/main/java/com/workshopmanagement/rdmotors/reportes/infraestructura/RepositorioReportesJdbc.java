package com.workshopmanagement.rdmotors.reportes.infraestructura;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.caja.dominio.NaturalezaGasto;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.reportes.dominio.CarteraDelPeriodo;
import com.workshopmanagement.rdmotors.reportes.dominio.CobroDeVenta;
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

    /**
     * Tres fuentes de plata (spec 0014): lo pagado al cobrar, una venta de $0 (que se completa sola al cobrarse), y cada
     * aplicación vigente de un abono a la deuda de una venta, que cuenta en el más tardío entre la venta y el abono.
     */
    @Override
    public List<CobroDeVenta> cobrosDeVentas(Instant desde, Instant hasta) {
        return jdbc.query("""
                with cobros as (
                    select p.venta_id, v.cobrada_en as momento, p.forma, p.monto, false as de_abono, 0::bigint as orden
                    from pago_venta p
                    join venta v on v.id = p.venta_id
                    where v.estado = 'COBRADA'
                    union all
                    select v.id, v.cobrada_en, 'EFECTIVO', 0, false, 0
                    from venta v
                    where v.estado = 'COBRADA' and v.total = 0
                      and not exists (select 1 from pago_venta p where p.venta_id = v.id)
                    union all
                    select d.venta_id, greatest(v.cobrada_en, a.recibido_en), a.forma, ap.monto, true, a.numero
                    from aplicacion_abono ap
                    join abono a on a.id = ap.abono_id
                    join deuda d on d.id = ap.deuda_id
                    join venta v on v.id = d.venta_id
                    where ap.anulada_en is null and a.anulado_en is null and d.origen = 'VENTA'
                      and v.estado = 'COBRADA'
                )
                select c.venta_id, c.momento, (c.momento at time zone ?)::date as dia, c.forma, c.monto, c.de_abono
                from cobros c
                where c.momento < ?
                  and c.venta_id in (select venta_id from cobros where momento >= ? and momento < ?)
                order by c.venta_id, c.momento, c.de_abono, c.orden
                """,
                (rs, fila) -> new CobroDeVenta(
                        rs.getObject("venta_id", UUID.class),
                        rs.getObject("momento", OffsetDateTime.class).toInstant(),
                        rs.getObject("dia", LocalDate.class),
                        FormaPago.valueOf(rs.getString("forma")),
                        Dinero.de(rs.getBigDecimal("monto")),
                        rs.getBoolean("de_abono")),
                ZONA, utc(hasta), utc(desde), utc(hasta));
    }

    @Override
    public List<VentaCobrada> ventasPorId(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return new NamedParameterJdbcTemplate(jdbc).query("""
                select v.id,
                       (v.cobrada_en at time zone :zona)::date as dia,
                       v.total,
                       v.descuento_monto,
                       coalesce(sum(p.monto) filter (where p.forma = 'EFECTIVO'), 0) as efectivo,
                       coalesce(sum(p.monto) filter (where p.forma = 'TRANSFERENCIA'), 0) as transferencia,
                       v.fiado
                from venta v
                left join pago_venta p on p.venta_id = v.id
                where v.id in (:ids)
                group by v.id
                """,
                new MapSqlParameterSource().addValue("zona", ZONA).addValue("ids", ids),
                (rs, fila) -> new VentaCobrada(
                        rs.getObject("id", UUID.class),
                        rs.getObject("dia", LocalDate.class),
                        Dinero.de(rs.getBigDecimal("total")),
                        Dinero.de(rs.getBigDecimal("descuento_monto")),
                        Dinero.de(rs.getBigDecimal("efectivo")),
                        Dinero.de(rs.getBigDecimal("transferencia")),
                        Dinero.de(rs.getBigDecimal("fiado"))));
    }

    @Override
    public List<RenglonVendido> renglonesDe(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        // El costo es el de la salida del kardex: el que tenía el repuesto al venderse, no el de hoy.
        return new NamedParameterJdbcTemplate(jdbc).query("""
                select l.venta_id, l.posicion, l.variante_id, va.codigo, pr.nombre, va.marca_repuesto,
                       pr.categoria_id, c.nombre as categoria, l.cantidad, l.total, m.costo_total
                from linea_venta l
                join variante va on va.id = l.variante_id
                join producto pr on pr.id = va.producto_id
                left join categoria c on c.id = pr.categoria_id
                left join movimiento_kardex m on m.id = l.movimiento_salida_id
                where l.venta_id in (:ids)
                """,
                new MapSqlParameterSource("ids", ids),
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
                        dineroONulo(rs.getBigDecimal("costo_total"))));
    }

    @Override
    public List<GastoDelPeriodo> gastos(Periodo periodo) {
        return jdbc.query("""
                select g.id, g.fecha, g.categoria_id, c.nombre, c.naturaleza, g.descripcion, g.del_mes, g.repartir,
                       g.monto
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
                        rs.getString("descripcion"),
                        rs.getBoolean("del_mes"),
                        rs.getObject("repartir", Boolean.class),
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
                         where anulada_en is null and monto > abonado) as clientes,
                       coalesce((select sum(fiado) from venta
                                  where estado = 'COBRADA' and cobrada_en >= ? and cobrada_en < ?), 0) as vendido_fiado,
                       coalesce((select sum(ap.monto) from aplicacion_abono ap
                                  join abono a on a.id = ap.abono_id
                                  join deuda d on d.id = ap.deuda_id
                                  where ap.anulada_en is null and a.anulado_en is null and d.origen = 'CUADERNO'
                                    and a.recibido_en >= ? and a.recibido_en < ?), 0) as cobrado_cuaderno
                """,
                (rs, fila) -> new CarteraDelPeriodo(
                        Dinero.de(rs.getBigDecimal("efectivo")),
                        Dinero.de(rs.getBigDecimal("transferencia")),
                        Dinero.de(rs.getBigDecimal("por_cobrar")),
                        rs.getInt("clientes"),
                        Dinero.de(rs.getBigDecimal("vendido_fiado")),
                        Dinero.de(rs.getBigDecimal("cobrado_cuaderno"))),
                utc(desde), utc(hasta), utc(desde), utc(hasta), utc(desde), utc(hasta), utc(desde), utc(hasta));
    }

    private static OffsetDateTime utc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private static Dinero dineroONulo(BigDecimal valor) {
        return valor == null ? null : Dinero.de(valor);
    }
}
