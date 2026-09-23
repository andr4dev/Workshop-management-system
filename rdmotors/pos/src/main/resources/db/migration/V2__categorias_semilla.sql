-- ═══════════════════════════════════════════════════════════════════════════
--  Las 16 categorias iniciales, por sistema del vehiculo.
--
--  SON SEMILLA, NO LISTA FIJA. El cliente crea, renombra, reordena y desactiva
--  desde la aplicacion. Por eso viven en una tabla y no en un enum.
--
--  El porcentaje es cuanto cubre cada una de las 8.824 referencias del catalogo
--  real de Importadora Jotapartes, midiendo la primera palabra de la descripcion.
--  Sirve para ver que quedan balanceadas, no como verdad absoluta.
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO categoria (id, nombre, orden, activa) VALUES
    (gen_random_uuid(), 'MOTOR',                   1,  true),  -- 20,9%
    (gen_random_uuid(), 'EMPAQUES Y SELLOS',       2,  true),  -- 12,5%
    (gen_random_uuid(), 'CONTROLES Y GUAYAS',      3,  true),  -- 12,1%
    (gen_random_uuid(), 'ELECTRICO',               4,  true),  --  7,6%
    (gen_random_uuid(), 'ILUMINACION',             5,  true),  --  5,4%
    (gen_random_uuid(), 'TRANSMISION Y ARRASTRE',  6,  true),  --  4,1%
    (gen_random_uuid(), 'CARROCERIA',              7,  true),  --  3,3%
    (gen_random_uuid(), 'FRENOS',                  8,  true),  --  2,4%
    (gen_random_uuid(), 'SUSPENSION Y DIRECCION',  9,  true),  --  2,2%
    (gen_random_uuid(), 'RODAMIENTOS Y BUJES',     10, true),  --  2,1%
    (gen_random_uuid(), 'FILTROS',                 11, true),  --  2,0%
    (gen_random_uuid(), 'TORNILLERIA Y VARIOS',    12, true),  --  2,0%
    (gen_random_uuid(), 'CARBURACION',             13, true),  --  2,0%
    (gen_random_uuid(), 'LLANTAS',                 14, true),  --  0,3%
    -- Estas dos el catalogo de Jotapartes no las trae, pero RD Motors si las vendera.
    (gen_random_uuid(), 'LUBRICANTES Y QUIMICOS',  15, true),
    (gen_random_uuid(), 'ACCESORIOS',              16, true);
