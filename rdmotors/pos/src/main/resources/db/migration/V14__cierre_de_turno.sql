-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V14: el cierre del turno con su arqueo (spec 0006, fase 3)
-- ═══════════════════════════════════════════════════════════════════════════

-- Se GUARDA cada parte de lo que debería haber, no solo el total: si mañana se
-- anula una venta de este turno, esa devolución resta mañana, y el cierre de hoy
-- tiene que seguir diciendo lo mismo, con el mismo desglose.
ALTER TABLE turno_caja
    ADD COLUMN ventas_efectivo        numeric(14,2) CHECK (ventas_efectivo >= 0),
    ADD COLUMN ventas_transferencia   numeric(14,2) CHECK (ventas_transferencia >= 0),
    ADD COLUMN descuentos             numeric(14,2) CHECK (descuentos >= 0),
    ADD COLUMN devoluciones_efectivo  numeric(14,2) CHECK (devoluciones_efectivo >= 0),
    ADD COLUMN gastos_cajon           numeric(14,2) CHECK (gastos_cajon >= 0),
    ADD COLUMN retiros                numeric(14,2) CHECK (retiros >= 0),
    ADD COLUMN compras_cajon          numeric(14,2) CHECK (compras_cajon >= 0),
    -- Puede ser negativo: se sacó más de lo que había (decisión 3 avisa, no bloquea).
    ADD COLUMN esperado               numeric(14,2),
    ADD COLUMN contado                numeric(14,2) CHECK (contado >= 0),
    -- contado − esperado: positiva es sobrante, negativa faltante.
    ADD COLUMN diferencia             numeric(14,2),
    ADD COLUMN observaciones          varchar(500) CHECK (btrim(observaciones) <> '');

-- Un turno cerrado tiene todas sus cifras; uno abierto, ninguna. El arqueo es a
-- ciegas: mientras está abierto, lo que debería haber no existe en ninguna parte.
ALTER TABLE turno_caja ADD CONSTRAINT ck_turno_montos_de_cierre
    CHECK ((estado = 'CERRADO') = (ventas_efectivo IS NOT NULL AND ventas_transferencia IS NOT NULL
                                   AND descuentos IS NOT NULL AND devoluciones_efectivo IS NOT NULL
                                   AND gastos_cajon IS NOT NULL AND retiros IS NOT NULL
                                   AND compras_cajon IS NOT NULL AND esperado IS NOT NULL
                                   AND contado IS NOT NULL AND diferencia IS NOT NULL));

-- Las partes suman lo que debería haber, también para quien escriba por otro camino.
ALTER TABLE turno_caja ADD CONSTRAINT ck_turno_esperado_suma_sus_partes
    CHECK (esperado IS NULL
           OR esperado = fondo + ventas_efectivo - devoluciones_efectivo - gastos_cajon - retiros - compras_cajon);

ALTER TABLE turno_caja ADD CONSTRAINT ck_turno_diferencia
    CHECK (diferencia IS NULL OR diferencia = contado - esperado);

-- Las observaciones explican un cierre: un turno abierto no tiene qué explicar.
ALTER TABLE turno_caja ADD CONSTRAINT ck_turno_observaciones_al_cerrar
    CHECK (observaciones IS NULL OR estado = 'CERRADO');

-- El historial va del cierre más reciente al más antiguo.
CREATE INDEX idx_turno_cerrado_en ON turno_caja (cerrado_en DESC) WHERE estado = 'CERRADO';
