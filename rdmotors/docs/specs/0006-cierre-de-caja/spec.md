# Spec 0006 — Cierre de caja, gastos y retiros

**Estado:** cerrado el 2026-09-16 · las 5 decisiones resueltas · [plan](plan.md) aprobado e **implementado (5 de 5 fases) el 2026-09-16** · falta la verificación manual
**Rebanada:** se adelanta de la 3 · ver [`PLAN_DE_TRABAJO.md`](../../PLAN_DE_TRABAJO.md)
**Depende de:** [spec 0003](../0003-venta-de-mostrador/spec.md) (turno de caja, ventas y anulaciones), implementado
**Va antes de:** el spec 0007 (reportes) y el 0004 (usuarios y login). Los tres, antes de usar el sistema en la tienda.

> **Convención:** lo que dice *"hoy"* está verificado con `archivo:línea`. Lo que dice *"debe"* es
> propuesta y todavía no existe. Las dos cosas nunca se mezclan.

---

## 1. Objetivo de negocio

Hoy el turno se abre y **nunca se cierra**. Nadie sabe al final del día si la plata del cajón cuadra
con lo que se vendió, y lo que sale del cajón durante el día —el flete, el almuerzo, lo que se lleva
el dueño— no queda en ninguna parte.

El cliente lo pidió en su cuestionario: apertura y cierre de caja, que cierren el cajero y el
administrador, y registrar gastos y retiros (`SPEC_Sistema_Ventas_Repuestos (3).md:112-114`). Y lo
puso como P1: *"Cierre de caja diario"* (`:238`). Además: *"Cajero responde por su turno"* (`:40`).

Lo que resuelve:

- **Al cerrar, se sabe si falta o sobra plata**, cuánto y quién respondía por el cajón.
- **Cada peso que sale del cajón tiene una razón**: un gasto con su categoría, un retiro con su
  motivo, o una compra pagada con esa plata.
- El dueño ve después cada cierre, con su diferencia y su explicación.

---

## 2. Caso de uso

**Ejemplo que atraviesa todo el spec.** El turno abre a las 8 a. m. con $100.000 de fondo. En el día:

- se venden $223.400 en efectivo (y $111.000 por transferencia, que no entran al cajón);
- se anula una venta de ayer pagada en efectivo: se le devuelven $70.000 al cliente;
- se paga un flete de $15.000 con plata del cajón;
- el dueño se lleva $100.000 a mediodía;
- se paga en efectivo una compra de $50.000 a Jotapartes con plata del cajón.

En el cajón debería haber **$88.400**. El cajero cuenta $87.000: **faltan $1.400**.

### P1 — bloquean

**H1 · Registrar un gasto.**
Llega el flete. El cajero lo paga con plata del cajón y lo registra: *Transporte y fletes*, $15.000,
*"Flete Jotapartes FV-9912"*. Queda en su turno, con quién y cuándo.
*Se demuestra solo:* se registra el flete y aparece en los movimientos del turno; al cerrar, resta
$15.000 de lo que debería haber.

**H2 · Registrar un retiro.**
El dueño se lleva $100.000. El cajero lo registra como retiro con su motivo: *"Se lo llevó don Rubén"*.
No es un gasto: no es plata que se gastó, es plata que salió del cajón.
*Se demuestra solo:* el retiro aparece en el turno y resta al cerrar; no aparece entre los gastos.

**H3 · Cerrar el turno.**
Al final del día el cajero cuenta los billetes y monedas y escribe cuánto hay: $87.000. **No ve antes
cuánto debería haber** (arqueo a ciegas; *cambió el 2026-09-16: ahora se ve en vivo, ver §4*). Al confirmar, el turno se cierra y la pantalla muestra:
debería haber $88.400, contaste $87.000, **faltan $1.400**, con el detalle de dónde sale cada cifra. Si
no cuadra, se le pide que explique la diferencia.
*Se demuestra solo:* con el ejemplo, el cierre muestra $88.400 esperados, $87.000 contados y un
faltante de $1.400; después de cerrar no se puede vender hasta abrir otro turno.

