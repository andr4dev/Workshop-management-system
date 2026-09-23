# Spec 0007 — Reportes de resultados: ganancia, utilidad bruta y utilidad operativa

**Estado:** cerrado el 2026-09-16 · decisiones resueltas · [plan](plan.md) aprobado el 2026-09-16 · **implementado el 2026-09-17**, con la base de QA limpia para empezar de cero
**Rebanada:** 3 (cierre de caja, gastos y reportes) · ver [`PLAN_DE_TRABAJO.md`](../../PLAN_DE_TRABAJO.md)
**Depende de:** [spec 0003](../0003-venta-de-mostrador/spec.md) (ventas, costo al vender, anulaciones) y [spec 0006](../0006-cierre-de-caja/spec.md) (gastos con su categoría de costo o gasto), implementados
**Va antes de:** el spec 0004 (usuarios y login). Los dos, antes de usar el sistema en la tienda.

> **Convención:** lo que dice *"hoy"* está verificado con `archivo:línea`. Lo que dice *"debe"* es
> propuesta y todavía no existe. Las dos cosas nunca se mezclan.

---

## 1. Objetivo de negocio

El dueño quiere saber **cuánto ganó**: hoy, esta semana, este mes o entre dos fechas. No cuánto vendió
(eso ya lo ve en *Ventas del turno*), sino cuánto le quedó después de pagar la mercancía que vendió y
después de sostener el local.

El cliente lo pidió en su cuestionario: *"Ventas del día, cuánto se vendió, ganancia por período, productos
más vendidos, stock bajo"* y *"Comparar ganancia bruta vs neta"* (`SPEC_Sistema_Ventas_Repuestos (3).md:127-128`),
y lo marcó P1: *"Reporte de ganancias · básico y mínimo funcional"* (`:240`).

Lo que resuelve:

- **Tres cifras por período**, siempre las mismas y siempre calculadas igual: ventas, utilidad bruta
  (lo que dejó la mercancía) y utilidad operativa (lo que quedó después de los gastos del local: la
  ganancia).
- **Cada cifra dice de dónde sale.** Un "−$716.625" sin su desglose no se puede creer; con el desglose,
  el dueño ve que es el arriendo que él mismo registró.
- **Las cifras que importan para decidir**: ticket promedio, margen, descuentos dados, cómo pagaron, qué
  repuestos dejan plata y cuáles se vendieron por debajo del costo.

---

## 2. Caso de uso

**Ejemplo que atraviesa todo el spec.** La semana del lunes 14 al domingo 20 de septiembre:

- se hicieron **48 ventas** con **132 unidades**; los renglones suman **$1.250.000** y se dieron **$30.000**
  en descuentos: las ventas netas son **$1.220.000** (ticket promedio **$25.417**);
- de eso entraron **$820.000 en efectivo** y **$400.000 por transferencia**;
- se anularon **2 ventas por $45.000**: no cuentan en nada;
- lo vendido **costó $780.000** a precio de compra (el costo que tenía cada repuesto al venderse);
- el dueño creó la categoría *"Fletes de mercancía"* como **costo** y registró **$20.000**;
- gastos del local: almuerzos **$60.000**, aseo y cafetería **$25.000**, papelería **$15.000**.

El reporte de esa semana dice:

```
  Renglones vendidos                        $ 1.250.000
− Descuentos dados                          −   30.000
= VENTAS NETAS                              $ 1.220.000
− Costo de los repuestos vendidos           −  780.000
− Costos adicionales (Fletes de mercancía)  −   20.000
= UTILIDAD BRUTA                            $   420.000    34,4 % de las ventas
− Gastos del local                          −  100.000
      Alimentación            60.000
      Aseo y cafetería        25.000
      Papelería               15.000
= UTILIDAD OPERATIVA (la ganancia)          $   320.000    26,2 % de las ventas
```

### P1 — bloquean

**H1 · Ver la ganancia de un período.**
El dueño abre *Reportes › Resultados*, elige *Semana pasada* y ve cuatro cifras: ventas netas, utilidad
bruta, gastos y utilidad operativa, con sus márgenes.
*Se demuestra solo:* con el ejemplo, dice $1.220.000, $420.000 (34,4 %), $100.000 y $320.000 (26,2 %).

