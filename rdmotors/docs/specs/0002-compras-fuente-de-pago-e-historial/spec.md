# Spec 0002 — Compras: fuente de pago, historial y corrección

**Estado:** cerrado — ampliado el 2026-09-14 con H9 (buscar las compras de un repuesto), pedido en el QA · ampliado otra vez el 2026-09-15: la lista dice qué repuesto trae cada factura (RF-027) · ampliado el 2026-09-16: sin buscar, cada factura dice qué trae (RF-028)
**Rebanada:** extiende la 1 · ver [`PLAN_DE_TRABAJO.md`](../../PLAN_DE_TRABAJO.md)
**Depende de:** [spec 0001](../0001-catalogo-y-compras-usables/spec.md), implementado

> **Convención:** lo que dice *"hoy"* está verificado con `archivo:línea`. Lo que dice
> *"debe"* es propuesta y todavía no existe. Las dos cosas nunca se mezclan.

---

## 1. Objetivo de negocio

Hoy una compra registrada tiene tres huecos:

- **No dice con qué se pagó.** Sin eso no se puede saber cuánto efectivo salió para pagar
  proveedores, ni cuadrar las transferencias contra el extracto de cada cuenta.
- **No se puede volver a ver.** Solo queda rastro disperso en el kardex de cada repuesto.
- **No se puede corregir.** Si el administrador escribe 100 unidades en vez de 10, el stock queda
  inflado y el costo promedio queda falso **para siempre**. Y como el margen de cada venta se
  calcula con ese promedio, el error se contagia a todas las ventas futuras de ese repuesto. Hoy
  la única salida sería tocar la base a mano.

Al terminar este spec, cada compra dice cómo se pagó, se consulta en un historial, y cuando quedó
mal se corrige dejando rastro de quién, cuándo, por qué y qué cambió.

---

## 2. Caso de uso

### P1 — bloquean

**H1 · Registrar con qué se pagó.**
Al registrar la factura se elige **Efectivo** o **Transferencia**. Si es transferencia, se elige
**desde qué cuenta** (por ejemplo "Bancolombia ahorros ···4521" o "Nequi del dueño"). Si la cuenta
no existe, se crea ahí mismo, igual que el proveedor.
*Se demuestra solo:* se registra una compra en efectivo y otra por transferencia desde Nequi, y el
historial las muestra distintas.

**H2 · Ver el historial de compras.**
La lista de compras con filtros por proveedor, fechas, fuente y cuenta. Al abrir una se ve la
factura completa renglón por renglón, y el rastro de sus correcciones si las tuvo.
*Se demuestra solo:* se busca la factura FV-9912 y se ve completa.

**H3 · Corregir los datos de la factura.**
El proveedor, la fecha, el número de factura o la forma de pago quedaron mal. Se corrigen con un
motivo, y **el inventario no se mueve**: nada de eso cambia cuántas unidades entraron ni a qué
costo.
*Se demuestra solo:* una compra pasa de Efectivo a Transferencia y el detalle muestra quién la
cambió, cuándo, por qué y qué decía antes.

**H4 · Corregir los renglones de una compra.**
Una cantidad, un costo, un precio o un repuesto quedaron mal, faltó un renglón o sobró uno. Se abre
la compra, se corrige lo que haga falta y se guarda con un motivo. **Stock y costo promedio quedan
como si la factura se hubiera registrado bien desde el principio.**
*Se demuestra solo:* una compra registrada con 100 unidades se corrige a 10, y el repuesto queda
igual que otro al que se le registraron 10 desde el comienzo.

**H5 · Anular una compra que no debió registrarse.**
Se registró dos veces, o no era de esta tienda. Se anula con un motivo: todo lo que metió al
inventario sale, y la compra queda en el historial marcada como anulada.
*Se demuestra solo:* una factura registrada dos veces se anula una vez y el stock queda como con
una sola.

### P2 — importantes, no bloquean

**H6 · Totales por fuente y por cuenta en un período.**
Encima del historial, lo filtrado sumado: *"septiembre: efectivo $420.000 · transferencia
$1.200.000 (Bancolombia $900.000 · Nequi $300.000)"*.
*Se demuestra solo:* los totales cambian con el filtro y suman exactamente el total del período.

**H7 · La corrección de ficha deja auditoría** (deuda del spec 0001).
El spec 0001 pidió que corregir la ficha de un repuesto dejara auditoría (`spec 0001:50` y `:297`),
pero no se construyó porque no existía dónde guardarla. Este spec la crea (H3-H5), así que la
deuda se salda aquí.
*Se demuestra solo:* se corrige el nombre de un repuesto y queda el evento con el antes y el
después.

