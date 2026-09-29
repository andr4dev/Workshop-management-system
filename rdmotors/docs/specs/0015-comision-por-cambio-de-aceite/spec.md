# Spec 0015 · La comisión por cambio de aceite

**Estado:** el usuario pidió el plan el 2026-09-29 sin cambios: se toman las recomendaciones · [plan](plan.md) · en implementación

**Pedido del usuario (2026-09-29):** *«cada venta de aceite son 3000 pesos que se deben sacar aparte para el que
cambió el aceite, es decir el aceite cuesta 64000, de esos 64000 salen 3000 para el que cambió el aceite o atendió,
y debe quedar el registro»*.

---

## 1. Objetivo de negocio

Cuando se vende un aceite y se cambia en la tienda, **$3.000 son de quien hizo el cambio**. Hoy eso se hace de
memoria: nadie sabe cuánto se le debe a quién, si ya se le pagó, ni cuánto de la ganancia del aceite se va en eso. El
dueño quiere que cada cambio **quede registrado**, a nombre de la persona, y que se vea qué está pagado y qué no.

El precio para el cliente no cambia: el aceite sigue costando $64.000. Lo que cambia es cuánto le queda a la tienda:
$64.000 − su costo − $3.000.

## 2. Historias

| | Historia | Se demuestra sola cuando… |
|---|---|---|
| **P1** | Como cajero, al cobrar una venta con aceite queda registrado **quién hizo el cambio** y los $3.000 que le tocan, sin pasos de más. | Se cobra un MOTUL 7100 y en el registro aparece: venta N.º, aceite, Gustavo, $3.000, *por pagar*. |
| **P1** | Como dueño, veo **cuánto se le debe a cada uno** y le pago; el pago queda registrado y sale del cajón o por fuera. | Gustavo tiene $9.000 por pagar de tres cambios; se le pagan, y quedan *pagados* con la fecha y quién pagó. |
| **P1** | Como dueño, los reportes descuentan los $3.000 de la ganancia del aceite. | La utilidad del día baja $3.000 por cada cambio, y *Ver cálculo* dice que es por comisiones. |
| **P2** | Como dueño, escojo qué repuestos pagan comisión y cuánto. | Se marca el KIXX 10W40 con $3.000; el lubricante de cadena no se marca y no paga nada. |
| **P2** | Como dueño, si se anula una venta con aceite, su comisión también se anula; si ya se había pagado, se descuenta del próximo pago. | Se anula la venta 5: la comisión de Ruben sale de *por pagar*. |
| **P3** | Como cajero, veo lo que me deben a mí. | Gustavo entra y ve sus cambios del mes y cuánto le falta por cobrar. |

## 3. Qué existe hoy

| Qué | Dónde | Hecho verificado |
|---|---|---|
| La venta guarda **quién la registró**, no quién hizo el trabajo | `domain/…/ventas/dominio/Venta.java:66` | Un solo "quién" por venta: el que cobró |
| Los renglones de la venta | `domain/…/ventas/dominio/Venta.java:129` | Repuesto, cantidad, precio |
| El repuesto no tiene nada que diga "esto es un aceite" | `domain/…/inventario/dominio/Producto.java:37-42` | Nombre y categoría |
| **El aceite no está en una sola categoría** | producción, 2026-09-29 | KIXX 10W40 y 20W50 en *LUBRICANTES Y QUIMICOS*; MOTUL 5100 y 7100 en *MOTOR*; en *LUBRICANTES* también está el lubricante de cadena, que no es un cambio |
| **Hay ventas de dos aceites juntos** | producción: ventas 8 y 10 | 2 × MOTUL 5100 cada una |
| Las personas que atienden | producción: tabla de usuarios | Ruben y Deibis (administradores), Gustavo (cajero). Hasta hoy todas las ventas las registró Ruben |
| Lo que sale del cajón resta de lo que debería haber al cerrar | `domain/…/caja/dominio/ArqueoDeTurno.java:22, 98-99` | Los gastos del cajón y los retiros |
| Un gasto puede ser **costo** (resta de la utilidad bruta) o **gasto** (de la operativa) | `domain/…/caja/dominio/NaturalezaGasto.java:10-13` | El envío de la WE-10238 es costo |
| Lo fiado cuenta en los reportes cuando se cobra, con su parte del costo | spec 0014 (`reportes/dominio/LoCobrado.java`) | Si el aceite se fió, su plata y su costo entran al abonarse |
| El cobro ya pregunta cosas cuando hacen falta (cliente al fiar) sin frenar la venta | `frontend/src/componentes/venta/ModalCobro.jsx` | Modelo a seguir para preguntar quién hizo el cambio |

