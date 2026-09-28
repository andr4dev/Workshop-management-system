# Spec 0013 — El envío de una compra, aparte del costo de los repuestos

---

## 1. Objetivo de negocio

La factura **WE-10238** de Comercializadora San José (7-sep-2026) trae 60 aceites KIXX a $23.690 cada uno con IVA, y
además cobra **$40.000 de envío**: $1.461.400 en total. El usuario decidió el 2026-09-28 que el envío **no se reparte
en el costo** de cada aceite: se registra aparte, como gasto, y se ve **en la lista de gastos con el enlace a la
factura**.

Así el costo por unidad queda igual al de la factura —se puede comparar entre proveedores sin hacer cuentas— y lo que
se pagó por traer la mercancía queda a la vista, junto a la compra a la que pertenece.

Hoy no hay cómo hacerlo. La compra no tiene dónde poner el envío, y un gasto se puede registrar, pero suelto: nadie
sabe después de qué compra era. La WE-10238 ya está registrada, sin el envío.

---

## 2. Casos de uso

| # | Historia | Prioridad | Qué se puede demostrar solo |
|---|---|---|---|
| **H1** | Al registrar una compra escribo el envío. La compra queda por los repuestos y el envío queda como un gasto enlazado a ella | **P1** | Compra por $1.421.400 con los aceites a $23.690, y un gasto de $40.000 que dice de qué factura es |
| **H2** | En *Reportes › Gastos* el envío dice de qué compra es, y con un clic la abro | **P1** | El gasto de $40.000 dice *"Envío · WE-10238 · Comercializadora San José"* y el enlace abre esa compra |
| **H3** | A una compra ya registrada le agrego, cambio o quito el envío | **P1** | A la WE-10238, que hoy no lo tiene, se le agregan los $40.000 |
| **H4** | El detalle de la compra muestra el envío y lo que se pagó en total | **P1** | *"Envío $40.000 · Pagado en total $1.461.400"* |
| **H5** | Anular una compra anula su envío | **P2** | Se anula la compra y el gasto del envío deja de sumar |
| **H6** | En la pre-carga de una factura (spec 0012) el envío va con los datos de la compra | **P2** | Se sube una factura, se escribe el envío, se confirma, y quedan la compra y su envío |
| **H7** | Los totales del historial de compras por cuenta muestran los envíos, para cuadrar con el extracto | **P3** | *"Nequi Ruben: compras $1.421.400 + envíos $40.000"* |

---

## 3. Qué existe hoy

Verificado en el código y en producción el 2026-09-28.

### Lo que ya resuelve casi todo

| Hecho | Dónde |
|---|---|
| Un gasto puede ser **por fuera del cajón**, pagado en efectivo o por transferencia desde una cuenta, con su propia fecha | `pos/…/db/migration/V12__gastos.sql:44-70`; `domain/…/caja/dominio/Gasto.java:133` |
| Existe la naturaleza **COSTO**, definida justo para esto: *"lo que se le suma a la mercancía por fuera de la factura. Resta de la utilidad bruta"* | `domain/…/caja/dominio/NaturalezaGasto.java:9-10` |
| Los reportes ya restan esos costos de la utilidad bruta, como *costos adicionales* | `domain/…/reportes/dominio/ResultadosDelPeriodo.java:145-149` |
| Un gasto se anula con motivo y queda auditado | `Gasto.java:199`; `V12__gastos.sql` (acción `ANULAR_GASTO`) |
| Corregir y anular una compra ya existen, con auditoría | `domain/…/compras/aplicacion/CorregirCompra.java:89`; `AnularCompra.java:55` |
| Registrar una compra dos veces con la misma llave devuelve la primera | `domain/…/compras/aplicacion/RegistrarCompra.java:76-79` |

### Lo que condiciona el diseño

