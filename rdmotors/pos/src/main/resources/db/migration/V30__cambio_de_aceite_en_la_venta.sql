-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTOS — V30: el cambio de aceite en la venta (spec 0015, versión 2, fase 2)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- El renglón de un repuesto que paga comisión por cambio dice qué se escogió:
--   SE_CAMBIA     se cobra el precio del repuesto, y la comisión sale del cajón
--                 para quien hizo el cambio: un gasto del cajón, de costo.
--   NO_SE_CAMBIA  el cliente se lo lleva: el precio baja la comisión.
-- Los renglones de antes, y los de repuestos que no pagan, quedan en nulo.

ALTER TABLE linea_venta
    ADD COLUMN cambio            varchar(15)   CHECK (cambio IN ('SE_CAMBIA', 'NO_SE_CAMBIA')),
    ADD COLUMN cambio_por_id     uuid          REFERENCES usuario (id),
    -- La comisión del renglón (la del repuesto al cobrar × la cantidad): la que se pagó, o la que se descontó.
    ADD COLUMN comision          numeric(14,2) CHECK (comision > 0),
    ADD COLUMN comision_gasto_id uuid          REFERENCES gasto (id);

ALTER TABLE linea_venta ADD CONSTRAINT ck_linea_venta_cambio CHECK (
    (cambio IS NULL AND cambio_por_id IS NULL AND comision IS NULL AND comision_gasto_id IS NULL)
    OR (cambio = 'NO_SE_CAMBIA' AND comision IS NOT NULL AND cambio_por_id IS NULL AND comision_gasto_id IS NULL)
    OR (cambio = 'SE_CAMBIA' AND comision IS NOT NULL AND cambio_por_id IS NOT NULL AND comision_gasto_id IS NOT NULL));

-- La categoría del sistema donde caen las comisiones: costo, porque es plata que se va por la venta del aceite.
INSERT INTO categoria_gasto (id, nombre, naturaleza)
VALUES (gen_random_uuid(), 'Comisión cambio de aceite', 'COSTO')
ON CONFLICT DO NOTHING;
