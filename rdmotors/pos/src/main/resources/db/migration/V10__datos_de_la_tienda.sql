-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V10: los datos de la tienda (spec 0003, fase 4, RF-033)
-- ═══════════════════════════════════════════════════════════════════════════

-- Lo que encabeza el comprobante. La tienda es UNA: la tabla tiene una sola fila,
-- y lo exige la base con el CHECK del id, no la aplicación. No hay "crear" ni
-- "borrar": la pantalla solo reemplaza.
CREATE TABLE datos_tienda (
    id                smallint      PRIMARY KEY CHECK (id = 1),
    -- Lo primero del ticket. Sin nombre, el comprobante no dice de dónde es.
    nombre_comercial  varchar(80)   NOT NULL CHECK (btrim(nombre_comercial) <> ''),
    -- Opcionales: vacíos se guardan como NULL, para que el ticket no imprima una
    -- línea "NIT" sin número.
    nit               varchar(30)   CHECK (btrim(nit) <> ''),
    direccion         varchar(120)  CHECK (btrim(direccion) <> ''),
    telefono          varchar(40)   CHECK (btrim(telefono) <> ''),
    mensaje_pie       varchar(160)  CHECK (btrim(mensaje_pie) <> '')
);

-- Arranca con el nombre y nada más, mientras el cliente da NIT, dirección y
-- teléfono (spec 0003, pregunta abierta 1). Así la primera venta ya tiene
-- encabezado.
INSERT INTO datos_tienda (id, nombre_comercial) VALUES (1, 'RD MOTORS');