**H9 · Buscar las compras de un repuesto** *(ampliación del 2026-09-14, pedida en el QA)*.
Llega un cliente preguntando por el filtro INOKI. En el historial se escribe *inoki* y quedan solo
las facturas donde vino. Al abrir una, el renglón del INOKI se ve resaltado: con una factura de
veinte renglones no hay que buscarlo con el dedo.
*Se demuestra solo:* se escribe *inoki*, sale FV-9912, y al abrirla su renglón INOKI está resaltado
y el FACTORY no.

*Ampliación del 2026-09-16:* **sin buscar nada**, cada factura de la lista dice debajo qué trae
("FILTRO DE ACEITE INOKI × 25 · PASTILLAS FRENO CBI × 4 · y 1 más"). Con quince facturas de
Importadora Jotapartes, casi todas *Sin número*, la lista no dejaba distinguir una de otra sin
abrirlas.

*Ampliación del 2026-09-15:* buscando *aceite*, cada factura de la lista dice debajo **qué aceites
trae** ("FILTRO DE ACEITE INOKI × 10 · FILTRO ACEITE FACTORY × 5"), sin abrirla. Con ocho facturas
en la lista, abrir una por una para saber cuál trajo el de motor era el trabajo que sobraba.

### P3 — deseables

**H8 · Ir de un movimiento del kardex a su compra.**
En la ficha de un repuesto, la factura de cada movimiento es un enlace a esa compra.

---

## 3. Qué existe hoy

| Qué | Dónde | Estado |
|---|---|---|
| La compra guarda proveedor, las dos fechas, factura y total | `compras/dominio/Compra.java:42-57` | **funciona** — sin fuente, sin estado, sin anulación |
| Registrar una compra | `compras/aplicacion/RegistrarCompra.java:68-114` | **funciona**, 24 pruebas |
| El puerto de compras | `compras/dominio/puerto/RepositorioCompras.java:9-14` | solo `guardar` y `buscar`: **no hay forma de listar** |
| Endpoints de compras | `compras/infraestructura/CompraController.java:40` | **un solo** `POST` |
| Proveedor y factura de cada movimiento del kardex | `compras/infraestructura/DocumentosDeCompra.java` | **funciona** (fase 6 del spec 0001) |
| Cabecera de la pantalla de compra | `frontend/src/paginas/Compra.jsx:375-410` | proveedor, fecha y factura; **sin forma de pago** |
| El kardex ya tiene el tipo `REVERSION` y la columna `movimiento_revertido_id` | `V1__esquema_inicial.sql:102` y `:112` · `MovimientoKardex.java:71-72` | **existen, nadie los usa** |
| Caja y medios de pago | — | **no existen**: llegan con las rebanadas 2 y 3 |

### Con qué choca

Esto es lo que el spec descubrió que **no encaja** con lo que hay, y que el plan tiene que resolver:

| Qué | Dónde | Por qué choca |
|---|---|---|
| **La reversión de hoy nunca toca el costo promedio** | `TipoMovimiento.java:18` · `Variante.java:166-170` (`reponerPorReversion`) | Se pensó para **anular una venta**: devuelve unidades al promedio vigente. Anular o corregir una **compra** es lo contrario: **saca** unidades que entraron a un costo propio, y el promedio **tiene** que moverse. Son dos reversiones distintas y no pueden compartir la regla |
| **El precio anterior no se guarda en ninguna parte** | `RegistrarCompra.java:98-100` · `V1__esquema_inicial.sql:87-88` | La compra fija el precio nuevo y el renglón guarda solo ese. Si se deshace un renglón que cambió el precio, hoy no hay forma de saber cuál tenía antes |
| **No existe dónde guardar la auditoría** | `SPEC_Modelo_Datos.md:444-454` la decide; ninguna migración ni clase la crea | H3, H4, H5 y H7 la necesitan. Este spec la trae antes de la rebanada 2 |
| **"Quién" es provisional** | `CompraController.java:43-45` — `TODO(auth)` | La auditoría va a guardar el usuario que manda el navegador hasta que exista el login (rebanada 2). **Hasta entonces, "quién" no prueba nada** |
| Las acciones de auditoría decididas no incluyen compras | `SPEC_Modelo_Datos.md:451` | Hay que agregar las de corregir y anular compra |

