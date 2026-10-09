# Plan 0017 — Abono mixto

## Contexto

El dueño pidió (2026-10-09) *«agrega que se pueda pagar mixto en los abonos, no sale hoy»*. El spec está en
[`spec.md`](spec.md). A la decisión respondió *«pagos mixtos en el total, por producto en todo como tal»*: el mixto se
escribe sobre **el total** del abono (cuánto en efectivo, el resto por transferencia) y marcar qué productos paga
(spec 0016) funciona igual. En pantalla eso es lo que da la **opción A** del spec, que es la que se sigue: **un pago
mixto son dos abonos que nacen juntos** (uno en efectivo, uno por transferencia), unidos por el pago.

Todo en local y en su rama. **Nada a producción sin la orden del dueño.**

### Hechos verificados que mandan el diseño

| Hecho | Dónde | Qué decide |
|---|---|---|
| `FormaPago` tiene **dos** valores a propósito | `domain/…/compartido/dominio/FormaPago.java` | *Mixto* no es una forma nueva: es un pago de dos abonos |
| El número de recibo sale de `update consecutivo … returning`, dentro de la transacción | `pos/…/clientes/infraestructura/RepositorioAbonosJpa.java:32-39` | Las dos partes reciben números seguidos; si algo falla, los dos vuelven a quedar libres |
| `numero` y `llave_idempotencia` son únicos | `pos/…/db/migration/V20__clientes_y_fiado.sql:119-120` | Cada parte con su número y su llave; la de la transferencia se **deriva** de la del pedido para que reintentar la encuentre |
| Cada abono cumple solo `forma IN (EFECTIVO, TRANSFERENCIA)`, *efectivo en turno* y *referencia solo en transferencia* | `V20:98,112-113` | La referencia va en la parte de transferencia; la base no cambia de reglas |
| El efectivo exige poder operar el turno; una transferencia de otro cajero no se cuelga al turno | `domain/…/clientes/aplicacion/RegistrarAbono.java:70-78` | En el mixto manda el efectivo: las dos partes quedan en el turno de quien recibe |
| Repartir: primero lo escogido, el resto a lo más viejo; lo que debe se valida contra el abono | `domain/…/clientes/dominio/CarteraDelCliente.java:138-176` | Hace falta repartir **el total** de las dos partes, no cada una por su lado |
| Anular un abono libera, anula y reparte lo que haya a favor | `CarteraDelCliente.java:211-225` | **Anular de a una parte cambia el resultado** (lo a favor puede caer en otra deuda); hace falta anular las dos y repartir una vez, como `anularDeudas` (`:188-209`) |
| Lo que entró al cajón solo se anula con su turno abierto | `domain/…/clientes/aplicacion/AnularAbono.java:61-66` | Un pago mixto solo se anula con su turno abierto, aunque se pida desde la parte de transferencia |
| El recibo sale de la ficha y la respuesta lleva una forma, un monto y lo aplicado | `ConsultarCartera.java:76-82`, `pos/…/clientes/infraestructura/AbonoController.java:111-125` | El recibo pasa a ser **del pago**: total, partes y lo aplicado sumado |
| El recibo en papel no recalcula y avisa si no cuadra | `frontend/src/utils/reciboAbono.js:18-50` | Se agrega la comprobación de que las partes den el total |
| La ficha lista abono por abono; cada producto lista sus abonos; se reimprime solo el recién recibido | `frontend/src/paginas/FichaCliente.jsx:311-337`, `:470`, `:194-209` | Una línea por pago; en cada producto, igual que hoy |
| Flyway sin `outOfOrder` | `pos/src/main/resources/application.properties:17-18` | El orden de las migraciones al desplegar importa (ver abajo) |

## Antes de empezar: la V32 de transferencias

Al escribir el plan, la V32 *transferencias en la caja* estaba en curso, sin commit, en la copia de trabajo de otra
sesión. **Salió a producción el 2026-10-09 a las 14:07**, mientras se implementaba esto (`f96fdc1`).

1. El 0017 va en **su rama** (`spec-0017-abono-mixto`), en una copia aparte (`git worktree`), sin tocar ese trabajo.
2. Su migración es la **V33** y la rama va **encima de `origin/main`**. **Nunca se despliega una migración de número
   menor después de una mayor, ni dos con el mismo número**: con la configuración de hoy, Flyway falla la validación al
   arrancar y **la app no sube**.
