-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V26: cargar el inventario desde la factura (spec 0012)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- La factura MAG477 de Jotapartes tiene 592 renglones; teclearlos uno por uno
-- en la pantalla de compras es la noche entera. Ahora se sube el archivo, se
-- revisa una PRE-CARGA —los precios sugeridos, lo que falta, lo que ya
-- existía— y al confirmar entra como UNA compra, igual que si se hubiera
-- tecleado.
--
-- Estas dos tablas son esa pre-carga mientras se revisa. Se guarda en el
-- servidor (decisión 4 del spec): seiscientos renglones no se revisan de una
-- sentada, y el socio y el dueño lo hacen desde equipos distintos.
--
-- NADA DE AQUÍ ES INVENTARIO. El stock, el costo y el kardex los mueve la
-- compra al confirmar. Una carga descartada no deja rastro en el inventario.
--
-- El archivo subido no se guarda: se lee y se descarta. Lo que queda es lo
-- leído, renglón por renglón.
--
-- Tablas nuevas, sin filas que migrar: en una base que ya tiene compras y
-- ventas, esto solo agrega.

CREATE TABLE carga_inventario (
    id                uuid           PRIMARY KEY,
    version           bigint         NOT NULL DEFAULT 0,
    estado            varchar(12)    NOT NULL CHECK (estado IN ('BORRADOR', 'CONFIRMADA', 'DESCARTADA')),
    origen            varchar(20)    NOT NULL CHECK (origen IN ('PDF_JOTAPARTES', 'EXCEL', 'CSV')),
    nombre_archivo    varchar(200),

    -- La compra que va a ser. Todo puede faltar mientras se revisa; lo que no
    -- puede es confirmarse sin ello, y eso lo dice la pre-carga.
    proveedor_id      uuid           REFERENCES proveedor (id),
    -- El NIT impreso en la factura, solo dígitos: con él se encontró al proveedor.
    nit_proveedor     varchar(40),
    numero_factura    varchar(60),
    fecha_factura     date,
    forma_pago        varchar(15)    CHECK (forma_pago IN ('EFECTIVO', 'TRANSFERENCIA')),
    cuenta_id         uuid           REFERENCES cuenta_pago (id),

    -- El sub-total de la factura, antes del IVA: contra él se comprueba que la
    -- lectura esté completa. Del PDF si se leyó (y entonces no se escribe a
    -- mano); escrito a mano si vino en Excel.
    subtotal_factura  numeric(14,2)  CHECK (subtotal_factura > 0),
    subtotal_leido    boolean        NOT NULL,
    iva_impreso       numeric(14,2),
    total_impreso     numeric(14,2),

    -- La regla del precio sugerido: costo con IVA, más la ganancia, redondeado
    -- hacia arriba al múltiplo (decisiones 1 y 2 del spec).
    iva_pct           numeric(8,4)   NOT NULL CHECK (iva_pct >= 0 AND iva_pct <= 1000),
    ganancia_pct      numeric(8,4)   NOT NULL CHECK (ganancia_pct >= 0 AND ganancia_pct <= 1000),
    redondeo          integer        NOT NULL CHECK (redondeo BETWEEN 1 AND 10000),

    creada_por_id     uuid           NOT NULL REFERENCES usuario (id),
    creada_en         timestamptz    NOT NULL,
    modificada_en     timestamptz    NOT NULL,
    -- Quién la confirmó o la descartó, y cuándo.
    cerrada_por_id    uuid           REFERENCES usuario (id),
    cerrada_en        timestamptz,
    compra_id         uuid           REFERENCES compra (id),

    -- Confirmada si y solo si dejó su compra.
    CONSTRAINT ck_carga_confirmada_con_compra
        CHECK ((estado = 'CONFIRMADA') = (compra_id IS NOT NULL)),
    -- Cerrada —confirmada o descartada— si y solo si dice quién y cuándo.
    CONSTRAINT ck_carga_cerrada
        CHECK ((estado = 'BORRADOR') = (cerrada_en IS NULL AND cerrada_por_id IS NULL))
);

-- La misma factura no se revisa dos veces a la vez: confirmar las dos entraría
-- la mercancía dos veces. El caso de uso lo avisa antes con un mensaje; este
-- índice cubre dos subidas en el mismo instante.
CREATE UNIQUE INDEX ux_carga_factura_en_borrador
    ON carga_inventario (nit_proveedor, numero_factura)
    WHERE estado = 'BORRADOR' AND numero_factura IS NOT NULL;

CREATE INDEX idx_carga_factura ON carga_inventario (nit_proveedor, numero_factura);
CREATE INDEX idx_carga_estado ON carga_inventario (estado, modificada_en);

CREATE TABLE renglon_carga (
    id                    uuid           PRIMARY KEY,
    carga_id              uuid           NOT NULL REFERENCES carga_inventario (id),
    posicion              integer        NOT NULL CHECK (posicion >= 0),
    -- Dónde está en el archivo: "pág. 26", "fila 14". Para ir al papel.
    ubicacion             varchar(40),

    -- Lo leído, TAL CUAL, aunque esté mal: sin CHECK de cantidad positiva ni de
    -- código presente. Un borrador tiene que poder guardar un renglón malo para
    -- mostrarlo marcado y que se arregle; lo que no puede es confirmarse.
    codigo                varchar(200),
    descripcion           varchar(500),
    -- La descripción sin la marca del final: el nombre del repuesto nuevo.
    nombre                varchar(500),
    cantidad              integer,
    unidad                varchar(20),
    -- Precio de lista y descuento: solo para comprobar el renglón. El costo
    -- sale del valor total, que es de TODAS las unidades del renglón.
    precio_unitario       numeric(14,2),
    descuento_pct         numeric(7,4),
    valor_total           numeric(14,2),

    marca                 varchar(200),
    -- La propuso el sistema leyendo la descripción, y nadie la ha mirado.
    marca_propuesta       boolean        NOT NULL,
    categoria_id          uuid           REFERENCES categoria (id),
    categoria_propuesta   boolean        NOT NULL,

    -- El precio de venta que va a quedar: el sugerido o el escrito a mano.
    precio_final          numeric(14,2)  CHECK (precio_final > 0),
    ajustado_a_mano       boolean        NOT NULL,
    -- Fuera de la carga, no del archivo: su valor sigue contando para
    -- comprobar que la lectura esté completa.
    quitado               boolean        NOT NULL,
    -- Solo en una reposición: el repuesto toma el precio de esta carga.
    aplicar_precio_nuevo  boolean        NOT NULL,

    CONSTRAINT ux_renglon_carga_posicion UNIQUE (carga_id, posicion),
    CONSTRAINT ck_renglon_carga_ajustado_con_precio
        CHECK (NOT ajustado_a_mano OR precio_final IS NOT NULL)
);

COMMENT ON TABLE carga_inventario IS
    'Una factura subida mientras se revisa (spec 0012). Nada de aquí es inventario hasta confirmarla: al confirmar entra como UNA compra, la de compra_id.';

COMMENT ON TABLE renglon_carga IS
    'Los renglones leídos de la factura, aunque estén mal: el borrador los guarda para mostrarlos marcados. Si el código ya existe en el inventario no se guarda aquí: se mira cada vez.';