### Para buscar por repuesto (ampliación del 2026-09-14)

| Qué | Dónde | Estado |
|---|---|---|
| Los filtros del historial se arman en un solo sitio y los usan la lista y los totales | `compras/infraestructura/RepositorioComprasJpa.java:144-179` | **funciona**: un filtro nuevo ahí aplica a los dos |
| Buscar número de factura: texto parcial, sin distinguir mayúsculas | `RepositorioComprasJpa.java:171` · `FiltroCompras.java:33` | **funciona** — es el modelo a seguir |
| El inventario busca por código, nombre, marca y aplicación | `inventario/infraestructura/RepositorioVariantesJpa.java:114-136` | **funciona** — el historial debe buscar igual |
| Índice de renglones vigentes por repuesto | `V6__reversion_de_compra.sql:83` | **existe**: no hace falta migración |
| El renglón del detalle no dice si coincide con una búsqueda, ni trae la aplicación | `compras/aplicacion/DetalleCompra.java:58-75` · `CompraController.java:345` | **no existe** |
| Abrir una compra desde el historial se lleva los filtros solo para el botón de volver | `frontend/src/paginas/HistorialCompras.jsx:109` | la búsqueda **no llega** al detalle |
| El doble en memoria filtra compras sin mirar sus renglones | `domain/src/test/.../compartido/Falsos.java:272-283` | hay que enseñarle |
| *(2026-09-15)* La fila de la lista no trae renglones: fecha, proveedor, factura, pago, cuántos renglones y total | `compras/dominio/ResumenCompra.java:20-30` | para RF-027 hay que traer **cuáles** coinciden |
| *(2026-09-15)* La regla de "coincide" ya existe y la usa el detalle | `compras/dominio/BusquedaDeRepuesto.java:44` · `ConsultarCompras.java:72` | la lista la reusa: misma regla, mismas coincidencias |
| *(2026-09-15)* El resaltado de un texto buscado ya existe como componente | `frontend/src/componentes/Resaltado.jsx:10` | se reusa en la lista |

**Con qué choca:** la regla "este renglón coincide con lo buscado" se necesita en dos sitios: en la
consulta de la base, para filtrar la lista, y al armar el detalle, para resaltar. Si cada uno la
escribe a su manera, puede salir una factura en la lista sin nada resaltado al abrirla (ver RF-026).

### Lo que dicen los documentos

| Qué | Dónde |
|---|---|
| **Una venta no se edita nunca: se anula y se hace de nuevo**, porque anular y recrear deja el rastro gratis | `SPEC_Modelo_Datos.md:482-484` |
| **No se puede anular una compra si después se vendió stock**: con promedio no se sabe si esas unidades salieron de esa compra | `SPEC_Modelo_Datos.md:166-167` |
| La auditoría es una sola tabla, append-only, con antes, después y motivo obligatorio | `SPEC_Modelo_Datos.md:444-454` |
| El cliente pidió historial de precios de compra por proveedor, y registro de compras como P1 | `SPEC_Sistema_Ventas_Repuestos (3).md:100` y `:241` |

### Lo que hace el car-wash

| Qué | Dónde |
|---|---|
| **Anular compra** con motivo, quién y cuándo; bloquea la compra antes de revisar si ya estaba anulada, para que dos anulaciones a la vez no reviertan dos veces | `AccessoryPurchaseServiceImpl.java:173-209` |
| **No deja anular si hay menos stock que lo comprado, o si hubo ventas o ajustes después**: *"se vendió o ajustó stock después de registrarla, y con costeo por promedio no se puede saber si esas unidades siguen en inventario"* | `AccessoryInventoryServiceImpl.java:65-87` |
| Al anular, el promedio se recalcula quitando lo que entró con esa compra. Si el stock queda en cero, deja el promedio vacío. **RD Motors no copia esto último**: aquí "—" significa *nunca se compró* (ver RF-016) | `AccessoryInventoryServiceImpl.java:92-93` |
| Corregir el costo de un renglón **sin anular**, con un movimiento que ajusta el valor. El renglón original no se modifica: *"es un documento"* | `AccessoryPurchaseServiceImpl.java:225-284` |
| Solo lo pagado "de caja" resta del arqueo y exige turno abierto | `AccessoryPurchaseServiceImpl.java:315-330` · `CashSessionBalanceService.java:77-83` |

---

## 4. Las decisiones