3. El 0017 no agrega puertos (el falso de abonos no cambia) ni toca clases de caja. La etiqueta *mixto* en
   *Caja › Abonos* queda opcional, para después.

## Dónde vive cada pieza

| Pieza | Carpeta |
|---|---|
| `Abono.pagoId`; `Abono.recibir(…, pagoId)`; la regla `Abono.partesDelMixto(total, efectivo)`; `Abono.llaveDeLaTransferencia(llave)` | `clientes/dominio/` |
| `CarteraDelCliente.abonar(List<Abono> partes, …)`, `anularAbonos(List<Abono>, …)` | `clientes/dominio/` |
| `RepositorioAbonos` | **sin cambios**: `delCliente` y `buscarPorLlave` alcanzan |
| `RegistrarAbono.ejecutarMixto`; `AnularAbono` anula el pago entero | `clientes/aplicacion/` |
| `FichaCliente.AbonoDeLaFicha` + `pagoId`; `ConsultarCartera.recibo` con las partes del pago | `clientes/aplicacion/` |
| La migración (`pago_id`) y su mapeo JPA en `Abono` | `pos/…/db/migration/`, `clientes/dominio/Abono.java` |
| `AbonoController` (pedir un mixto; el recibo del pago), `ClienteController` (la ficha con `pagoId`) | `pos/…/clientes/infraestructura/` |
| `ModalAbono.jsx`, `FichaCliente.jsx`, `utils/cartera.js`, `utils/reciboAbono.js` | `frontend/src/` |

## Fase 0 · Los documentos

- Spec: estado *con plan*; la decisión del dueño; RF-004 ajustado (hoy el recibo se ve y se reimprime en el aviso de
  *Abono recibido*, no hay una lista de abonos en *Cartera*); RF-007 (una línea por pago; por producto, igual).
- Este plan y la línea del índice (`docs/specs/README.md`).

## Fase 1 · El pago mixto en el dominio y la base

- **Migración `V33__abono_mixto`**:
  `ALTER TABLE abono ADD COLUMN pago_id uuid;`
  `CREATE UNIQUE INDEX ux_abono_pago_forma ON abono (pago_id, forma) WHERE pago_id IS NOT NULL;`
  El índice deja **a lo más una parte de cada forma** por pago y sirve para buscarlas. Los abonos de antes quedan con
  `pago_id` nulo: no hay nada que llenar y nada cambia de valor.
- **`Abono`**: campo `pagoId` (`updatable = false`), también en `fotografia()`. `recibir(…, pagoId)`; el de hoy delega
  con `null`. `partesDelMixto(total, efectivo)` devuelve `[efectivo, total − efectivo]` o el error
  *"En mixto, el efectivo es una parte: menos que el total"* (efectivo nulo, $0, igual o mayor al total).
  `llaveDeLaTransferencia(llave)` = `UUID.nameUUIDFromBytes("abono-mixto:" + llave)`: siempre la misma para la misma
  llave.
- **`CarteraDelCliente.abonar(List<Abono> partes, List<UUID> primero, Instant cuando)`**:
  1. valida como hoy, pero contra **el total**: que deba, que no supere lo que debe, las escogidas una sola vez;
  2. agrega las partes;
  3. a cada escogida, en su orden, le aplica lo que le falta tomando de las partes en orden (**primero el efectivo**,
     después la transferencia);
  4. lo que le sobre a cada parte va a lo más viejo (`repartir`);
  5. todas anotan el mismo *debe después*: el final.
  El `abonar(Abono, …)` de hoy delega con una sola parte y da exactamente lo mismo (las pruebas de hoy no cambian).
- **`CarteraDelCliente.anularAbonos(List<Abono>, motivo, por, cuando)`**: libera todas las partes, las anula todas y
  reparte lo que haya a favor **una vez**. `anularAbono` delega.