**H2 · Saber de dónde sale cada cifra.**
Ve un "−$716.625" y no lo cree. Toca *Ver cálculo* y sale el desglose de arriba, que cuadra al peso.
*Se demuestra solo:* el desglose de cada cifra suma la cifra; si no sumara, lo diría en rojo.

**H3 · Cambiar de período.**
Hoy, Ayer, Esta semana, Semana pasada, Este mes, Mes pasado, o un rango entre dos fechas. Con flechas para
pasar al día, la semana o el mes anterior.
*Se demuestra solo:* *Este mes* muestra del 1 a hoy; la flecha lleva a agosto completo.

**H4 · Ver el período día por día.**
Una tabla y una gráfica con cada día del período: ventas netas, utilidad bruta, gastos y utilidad
operativa. La fila de totales es igual a las cifras de arriba.
*Se demuestra solo:* en la semana del ejemplo, los siete días suman $1.220.000 y $320.000.

**H5 · Las cifras que importan.**
Número de ventas, ticket promedio, unidades, margen bruto y operativo, descuentos dados y cómo pagaron
(efectivo y transferencia).
*Se demuestra solo:* 48 ventas, $25.417 de ticket promedio, $820.000 en efectivo y $400.000 por
transferencia, que suman las ventas netas.

**H6 · Saber cuándo una cifra no se puede creer del todo.**
Si se vendió un repuesto sin costo conocido, la utilidad está inflada. El reporte lo marca con un asterisco
y dice cuáles.
*Se demuestra solo:* una venta de 3 unidades sin costo marca la utilidad con "*" y la lista la nombra.

### P2 — importantes, no bloquean

**H7 · Qué repuestos dejan plata.**
Los más vendidos por utilidad, por unidades o por ventas, con su margen. Y los que se vendieron **por
debajo del costo**.
*Se demuestra solo:* el filtro de aceite más rentable encabeza la lista; uno vendido a $25 con costo
$1.000 sale en *"Vendidos con pérdida"*.

**H8 · Por categoría de repuesto.**
Cuánto vendió y cuánto dejó cada categoría (Filtros, Frenos, Lubricantes…).

**H9 · Comparado con el período anterior.**
Cada cifra dice cuánto subió o bajó frente al período anterior del mismo largo: esta semana contra la
anterior, este mes contra el mes pasado hasta el mismo día.

**H10 · Señales de control.**
Ventas anuladas (cuántas y por cuánto), descuentos (cuántos y qué porcentaje de las ventas), y las
diferencias de caja de los turnos cerrados en el período (faltantes y sobrantes).

### P3 — deseables

**H11 · El inventario de hoy**, al lado: cuánto vale al costo, cuántos repuestos tienen stock bajo y cuántos
no tienen costo. Es una foto de hoy, no del período, y lo dice.

**H12 · Lo que se compró en el período**, como dato aparte: *"se compraron $2.300.000 en mercancía"*, con la
aclaración de que comprar no resta de la utilidad (el costo entra cuando se vende).

---

## 3. Qué existe hoy