| Hecho | Dónde | Consecuencia |
|---|---|---|
| **El gasto no apunta a nada**: no tiene compra | `V12__gastos.sql:44-70` | El enlace es lo nuevo. Hace falta esquema |
| El total de la compra **es la suma de sus renglones** | `domain/…/compras/dominio/Compra.java:204` y `:446` | El envío no puede ir en el total sin repartirse en algún renglón, que es justo lo que no se quiere |
| Un gasto **no se edita**: se anula y se registra otro | `Gasto.java:57-83` (todo `updatable = false`) | Cambiar el envío de una compra es anular el gasto viejo y crear uno nuevo |
| La fecha de un gasto es *"el día en que se pagó"*, y no puede ser después de hoy | `Gasto.java:131` y `:151` | Ver RF-002 sobre qué fecha lleva el envío |
| La categoría sembrada **"Transporte y fletes" es GASTO**, no COSTO | `V12__gastos.sql:33` | Si el envío cae ahí, resta de la operativa junto al arriendo. Ver decisión 2 |
| En producción hay 11 categorías, **todas GASTO**, y **0 gastos** registrados | consulta de solo lectura, 2026-09-28 | Agregar una categoría nueva no mueve nada de lo que ya hay |
| Los totales del historial de compras por forma de pago y cuenta suman **solo compras** | `pos/…/compras/infraestructura/RepositorioComprasJpa.java:131` | Sin H7, el envío no aparece al cuadrar una cuenta desde Compras |
| La lista de gastos muestra: fecha, categoría, en qué, de dónde salió, monto | `frontend/src/paginas/Gastos.jsx:175-182` | El enlace a la compra va en *"En qué"* |

---

## 4. Las decisiones

### Decisión 1 — ¿Dónde vive el envío? · la que cambia el alcance

| | Costo | Riesgo |
|---|---|---|
| **A. Un gasto enlazado a la compra** | Una columna nueva en el gasto (de qué compra es), registrarlo desde la compra, y el enlace en la lista. Los reportes, anular, las cuentas y la auditoría ya existen | Ninguno nuevo: es un gasto como los demás |
| **B. Un campo en la compra, que la lista de gastos lee de las compras** | Campo nuevo en la compra, y enseñarles a los reportes y a la lista de gastos a leer dos sitios | Dos listas de gastos que hay que juntar: el día que un reporte olvide la segunda, la utilidad miente sin avisar |

**Recomendación: A.** Es exactamente lo que el usuario pidió —*"que se vea reflejado en los gastos"*— y el gasto ya
sabe todo lo demás.

### Decisión 2 — ¿Costo o gasto? · la que mueve plata en los reportes

| | Dónde resta | Qué se ve |
|---|---|---|
| **COSTO** | En la **utilidad bruta**, como *costos adicionales* | Lo que de verdad costó la mercancía que se vende |
| GASTO (como "Transporte y fletes" hoy) | En la **operativa**, junto al arriendo y la luz | Una utilidad bruta más alta de lo que es |

**Recomendación: COSTO**, con una categoría sembrada nueva, **"Envío de mercancía"**, que usan todos los envíos de
compras sin preguntarle a nadie. Es la definición que el sistema ya tiene para ese tipo de costo.

> ✅ **Decidido el 2026-09-28: COSTO.** Palabras del usuario: *"métalo ahora como costo"*.

> **La consecuencia de no repartirlo, dicha antes de construirlo:** el envío pesa **en el mes en que se paga**, no
> cuando se vende la mercancía. Si en septiembre llegan 60 aceites con $40.000 de envío y se venden en octubre,
> septiembre carga los $40.000 y octubre no. Con envíos de este tamaño no cambia la lectura de un mes; con un flete
> grande sí.

### Decisión 3 — Anular la compra, ¿anula su envío?

**Recomendación: sí, junto, con el mismo motivo.** En este sistema anular una compra es decir *"esto no debió
registrarse así"*: se vuelve a registrar bien, y lleva su envío.

`[NECESITA ACLARACIÓN: si algún día se devuelve la mercancía al proveedor, el envío se pagó igual. ¿Ese caso existe en
la tienda? Si existe, al anular se pregunta "¿el envío también?" en vez de anularlo siempre.]`