- **`RegistrarAbono.ejecutarMixto(llave, clienteId, total, efectivo, referencia, nota, primero, actor)`** → las partes:
  - llave ya usada (antes y después de bloquear al cliente, como hoy) → las partes de ese pago, sin crear nada;
  - el turno, con la regla del efectivo (`exigirQuePuedaOperar`); las dos partes en ese turno;
  - parte en efectivo: número *n*, la llave del pedido. Parte por transferencia: número *n + 1*, la llave derivada, la
    referencia. Las dos con el mismo `pagoId` nuevo y la nota;
  - `cartera.abonar(partes, primero, ahora)`; se guardan las deudas y después las dos partes.
- **`AnularAbono`**: si el abono es de un pago, toma sus partes vigentes. Aplica la regla del turno a **cada** parte que
  entró al cajón, así que pedirlo desde la transferencia no se salta un turno cerrado. Después `anularAbonos` y un
  evento de auditoría por parte, con el mismo motivo.
- **Pruebas** (`CarteraDelClienteTest`, `AbonosEnElCajonTest`), con cifras escritas a mano:
  - $50.000 mixto ($20.000 + $30.000) a un producto de $50.000: queda pagado, con $20.000 en efectivo y $30.000 por
    transferencia; las dos partes con el mismo *debe después*.
  - Marcando MOTUL $62.880 y luego GUAYA $26.120, con $30.000 + $59.000: efectivo $30.000 al MOTUL; transferencia
    $32.880 al MOTUL y $26.120 a la GUAYA.
  - Sin marcar: a lo más viejo, el efectivo primero.
  - Errores: efectivo $0, igual al total o mayor; total mayor a lo que debe; sin turno; turno de otro cajero.
  - La misma llave dos veces: las mismas dos partes y ningún número nuevo.
  - El cajón: el esperado sube solo lo del efectivo.
  - Anular el pago, pedido por cualquiera de las dos partes: las dos quedan anuladas. Con algo a favor del cliente
    (una venta anulada después), lo a favor va a lo más viejo. **Romper a propósito**: anular de a una parte → esa
    prueba falla. Con el turno cerrado, se rechaza aunque se pida por la transferencia.

## Fase 2 · La API

- `PeticionAbono.forma` pasa a un enum de la petición `{EFECTIVO, TRANSFERENCIA, MIXTO}` (en el controlador;
  `FormaPago` no cambia), más `Long efectivo`, que solo va en `MIXTO`. Una pantalla vieja manda lo de hoy y funciona
  igual.
- `RespuestaAbono` pasa a ser **el recibo del pago**:
  - `forma` como texto (`MIXTO` en un mixto); `monto` el total; `numero` el de la primera parte;
  - `partes`: `[{id, numero, forma, monto, referencia}]` (una sola en un abono normal);
  - `aplicaciones` sumadas por producto, en el orden en que se aplicaron; `sinAplicar` sumado; `debeDespues` el del
    pago.
  `ConsultarCartera.recibo` devuelve las partes del pago: `GET /api/abonos/{id}` con cualquiera de las dos da el mismo
  recibo. Si dos pedidos chocan por la llave (`MovimientoRepetidoException`), se responde el recibo del pago,
  cualquiera que haya sido la llave del choque.
- La ficha (`ClienteController`): cada abono con su `pagoId`. Lo de cada producto (`ParteDeAbono`) no cambia: ya dice
  qué abono y qué forma lo pagó.
- **Pruebas**:
  - `AbonoMixtoIntegracionTest` (Postgres 18 propio, por HTTP):
    - fiar MOTUL + GUAYA; abonar mixto marcando; el recibo; la ficha;
    - el arqueo del turno (abonos en efectivo, por transferencia, esperado);
    - el reporte del día (efectivo, transferencia, ventas netas);
    - repetir con la misma llave (200, el mismo recibo);
    - anular por la parte de transferencia (las dos quedan anuladas y el esperado vuelve);
    - anular con el turno cerrado (rechazado).
  - `MigracionesIntegracionTest`: la migración sobre una base con abonos (siguen iguales) y el índice no deja dos
    partes en efectivo en un mismo pago.

## Fase 3 · Las pantallas

- **`ModalAbono.jsx`**: *Efectivo · Transferencia · Mixto*. En *Mixto*:
  - *Parte en efectivo* debajo de *Cuánto abona*, con *Por transferencia: $X* al lado;
  - *Referencia (opcional)* de la transferencia;
  - marcar productos sigue llenando *Cuánto abona*;
  - el botón dice *Recibir $50.000 ($20.000 + $30.000)*;
  - el error del mixto se muestra antes de mandar. Cabe a 390 px.
