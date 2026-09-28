-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V28: cada gasto del mes dice si se reparte (spec 0014, decisión 5)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Hasta aquí el reporte decidía para todos los gastos del mes a la vez: el
-- selector "Repartidos / Solo en el mes". Ahora lo decide cada gasto, como en
-- el car‑wash: uno del mes se reparte día a día, o va entero, solo en el
-- reporte que cubre su mes.
--
-- Un gasto del mes SIEMPRE dice cuál (no hay "sin escoger" guardado); uno del
-- día, nunca: para él la pregunta no existe.
--
-- Los del mes que ya existían quedan repartidos: el selector arrancaba en
-- "Repartidos", así que así se veían.

ALTER TABLE gasto ADD COLUMN repartir boolean;

UPDATE gasto SET repartir = true WHERE del_mes;

ALTER TABLE gasto ADD CONSTRAINT ck_gasto_repartir CHECK (del_mes = (repartir IS NOT NULL));
