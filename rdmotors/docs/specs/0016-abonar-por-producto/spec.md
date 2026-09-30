# Spec 0016 · Abonar por producto

**Estado:** **implementado en local**, 2026-09-30 (rama `spec-0015-comisiones`) · [plan](plan.md) · sin desplegar:
nada sube a producción sin la orden del dueño

**Aclaración del usuario (2026-09-30), decisión 2:** *el cajero escoge qué pagó* al llevárselo, no se reparte en
proporción.

**Pedido del usuario (2026-09-30):** *«cuando se fía y se va a hacer un abono/pago, que se muestre la compra y los
productos, y poder abonar por productos y no solo por compra»*.

---

## 1. Objetivo de negocio

En el mostrador el cliente no habla de números de venta: dice *«le pago el aceite»* o *«le dejo el filtro pagado y el
aceite se lo debo»*. Hoy la app solo entiende ventas: al abonar se escribe un monto y se reparte a la venta más vieja,
o a una venta que se escoja de una lista que dice *"Venta N.º 10 · faltan $90.000"*, sin decir qué había en ella.

El dueño quiere que al abonar **se vea qué se llevó el cliente** y que **se pueda pagar un producto** (o varios), y que
después la ficha diga **qué productos están pagados y cuáles se deben**.

## 2. Historias

| | Historia | Se demuestra sola cuando… |
|---|---|---|
| **P1** | Como cajero, al abrir *Abonar* veo cada venta pendiente **con sus productos** y lo que falta de cada uno. | Juan debe la venta N.º 18: el modal muestra *MOTUL 7100 · faltan $…* y *el otro repuesto · faltan $…*. |
| **P1** | Como cajero, **marco los productos** que el cliente paga y el monto se llena solo. | Marco el MOTUL: el monto se pone en lo que falta del MOTUL; al recibirlo, el MOTUL queda *Pagado* y el otro sigue *Pendiente*. |
| **P1** | Como dueño, en la ficha del cliente veo por cada venta **qué producto está pagado, abonado o pendiente**, con sus abonos. | La venta N.º 18 dice *MOTUL 7100 · Pagado* y *el otro · Pendiente $…*. |
| **P2** | Como cajero, el recibo del abono dice **a qué producto fue** cada parte. | *"$62.000 · MOTUL 7100 10W30 (venta N.º 18)"*. |
| **P2** | Como cajero, si no marco nada, el abono se reparte como hoy: a lo más viejo. | Un abono de $20.000 sin marcar paga lo más viejo, producto por producto. |
| **P3** | Como dueño, los reportes dicen qué productos se cobraron de un fiado a medias. | *Fuera de esta versión* (ver decisión 5). |

## 3. Qué existe hoy

| Qué | Dónde | Hecho verificado |
|---|---|---|
| Una venta fiada crea **una sola deuda**, por lo fiado de toda la venta | `domain/…/clientes/aplicacion/FiarVenta.java:58-66` | `Deuda.porVenta(…, fiado, …)` |
| La base **no deja dos deudas para la misma venta** | `pos/…/db/migration/V20__clientes_y_fiado.sql:83` | `ux_deuda_venta`, índice único por `venta_id` |
| Un abono se reparte **de la deuda más vieja a la más nueva**, o primero a **una** deuda escogida | `domain/…/clientes/dominio/CarteraDelCliente.java:118-143` | `primeroA`: una sola; lo que sobra sigue a lo más viejo |
| El orden de pago es por fecha, luego por cuál se registró antes, luego por id | `CarteraDelCliente.java` (`ORDEN_DE_PAGO`) | Dos deudas del mismo instante quedarían en orden de id (al azar) |
| Anular una venta fiada busca **su** deuda (una) y la anula; lo abonado pasa a las otras | `FiarVenta.java:72-84` | `deudas.deLaVenta(ventaId)` devuelve una |
| El modal de abono: monto, forma, y *"A cuál se aplica"* con la lista de ventas | `frontend/src/componentes/clientes/ModalAbono.jsx` | Sin productos |
| La ficha ya muestra **qué se llevó** en cada venta fiada (hecho hoy, en la rama local) | commit `63df2a1` | Solo se muestra; no se paga por producto |
| Los reportes cuentan lo fiado **cuando se cobra**, repartido en proporción entre los renglones de la venta | `domain/…/reportes/dominio/LoCobrado.java` (spec 0014) | Leen los abonos **por venta** (`deuda.venta_id`) |
| En producción (2026-09-30, solo lectura): 11 ventas fiadas; **todas de un solo producto menos dos**: la N.º 6 (anulada, 4) y la N.º 18 (2 productos, **sin abonos**) | base de producción | 4 abonos, ninguno anulado; sin saldo de cuaderno |