| Qué | Dónde | Estado |
|---|---|---|
| Cada venta guarda cuándo se cobró, su total, su descuento y si está anulada | `V9__venta.sql:28,31,36,39` | **funciona** |
| El total de la venta es la suma de sus renglones **menos el descuento de toda la venta** | `V9__venta.sql:47` | **funciona**; el descuento no está repartido por renglón (ver §3, *Con qué choca*) |
| Hay índice por fecha de cobro | `V9__venta.sql:62` (`idx_venta_cobrada_en`) | **existe** |
| Cada renglón apunta al movimiento del kardex con que salió | `V9__venta.sql:76` · `LineaVenta.java:53` | **funciona**: de ahí sale el costo de lo vendido |
| La salida por venta guarda **el costo promedio de ese momento**, y queda en blanco si el repuesto no tenía costo | `MovimientoKardex.java:175-181` | **funciona**: "sin costo" es un dato, no un $0 |
| Anular una venta devuelve el stock con **el mismo costo** con que salió | `MovimientoKardex.java:195-207` | **funciona** |
| Los pagos de cada venta, por forma (efectivo o transferencia) | `V9__venta.sql:85` (`pago_venta`) | **funciona** |
| Cada gasto tiene fecha, si está anulado, y una categoría que dice si es **costo o gasto** | `V12__gastos.sql:12,54,59` · `NaturalezaGasto.java:10,13` | **funciona** (spec 0006) |
| Totales de gastos por filtro, con lo que salió del cajón | `RepositorioGastosJpa.totales` · *Reportes › Gastos* | **funciona** |
| El módulo *Reportes* existe, con la pestaña *Gastos* | `App.jsx:94-95` · `componentes/reportes/PestanasReportes.jsx` | **existe**; no tiene resultados |
| El resumen del inventario activo: valor al costo, sin costo y stock bajo | `RepositorioVariantes.java:78` · `ResumenInventario.java` | **funciona**: sirve para H11 |
| Cada cierre de turno guarda lo contado y la diferencia | `V14__cierre_de_turno.sql:18-20` | **funciona**: sirve para H10 |
| Un resumen de ventas del turno (efectivo, transferencia, anuladas) calculado en el navegador | `frontend/src/utils/ventasDelTurno.js:17` | funciona, **solo para el turno abierto** |
| El frontend no tiene librería de gráficas | `frontend/package.json:12-16` | **no existe** (se decide en el plan) |

### Lo que hace el car-wash (y por qué se porta)

| Qué | Dónde | Qué se toma |
|---|---|---|
| Tres cifras grandes: ingresos, utilidad bruta y utilidad operativa, con "Ver cálculo" | `frontend/src/components/reports/MonthlyDashboard.jsx:417-445` | La misma franja y el mismo botón |
| La memoria de cálculo **dice si cuadra**: si los renglones no suman la cifra, lo muestra | `frontend/src/utils/reportCalculation.js:54-71` | Igual: una memoria que miente es peor que no tenerla |
| Un costo en cero se muestra "sin medir", no $0, cuando sí hubo ventas | `reportCalculation.js:83-97` | Igual, con la lista de cuáles |
| *"Utilidad del período"* y *"Utilidad operativa"* con nombres distintos, porque los gastos fijos del mes no ocurren por día | `frontend/src/utils/reportHelp.js:134-160` | La lección, no la solución: ver **decisión 1** |
| Ingresos por el día **real del cobro**; lo que se volvió a atribuir mal costó un arreglo documentado | `docs/FIX-REPORTES-ATRIBUCION-FECHA.md` | Aquí es sencillo: se vende y se cobra en el mismo momento |
| Ayuda (?) en cada cifra: qué es, fórmula, qué no incluye | `frontend/src/utils/reportHelp.js` | Igual |
| Ingreso y costo filtrados con criterios distintos mostraron productos rentables con pérdida | `.claude/skills/backend/SKILL.md` (car-wash), *"Ingreso y costo tienen que medir las MISMAS unidades"* | Los dos salen de los mismos renglones de las mismas ventas |

### Con qué choca

- **Con el descuento.** Se da a toda la venta, no a cada repuesto. En QA los renglones de las ventas
  vigentes suman **$365.000** y lo vendido **$352.400**: los **$12.600** de diferencia son descuentos. Las
  cifras del período cuadran restando el descuento aparte; **un ranking por repuesto** necesita repartirlo
  (decisión 3).
- **Con los gastos fijos.** El arriendo se registra un día. En el reporte de ese día la utilidad operativa
  se hunde; en el mes cuadra (decisión 1).
- **Con las anulaciones de otro día.** Una venta del lunes anulada el miércoles: ¿el reporte del lunes
  cambia? (decisión 2).
- **Con la hora.** Las fechas se guardan en hora universal. Una venta a las **7:30 p. m. en Colombia** ya
  es del día siguiente en hora universal; si el reporte corta los días en hora universal, esa venta cae en
  el día equivocado.
- **Con los datos de QA.** De 22 renglones vendidos, **9 se vendieron por debajo del costo** (precios de
  prueba como $25). El reporte de QA dará utilidades raras: es el dato, no un error. **Decidido el 2026-09-16:
  al terminar este spec la base de QA se limpia y se empieza de cero** (con respaldo).