**H4 · Una compra pagada con plata del cajón.**
Llega Jotapartes con una factura de $50.000 y se le paga con billetes del cajón. Al registrar la
compra en efectivo, se marca *"Se pagó con plata del cajón"*: esa compra resta del turno abierto.
*Se demuestra solo:* una compra en efectivo marcada así resta $50.000 al cerrar; otra en efectivo sin
marcar (la pagó el dueño de su bolsillo) no resta nada.

**H10 · Un gasto que no sale del cajón** *(propuesta del 2026-09-16, decisión 5)*.
El arriendo se paga por transferencia desde Nequi, y la luz la paga el dueño de su bolsillo. No tocan
el cajón, pero son gastos del negocio: sin ellos, la utilidad neta del reporte sale inflada. Se
registran igual, con su categoría, diciendo que no salieron del cajón.
*Se demuestra solo:* el arriendo de $800.000 por transferencia desde "Nequi del dueño" queda en los
gastos del mes y no cambia lo que debería haber en el cajón.

**H11 · Categorías de gasto propias.**
Vienen unas por defecto. El dueño crea las que le hagan falta (*"Publicidad"*) y dice una sola vez si
son **costo** o **gasto**; al registrar un gasto solo se elige la categoría.
*Se demuestra solo:* se crea *Publicidad* como gasto y aparece para elegir; una categoría con gastos
no se borra, se desactiva.

### P2 — importantes, no bloquean

**H5 · Ver los cierres anteriores.**
El dueño revisa la semana: cada turno con quién lo abrió y lo cerró, cuánto debería haber, cuánto se
contó y la diferencia. Los faltantes resaltan. Abre uno y ve el detalle y la explicación.
*Se demuestra solo:* el cierre del ejemplo aparece en la lista con un faltante de $1.400 en rojo, y su
detalle muestra la nota.

**H6 · El comprobante del cierre.**
Al cerrar, sale por la ticketera un comprobante de 80 mm con el resumen del turno, que se guarda con
la plata. Se puede reimprimir desde el historial.
*Se demuestra solo:* el comprobante del ejemplo muestra fondo, ventas en efectivo y transferencia,
anulaciones, gastos, retiros, compras de caja, lo que debería haber, lo contado y la diferencia, y
sus cifras cuadran.

**H7 · Anular un gasto o un retiro mal registrado.**
Se escribió $150.000 en vez de $15.000. Mientras el turno siga abierto, se anula con motivo y se
registra bien. Queda el rastro.
*Se demuestra solo:* el gasto anulado sigue en la lista, tachado, con su motivo, y no resta al cerrar.

### P3 — deseables

**H8 · Contar por billetes.** El cajero escribe cuántos billetes hay de cada denominación y el total
sale solo. Ayuda a no equivocarse sumando.

**H9 · Fondo sugerido.** Al abrir el turno siguiente se sugiere como fondo el del turno anterior.

---

## 3. Qué existe hoy

