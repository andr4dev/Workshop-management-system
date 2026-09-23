-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V12: categorías de gasto y gastos (spec 0006, fase 1)
-- ═══════════════════════════════════════════════════════════════════════════

-- ── Las categorías ─────────────────────────────────────────────────────────
-- Una lista y no texto libre: "flete", "Fletes" y "FLETE" serían tres renglones
-- en el reporte. Cada una dice UNA VEZ si es costo o gasto (decisión 4): al
-- registrar un gasto nadie decide eso de nuevo.
CREATE TABLE categoria_gasto (
    id          uuid         PRIMARY KEY,
    nombre      varchar(80)  NOT NULL CHECK (btrim(nombre) <> ''),
    naturaleza  varchar(10)  NOT NULL CHECK (naturaleza IN ('COSTO', 'GASTO')),
    activa      boolean      NOT NULL DEFAULT true
);

-- Sin distinguir mayúsculas NI TILDES: "Papeleria" y "Papelería" son la misma.
-- La expresión es la misma función sin_tildes que usan las consultas
-- (FuncionesDeBusqueda), con la misma tabla de letras que TextoDeBusqueda.
CREATE UNIQUE INDEX ux_categoria_gasto_nombre ON categoria_gasto (
    translate(lower(nombre),
              'áàäâãéèëêíìïîóòöôõúùüûñçÁÀÄÂÃÉÈËÊÍÌÏÎÓÒÖÔÕÚÙÜÛÑÇ',
              'aaaaaeeeeiiiiooooouuuuncaaaaaeeeeiiiiooooouuuunc'));

-- Semilla, no lista fija: el cliente crea, renombra y desactiva las suyas.
-- Todas gasto: lo que cuesta tener la tienda abierta. El flete también, como
-- decidió el modelo de datos (SPEC_Modelo_Datos.md:444-450); se revisa en el
-- spec de reportes.
INSERT INTO categoria_gasto (id, nombre, naturaleza) VALUES
    (gen_random_uuid(), 'Arriendo',               'GASTO'),
    (gen_random_uuid(), 'Servicios públicos',     'GASTO'),
    (gen_random_uuid(), 'Internet y teléfono',    'GASTO'),
    (gen_random_uuid(), 'Nómina',                 'GASTO'),
    (gen_random_uuid(), 'Transporte y fletes',    'GASTO'),
    (gen_random_uuid(), 'Alimentación',           'GASTO'),
    (gen_random_uuid(), 'Aseo y cafetería',       'GASTO'),
    (gen_random_uuid(), 'Papelería',              'GASTO'),
    (gen_random_uuid(), 'Mantenimiento',          'GASTO'),
    (gen_random_uuid(), 'Impuestos y trámites',   'GASTO'),
    (gen_random_uuid(), 'Otros',                  'GASTO');

-- ── Los gastos ─────────────────────────────────────────────────────────────
-- Del cajón (resta del arqueo de su turno) o por fuera (el arriendo por Nequi:
-- no toca ningún arqueo, pero es gasto del negocio).
CREATE TABLE gasto (
    id                  uuid           PRIMARY KEY,
    version             bigint         NOT NULL DEFAULT 0,
    categoria_id        uuid           NOT NULL REFERENCES categoria_gasto (id),
    monto               numeric(14,2)  NOT NULL CHECK (monto > 0),
    descripcion         varchar(300)   NOT NULL CHECK (btrim(descripcion) <> ''),
    del_cajon           boolean        NOT NULL,
    turno_id            uuid           REFERENCES turno_caja (id),
    forma_pago          varchar(15)    NOT NULL CHECK (forma_pago IN ('EFECTIVO', 'TRANSFERENCIA')),
    cuenta_id           uuid           REFERENCES cuenta_pago (id),
    fecha               date           NOT NULL,
    registrado_por_id   uuid           NOT NULL,
    registrado_en       timestamptz    NOT NULL,
    -- La llave contra el doble clic: nace al abrir el formulario.
    llave_idempotencia  uuid           NOT NULL,
    anulado_en          timestamptz,
    anulado_por_id      uuid,
    motivo_anulacion    varchar(300),

    -- Uno del cajón es de un turno y en efectivo; uno por fuera no es de ningún turno.
    CONSTRAINT ck_gasto_del_cajon
        CHECK ((del_cajon = (turno_id IS NOT NULL)) AND (NOT del_cajon OR forma_pago = 'EFECTIVO')),
    -- Como en las compras: la transferencia dice desde qué cuenta; el efectivo no lleva.
    CONSTRAINT ck_gasto_cuenta_segun_forma_pago
        CHECK ((forma_pago = 'TRANSFERENCIA') = (cuenta_id IS NOT NULL)),
    -- Un anulado dice cuándo, quién y por qué; uno vigente, nada de eso.
    CONSTRAINT ck_gasto_datos_de_anulacion
        CHECK ((anulado_en IS NULL) = (anulado_por_id IS NULL)
               AND (anulado_en IS NULL) = (motivo_anulacion IS NULL))
);

CREATE UNIQUE INDEX ux_gasto_llave ON gasto (llave_idempotencia);
CREATE INDEX idx_gasto_fecha ON gasto (fecha, registrado_en);
CREATE INDEX idx_gasto_turno ON gasto (turno_id) WHERE turno_id IS NOT NULL;
CREATE INDEX idx_gasto_categoria ON gasto (categoria_id);

-- ── La auditoría conoce las acciones de caja ───────────────────────────────
-- Las tres del spec 0006 entran juntas (plan 0006, decisión 9): el CHECK se
-- recrea una sola vez.
ALTER TABLE evento_auditoria DROP CONSTRAINT evento_auditoria_accion_check;
ALTER TABLE evento_auditoria ADD CONSTRAINT evento_auditoria_accion_check
    CHECK (accion IN ('CORREGIR_COMPRA', 'ANULAR_COMPRA', 'CORREGIR_REPUESTO',
                      'APLICAR_DESCUENTO', 'ANULAR_VENTA',
                      'CERRAR_CAJA_CON_DIFERENCIA', 'ANULAR_GASTO', 'ANULAR_RETIRO'));