### Decisión 1 · Cómo se corrige una compra que ya movió inventario

Ejemplo: la factura FV-9912 trae 30 renglones y en uno se escribió 100 en vez de 10.

| Opción | Qué pasa en el ejemplo | Costo |
|---|---|---|
| **A.** Editar la compra y reescribir sus movimientos | Se cambia el 100 por 10 en el renglón y en el kardex | **Descartada.** Rompe la regla de que el kardex nunca se edita, y se pierde lo que decía antes. Es justo el rastro que la auditoría existe para guardar |
| **B.** Anular la factura completa y registrarla de nuevo (lo que hace el modelo con las ventas) | Los **30 repuestos** reciben una salida por anulación y una entrada nueva, aunque 29 estaban bien. Y si **cualquiera** de los 30 ya se vendió, no se puede anular: la factura queda sin corregir | Bajo de construir. Pero ensucia el kardex de 29 repuestos sin razón, y en una tienda real algo de la factura se vende en horas |
| **C.** Corregir por renglón, dentro de la misma compra | Solo el renglón malo se revierte y vuelve a entrar con 10. Los otros 29 no se tocan. El bloqueo por ventas mira **ese** repuesto, no la factura entera | Medio. La compra conserva su número y el renglón viejo queda como historia |

**[DECIDIDO] C.** Corrige lo que está mal y nada más. El kardex de cada repuesto cuenta su
propia historia sin movimientos fantasma, y una venta de un filtro no impide arreglar el costo de
unas pastillas de la misma factura.

**Anular (H5) sigue existiendo**, para la compra que no debió registrarse. Por dentro es la misma
corrección aplicada a todos los renglones.

**Los datos de la factura** —proveedor, fecha, número, forma de pago— **se corrigen en su lugar**,
sin revertir nada, porque no mueven inventario. Anular y recrear por un número de factura mal
escrito llenaría de ruido el kardex de toda la factura.

### Decisión 2 · Cómo se guarda la cuenta de una transferencia

| Opción | Qué implica |
|---|---|
| **A.** Texto libre | "bancolombia", "Bancolombia", "BANCOLOMBIA AHORROS" terminan siendo tres cuentas en el reporte |
| **B.** Lista de cuentas | Se elige de la lista; si falta, se crea ahí mismo. Una cuenta que ya no se usa se desactiva |

**[DECIDIDO] B.** Los totales por cuenta (H6) solo cuadran si cada cuenta se escribe una vez.
Se guarda **un nombre reconocible, no el número completo**: para el reporte basta
"Bancolombia ···4521", y un número de cuenta completo no tiene por qué vivir en esta base.

Nequi y Daviplata no son una fuente aparte: son cuentas. "Transferencia · Nequi del dueño".

### Decisiones tomadas en la revisión

| Qué | Resolución |
|---|---|
| Compras a crédito | **fuera de este spec** hasta consultarlo con el cliente (ver §10) |
| Una factura que llega a crédito mientras tanto | **se registra al llegar**, con la forma de pago con la que se piensa pagar. Si al final se paga distinto, se corrige con H3 y queda auditado |
| "De caja" antes de que exista la caja | **no se ofrece.** Efectivo hace ese papel hasta que exista la caja |
| Formas de pago | **Efectivo** o **Transferencia** con su cuenta |
| Cómo se guarda la cuenta | **lista de cuentas con nombre reconocible**, sin el número completo (decisión 2) |
| Corregir la forma de pago de una compra ya registrada | **sí**, con auditoría, como pide el spec 0001 para corregir |
| Compras registradas antes de este cambio | se marcan **Efectivo**: hoy solo hay datos de prueba |
| Corregir una compra | **entra en este spec**, **por renglón** dentro de la misma compra (decisión 1) |
| Auditoría de corregir ficha (deuda del spec 0001) | **entra en este spec** como P2 (H7, RF-024) |
| Buscar por repuesto (pedido en el QA, 2026-09-14) | **entra en este spec** como P2 (H9, RF-025 y RF-026) |
| Qué cifras se ven al buscar un repuesto | **las de siempre: facturas completas.** No se agrega columna ni total "del repuesto" |
| Qué se ve en la lista al buscar un repuesto (**revisado el 2026-09-15**) | Antes: nada, se abría la factura para ver el resaltado. **Ahora: debajo de cada factura, los repuestos que coinciden con su cantidad** (RF-027). Las cifras siguen siendo las de la factura completa |
| Cómo se busca | **escribiendo**, como en el inventario. No se elige de una lista |
| Tildes (pedido en el QA, 2026-09-14) | **se ignoran, en todos los buscadores de repuestos**: historial, inventario, búsqueda por texto y sugerencias de concepto. "bujia" encuentra "BUJÍA" y al revés; la eñe cuenta como tilde ("nandu" encuentra "ÑANDÚ"). En todos a la vez para que la misma palabra encuentre lo mismo en cualquier pantalla, y porque en las sugerencias evita duplicar un concepto que ya existe |