- **`utils/cartera.js`**: `problemaDelMixto(total, efectivo)`; `pagosDeLaFicha(abonos)`, que junta las partes de un
  pago en una línea con el total y las partes.
- **`FichaCliente.jsx`**: en *Abonos*, una línea por pago, por ejemplo *"Abono N.º 7 · Mixto: efectivo $20.000 +
  transferencia $30.000 (N.º 8)"*, con lo aplicado sumado. *Anular* avisa que se anulan las dos partes. Cada producto,
  igual que hoy (N.º 7 efectivo, N.º 8 transferencia).
- **`utils/reciboAbono.js`**: con dos partes, *Cómo pagó* va en dos renglones (cada uno con su número) y el total.
  `problemasDelRecibo` también comprueba que las partes den el total.
- Opcional, **después de la V32**: en *Caja › Abonos*, la etiqueta *mixto* en las dos partes.
- **Pruebas**: `cartera.test.js`, `reciboAbono.test.js`. **Capturas** con Edge contra el servidor local, claro y
  oscuro, a 1280 y 390: abonar mixto marcando, el recibo, la ficha y anular.

## Fase 4 · De punta a punta y ensayo

- QA en local con la base de QA: el recorrido completo de los criterios de aceptación, con la Caja y el reporte.
- Ensayo sobre una copia de producción (en solo lectura sobre producción): la migración sobre una base con datos, y una
  foto antes y después. Todo debe salir igual, salvo `pagoId` nulo y `partes` en los recibos, que son nuevos.
- Los pasos se agregan al QA del despliegue.

## Verificación

1. `./mvnw clean verify` en una copia aparte (`git worktree`), para no tumbar el servidor local.
2. `npx eslint src/ && node --test src/utils/*.test.js && npx vite build`.
3. Local: servidor 8081, pantalla 5174 (`ruben` / `rdmotors2026`), capturas desde PowerShell.
4. Romper a propósito, por fase:
   - anular de a una parte;
   - aplicar la transferencia antes que el efectivo.
5. Antes de desplegar, confirmar el orden de las migraciones (V32 y la del 0017). Nada a producción sin la orden del
   dueño.

## Riesgos

- **El orden de las migraciones** (arriba): el único que puede tumbar el arranque.
- **Pantallas viejas abiertas**: siguen abonando en efectivo o por transferencia. Un recibo mixto en una pantalla vieja
  sale con forma *MIXTO* y lo aplicado, sin las partes: se ve, no se rompe.
- **Dos números por un pago**: a propósito. Cada parte cuadra con su lado en la Caja, y el recibo muestra los dos.

## Bitácora de decisiones

- 2026-10-09 — El dueño: *mixto en el total, por producto como hoy* → opción A.
- 2026-10-09 — **La V32 de transferencias salió a producción a las 14:07** (otra sesión), mientras esto se hacía. Se vio
  al leer la versión de Flyway de producción antes del ensayo: la migración del 0017 iba a ser la V32 y la app no habría
  arrancado. Pasó a **V33** y la rama se puso encima de `f96fdc1`.
- 2026-10-09 — **Anular bloquea el turno antes que el cliente**, en el mismo orden en que se bloquean al abonar: al
  revés, un abono y una anulación a la vez podían quedar esperándose. Un mixto pide el turno abierto aunque se anule
  desde la parte de transferencia (sus dos partes son del mismo turno).
- 2026-10-09 — **Romper a propósito**: anular de a una parte hace fallar `anularMixtoJuntas`; aplicar la transferencia
  antes que el efectivo hace fallar `mixtoMarcandoDos`. *Derivar la llave al azar* se quitó de la lista: no rompe
  nada, porque un reintento encuentra el pago por la llave del pedido (la de la parte en efectivo); la derivada solo
  hace que la de transferencia sea única y siempre la misma.
- 2026-10-09 — El recibo junta lo aplicado por las dos partes **en el orden de la ficha** (el de pago de las deudas),
  no en el orden en que se aplicó: es el mismo que tiene el recibo de un abono de una forma.
- 2026-10-09 — El botón dice *Recibir $70.000*, sin las dos cifras: la división se ve justo arriba (*Por
  transferencia*), y así cabe a 390 px.
