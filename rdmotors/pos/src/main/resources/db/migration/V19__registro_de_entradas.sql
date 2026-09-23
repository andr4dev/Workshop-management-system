-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V19: el registro de entradas (spec 0004, RF-025)
-- ═══════════════════════════════════════════════════════════════════════════

-- Cada intento de entrar, entre o no: cinco fallidos seguidos de madrugada son
-- justo lo que el dueño tiene que poder ver. NUNCA guarda la contraseña.
--
-- `usuario_escrito` va tal cual se escribió, aunque no exista nadie así; en ese
-- caso `usuario_id` queda vacío. Solo se agrega: no se edita ni se borra.
CREATE TABLE entrada (
    id              uuid          PRIMARY KEY,
    usuario_id      uuid          REFERENCES usuario (id),
    usuario_escrito varchar(60)   NOT NULL,
    exito           boolean       NOT NULL,
    momento         timestamptz   NOT NULL,
    -- 45 caracteres: lo que ocupa una IPv6 escrita.
    ip              varchar(45),
    navegador       varchar(200)
);

-- La pantalla lo lee de la más reciente a la más antigua; el id desempata dos
-- intentos en el mismo instante.
CREATE INDEX idx_entrada_momento ON entrada (momento DESC, id);
