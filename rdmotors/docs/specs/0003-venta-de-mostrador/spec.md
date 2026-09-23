# Spec 0003 — Venta de mostrador y comprobante

**Estado:** cerrado · [plan](plan.md) aprobado el 2026-09-14, que agregó RF-031 a RF-033 · sin valor
fiscal · dos datos del cliente pendientes que no bloquean (§12)
**Rebanada:** 2 · ver [`PLAN_DE_TRABAJO.md`](../../PLAN_DE_TRABAJO.md)
**Depende de:** [spec 0001](../0001-catalogo-y-compras-usables/spec.md) y
[spec 0002](../0002-compras-fuente-de-pago-e-historial/spec.md), implementados y verificados

> **Convención:** lo que dice *"hoy"* está verificado con `archivo:línea`. Lo que dice
> *"debe"* es propuesta y todavía no existe. Las dos cosas nunca se mezclan.

---

## 1. Objetivo de negocio

Hoy el sistema compra pero no vende. El stock solo sube, no hay forma de saber cuánto se vendió ni
cuánto se ganó, y la caja no existe.

El cliente describió la venta así (`SPEC_Sistema_Ventas_Repuestos (3).md:34-40`): *entra el
cliente, pregunta por el repuesto, se busca sin escanear, se revisa si hay y el precio, se paga de
inmediato y el stock baja.* Y agregó que **el cajero responde por su turno**.

Al terminar este spec:

- se vende desde el mostrador buscando por código o por nombre;
- el stock baja con su movimiento en el kardex;
- sale el comprobante impreso con su número;
- cada venta queda dentro de un turno de caja;
- una venta mal hecha se anula dejando rastro de quién, cuándo y por qué.

---

## 2. Caso de uso

### P1 — bloquean

**H1 · Abrir el turno.**
El cajero abre su turno con el fondo con que arranca el cajón, por ejemplo $100.000. Sin turno
abierto no se vende.
*Se demuestra solo:* se abre el turno con $100.000 y la pantalla de venta queda habilitada; sin
abrirlo, la pantalla lo pide.

**H2 · Vender.**
Se escribe el código (`352B59K`) y el repuesto entra a la venta. O se escribe *filtro inoki* y se
elige de la lista. Se ajusta la cantidad, se ve el precio y cuántas hay, y se cobra.
*Se demuestra solo:* se venden dos repuestos, el stock de cada uno baja y su kardex muestra la
venta.

**H3 · Cobrar como pague el cliente.**
En **efectivo**: se escribe con cuánto paga y se muestra el cambio. Por **transferencia** (incluye
el QR): se marca así, sin decir a qué cuenta. **Mixto**: una parte de cada uno.
*Se demuestra solo:* una venta de $38.000 pagada con un billete de $50.000 muestra $12.000 de
cambio. Otra se paga $20.000 en efectivo y $18.000 por transferencia.

**H4 · El comprobante.**
Al cobrar, la ticketera del mostrador imprime el ticket sola, sin preguntar nada: número,
repuestos, total, cómo se pagó y el cambio.
*Se demuestra solo:* se cobra en el computador del mostrador, sale el ticket N.º 1, y sus renglones
suman exactamente el total impreso. (revisar comprobante de car-wash para adaptarlo)

**H5 · No vender lo que no hay.**
Si hay 2 y se piden 3, no se deja. Tampoco si otro dispositivo vendió la última unidad mientras se
armaba la venta.
*Se demuestra solo:* con 2 en stock, pedir 3 lo impide y dice cuántas hay.

**H6 · Descuento.**
*"Te lo dejo en $35.000."* Se aplica sobre el total, en pesos o en porcentaje, con un motivo.
*Se demuestra solo:* 10% sobre $38.500 deja $34.650 a pagar, y queda registrado quién lo dio y por
qué. cada venta que salga con descuento mostrar en una etiqueta

**H7 · Encontrar una venta y reimprimir.**
El cliente vuelve con su ticket, o pide otra copia. Se busca por número y se reimprime. historial de ventas
*Se demuestra solo:* se busca la venta N.º 1 y sale la copia igual a la original.

**H8 · Anular una venta.**
Se cobró mal o el cliente se arrepintió en el mostrador. Se anula con un motivo: el stock vuelve,
el número queda anulado para siempre y queda el rastro.
*Se demuestra solo:* se anula la venta N.º 2; el stock vuelve a lo que era, el kardex muestra la
reversión, y la siguiente venta es la N.º 3.

