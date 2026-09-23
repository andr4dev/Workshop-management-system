-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V15: gastos del mes (spec 0007, fase 1)
-- ═══════════════════════════════════════════════════════════════════════════

-- El arriendo y la nómina no ocurren un día: son del mes. El reporte de
-- resultados los reparte entre los días de su mes o los muestra solo en el mes
-- (spec 0007, decisión 1). Cada gasto dice si es del mes; la categoría lo
-- sugiere al registrar.

-- Los gastos que ya existen se registraron sin esa pregunta: quedan como
-- gastos de su día, que es como se leían hasta hoy.
ALTER TABLE gasto ADD COLUMN del_mes boolean NOT NULL DEFAULT false;
ALTER TABLE gasto ALTER COLUMN del_mes DROP DEFAULT;

ALTER TABLE categoria_gasto ADD COLUMN mensual boolean NOT NULL DEFAULT false;

-- Las sembradas que se pagan cada mes. Por nombre: son las de V12, y si el
-- cliente ya las renombró, se quedan como las dejó.
UPDATE categoria_gasto SET mensual = true
WHERE nombre IN ('Arriendo', 'Nómina', 'Servicios públicos', 'Internet y teléfono');