- **Con los roles, que todavía no existen.** Hasta el spec 0004 cualquiera que abra *Reportes* ve costos,
  márgenes y ganancia, incluido el cajero.

---

## 4. Las decisiones

### Resueltas con el usuario

| Qué | Resolución |
|---|---|
| Qué entra (2026-09-16) | Reportes por día, por semana y por rango, con ganancia, utilidad bruta, utilidad operativa y las cifras que importan. Guiarse por el reporte del car-wash |
| Orden (2026-09-16) | Este spec antes que el 0004 (login y roles) |
| Costo o gasto (spec 0006, decisión 4) | Lo dice la categoría: las de **costo** restan en la utilidad bruta, las de **gasto** en la operativa |
| El flete (`SPEC_Modelo_Datos.md:446-450`) | Se sembró como **gasto**. El dueño puede crear una categoría de costo para el flete de mercancía si lo quiere dentro de la utilidad bruta |
| Gastos del mes (2026-09-16) | **Decisión 1 → ninguna de las tres tal cual**: un gasto se marca *del mes* (arriendo, nómina) y el reporte deja elegir **repartirlo día por día** o **mostrarlo solo en el mes**. Los demás gastos cuentan en su fecha |
| Venta anulada otro día (2026-09-16) | **Decisión 2 → A**: sale del reporte del día en que se vendió |
| Descuento en el ranking (2026-09-16) | **Decisión 3 → A**: se reparte entre los renglones de su venta |
| Datos de QA (2026-09-16) | Se limpian y se empieza de cero, con respaldo, al terminar |

### Decisión 1 · Los gastos del mes en un reporte de un día o una semana — [RESUELTO] gastos del mes, repartidos o solo en el mes (2026-09-16)

El arriendo de $800.000 se registra el 1 de octubre. Ese día se vendió bien: $250.000 de utilidad bruta.
¿Qué dice la utilidad operativa del 1 de octubre?

| Opción | Qué implica |
|---|---|
| **A. Cada gasto cuenta el día de su fecha, y en rangos de menos de un mes se avisa** | El 1 de octubre dice **−$550.000**; los demás días, sin arriendo, se ven mejor de lo que son; el mes completo cuadra al peso. Los gastos del reporte son exactamente los de *Reportes › Gastos*. Sin esquema nuevo. El aviso: *"Los gastos del mes (arriendo, nómina) cuentan el día en que se registraron: en rangos cortos la utilidad operativa sube y baja con ellos. Para verla completa, mira el mes."* |
| B. Repartir los gastos fijos entre los días del mes | Cada categoría dice si es "fija del mes"; un día carga 1/30 del arriendo ($26.667). La utilidad diaria es comparable, pero el reporte muestra otra cifra de gastos que la lista de gastos, y hay que explicar por qué. Es lo que el car-wash terminó haciendo, y le costó varios arreglos |
| C. Utilidad operativa solo en meses completos | En un día o una semana solo se ve la utilidad bruta. Limpio, pero no es lo que se pidió |

**Recomendación: A.** Es verdad al peso, no inventa cifras y coincide con la lista de gastos. Si el dueño,
usándolo, encuentra inútil la utilidad del día, B se agrega después sin rehacer nada.

**Resolución del usuario (2026-09-16):** un gasto se marca **del mes**, y el reporte ofrece las dos lecturas:
**repartido día por día** (B, por gasto y no por categoría) o **solo en el mes** (C, solo para los gastos del
mes). Los demás gastos cuentan en su fecha (A). Ver RF-008a y RF-010a.

### Decisión 2 · Una venta anulada otro día — [RESUELTO] A: sale del día en que se vendió (2026-09-16)

| Opción | Qué implica |
|---|---|
| **A. Una venta anulada no cuenta en ningún reporte, nunca** | Se trata como una venta que no debió existir: ni ingreso, ni costo (su stock volvió con el mismo costo). El reporte del lunes baja si la venta se anula el miércoles. El cierre de caja del lunes no cambia: esa es la caja, y la devolución resta el miércoles |
| B. Cuenta el lunes, y resta el miércoles | Los reportes pasados no cambian nunca, pero se mezcla el criterio de la caja con el de la ganancia: el miércoles "pierde" una venta que no hizo |