## 4. Las decisiones

### Decisión 1 · ¿Qué es "un aceite"? — la que cambia el alcance

Por categoría no sirve: los aceites están en dos categorías, y en una de ellas hay cosas que no son cambios.

**Recomendación: una marca en cada repuesto, *"paga comisión de cambio: $3.000"*,** que el administrador pone en la
ficha del repuesto. El monto es por repuesto (el día que un aceite sintético pague $4.000, se cambia ahí). Al
arrancar se marcan los cuatro de hoy: MOTUL 5100, MOTUL 7100, KIXX 10W40 y KIXX 20W50.

### Decisión 2 · ¿Se paga por venta o por unidad?

Las ventas 8 y 10 llevaron 2 aceites cada una. ¿Son $3.000 o $6.000?

**Recomendación: por unidad**, porque en una moto un aceite es un cambio: dos aceites suelen ser dos motos, o una que
lleva más. En el cobro se puede corregir cuántos cambios fueron.
[NECESITA ACLARACIÓN: ¿una moto que lleva dos botellas paga uno o dos cambios?]

### Decisión 3 · ¿Y si el cliente se lleva el aceite y no se lo cambian aquí?

*"Para el que cambió el aceite"*: si nadie lo cambió, no hay a quién pagarle.

**Recomendación: en el cobro, cada aceite viene marcado *"se cambió aquí"*,** y se desmarca si se lo lleva. Es
un toque, solo cuando hay aceite, y no frena la venta.

### Decisión 4 · ¿A quién le toca?

La venta guarda quién la registró. Pero el que registra puede no ser el que cambió el aceite (Ruben cobra, Gustavo
cambia).

**Recomendación: en el cobro, *"¿Quién hizo el cambio?"*, con el que registra escogido de entrada,** y se puede
escoger a otra persona de la tienda. [NECESITA ACLARACIÓN: ¿hay quien cambie aceite y no tenga usuario en el
sistema? Si lo hay, hace falta una lista de trabajadores aparte de los usuarios.]
Solo hay 3 usuarios son los unicos 3 que trabajan 

### Decisión 5 · ¿Cuándo se le paga, y de dónde sale la plata?

| Opción | Qué pasa |
|---|---|
| **A. Queda *por pagar* y se paga cuando el dueño decida** | Cada cambio queda como deuda con la persona. En *Comisiones* se ve cuánto se le debe a cada uno, y *Pagar* registra la salida: del cajón del turno abierto (resta del esperado, como un gasto del cajón) o por fuera. Se puede pagar al cierre, cada semana, o con la nómina. |
| B. Sale del cajón en el momento | Cada cobro con aceite saca $3.000 del cajón automáticamente. El arqueo lo descuenta solo, pero los $3.000 tienen que salir físicamente del cajón en ese momento, venta por venta; y un aceite fiado sacaría plata que no entró. |

**Recomendación: A.** Deja el registro de quién debe cobrar qué, no mueve el cajón a cada venta, y sirve igual si
se paga a diario o cada semana. Pagar desde el cajón sigue siendo un toque.

### Decisión 6 · ¿Cómo cuenta en los reportes?

Es plata que se va por la venta del aceite, como su costo.

**Recomendación: como costo de la venta** (resta de la utilidad bruta) y **con la misma regla del spec 0014**: si el
aceite se fió, su comisión entra al reporte en la parte que se cobra. En *Ver cálculo* aparece como *Comisiones por
cambio de aceite*, con cada una. Pagarla después no vuelve a restar: el costo ya estaba.

## 5. Requisitos funcionales

**Qué paga comisión (P2)**
- **RF-001** · Un repuesto puede estar marcado *paga comisión de cambio*, con su monto en pesos. Lo marca el
  administrador en la ficha del repuesto. Cambiar el monto no cambia las comisiones que ya se registraron.

**Al cobrar (P1)**
- **RF-002** · Si la venta lleva algún repuesto marcado, el cobro muestra cada uno con *se cambió aquí* (marcado) y
  *¿quién hizo el cambio?* (el que registra, de entrada). Sin aceite, el cobro es como hoy.
