-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V23: el registro de los respaldos (spec 0009, H2)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- La copia vive en el disco; esta tabla es el registro de que se hizo, o de
-- que falló. Se guarda también lo que falló: un respaldo que falla en silencio
-- es peor que no tenerlo, porque el dueño cree que está cubierto.
--
-- No lleva llave foránea a 'usuario' en 'pedido_por_id' a propósito: el
-- respaldo automático no lo pide nadie, y si alguna vez se restaura esta tabla
-- junto a una base sin ese usuario, el registro del respaldo tiene que
-- sobrevivir igual.

CREATE TABLE respaldo (
    id                  uuid          PRIMARY KEY,
    hecho_en            timestamptz   NOT NULL,
    -- Dónde quedó el archivo. Se conserva aunque el archivo ya se haya podado.
    archivo             varchar(400)  NOT NULL,
    bytes               bigint        CHECK (bytes IS NULL OR bytes >= 0),
    duracion_ms         bigint        NOT NULL CHECK (duracion_ms >= 0),
    estado              varchar(10)   NOT NULL CHECK (estado IN ('HECHO', 'FALLO')),
    error               varchar(1000),
    segunda_copia       varchar(400),
    aviso_segunda_copia varchar(1000),
    origen              varchar(12)   NOT NULL CHECK (origen IN ('AUTOMATICO', 'A_MANO')),
    pedido_por_id       uuid,
    -- Cuándo se borró el archivo por viejo (RF-004); la fila queda como historia.
    archivo_borrado_en  timestamptz,

    -- Una copia que salió bien tiene su tamaño y no tiene error; una que falló,
    -- al revés. Sin esto, una fila podría decir "hecho" sin que exista nada.
    CONSTRAINT ck_respaldo_hecho_o_fallo CHECK (
        (estado = 'HECHO' AND bytes IS NOT NULL AND error IS NULL)
     OR (estado = 'FALLO' AND bytes IS NULL     AND error IS NOT NULL)
    ),
    -- Lo que falló no dejó archivo que borrar.
    CONSTRAINT ck_respaldo_borrado_solo_si_hubo CHECK (
        archivo_borrado_en IS NULL OR estado = 'HECHO'
    )
);

-- La pantalla siempre pregunta por las últimas.
CREATE INDEX idx_respaldo_hecho_en ON respaldo (hecho_en DESC);

COMMENT ON TABLE respaldo IS
    'Las copias de la base (spec 0009, H2): cuándo, dónde quedó, cuánto pesó y, si falló, por qué.';