**H9 · Retomar una venta interrumpida.**
Se corta la luz o se cierra el navegador con la venta a medias. Al volver, la pantalla ofrece
retomarla. Lo pidió el cliente (`SPEC_Sistema_Ventas_Repuestos (3).md:209`).
*Se demuestra solo:* se agregan tres repuestos, se cierra la pestaña, y al abrirla de nuevo están
ahí.

### P2 — importantes, no bloquean

**H10 · Ventas del turno.**
La lista de lo vendido en el turno, con cuánto entró en efectivo y cuánto a cada cuenta. Es el
anticipo del arqueo de la rebanada 3.

**H11 · Aviso de venta a pérdida.**
Si el total queda por debajo de lo que costaron los repuestos (por ejemplo, por un descuento), la
pantalla lo avisa antes de cobrar. No lo impide.

---

## 3. Qué existe hoy

| Qué | Dónde | Estado |
|---|---|---|
| Sacar unidades del inventario y rechazar si no hay suficientes | `inventario/dominio/Variante.java:117-129` | **existe, nadie la llama**. No toca el costo promedio |
| El rechazo por stock, con código, disponible y pedido | `Variante.java:126` · `compartido/infraestructura/ManejadorDeErrores.java:29-31` | **existe**: responde 409 con los números |
| El movimiento de kardex de una venta, al costo promedio vigente | `inventario/dominio/MovimientoKardex.java:170-180` | **existe, nadie lo usa** |
| Los tipos VENTA y REVERSION en el kardex | `TipoMovimiento.java:9-22` · `V6__reversion_de_compra.sql:10` | **existen**: la base ya los acepta |
| Bloquear un repuesto mientras se mueve su stock | `inventario/dominio/puerto/RepositorioVariantes.java:33` | **funciona** (lo usan las compras) |
| Buscar por código exacto y por texto, sin tildes | `inventario/aplicacion/BuscarRepuestos.java:43` y `:72` · `compartido/dominio/TextoDeBusqueda.java` | **funcionan**; ninguna pantalla usa todavía la búsqueda por texto |
| Forma de pago Efectivo o Transferencia | `compras/dominio/FormaPago.java` · spec 0002 | **funciona** en compras, donde la transferencia además exige cuenta. En ventas no se pide cuenta (decisión 2) |
| Auditoría con antes, después y motivo | `V5__auditoria.sql` | **funciona**, pero la base **solo acepta tres acciones**, todas de compras y fichas (`V5__auditoria.sql:14`) |
| "Quién" hizo algo | `frontend/src/api/cliente.js:9-11` | **provisional**: un usuario fijo que manda el navegador |
| Pantallas | `frontend/src/App.jsx:66-73` | Compras e Inventario; **no hay pantalla de venta** |
| Venta, turno de caja, pagos, usuarios | — | **no existen**: ninguna tabla ni clase |

### Con qué choca

| Qué | Dónde | Por qué choca |
|---|---|---|
| **La primera venta de un repuesto congela la corrección de sus compras anteriores** | `compras/aplicacion/InventarioDeCompra.java:149` · `inventario/infraestructura/RepositorioKardexJpa.java:53-56` | Spec 0002, RF-019: una salida posterior a la compra la bloquea. Hoy nunca pasa porque no hay ventas. Desde este spec pasa todo el tiempo, y el ajuste de inventario que lo destraba llega en la rebanada 3 |
| **Una venta anulada también congela, aunque ya no haya salido nada** | los mismos | La venta y su reversión se cancelan: el stock y el promedio quedan como si no hubiera existido. Pero la regla cuenta la salida y no mira la reversión (ver RF-030) |
| El modelo pone un estado "en curso" en la venta, y también dice que la venta a medias no va a la base | `SPEC_Modelo_Datos.md:272` y `:519-523` | Manda lo segundo: el borrador vive en el navegador, así que en la base una venta nace cobrada. Es lo que permite que el número se asigne al cobrar sin dejar huecos (`:294`) |
| El modelo decidió un catálogo editable de medios de pago | `SPEC_Modelo_Datos.md:335-359` | **Revisado en la decisión 2**: las ventas se cobran en efectivo o transferencia, sin catálogo y sin cuenta |
| **El modelo decidió que imprime el servidor; el car-wash imprime desde el navegador** | `SPEC_Modelo_Datos.md:386-394` · car-wash `scripts/SETUP-TICKETERA.md:3-7` | **Revisado en la decisión 3**: imprime el navegador del computador del mostrador |
| El car-wash no deja anular una venta de una caja ya cerrada; el modelo sí lo permite | car-wash `AccessorySaleServiceImpl.java:161-164` · `SPEC_Modelo_Datos.md:472-488` | Manda el modelo: se anula, y el efecto va en el turno de hoy, sin tocar el cierre viejo (RF-026) |