**Recomendación: A.** Anular es para ventas que salieron mal (spec 0003); las devoluciones de verdad llegan
en otro spec y sí restarían el día que ocurren. Las anuladas se cuentan aparte, como señal de control (H10).

### Decisión 3 · El descuento en el ranking de repuestos (P2) — [RESUELTO] A: se reparte en su venta (2026-09-16)

Una venta de un filtro de $24.000 y unas pastillas de $12.000 con $3.600 de descuento.

| Opción | Qué implica |
|---|---|
| **A. El descuento se reparte entre los renglones de su venta, en proporción a su valor** | Filtro $21.600 y pastillas $10.800. El ranking suma las ventas netas del período y el margen de cada repuesto es el real. Lo que sobre de redondear al peso va al renglón más caro |
| B. Ranking a precio de lista, y el descuento aparte | Más simple, pero el ranking no suma las ventas netas y un repuesto que siempre se rebaja parece más rentable de lo que es |

**Recomendación: A.** Es la regla del proyecto: las partes suman el total.

---

## 5. Requisitos funcionales

### El período

- **RF-001** · Se elige el período: **Hoy, Ayer, Esta semana** (lunes a hoy), **Semana pasada** (lunes a
  domingo), **Este mes** (del 1 a hoy), **Mes pasado**, o **un rango** entre dos fechas. En día, semana y mes
  hay flechas para pasar al anterior y al siguiente (no más allá de hoy). El período queda en la dirección:
  un enlace o volver atrás deja el mismo reporte.
- **RF-002** · Los días se cortan **en hora de Colombia**: una venta cobrada a las 7:30 p. m. es de ese día,
  aunque en hora universal ya sea el siguiente. Un rango va de las 12:00 a. m. del primer día a las
  11:59 p. m. del último.
- **RF-003** · Un rango no puede pasar de **366 días** ni terminar después de hoy.

### Las cifras

- **RF-004** · **Ventas netas** = la suma del total de las ventas **cobradas y no anuladas** cuya fecha de
  cobro cae en el período. Con ellas: número de ventas, unidades vendidas y **ticket promedio** (ventas
  netas ÷ número de ventas, redondeado al peso).
- **RF-005** · **Una venta anulada no cuenta** en ninguna cifra del período, se haya anulado cuando se haya
  anulado (decisión 2).
- **RF-006** · **Costo de los repuestos vendidos** = la suma del costo con que salió cada renglón de esas
  ventas, **el que tenía el repuesto al venderse**, no su costo de hoy.
- **RF-007** · Un renglón **sin costo conocido** al venderse no suma $0: su costo queda *sin medir*. La
  utilidad bruta y la operativa llevan un asterisco con *"N renglones sin costo: la utilidad está
  sobrestimada"*, y un botón *¿Cuáles?* lista el repuesto, las unidades y lo vendido, con enlace a su ficha.
- **RF-008** · **Costos adicionales** = los gastos no anulados de categorías de **costo** con fecha en el
  período.
- **RF-008a** · Un gasto se puede marcar **del mes** al registrarlo (arriendo, nómina, servicios). La categoría lo
  sugiere: *Arriendo*, *Nómina*, *Servicios públicos* e *Internet y teléfono* vienen como *"se paga cada mes"*, y
  el dueño lo puede cambiar en cualquier categoría. El mes de un gasto es el de su fecha.
- **RF-009** · **Utilidad bruta** = ventas netas − costo de los repuestos vendidos − costos adicionales.
  **Margen bruto** = utilidad bruta ÷ ventas netas.
- **RF-010** · **Gastos del local** = los gastos no anulados de categorías de **gasto** con fecha en el
  período, del cajón y por fuera, con los gastos del mes según RF-010a. **No** son gasto: los retiros, las
  compras de mercancía ni las diferencias de caja.
