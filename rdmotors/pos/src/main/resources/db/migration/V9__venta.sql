-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V9: la venta de mostrador (spec 0003, fase 2)
-- ═══════════════════════════════════════════════════════════════════════════

-- ── El número del comprobante ──────────────────────────────────────────────
-- Una FILA CONTADOR y no una secuencia de Postgres. Una secuencia no retrocede
-- con un rollback: un cobro que falla se "comería" su número y dejaría un hueco,
-- y un hueco en una serie de comprobantes se lee como una venta borrada
-- (SPEC_Modelo_Datos.md §3.5.1). La fila se actualiza dentro de la transacción
-- del cobro: si el cobro se deshace, el número vuelve.
CREATE TABLE consecutivo (
    nombre  varchar(20)  PRIMARY KEY,
    ultimo  bigint       NOT NULL CHECK (ultimo >= 0)
);

-- Arranca en 1. Si el cliente trae la numeración de un talonario, se ajusta
-- este valor antes de la primera venta.
INSERT INTO consecutivo (nombre, ultimo) VALUES ('VENTA', 0);

-- ── La venta ───────────────────────────────────────────────────────────────
CREATE TABLE venta (
    id                    uuid           PRIMARY KEY,
    version               bigint         NOT NULL DEFAULT 0,
    numero                bigint         NOT NULL,
    -- Por enlace, nunca por fecha: un turno puede cruzar la medianoche.
    turno_id              uuid           NOT NULL REFERENCES turno_caja (id),
    vendido_por_id        uuid           NOT NULL,
    cobrada_en            timestamptz    NOT NULL,
    subtotal              numeric(14,2)  NOT NULL CHECK (subtotal >= 0),
    -- El descuento se guarda en PESOS; el porcentaje es cómo se capturó.
    descuento_monto       numeric(14,2)  NOT NULL DEFAULT 0 CHECK (descuento_monto >= 0),
    descuento_modo        varchar(10)    CHECK (descuento_modo IN ('MONTO', 'PORCENTAJE')),
    descuento_porcentaje  numeric(5,2)   CHECK (descuento_porcentaje > 0 AND descuento_porcentaje <= 100),
    descuento_motivo      varchar(300),
    total                 numeric(14,2)  NOT NULL,
    estado                varchar(10)    NOT NULL CHECK (estado IN ('COBRADA', 'ANULADA')),
    -- La llave contra el doble cobro: nace con la venta en el navegador.
    llave_idempotencia    uuid           NOT NULL,
    anulada_en            timestamptz,
    anulada_por_id        uuid,
    -- Lo que devuelve la caja al anular cuenta en el turno de ESE momento.
    anulada_en_turno_id   uuid           REFERENCES turno_caja (id),
    motivo_anulacion      varchar(300),

    -- Las partes suman el total, también para quien inserte por otro camino.
    CONSTRAINT ck_venta_total
        CHECK (total = subtotal - descuento_monto AND total >= 0),
    -- Hay descuento si y solo si dice cómo se capturó y por qué.
    CONSTRAINT ck_venta_descuento
        CHECK ((descuento_monto > 0) = (descuento_modo IS NOT NULL AND descuento_motivo IS NOT NULL)),
    CONSTRAINT ck_venta_porcentaje_segun_modo
        CHECK ((coalesce(descuento_modo, '') = 'PORCENTAJE') = (descuento_porcentaje IS NOT NULL)),
    -- Una anulada dice cuándo, quién, en qué turno y por qué; una cobrada, nada de eso.
    CONSTRAINT ck_venta_datos_de_anulacion
        CHECK ((estado = 'ANULADA') = (anulada_en IS NOT NULL AND anulada_por_id IS NOT NULL
                                       AND anulada_en_turno_id IS NOT NULL AND motivo_anulacion IS NOT NULL))
);

CREATE UNIQUE INDEX ux_venta_numero ON venta (numero);
CREATE UNIQUE INDEX ux_venta_llave ON venta (llave_idempotencia);
CREATE INDEX idx_venta_turno ON venta (turno_id, numero);
CREATE INDEX idx_venta_cobrada_en ON venta (cobrada_en);

-- ── Los renglones ──────────────────────────────────────────────────────────
CREATE TABLE linea_venta (
    id                    uuid           PRIMARY KEY,
    venta_id              uuid           NOT NULL REFERENCES venta (id),
    posicion              integer        NOT NULL CHECK (posicion >= 0),
    variante_id           uuid           NOT NULL REFERENCES variante (id),
    cantidad              integer        NOT NULL CHECK (cantidad > 0),
    -- Una foto del precio al vender. No se vende a $0.
    precio_unitario       numeric(14,2)  NOT NULL CHECK (precio_unitario > 0),
    total                 numeric(14,2)  NOT NULL,
    -- Con qué movimiento de kardex salió. Sin llave foránea, como el movimiento de
    -- entrada de linea_compra: el orden de los inserts lo decide Hibernate.
    movimiento_salida_id  uuid,

    CONSTRAINT ck_linea_venta_total CHECK (total = precio_unitario * cantidad),
    CONSTRAINT ux_linea_venta_repuesto UNIQUE (venta_id, variante_id)
);

CREATE INDEX idx_linea_venta_variante ON linea_venta (variante_id);

-- ── Los pagos ──────────────────────────────────────────────────────────────
CREATE TABLE pago_venta (
    id        uuid           PRIMARY KEY,
    venta_id  uuid           NOT NULL REFERENCES venta (id),
    forma     varchar(15)    NOT NULL CHECK (forma IN ('EFECTIVO', 'TRANSFERENCIA')),
    monto     numeric(14,2)  NOT NULL CHECK (monto > 0),
    -- El billete que entregó el cliente, para el cambio del comprobante.
    recibido  numeric(14,2),

    CONSTRAINT ck_pago_venta_recibido
        CHECK (recibido IS NULL OR (forma = 'EFECTIVO' AND recibido >= monto)),
    CONSTRAINT ux_pago_venta_forma UNIQUE (venta_id, forma)
);

-- ── La auditoría conoce las acciones de venta ──────────────────────────────
ALTER TABLE evento_auditoria DROP CONSTRAINT evento_auditoria_accion_check;
ALTER TABLE evento_auditoria ADD CONSTRAINT evento_auditoria_accion_check
    CHECK (accion IN ('CORREGIR_COMPRA', 'ANULAR_COMPRA', 'CORREGIR_REPUESTO',
                      'APLICAR_DESCUENTO', 'ANULAR_VENTA'));