### Lo que dice el cliente

| Qué | Dónde |
|---|---|
| Un solo cajero a la vez, atención solo en el local, al detalle, busca por código, nombre, modelo o marca | `SPEC_Sistema_Ventas_Repuestos (3).md:34-40` |
| Comprobante de pago interno, **sin requisitos fiscales**. Efectivo, QR y mixto, sin datáfono. El QR se marca a mano, sin pasarela | `:59-66` |
| Descuentos: **los autoriza el propio cajero** | `:65` |
| Ticketera térmica tipo POS | `:66` |
| Venderán desde "computador, celular etc" | `:166` |
| No hay un tiempo máximo para registrar una venta | `:174` |
| Venta interrumpida: ofrecer retomarla. Sin stock: no dejar vender. Anular: el cajero, con auditoría | `:209-217` |

### Lo que decidió el modelo de datos

| Qué | Dónde |
|---|---|
| **No se vende sin caja abierta** y no se vende sin stock | `SPEC_Modelo_Datos.md:562-563` |
| El precio vendido es una foto: si mañana sube, el ticket de ayer no cambia | `:277-278` |
| Número corrido que nunca reinicia; se asigna al cobrar; un anulado no se reutiliza | `:280-304` |
| Descuento sobre el total, por monto o porcentaje; **se guarda el monto**; topado al total; motivo obligatorio | `:306-333` |
| La caja es por **turno**, no por día; solo una abierta a la vez | `:396-442` |
| Anular: la venta queda anulada, el stock vuelve con una reversión que no toca el promedio, el efecto en caja va en el turno de hoy | `:470-488` |

### Lo que hace el car-wash

| Qué | Dónde |
|---|---|
| La venta de mostrador guarda el precio al vender por renglón y se registra una sola vez aunque llegue repetida | `AccessorySaleServiceImpl.java:72-79` y `:115-121` |
| El comprobante lista cada forma de pago con su monto cuando el pago es dividido | `frontend/src/utils/printPaymentReceipt.js:184-202` |
| **La impresión nunca bloquea ni deshace el cobro** | `printPaymentReceipt.js:23-24` |
| Imprime desde el navegador con Chrome en modo kiosco; el setup es manual, una vez por PC | `scripts/SETUP-TICKETERA.md:3-12` |
| Para celular usa RawBT (impresora Bluetooth), **marcado como no verificado** | `frontend/src/utils/rawbtPrint.js:173-176` |

---

## 4. Las decisiones

### Decisión 1 · Qué entra en este spec — [RESUELTO] opción B: usuarios y login van en el spec 0004

La rebanada 2 del plan de trabajo trae todo junto: usuarios y login, turno de caja, venta,
comprobante, anulación, descuento y venta interrumpida. Es demasiado para un solo spec, y hay una
dependencia: **la venta necesita un turno abierto**, y el turno y la venta necesitan saber quién.

| Opción | Qué implica |
|---|---|
| A. Toda la rebanada 2 en este spec | Un spec enorme. La primera venta espera a que exista el login completo |
| **B. Venta, comprobante y abrir turno aquí; usuarios y login en el spec 0004** | Se vende pronto. "Quién" vendió, descontó o anuló es el usuario provisional hasta el 0004, **igual que en compras hoy**. El 0004 lo reemplaza en compras y ventas a la vez |
| C. Vender sin turno de caja | Rompe "no se vende sin caja abierta" (`SPEC_Modelo_Datos.md:563`): quedarían ventas fuera de todo arqueo que habría que repartir a mano. Descartada |

**Resolución (2026-09-14): B.** Con una condición que no se negocia: **el spec 0004 tiene que estar
hecho antes de usar las ventas en la tienda de verdad.** Sin él, un descuento o una anulación no señalan
a nadie, y ese es justo el control que el cliente pidió.

El **cierre** del turno queda en la rebanada 3, con gastos, retiros y arqueo. Hasta entonces el
turno abierto no se cierra. No se agrega un "cerrar" sin conteo: un cierre sin arqueo parece un
control y no lo es.

