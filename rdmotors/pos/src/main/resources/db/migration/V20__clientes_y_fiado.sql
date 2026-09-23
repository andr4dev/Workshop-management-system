-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V20: clientes, fiado y cartera (spec 0008, fase 1)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Las tablas de toda la cartera nacen juntas: el cliente, lo fiado de cada
-- venta, la deuda (una por venta fiada o por el saldo del cuaderno), los abonos
-- y cuánto de cada abono fue a cada deuda. Los abonos se usan desde la fase 3;
-- la parte de abonos del cierre de caja llega con la V21.

-- ── El cliente ─────────────────────────────────────────────────────────────
CREATE TABLE cliente (
    id                     uuid          PRIMARY KEY,
    version                bigint        NOT NULL DEFAULT 0,
    nombre                 varchar(120)  NOT NULL CHECK (btrim(nombre) <> ''),
    -- En minúsculas y sin tildes (TextoDeBusqueda): así se busca.
    nombre_normalizado     varchar(120)  NOT NULL,
    documento              varchar(30),
    -- Sin puntos, guiones ni espacios: "1.234.567-8" y "12345678" son la misma cédula.
    documento_normalizado  varchar(30),
    celular                varchar(30),
    -- Solo los dígitos.
    celular_normalizado    varchar(20),
    direccion              varchar(200),
    nota                   varchar(300),
    -- Sin cupo (spec 0008, decisión 5): lo que frena a quien no paga es cerrarle el fiado.
    fiado_cerrado          boolean       NOT NULL DEFAULT false,
    motivo_fiado_cerrado   varchar(300),
    creado_en              timestamptz   NOT NULL,
    creado_por_id          uuid          NOT NULL REFERENCES usuario (id),

    CONSTRAINT ck_cliente_documento CHECK ((documento IS NULL) = (documento_normalizado IS NULL)),
    CONSTRAINT ck_cliente_celular CHECK ((celular IS NULL) = (celular_normalizado IS NULL)),
    CONSTRAINT ck_cliente_fiado_cerrado CHECK (fiado_cerrado = (motivo_fiado_cerrado IS NOT NULL))
);

-- La cédula no se repite: escribirla encuentra al cliente que ya existe.
CREATE UNIQUE INDEX ux_cliente_documento ON cliente (documento_normalizado) WHERE documento_normalizado IS NOT NULL;
CREATE INDEX idx_cliente_nombre ON cliente (nombre_normalizado);

-- ── La venta sabe a quién y cuánto quedó fiado ─────────────────────────────
-- Las ventas de antes quedan sin cliente y con $0 fiado: se cobraron completas.
ALTER TABLE venta
    ADD COLUMN cliente_id uuid REFERENCES cliente (id),
    ADD COLUMN fiado      numeric(14,2) NOT NULL DEFAULT 0 CHECK (fiado >= 0);

-- Lo fiado no pasa del total, y si hay fiado hay a quién. Que los pagos más lo
-- fiado sumen el total lo exige el dominio: los pagos están en otra tabla.
ALTER TABLE venta ADD CONSTRAINT ck_venta_fiado
    CHECK (fiado <= total AND (fiado = 0 OR cliente_id IS NOT NULL));

CREATE INDEX idx_venta_cliente ON venta (cliente_id, cobrada_en DESC) WHERE cliente_id IS NOT NULL;

-- ── La deuda ───────────────────────────────────────────────────────────────
CREATE TABLE deuda (
    id                 uuid           PRIMARY KEY,
    version            bigint         NOT NULL DEFAULT 0,
    cliente_id         uuid           NOT NULL REFERENCES cliente (id),
    origen             varchar(10)    NOT NULL CHECK (origen IN ('VENTA', 'CUADERNO')),
    venta_id           uuid           REFERENCES venta (id),
    numero_venta       bigint,
    -- El día de Colombia en que nació: por él se decide qué se paga primero.
    fecha              date           NOT NULL,
    registrada_en      timestamptz    NOT NULL,
    registrada_por_id  uuid           NOT NULL REFERENCES usuario (id),
    monto              numeric(14,2)  NOT NULL CHECK (monto > 0),
    -- La suma de las aplicaciones vigentes de abonos a esta deuda.
    abonado            numeric(14,2)  NOT NULL DEFAULT 0,
    -- Solo el saldo del cuaderno: de dónde sale esa cifra.
    motivo             varchar(300),
    -- Cuánto debía el cliente en total justo después: lo dice el comprobante al reimprimirlo.
    debe_despues       numeric(14,2)  NOT NULL CHECK (debe_despues >= 0),
    anulada_en         timestamptz,
    anulada_por_id     uuid           REFERENCES usuario (id),

    CONSTRAINT ck_deuda_abonado CHECK (abonado >= 0 AND abonado <= monto),
    CONSTRAINT ck_deuda_de_venta CHECK ((origen = 'VENTA') = (venta_id IS NOT NULL AND numero_venta IS NOT NULL)),
    CONSTRAINT ck_deuda_motivo_del_cuaderno CHECK ((origen = 'CUADERNO') = (motivo IS NOT NULL)),
    CONSTRAINT ck_deuda_anulada CHECK ((anulada_en IS NULL) = (anulada_por_id IS NULL)),
    -- Al anularse, lo que tenía abonado se libera hacia las otras deudas o queda a favor.
    CONSTRAINT ck_deuda_anulada_sin_abonado CHECK (anulada_en IS NULL OR abonado = 0)
);