- **RF-010a** · Los **gastos del mes** (de costo o de gasto) se leen de una de dos formas, a elección en el
  reporte:
  - **Repartidos día por día** (por defecto): cada día de su mes carga una cuota igual (el monto entre los días
    del mes; los pesos que sobran, a los primeros días). Un período carga las cuotas de sus días.
  - **Solo en el mes**: cuentan enteros cuando el período cubre su mes completo (o del 1 a hoy, en el mes en
    curso); en un día o una semana no aparecen, y el reporte dice cuánto quedó fuera. En el día por día van en
    una fila aparte para que los totales cuadren.
- **RF-011** · **Utilidad operativa** = utilidad bruta − gastos del local. Es **la ganancia** del período.
  **Margen operativo** = utilidad operativa ÷ ventas netas. Sin ventas, los márgenes dicen "—", no 0 %.
- **RF-012** · Las cuatro cifras grandes (ventas netas, utilidad bruta, gastos del local, utilidad operativa)
  tienen **Ver cálculo**: el desglose de §2, con cada categoría de gasto y de costo. El desglose **dice si
  cuadra**; si sus renglones no suman la cifra, lo dice en rojo.
- **RF-013** · **Descuentos**: cuánto se dio y en cuántas ventas. Renglones vendidos − descuentos = ventas
  netas.
- **RF-014** · **Cómo pagaron**: efectivo y transferencia de esas mismas ventas. Suman las ventas netas.
- **RF-015** · Junto a la utilidad operativa va un aviso que dice cómo se leyeron los gastos del mes: *"El
  arriendo y los demás gastos del mes van repartidos día por día"*, o *"No incluye $800.000 de gastos del mes: se
  ven en el reporte del mes"*.
- **RF-016** · Cada cifra tiene su ayuda (?): qué es, cómo se calcula y qué **no** incluye. Por ejemplo, la
  utilidad bruta aclara que comprar mercancía no resta: el costo entra cuando se vende.

### Día por día

- **RF-017** · Una **tabla** con cada día del período: ventas, ventas netas, costo de lo vendido, utilidad
  bruta, gastos y utilidad operativa. Los días sin movimiento salen en $0. La **fila de totales es igual a
  las cifras grandes**, al peso.
- **RF-018** · Una **gráfica** por día de ventas netas y utilidad operativa. En rangos de más de 62 días,
  la tabla y la gráfica van **por semana** (lunes a domingo, recortada al rango).

### P2

- **RF-019** · **Repuestos**: los 20 primeros por utilidad, por unidades o por ventas netas, con unidades,
  ventas netas, costo, utilidad y margen. El descuento de cada venta se reparte entre sus renglones
  (decisión 3). Los renglones sin costo no tienen margen: dicen "—".
- **RF-020** · **Vendidos con pérdida**: los repuestos cuya venta en el período quedó por debajo de su costo,
  con cuánto se perdió.
- **RF-021** · **Por categoría de repuesto**: ventas netas, utilidad y margen de cada una, más *Sin categoría*.
  Las categorías suman las ventas netas.
- **RF-022** · **Comparación con el período anterior** del mismo largo: cada cifra grande dice cuánto subió o
  bajó (en pesos y en porcentaje). *Este mes* se compara con el mes pasado **hasta el mismo día**.
- **RF-023** · **Control**: ventas anuladas en el período (cuántas y por cuánto), ventas con descuento y qué
  porcentaje de las ventas se regaló, y los **turnos cerrados** en el período con su diferencia (faltantes y
  sobrantes), con enlace a cada cierre.
- **RF-024** · **Gastos por categoría** del período, con enlace a *Reportes › Gastos* filtrado por esas fechas
  y esa categoría.

### P3

- **RF-025** · **Inventario de hoy**: valor al costo, repuestos con stock bajo y repuestos sin costo. Dice
  *"hoy"*: no cambia con el período.
- **RF-026** · **Mercancía comprada en el período**: la suma de las compras vigentes por fecha de la factura,
  aparte y con la aclaración de que no resta de la utilidad.

### La pantalla

- **RF-027** · En *Reportes*, una pestaña **Resultados**, la primera, antes de *Gastos*. Arriba el período;
  debajo las cuatro cifras grandes; después las cifras que importan (RF-004, RF-013, RF-014), el día por
  día, y lo de P2. Se lee en el celular y en modo oscuro.