### Lo que ya se hizo a mano, antes de construir esto

El 2026-09-28, a pedido del usuario, el envío de la WE-10238 se registró en producción **como un gasto normal**,
porque la función todavía no existía:

- Se creó la categoría **"Envío de mercancía"**, naturaleza COSTO, desde *Categorías de gasto*. **Ya existe en
  producción**: la migración no puede volver a sembrarla, porque el nombre es único. Tiene que usarla si ya está y
  crearla solo si no.
- Gasto de **$40.000**, fecha 7-sep-2026, por fuera del cajón, transferencia desde Nequi Ruben. La descripción lleva
  la referencia completa a la compra: *"Envío de la factura WE-10238 · Comercializadora San José · 60 aceites KIXX ·
  ver Compras › Historial › WE-10238 [compra 728b3d46-7fed-4a75-943d-94f448b9c29b]"*.
- **Al construir esta función, ese gasto se enlaza a su compra**: la migración reconoce `[compra <id>]` en la
  descripción. No se crea otro envío para la WE-10238. El criterio de aceptación de "agregarle el envío" pasa a ser
  *"el envío que ya existe queda enlazado"*.

---

## 5. Requisitos funcionales

**Registrar**

- **RF-001** — *Registrar compra* tiene un campo **Envío**, opcional, en pesos. Arranca con la forma de pago y la
  cuenta de la compra, y se pueden cambiar: el transportador se le puede pagar distinto que al proveedor.
