-- ═══════════════════════════════════════════════════════════════════════════
--  RD MOTORS — V3: con qué se pagó cada compra (spec 0002, fase 1)
-- ═══════════════════════════════════════════════════════════════════════════

-- Las cuentas desde las que se transfiere. Un nombre reconocible ("Nequi del
-- dueño", "Bancolombia ···4521"), NUNCA el número completo.
CREATE TABLE cuenta_pago (
    id      uuid         PRIMARY KEY,
    nombre  varchar(80)  NOT NULL,
    activa  boolean      NOT NULL DEFAULT true
);

-- Sin distinguir mayúsculas: "nequi del dueño" y "Nequi del dueño" son la misma
-- cuenta. Si pudieran convivir, el total por cuenta se partiría en dos filas.
CREATE UNIQUE INDEX ux_cuenta_pago_nombre ON cuenta_pago (lower(nombre));

-- La forma de pago de las compras que ya existen no se conoce. Hoy son solo datos
-- de prueba, y se decidió marcarlas EFECTIVO (spec 0002, §4).
ALTER TABLE compra
    ADD COLUMN forma_pago varchar(15) NOT NULL DEFAULT 'EFECTIVO'
        CHECK (forma_pago IN ('EFECTIVO', 'TRANSFERENCIA'));

-- El default solo servía para rellenar las filas viejas. Se quita para que una
-- inserción nueva no herede "efectivo" sin que nadie lo haya decidido.
ALTER TABLE compra ALTER COLUMN forma_pago DROP DEFAULT;

ALTER TABLE compra
    ADD COLUMN cuenta_id uuid REFERENCES cuenta_pago (id);

-- Una transferencia siempre dice desde qué cuenta salió; el efectivo nunca lleva
-- cuenta. Lo exige la base, no solo la aplicación: un importador o un script que
-- inserte por otro camino también tiene que respetarlo.
ALTER TABLE compra
    ADD CONSTRAINT ck_compra_cuenta_segun_forma_pago
        CHECK ((forma_pago = 'TRANSFERENCIA') = (cuenta_id IS NOT NULL));

CREATE INDEX idx_compra_cuenta ON compra (cuenta_id) WHERE cuenta_id IS NOT NULL;