### Decisión 2 · Con qué paga el cliente — [RESUELTO] efectivo o transferencia, sin cuenta

Se consideraron un catálogo editable de medios de pago (lo que proponía el modelo,
`SPEC_Modelo_Datos.md:335-359`) y cobrar a las cuentas del spec 0002.

**Resolución: Efectivo o Transferencia, y nada más.** La transferencia no dice a qué cuenta llegó;
el QR cuenta como transferencia. Mixto es una parte de cada una.

Qué implica:

- **Solo el efectivo entra al cajón**, que es la regla del modelo para el arqueo. Aquí sale sola,
  sin un campo que alguien pueda marcar mal.
- **Lo cobrado por transferencia se sabe en total, no por cuenta.** Cuadrar Nequi y Bancolombia por
  separado contra sus extractos no se puede con los datos de las ventas. Aceptado.
- En compras la transferencia sí exige cuenta (spec 0002). Son decisiones distintas a propósito: al
  pagar a un proveedor importa desde dónde salió la plata; al cobrar en el mostrador, no.
- La transferencia no se valida contra el banco: el cajero la marca y verificar que llegó es su
  responsabilidad. El cliente ya lo sabe (`SPEC_Sistema_Ventas_Repuestos (3).md:68-71`).

### Decisión 3 · Cómo sale el comprobante — [RESUELTO] lo imprime el navegador del mostrador

Se consideraron dos formas:

- **Que imprima el servidor** (lo que decidía el modelo, `SPEC_Modelo_Datos.md:386-394`): el ticket
  saldría por la ticketera del mostrador aunque la venta se hiciera desde un celular. Cuesta
  conectar el servidor con la impresora.
- **Que imprima el navegador**, como el car-wash en producción (`scripts/SETUP-TICKETERA.md`).

**Resolución: imprime el navegador del computador del mostrador.** La ticketera es una **POS
térmica tradicional**, instalada en ese computador como impresora de Windows.

Cómo funciona, igual que en el car-wash:

- **Se configura una sola vez** en ese computador: la ticketera queda como impresora predeterminada,
  y el sistema se abre desde un acceso directo del navegador en modo kiosco. Con eso, **al cobrar
  el ticket sale solo, sin diálogo de impresión**.
- Sin esa configuración, al cobrar aparece el diálogo normal de impresión. Sirve para probar.
- **La impresión nunca bloquea ni deshace la venta**, y el comprobante siempre se puede ver en
  pantalla y reimprimir.

Qué implica:

- **Solo imprime el computador que tiene la ticketera.** Una venta hecha desde un celular o una
  tablet queda registrada, pero su ticket se reimprime desde el computador del mostrador.
- Si alguien cambia la impresora predeterminada o abre el sistema desde otro acceso directo, el
  ticket deja de salir solo. Se documenta en una guía de instalación, como la del car-wash.
- El ticket se diseña para papel de **80 mm**, el de las ticketeras POS de toda la vida. Si fuera
  de 58 mm, cambia un ajuste del formato, no la venta.

---

## 5. Requisitos funcionales

### Turno de caja

- **RF-001** · Se abre un turno con el fondo del cajón (cero o más) y queda quién y cuándo lo abrió.
  **Solo puede haber un turno abierto.**
- **RF-002** · Sin turno abierto no se vende ni se anula. La pantalla de venta lo dice y ofrece
  abrirlo.
- **RF-003** · Cada venta pertenece al turno en que se cobró, por enlace directo y nunca por la fecha.
  Así un turno puede cruzar la medianoche sin partirse.

### Armar la venta

- **RF-004** · **Un solo buscador.** Si lo escrito es exactamente el código de un repuesto, entra a
  la venta y el cursor vuelve al buscador. Si no, busca por parte del código, nombre, marca o
  aplicación, sin mayúsculas ni tildes, y muestra la lista para elegir con el teclado.
- **RF-005** · Solo se ofrecen repuestos activos. Cada renglón muestra código, nombre, marca, precio,
  cantidad y cuántos hay.
- **RF-006** · Agregar un repuesto que ya está en la venta le suma cantidad a su renglón, en vez de
  crear otro.
- **RF-007** · **El precio es el precio de venta del repuesto** y no se cambia en el renglón. Negociar
  es un descuento (RF-015). Al cobrar, el precio queda guardado como foto: si después sube, la venta
  no cambia.
