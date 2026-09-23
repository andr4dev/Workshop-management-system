-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V6: lo necesario para deshacer una compra (spec 0002, fase 4)
-- ═══════════════════════════════════════════════════════════════════════════

-- ── Kardex: dos tipos nuevos ────────────────────────────────────────────────
-- Las reversiones de compra SÍ recalculan el promedio. REVERSION queda para
-- anular ventas, que nunca lo tocan.
ALTER TABLE movimiento_kardex DROP CONSTRAINT movimiento_kardex_tipo_check;
ALTER TABLE movimiento_kardex ADD CONSTRAINT movimiento_kardex_tipo_check
    CHECK (tipo IN ('COMPRA', 'VENTA', 'DEVOLUCION', 'AJUSTE', 'REVERSION',
                    'CORRECCION_COMPRA', 'ANULACION_COMPRA'));

-- ── Kardex: el orden sin empates ────────────────────────────────────────────
-- Al corregir un renglón, la salida que lo revierte y la entrada corregida del
-- mismo repuesto nacen en el mismo instante. creado_en no alcanza para decir
-- cuál fue primero; la secuencia sí.
ALTER TABLE movimiento_kardex ADD COLUMN secuencia bigint;

-- Las filas que ya existen se numeran por fecha, explícitamente. Agregar la
-- columna como identidad de una vez las numeraría en orden físico, que no es
-- el cronológico.
UPDATE movimiento_kardex m
SET secuencia = orden.n
FROM (
    SELECT id, row_number() OVER (ORDER BY creado_en, id) AS n
    FROM movimiento_kardex
) orden
WHERE orden.id = m.id;

ALTER TABLE movimiento_kardex ALTER COLUMN secuencia SET NOT NULL;

-- Desde aquí la asigna la base, arrancando después de la última existente.
DO $$
DECLARE
    siguiente bigint;
BEGIN
    SELECT coalesce(max(secuencia), 0) + 1 INTO siguiente FROM movimiento_kardex;
    EXECUTE format(
        'ALTER TABLE movimiento_kardex ALTER COLUMN secuencia ADD GENERATED ALWAYS AS IDENTITY (START WITH %s)',
        siguiente);
END $$;

CREATE UNIQUE INDEX ux_kardex_secuencia ON movimiento_kardex (secuencia);
CREATE INDEX idx_kardex_variante_secuencia ON movimiento_kardex (variante_id, secuencia);

-- ── Renglones de compra ─────────────────────────────────────────────────────

-- El precio que tenía el repuesto antes de que el renglón le fijara uno. Los
-- renglones que ya existen no lo guardaron: al revertirlos, el precio no vuelve
-- y la pantalla lo avisa. Solo afecta datos de prueba.
ALTER TABLE linea_compra ADD COLUMN precio_anterior numeric(14,2);

-- El movimiento con que el renglón entró al inventario: lo que se revierte.
-- Sin clave foránea a propósito: el renglón se guarda con la compra y el
-- movimiento con el kardex, y el orden de esas inserciones no debe importar.
ALTER TABLE linea_compra ADD COLUMN movimiento_entrada_id uuid;

-- Los renglones que ya existen se emparejan con su movimiento de COMPRA por
-- compra y repuesto. El row_number cubre renglones repetidos de datos viejos de
-- prueba, de antes de que se rechazara el mismo repuesto dos veces.
WITH renglones AS (
    SELECT id, compra_id, variante_id,
           row_number() OVER (PARTITION BY compra_id, variante_id ORDER BY posicion) AS n
    FROM linea_compra
), entradas AS (
    SELECT id, origen_id, variante_id,
           row_number() OVER (PARTITION BY origen_id, variante_id ORDER BY secuencia) AS n
    FROM movimiento_kardex
    WHERE tipo = 'COMPRA' AND origen_tipo = 'COMPRA'
)
UPDATE linea_compra l
SET movimiento_entrada_id = entradas.id
FROM renglones
JOIN entradas ON entradas.origen_id = renglones.compra_id
             AND entradas.variante_id = renglones.variante_id
             AND entradas.n = renglones.n
WHERE l.id = renglones.id;

-- Un renglón corregido no se borra: se da de baja y queda como historia.
ALTER TABLE linea_compra ADD COLUMN vigente boolean NOT NULL DEFAULT true;
ALTER TABLE linea_compra ADD COLUMN reemplazada_en timestamptz;

CREATE INDEX idx_linea_compra_variante_vigente ON linea_compra (variante_id) WHERE vigente;