## 4. Las decisiones

### Decisión 1 · Cómo se guarda "lo que se debe de cada producto" — **la que cambia el alcance**

| Opción | Qué es | Costo | Riesgo |
|---|---|---|---|
| **A. Una deuda por producto** (recomendada) | Al fiar, cada producto de la venta queda como **su propia deuda**, con la venta de referencia. Pagar un producto es abonarle a esa deuda. | Quitar el índice único; fiar crea varias; anular la venta anula todas; la ficha y el modal las agrupan por venta. | Bajo: **todas las reglas de hoy siguen iguales** (repartir, a lo más viejo, a favor, anular abono, anular venta). Solo cambia la unidad: de venta a producto. |
| B. Una deuda por venta, con un reparto por producto adentro | La deuda sigue siendo de la venta, y cada abono guarda además cuánto le tocó a cada producto. | Un reparto nuevo en cada abono, en cada anulación de abono, en cada anulación de venta, en lo que queda a favor… | Alto: son dos cuentas que se tienen que mover siempre juntas, y cualquier camino que se olvide las separa. |

**Recomendación: A.** El cuaderno de la tienda ya funciona así (*"le debe el aceite"*), y en la app reusa reglas que
tienen meses de pruebas en lugar de escribir otras. Lo que cambia se ve en la pantalla, no en las cuentas.

### Decisión 2 · Cuánto se debe de cada producto si hubo descuento o pagó una parte al llevárselo — **decidida**

**Lo que paga al llevárselo lo escoge el cajero** (aclaración del usuario). En *Cobrar › Fiado*, debajo de *Paga
ahora*, cada producto tiene *"lo paga ahora"*; marcarlo llena *Paga ahora* con su valor. Lo pagado cubre primero los
productos marcados, en su orden, y si sobra, los demás en el orden de la venta. **Sin marcar nada**, lo pagado cubre
los productos en el orden de la venta (no en proporción). Así las cifras quedan redondas: MOTUL $65.000 + filtro
$11.000, marca el filtro y paga $11.000 → el MOTUL queda debiendo $65.000.

**El descuento** sí se reparte **en proporción al valor de cada producto** (piso, y el peso que sobre al más caro): no
hay qué escoger, y es la misma regla del comprobante y de los reportes. Con descuento, lo que vale cada producto puede no
ser redondo.

- Sin descuento ni pago al llevárselo (casi siempre, en producción): cada producto debe **su precio × cantidad**.
- Un producto que quede en $0 (lo cubrió todo lo pagado) no queda como deuda.

### Decisión 3 · A qué se va un abono sin marcar productos

Como hoy, **a lo más viejo**; y dentro de una misma venta, **en el orden de sus productos** (el del comprobante). Así,
$20.000 sin marcar pagan primero el primer producto de la venta más vieja.

La alternativa (repartirlo en proporción entre los productos de la venta) deja todos a medias y es más difícil de
explicar al cliente. **Recomendación: en orden.**

### Decisión 4 · Los fiados que ya existen

Al desplegar, cada deuda de una venta de **un solo producto** pasa a ser la deuda de ese producto, sin cambiar un peso.
Una de **varios productos sin abonos** se parte con la regla de la decisión 2. Una de **varios productos que ya tiene
abonos** se queda como está, *por venta*: no se sabe qué producto pagaron esos abonos, y se abona por monto.

En producción hoy eso es: todas pasan a producto; la N.º 18 se parte en dos; ninguna queda *por venta*. Se ensaya
sobre una copia de producción antes de desplegar, como la V30.

### Decisión 5 · Los reportes no cambian en esta versión

Las ventas netas, el costo y la utilidad de cada día **quedan igual**: siguen contando lo cobrado de cada venta y
repartiéndolo en proporción entre sus renglones. Pagar un producto no cambia la plata que entró ese día; solo cambiaría
*cuál* costo se cuenta primero en una venta fiada a medias. Hacerlo exacto por producto es la **P3**, para después.

## 5. Requisitos funcionales

- **RF-001** Al fiar, cada producto de la venta queda como su propia deuda, con cuánto debe (decisión 2). La venta, su
  comprobante y lo que dice *"quedó debiendo"* no cambian.
- **RF-014** En *Cobrar › Fiado*, el cajero puede marcar qué productos paga ahora; *Paga ahora* se llena con su valor
  (con el descuento ya repartido) y se puede cambiar. Lo pagado cubre lo marcado primero (decisión 2).
