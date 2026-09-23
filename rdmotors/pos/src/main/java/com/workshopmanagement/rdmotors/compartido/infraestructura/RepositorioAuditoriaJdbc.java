package com.workshopmanagement.rdmotors.compartido.infraestructura;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * ADAPTADOR — el registro de auditoría sobre Postgres, con el antes y el después en {@code jsonb}.
 *
 * <p>Con JDBC y no con una entidad JPA: el evento del dominio no es entidad, y el {@code jsonb} se
 * escribe mejor diciéndolo en el SQL ({@code cast(? as jsonb)}) que peleando con el mapeo.
 *
 * <p>Al leer, <b>los decimales vuelven como {@code BigDecimal}</b>. Con el valor por defecto de
 * Jackson volverían como {@code double} y un costo de $13.333,3333 podría leerse distinto de como
 * se guardó.
 */
@Repository
@RequiredArgsConstructor
class RepositorioAuditoriaJdbc implements RepositorioAuditoria {

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();
    private static final TypeReference<LinkedHashMap<String, Object>> MAPA = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;

    @Override
    public void registrar(EventoAuditoria evento) {
        jdbc.update("""
                insert into evento_auditoria
                    (id, ocurrido_en, usuario_id, accion, entidad_tipo, entidad_id, antes, despues, motivo)
                values (?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?)
                """,
                evento.id(),
                OffsetDateTime.ofInstant(evento.ocurridoEn(), ZoneOffset.UTC),
                evento.usuarioId(),
                evento.accion().name(),
                evento.entidadTipo(),
                evento.entidadId(),
                aJson(evento.antes()),
                aJson(evento.despues()),
                evento.motivo());
    }

    @Override
    public List<EventoAuditoria> historialDe(String entidadTipo, UUID entidadId) {
        return jdbc.query("""
                select id, ocurrido_en, usuario_id, accion, entidad_tipo, entidad_id,
                       antes::text as antes, despues::text as despues, motivo
                from evento_auditoria
                where entidad_tipo = ? and entidad_id = ?
                order by ocurrido_en, id
                """,
                (rs, fila) -> new EventoAuditoria(
                        rs.getObject("id", UUID.class),
                        rs.getObject("ocurrido_en", OffsetDateTime.class).toInstant(),
                        rs.getObject("usuario_id", UUID.class),
                        AccionAuditada.valueOf(rs.getString("accion")),
                        rs.getString("entidad_tipo"),
                        rs.getObject("entidad_id", UUID.class),
                        deJson(rs.getString("antes")),
                        deJson(rs.getString("despues")),
                        rs.getString("motivo")),
                entidadTipo, entidadId);
    }

    private static String aJson(Map<String, Object> mapa) {
        return mapa == null ? null : JSON.writeValueAsString(mapa);
    }

    private static Map<String, Object> deJson(String texto) {
        return texto == null ? null : JSON.readValue(texto, MAPA);
    }
}
