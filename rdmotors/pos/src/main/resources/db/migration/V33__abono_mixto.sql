-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTOS — V33: el abono mixto, parte en efectivo y parte por transferencia (spec 0017, 2026-10-09)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Un abono mixto son DOS abonos que nacen juntos: uno en efectivo (entra al cajón) y uno por transferencia (no). Cada
-- uno sigue teniendo una sola forma, así que la caja, el arqueo y los reportes los leen como siempre. Lo único nuevo
-- es lo que los une: el pago del que son parte.
--
-- Los abonos de antes quedan con pago_id nulo: no hay nada que llenar y ninguna cifra cambia.

ALTER TABLE abono ADD COLUMN pago_id uuid;

-- Un pago tiene a lo más una parte de cada forma. Sirve también para buscar las partes de un pago.
CREATE UNIQUE INDEX ux_abono_pago_forma ON abono (pago_id, forma) WHERE pago_id IS NOT NULL;