| Qué | Dónde | Estado |
|---|---|---|
| El turno se abre con fondo; solo uno abierto, garantizado por índice único | `caja/dominio/TurnoCaja.java` · `V8__turno_de_caja.sql` (`ux_turno_abierto`) | **funciona** |
| La tabla del turno ya tiene quién y cuándo cerró, vacíos, con su `CHECK` | `V8__turno_de_caja.sql:18-23` | **existe**; no tiene esperado, contado, diferencia ni nota |
| No hay forma de cerrar: ni caso de uso ni endpoint | `caja/infraestructura/TurnoController.java` (solo abrir y consultar el abierto) | **no existe** |
| Sin turno abierto no se vende ni se anula | `ventas/aplicacion/CobrarVenta.java:86` · `AnularVenta.java:70` | **funciona** |
| Cada venta apunta a su turno; cobrar lee el turno abierto **sin bloquearlo** | `CobrarVenta.java:86` · `RepositorioVentasJpa.java:87-98` | funciona; **choca con el cierre** (ver abajo) |
| Una venta anulada guarda **en qué turno** se anuló, que puede no ser el suyo | `ventas/dominio/Venta.java:231-243` · `V9__venta.sql` (`anulada_en_turno_id`) | **funciona**: es lo que deja restar la devolución en el turno de hoy |
| Solo el efectivo entra al cajón | `compartido/dominio/FormaPago.java:22-24` (`entraAlCajon`) | **funciona** |
| Ventas del turno con total, efectivo y transferencia; las anuladas no suman | `frontend/src/utils/ventasDelTurno.js:17` · `paginas/Ventas.jsx:97` | funciona; **no mira las anulaciones de ventas de otros turnos** |
| Una compra se paga en efectivo o por transferencia, **sin decir si salió del cajón** | `V3__forma_de_pago.sql:20-34` | funciona; el spec 0002 dejó esa pregunta para este spec (`0002/spec.md:438`) |
| Imprimir un documento de 80 mm por la ticketera | `frontend/src/utils/imprimir.js:39` · `utils/ticket.js` | **funciona**: se reusa para el comprobante del cierre |
| La auditoría tiene acciones para compras, ficha, descuento y anulación de venta | `compartido/dominio/AccionAuditada.java` · `V9__venta.sql:101-102` | funciona; **no tiene las de caja** |
| El modelo de datos ya decidió la caja por turno, `esperado = fondo + efectivo cobrado − gastos y retiros`, las tablas de movimientos y de categorías de gasto, y la acción *cerrar caja con diferencia* | `SPEC_Modelo_Datos.md:407-453` y `:455-466` | **decidido**, no construido |
| El flete es gasto, no costo de inventario | `SPEC_Modelo_Datos.md:444-450` | **decidido** |

### Lo que hace el car-wash

| Qué | Dónde |
|---|---|
| El esperado es fondo + cobros en efectivo − pagos a operarios − gastos − compras pagadas de caja | `backend/.../service/CashSessionBalanceService.java` (`expectedCashInDrawer`) |
| El cierre bloquea la fila del turno para que un cobro que llega a la vez no se pierda del arqueo | `backend/.../service/impl/CashSessionServiceImpl.java:164-175` |
| **Muestra el esperado antes de contar**: el cajero ve la meta y puede acomodar el conteo | `frontend/src/components/cash/CloseCashModal.jsx:43-59` |
| Solo lo pagado "de caja" resta del arqueo | `CashSessionBalanceService.java` (compras con fuente `CASH_REGISTER`) |

### Con qué choca

- **Con el cobro.** Hoy cobrar lee el turno abierto y sigue sin bloquearlo. Si el cierre calcula lo
  que debería haber mientras entra un cobro, ese cobro puede quedar en un turno ya cerrado y fuera de
  su arqueo. **El cierre y el cobro tienen que ponerse en fila.**
- **Con las anulaciones de otros días.** Lo que muestra *Ventas del turno* no alcanza para el arqueo:
  una venta de ayer anulada hoy devuelve efectivo **hoy**, y no está en la lista de hoy.
- **Con las compras en efectivo.** Hoy "Efectivo" no dice si la plata salió del cajón o del bolsillo
  del dueño. Sin eso, el arqueo no cuadra o se inventa (decisión 1).
- **Con los roles, que todavía no existen.** Un arqueo a ciegas solo protege si el cajero no puede ver
  lo que debería haber. Hoy cualquiera ve *Ventas del turno*, que muestra el efectivo vendido. Hasta
  el spec 0004, el arqueo a ciegas evita que se vea la cifra final pero no impide calcularla.
- **Con los datos de QA.** El turno de prueba está abierto desde el 14 de septiembre con catorce
  ventas. Su primer cierre será el primero de todos.

---

## 4. Las decisiones

### Resueltas con el usuario

