package com.workshopmanagement.rdmotors.clientes.infraestructura;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.clientes.dominio.FiltroCartera;
import com.workshopmanagement.rdmotors.clientes.dominio.ResumenDeCliente;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.ConsultasDeCartera;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.TextoDeBusqueda;

import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR — la lista de la Cartera con SQL sobre Postgres (spec 0008): una fila por cliente, con sus cuentas.
 *
 * <p>Las mismas cuentas que {@code CarteraDelCliente}: lo que debe es lo fiado menos lo abonado de las deudas sin
 * anular; lo que tiene a favor, lo que sus abonos vigentes no aplicaron. Una prueba de integración las compara.
 */
@Repository
@RequiredArgsConstructor
class ConsultasDeCarteraJdbc implements ConsultasDeCartera {

    private final JdbcTemplate jdbc;

    @Override
    public List<ResumenDeCliente> resumen(FiltroCartera filtro) {
        StringBuilder sql = new StringBuilder("""
                with d as (
                    select cliente_id,
                           coalesce(sum(monto - abonado) filter (where anulada_en is null), 0) as debe,
                           count(*) filter (where anulada_en is null and monto > abonado) as pendientes,
                           min(fecha) filter (where anulada_en is null and monto > abonado) as desde,
                           coalesce(sum(monto) filter (where anulada_en is null), 0) as fiado_total,
                           max(registrada_en) as ultima_deuda
                    from deuda
                    group by cliente_id
                ), a as (
                    select cliente_id,
                           coalesce(sum(monto) filter (where anulado_en is null), 0) as pagado,
                           max(recibido_en) as ultimo_abono
                    from abono
                    group by cliente_id
                ), ap as (
                    select ab.cliente_id, coalesce(sum(ap.monto), 0) as aplicado
                    from aplicacion_abono ap
                    join abono ab on ab.id = ap.abono_id
                    where ap.anulada_en is null and ab.anulado_en is null
                    group by ab.cliente_id
                )
                select c.id, c.nombre, c.documento, c.celular, c.fiado_cerrado,
                       d.debe, coalesce(a.pagado, 0) - coalesce(ap.aplicado, 0) as a_favor,
                       d.pendientes, d.desde, d.fiado_total, coalesce(a.pagado, 0) as pagado,
                       greatest(d.ultima_deuda, a.ultimo_abono) as ultimo
                from cliente c
                join d on d.cliente_id = c.id
                left join a on a.cliente_id = c.id
                left join ap on ap.cliente_id = c.id
                where 1 = 1
                """);
        List<Object> parametros = new ArrayList<>();
        if (filtro.vista() == FiltroCartera.Vista.DEBEN) {
            sql.append(" and d.debe > 0");
        }
        // Por fecha de la venta o del abono (RF-022): salen los clientes que tuvieron ese movimiento en el período.
        if (filtro.tienePeriodo()) {
            if (filtro.modoFecha() == FiltroCartera.ModoFecha.ABONO) {
                sql.append("""
                         and exists (select 1 from abono ab
                                      where ab.cliente_id = c.id and ab.anulado_en is null
                                        and (ab.recibido_en at time zone 'America/Bogota')::date between ? and ?)""");
            } else {
                sql.append("""
                         and exists (select 1 from deuda de
                                      where de.cliente_id = c.id and de.anulada_en is null
                                        and de.fecha between ? and ?)""");
            }
            parametros.add(filtro.desde());
            parametros.add(filtro.hasta());
        }
        if (filtro.texto() != null) {
            String documento = Cliente.normalizarDocumento(filtro.texto());
            String celular = Cliente.normalizarCelular(filtro.texto());
            sql.append(" and (c.nombre_normalizado like ? escape '!'");
            parametros.add("%" + escapar(TextoDeBusqueda.normalizar(filtro.texto())) + "%");
            if (documento != null) {
                sql.append(" or c.documento_normalizado like ? escape '!'");
                parametros.add("%" + escapar(documento) + "%");
            }
            if (celular != null) {
                sql.append(" or c.celular_normalizado like ?");
                parametros.add("%" + celular + "%");
            }
            sql.append(")");
        }
        return jdbc.query(sql.toString(), (rs, fila) -> new ResumenDeCliente(
                rs.getObject("id", UUID.class),
                rs.getString("nombre"),
                rs.getString("documento"),
                rs.getString("celular"),
                rs.getBoolean("fiado_cerrado"),
                Dinero.de(rs.getBigDecimal("debe")),
                Dinero.de(rs.getBigDecimal("a_favor")),
                rs.getInt("pendientes"),
                rs.getObject("desde", LocalDate.class),
                Dinero.de(rs.getBigDecimal("fiado_total")),
                Dinero.de(rs.getBigDecimal("pagado")),
                rs.getObject("ultimo", OffsetDateTime.class) == null ? null
                        : rs.getObject("ultimo", OffsetDateTime.class).toInstant()),
                parametros.toArray());
    }

    private static String escapar(String texto) {
        return texto.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