- **RF-003** · Al cobrar, cada unidad marcada *se cambió aquí* deja una comisión: la venta, el repuesto, la persona, el
  monto y la fecha. Queda *por pagar*.
- **RF-004** · La comisión no cambia el precio ni el total de la venta, ni lo que se cobra al cliente.

**Pagar (P1)**
- **RF-005** · *Comisiones* muestra, por persona, lo por pagar y lo pagado del período, con cada cambio (fecha, venta,
  aceite, monto).
- **RF-006** · *Pagar* a una persona paga todo lo que tiene por pagar (o lo que se escoja): sale del cajón del turno
  abierto o por fuera (efectivo o transferencia desde una cuenta), con quién pagó y cuándo. Si sale del cajón, resta
  del esperado al cerrar.
- **RF-007** · Un pago no se edita: se anula con motivo, y lo que pagaba vuelve a *por pagar*.

**Anular (P2)**
- **RF-008** · Al anular una venta, sus comisiones se anulan. Si alguna ya se había pagado, queda como saldo a favor de
  la tienda con esa persona y se descuenta del próximo pago.

**Reportes (P1)**
- **RF-009** · En *Resultados*, las comisiones son costo de la venta: restan de la utilidad bruta, en la parte cobrada
  de su venta (spec 0014), y aparecen en *Ver cálculo* como *Comisiones por cambio de aceite*, cada una con su venta.
- **RF-010** · Pagarlas no vuelve a restar: el pago mueve plata, no ganancia.

**Lo del cajero (P3)**
- **RF-011** · El cajero ve sus propias comisiones: por pagar y pagadas. No ve las de los demás.

## 6. Manejo de errores

- Una venta con aceite y *se cambió aquí* sin persona → no puede pasar: la persona viene escogida de entrada.
- Pagar desde el cajón sin turno abierto → *"No hay turno abierto: págalo por fuera, o abre el turno"*.
- Pagar desde el cajón más de lo que debería haber → se pide confirmar, como un gasto del cajón.
- Pagar dos veces lo mismo (doble clic, reintento) → una llave por pago, como los gastos: queda uno.
- Cambiar el monto de un repuesto con comisiones por pagar → las que ya están no cambian; se avisa.

## 7. No funcionales

- **Plata:** pesos enteros. Por persona, *lo registrado − lo anulado = por pagar + pagado*, con su prueba.
- **Roles:** marcar repuestos y pagar es del administrador; el cajero registra (al cobrar) y ve lo suyo.
- **Auditoría:** anular un pago y cambiar el monto de un repuesto quedan con quién y por qué.
- **Esquema:** una migración nueva (la marca y el monto en el repuesto; la comisión; el pago).
- **Caja:** un pago desde el cajón entra al arqueo como una salida más, con su línea propia en el cierre.

## 8. Criterios de aceptación

- [ ] Con MOTUL 7100 marcado en $3.000, cobrar uno con Gustavo como quien hizo el cambio deja una comisión de $3.000
      por pagar a Gustavo; el total de la venta sigue en $65.000.
- [ ] Cobrar 2 × MOTUL 5100 deja $6.000 (dos comisiones); desmarcar uno *se cambió aquí* deja $3.000.
- [ ] El lubricante de cadena, sin marca, no deja comisión.
- [ ] En *Comisiones*, Gustavo con tres cambios debe $9.000; pagarle desde el cajón baja el esperado en $9.000 y deja
      los tres pagados.
- [ ] Anular la venta de un cambio ya pagado deja $3.000 a favor de la tienda, que se descuentan del próximo pago.
- [ ] La utilidad bruta del día baja $3.000 por cada cambio de un aceite de contado; uno fiado, en la parte cobrada.

## 9. Qué no se toca

- El precio y el comprobante que ve el cliente.
- La nómina: la comisión es aparte (si se paga junto con ella, son dos registros).

## Fuera de alcance

- Comisiones por otros servicios (cambio de llanta, mano de obra general). Si el dueño las quiere, la marca del
  repuesto (decisión 1) sirve igual para cualquier repuesto.
- Porcentajes: la comisión es un monto fijo por unidad.
- Comisiones de ventas pasadas: arranca el día que se construya. [NECESITA ACLARACIÓN: ¿se cargan las 6 ventas de
  aceite que ya hay (del 26 y 28 de septiembre)?]