| Qué | Resolución |
|---|---|
| Qué entra (2026-09-15) | Cierre con **arqueo a ciegas**, gastos y retiros. Ajustes de inventario y reportes de ganancia siguen en la rebanada 3 |
| Cuándo (2026-09-15) | Después del catálogo y **antes del login** (spec 0004); los dos antes de usar el sistema en la tienda |
| Quién cierra (cuestionario del cliente) | El cajero y el administrador (`SPEC_Sistema_Ventas_Repuestos (3).md:113`) |
| Compras en efectivo (2026-09-16) | **Decisión 1 → A**: al registrarla se marca si salió del cajón |
| Conteo que no cuadra (2026-09-16) | **Decisión 2 → A**: se cierra igual y se escriben las observaciones |
| Categorías de gasto (2026-09-16) | **Unas por defecto, y el cliente crea las que quiera** (decisión 4) |
| Retiro o gasto mayor que lo que debería haber (2026-09-16) | **Decisión 3 → A**: se pide confirmar antes de registrarlo, sin decir la cifra |
| Gastos que no salen del cajón (2026-09-16) | **Decisión 5 → A**: entran en este spec |
| **Lo que debería haber, en vivo** (2026-09-16, después de usarlo) | **Deja de ser a ciegas.** La sección Caja muestra todo el turno lo que debería haber y su desglose (RF-011); al cerrar, la diferencia se ve mientras se escribe lo contado y antes de confirmar. Reemplaza lo decidido el 2026-09-15. Con el login (spec 0004) se puede esconder solo al cajero |
| **La lista de gastos va en Reportes** (2026-09-16, después de usarlo) | *Reportes › Gastos* consulta, anula y administra categorías. Los gastos se registran en *Vender › Caja*, con un botón que abre el modal |
| Orden (2026-09-16) | **Caja (este spec) → reportes (0007) → login (0004)**, los tres antes de usar el sistema en la tienda. Reportes va después porque la utilidad neta necesita los gastos que nacen aquí |

### Decisión 1 · Una compra pagada en efectivo, ¿sale del cajón? — [RESUELTO] A (2026-09-16)

| Opción | Qué implica |
|---|---|
| **A. Al registrar una compra en efectivo, se marca si se pagó con plata del cajón** | Solo esas restan del turno abierto, y exigen que haya uno. Las demás (el dueño paga de su bolsillo o de la caja fuerte) no tocan el arqueo. Es lo que hace el car-wash |
| B. Ninguna compra toca el cajón | Si se paga con plata del cajón, se registra además un retiro a mano. Se hace dos veces, y el día que se olvide el arqueo da un faltante que no existe |
| C. Toda compra en efectivo sale del cajón | Cuando el dueño paga de su bolsillo, el arqueo da un sobrante falso |

**Recomendación: A.** Es una casilla más, solo en efectivo, y es la única que no inventa diferencias.

### Decisión 2 · Si el conteo no cuadra — [RESUELTO] A, con observaciones (2026-09-16)

| Opción | Qué implica |
|---|---|
| **A. El conteo cierra el turno; después se muestra la diferencia y se pide la explicación** | El cajero no puede ver lo que debería haber y ajustar su conteo. Si se equivocó contando, lo dice en la nota; el dueño lo ve |
| B. Mostrar la diferencia y dejar recontar antes de cerrar | Más cómodo, pero tras ver la cifra "recontar" es escribirla: el arqueo a ciegas deja de servir |
| C. Mostrar lo que debería haber desde el principio (como el car-wash) | Es lo que el usuario descartó al pedir arqueo a ciegas |

**Recomendación: A.**

### Decisión 3 · Un retiro o gasto mayor que lo que debería haber en el cajón — [RESUELTO] A: confirmar antes de registrar (2026-09-16)

| Opción | Qué implica |
|---|---|
| **A. Avisar y pedir confirmación** | Casi siempre es un cero de más. Pero si hay un sobrante real (se cobró algo sin registrar), la plata sí está y bloquear impediría registrar lo que pasó |
| B. Bloquear | Protege del cero de más, pero obliga a inventar otra cosa cuando la plata sí estaba |

**Recomendación: A.** El aviso no dice la cifra que debería haber, para no romper el arqueo a ciegas.

### Decisión 4 · Categorías de gasto — [RESUELTO] por defecto y editables (2026-09-16)

Vienen sembradas y el cliente crea, renombra y desactiva las suyas. **Cada categoría dice si es costo o
gasto**, y se dice una sola vez, al crearla: al registrar un gasto nadie decide eso de nuevo
(`SPEC_Modelo_Datos.md:452-453`). Es lo que después deja al reporte separar utilidad bruta y neta.