- **RF-008** · No se vende un repuesto con precio $0: *"No tiene precio de venta. Fíjalo en su
  ficha."*
- **RF-009** · La cantidad no puede pasar de lo que hay. La pantalla lo avisa mientras se arma, y el
  servidor lo rechaza igual al cobrar.

### Cobrar

- **RF-010** · Se cobra en **efectivo**, por **transferencia** (el QR cuenta como transferencia,
  sin decir a qué cuenta), o **mixto**: una parte en efectivo y otra por transferencia. **La suma de
  los pagos cuadra al peso con el total a pagar.**
- **RF-011** · En efectivo se puede escribir con cuánto pagó el cliente: se muestra el cambio y ambos
  quedan guardados para el comprobante. Lo recibido no puede ser menor que la parte en efectivo.
- **RF-012** · Al cobrar, **todo o nada**:
  - la venta recibe su número;
  - el stock de cada repuesto baja, con su movimiento de venta en el kardex al costo promedio
    vigente;
  - la venta queda cobrada en el turno abierto.
- **RF-013** · **El número es corrido, nunca reinicia, se asigna al cobrar y no se reutiliza**, ni
  aunque la venta se anule.
  `[NECESITA ACLARACIÓN: ¿arranca en 1, o sigue la numeración de algún talonario que ya usen?]`
- **RF-014** · Cobrar dos veces la misma venta (doble clic, o reenviar tras un corte) registra
  **una sola**.

### Descuento

- **RF-015** · El descuento va sobre el total, capturado en pesos o en porcentaje. **Se guarda el
  monto en pesos**, redondeado al peso; el porcentaje queda como registro de cómo se capturó.
- **RF-016** · No puede superar el total. Exige motivo, y queda quién lo aplicó y un evento de
  auditoría.
- **RF-017** *(P2)* · Si el total a pagar queda por debajo de lo que costaron los repuestos al
  promedio vigente, la pantalla avisa antes de cobrar. No bloquea. Un repuesto sin costo conocido no
  entra en la cuenta.

### Comprobante

- **RF-018** · El comprobante, para papel de 80 mm, lleva:
  - encabezado con los datos de la tienda (RF-033);
  - "COMPROBANTE DE PAGO", número, fecha y hora, y quién atendió;
  - un renglón por repuesto: nombre, marca, cantidad × precio y total;
  - subtotal, descuento si lo hubo, y total;
  - cada pago: en efectivo, lo recibido y el cambio; por transferencia, solo "Transferencia" y el
    monto;
  - al pie: *"Comprobante interno · no es factura electrónica"*.
- **RF-019** · Los renglones del comprobante suman exactamente el total impreso.
- **RF-020** · **Al cobrar en el computador del mostrador, el comprobante se imprime solo** en la
  ticketera (decisión 3). Si ese computador todavía no está configurado, aparece el diálogo de
  impresión.
- **RF-021** · Si la impresión falla, **la venta queda cobrada igual**: la pantalla avisa y ofrece
  reimprimir.
- **RF-022** · Todo comprobante se puede ver en pantalla y reimprimir. El de una venta anulada sale
  marcado **ANULADA**. Una venta hecha desde un celular o una tablet se reimprime desde el computador
  del mostrador.
- **RF-023** · Se entrega una **guía de instalación** de la ticketera en el computador del mostrador:
  driver, impresora predeterminada, papel de 80 mm y acceso directo en modo kiosco.

### Encontrar y anular

- **RF-024** · Se ven las ventas del turno, de la más reciente a la más antigua, y se busca cualquier
  venta por su número.
- **RF-025** · Se anula con motivo:
  - el stock de cada repuesto vuelve, con una reversión en el kardex que **no toca el costo
    promedio**;
  - la venta queda anulada con quién, cuándo y por qué;
  - su número no se reutiliza;
  - queda un evento de auditoría con el antes y el después.
- **RF-026** · Anular una venta de un turno anterior se permite. Lo que devuelve la caja cuenta en el
  **turno abierto hoy**; el turno viejo no se toca. (Pesa desde la rebanada 3, cuando exista el
  arqueo.)
- **RF-027** · Una venta no se edita. Si quedó mal, se anula y se vende de nuevo. Una anulada no se
  vuelve a anular.

### Venta interrumpida