CREATE UNIQUE INDEX ux_deuda_venta ON deuda (venta_id) WHERE venta_id IS NOT NULL;
-- El saldo del cuaderno se carga una sola vez por cliente.
CREATE UNIQUE INDEX ux_deuda_cuaderno ON deuda (cliente_id) WHERE origen = 'CUADERNO';
CREATE INDEX idx_deuda_cliente ON deuda (cliente_id, fecha, registrada_en);

-- ── Los abonos ─────────────────────────────────────────────────────────────
-- Numerados como las ventas: una fila contadora que vuelve atrás si el abono se deshace.
INSERT INTO consecutivo (nombre, ultimo) VALUES ('ABONO', 0);

CREATE TABLE abono (
    id                  uuid           PRIMARY KEY,
    version             bigint         NOT NULL DEFAULT 0,
    numero              bigint         NOT NULL,
    cliente_id          uuid           NOT NULL REFERENCES cliente (id),
    monto               numeric(14,2)  NOT NULL CHECK (monto > 0),
    forma               varchar(15)    NOT NULL CHECK (forma IN ('EFECTIVO', 'TRANSFERENCIA')),
    referencia          varchar(100),
    nota                varchar(300),
    -- El turno abierto al recibirlo. El efectivo entra a su cajón.
    turno_id            uuid           REFERENCES turno_caja (id),
    recibido_por_id     uuid           NOT NULL REFERENCES usuario (id),
    recibido_en         timestamptz    NOT NULL,
    llave_idempotencia  uuid           NOT NULL,
    debe_despues        numeric(14,2)  NOT NULL CHECK (debe_despues >= 0),
    anulado_en          timestamptz,
    anulado_por_id      uuid           REFERENCES usuario (id),
    motivo_anulacion    varchar(300),

    -- Sin turno no hay cajón al que entre el efectivo.
    CONSTRAINT ck_abono_efectivo_en_turno CHECK (forma <> 'EFECTIVO' OR turno_id IS NOT NULL),
    CONSTRAINT ck_abono_referencia CHECK (referencia IS NULL OR forma = 'TRANSFERENCIA'),
    CONSTRAINT ck_abono_anulado CHECK (
        (anulado_en IS NULL AND anulado_por_id IS NULL AND motivo_anulacion IS NULL)
        OR (anulado_en IS NOT NULL AND anulado_por_id IS NOT NULL AND motivo_anulacion IS NOT NULL))
);

CREATE UNIQUE INDEX ux_abono_numero ON abono (numero);
CREATE UNIQUE INDEX ux_abono_llave ON abono (llave_idempotencia);
CREATE INDEX idx_abono_cliente ON abono (cliente_id, recibido_en);
CREATE INDEX idx_abono_turno ON abono (turno_id) WHERE turno_id IS NOT NULL;
CREATE INDEX idx_abono_recibido_en ON abono (recibido_en);

-- Cuánto de cada abono fue a cada deuda. No se borran: al anular un abono o una
-- venta fiada se marcan, y lo liberado que vuelve a aplicarse es una fila nueva.
CREATE TABLE aplicacion_abono (
    id           uuid           PRIMARY KEY,
    abono_id     uuid           NOT NULL REFERENCES abono (id),
    deuda_id     uuid           NOT NULL REFERENCES deuda (id),
    monto        numeric(14,2)  NOT NULL CHECK (monto > 0),
    aplicada_en  timestamptz    NOT NULL,
    anulada_en   timestamptz
);

CREATE INDEX idx_aplicacion_abono_abono ON aplicacion_abono (abono_id);
CREATE INDEX idx_aplicacion_abono_deuda ON aplicacion_abono (deuda_id);

-- ── La auditoría conoce las acciones de la cartera ─────────────────────────
ALTER TABLE evento_auditoria DROP CONSTRAINT evento_auditoria_accion_check;
ALTER TABLE evento_auditoria ADD CONSTRAINT evento_auditoria_accion_check
    CHECK (accion IN ('CORREGIR_COMPRA', 'ANULAR_COMPRA', 'CORREGIR_REPUESTO',
                      'APLICAR_DESCUENTO', 'ANULAR_VENTA',
                      'CERRAR_CAJA_CON_DIFERENCIA', 'ANULAR_GASTO', 'ANULAR_RETIRO',
                      'CREAR_USUARIO', 'CAMBIAR_ROL', 'DESACTIVAR_USUARIO', 'ACTIVAR_USUARIO',
                      'RESTABLECER_CONTRASENA',
                      'CORREGIR_CLIENTE', 'CERRAR_FIADO', 'ABRIR_FIADO', 'CARGAR_SALDO_CUADERNO',
                      'ANULAR_ABONO'));
