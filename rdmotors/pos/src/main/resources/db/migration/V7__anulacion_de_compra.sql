-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V7: anular una compra (spec 0002, fase 5)
--
--  Una compra se anula, no se borra. Queda en el historial con quién, cuándo y
--  por qué, y deja de contar en los totales.
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE compra
    ADD COLUMN estado varchar(10) NOT NULL DEFAULT 'VIGENTE'
        CHECK (estado IN ('VIGENTE', 'ANULADA'));

ALTER TABLE compra ADD COLUMN anulada_en timestamptz;
ALTER TABLE compra ADD COLUMN anulada_por_id uuid;
ALTER TABLE compra ADD COLUMN motivo_anulacion varchar(300);

-- Una anulada tiene que decir cuándo, quién y por qué; una vigente no puede
-- tener nada de eso.
ALTER TABLE compra
    ADD CONSTRAINT ck_compra_datos_de_anulacion
        CHECK ((estado = 'ANULADA') = (anulada_en IS NOT NULL
                                        AND anulada_por_id IS NOT NULL
                                        AND motivo_anulacion IS NOT NULL));

CREATE INDEX idx_compra_estado ON compra (estado);