- **RF-002** — El envío **no entra** en el costo de ningún repuesto ni en el total de la compra. Crea un gasto por
  fuera del cajón con:
  - categoría **"Envío de mercancía"** (COSTO);
  - el monto;
  - la descripción *"Envío de la factura WE-10238 · Comercializadora San José"* (sin número: *"Envío de la compra
    del 7 sep. · …"*);
  - la **fecha de la factura**;
  - el enlace a la compra.

  Se guarda **en el mismo movimiento que la compra**: quedan las dos o ninguna.
- **RF-003** — Una compra tiene **a lo sumo un envío vigente**.
- **RF-004** — Reintentar el registro (la misma llave, un doble clic, un corte de red) **no crea otro envío**.

**Ver**

- **RF-005** — El detalle de la compra muestra *"Envío $40.000"* con enlace al gasto, y *"Pagado en total
  $1.461.400"* = total de la compra + envío. La comprobación de siempre —la suma de los renglones contra el total de
  la compra— no cambia: el envío no es un renglón.
- **RF-006** — *Reportes › Gastos* muestra en el envío *"WE-10238 · Comercializadora San José"*, y el enlace abre la
  compra.

**Cambiar**

- **RF-007** — *Corregir compra* deja agregar, cambiar o quitar el envío. Cambiarlo es anular el gasto viejo con el
  motivo de la corrección y crear otro. Queda en la auditoría de la compra, en el antes y el después.
- **RF-008** — Anular la compra anula su envío, con el mismo motivo (decisión 3).
- **RF-009** — El gasto de un envío **no se anula suelto** desde *Gastos*. Se dice *"Es el envío de la compra
  WE-10238: se cambia desde la compra"*, con el enlace. Así una compra nunca queda diciendo que tiene un envío que
  ya no existe.
- **RF-010** — La categoría "Envío de mercancía" es del sistema: no se desactiva ni cambia de naturaleza.

**P2 y P3**

- **RF-011** *(P2)* — La pre-carga de una factura (spec 0012) tiene el campo **Envío** en *La compra*. Al
  confirmar, se crea igual que en RF-002.
- **RF-012** *(P3)* — Los totales del historial de compras por forma de pago y cuenta muestran los envíos aparte:
  *"compras $1.421.400 + envíos $40.000"*.

---

## 6. Manejo de errores

| Qué pasa | Qué hace el sistema | Qué ve la persona |
|---|---|---|
| El envío es cero, negativo o no se entiende | No registra nada | *"El envío tiene que ser mayor que cero"*, junto al campo |
| Envío por transferencia sin cuenta | No registra nada | *"Una transferencia necesita la cuenta desde la que salió"*, el mismo de la compra |
| Doble clic al registrar | Una compra, un envío | Nada raro: la segunda vez vuelve la misma compra |
| Anular desde *Gastos* el envío de una compra | No lo anula | *"Es el envío de la compra WE-10238: se cambia desde la compra"*, con el enlace |
| Corregir el envío de una compra anulada | No lo deja | El mensaje de siempre: la compra ya fue anulada |
| Alguien desactiva la categoría "Envío de mercancía" | No lo deja | *"Esta categoría la usan los envíos de las compras: no se desactiva"* |

---

## 7. Requisitos no funcionales

- **Plata**: todo en pesos enteros. **Las partes suman el total**: *pagado en total* = total de la compra + envío,
  al peso.
- **Esquema**: una migración nueva. El gasto gana *"de qué compra es"* (vacío para los que ya existen) y se siembra
  la categoría. Funciona sobre una base con filas: los gastos que ya hay quedan sin compra, que es la verdad.
  Producción tiene 0 gastos; QA puede tener algunos.
- **Quién**: todo es del administrador, como las compras y los gastos por fuera. El cajero no ve compras ni el costo
  de nada.
- **Auditoría**: corregir y anular la compra ya quedan auditados. El envío entra en su antes y después, y anular su
  gasto queda como `ANULAR_GASTO` con el motivo.
- **Sin internet**: no aplica. El sistema vive en la nube desde el spec 0011.

---

## 8. Criterios de aceptación

- [ ] A la WE-10238 se le agrega el envío de $40.000. La compra **sigue en $1.421.400** y los dos aceites **en
      $23.690** cada uno.
- [ ] Aparece un gasto de $40.000, categoría "Envío de mercancía" (COSTO), con fecha 7-sep-2026, por transferencia
      desde Nequi Ruben, que dice *"WE-10238 · Comercializadora San José"* y abre esa compra.
- [ ] El detalle de la WE-10238 dice *"Envío $40.000 · Pagado en total $1.461.400"*.
- [ ] *Reportes › Resultados* de septiembre: los costos adicionales suben $40.000 y la utilidad bruta baja $40.000.
- [ ] Registrar una compra con envío con doble clic deja una compra y un envío.
- [ ] Cambiar el envío a $35.000 deja el gasto de $40.000 anulado, uno nuevo de $35.000, y la auditoría de la compra
      con los dos valores.
- [ ] Anular la compra deja su envío anulado; ya no suma en gastos ni en resultados.
- [ ] Desde *Gastos*, el envío de una compra no se anula suelto.
- [ ] Una compra sin envío se registra exactamente como hoy.
- [ ] El cajero no ve nada de esto.

---

## 9. Qué no se toca

- El **costo promedio**, el **kardex** y el **total de la compra**: el envío no pasa por ninguno.
- La lectura del PDF de Jotapartes (spec 0012).
- Los gastos que ya existen.

## 10. Fuera de alcance

- **Repartir el envío en el costo** de los repuestos: descartado por el usuario el 2026-09-28.
- **Envío pagado con billetes del cajón.** Hoy se registra como gasto del cajón, suelto. Enlazarlo a la compra toca
  el arqueo del turno, y es otro spec si hace falta.
- **Varios cargos por compra** (envío, seguro, manejo): uno solo, el envío.
- **Compras a crédito.** La WE-10238 es a crédito 30 días y se registró como transferencia. Sigue fuera, como se
  anotó en el spec 0002.
- La MAG477: `[NECESITA ACLARACIÓN: la factura dice "Transportado: ENVIA, guía 954101523494". ¿Se le pagó flete a
  ENVIA al recibirla? Si sí, se le agrega con esta función (H3), igual que a la WE-10238.]`
