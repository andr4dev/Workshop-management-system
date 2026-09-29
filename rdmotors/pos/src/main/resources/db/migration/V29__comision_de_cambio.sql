-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTOS — V29: qué repuestos pagan comisión por cambio de aceite
--  (spec 0015, fase 1, decisión 1)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Cuando se vende un aceite y se cambia en la tienda, una parte es de quien
-- hizo el cambio: hoy $3.000. Qué es "un aceite" no se puede sacar de la
-- categoría (los KIXX están en LUBRICANTES Y QUIMICOS, los MOTUL en MOTOR, y en
-- LUBRICANTES también está el lubricante de cadena): lo dice cada repuesto.
--
-- Nulo: no paga comisión. Todos los que ya existen quedan así; el administrador
-- marca los que pagan desde la ficha.

ALTER TABLE variante ADD COLUMN comision_cambio numeric(14,2);

ALTER TABLE variante ADD CONSTRAINT ck_variante_comision_cambio
    CHECK (comision_cambio IS NULL OR comision_cambio > 0);
