-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V17: cada "quién" es una persona de verdad (spec 0004, fase 2)
-- ═══════════════════════════════════════════════════════════════════════════

-- Hasta aquí, el "quién" de cada venta, compra, turno o anulación era un id que
-- mandaba el navegador y nadie revisaba. Desde el spec 0004 lo pone la sesión, y
-- la base lo exige: un id que no es de un usuario se rechaza.
--
-- Corre sobre bases sin filas huérfanas: QA quedó limpia el 2026-09-17, y no hay
-- tienda en producción todavía. Si alguna fila apuntara a un id inventado, la
-- migración falla entera y no deja la base a medias.
--
-- Sin índices nuevos: los usuarios no se borran (se desactivan), así que la base
-- nunca tiene que buscar las filas de una persona para impedir su borrado.

ALTER TABLE compra
    ADD CONSTRAINT fk_compra_registrado_por FOREIGN KEY (registrado_por_id) REFERENCES usuario (id),
    ADD CONSTRAINT fk_compra_anulada_por    FOREIGN KEY (anulada_por_id)    REFERENCES usuario (id);

ALTER TABLE movimiento_kardex
    ADD CONSTRAINT fk_kardex_registrado_por FOREIGN KEY (registrado_por_id) REFERENCES usuario (id);

ALTER TABLE evento_auditoria
    ADD CONSTRAINT fk_auditoria_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id);

ALTER TABLE turno_caja
    ADD CONSTRAINT fk_turno_abierto_por FOREIGN KEY (abierto_por_id) REFERENCES usuario (id),
    ADD CONSTRAINT fk_turno_cerrado_por FOREIGN KEY (cerrado_por_id) REFERENCES usuario (id);

ALTER TABLE venta
    ADD CONSTRAINT fk_venta_vendido_por FOREIGN KEY (vendido_por_id) REFERENCES usuario (id),
    ADD CONSTRAINT fk_venta_anulada_por FOREIGN KEY (anulada_por_id) REFERENCES usuario (id);

ALTER TABLE gasto
    ADD CONSTRAINT fk_gasto_registrado_por FOREIGN KEY (registrado_por_id) REFERENCES usuario (id),
    ADD CONSTRAINT fk_gasto_anulado_por    FOREIGN KEY (anulado_por_id)    REFERENCES usuario (id);

ALTER TABLE retiro_caja
    ADD CONSTRAINT fk_retiro_registrado_por FOREIGN KEY (registrado_por_id) REFERENCES usuario (id),
    ADD CONSTRAINT fk_retiro_anulado_por    FOREIGN KEY (anulado_por_id)    REFERENCES usuario (id);