---

## 5. Requisitos funcionales

### Forma de pago

- **RF-001** · Toda compra se registra con su forma de pago: **Efectivo** o **Transferencia**. Es
  obligatoria.
- **RF-002** · Una transferencia lleva su **cuenta de origen**, obligatoria. Una compra en efectivo
  no lleva cuenta.
- **RF-003** · Las cuentas son una lista: nombre reconocible y activa o no. Se crea una nueva desde
  la pantalla de compra sin perder lo capturado.
- **RF-004** *(P2)* · Una cuenta que ya no se usa se desactiva: deja de ofrecerse, y las compras que
  la usaron la siguen mostrando. Una cuenta con compras no se borra.

### Historial

- **RF-005** · Lista de compras, de la más reciente a la más antigua **por fecha de la factura**,
  con: fecha de la factura, proveedor, número, forma de pago y cuenta, cantidad de renglones, total
  y estado (vigente o anulada).
- **RF-006** · Filtros: proveedor, rango de fechas de la factura, forma de pago, cuenta y estado.
  Por defecto se ven las vigentes.
- **RF-007** · Búsqueda por número de factura.
- **RF-008** · Detalle de una compra:
  - la cabecera, incluidas la fecha en que se registró y quién la registró;
  - los renglones vigentes: código, repuesto, marca, cantidad, modo de captura, costo unitario,
    costo total y el precio de venta que fijó;
  - el rastro de correcciones: quién, cuándo, motivo, y qué decía antes y después. Los renglones
    reemplazados se ven ahí, no desaparecen.
- **RF-009** · El total del detalle **cuadra al peso** con la suma de sus renglones vigentes.
- **RF-010** *(P2)* · Totales de lo filtrado por forma de pago y por cuenta. Las anuladas no cuentan.
  **Las partes suman el total**: si no cuadra es un error, no un redondeo.
- **RF-011** *(P3)* · Desde el kardex de un repuesto se abre la compra de ese movimiento.
- **RF-025** *(P2)* · Buscar por repuesto: se escribe parte del código, el nombre, la marca o la
  aplicación, y la lista deja solo las compras con **al menos un renglón vigente** que coincida, sin
  distinguir mayúsculas **ni tildes**. Un renglón que se cambió o se quitó en una corrección no cuenta. Se combina
  con los demás filtros, y los totales (RF-010) son los de las compras que quedan en la lista.
- **RF-026** *(P2)* · Al abrir una compra desde esa búsqueda, **los renglones que coinciden se ven
  resaltados** y lo buscado queda marcado dentro del texto. Si coincidió por la aplicación, que no se
  ve en la tabla, se muestra en ese renglón. Son exactamente los renglones que hicieron salir la
  compra en la lista: nunca una compra en la lista sin nada resaltado al abrirla. Los renglones
  "antes de corregir" no se resaltan.
- **RF-027** *(P2, 2026-09-15)* · En la lista, **debajo de cada factura se ven los repuestos que
  coinciden** con lo buscado: nombre y marca, con lo buscado resaltado, y la cantidad que trajo
  ("FILTRO DE ACEITE INOKI × 10"). Si son más de tres, los tres primeros y "y 2 más". Son
  exactamente los mismos renglones que se resaltan al abrirla (RF-026). Sin búsqueda de repuesto, se ve lo de RF-028.
- **RF-028** *(P2, 2026-09-16)* · **Sin buscar un repuesto**, debajo de cada factura se ven sus
  primeros repuestos vigentes, en el orden de la factura, con la cantidad: *"FILTRO DE ACEITE INOKI
  × 25 · PASTILLAS FRENO CBI × 4 · y 1 más"*. Hasta tres; si hay más, cuántos más. Una anulada muestra
  lo que traía. Al buscar un repuesto, la línea pasa a ser la de RF-027.

### Corregir los datos de la factura

- **RF-012** · En una compra vigente se corrigen proveedor, fecha de la factura, número, forma de
  pago y cuenta. **No se mueve el inventario.**