- **RF-028** · La venta que se está armando se guarda en el dispositivo con cada cambio. Al volver a
  la pantalla se ofrece retomarla o descartarla. **Mientras no se cobre, no tiene número ni mueve
  stock.**
  *Revisado el 2026-09-16:* **se retoma sola, sin preguntar.** Al volver a Vender la venta aparece
  armada como estaba, y *Cancelar venta* la descarta. El aviso que obligaba a elegir entre retomar o
  descartar bloqueaba el buscador y el catálogo. Un cobro que se mandó y no tuvo respuesta sigue
  bloqueando hasta reintentarlo: es lo que evita cobrar dos veces. **Implementado el 2026-09-19.**
- **RF-029** · Al retomarla, precio y stock se vuelven a revisar contra lo actual: pudieron cambiar
  mientras estaba guardada.

### Compras

- **RF-030** *(P2)* · Una venta anulada no bloquea corregir ni anular las compras de sus repuestos
  (spec 0002, RF-019). La venta y su reversión se cancelan: no sacaron nada del inventario.

### Cobro sin fricción *(agregado al aprobar el plan, 2026-09-14)*

- **RF-031** · El cobro arranca en **efectivo**. Hay botones rápidos: *Exacto*, y los billetes que
  superan el total ($20.000, $50.000, $100.000). El cambio sale solo, sin teclear montos.
- **RF-032** · El descuento ofrece **motivos frecuentes de un toque** ("Cliente frecuente",
  "Negociación", "Producto con detalle") y texto libre para lo demás. El motivo sigue siendo
  obligatorio.
- **RF-033** · Los **datos de la tienda** que salen en el comprobante (nombre comercial, NIT,
  dirección, teléfono y mensaje al pie) se editan en una pantalla del sistema. Mientras el cliente
  no los dé, arranca con "RD MOTORS".

---

## 6. Manejo de errores

| Situación | Qué pasa |
|---|---|
| Se intenta vender o anular sin turno abierto | No se permite; la pantalla ofrece abrir el turno |
| Se intenta abrir un turno con otro ya abierto | No se permite; dice desde cuándo está abierto y quién lo abrió |
| Se pide más de lo que hay | No se agrega; dice cuántas hay |
| Otro dispositivo vendió la última unidad mientras se armaba la venta | Al cobrar se rechaza: *"Ya no quedan unidades de 352B59K"*. **La venta armada no se pierde**: se ajusta y se cobra |
| El precio de un repuesto cambió mientras se armaba la venta (por ejemplo, entró una compra que fijó precio nuevo) | Al cobrar se rechaza y la pantalla muestra el precio nuevo antes de cobrarle al cliente. Nunca se cobra un precio distinto al que se vio |
| Repuesto con precio $0 | No se agrega: *"No tiene precio de venta. Fíjalo en su ficha."* |
| Los pagos no suman el total | No se cobra; dice cuánto falta o sobra |
| Lo recibido en efectivo es menor que la parte en efectivo | No se cobra |
| Descuento sin motivo, o mayor que el total | No se cobra; se marca el campo |
| Doble clic en cobrar | Una sola venta, un solo número, un solo descuento de stock |
| La impresora está apagada o sin papel | La venta queda cobrada; aviso con *Reimprimir* |
| Al cobrar aparece el diálogo de impresión en vez de salir el ticket solo | El computador no está configurado (guía de RF-023). Se imprime desde el diálogo; la venta ya está cobrada |
| Se cobra desde un celular o una tablet | La venta queda cobrada; el comprobante se ve en pantalla y se reimprime desde el computador del mostrador |
| Sin conexión con el servidor de la tienda (la tablet salió del WiFi) | No se cobra. Aviso ámbar; la venta armada queda guardada en el dispositivo |
| Anular sin motivo | No se anula; se pide el motivo |
| Anular una venta ya anulada | *"Esta venta ya fue anulada"* |
| Se busca un número que no existe | *"No hay una venta con ese número"* |

---

## 7. Requisitos no funcionales

- **Todo o nada.** Una venta toca el turno, varios repuestos, el kardex y los pagos. Se aplica
  completa o no se aplica nada: jamás stock descontado sin venta, ni venta sin su stock.
- **Concurrencia.** Cada repuesto se bloquea mientras se descuenta, en un orden fijo. Así dos ventas
  con los mismos repuestos en distinto orden no se traban esperándose.
- **Plata.** Todo monto en pesos enteros. El descuento se guarda en pesos. **Las partes suman el
  total**: renglones = subtotal; subtotal − descuento = total; pagos = total.