- **Costo**: lo que cuesta la mercancía que se vende. El costo de los repuestos ya sale del kardex; una
  categoría de costo es para lo que se le suma a la mercancía por fuera de la factura.
- **Gasto**: lo que cuesta tener la tienda abierta (arriendo, luz, almuerzo, papelería).

`[NECESITA ACLARACIÓN: el modelo decidió que el flete es gasto y no costo (`SPEC_Modelo_Datos.md:444-450`),
así el margen por repuesto queda un poco optimista. Se revisa en el spec de reportes; aquí se siembra
como gasto]`

### Decisión 5 · ¿Los gastos que no salen del cajón entran en este spec? — [RESUELTO] A, sí (2026-09-16)

| Opción | Qué implica |
|---|---|
| **A. Sí: un gasto dice si salió del cajón o no** (como las compras) | Un solo lugar para todos los gastos. El cierre resta los del cajón; el reporte de ganancia usa todos. El spec de reportes arranca con los datos completos |
| B. No: solo gastos del cajón aquí, los demás con los reportes | El cierre sale antes, pero la utilidad neta del reporte no puede calcularse hasta construir otra pantalla de gastos |

**Recomendación: A.** Es la misma pregunta que ya se hace en la compra (*"¿salió del cajón?"*) y evita que
el reporte quede esperando otro módulo.

---

## 5. Requisitos funcionales

### Gastos y retiros

- **RF-001** · Se registra un **gasto**: categoría, monto en pesos mayor que cero, descripción obligatoria
  y **si salió del cajón**. Si salió del cajón, necesita el turno abierto y queda en ese turno. Si no
  (decisión 5), dice cómo se pagó —efectivo por fuera del cajón, o transferencia con su cuenta, como en
  las compras— y su fecha; no toca ningún arqueo.
- **RF-002** · Las categorías de gasto vienen sembradas, cada una con su naturaleza: *Arriendo*,
  *Servicios públicos*, *Internet y teléfono*, *Nómina*, *Transporte y fletes*, *Alimentación*, *Aseo y
  cafetería*, *Papelería*, *Mantenimiento*, *Impuestos y trámites* y *Otros*, todas **gasto** (decisión 4).
- **RF-002a** · El cliente **crea** una categoría con su nombre y si es **costo o gasto**; la **renombra**;
  y la **desactiva** cuando ya no la use. Una categoría con gastos no se borra: sus gastos la siguen
  nombrando. No hay dos con el mismo nombre (sin distinguir mayúsculas ni tildes).
- **RF-003** · Con el turno abierto se registra un **retiro**: monto mayor que cero y motivo obligatorio
  (quién se la llevó o para qué). Un retiro no es un gasto: no cuenta como gasto en ningún reporte.
- **RF-004** · Un gasto o retiro mayor que lo que debería haber en el cajón en ese momento pide
  confirmación, sin decir la cifra (decisión 3).
- **RF-005** · Registrar dos veces el mismo gasto o retiro por un doble clic o un reintento tras un corte
  deja **uno solo**.
- **RF-006** *(P2)* · Mientras el turno siga abierto, un gasto o retiro se **anula con motivo**: queda
  en la lista marcado como anulado y no resta. Con el turno cerrado no se anula: ese arqueo ya se firmó.
- **RF-007** · Sin turno abierto no se registran retiros ni gastos **del cajón**; la pantalla ofrece abrir
  el turno. Un gasto que no sale del cajón sí se registra.
- **RF-007a** · Una lista de **gastos**, por fecha y categoría, con cuánto suman y cuánto salió del cajón.
  Es lo que después usa el reporte de ganancia.

### Compras pagadas con plata del cajón

- **RF-008** · Al registrar o corregir una compra **en efectivo** se indica si se pagó con plata del
  cajón (decisión 1). Si sí, necesita un turno abierto y queda en ese turno.
- **RF-009** · Una compra por transferencia nunca sale del cajón.
- **RF-010** · Si una compra de cajón se corrige o se anula **mientras su turno sigue abierto**, el
  cierre toma lo que quede vigente. Si su turno ya cerró, el arqueo cerrado no cambia.

### Cerrar el turno