- **RF-013** · **Toda corrección y toda anulación exigen un motivo** y dejan un evento de auditoría
  con quién, cuándo, motivo, y el antes y el después.

### Corregir los renglones

- **RF-014** · La corrección se hace en **un solo paso**, sobre la pantalla de registrar compra ya
  cargada con la factura. Se puede cambiar cantidad, costo (en cualquiera de los dos modos), precio
  de venta y repuesto, y agregar o quitar renglones.
- **RF-015** · **Solo los renglones que cambian mueven inventario**: la entrada original de ese
  renglón se revierte y entra la corregida. Los que no cambian no generan movimientos. En el kardex
  del repuesto la reversión se ve como *Corrección* o *Anulación* de esa factura, con su motivo.
- **RF-016** · Stock y costo promedio quedan **como si la compra se hubiera registrado bien desde el
  principio**. Si al revertir el stock vuelve a cero, el costo promedio vuelve al que tenía antes de
  esa compra, o a desconocido ("—") si nunca se había comprado.
- **RF-017** · Precio de venta:
  - si un renglón que se revierte había cambiado el precio, el precio **vuelve al que tenía antes**
    de esa compra, salvo que una compra posterior ya lo haya cambiado otra vez: entonces se deja
    el vigente;
  - si el renglón corregido trae precio, se aplica ese.
- **RF-018** · La compra **conserva su número y su fecha de registro**. Ningún renglón se borra: el
  reemplazado queda como historia (RF-008).
- **RF-019** · **No se corrige un renglón si su repuesto tuvo alguna salida** (venta o ajuste)
  **después de esa compra**. Con costo promedio no se puede saber si esas unidades siguen en el
  estante. Hoy no hay ventas, pero la regla entra ya: el día que existan, no hay que acordarse de
  agregarla.
- **RF-020** · Una corrección no puede dejar la compra sin renglones: para eso está anular.
- **RF-021** · Un repuesto que nació con esa compra y se quita en la corrección **no se borra**:
  queda en el catálogo sin stock y con costo desconocido.

### Anular

- **RF-022** · Se anula la compra completa con un motivo. Todos sus renglones se revierten con las
  reglas de RF-016, RF-017 y RF-019. **Si uno solo no se puede, no se anula nada** y se dice cuál
  repuesto lo impide.
- **RF-023** · La compra anulada sigue en el historial, marcada con quién, cuándo y por qué. No
  cuenta en los totales, y no se puede corregir ni anular otra vez.

### Auditoría de la ficha

- **RF-024** *(P2)* · Corregir la ficha de un repuesto deja un evento de auditoría con el antes y el
  después (H5 del spec 0001).

---

## 6. Manejo de errores

| Situación | Qué pasa |
|---|---|
| Registrar sin forma de pago, o transferencia sin cuenta | No se registra. El campo se marca en pantalla y el backend lo rechaza igual |
| Llega una cuenta en una compra en efectivo | Se rechaza: una cuenta en efectivo es un dato contradictorio, no un detalle |
| Se elige una cuenta desactivada | No se ofrece en la pantalla; si llega igual, se rechaza |
| Corregir o anular sin motivo | No se guarda; se pide el motivo |
| Corregir un renglón cuyo repuesto tuvo ventas o ajustes después | No se permite. El mensaje dice qué repuesto, qué salida hubo y por qué no se puede |
| Anular una compra donde un solo renglón está bloqueado | No se anula nada; se dice cuál lo impide |
| Guardar una corrección sin cambios | No se registra nada: "No hay cambios que guardar" |
| La corrección deja la compra sin renglones | No se permite; se ofrece anular |
| La corrección repite un repuesto en dos renglones | Mismo aviso que al registrar (spec 0001) |
| Corregir o anular una compra ya anulada | "Esta compra ya fue anulada" |
| Dos personas corrigen la misma compra a la vez | La segunda no pisa a la primera: recibe "La compra cambió mientras la corregías" y la vuelve a abrir con lo nuevo |
| Se abre una compra que no existe | "Esa compra no existe", con enlace al historial |
| Se busca un repuesto que no está en ninguna compra | "Ninguna compra tiene un repuesto con «texto»", con *Limpiar filtros* |
| Se escribe `%` o `_` en la búsqueda | Se busca ese carácter tal cual, no como comodín que traiga todo |
| Sin conexión | Aviso ámbar con *Reintentar*, como en el inventario. Una corrección a medio enviar no queda aplicada a medias (ver §7) |

