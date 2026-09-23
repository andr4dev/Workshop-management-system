-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V22: la llave contra el doble clic en compras (spec 0009, RF-009)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Cobrar, el gasto, el retiro y el abono ya llevan llave: reintentar es seguro.
-- Registrar una compra era la única escritura que SUMA —sube el stock y
-- recalcula el costo promedio— y no la tenía: un doble clic con la red lenta
-- dejaba la mercancía entrada dos veces.
--
-- Las compras que ya existen reciben cada una su propia llave. El DEFAULT es
-- gen_random_uuid(), que se evalúa FILA POR FILA: un valor fijo haría chocar
-- la segunda compra contra el índice único de abajo.

ALTER TABLE compra
    ADD COLUMN llave_idempotencia uuid NOT NULL DEFAULT gen_random_uuid();

-- El DEFAULT era solo para las filas viejas: de aquí en adelante la llave la
-- pone quien registra, y una compra sin llave tiene que fallar, no inventarse una.
ALTER TABLE compra
    ALTER COLUMN llave_idempotencia DROP DEFAULT;

CREATE UNIQUE INDEX ux_compra_llave ON compra (llave_idempotencia);

COMMENT ON COLUMN compra.llave_idempotencia IS
    'La llave contra el doble registro. Nace en la pantalla de compra y se renueva solo cuando la compra queda guardada.';