- **Inventario.** Vender **no toca el costo promedio**. El kardex registra la salida al promedio
  vigente, que es lo que medirá la ganancia en la rebanada 3. El kardex sigue siendo solo de
  agregar.
- **Auditoría.** Nacen las acciones **anular venta** y **aplicar descuento**. La base hoy las
  rechaza (`V5__auditoria.sql:14`): hace falta migración.
- **Esquema.** Tablas nuevas para turno, venta, renglones y pagos. **¿Funciona en una base que ya
  tiene filas?** No hay ventas previas. El kardex ya acepta los tipos de venta y reversión
  (`V6__reversion_de_compra.sql:10`).
- **Doble envío.** Cobrar **suma** (stock, dinero), así que lleva llave contra repetidos desde ya,
  por el doble clic (`SPEC_Modelo_Datos.md:574`). Anular no la necesita: una venta anulada no se
  anula dos veces.
- **Sin internet.** Es la rebanada 4. Hasta entonces, el dispositivo que vende necesita el WiFi
  local con el servidor de la tienda. No necesita internet.
- **Roles.** Hasta el spec 0004, "quién" es el usuario provisional. Lo que queda decidido para
  entonces: el cajero **vende, descuenta y anula** (`SPEC_Sistema_Ventas_Repuestos (3).md:65` y
  `:217`), y **no ajusta inventario** (`SPEC_Modelo_Datos.md`, pregunta 5).
- **Teclado primero.** Agregar por código, cambiar cantidad y cobrar sin tocar el mouse. El cliente
  no pide un tiempo máximo (`:174`), pero es un mostrador con alguien esperando.
- **Fechas.** Hora de Colombia en el comprobante y en la lista del turno.

---

## 8. Criterios de aceptación

**Turno**
- [ ] Sin turno abierto la pantalla de venta no deja vender y ofrece abrirlo
- [ ] Se abre un turno con $100.000 y no se puede abrir otro mientras esté abierto

**Vender y cobrar**
- [ ] Escribir un código exacto agrega el repuesto y deja el cursor en el buscador
- [ ] Buscar *filtro inoki* (y *bujia* para una BUJÍA) muestra la lista y se elige con el teclado
- [ ] Se venden dos repuestos: el stock de cada uno baja la cantidad exacta y su kardex muestra la
      venta, con el costo promedio sin cambiar
- [ ] $38.000 pagados con $50.000 muestran y guardan $12.000 de cambio
- [ ] Una venta mixta de $20.000 en efectivo y $18.000 por transferencia se cobra; una que no
      cuadra, no
- [ ] Con 2 en stock no se pueden vender 3, ni desde la pantalla ni directo al servidor
- [ ] Hay prueba de que dos cobros simultáneos por la última unidad dejan una sola venta
- [ ] Doble clic en cobrar registra una sola venta
- [ ] Si el precio cambió mientras se armaba la venta, no se cobra el precio viejo

**Descuento**
- [ ] 10% sobre $38.500 guarda $3.850 de descuento y $34.650 a pagar
- [ ] Sin motivo no se cobra; con motivo queda el evento de auditoría con quién

**Comprobante**
- [ ] En el computador del mostrador configurado con la guía, al cobrar el ticket de 80 mm sale solo,
      sin diálogo, con número, renglones, total, pagos y cambio, y sus renglones suman el total
- [ ] Con la impresora apagada la venta queda cobrada y se reimprime después
- [ ] Una venta cobrada desde el celular se reimprime desde el computador del mostrador
- [ ] La venta N.º 1 se encuentra por su número y su copia es igual al original

**Anular**
- [ ] Se anula una venta con motivo: el stock vuelve, el kardex muestra la reversión con el promedio
      sin cambiar, y queda el evento de auditoría
- [ ] Tras anular la N.º 2, la siguiente venta es la N.º 3
- [ ] El comprobante de una venta anulada sale marcado ANULADA

**Venta interrumpida**
- [ ] Una venta con tres repuestos sobrevive a cerrar la pestaña y se retoma al volver
- [ ] Mientras no se cobra no tiene número ni mueve stock

**General**
- [ ] Hay prueba contra Postgres real de que una venta que falla a mitad no deja nada aplicado
- [ ] `./mvnw clean test` en verde

---

## 9. Qué no se toca