- **RF-011** · Lo que debería haber en el cajón es, siempre al peso:

  ```
    fondo del turno
  + efectivo de las ventas cobradas en el turno (lo pagado en efectivo, no lo recibido)
  − efectivo devuelto por las ventas anuladas en el turno (sean de este turno o de uno anterior)
  − gastos del turno pagados con plata del cajón que no estén anulados
  − retiros del turno que no estén anulados
  − compras pagadas con plata del cajón en el turno que sigan vigentes
  ```

  De una venta mixta anulada se devuelve en efectivo solo su parte en efectivo; la de transferencia se
  devuelve por transferencia y no toca el cajón.
- **RF-012** · Para cerrar, el cajero **escribe cuánto efectivo contó** (o lo cuenta por billetes), viendo lo
  que debería haber y la diferencia mientras escribe. $0 vale. *(Hasta el 2026-09-16 decía "sin ver antes
  cuánto debería haber": ver §4.)*
- **RF-013** · Al confirmar, el turno queda **cerrado** con quién, cuándo, lo que debería haber, lo
  contado y la diferencia (contado − esperado: positiva es sobrante, negativa faltante). Esas cifras
  quedan guardadas tal cual; nada de lo que pase después las cambia.
- **RF-014** · Después de cerrar, la pantalla muestra las cifras **guardadas**: lo que debería haber **con el
  desglose de RF-011**, lo contado y la diferencia. Pueden diferir de lo que se vio al contar si entró un cobro en
  el medio. Si la diferencia no es cero, pide las **observaciones** (decisión 2): qué
  pudo pasar. Quedan con el cierre y se escriben una sola vez.
- **RF-015** · Un cierre con diferencia deja un evento de auditoría con lo que debería haber, lo contado,
  la diferencia y quién cerró.
- **RF-016** · Cerrado el turno, no se vende, no se anula, no se registran gastos ni retiros ni compras de
  cajón hasta abrir otro.
- **RF-017** · Un cobro, una anulación o un gasto que llegan mientras se cierra: **o quedan en el turno
  antes de calcular el cierre, o se rechazan por turno cerrado**. Nunca en un turno cerrado sin contar
  en su arqueo.
- **RF-018** · Cerrar dos veces el mismo turno (doble clic, dos pestañas) deja un solo cierre; el segundo
  intento dice que ya está cerrado.

### Comprobante e historial

- **RF-019** *(P2)* · Al cerrar sale por la ticketera un **comprobante de cierre** de 80 mm: la tienda,
  quién abrió y cerró y cuándo, fondo, ventas en efectivo y por transferencia, descuentos dados,
  anulaciones, gastos por categoría, retiros, compras de caja, lo que debería haber, lo contado, la
  diferencia y la explicación. Sus cifras cuadran con RF-011. Como el comprobante de venta, la
  impresión nunca deshace el cierre.
- **RF-020** *(P2)* · Una pantalla con los **turnos cerrados**, del más reciente al más antiguo: abrió,
  cerró, lo que debería haber, lo contado y la diferencia; los faltantes resaltan. El detalle de uno
  muestra su desglose, sus movimientos y sus ventas, y reimprime el comprobante.
- **RF-021** *(P3)* · Contar por denominaciones: cuántos billetes y monedas de cada valor, y el total
  sale solo.
- **RF-022** *(P3)* · Al abrir un turno se sugiere como fondo el del turno anterior.

### Pantallas

- **RF-023** · En el módulo *Vender*, una sección **Caja** con: el turno abierto, **lo que debería haber en
  vivo con su desglose**, y sus gastos, retiros y compras de caja; *Registrar gasto* (abre el modal),
  *Registrar retiro*, *Categorías de gasto* y *Cerrar turno*; y *Turnos anteriores* (RF-020).
- **RF-024** · En el módulo *Reportes*, la lista de **gastos** de RF-007a, con sus totales, anular y las
  categorías. No se registran gastos desde ahí.

---

## 6. Manejo de errores

| Situación | Qué pasa |
|---|---|
| Retiro o gasto del cajón sin turno abierto | No se registra; ofrece abrir el turno o registrar el gasto como pagado por fuera |
| Gasto por transferencia sin cuenta | No se registra; se pide la cuenta |
| Crear una categoría con un nombre que ya existe | *"Ya existe la categoría «Arriendo»"* |
| Gasto sin categoría, sin descripción, o con monto $0 o negativo | No se registra; se marca el campo |
| Retiro sin motivo | No se registra; se pide el motivo |
| Gasto o retiro mayor que lo que debería haber | Pide confirmación: *"Es más de lo que debería haber en el cajón. ¿Seguro?"* |
| Doble clic al registrar un gasto | Uno solo |
| Anular un gasto de un turno cerrado | *"Ese turno ya se cerró: su arqueo no se modifica"* |
| Compra de cajón sin turno abierto | No se registra así; ofrece abrir el turno o desmarcar *"con plata del cajón"* |
| Contado negativo o vacío | No se cierra; *"Escribe cuánto contaste, aunque sea $0"* |
| Cerrar un turno ya cerrado | *"Este turno ya se cerró"*, con quién y cuándo |
| Un cobro llega justo cuando se cierra | O entra antes del cálculo y cuenta, o se rechaza: *"No hay un turno abierto"* |
| Diferencia distinta de cero y el cajero cierra la pantalla sin explicar | El cierre queda con la diferencia y sin explicación; el historial lo marca *"sin explicación"* y se puede escribir después |
| La ticketera falla al imprimir el cierre | El turno queda cerrado; aviso con *Reimprimir* |

---

## 7. Requisitos no funcionales

- **Plata.** Todo en pesos enteros. **Las partes suman lo que debería haber** (RF-011), en la pantalla y
  en el comprobante. Lo que debería haber, lo contado y la diferencia se **guardan** al cerrar; nunca se
  recalculan para un turno cerrado.
- **Concurrencia.** El cierre y todo lo que mueve plata del turno (cobrar, anular, gastos, retiros,
  compras de caja) se ponen en fila sobre el turno (RF-017). Hoy cobrar no lo hace.
- **Doble envío.** Registrar un gasto o un retiro **suma** plata que sale: lleva llave contra repetidos
  (RF-005). Cerrar no la necesita: un turno cerrado no se cierra dos veces (RF-018).
- **Auditoría.** Nacen las acciones **cerrar caja con diferencia** (ya decidida en el modelo) y **anular un
  gasto o retiro**. Los gastos y retiros no necesitan evento propio: son registros con quién y cuándo,
  que no se editan.
- **Esquema.** Migración nueva: los datos del cierre en el turno, las categorías de gasto sembradas, los
  movimientos de caja, y en la compra si se pagó con plata del cajón y en qué turno. **¿Funciona en una
  base con filas?** El turno abierto de QA queda con los datos de cierre vacíos; las compras que ya
  existen quedan como *no pagadas con plata del cajón*; el `CHECK` de acciones de auditoría se recrea.
- **Inventario.** No se toca.
- **Sin internet.** Rebanada 4. El cierre es de los eventos que el dueño verá en su panel (rebanada 5).
- **Roles.** Hasta el spec 0004 "quién" es el usuario provisional. Queda decidido para entonces: cajero y
  administrador cierran (cliente); el administrador ve lo que debería haber y los cierres anteriores; el
  cajero no ve lo que debería haber antes de cerrar.
- **Fechas.** Hora de Colombia en la pantalla y en el comprobante.

---

## 8. Criterios de aceptación

**Gastos y retiros**
- [ ] Un gasto de $15.000 en *Transporte y fletes* con descripción queda en el turno y resta al cerrar
- [ ] Un retiro de $100.000 con motivo resta al cerrar y no aparece como gasto
- [ ] Un arriendo de $800.000 por transferencia desde una cuenta queda en los gastos y no cambia lo que
      debería haber en el cajón
- [ ] Se crea la categoría *Publicidad* como gasto y aparece para elegir; una categoría con gastos se
      desactiva y no se borra
- [ ] Sin turno abierto no se registran; sin descripción o motivo, tampoco
- [ ] Doble clic al registrar un gasto deja uno solo
- [ ] *(P2)* Un gasto anulado en el turno abierto no resta; en un turno cerrado no se anula

**Compras de caja**
- [ ] Una compra en efectivo *con plata del cajón* resta al cerrar; una sin marcar, no; una por transferencia
      no ofrece la opción
- [ ] Sin turno abierto, una compra no se puede marcar *con plata del cajón*

**Cerrar**
- [ ] Con el ejemplo del §2, lo que debería haber da **$88.400**, con su desglose, y con $87.000 contados
      el cierre dice **faltan $1.400**
- [ ] Una venta de otro turno anulada en este, pagada en efectivo, resta su efectivo; una mixta resta solo
      su parte en efectivo
- [ ] La sección Caja muestra lo que debería haber en vivo, con su desglose; al contar, la diferencia se ve antes de confirmar
- [ ] La lista de gastos está en Reportes; en Caja, *Registrar gasto* abre el modal
- [ ] Con diferencia se piden las observaciones, y queda un evento de auditoría con las cifras y quién cerró
- [ ] Cerrado el turno no se vende, no se anula y no se registran gastos hasta abrir otro
- [ ] Hay prueba contra Postgres de que un cobro simultáneo al cierre o cuenta en el arqueo o se rechaza
- [ ] Dos cierres simultáneos del mismo turno dejan uno
- [ ] Las cifras guardadas de un turno cerrado no cambian al anular después una de sus ventas

**Comprobante e historial** *(P2)*
- [ ] Al cerrar sale el comprobante de 80 mm y sus partes suman lo que debería haber
- [ ] El historial muestra el cierre del ejemplo con el faltante resaltado, y su detalle reimprime

**General**
- [ ] `./mvnw clean test` en verde; lint, pruebas y build del frontend en verde

---

## 9. Qué no se toca

- **Cómo se cobra y se anula una venta**: montos, stock, kardex y comprobante quedan igual. Solo se ponen
  en fila con el cierre (RF-017).
- **Cómo una compra mueve el inventario** y cómo se corrige: solo gana si se pagó con plata del cajón.
- **Las ventas del turno** siguen mostrándose como hoy.

## 10. Fuera de alcance

| Qué | Por qué |
|---|---|
| Reportes: utilidad bruta y neta, ventas por medio de pago, productos más vendidos | Spec 0007, justo después de este. Este spec deja los gastos y su naturaleza listos para ese reporte |
| Cuadrar lo cobrado por transferencia contra el extracto | Decisión 2 del spec 0003: la transferencia no dice a qué cuenta llegó |
| Ajustes de inventario y reportes de ganancia | Rebanada 3 |
| Mandarle el cierre al dueño por correo o a su celular | Rebanada 5 (panel del propietario) |
| Devoluciones de clientes (la venta fue buena, la pieza vuelve) | Diferidas en el modelo (`SPEC_Modelo_Datos.md`, pregunta 2); cuando lleguen, su efectivo sale de la caja del día en que se reciben |
| Varias cajas a la vez | El cliente tiene un solo cajero (`SPEC_Sistema_Ventas_Repuestos (3).md:32`) |

---

## 11. Riesgos

| Riesgo | Qué hacer |
|---|---|
| Hasta el login (0004) el arqueo no señala a una persona real, y el cajero puede calcular lo que debería haber sumando lo que ve | Declarado. El 0004 va después de reportes (0007) y antes de usar el sistema en la tienda |
| Un retiro usado para tapar un faltante ("me lo llevé yo") | Todo retiro lleva motivo y queda con quién y cuándo; el dueño los ve en el historial |
| El cajero se equivoca contando y el turno ya quedó cerrado con esa cifra | Decisión 2: la explicación lo dice. Un cierre no se reabre |
| Se olvida marcar *"con plata del cajón"* en una compra | El arqueo muestra un sobrante; al verlo en el desglose se entiende. Mientras el turno esté abierto, se corrige la compra |
| Turnos que quedan abiertos varios días | Permitido (el turno puede cruzar la medianoche). El historial muestra desde cuándo |

---

## 12. Preguntas abiertas

Ninguna que bloquee. Queda para el spec 0007: **el flete como costo o gasto** (decisión 4); aquí se
siembra como gasto.
