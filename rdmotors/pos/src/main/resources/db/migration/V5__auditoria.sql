-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V5: auditoría y control de concurrencia (spec 0002, fase 3)
-- ═══════════════════════════════════════════════════════════════════════════

-- APPEND-ONLY, como el kardex: un evento nunca se edita ni se borra.
-- Una sola tabla para todo lo sensible (SPEC_Modelo_Datos §3.7): la pregunta
-- "¿qué hizo Juan ayer?" tiene que responderse con una consulta, no con cinco.
CREATE TABLE evento_auditoria (
    id            uuid          PRIMARY KEY,
    ocurrido_en   timestamptz   NOT NULL,
    -- Hasta la rebanada 2 es el usuario provisional que manda el navegador.
    usuario_id    uuid          NOT NULL,
    accion        varchar(40)   NOT NULL
        CHECK (accion IN ('CORREGIR_COMPRA', 'ANULAR_COMPRA', 'CORREGIR_REPUESTO')),
    entidad_tipo  varchar(40)   NOT NULL,
    entidad_id    uuid          NOT NULL,
    -- Para que lo lea una persona. Los reportes leen las columnas concretas.
    antes         jsonb,
    despues       jsonb,
    motivo        varchar(300)
);

CREATE INDEX idx_auditoria_entidad ON evento_auditoria (entidad_tipo, entidad_id, ocurrido_en);
CREATE INDEX idx_auditoria_usuario ON evento_auditoria (usuario_id, ocurrido_en);

-- La versión que la pantalla devuelve al corregir: si otra corrección se guardó
-- en el medio, no coincide y se rechaza en vez de pisarla.
ALTER TABLE compra ADD COLUMN version bigint NOT NULL DEFAULT 0;

-- Toda corrección la toca, así que la fila cambia y la versión sube aunque solo
-- se haya corregido el precio de un renglón.
ALTER TABLE compra ADD COLUMN modificada_en timestamptz;
