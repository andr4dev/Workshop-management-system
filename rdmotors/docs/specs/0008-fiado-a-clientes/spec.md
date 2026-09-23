# Spec 0008 — Fiado a clientes y cartera

**Estado:** aprobado el 2026-09-21 (el usuario mandó implementarlo, con las recomendaciones) · [plan](plan.md)
**Rebanada:** fase 2 del cliente (P2 confirmado) · ver [`PLAN_DE_TRABAJO.md`](../../PLAN_DE_TRABAJO.md)
**Depende de:** specs [0003](../0003-venta-de-mostrador/spec.md), [0004](../0004-usuarios-y-roles/spec.md),
[0006](../0006-cierre-de-caja/spec.md) y [0007](../0007-reportes-de-resultados/spec.md), implementados
**No es:** compras a crédito a proveedores (cuentas por pagar). Eso sigue pendiente de consultarlo con el cliente.

> **Convención:** lo que dice *"hoy"* está verificado con `archivo:línea`. Lo que dice *"debe"* es
> propuesta y todavía no existe. Las dos cosas nunca se mezclan.

**Cambios de la versión 2** (pedidos por el usuario el 2026-09-21): **se quita el cupo**; **fiar exige los datos
del cliente** para cerrar la venta; y todo lo del cliente —cuánto debe, qué se le fió, qué ha abonado— vive en un
módulo de **Cartera al estilo del car‑wash**.

---

## 1. Objetivo de negocio

Un almacén de repuestos le fía a sus clientes de confianza: el mecánico del barrio se lleva unas pastillas y
un filtro y paga el viernes. El cliente lo pidió con estas palabras: *"Crédito a clientes"* e *"Historial de
compras por cliente"*, confirmado como fase 2 (`SPEC_Sistema_Ventas_Repuestos (3).md:144-153` y `:244`).

Hoy **el sistema no puede fiar**: una venta solo se cobra si los pagos suman el total al peso
(`domain/…/ventas/dominio/Venta.java:194-209`). Entonces el fiado pasa por fuera, y eso deja dos agujeros:

- Si se registra la venta como *"efectivo"* sin que entre plata, el cierre de caja da un **faltante** que no es
  de nadie, y el arqueo deja de poder señalar a una persona.
- Si no se registra, **el stock no baja** y el cuaderno del fiado no cuadra con nada del sistema.

Con este spec:

- **Nadie se lleva mercancía fiada sin quedar anotado**: el nombre es obligatorio; la cédula y el celular se piden
  y quedan recordados hasta completarlos (decisión 2, cambiada el 2026-09-21).
- Lo fiado baja el stock, cuenta como venta y no se espera en el cajón.
- En un solo lugar, la **Cartera**, se ve quién debe, cuánto, desde cuándo, qué venta sigue pendiente y qué ha
  abonado cada uno.

---

## 2. Caso de uso

### P1 — bloquean

**H1 · Fiar exige los datos del cliente.** En *Vender*, al cobrar, se escoge dejar fiado todo o una parte. La
venta se cierra con el cliente anotado: se busca por nombre, cédula o celular, y si no
existe se crea ahí mismo, sin salir de la venta. *Demo:* se fían $50.000 a alguien nuevo sin escribir su celular
y ofrece completarlos sin frenar el cobro; se cobra, baja el stock y el cajón **no** espera esos $50.000.

**H2 · Fiar una parte.** *"Paga $20.000 en efectivo y el resto lo debe."* El comprobante dice a nombre de quién
se fió, cuánto quedó fiado y **cuánto debe ahora** en total. *Demo:* venta de $80.000, $30.000 en efectivo y
$50.000 fiados; el cajón espera solo los $30.000.

**H3 · La Cartera.** Un módulo propio en el menú, como el del car‑wash. Arriba, cuántos clientes deben y el
total por cobrar. Debajo, los clientes que deben, del que más al que menos. Al abrir uno: sus datos, cuánto
debe y desde cuándo, **cada venta fiada con su estado** (*Pendiente · Abonado · Pagado*) y los abonos debajo
de cada una, y la línea de tiempo de abonos con quién los recibió. *Demo:* se abre a Juan y se ve la venta
N.º 41 de $50.000 *Abonada*, con el abono de $30.000 del viernes debajo, y que debe $20.000 desde el 12 de
septiembre.

