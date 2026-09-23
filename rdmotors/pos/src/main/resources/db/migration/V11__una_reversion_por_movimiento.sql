-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V11: un movimiento se revierte una sola vez (spec 0003, fase 5)
-- ═══════════════════════════════════════════════════════════════════════════

-- Una salida de venta se deshace una vez: anular dos veces la misma venta
-- devolvería su stock dos veces. El caso de uso ya lo impide (bloquea la venta y
-- revisa que siga cobrada); la base lo exige también, para quien llegue por otro
-- camino. Igual con las entradas de compra: cada renglón corregido o anulado
-- revierte su entrada una sola vez.
--
-- Además, "¿esta salida tiene su reversión?" es la pregunta de RF-030 al decidir
-- si una compra se puede corregir: con el índice es una búsqueda, no un recorrido
-- del kardex.
CREATE UNIQUE INDEX ux_kardex_revertido
    ON movimiento_kardex (movimiento_revertido_id)
    WHERE movimiento_revertido_id IS NOT NULL;
