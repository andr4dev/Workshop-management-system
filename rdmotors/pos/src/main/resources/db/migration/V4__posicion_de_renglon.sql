-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V4: el orden de los renglones de una compra (spec 0002, fase 2)
--
--  El detalle de una compra se compara contra la factura en papel, renglón por
--  renglón. Sin un orden guardado, la base los devuelve en el orden que quiera.
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE linea_compra ADD COLUMN posicion integer;

-- Las compras que ya existen no guardaron su orden. El orden físico de la tabla
-- (ctid) es la mejor aproximación al de captura: los renglones de una compra se
-- insertaron juntos y nunca se han actualizado. Solo afecta datos de prueba.
UPDATE linea_compra l
SET posicion = orden.posicion
FROM (
    SELECT id, row_number() OVER (PARTITION BY compra_id ORDER BY ctid) - 1 AS posicion
    FROM linea_compra
) orden
WHERE orden.id = l.id;

ALTER TABLE linea_compra ALTER COLUMN posicion SET NOT NULL;

-- Sin UNIQUE (compra_id, posicion) a propósito: cuando llegue corregir una compra
-- (spec 0002, fase 5), el renglón corregido ocupa el lugar del que reemplaza y el
-- viejo se queda como historia con la misma posición.
