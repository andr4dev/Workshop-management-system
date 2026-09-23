-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V24: el correo del cierre de caja (spec 0010)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Una cola, no un envío directo como en el car-wash: el servidor vive en la
-- tienda y puede no tener internet al cerrar. El cierre deja aquí su correo
-- en el mismo commit, y una tarea lo manda cuando puede, reintentando.
--
-- No se guarda el cuerpo: se arma al mandarlo, con las cifras firmadas del
-- cierre, para que lleve las observaciones que se escriben justo después.

CREATE TABLE correo (
    id                 uuid          PRIMARY KEY,
    tipo               varchar(20)   NOT NULL CHECK (tipo IN ('CIERRE_DE_TURNO', 'PRUEBA')),
    -- El turno del resumen. Sin llave foránea a propósito: un correo que ya
    -- salió no tiene por qué impedir nada sobre su turno, y el turno nunca se
    -- borra de todos modos.
    turno_id           uuid,
    destinatarios      varchar(700)  NOT NULL CHECK (length(destinatarios) > 0),
    estado             varchar(12)   NOT NULL CHECK (estado IN ('POR_MANDAR', 'ENVIADO', 'FALLO')),
    intentos           integer       NOT NULL DEFAULT 0 CHECK (intentos >= 0),
    creado_en          timestamptz   NOT NULL,
    no_antes_de        timestamptz   NOT NULL,
    ultimo_intento_en  timestamptz,
    ultimo_error       varchar(1000),
    enviado_en         timestamptz,
    id_en_brevo        varchar(200),

    -- El del cierre sabe de qué turno es; el de prueba, no.
    CONSTRAINT ck_correo_turno_segun_tipo CHECK ((tipo = 'CIERRE_DE_TURNO') = (turno_id IS NOT NULL)),
    -- Uno que salió dice cuándo; uno que no, no.
    CONSTRAINT ck_correo_enviado_con_fecha CHECK ((estado = 'ENVIADO') = (enviado_en IS NOT NULL))
);

-- La tarea de cada minuto pregunta por los que ya se pueden intentar: son
-- pocos, y el índice parcial los tiene a mano aunque la tabla crezca.
CREATE INDEX idx_correo_por_mandar ON correo (no_antes_de) WHERE estado = 'POR_MANDAR';
CREATE INDEX idx_correo_creado_en ON correo (creado_en DESC);

-- Un turno deja un solo resumen: si el cierre se reintentara, no habría dos.
CREATE UNIQUE INDEX ux_correo_cierre_por_turno ON correo (turno_id) WHERE tipo = 'CIERRE_DE_TURNO';

COMMENT ON TABLE correo IS
    'La cola de correos (spec 0010): se escribe en el commit del cierre y la manda una tarea, reintentando sin internet.';

-- A quién le llega el resumen. Una sola fila; vacío apaga el correo.
-- La llave de Brevo NO va aquí: es un secreto y vive en la configuración.
CREATE TABLE ajustes_correo (
    id                  smallint      PRIMARY KEY CHECK (id = 1),
    destinatarios       varchar(700)  NOT NULL DEFAULT '',
    actualizado_en      timestamptz,
    actualizado_por_id  uuid          REFERENCES usuario (id)
);

INSERT INTO ajustes_correo (id, destinatarios) VALUES (1, '');
