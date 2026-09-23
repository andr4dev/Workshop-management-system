-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — esquema inicial
--  Catalogo, inventario con kardex, y compras a proveedor.
--  Ventas, caja y sincronizacion llegan en migraciones siguientes.
-- ═══════════════════════════════════════════════════════════════════════════

-- ── Catalogo ────────────────────────────────────────────────────────────────

-- Tabla y no enum: el cliente crea, renombra y desactiva categorias sin despliegue.
CREATE TABLE categoria (
    id       uuid         PRIMARY KEY,
    nombre   varchar(80)  NOT NULL UNIQUE,
    orden    integer      NOT NULL DEFAULT 0,
    activa   boolean      NOT NULL DEFAULT true
);

-- El CONCEPTO de repuesto. La compatibilidad vehicular cuelga de aqui, no de la
-- variante: un filtro sirve para una Pulsar sin importar quien lo fabrico.
CREATE TABLE producto (
    id                   uuid          PRIMARY KEY,
    nombre               varchar(200)  NOT NULL,
    categoria_id         uuid          REFERENCES categoria(id),
    -- Texto crudo del proveedor, verbatim. NUNCA se sobrescribe con el resultado
    -- del parseo: es la fuente para auditar el emparejador de modelos.
    aplicacion_original  varchar(500),
    es_universal         boolean       NOT NULL DEFAULT false,
    activo               boolean       NOT NULL DEFAULT true
);

-- Lo que efectivamente se vende. Stock, costo y precio viven AQUI.
CREATE TABLE variante (
    id              uuid           PRIMARY KEY,
    producto_id     uuid           NOT NULL REFERENCES producto(id),
    codigo          varchar(60)    NOT NULL UNIQUE,
    codigo_barras   varchar(60),
    marca_repuesto  varchar(80)    NOT NULL,
    precio          numeric(14,2)  NOT NULL,
    stock           integer        NOT NULL DEFAULT 0,
    stock_minimo    integer        NOT NULL DEFAULT 5,
    -- NULL = nunca se compro. Null y no cero a proposito: un cero inventado haria
    -- que el producto reportara 100% de margen.
    costo_promedio  numeric(14,4),
    activa          boolean        NOT NULL DEFAULT true
);

CREATE INDEX idx_variante_producto      ON variante (producto_id);
CREATE INDEX idx_variante_codigo_barras ON variante (codigo_barras);
-- Alerta de stock bajo sin escanear la tabla entera.
CREATE INDEX idx_variante_stock_bajo    ON variante (stock) WHERE activa;

-- ── Proveedores y compras ───────────────────────────────────────────────────

CREATE TABLE proveedor (
    id        uuid          PRIMARY KEY,
    nombre    varchar(200)  NOT NULL,
    nit       varchar(40),
    telefono  varchar(40),
    activo    boolean       NOT NULL DEFAULT true
);

CREATE TABLE compra (
    id                 uuid           PRIMARY KEY,
    proveedor_id       uuid           NOT NULL REFERENCES proveedor(id),
    -- DOS FECHAS, a proposito. La factura del proveedor puede ser vieja y
    -- registrarse hoy; confundirlas mete compras fantasma en meses ya cerrados.
    fecha_documento    date           NOT NULL,
    fecha_registro     timestamptz    NOT NULL,
    numero_factura     varchar(60),
    total              numeric(14,2)  NOT NULL,
    registrado_por_id  uuid           NOT NULL
);

CREATE INDEX idx_compra_proveedor      ON compra (proveedor_id, fecha_documento);
CREATE INDEX idx_compra_fecha_registro ON compra (fecha_registro);

CREATE TABLE linea_compra (
    id              uuid           PRIMARY KEY,
    compra_id       uuid           NOT NULL REFERENCES compra(id),
    variante_id     uuid           NOT NULL REFERENCES variante(id),
    cantidad        integer        NOT NULL CHECK (cantidad > 0),
    -- AUTORITATIVO: es lo que salio de la caja por este renglon.
    costo_total     numeric(14,2)  NOT NULL,
    -- DERIVADO, con 4 decimales. Guardar el unitario redondeado a entero hace que
    -- 15 x 13.333 = 199.995 y falten $5 contra la factura del proveedor.
    costo_unitario  numeric(14,4)  NOT NULL,
    modo_captura    varchar(10)    NOT NULL CHECK (modo_captura IN ('TOTAL','UNITARIO')),
    -- NULL = no tocar el precio del producto.
    precio_venta    numeric(14,2)
);

CREATE INDEX idx_linea_compra_compra   ON linea_compra (compra_id);
CREATE INDEX idx_linea_compra_variante ON linea_compra (variante_id);

-- ── Kardex ──────────────────────────────────────────────────────────────────

-- APPEND-ONLY. Nunca se edita ni se borra: corregir es agregar un movimiento nuevo.
-- Esto es lo que convierte al kardex en la auditoria de inventario.
CREATE TABLE movimiento_kardex (
    id                       uuid           PRIMARY KEY,
    variante_id              uuid           NOT NULL REFERENCES variante(id),
    tipo                     varchar(20)    NOT NULL
        CHECK (tipo IN ('COMPRA','VENTA','DEVOLUCION','AJUSTE','REVERSION')),
    cantidad_delta           integer        NOT NULL,
    costo_unitario           numeric(14,4),
    costo_total              numeric(14,2),
    -- Snapshot: cada fila se puede leer sola, sin recalcular la historia entera.
    saldo_despues            integer        NOT NULL,
    costo_promedio_despues   numeric(14,4),
    origen_tipo              varchar(20)    NOT NULL
        CHECK (origen_tipo IN ('COMPRA','VENTA','DEVOLUCION','AJUSTE_MANUAL')),
    origen_id                uuid,
    movimiento_revertido_id  uuid,
    motivo                   varchar(300),
    registrado_por_id        uuid           NOT NULL,
    creado_en                timestamptz    NOT NULL
);

CREATE INDEX idx_kardex_variante ON movimiento_kardex (variante_id, creado_en);
CREATE INDEX idx_kardex_origen   ON movimiento_kardex (origen_tipo, origen_id);