**H4 · El cliente abona lo que tenga.** Desde la Cartera, cualquier monto hasta lo que debe, en efectivo o por
transferencia. Se aplica **a lo más viejo primero**, o a la venta que el cliente diga (*"esto es de la
factura 57"*). El efectivo entra al cajón del turno. Sale un comprobante del abono. *Demo:* Juan debe la 41
($50.000) y la 57 ($30.000) y abona $60.000: la 41 queda *Pagada*, la 57 *Abonada* con $20.000 pendientes, y
el esperado del cajón sube $60.000.

### P2 — importantes, no bloquean

**H5 · Historial completo** (el *"Ver historial completo"* del car‑wash). Todos los clientes que alguna vez
tuvieron fiado, **también los que ya están al día**, buscables por nombre, cédula o celular, con cuánto se les
ha fiado en total, cuánto han pagado y su último movimiento.

**H6 · Historial de compras por cliente** (lo pidió el cliente). Cualquier venta, fiada o de contado, se puede
poner a nombre de un cliente, y su ficha las muestra todas. *Demo:* Juan tiene 6 compras este mes, 2 fiadas.

**H7 · Filtrar por fechas.** La cartera por **fecha de la venta** (*"lo que se fió esta semana"*) o por **fecha
del abono** (*"lo que se cobró hoy"*), como el car‑wash.

**H8 · Los reportes lo dicen bien.** En *Resultados*: cuánto se vendió fiado en el período, cuánto se cobró en
abonos y cuánto está por cobrar hoy. **Las ventas netas no cambian**: lo fiado es venta el día que se vende.

**H9 · Anular una venta fiada.** Devuelve el stock y le baja la deuda al cliente. Por la parte fiada no sale
plata del cajón.

**H10 · La deuda que ya existe en el cuaderno.** El administrador carga, una vez, lo que cada cliente ya debe
hoy (*saldo del cuaderno*), con su fecha y motivo. Sin esto el sistema arranca diciendo que nadie debe nada.

**H11 · No fiarle más a alguien.** Sin cupo, la manera de frenar a quien no paga es que el administrador **le
cierre el fiado**: sigue comprando de contado y abonando, pero no se le fía más.

### P3 — deseables

**H12 · Exportar la cartera en PDF**, con el filtro que esté puesto (como el car‑wash).

**H13 · Aviso de deuda vieja.** En la Cartera se marcan los que deben hace más de 30 días.

---

## 3. Qué existe hoy

### En RD MOTORS

| Qué | Dónde | Qué significa para este spec |
|---|---|---|
| Una venta exige que los pagos sumen **el total al peso**, y un pago por forma | `domain/…/ventas/dominio/Venta.java:194-209` | Hay que abrir un tercer componente: pagado + **fiado** = total |
| Las formas de pago son **efectivo y transferencia**, y solo el efectivo entra al cajón | `domain/…/compartido/dominio/FormaPago.java` | Esa lista **la comparten compras y gastos** (`compras/dominio/Compra.java:81`). Meter "fiado" ahí lo volvería posible en una compra o un gasto |
| La base solo acepta esas dos formas en los pagos de una venta | `pos/…/db/migration/V9__venta.sql:88` | Si el fiado fuera una forma de pago, habría que reescribir esa regla |
| El arqueo suma lo cobrado **por forma de pago**; de una venta anulada devuelve solo su efectivo | `domain/…/caja/dominio/ArqueoDeTurno.java:53-75` | Lo fiado no entraría al cajón (bien), pero **los abonos no existen** en la cuenta del cajón |
| El cierre firmado guarda sus partes, y la base obliga a que sumen: `esperado = fondo + ventas en efectivo − devoluciones − gastos − retiros − compras` | `pos/…/db/migration/V14__cierre_de_turno.sql:33-35` | Un abono en efectivo es una parte nueva: **columna nueva y esa regla reescrita, sobre turnos que ya existen** |
| Anular devuelve lo cobrado desde **el turno de hoy** | `domain/…/ventas/aplicacion/AnularVenta.java:74` | La parte fiada no se devuelve de ningún cajón: se descuenta de la deuda |
| Los reportes cuentan la venta por su **total** y la reparten por forma de pago | `pos/…/reportes/infraestructura/RepositorioReportesJdbc.java:48-53` | Lo fiado ya contaría como venta (bien). Pero… |
| …el reporte **prueba** que `efectivo + transferencia = ventas netas` | `domain/…/reportes/dominio/ResultadosDelPeriodo.java:22-27` | Con fiado esa igualdad es falsa: pasa a `efectivo + transferencia + fiado = ventas netas` |
| El comprobante lista los pagos; el cobro ofrece efectivo o transferencia | `frontend/src/utils/ticket.js:66`, `frontend/src/componentes/venta/ModalCobro.jsx:10-12` | El comprobante tiene que decir lo fiado, el cliente y lo que debe |
| **No existe el cliente**: ni tabla, ni ficha, ni la venta guarda a quién se le vendió | búsqueda en `domain/` y `pos/` sin resultados | Todo lo de clientes es nuevo, y las ventas de antes quedan sin cliente |
| El cajero cobra **en su turno**; el administrador en cualquiera | spec 0004, §5 | Fiar y recibir abonos en efectivo siguen esa misma regla |

### En el car‑wash: qué se porta y qué no

| Qué hace el car‑wash | Dónde | Aquí |
|---|---|---|
| **Cartera**: total pendiente arriba; una tarjeta por cliente con lo que debe; al abrirla, la línea de tiempo de cobros y *"Créditos por cobrar"* con un botón *Cobrar* por orden | `CAR-WASH-SYSTEM/frontend/src/pages/app/CarteraPage.jsx:200-230` y `:439-532` | **Se porta la pantalla** (H3) |
| **Historial completo**: todos los clientes que alguna vez tuvieron crédito, buscables, con *prestado histórico · cobrado · último movimiento* y *"Al día"* | `…/components/credit/CreditHistoryModal.jsx:137-190` | **Se porta** (H5) |
| El detalle agrupa **por orden**, con los abonos debajo y su estado *Pendiente · Abonado · Pagado*; su comentario explica por qué: una lista plana mezcla la fecha de la deuda con la del pago | `…/CreditHistoryModal.jsx:35-48` y `:221-280`; `…/utils/creditPortfolioUtils.js:28-32` | **Se porta**, por venta (decisión 1) |
| Filtro por fecha del lavado o del cobro, con *Todo, Hoy, Ayer, Esta semana, Este mes, Fecha específica, Rango* | `…/utils/creditPortfolioUtils.js:16-19` y `:45-53`; `CarteraPage.jsx:232-300` | **Se porta** (H7): fecha de la venta o del abono |
| Exportar la cartera en PDF | `CarteraPage.jsx:164-198` | P3 (H12) |
| Comprobante del cobro, impreso al registrarlo | `…/components/credit/CreditCollectionModal.jsx:67-74` | **Se porta**, con el formato de la ticketera de aquí |
| Pagar a crédito exige un cliente en la orden | `CAR-WASH-SYSTEM/backend/…/service/impl/PaymentServiceImpl.java:175-178` | **Se porta igual**: allá la cédula y el celular son opcionales (`…/entities/Person.java:37-52`), y aquí también — se piden, no se exigen (decisión 2) |
| El crédito **no se combina** con otro medio: la orden va toda a crédito o nada | `PaymentServiceImpl.java:162-166` | **No se porta**: aquí se fía una parte (decisión 3) |
| El cobro es **por el total pendiente de la orden**, con el monto fijo y no editable | `…/service/impl/CreditServiceImpl.java:126-130`; `CreditCollectionModal.jsx:173-179` | **No se porta**: aquí el cliente abona lo que tenga (decisión 1) |
| El crédito es **un medio de pago más**, y por eso tiene que prohibir *"pagar un crédito con otro crédito"* | `CreditServiceImpl.java:102-104`; `…/entities/CreditCollection.java:54` | **No se porta** (decisión 4) |
| *"¿Entra a la caja de hoy? Sí / No, fuera de caja"* | `CreditCollectionModal.jsx:121-142`; `CreditServiceImpl.java:142-159` | **No se porta**: el efectivo que se recibe en la tienda entra al cajón. Plata fuera del cajón es plata que el arqueo no puede señalar |
| El cobro cuenta como **ingreso del día en que entró la plata** | `CreditCollection.java:66-85` | **Distinto aquí**: la venta fiada es venta **el día que se vende**, porque la ganancia necesita el costo de lo que salió ese día. El abono es un cobro, no una venta |
| Candado sobre la cuenta del cliente: sin él, dos cobros a la vez dejaban el saldo **negativo** (lo aprendieron con un error real) | `CreditServiceImpl.java:114-122` | **Se porta la lección** |
| Llave contra el doble cobro | `CreditServiceImpl.java:93-100` | Igual que cobrar aquí |
| Recordatorios de deuda por SMS o WhatsApp | `…/service/impl/CreditReminderServiceImpl.java` | Fuera de alcance; con el celular obligatorio queda listo para después |

---

## 4. Las decisiones

### Decisión 1 · ¿Cómo se lleva la deuda y cómo se abona? — la que cambia el alcance

| Opción | Qué implica |
|---|---|
| **A. Por venta, como el car‑wash, pero con abono libre** | Cada venta fiada es una deuda con su estado. El cliente abona **cualquier monto** y se reparte de la venta más vieja a la más nueva, o a la que él diga. Lo que debe es la suma de lo pendiente de sus ventas |
| B. Igual al car‑wash | Cada venta se cobra completa, de una. Es lo más simple, pero el mecánico que debe $180.000 y trae $50.000 **no puede abonar** |
| C. Solo un saldo por cliente (el cuaderno) | Sencillo, pero la Cartera no puede decir qué venta sigue pendiente ni verse como la del car‑wash |

**Recomendación: A.** Da la pantalla del car‑wash —venta por venta, con su estado y sus abonos debajo— sin el
límite que allá obliga a pagar una orden entera. Y en el mostrador nadie reparte nada a mano: si el cliente no
dice a qué va, se aplica solo a lo más viejo. *Desde cuándo debe* sale directo: la fecha de la venta pendiente
más vieja.

### Decisión 2 · ¿Qué datos se exigen para fiar?

> **Cambiada el 2026-09-21, después de usarla.** La primera versión los exigía; el dueño la revirtió al probar
> el mostrador: *«cliente fiado, celular y cédula o NIT opcionales»*.

**Decidido: solo el nombre es obligatorio**, también para fiar. La cédula o NIT, el celular, la dirección y la
nota son opcionales y se pueden completar después.

- **Por qué se cambió:** con el cliente enfrente, frenar la venta por un dato que no trae encima es peor que
  fiar con lo que hay. Un conocido del barrio no siempre carga la cédula, y el mostrador no puede quedarse
  esperando.
- **Lo que se pierde, dicho claro:** sin la cédula, dos *"Juan"* pueden terminar en el mismo renglón de la
  cartera; sin el celular no hay a quién llamarle a cobrar. Por eso la pantalla **los pide igual**, ofrece
  anotarlos ahí mismo y deja el recordatorio en la ficha hasta que se completen.
- La cédula, **si se escribe, no se repite**: aparece el cliente que ya existe en vez de crear otro.
- Dos clientes sin cédula sí pueden existir: se distinguen por el nombre y por lo que deben.

### Decisión 3 · ¿Se puede fiar una parte?

**Propuesta: sí.** *"Paga $20.000 y el resto lo debe"* es lo normal en el mostrador. El car‑wash no lo deja
(§3); aquí lo pagado más lo fiado suman el total al peso, igual que hoy los pagos.

### Decisión 4 · ¿El fiado es una "forma de pago"?

**Propuesta: no.** Es **lo que no se pagó**. Si fuera una forma de pago más, quedaría disponible en compras y
gastos (comparten la lista, §3), la regla de la base se reescribiría para los tres, y habría que prohibir, como
el car‑wash, abonar un fiado con otro fiado. Queda como su propia parte de la venta, con el cliente al lado.

### Decisión 5 · Sin cupo, ¿quién puede qué?

**Propuesta:**

| Quién | Qué |
|---|---|
| **Cajero** (en su turno) | Fiar, con los datos completos · crear clientes y completar datos que falten · recibir abonos · ver la Cartera (no tiene costos) |
| **Administrador** | Todo lo anterior · **corregir** un dato ya escrito (nombre, cédula, celular) · cerrarle el fiado a un cliente · cargar el saldo del cuaderno · anular abonos |

Sin cupo, lo que frena un fiado indebido es: nadie fía sin identificar al cliente, cada venta fiada dice
**quién la fió**, y el administrador ve en la Cartera todo lo que se fió hoy.

### Decisión 6 · ¿Qué pasa si se anula una venta fiada que ya tenía abonos?

Juan debía la venta 41 ($50.000), le había abonado $30.000 y se anula.
**Propuesta:** esos $30.000 pasan a las otras ventas pendientes de Juan, de la más vieja a la más nueva. Si no
debe nada más, le quedan **a favor**. [NECESITA ACLARACIÓN: un saldo a favor, ¿se le devuelve en efectivo o
siempre queda para la próxima compra?]

---

## 5. Requisitos funcionales

### Clientes
- **RF-001** · Un cliente tiene **nombre** (obligatorio), cédula o NIT, celular, dirección y nota. Una cédula o
  NIT no se repite.
- **RF-002** · Para fiar basta el **nombre** (decisión 2, cambiada el 2026-09-21). La cédula o NIT y el celular
  se piden en el cobro y quedan como recordatorio en la ficha, pero **no frenan la venta**. Lo único que impide
  fiarle es que el administrador le haya cerrado el fiado (RF-005).
- **RF-003** · En el cobro, el cliente se busca por nombre, cédula o celular, **sin distinguir tildes ni
  mayúsculas** (como los repuestos). Si no existe, se crea ahí mismo sin salir de la venta, y la venta a medias
  no se pierde mientras tanto.
- **RF-004** · El cajero crea clientes y completa los datos que falten. **Corregir** un dato ya escrito es del
  administrador y queda en la auditoría con el antes y el después.
- **RF-005** · Un cliente no se borra. (P2) El administrador le puede **cerrar el fiado**, con motivo: sigue
  comprando de contado y abonando, pero no se le fía más. Se puede volver a abrir. Queda en la auditoría.

### Fiar
- **RF-006** · Al cobrar se puede dejar fiado todo o una parte: lo pagado más lo fiado suman **el total al peso**.
- **RF-007** · Lo fiado **no entra al cajón**: el esperado del turno no lo cuenta.
- **RF-008** · Cada venta fiada guarda su cliente, cuánto se fió, **quién la fió** y cuánto le falta por pagar.
- **RF-009** · El comprobante dice a nombre de quién (nombre y cédula), cuánto quedó fiado y **cuánto debe
  ahora** en total.
- **RF-010** · Cobrar fiado sigue las reglas de siempre: llave contra el doble cobro, en el turno de quien lo
  abrió (spec 0004), y un cobro que no respondió se reintenta igual, sin fiar dos veces.

### Abonar
- **RF-011** · Un abono lleva su monto, su forma (efectivo o transferencia), la referencia si es transferencia
  (opcional) y una nota. Lleva llave: un doble clic no abona dos veces.
- **RF-012** · El abono se aplica a las ventas pendientes **de la más vieja a la más nueva**, salvo que se
  escoja una venta. A una venta nunca se le aplica más de lo que le falta: lo que sobra pasa a la siguiente.
  **Las partes aplicadas suman el abono al peso.**
- **RF-013** · **El abono en efectivo entra al cajón del turno abierto** y aparece en su arqueo y en su cierre
  como una parte propia: *"abonos de clientes"*. El de transferencia no entra al cajón.
- **RF-014** · Sin turno abierto no se recibe un abono en efectivo (no hay cajón al que entre); por
  transferencia sí.
- **RF-015** · Un abono no puede ser mayor que lo que el cliente debe.
- **RF-016** · Sale un comprobante del abono: cuánto, cómo, a qué ventas se aplicó y cuánto sigue debiendo.
- **RF-017** · Un abono mal registrado se **anula** con motivo, mientras su turno siga abierto (igual que un
  gasto o un retiro): las ventas a las que se aplicó vuelven a deber eso. Queda en la auditoría.

### La Cartera
- **RF-018** · La Cartera es un módulo del menú, para los dos roles. Arriba: cuántos clientes deben y el total
  por cobrar. Debajo, los clientes que deben, del que más al que menos, cada uno con nombre, cédula, cuánto
  debe, cuántas ventas tiene pendientes y **desde cuándo** debe (la venta pendiente más vieja).
- **RF-019** · Al abrir un cliente: sus datos (cédula, celular, dirección), cuánto debe, y **sus ventas fiadas**
  con fecha, número, lo fiado, lo abonado, lo pendiente y su estado (*Pendiente · Abonado · Pagado*), con los
  abonos debajo de cada una. Y la **línea de tiempo de abonos**: fecha, monto, forma y quién lo recibió. Desde
  ahí se abona.
- **RF-020** · Lo que debe un cliente es **la suma de lo pendiente de sus ventas y de su saldo del cuaderno**, al
  peso. La pantalla no suma: lo dice el servidor.
- **RF-021** · (P2) **Historial completo**: todos los clientes que alguna vez tuvieron fiado, también los que
  están al día, buscables por nombre, cédula o celular, con lo fiado en total, lo pagado en total y el último
  movimiento; los que no deben dicen *"Al día"*.
- **RF-022** · (P2) Filtro por **fecha de la venta** o por **fecha del abono**, con *Todo, Hoy, Ayer, Esta
  semana, Este mes, Fecha específica* y *Rango*.
- **RF-023** · (P2) La ficha del cliente muestra **todas** sus compras, fiadas o no. Al cobrar de contado,
  poner la venta a nombre de un cliente es opcional.
- **RF-024** · (P2) *Resultados* dice, para el período: vendido fiado, cobrado en abonos, y **por cobrar hoy**.
  Las ventas netas no cambian: `efectivo + transferencia + fiado = ventas netas`. **Un abono no es una venta.**
- **RF-025** · (P3) Exportar a PDF la cartera que se está viendo, con su filtro.
- **RF-026** · (P3) Se marcan los clientes cuya venta pendiente más vieja tiene más de 30 días.

### Anular y cargar lo de antes
- **RF-027** · (P2) Anular una venta fiada devuelve el stock y le quita al cliente **lo pendiente de esa
  venta**. Lo que ya se le había abonado se trata según la decisión 6.
- **RF-028** · (P2) El administrador carga el **saldo del cuaderno** de un cliente —lo que ya debía—, con su
  fecha y motivo, una sola vez por cliente. En la Cartera aparece como una deuda más (*"Saldo del cuaderno"*), y
  como es lo más viejo, los abonos lo pagan primero. Queda en la auditoría.

---

## 6. Manejo de errores

| Situación | Qué pasa |
|---|---|
| Fiar sin los datos completos | Se cobra igual. La pantalla ofrece anotar lo que falta antes de cerrar, y la ficha del cliente lo sigue recordando |
| Crear un cliente con una cédula que ya existe | *"Esa cédula es de Juan Pérez (debe $20.000)"*, y se usa ese cliente: no se crea otro |
| Fiar a un cliente al que se le cerró el fiado | *"A Juan Pérez no se le fía: lo cerró el administrador. Puede pagar de contado"*; no se cobra |
| Lo pagado más lo fiado no da el total | El mismo mensaje de hoy: *"faltan $X"* / *"sobran $X"* |
| Abono en efectivo sin turno abierto | *"No hay un turno abierto: el efectivo no tiene a qué cajón entrar. Ábrelo en Vender, o recíbelo por transferencia"* |
| Abono mayor que la deuda | *"Juan debe $20.000: no se le puede recibir más"* |
| Abonar a una venta que ya está pagada | *"La venta N.º 41 ya está pagada"* |
| Dos abonos al mismo cliente al mismo tiempo (dos equipos) | Uno espera al otro; el segundo ve la deuda ya rebajada. Nunca queda debiendo menos de cero |
| Doble clic en abonar o en cobrar fiado | Se registra una sola vez (llave) |
| Anular un abono de un turno ya cerrado | *"Ese turno ya se cerró: el abono quedó en su arqueo"*, como un gasto |
| El cajero intenta corregir un dato, cerrar el fiado o cargar un saldo del cuaderno | *"No permitido: es del administrador"* |

---

## 7. Requisitos no funcionales

- **Plata.** Todo en pesos enteros. En cada venta: pagado + fiado = total. En cada venta fiada: abonado +
  pendiente = fiado. En cada abono: lo aplicado a cada venta suma el abono. En cada cliente: lo que debe = Σ lo
  pendiente de sus ventas y de su saldo del cuaderno. Cada igualdad con su prueba.
- **Caja.** El abono en efectivo es una **parte nueva del esperado** del cajón, a la vista y firmada en el cierre.
  Los turnos que ya se cerraron quedan con esa parte en $0 y su cierre sigue cuadrando.
- **Reportes.** La venta fiada cuenta como venta **el día que se vende**; el abono cuenta como **cobro**, nunca
  como venta. Si se contara dos veces, la ganancia del período saldría inflada.
- **Roles.** Los de la decisión 5. El bloqueo va en el servidor, con su prueba; esconder el botón no protege nada.
- **Concurrencia.** Todo lo que cambia la deuda de un cliente (fiar, abonar, anular) toma un candado sobre ese
  cliente, para que dos operaciones a la vez no la dejen mal (la lección del car‑wash, §3).
- **Auditoría.** Corregir los datos de un cliente, cerrar o abrir su fiado, cargar un saldo del cuaderno, anular
  un abono y anular una venta fiada quedan con quién, cuándo y motivo.
- **Datos personales.** La cédula y el celular se piden para identificar y cobrar, y no se usan para nada más.
  No salen en listados que no los necesiten.
- **Sin internet.** Nada de esto necesita internet: vive en el computador de la tienda. Cuando exista la nube
  (rebanada 5, spec 0009), fiados y abonos viajan como eventos para que el dueño vea *por cobrar* desde su casa.
- **Esquema.** Tablas nuevas de clientes, deudas por venta, abonos y lo aplicado de cada abono; la venta gana un
  cliente opcional y lo fiado; el turno gana la parte de abonos. Todo con migraciones nuevas que funcionen
  **sobre la base que ya tiene ventas y turnos cerrados**.

---

## 8. Criterios de aceptación

- [ ] Fiar a alguien que solo dio su nombre se puede, y su deuda queda en la cartera como cualquier otra; la
      pantalla y su ficha siguen recordando que faltan la cédula y el celular
- [ ] Un cliente nuevo se crea desde el cobro, sin perder la venta a medias
- [ ] Escribir una cédula que ya existe trae a ese cliente, no crea otro
- [ ] Se vende $50.000 a Juan, todo fiado: baja el stock, cuenta como venta y **el cajón no la espera**
- [ ] Se vende $80.000: $30.000 en efectivo y $50.000 fiados; el cajón espera solo los $30.000
- [ ] Juan debe la 41 ($50.000) y la 57 ($30.000), abona $60.000 en efectivo: la 41 queda *Pagada*, la 57
      *Abonada* con $20.000, el esperado del cajón sube $60.000 y el cierre lo muestra como *abonos de clientes*
- [ ] Un abono dirigido a la 57 se aplica a la 57, aunque la 41 sea más vieja
- [ ] Un abono por transferencia baja la deuda y **no** mueve el cajón
- [ ] La Cartera muestra el total por cobrar, quién debe más y desde cuándo, y al abrir a Juan, sus ventas con
      estado, sus abonos y quién los recibió
- [ ] El historial completo encuentra a un cliente que ya está *Al día*
- [ ] El comprobante de la venta y el del abono dicen cuánto debe Juan después
- [ ] A un cliente con el fiado cerrado no se le fía, pero sí puede abonar y comprar de contado
- [ ] El cajero no puede corregir la cédula de un cliente ni cerrarle el fiado
- [ ] En *Resultados*: `efectivo + transferencia + fiado = ventas netas`, y los abonos no suben las ventas
- [ ] Anular una venta fiada devuelve el stock y baja la deuda; no sale plata del cajón por la parte fiada
- [ ] Los turnos que ya estaban cerrados siguen cuadrando después de la migración
- [ ] `./mvnw clean test` en verde; lint, pruebas y build del frontend en verde; se ve bien a 390 px y en oscuro

---

## 9. Qué no se toca y fuera de alcance

**No se toca:** cómo se cobra de contado (poner un cliente es opcional); compras y gastos (su forma de pago no
cambia); el kardex y el costo promedio (fiar descuenta stock como cualquier venta).

**Fuera de alcance:**
- **Cupos** o topes de fiado: se quitaron por decisión del usuario (2026-09-21). Para frenar a alguien está
  cerrarle el fiado (H11).
- Recibir un abono **por fuera del cajón** (la opción *"fuera de caja"* del car‑wash).
- **Compras a crédito a proveedores** (cuentas por pagar): otro spec, cuando se consulte con el cliente.
- Intereses, recargos o plazos con fecha de vencimiento.
- Recordatorios por WhatsApp o SMS a quien debe.
- Crédito con validez fiscal o facturación electrónica.
- Que el dueño vea o cobre deudas desde la nube: llega con la rebanada 5, solo para mirar.

---

## 10. Riesgos

| Riesgo | Qué hacer |
|---|---|
| **Contar el abono como venta** infla la ganancia del período (el mismo peso entra dos veces: al fiarse y al abonarse). El car‑wash cuenta el cobro como ingreso del día (§3); **copiar eso aquí sería el error** | El reporte separa *vendido* de *cobrado*, y su igualdad nueva tiene prueba |
| **Reescribir la regla del cierre sobre turnos cerrados** (`V14:33-35`) | La parte nueva nace en $0 para los viejos; la migración se prueba sobre una base con turnos cerrados, como la V12–V14 |
| **Sin cupo, el único freno es la gente**: un cajero puede fiarle a un conocido | Nadie fía sin cédula y celular; cada venta fiada dice quién la fió; el administrador ve lo fiado del día y puede cerrarle el fiado a un cliente |
| **Repartir un abono entre ventas pierde un peso** si las partes no suman | Las partes aplicadas suman el abono al peso, con prueba (RF-012) |
| **Clientes repetidos** (*"Juan Pérez"* y *"Juan Perez"*): la deuda queda partida en dos | La cédula es única y la búsqueda no distingue tildes; al escribir una cédula existente se usa ese cliente |
| **Arrancar con los saldos del cuaderno en cero**: el primer día el sistema diría que nadie debe | RF-028, saldo del cuaderno por cliente, con fecha, motivo y auditado |
| **Las ventas de antes no tienen cliente**: el historial por cliente empieza el día que se instale esto | Se dice en la ficha del cliente; no se inventa a quién se le vendió antes |
| **Pedir cédula y celular es recoger datos personales** | Se usan solo para identificar y cobrar. El aviso de tratamiento de datos, si la tienda lo necesita, no es software |