- **RF-002** La ficha del cliente agrupa por venta: la venta (número, fecha, lo fiado en total, su estado) y debajo
  cada producto con lo que debe, lo abonado, lo pendiente, su estado y los abonos que le aplicaron.
- **RF-003** El modal de abono muestra las ventas con algo pendiente, cada una con sus productos y lo que falta de cada
  uno. Cada producto tiene su casilla, y cada venta una para marcar todos sus productos.
- **RF-004** Al marcar productos, *Cuánto abona* se llena con lo que falta de todos los marcados. Se puede cambiar.
- **RF-005** El abono paga primero los productos marcados, en el orden de la lista. Si es menos, el último que alcanza
  queda abonado a medias. Si es más, lo que sobra va a lo más viejo (decisión 3).
- **RF-006** Sin marcar nada, el abono va a lo más viejo, producto por producto (decisión 3).
- **RF-007** El recibo del abono dice a qué producto fue cada parte, con su venta.
- **RF-008** Anular una venta fiada anula todos sus productos; lo abonado a ellos pasa a las otras deudas o queda a
  favor, como hoy.
- **RF-009** Anular un abono devuelve lo pagado a cada producto, como hoy.
- **RF-010** La lista de la Cartera sigue contando **ventas** pendientes, no productos.
- **RF-011** El saldo del cuaderno sigue sin productos: se abona por monto.
- **RF-012** Los fiados que ya existen pasan a producto según la decisión 4.
- **RF-013** Los reportes dan las mismas cifras que antes (decisión 5).

## 6. Manejo de errores

| Caso | Qué pasa |
|---|---|
| Se marca un producto ya pagado o de una venta anulada (una pantalla vieja) | No se recibe: *"El MOTUL 7100 de la venta N.º 18 ya está pagado"*. |
| El monto es mayor que lo que debe | Como hoy: *"Juan debe $…: no se le puede recibir más"*. |
| El monto es $0 | Como hoy: no se recibe. |
| Dos cajeros le abonan a la vez al mismo cliente | Como hoy: el cliente se bloquea y los abonos van en fila. |
| El abono se manda dos veces (doble clic o corte de red) | Como hoy: con la misma llave es el mismo abono. |
| Se marca un producto de otro cliente | No se recibe: *"Ese producto no es una deuda de Juan"*. |
| En el cobro se marca como pagado un producto que no está en la venta, o se marca sin fiar | No se cobra: *"Lo que se paga ahora tiene que ser de esta venta"* / *"Solo se escoge qué se paga cuando se fía"*. |

## 7. Requisitos no funcionales

- **Quién:** abonan el cajero y el administrador, como hoy. Nada nuevo es solo del administrador.
- **Auditoría:** nada nuevo; el abono y su anulación ya quedan con quién y cuándo.
- **Plata:** en pesos enteros; las partes de cada venta suman lo fiado, y las de cada abono suman el abono, al peso.
- **Celular:** el modal con productos tiene que caber a 390 px y marcarse con el dedo.
- **Despliegue:** la migración cambia deudas que ya existen: se ensaya en una copia de producción y se comparan
  reportes, cartera y fichas antes y después.

## 8. Criterios de aceptación

- [ ] Fiar una venta de dos productos crea dos deudas que suman lo fiado, al peso, aun con descuento y pago parcial.
- [ ] En el cobro fiado, marcar el filtro como pagado deja el MOTUL debiendo su precio exacto.
- [ ] El modal de abono muestra los productos pendientes con lo que falta de cada uno.
- [ ] Marcar un producto llena el monto con lo que falta de él; al recibirlo, ese producto queda *Pagado* y el otro no.
- [ ] Un abono sin marcar paga lo más viejo, en el orden de los productos de la venta.
- [ ] Anular la venta anula sus productos y lo abonado pasa a las otras deudas o queda a favor.
- [ ] Anular el abono devuelve lo pagado a cada producto.
- [ ] El recibo dice a qué producto fue cada parte.
- [ ] La lista de la Cartera cuenta ventas pendientes, no productos.
- [ ] En una copia de producción, después de migrar: las fichas, la cartera y los reportes dan las mismas cifras; la
      venta N.º 18 queda en dos productos.

## 9. Qué no se toca

El cobro en el mostrador (salvo que la venta fiada crea una deuda por producto), el comprobante de la venta, el arqueo
y el cierre, los reportes (decisión 5), la comisión por cambio de aceite (spec 0015).

## 10. Fuera de alcance

- Reportes exactos por producto cobrado (P3, decisión 5).
- Devolver un solo producto de una venta: hoy se anula la venta entera.
- Cambiar a qué producto fue un abono ya hecho: se anula el abono y se hace otra vez.
