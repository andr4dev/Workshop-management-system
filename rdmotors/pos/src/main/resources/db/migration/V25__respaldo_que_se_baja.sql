-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V25: el respaldo ya no vive en un disco nuestro (spec 0011)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Hasta la V23 el respaldo era: un programa saca una copia de la base y la deja
-- en una carpeta del computador de la tienda, todas las noches, y se guardan
-- catorce días. Al mudarse el sistema a la nube, cada pieza de esa frase deja de
-- sostenerse: el disco del servidor es prestado y se borra en cada reinicio.
--
-- Ahora la copia SE BAJA: el administrador aprieta un botón y el archivo llega a
-- su computador o a su celular. En el servidor no queda nada. Entonces sobran:
--
--   segunda_copia        la memoria USB o el disco externo: no hay dónde enchufarlos
--   aviso_segunda_copia  su aviso
--   archivo_borrado_en   la poda de copias viejas, que ya no existe
--
-- Las filas se quedan: son la historia de cuándo se bajó cada copia, que es lo
-- que enciende el aviso de "hace ocho días que no bajas ninguna" (RF-011). Lo
-- que se pierde de las filas viejas —de dónde salió la USB, cuándo se podó— es
-- historia de un disco que ya no existe.
--
-- 'archivo' se queda, pero cambia de significado: antes era una ruta del disco
-- de la tienda, ahora es el NOMBRE del archivo que se bajó. Las filas viejas
-- conservan su ruta completa; no estorba y no vale la pena reescribirlas.
--
-- 'origen' conserva sus dos valores: las filas anteriores a hoy pueden decir
-- AUTOMATICO, y esa era la verdad cuando se escribieron. De aquí en adelante
-- todas dicen A_MANO, porque ya no hay nadie sacando copias de madrugada.

ALTER TABLE respaldo DROP CONSTRAINT ck_respaldo_borrado_solo_si_hubo;

ALTER TABLE respaldo DROP COLUMN archivo_borrado_en;
ALTER TABLE respaldo DROP COLUMN segunda_copia;
ALTER TABLE respaldo DROP COLUMN aviso_segunda_copia;

COMMENT ON TABLE respaldo IS
    'Las copias de la base que el administrador bajó (spec 0011): cuándo, cómo se llamaba el archivo, cuánto pesó y, si falló, por qué. El archivo no queda aquí: queda en el equipo de quien lo bajó.';

COMMENT ON COLUMN respaldo.archivo IS
    'El nombre del archivo que se bajó. Las filas anteriores a la V25 guardan la ruta completa en el disco de la tienda.';
