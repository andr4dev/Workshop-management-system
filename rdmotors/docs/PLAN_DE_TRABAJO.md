# Plan de trabajo — RD MOTORS

> El documento que se abre cuando uno se siente perdido. Dice **dónde estamos**, **cómo se
> trabaja** y **qué sigue**. Se actualiza al cerrar cada rebanada.

Última actualización: 2026-09-20 — spec 0004 (usuarios, entrada y roles) implementado: las 5 fases; arranca el spec de venta

---

## 1. Dónde estamos hoy

| | |
|---|---|
| Módulos Maven | `domain` + `pos` (el de `cloud` **ya no se va a construir**: ver rebanada 5) |
| **Dónde corre** | **En la nube**, desde el 2026-09-23 · https://workshop-management-system-1-lujm.onrender.com · Render + Neon, gratis ([`DESPLIEGUE.md`](DESPLIEGUE.md)) |
| Tablas | **15 de 21** del modelo de datos (más `datos_tienda`) |
| Casos de uso | **13** — compras (registrar, consultar, corregir, anular), proveedores, cuentas (registrar, desactivar), repuestos (crear, buscar, corregir), abrir turno, cobrar, anular y consultar ventas, datos de la tienda |
| Endpoints | **30** |
| Pruebas | **333** de dominio · **75** contra Postgres real · **206** del frontend |
| Frontend | **Vender** (abrir turno, buscar por teclado, descuento, cobro en efectivo, transferencia o mixto, retomar una venta interrumpida, catálogo por categorías con F2, comprobante de 80 mm que se imprime al cobrar, ventas del turno, reimprimir y anular con motivo), **Datos de la tienda**, **Compras** (registrar, historial, detalle, corregir) e **Inventario** (listado y ficha con kardex) |

**Rebanada 1 cerrada.** [Spec 0001](specs/0001-catalogo-y-compras-usables/spec.md): se registra
una factura desde el navegador creando proveedor y repuestos sobre la marcha, el stock sube, el
costo promedio se recalcula, y el inventario y el kardex se consultan desde su pantalla. Verificado
con una factura real.

[Spec 0002](specs/0002-compras-fuente-de-pago-e-historial/spec.md), cerrado con su QA manual: forma
de pago (efectivo o transferencia con su cuenta), historial con totales, corregir o anular una
compra con auditoría, rastro de las correcciones de ficha, y buscar las compras de un repuesto.
Todos los buscadores ignoran tildes. Las compras a crédito quedan fuera hasta consultarlo con el
cliente.

**En curso — rebanada 2:** [spec 0003](specs/0003-venta-de-mostrador/spec.md), venta de mostrador y
comprobante. Spec cerrado y [plan](specs/0003-venta-de-mostrador/plan.md) aprobado: 5 fases
(turno, cobrar en el backend, pantalla de venta, comprobante y ventas del turno, anular); **la 1 (turno
de caja), la 2 (cobrar en el backend) y la 3 (pantalla de venta y cobro), la 4 (comprobante, datos de la tienda y ventas del turno) y la 5
(anular una venta) completas el 2026-09-14**. Falta, para cerrar el spec: probar con la ticketera real
([guía](INSTALAR_TICKETERA.md)) y la verificación manual del usuario.

**Orden acordado el 2026-09-15**, tras la revisión del usuario:
1. ✅ [Spec 0002, fase 8](specs/0002-compras-fuente-de-pago-e-historial/plan.md) (2026-09-15): la lista del
   historial dice qué repuesto trae cada factura, y el aviso de venta a pérdida con un texto que se entiende.
2. ✅ [Spec 0005](specs/0005-catalogo-en-el-mostrador/spec.md): ver el catálogo en el mostrador (F2, por
   categorías) y categoría obligatoria al crear un repuesto. Spec cerrado y
   [plan](specs/0005-catalogo-en-el-mostrador/plan.md) aprobado el 2026-09-15: 3 fases. **Las 3 fases completas
   el 2026-09-15**; falta la verificación manual del usuario.
