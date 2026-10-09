-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTOS — V32: las transferencias se cuadran con el turno, como el cajón (2026-10-09)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- La plata de las transferencias vive en una cuenta (el Daviplata de la tienda). El turno anota con cuánto arrancó
-- esa cuenta; mientras está abierto, la caja suma lo que entró por transferencia y resta lo que se pagó desde ahí
-- (compras y gastos por transferencia, devoluciones, retiros de la cuenta). Al cerrar, se escribe lo que dice la
-- cuenta y queda la diferencia, igual que con el efectivo.
--
-- Todo es opcional: los turnos de antes no lo tienen, y un turno puede no anotar con cuánto arrancó la cuenta.

ALTER TABLE turno_caja
    ADD COLUMN saldo_transferencias       numeric(14, 2),
    ADD COLUMN transf_ventas              numeric(14, 2),
    ADD COLUMN transf_abonos              numeric(14, 2),
    ADD COLUMN transf_desde_cajon         numeric(14, 2),
    ADD COLUMN transf_devoluciones        numeric(14, 2),
    ADD COLUMN transf_compras             numeric(14, 2),
    ADD COLUMN transf_gastos              numeric(14, 2),
    ADD COLUMN transf_retiros             numeric(14, 2),
    ADD COLUMN transf_esperado            numeric(14, 2),
    ADD COLUMN transf_contado             numeric(14, 2),
    ADD COLUMN transf_diferencia          numeric(14, 2);

ALTER TABLE turno_caja ADD CONSTRAINT ck_turno_saldo_transferencias
    CHECK (saldo_transferencias IS NULL OR saldo_transferencias >= 0);
ALTER TABLE turno_caja ADD CONSTRAINT ck_turno_transf_contado
    CHECK (transf_contado IS NULL OR transf_contado >= 0);

-- De dónde sale un retiro: del cajón (como hasta hoy), del cajón a la cuenta de transferencias, o de la cuenta.
ALTER TABLE retiro_caja
    ADD COLUMN tipo varchar(20) NOT NULL DEFAULT 'DEL_CAJON';

ALTER TABLE retiro_caja ADD CONSTRAINT ck_retiro_tipo
    CHECK (tipo IN ('DEL_CAJON', 'A_TRANSFERENCIAS', 'DE_TRANSFERENCIAS'));

-- Las compras y gastos por transferencia de un turno se buscan por cuándo se registraron.
CREATE INDEX idx_compra_transferencia_registro ON compra (fecha_registro) WHERE forma_pago = 'TRANSFERENCIA';
CREATE INDEX idx_gasto_transferencia_registro ON gasto (registrado_en) WHERE forma_pago = 'TRANSFERENCIA';
