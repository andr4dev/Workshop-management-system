-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V18: los administradores activos (spec 0004, fase 4)
-- ═══════════════════════════════════════════════════════════════════════════

-- Siempre queda al menos un administrador activo (RF-016). Antes de pasar uno a
-- cajero o de desactivarlo se cuentan los que quedan, bajo candado. Con este
-- índice parcial esa cuenta no recorre a los cajeros ni a los desactivados.
CREATE INDEX ix_usuario_administradores_activos ON usuario (id)
    WHERE rol = 'ADMINISTRADOR' AND activo;
