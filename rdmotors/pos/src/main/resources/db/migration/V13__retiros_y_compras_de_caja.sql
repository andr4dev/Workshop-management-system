-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V13: retiros y compras pagadas con plata del cajón (spec 0006, fase 2)
-- ═══════════════════════════════════════════════════════════════════════════

-- ── Los retiros ────────────────────────────────────────────────────────────
-- Plata que sale del cajón sin ser un gasto: el dueño se lleva $100.000. Tabla
-- aparte y no un tipo dentro de gasto: un retiro no tiene categoría, siempre es
-- de un turno y no cuenta como gasto en ningún reporte.
CREATE TABLE retiro_caja (
    id                  uuid           PRIMARY KEY,
    version             bigint         NOT NULL DEFAULT 0,
    turno_id            uuid           NOT NULL REFERENCES turno_caja (id),
    monto               numeric(14,2)  NOT NULL CHECK (monto > 0),
    -- Quién se la llevó o para qué. Sin motivo, un retiro tapa cualquier faltante.
    motivo              varchar(300)   NOT NULL CHECK (btrim(motivo) <> ''),
    registrado_por_id   uuid           NOT NULL,
    registrado_en       timestamptz    NOT NULL,
    llave_idempotencia  uuid           NOT NULL,
    anulado_en          timestamptz,
    anulado_por_id      uuid,
    motivo_anulacion    varchar(300),

    CONSTRAINT ck_retiro_datos_de_anulacion
        CHECK ((anulado_en IS NULL) = (anulado_por_id IS NULL)
               AND (anulado_en IS NULL) = (motivo_anulacion IS NULL))
);

CREATE UNIQUE INDEX ux_retiro_llave ON retiro_caja (llave_idempotencia);
CREATE INDEX idx_retiro_turno ON retiro_caja (turno_id);

-- ── Compras pagadas con plata del cajón ────────────────────────────────────
-- Decisión 1: al registrar una compra en efectivo se dice si salió del cajón.
-- Las que ya existen no lo dicen: quedan como NO pagadas con plata del cajón,
-- que es no tocar ningún arqueo.
ALTER TABLE compra ADD COLUMN pagada_de_caja boolean NOT NULL DEFAULT false;
-- El default solo rellena las viejas: una inserción nueva tiene que decidirlo.
ALTER TABLE compra ALTER COLUMN pagada_de_caja DROP DEFAULT;

ALTER TABLE compra ADD COLUMN turno_id uuid REFERENCES turno_caja (id);

-- Una pagada con plata del cajón es en efectivo y de un turno; las demás, de ninguno.
ALTER TABLE compra ADD CONSTRAINT ck_compra_pagada_de_caja
    CHECK ((pagada_de_caja = (turno_id IS NOT NULL)) AND (NOT pagada_de_caja OR forma_pago = 'EFECTIVO'));

CREATE INDEX idx_compra_turno ON compra (turno_id) WHERE turno_id IS NOT NULL;

-- ── Las ventas anuladas en un turno ────────────────────────────────────────
-- El arqueo resta el efectivo de las ventas anuladas mientras el turno estaba
-- abierto, sean de ese turno o de otro: se buscan por este enlace.
CREATE INDEX idx_venta_anulada_en_turno ON venta (anulada_en_turno_id) WHERE anulada_en_turno_id IS NOT NULL;
