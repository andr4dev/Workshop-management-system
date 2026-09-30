-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTOS — V31: la deuda de un fiado, por producto (spec 0016)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Desde el 0016 cada producto fiado de una venta es su propia deuda: así se puede pagar un producto solo. La deuda
-- guarda el renglón de la venta, su posición (dentro de una venta se paga en ese orden) y el nombre del producto
-- como una foto.
--
-- Las de antes (decisión 4 del spec):
--   · de un solo producto: se les anota el producto, sin cambiar un peso ni sus abonos;
--   · de varios productos, sin abonos, sin anular y sin nada pagado al llevárselo: se parten, cada producto con lo
--     que vale (su total menos su parte del descuento, repartido como RepartoDeDescuento: piso, y el sobrante al más
--     caro, el primero si empatan). La fila original queda como el primer producto, con su id;
--   · las demás quedan de la venta entera: no se sabe qué producto cubrieron sus abonos o lo que se pagó.

ALTER TABLE deuda
    ADD COLUMN linea_venta_id uuid         REFERENCES linea_venta (id),
    ADD COLUMN posicion       integer,
    ADD COLUMN descripcion    varchar(200);

-- Las tres juntas, y solo en la deuda de una venta.
ALTER TABLE deuda ADD CONSTRAINT ck_deuda_de_producto CHECK (
    (linea_venta_id IS NULL AND posicion IS NULL AND descripcion IS NULL)
    OR (linea_venta_id IS NOT NULL AND posicion IS NOT NULL AND btrim(descripcion) <> '' AND origen = 'VENTA'));

-- Un producto se fía una vez; una venta entera, una vez. (Que una venta no tenga a la vez deuda entera y por
-- producto lo cuida el dominio: fiar crea una u otras, nunca las dos.)
DROP INDEX ux_deuda_venta;
CREATE UNIQUE INDEX ux_deuda_linea ON deuda (linea_venta_id) WHERE linea_venta_id IS NOT NULL;
CREATE UNIQUE INDEX ux_deuda_venta_entera ON deuda (venta_id) WHERE venta_id IS NOT NULL AND linea_venta_id IS NULL;
CREATE INDEX idx_deuda_venta ON deuda (venta_id) WHERE venta_id IS NOT NULL;

-- ── Las de un solo producto: se les anota cuál ──────────────────────────────
UPDATE deuda d
SET linea_venta_id = l.id, posicion = l.posicion, descripcion = p.nombre
FROM linea_venta l
JOIN variante va ON va.id = l.variante_id
JOIN producto p ON p.id = va.producto_id
WHERE d.origen = 'VENTA'
  AND l.venta_id = d.venta_id
  AND (SELECT count(*) FROM linea_venta x WHERE x.venta_id = d.venta_id) = 1;

-- ── Las de varios productos que se pueden partir ────────────────────────────
CREATE TEMP TABLE renglon_por_partir ON COMMIT DROP AS
WITH partir AS (
    SELECT d.id AS deuda_id, v.id AS venta_id, v.subtotal, v.descuento_monto AS descuento
    FROM deuda d
    JOIN venta v ON v.id = d.venta_id
    WHERE d.origen = 'VENTA'
      AND d.linea_venta_id IS NULL
      AND d.anulada_en IS NULL
      AND d.monto = v.total AND v.fiado = v.total
      AND v.subtotal > 0
      AND (SELECT count(*) FROM linea_venta x WHERE x.venta_id = v.id) > 1
      AND NOT EXISTS (SELECT 1 FROM aplicacion_abono ap WHERE ap.deuda_id = d.id)
), renglones AS (
    SELECT pa.deuda_id, l.id AS linea_id, l.posicion, p.nombre, l.total, pa.descuento,
           floor(pa.descuento * l.total / pa.subtotal) AS parte,
           row_number() OVER (PARTITION BY pa.deuda_id ORDER BY l.total DESC, l.posicion) AS rango_caro
    FROM partir pa
    JOIN linea_venta l ON l.venta_id = pa.venta_id
    JOIN variante va ON va.id = l.variante_id
    JOIN producto p ON p.id = va.producto_id
)
SELECT deuda_id, linea_id, posicion, nombre,
       total - parte
           - CASE WHEN rango_caro = 1 THEN descuento - sum(parte) OVER (PARTITION BY deuda_id) ELSE 0 END AS neto
FROM renglones;

-- Un producto que quedó en $0 (uno de regalo) no se debe.
DELETE FROM renglon_por_partir WHERE neto <= 0;

CREATE TEMP TABLE primero_por_partir ON COMMIT DROP AS
SELECT DISTINCT ON (deuda_id) deuda_id, linea_id
FROM renglon_por_partir
ORDER BY deuda_id, posicion;

-- Los demás productos: filas nuevas, con todo lo de la deuda original menos el monto.
INSERT INTO deuda (id, version, cliente_id, origen, venta_id, numero_venta, fecha, registrada_en, registrada_por_id,
                   monto, abonado, motivo, debe_despues, anulada_en, anulada_por_id,
                   linea_venta_id, posicion, descripcion)
SELECT gen_random_uuid(), 0, d.cliente_id, d.origen, d.venta_id, d.numero_venta, d.fecha, d.registrada_en,
       d.registrada_por_id, r.neto, 0, NULL, d.debe_despues, NULL, NULL,
       r.linea_id, r.posicion, r.nombre
FROM renglon_por_partir r
JOIN deuda d ON d.id = r.deuda_id
WHERE NOT EXISTS (SELECT 1 FROM primero_por_partir pr WHERE pr.linea_id = r.linea_id);

-- El primero se queda con la fila original.
UPDATE deuda d
SET monto = r.neto, linea_venta_id = r.linea_id, posicion = r.posicion, descripcion = r.nombre
FROM renglon_por_partir r
JOIN primero_por_partir pr ON pr.linea_id = r.linea_id
WHERE d.id = r.deuda_id;