3. [Spec 0006](specs/0006-cierre-de-caja/spec.md): cierre de caja con arqueo a ciegas, gastos (del cajón y
   por fuera, con categorías de costo o gasto) y retiros; se adelanta de la rebanada 3. Spec cerrado y
   [plan](specs/0006-cierre-de-caja/plan.md) aprobado el 2026-09-16: **las 5 fases implementadas el 2026-09-16**; falta el recorrido en el navegador y la verificación manual. Antes, dos
   cambios chicos ya especificados: la fase 9 del spec 0002 (sin buscar, cada factura del historial dice
   qué trae) y la venta a medias que se retoma sola (spec 0003, RF-028 revisado).
4. [Spec 0007](specs/0007-reportes-de-resultados/spec.md) — **reportes de resultados**: ganancia, utilidad
   bruta y operativa por día, semana, mes o rango, con memoria de cálculo, día por día, repuestos que dejan
   plata y control. Spec cerrado y [plan](specs/0007-reportes-de-resultados/plan.md) aprobado el 2026-09-16:
   **las 6 fases hechas el 2026-09-17** (*Reportes › Resultados*; gastos marcados "del
   mes", repartidos por día o solo en el mes). **La base de QA se limpió el 2026-09-17** para empezar de cero,
   con respaldo en `respaldos/qa-2026-09-17.sql`. Falta la verificación manual del usuario.
5. ✅ [Spec 0004](specs/0004-usuarios-y-roles/spec.md) — **usuarios, entrada y roles**: se entra con usuario y
   contraseña (token firmado de 24 h en una cookie que la página no lee), el cajero no ve costos ni compras ni
   reportes, el turno es de quien lo abrió, el administrador administra usuarios, y cada detalle dice el nombre de
   quién hizo cada cosa. [Plan](specs/0004-usuarios-y-roles/plan.md) aprobado el 2026-09-19: **las 5 fases hechas
   entre el 2026-09-19 y el 2026-09-20**. Falta el QA del usuario: QA quedó sin administrador a propósito, así que
   la primera pantalla ofrece crearlo.
6. ✅ [Spec 0008](specs/0008-fiado-a-clientes/spec.md) — **fiado a clientes y cartera**: se fía en el cobro (con
   nombre, cédula y celular obligatorios: sin eso no se cierra la venta), hay un módulo *Cartera* al estilo del
   car-wash con quién debe, cuánto y desde cuándo, la ficha de cada cliente con sus ventas y sus abonos, abonos
   libres que se aplican a lo más viejo o a una venta escogida, el saldo del cuaderno de antes del sistema, y el
   fiado y lo cobrado en los reportes. [Plan](specs/0008-fiado-a-clientes/plan.md) aprobado el 2026-09-21:
   **las 5 fases hechas el 2026-09-21** (V20 y V21). Falta el QA del usuario.
7. ✅ [Spec 0009](specs/0009-sin-internet-y-respaldo/spec.md) — **sin internet y respaldo**: se dejó **probado**
   que sin internet la tienda trabaja igual ([SIN_INTERNET.md](SIN_INTERNET.md)), se construyó el **respaldo
   automático** de la base con su pantalla, su aviso cuando falla y su guía para restaurar
   ([RESPALDO_Y_RESTAURAR.md](RESPALDO_Y_RESTAURAR.md)), y registrar una compra ya lleva **llave contra el doble
   clic**. [Plan](specs/0009-sin-internet-y-respaldo/plan.md) aprobado el 2026-09-21: **las 3 fases hechas el
   2026-09-21** (V22 y V23). **La bandeja hacia la nube (H3–H5) queda para la rebanada 5**, con su panel: eventos
   que nadie consume son una forma que nadie probó contra el otro lado. Falta el QA del usuario.
8. ✅ [Spec 0010](specs/0010-correo-del-cierre/spec.md) — **el correo del cierre de caja**, pedido el 2026-09-22
   (*«como en el car-wash»*): al cerrar un turno, el resumen —producido, formas de pago, cartera y el cajón— sale
   por correo al dueño con la API de Brevo. **A diferencia del car-wash, con cola**: el cierre deja el correo en su
   mismo commit y una tarea lo manda reintentando, para que un corte de internet no lo pierda (V24).
   [Plan](specs/0010-correo-del-cierre/plan.md) · **implementado el 2026-09-22**. Falta que el dueño cree la cuenta
   de Brevo, ponga `BREVO_API_KEY` y escriba los destinatarios en *Ajustes › Correos*.

---

## 2. Cómo se trabaja

### Rebanadas verticales, no capas

No se hace "todo el backend y luego el frontend". Cada rebanada lleva **backend + frontend juntos**
y termina en algo que se le puede **enseñar al cliente**.

El motivo es concreto: un punto de venta no se puede probar sin pantalla. Una API perfecta no dice
si el cajero atiende en 30 segundos con el cliente enfrente. Y diseñar los endpoints adivinando qué
necesita la pantalla garantiza reescribirlos después.

La excepción: el outbox y Testcontainers son infraestructura sin pantalla propia. Se construyen **dentro de la rebanada que los necesita**, nunca como fase aparte.

### Antes de tocar código: spec, luego plan

Lo dicta la skill `spec`, y tiene **dos paradas en firme**:

```
1. spec.md   →  se entrega y SE PARA.   El usuario lo revisa y lo corrige.
2. plan.md   →  solo cuando el spec ya está como el usuario lo quiere.
3. código    →  la decisión de arrancar también es del usuario.
```

Los specs viven en [`docs/specs/NNNN-slug/`](specs/) y cada uno lleva su línea en el índice.

### Las dos clases de QA

| Tipo | Con qué | Cuándo sirve |
|---|---|---|
| **De contrato** | archivos `.http`, Postman | desde la rebanada 1 |
| **De producto** | la pantalla real | obligatorio desde la rebanada 2 |

En la rebanada 1 el QA de contrato alcanza: quien registra compras es el admin, sin prisa. En la
rebanada 2 ya hay un cliente esperando en el mostrador y la pantalla es el producto.

---

## 3. Las rebanadas

### Rebanada 1 — Catálogo y compras usables

> **Demo:** registras una compra real de la lista de Jotapartes y ves subir el stock.

**Backend**
- Crear y listar proveedores
- Crear producto + variante — el spec decidió que **nacen en la compra**
- Buscar variantes por código y por nombre
- Consultar el kardex de un repuesto
- `producto_proveedor` — decidido: Jotapartes no es el único proveedor
- **Testcontainers** y la primera prueba de integración contra Postgres real

**Frontend**
- Crear el proyecto React + Vite
- Pantalla de compra: los dos modos de captura, margen en vivo por línea
- Pantalla de inventario: listado, búsqueda, ficha del repuesto con su kardex

**Ya decidido** — `SPEC_Modelo_Datos.md` §3.1, §3.4, §6.1
**Por decidir** — si el producto se crea en un formulario aparte, inline en la compra, o ambos

---

### Rebanada 2 — Venta de mostrador

> **Demo:** vendes dos repuestos, sale el ticket impreso y el stock baja.

**Backend**
- `usuario` con roles ADMIN/CAJERO y login — la venta tiene que saber quién vendió
- Apertura de `sesion_caja` — sin caja abierta no hay venta
- Cobro en efectivo o transferencia, o mixto — sin catálogo de medios de pago ni cuenta (spec 0003, decisión 2)
- `RegistrarVenta`: bloqueo de fila, kardex, pagos mixtos, descuento con motivo
- `evento_auditoria` y la anulación con su rastro
- Consecutivo del ticket
- Ticket impreso **desde el navegador del computador del mostrador**, en modo kiosco, como el car-wash (spec 0003, decisión 3)

**Frontend**
- Pantalla del cajero, **primero teclado**: foco que vuelve solo, Enter agrega, sin depender del mouse
- Búsqueda en cascada: código de barras → código interno → código proveedor → texto libre
- Carrito con borrador persistido en `localStorage` (la venta interrumpida del spec; IndexedDB llega con la cola sin internet, plan 0003 decisión 9)
- Modal de cobro: mixto, descuento por monto o porcentaje con motivo obligatorio

**Ya decidido** — §3.5, §3.6, §3.7
**Por decidir** — nada pendiente

---

### Rebanada 3 — Cierre de caja, gastos y reportes

> **Demo:** cierras el turno, el arqueo cuadra, y ves la ganancia del día.

> **Actualizado el 2026-09-16:** el cierre, los gastos y los retiros se adelantaron al spec 0006 (gasto y
> retiro quedan en tablas separadas, no en `movimiento_caja`); los reportes van en el spec 0007.

**Backend**
- Cierre de `sesion_caja` con conteo físico y diferencia snapshoteada
- `movimiento_caja`: gastos y retiros, con `categoria_gasto` (el flete entra aquí)
- Ajustes de inventario — solo ADMIN, con motivo
- Reportes: ventas del día, ganancia bruta vs neta, productos más vendidos, stock bajo

**Frontend**
- Pantalla de cierre con el arqueo
- Registro de gastos y retiros
- Reportes en pantalla

**Ya decidido** — §3.6, §3.7
**Por decidir** — exportación a PDF/Excel es P2; puede quedar fuera de esta rebanada

---

### Rebanada 4 — Operación sin internet · **hecha el 2026-09-21** ([spec 0009](specs/0009-sin-internet-y-respaldo/spec.md))

> **Demo (la de entonces):** desconectas el cable de internet y la tienda trabaja igual; al día siguiente hay un
> respaldo nuevo.

> ⚠️ **Dos días después, el spec 0011 le dio la vuelta a la premisa de esta rebanada.** El sistema se mudó a la
> nube: ya **no** se vende sin internet, y el respaldo automático de madrugada se eliminó —el disco del servidor
> se borra en cada reinicio— y lo reemplazó una copia que el administrador baja cuando quiere. Lo de abajo se
> conserva porque explica el código que quedó; ver [`SIN_INTERNET.md`](SIN_INTERNET.md) para lo que es cierto hoy.

La rebanada se replanteó al investigarla, y el spec 0009 lo dejó por escrito: **esta rebanada se pensó para el
car-wash, que vivía en la nube**. Aquí el servidor y la base están en el computador de la tienda, así que
*"seguir vendiendo sin internet"* no había que construirlo — ya era así. Lo que sí faltaba, y se hizo:

**Backend**
- ✅ Idempotencia en todo lo que **suma**: faltaba compras (V22), y ya la tenían venta, gasto, retiro y abono
- ✅ **Respaldo automático** de la base (V23): `pg_dump` cada madrugada, 14 días de copias, segunda copia
  opcional en USB, la clave de sesiones al lado, y la fila con el error cuando falla
  — ⚠️ **eliminado por el spec 0011** (V25): ahora la copia se baja, no se guarda
- ❌ `evento_outbox` con secuencia monotónica: **ya no se va a construir.** Existía para alimentar el espejo de la
  rebanada 5, y al mudarse todo a la nube no hay nada que replicar

**Frontend**
- ✅ Pantalla *Ajustes › Respaldo* y el aviso al entrar cuando el respaldo falla
- ✅ Una prueba que vigila que **nada de la pantalla se descargue de internet** (`utils/sinInternet.test.js`)
- ❌ Portar `src/offline/` del car-wash: **descartado** con su porqué en el spec 0009 §4. Vender desde un celular
  con el Wi-Fi caído sería vender sin que el servidor revise stock, precio ni turno, y el cliente definió un solo
  dispositivo activo a la vez. Si se va la luz, la respuesta es una UPS, no software

**Ya decidido** — §3.9 · ~~**Por decidir** — si la tienda tendrá memoria USB o disco externo~~ (sin efecto desde
el spec 0011: no hay segunda copia que guardar)

---

### Rebanada 5 — Panel del propietario · **se disolvió, y eso es una buena noticia**

Esta rebanada existía para resolver un problema que **ya no existe**: cómo hacía el dueño para ver su negocio
desde la casa, si el sistema vivía encerrado en el computador del almacén. La respuesta iba a ser un espejo de
solo lectura en la nube, con su módulo Maven, su consumidor de eventos, su login aparte y su letrero de "última
actualización".

Desde el [spec 0011](specs/0011-la-tienda-en-la-nube/spec.md) (2026-09-23) **el sistema entero vive en la nube**.
El dueño entra desde su celular, en cualquier parte, al sistema de verdad — no a un espejo con retraso. Así que:

| Lo que se iba a construir | Qué pasó |
|---|---|
| Módulo Maven `cloud` | **No hace falta.** Ya no hay dos sistemas que sincronizar |
| Consumidor de eventos, `evento_outbox`, secuencia monotónica | **No hace falta.** No hay nada que replicar |
| `usuario_propietario` aparte | **No hace falta**: el dueño tiene su usuario normal, con su rol |
| Letrero de "última actualización" | **No hace falta**: no hay retraso que avisar |
| Panel móvil de solo lectura | **Ya existe**: la pantalla es responsive y funciona en el celular |
| Hosting (VPS, dominio propio) | Resuelto: Render + Neon, **gratis**, sin dominio propio por ahora |

Lo que quedó **abierto** y algún día habrá que decidir: si el dueño debe ver algo distinto de lo que ve un
administrador. Hoy ve exactamente lo mismo, y para un almacén de una sola tienda eso alcanza.

> Se conserva escrito lo que se iba a hacer porque explica decisiones que siguen en el código — los UUID, las
> llaves de idempotencia, que el pago en línea quedara fuera del arqueo físico. Esas siguen siendo correctas por
> otras razones.

---

## 4. Deuda conocida

Cosas **ya decididas** en el spec que el código todavía no tiene:

| Qué | Dónde se decidió | Entra en |
|---|---|---|
| El producto nace en la compra | §3.4 | rebanada 1 |
| `producto_proveedor` | §3.4, §9.1 | rebanada 1 |
| Compatibilidad vehicular normalizada (~250 modelos) | §3.2 | P2 — ver abajo |

Y deuda de proceso: **el módulo de compras se construyó sin spec ni plan propios.** No se le escribe
spec retroactivo — sería papeleo. Queda anotado en [`docs/specs/README.md`](specs/README.md).

---

## 5. Fuera de alcance por ahora

| Qué | Estado |
|---|---|
| Compatibilidad vehicular normalizada | P2 — hoy se guarda `aplicacion_original` como texto y se busca así. Normalizar son ~250 modelos a curar: es un proyecto de datos, no de código |
| Devoluciones de cliente | diferido — se modela al construir ventas. **Regla**: no asumir en ninguna parte que el stock solo entra por compras |
| Crédito / fiado a clientes | fase 2, confirmado con el cliente |
| Código de barras | el campo `codigo_barras` ya está puesto; imprimir etiquetas propias es futuro |
| Multi-sucursal | no va |
| Pasarela de pago | no va, ni en fase 2 |

---

## 6. Lo siguiente, concreto

1. ✅ **Dos cambios chicos** (2026-09-19): la fase 9 del spec 0002 (sin buscar, cada factura dice qué trae) y
   la venta a medias que se retoma sola (spec 0003, RF-028). Quedan para el QA general del usuario.
2. **Verificar el [spec 0006](specs/0006-cierre-de-caja/plan.md)** — implementado; falta recorrerlo en el
   navegador contra QA, que ya está limpia (abrir turno, gastos, retiros, cerrar, turnos anteriores).
3. **Verificar el [spec 0007](specs/0007-reportes-de-resultados/spec.md)** — implementado y con QA limpia: registrar compras, ventas y gastos reales de prueba y leer *Reportes › Resultados*.
4. **[Spec 0004](specs/0004-usuarios-y-roles/spec.md) — usuarios, entrada y roles.** Spec cerrado y [plan](specs/0004-usuarios-y-roles/plan.md) de 5 fases el 2026-09-19 (Spring Security, contraseñas con hash, token firmado de 24 h en cookie, el cajero sin costos, el turno con dueño). Ninguna fase empezada. Tiene que estar hecho antes de usar las ventas en la tienda.
5. Pendiente del usuario: la verificación manual de los specs 0003 y 0005, y probar la ticketera real.
