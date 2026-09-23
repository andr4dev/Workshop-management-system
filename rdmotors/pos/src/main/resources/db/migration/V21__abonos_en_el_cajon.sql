-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V21: los abonos en el cajón y en el cierre (spec 0008, fase 3)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- El abono en efectivo es una parte NUEVA de lo que debería haber en el cajón:
--     esperado = fondo + ventas en efectivo + abonos en efectivo
--                − devoluciones − gastos − retiros − compras
-- Lo fiado y los abonos por transferencia no entran al cajón: se guardan para
-- que el comprobante del cierre diga por qué el cajón no espera esa plata.

ALTER TABLE turno_caja
    -- Lo que se fió en el turno: no entró al cajón, lo deben los clientes.
    ADD COLUMN ventas_fiado          numeric(14,2) CHECK (ventas_fiado >= 0),
    ADD COLUMN abonos_efectivo       numeric(14,2) CHECK (abonos_efectivo >= 0),
    ADD COLUMN abonos_transferencia  numeric(14,2) CHECK (abonos_transferencia >= 0);

-- Los turnos que ya se cerraron no tenían abonos ni fiado: nacen en $0 y su
-- cierre sigue cuadrando al peso.
UPDATE turno_caja
   SET ventas_fiado = 0, abonos_efectivo = 0, abonos_transferencia = 0
 WHERE estado = 'CERRADO';

-- Un turno cerrado tiene todas sus cifras; uno abierto, ninguna.
ALTER TABLE turno_caja DROP CONSTRAINT ck_turno_montos_de_cierre;
ALTER TABLE turno_caja ADD CONSTRAINT ck_turno_montos_de_cierre
    CHECK ((estado = 'CERRADO') = (ventas_efectivo IS NOT NULL AND ventas_transferencia IS NOT NULL
                                   AND descuentos IS NOT NULL AND devoluciones_efectivo IS NOT NULL
                                   AND gastos_cajon IS NOT NULL AND retiros IS NOT NULL
                                   AND compras_cajon IS NOT NULL AND esperado IS NOT NULL
                                   AND contado IS NOT NULL AND diferencia IS NOT NULL
                                   AND ventas_fiado IS NOT NULL AND abonos_efectivo IS NOT NULL
                                   AND abonos_transferencia IS NOT NULL));

-- Las partes suman lo que debería haber, también para quien escriba por otro camino.
ALTER TABLE turno_caja DROP CONSTRAINT ck_turno_esperado_suma_sus_partes;
ALTER TABLE turno_caja ADD CONSTRAINT ck_turno_esperado_suma_sus_partes
    CHECK (esperado IS NULL
           OR esperado = fondo + ventas_efectivo + abonos_efectivo
                       - devoluciones_efectivo - gastos_cajon - retiros - compras_cajon);