---

## 6. Manejo de errores

| Situación | Qué pasa |
|---|---|
| Rango con la fecha inicial después de la final | No se consulta; *"La fecha inicial es posterior a la final"* |
| Rango de más de 366 días, o que termina después de hoy | No se consulta; se dice por qué |
| Período sin ventas pero con gastos | Se muestra todo: ventas $0, utilidad operativa negativa. **No** dice "sin datos" (el car-wash escondía justo lo que había) |
| Período sin ventas ni gastos | *"Sin movimientos entre el 1 y el 7 de octubre"* |
| Renglones vendidos sin costo | Asterisco en las utilidades y lista de cuáles (RF-007) |
| Un desglose que no cuadra con su cifra | Se dice en rojo junto a la cifra; nunca se corrige en pantalla |
| Sin conexión con el servidor | Aviso y *Reintentar*. **No** se dejan a la vista cifras de otro período como si fueran de este |
| Margen sin ventas | "—" |
| El servidor tarda (un año de ventas) | Se ve cargando; el reporte anterior queda atenuado, no se borra |

---

## 7. Requisitos no funcionales

- **Plata.** Pesos enteros. **Las partes suman el total**: el día por día suma las cifras grandes, los medios
  de pago suman las ventas netas, el ranking y las categorías (P2) también. Cada una de esas igualdades tiene
  su prueba.
- **Un solo cálculo.** Las cifras grandes, el día por día y la comparación salen del mismo sitio en el
  servidor. La pantalla no vuelve a calcular utilidades por su cuenta (el car-wash tuvo el mismo cálculo en
  cuatro sitios y se contradijeron).
- **Ingreso y costo miden lo mismo.** Los dos salen de los mismos renglones de las mismas ventas, con el
  mismo filtro de fechas y de anuladas.
- **Es causación, no caja.** Lo vendido y lo gastado en el período. La caja (lo que entró y salió del cajón)
  es el cierre del spec 0006, y las dos cosas no se mezclan en un mismo reporte. Por eso las compras no restan
  y los retiros no son gasto.
- **Solo lectura.** No escribe nada: no lleva llave ni auditoría.
- **Rendimiento.** Un año de ventas (del orden de 20.000 ventas y 50.000 renglones) responde en menos de
  2 segundos en la PC de la tienda. `[sin verificar: medirlo con datos sembrados en el plan]`
- **Esquema.** No hacen falta tablas nuevas. Quizá un índice para buscar renglones por venta. `[sin verificar]`
- **Sin internet.** El servidor es el de la tienda: funciona sin internet. El panel del dueño en su celular es
  la rebanada 5.
- **Roles.** Hasta el spec 0004, cualquiera que abra *Reportes* ve costos, márgenes y ganancia. Queda para
  entonces: los reportes son del administrador.
- **Fechas.** Hora de Colombia en los cortes de día, en la pantalla y en la gráfica.

---

## 8. Criterios de aceptación

**Las cifras**
- [ ] Con la semana del ejemplo: ventas netas **$1.220.000**, utilidad bruta **$420.000 (34,4 %)**, gastos
      **$100.000** y utilidad operativa **$320.000 (26,2 %)**
- [ ] El *Ver cálculo* de cada cifra reproduce el desglose de §2 y dice que cuadra
- [ ] Las 2 ventas anuladas de la semana no suman en nada; anular el miércoles una venta del lunes baja el
      reporte del lunes
- [ ] El costo de lo vendido es el costo al vender: una compra posterior más cara no cambia la utilidad de la
      semana
- [ ] Una venta de un repuesto sin costo marca las utilidades con asterisco y aparece en *¿Cuáles?*
- [ ] Una categoría de costo resta en la utilidad bruta; una de gasto, en la operativa; un retiro y una compra
      no restan
- [ ] Una venta cobrada a las 7:30 p. m. de Colombia cuenta en ese día
- [ ] 48 ventas, ticket promedio $25.417; efectivo $820.000 + transferencia $400.000 = $1.220.000
- [ ] Renglones $1.250.000 − descuentos $30.000 = ventas netas