---

## 7. Requisitos no funcionales

- **Todo o nada.** Una corrección toca la compra, varios repuestos, el kardex y la auditoría. O se
  aplica completa o no se aplica nada: jamás un renglón revertido sin su entrada corregida.
- **Plata.** Totales son `Dinero` (COP entero); costos unitarios y promedios con 4 decimales. Los
  totales por forma de pago y cuenta se suman en la base.
- **Inventario.** El kardex sigue siendo solo de agregar: corregir es agregar movimientos, nunca
  editarlos. Mover stock bloquea la fila del repuesto, como al registrar.
- **Auditoría.** Nace aquí la tabla decidida en `SPEC_Modelo_Datos.md:444-454`. Límite declarado:
  **hasta la rebanada 2, "quién" es el usuario provisional** que manda el navegador.
- **Esquema.** Migración nueva (V3). **¿Funciona en una base que ya tiene filas?** Las compras que
  ya existen quedan como **Efectivo**. Sus renglones **no tienen precio anterior guardado**, así que
  al revertirlos el precio no puede volver atrás: se deja el vigente y la pantalla lo avisa. Solo
  afecta datos de prueba.
- **Fechas.** El historial filtra por **fecha de la factura**, porque eso es lo que se compró en el
  período. La fecha de registro y la de cada corrección quedan guardadas aparte.
- **Roles.** Registrar, corregir y anular compras es del administrador. Hoy no hay login; el
  bloqueo por rol llega con la rebanada 2 y va en el caso de uso, no en la pantalla.
- **Sin conexión.** Corregir y anular mueven stock. En la rebanada 4 necesitarán llave de
  idempotencia, igual que registrar. No aplica todavía.
- **Rendimiento.** El historial pagina. Buscar por repuesto con texto parcial no aprovecha índice,
  igual que el inventario hoy (`RepositorioVariantesJpa.java:91`); con el volumen de una tienda no se
  nota, y si algún día se nota se resuelve para los dos.
- **Tildes.** Java y Postgres quitan tildes con **la misma tabla de letras**: la del dominio, que la
  base usa en `translate()`. No se usa la extensión `unaccent`: pide permisos de superusuario y no
  coincide letra por letra con Java, y la lista y el detalle tienen que estar de acuerdo (RF-026).

---

## 8. Criterios de aceptación

**Forma de pago**
- [ ] No se registra una compra sin forma de pago, ni una transferencia sin cuenta
- [ ] Se crea una cuenta desde la pantalla de compra y aparece para la siguiente
- [ ] Una compra en efectivo y otra por transferencia desde "Nequi del dueño" se ven así en el
      historial
- [ ] Las compras registradas antes de la migración aparecen como Efectivo

**Historial**
- [ ] El historial filtra por proveedor, fechas de la factura, forma de pago y cuenta
- [ ] Buscar FV-9912 la encuentra y su detalle muestra todos los renglones
- [ ] El total del detalle cuadra al peso con la suma de sus renglones
- [ ] *(P2)* Los totales por forma de pago y por cuenta suman exactamente el total del período, sin
      las anuladas
- [ ] *(P2)* Buscar *inoki* deja solo las compras con ese repuesto; también lo encuentra por código,
      nombre y aplicación, y los totales son los de esas compras
- [ ] *(P2)* Una factura con dos renglones que coinciden sale una sola vez
- [ ] *(P2)* Buscar *bujia* encuentra *BUJÍA*, y *canción* encuentra *CANCION*, en el historial, el
      inventario y las sugerencias de concepto
- [ ] *(P2)* Si una corrección quitó el repuesto de una factura, buscarlo ya no la trae
- [ ] *(P2)* Al abrir la compra desde la búsqueda, están resaltados exactamente los renglones que
      coinciden (prueba contra Postgres: la lista y el detalle están de acuerdo)
- [ ] *(P2)* Buscando *aceite*, cada factura de la lista muestra debajo sus repuestos con aceite y la
      cantidad, y son los mismos que se resaltan al abrirla
- [ ] *(P2)* Una factura con cinco repuestos que coinciden muestra tres y "y 2 más"
- [ ] *(P2)* Sin buscar nada, cada factura muestra sus primeros tres repuestos con la cantidad y "y N
      más"; un renglón quitado en una corrección no sale

