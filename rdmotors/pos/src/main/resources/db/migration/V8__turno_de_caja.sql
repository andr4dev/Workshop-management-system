-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V8: el turno de caja (spec 0003, fase 1)
-- ═══════════════════════════════════════════════════════════════════════════

-- La caja es por TURNO, no por día (SPEC_Modelo_Datos.md §3.6): un cajero
-- responde por lo que pasó mientras su turno estuvo abierto. Las ventas apuntan
-- a su turno por enlace, nunca por fecha, así que un turno puede cruzar la
-- medianoche sin partirse.
CREATE TABLE turno_caja (
    id              uuid           PRIMARY KEY,
    abierto_por_id  uuid           NOT NULL,
    abierto_en      timestamptz    NOT NULL,
    -- La plata con que arranca el cajón. $0 vale; negativo no existe.
    fondo           numeric(14,2)  NOT NULL CHECK (fondo >= 0),
    estado          varchar(10)    NOT NULL CHECK (estado IN ('ABIERTO', 'CERRADO')),
    -- El cierre con arqueo llega en la rebanada 3. Las columnas existen desde ya
    -- para que esa migración no tenga que tocar filas de turnos viejos.
    cerrado_por_id  uuid,
    cerrado_en      timestamptz,

    -- Un turno cerrado dice cuándo y quién; uno abierto, nada de eso.
    CONSTRAINT ck_turno_datos_de_cierre
        CHECK ((estado = 'CERRADO') = (cerrado_en IS NOT NULL AND cerrado_por_id IS NOT NULL))
);

-- SOLO UN TURNO ABIERTO. Lo garantiza la base y no la aplicación: dos aperturas
-- simultáneas pasarían las dos la revisión del caso de uso. Con dos turnos
-- abiertos, una venta no sabría a cuál arqueo pertenece.
CREATE UNIQUE INDEX ux_turno_abierto ON turno_caja ((estado)) WHERE estado = 'ABIERTO';