**El período**
- [ ] Hoy, Ayer, Esta semana, Semana pasada, Este mes, Mes pasado y Rango, con flechas en día, semana y mes
- [ ] Un arriendo de $800.000 marcado del mes: repartido, el 1 de octubre carga $25.807 y octubre completo
      $800.000; solo en el mes, el 1 de octubre no lo carga y lo dice, y octubre completo lo carga entero
- [ ] Al registrar un gasto de *Arriendo*, la casilla *del mes* sale marcada
- [ ] El día por día suma las cifras grandes, al peso; un rango de 90 días va por semanas
- [ ] Un rango al revés, de más de 366 días o hasta mañana no se consulta y dice por qué
- [ ] Un período con gastos y sin ventas muestra la utilidad operativa negativa, no "sin datos"

**P2**
- [ ] El ranking por repuesto suma las ventas netas del período, con el descuento repartido
- [ ] Un repuesto vendido por debajo del costo sale en *Vendidos con pérdida*
- [ ] Las categorías de repuesto suman las ventas netas
- [ ] *Este mes* se compara con el mes pasado hasta el mismo día
- [ ] Los turnos cerrados del período salen con su diferencia y enlazan a su cierre

**General**
- [ ] Se ve bien en el celular (390 px) y en modo oscuro
- [ ] `./mvnw clean test` en verde; lint, pruebas y build del frontend en verde

---

## 9. Qué no se toca

- **Cómo se vende, se anula, se compra o se gasta.** El reporte solo lee.
- **El cierre de caja** (spec 0006): sigue siendo la caja del turno. El reporte no recalcula cierres.
- ***Reportes › Gastos***: sigue igual; los resultados enlazan a ella.

## 10. Fuera de alcance

| Qué | Por qué |
|---|---|
| Exportar a PDF o Excel | El cliente lo dejó para una fase 2 (`SPEC_Sistema_Ventas_Repuestos (3).md:275`) |
| Ver los reportes desde el celular del dueño fuera de la tienda | Rebanada 5, panel del propietario |
| Reportes por cajero (quién vendió cuánto) | Necesita usuarios reales: spec 0004 |
| Metas, presupuestos o proyecciones | No se pidieron |
| Devoluciones de clientes | Diferidas en el modelo; cuando lleguen, restarán el día en que ocurran |
| El mismo período del año anterior | Con el anterior inmediato basta para empezar (RF-022) |

## 11. Riesgos

| Riesgo | Qué hacer |
|---|---|
| El cajero ve costos, márgenes y ganancia hasta el spec 0004 | Declarado. El 0004 va justo después y antes de usar el sistema en la tienda |
| Compras mal capturadas dan costos absurdos, y la utilidad sale absurda | El reporte dice lo que hay; *Vendidos con pérdida* (P2) es justo lo que lo delata. En QA ya hay 9 de 22 renglones así |
| La utilidad de un día sube y baja con los gastos del mes | RF-010a: repartidos o solo en el mes, a elección |
| Un gasto del mes registrado en otro mes (el arriendo de septiembre pagado el 2 de octubre) | Cuenta en el mes de su fecha; la ayuda dice registrarlo con la fecha del mes que se paga |
| Limpiar QA borra algo que se quería conservar | Respaldo completo antes, y la orden explícita del usuario en ese momento |
| Un reporte ya visto cambia porque se anuló una venta después | Decisión 2: es lo correcto para la ganancia; la caja de ese día no cambia |
| Un año de ventas se vuelve lento | Se mide con datos sembrados antes de dar el plan por terminado |
| Que el costo de hoy se cuele en vez del costo al vender (cambiaría la utilidad de meses pasados con cada compra) | RF-006 y su criterio de aceptación: una compra posterior no mueve la utilidad |

---

## 12. Preguntas abiertas

Las decisiones 1 a 3 quedaron resueltas el 2026-09-16 (§4). Queda una, que no bloquea:

1. **¿Qué cifras quiere ver primero el dueño?** Se propusieron las del car-wash más las propias de un almacén
   de repuestos (vendidos con pérdida, por categoría). `[NECESITA ACLARACIÓN: confirmar con el cliente si
   falta alguna que use hoy en su cuaderno o en Excel]`