**Corregir**
- [ ] Una compra pasa de Efectivo a Transferencia con motivo; el detalle muestra quién, cuándo, por
      qué y qué decía antes, y **el kardex no cambia**
- [ ] Una compra registrada con 100 unidades se corrige a 10: **stock y costo promedio quedan
      idénticos** a los de un repuesto gemelo registrado con 10 desde el principio (el costo, con
      la tolerancia de redondeo del plan: el valor del repuesto difiere en menos de $1)
- [ ] En esa corrección, el kardex del repuesto muestra la entrada original, su reversión y la
      entrada corregida; **los demás repuestos de la factura no tienen movimientos nuevos**
- [ ] Corregir solo el costo de un renglón deja el promedio en el valor correcto
- [ ] Quitar un renglón que había cambiado el precio devuelve el precio anterior
- [ ] Hay prueba de que no se corrige un renglón cuyo repuesto tuvo una salida después de la compra
- [ ] Si falla un renglón a mitad de una corrección, no queda nada aplicado (prueba contra Postgres
      real)

**Anular**
- [ ] Una factura registrada dos veces se anula una vez y el stock y el promedio quedan como con
      una sola
- [ ] La anulada sigue en el historial, marcada, fuera de los totales, y no se puede corregir ni
      anular otra vez
- [ ] No se corrige ni se anula nada sin motivo

**Auditoría de la ficha**
- [ ] *(P2)* Corregir la ficha de un repuesto deja su evento con el antes y el después

**General**
- [ ] Prueba contra Postgres real de la migración sobre una base que ya tiene compras
- [ ] `./mvnw clean test` en verde

---

## 9. Qué no se toca

- **Registrar una compra no cambia cómo mueve el inventario**: stock, kardex, costo promedio y
  precio quedan igual. Solo gana la forma de pago y la cuenta. Sus pruebas siguen pasando.
- **Los movimientos de kardex ya escritos nunca se editan.** Corregir es agregar.
- **Un renglón de compra registrado no se borra ni se sobrescribe**: el reemplazado queda como
  historia.
- **La reversión de venta sigue sin tocar el costo promedio.** La de compra es otra cosa (§3).
- La migración V1 no se edita.

---

## 10. Fuera de alcance

| Qué | Por qué |
|---|---|
| **Compras a crédito y cuentas por pagar** | Pendiente de consultarlo con el cliente. Contexto verificado: Jotapartes despacha **a crédito si no se marca otra cosa** (*"Crédito a 55 días sin descuento… si no señala alguna opción se enviará a crédito sin descuento"*, `docs/LISTA DE PRECIOS IMPORTADORA JOTAPARTES 13 AGOSTO 2026-ruben h2o.pdf`, pág. 1). Mientras tanto, esas facturas se registran con la forma de pago prevista y se corrigen si cambia (§4) |
| "De caja" y enlazar el efectivo al turno | Cuando exista la caja. Hasta entonces Efectivo hace ese papel. En el car-wash solo lo pagado de caja resta del arqueo y exige turno abierto; esa regla se decide en el spec de caja |
| Corregir un renglón de un repuesto que ya tuvo ventas | Necesita un **ajuste de inventario**, que todavía no existe. RF-019 lo bloquea y lo dice |
| Borrar una compra | Nunca. Se anula |
| Exportar el historial a PDF o Excel | Rebanada 3 |
| Ver "cuánto fue de ese repuesto" en la lista o en los totales | Decidido en la revisión: se abre la factura y se ve resaltado (§4) |
| Un enlace desde la ficha del repuesto al historial ya filtrado | No se pidió. Se puede sumar después sin cambiar nada de lo anterior |

---

## 11. Riesgos

| Riesgo | Qué hacer |
|---|---|
| Revertir un promedio acumula diferencias de redondeo con 4 decimales | El plan fija la tolerancia y la prueba que la vigila, antes de escribir la reversión |
| Mientras no se decida el crédito, una factura a crédito queda registrada con una forma de pago que todavía no ocurrió, y los totales del período la cuentan como pagada | Aceptado en la revisión (§4). Se corrige con H3 cuando se pague. El día que entre el crédito, estas facturas son las que habrá que revisar |
| La auditoría dice "quién" con un usuario provisional | Declarado en §7. Con el login de la rebanada 2 se vuelve confiable desde ese día en adelante; lo anterior sigue siendo provisional |

---

## 12. Preguntas abiertas

**Ninguna.** Las cuatro de la segunda revisión quedaron resueltas en §4. El spec está cerrado y
listo para el plan.