- **Comprar sigue igual**: registrar, corregir y anular compras no cambian, salvo RF-030.
- **Vender no toca el costo promedio.** Solo las compras lo recalculan.
- **Los movimientos de kardex ya escritos nunca se editan.**
- **Una venta cobrada no se edita**, y su número no se reutiliza.

---

## 10. Fuera de alcance

| Qué | Por qué |
|---|---|
| **Usuarios, login y roles** | Spec 0004 (decisión 1). Tiene que estar antes de usar las ventas en la tienda |
| Cerrar el turno, arqueo, gastos y retiros | Rebanada 3 |
| Ajustes de inventario | Rebanada 3, solo ADMIN. Mientras tanto, RF-019 del spec 0002 bloquea corregir compras de repuestos ya vendidos |
| **Devoluciones de cliente** | No es lo mismo que anular: la venta fue legítima, la plata ya se cerró en otro turno, y la pieza vuelve al estante. Diferido (`SPEC_Modelo_Datos.md`, pregunta 2). Regla para este spec: no asumir en ninguna parte que el stock solo entra por compras |
| Cambiar el precio o dar descuento en un renglón | El descuento va sobre el total (`SPEC_Modelo_Datos.md:319-321`). Si algún día se necesita, se agrega sin romper nada |
| Código de barras y código del proveedor en el buscador | El código de barras es futuro (`SPEC_Modelo_Datos.md` §8); los códigos por proveedor se difirieron en el spec 0001 |
| Datos del comprador en el comprobante | El cliente pidió solo el comprobante de pago (`SPEC_Sistema_Ventas_Repuestos (3).md:60-61`) |
| Factura electrónica | Fuera del alcance del proyecto (`:60-61`) |
| Pasarela de pago para el QR | No va, ni en fase 2 (`SPEC_Modelo_Datos.md:352`) |
| Decir a qué cuenta llegó una transferencia de venta | Decisión 2 |
| Imprimir en el momento desde un celular o una tablet | Decisión 3: se reimprime desde el computador del mostrador |
| Vender sin internet con cola de reenvío | Rebanada 4 |
| Que las ventas lleguen al panel del dueño | Rebanada 5 |
| Crédito o fiado a clientes | Fase 2, confirmado |

---

## 11. Riesgos

| Riesgo | Qué hacer |
|---|---|
| **Desde la primera venta, las compras de ese repuesto ya no se pueden corregir**, y el ajuste llega en la rebanada 3 | Aceptarlo sabiéndolo. Si en el uso real aparece una compra mal registrada de un repuesto ya vendido, el ajuste de inventario pasa adelante en la rebanada 3. RF-030 al menos libera los casos de ventas anuladas |
| Hasta el spec 0004, "quién" vendió, descontó o anuló no prueba nada | Decisión 1: el 0004 va antes de usar las ventas en la tienda |
| El turno no se cierra hasta la rebanada 3 | Declarado en la decisión 1. En pruebas se trabaja con un turno abierto |
| Si alguien cambia la impresora predeterminada o abre el sistema desde otro acceso directo, el ticket deja de salir solo | La guía de instalación (RF-023) y *Reimprimir* (RF-022). La venta nunca depende de la impresión |
| Una venta hecha desde un celular no imprime en el momento | Decisión 3, aceptado: se reimprime desde el computador del mostrador |
| Lo cobrado por transferencia no se puede cuadrar cuenta por cuenta | Decisión 2, aceptado |
| La transferencia y el QR no se validan | Conocido y aceptado por el cliente (`SPEC_Sistema_Ventas_Repuestos (3).md:68-71`) |

---

## 12. Preguntas abiertas

Ninguna bloquea el plan. Quedan dos datos por pedirle al cliente:

1. **Encabezado del comprobante** — nombre comercial, NIT, dirección y teléfono. Ya no es código:
   se escriben en la pantalla de datos de la tienda (RF-033) cuando el cliente los dé.
2. **Primer número** — ¿arranca en 1 o sigue un talonario? Arranca en 1 si no se dice otra cosa, y
   se ajusta antes de la primera venta.

Resueltas el 2026-09-14:

- **Decisión 1:** venta, comprobante y abrir turno aquí; usuarios, login y roles en el spec 0004,
  que va antes de usar las ventas en la tienda.
- **Decisión 2:** efectivo o transferencia, sin cuenta.
- **Decisión 3:** imprime el navegador del computador del mostrador, con una ticketera POS
  tradicional de 80 mm.
