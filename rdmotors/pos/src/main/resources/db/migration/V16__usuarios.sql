-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V16: las personas que entran al sistema (spec 0004, fase 1)
-- ═══════════════════════════════════════════════════════════════════════════

-- ── El usuario ─────────────────────────────────────────────────────────────
-- La contraseña NUNCA se guarda: solo su hash (BCrypt, con el prefijo {bcrypt}
-- para poder cambiar de algoritmo sin invalidar los que ya hay).
-- No se borra: se desactiva, y lo que hizo lo sigue nombrando.
CREATE TABLE usuario (
    id                       uuid          PRIMARY KEY,
    -- Como lo escribió quien lo creó; se compara por el normalizado.
    usuario                  varchar(40)   NOT NULL,
    -- Sin mayúsculas ni tildes: "Carolina" y "carolina" son el mismo usuario.
    usuario_normalizado      varchar(40)   NOT NULL,
    -- El que se muestra y sale en el comprobante.
    nombre                   varchar(80)   NOT NULL,
    hash                     varchar(200)  NOT NULL,
    rol                      varchar(15)   NOT NULL CHECK (rol IN ('ADMINISTRADOR', 'CAJERO')),
    activo                   boolean       NOT NULL,
    -- Una contraseña inicial o restablecida se cambia al entrar.
    debe_cambiar_contrasena  boolean       NOT NULL,
    -- La lleva el token. Cambiar la contraseña, restablecerla o desactivar la
    -- sube: los tokens que ya había dejan de valer en el acto.
    version_sesion           bigint        NOT NULL CHECK (version_sesion >= 1),
    intentos_fallidos        integer       NOT NULL CHECK (intentos_fallidos >= 0),
    bloqueado_hasta          timestamptz,
    creado_en                timestamptz   NOT NULL,
    ultima_entrada           timestamptz
);

CREATE UNIQUE INDEX ux_usuario_normalizado ON usuario (usuario_normalizado);

-- ── La auditoría conoce las acciones sobre usuarios ─────────────────────────
ALTER TABLE evento_auditoria DROP CONSTRAINT evento_auditoria_accion_check;
ALTER TABLE evento_auditoria ADD CONSTRAINT evento_auditoria_accion_check
    CHECK (accion IN ('CORREGIR_COMPRA', 'ANULAR_COMPRA', 'CORREGIR_REPUESTO',
                      'APLICAR_DESCUENTO', 'ANULAR_VENTA',
                      'CERRAR_CAJA_CON_DIFERENCIA', 'ANULAR_GASTO', 'ANULAR_RETIRO',
                      'CREAR_USUARIO', 'CAMBIAR_ROL', 'DESACTIVAR_USUARIO', 'ACTIVAR_USUARIO',
                      'RESTABLECER_CONTRASENA'));
