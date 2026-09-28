-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V27: la cédula y el celular del cliente, como los escriban
--  (spec 0008, decisión 2, cambiada otra vez el 2026-09-28)
-- ═══════════════════════════════════════════════════════════════════════════
--
-- Hasta aquí la cédula tenía que parecer cédula (de 5 a 15 letras o números) y
-- el celular, celular (de 7 a 15 dígitos). El dueño lo probó al fiar y frenaba:
-- ahora se guarda lo que escriban.
--
-- Pero la cédula también dice QUIÉN es el cliente: con la misma, se usa el que
-- ya existe. Si "no tiene" o "123" lo dijeran, dos personas distintas quedarían
-- como una sola, debiendo lo de las dos. Por eso solo la que parece un documento
-- —de 5 a 15 letras o números, con al menos un número— no se puede repetir. Las
-- demás se guardan y se buscan, pero pueden estar en varios clientes.
--
-- Las cédulas que ya existían cumplían la regla vieja, que era más estricta: el
-- índice nuevo cubre menos filas que el viejo, así que crearlo no puede fallar.
-- El dominio repite la regla en Cliente.documentoQueIdentifica.

DROP INDEX ux_cliente_documento;
CREATE UNIQUE INDEX ux_cliente_documento ON cliente (documento_normalizado)
    WHERE documento_normalizado ~ '^[A-Z0-9]{5,15}$' AND documento_normalizado ~ '[0-9]';

-- El celular se guarda con todos sus dígitos, aunque traiga dos números: lo escrito
-- llega a 30 caracteres, y sus dígitos también.
ALTER TABLE cliente ALTER COLUMN celular_normalizado TYPE varchar(30);
