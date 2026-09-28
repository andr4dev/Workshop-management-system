# Plan 0014 · Lo cobrado, los gastos del mes uno por uno, y *Ver cálculo* con enlaces

**Traduce:** [`spec.md`](spec.md), revisado por el usuario el 2026-09-28 con las decisiones 1 a 5 tomadas.

**Estado:** en implementación desde el 2026-09-28 (el usuario: *«implementa el 0014»*) · **las 4 fases hechas el 2026-09-28** (la P3 de *Ver cálculo*, enlace a las ventas del período, queda pendiente)

**Orden:** primero los gastos del mes (fase 1): es lo que el dueño está usando hoy, es chico y cambia menos cosas.
Después lo cobrado, que es el cambio grande (fases 2 y 3). Los enlaces de *Ver cálculo* al final (fase 4), porque
se apoyan en las cifras nuevas. Cada fase se sube sola.

---

## Dónde vive cada pieza

| Pieza | Carpeta | Por qué ahí |
|---|---|---|
| `Gasto.repartir` y su regla (*del mes exige escoger*) | `domain/…/caja/dominio/` | Es una regla del gasto |
| `CobroDeVenta` (fila de lectura: venta, día en que cuenta, monto, forma, si es abono) | `domain/…/reportes/dominio/` | Como `VentaCobrada` hoy |
| `LoCobrado` (reparte cada cobro en renglones y costo, por acumulado) | `domain/…/reportes/dominio/` | Calcula plata: tiene cuerpo y reglas |
| Nuevas lecturas en `RepositorioReportes` | `domain/…/reportes/dominio/puerto/` | Lo que el cálculo pide de afuera; lo cumple JDBC |
| Consultas nuevas | `pos/…/reportes/infraestructura/RepositorioReportesJdbc` | SQL |
| Filtro *del día / del mes* | `domain/…/caja/dominio/FiltroGastos` + `pos/…/caja/infraestructura` | Ya existe el filtro; se le suma un campo |
| Pantallas | `frontend/src/…` | — |

No hay puerto nuevo: se agregan métodos al de reportes, que ya tiene su adaptador JDBC y su falso.

---

## Fase 1 · Los gastos del mes, uno por uno (decisión 5)

**Esquema — V28** (`pos/src/main/resources/db/migration/V28__gasto_repartir.sql`):

- `ALTER TABLE gasto ADD COLUMN repartir boolean;`
- Los del mes que ya existen, repartidos: `UPDATE gasto SET repartir = true WHERE del_mes;`
- `CHECK (del_mes = (repartir IS NOT NULL))`: un gasto del mes **siempre** dice si se reparte; uno del día, nunca.

**Dominio:**

- `Gasto`: campo `Boolean repartir`; `delCajon(…)` y `porFuera(…)` lo reciben. Del mes sin escoger →
  `ReglaDeNegocioException("Escoge si el gasto del mes se reparte día a día o se registra en un día")`. Del día con
  `repartir` → se guarda `null`. `fotografia()` lo incluye.
- `ComandoRegistrarGasto` y `DetalleGasto`: `repartir`.
- `GastoDelPeriodo`: `repartir`. Su SQL lo lee.
- `ResultadosDelPeriodo.cargosDe`: sin `modo`; cada gasto dice cómo cuenta. Del mes repartido → las cuotas de hoy.
  Del mes en un día → entero si el período cubre su mes (`cubreElMes`), si no, a `fuera`. `filaDeGastosDelMes`
  aparece cuando hay algún entero, sin mirar el modo.
- Se borra `ModoGastosDelMes`. `ConsultarResultados.ejecutar` deja de recibirlo. El controlador deja de leer
  `gastosDelMes` (una pantalla vieja que lo mande no rompe: Spring ignora el parámetro).
- `FiltroGastos`: `Boolean delMes` (`null` = todos). La consulta de la lista lo aplica.

**Pantalla:**

- `ModalGasto`: *¿Es del día o del mes?* (`Segmento`). Si es del mes: *¿Repartirlo día a día o registrarlo en un
  día?* (`Segmento`, sin nada marcado), con una nota debajo de cada respuesta: *"Cada día de septiembre carga unos
  $1.667"* y *"Entero, solo en el reporte de septiembre: en el de un día no sale"*. Reemplaza lo del commit c8067ef.
- `utils/gastos.js`: `gastoNuevo` con `repartir: ''`; `conCategoria` marca *del mes* si la categoría es mensual, y
  **nunca** escoge *repartir*; `problemasDelGasto` pide escoger; `comandoDelGasto` manda `repartir` (o `null`).
  `opcionesDeReparto` pasa a dar las dos notas.
- `Gastos.jsx`: filtro **Todos / Del día / Del mes** (como el car‑wash); la etiqueta dice *Del mes · repartido* o
  *Del mes · en un día*.
- `Resultados`: se quita el selector de `SelectorPeriodo`, y `gastosDelMes` sale de `periodo.js` y de
  `consultaDeResultados`. El aviso de `avisoDeGastosDelMes` queda con un solo texto: *"No incluye $X de gastos del mes
  que van enteros en el reporte de su mes"*. La ayuda `gastosDelMes` se reescribe.

**Pruebas:**

- `GastoTest`: del mes sin escoger no se registra; del día guarda `repartir` en `null`.
- `ResultadosDelPeriodoTest`: las 18 de hoy se ajustan al modo por gasto; nuevas: un repartido y un entero del mismo
  mes en un reporte de un día (solo la cuota), de una semana (solo cuotas, y el entero en `fuera`) y del mes entero
  (cuotas completas + el entero en su fila). Las partes suman las cifras.
- `MigracionesIntegracionTest`: V28 sobre una base con un gasto del mes y uno del día → el del mes queda repartido,
  el del día en `null`, y el `CHECK` no deja un gasto del mes sin escoger.
- `ReportesIntegracionTest` y `CajaIntegracionTest`: registrar y leer de punta a punta.
- `gastos.test.js`, `resultados.test.js`, `periodo.test.js`.

**Romper a propósito:** tratar el entero como repartido; dejar pasar un gasto del mes sin escoger; que V28 deje los
viejos en `null`.

**Checkpoint:** en producción, un gasto del mes *en un día* no aparece en el reporte de hoy y sí entero en el del mes;
la lista de gastos filtra del día / del mes.

---

## Fase 2 · Lo cobrado, en el servidor (decisiones 1 a 4)

**Lecturas nuevas** en `RepositorioReportes` (y `RepositorioReportesJdbc`):

- `cobrosDeVentas(desde, hasta)`: **todos** los cobros de las ventas no anuladas que tienen **alguno** que cuenta en el
  período, también los de antes (hacen falta para el acumulado). Dos fuentes:
  - lo pagado al cobrar (`pago_venta`), que cuenta el día de la venta;
  - cada aplicación **vigente** de un abono a la deuda de una venta (`aplicacion_abono` → `deuda` con `origen =
    'VENTA'`), que cuenta el día `greatest(venta.cobrada_en, abono.recibido_en)` en hora de Colombia (decisión 2),
    con la forma del abono.
- `ventasDe(ids)` (total, descuento, día de la venta) y `renglonesDe(ids)`: por ids, no por fecha, porque una venta
  de agosto puede cobrarse en septiembre.
- `cartera(…)`: suma `cobradoDelCuaderno` (aplicaciones a deudas `CUADERNO`) aparte (decisión 3).

**`LoCobrado`** (dominio, puro): por cada venta, sus cobros en orden (día en que cuentan, luego el orden en que
entraron). Para el cobro *k*, con *C<sub>k</sub>* lo cobrado acumulado hasta él:

- la parte de cada renglón (neto de descuento) = `redondeo(neto × C_k / total) − redondeo(neto × C_{k−1} / total)`,
  y lo mismo con su costo. **Por acumulado**: cuando la venta se termina de pagar, las partes suman exacto el
  renglón y su costo, sin residuos sueltos. Dentro de un mismo cobro, los renglones se ajustan por residuo mayor para
  que sumen al peso lo cobrado (como `RepartoDelIva`).
- la venta **se completa** en el cobro que lleva *C* al total: ese día cuenta como venta, con sus unidades y sus
  renglones (decisión 4).

**`ResultadosDelPeriodo.calcular`** recibe los cobros ya repartidos en vez de `VentaCobrada` y `RenglonVendido`:

- ventas netas, costo vendido, utilidades, día por día, repuestos y categorías de repuesto: de las partes cobradas
  en el período.
- `ventas`, `unidades`, `renglones` de *Vendidos* y de *SinCosto*: de las ventas que **se completaron** en el período.
- `ticketPromedio` = total de las completadas ÷ cuántas.
- `efectivo` y `transferencia` = todo lo cobrado por esa forma (de contado + abonos); `deAbonos` aparte, para
  mostrar; `fiado` pasa a ser **lo vendido fiado en el período**, informativo, fuera de la igualdad. La igualdad:
  `efectivo + transferencia = ventas netas`.
- Una venta de contado se comporta exactamente como hoy: un solo cobro, el día de la venta, completa.

**Pruebas** (`LoCobradoTest`, `ResultadosDelPeriodoTest`, `DatosDeReporte`):

- los datos de producción del 28-sep → mes **$426.850**, día 28 **$361.850**;
- venta 9 ($55.000) con $11.000 cobrados: entra el 20 % de cada renglón y de su costo, y no cuenta como venta;
- el abono que termina de pagarla: cuenta como venta ese día, y lo acumulado de sus partes es exacto el renglón;
- tres abonos de $33.333 a una venta de $100.000 con dos renglones de costo impar: sin residuos al peso;
- lo que estaba a favor aplicado a una venta nueva cuenta el día de la venta;
- una venta fiada anulada con abonos: su abono pasa a otra venta y cuenta el día en que entró;
- lo del cuaderno no suma a ventas y sale en `cobradoDelCuaderno`;
- un período sin ventas nuevas pero con abonos a ventas viejas: tiene ventas netas y costo, y cero ventas.
- `ReportesIntegracionTest`: el mismo recorrido contra Postgres, con ventas, abonos, una anulación y el cuaderno.

**Romper a propósito:** contar el abono el día de la aplicación y no el del abono; repartir el costo sin acumulado
(sobra un peso); contar la venta al primer abono; dejar el cuaderno en ventas.

**Checkpoint:** los tests con los datos de producción dan $426.850 y $361.850.

---

## Fase 3 · Lo cobrado, en la pantalla

- `Resultados.jsx`: *Cómo entró la plata*: efectivo y transferencia, con *de abonos* debajo; ya no suma *fiado*.
  La tarjeta del fiado: *Vendido fiado*, *Cobrado en abonos*, *Cobrado del cuaderno*, *Por cobrar hoy*.
- `utils/resultados.js`: `pagosCuadran` = efectivo + transferencia = ventas netas.
- `ayudaReportes.js`: la regla nueva en palabras (*"Lo fiado cuenta cuando se cobra, y en la parte que se cobra. Una
  venta fiada se cuenta como venta el día que se termina de pagar"*).
- Rótulos: *Ventas* dice *ventas completadas*; el ticket promedio dice de qué es.
- Pruebas de pantalla; capturas en claro, oscuro y 390 px con respuestas simuladas.

**Checkpoint:** en producción, el reporte de septiembre dice ventas netas de lo cobrado y la tarjeta del fiado
completa.

---

## Fase 4 · *Ver cálculo* con enlaces (P2 y P3)

- `ResultadosDelPeriodo`: cada `PorCategoria` trae sus **gastos**: id, fecha, descripción, monto del gasto y lo que
  carga en el período (para un repartido: cuántos días de cuántos).
- *Reportes › Gastos* acepta `?gasto=<id>`: filtra a ese gasto y lo abre (RF-009). Un gasto anulado se ve anulado.
- `PanelCalculo`: cada categoría se abre en sus gastos, cada uno con enlace. El envío de la WE-10238 enlaza también a
  su compra, desde la referencia `[compra <id>]` de la descripción (hasta que el spec 0013 le dé su enlace propio).
- (P3) *Ventas netas* enlaza a la lista de ventas del período, si la lista acepta el rango en la dirección. Se
  verifica al empezar la fase; si no lo acepta, se agrega.

**Checkpoint:** en producción, *Ver cálculo › Gastos del local › Nómina* muestra el pago de Gustavo y abre ese gasto.

---

## Verificación

1. Backend abajo, `./mvnw clean install` (dominio, Postgres, migraciones).
2. `cd frontend && npx eslint src/ && node --test src/utils/*.test.js && npx vite build`.
3. **Contra una copia de producción, no contra producción:** bajar el respaldo (*Respaldo › Bajar copia*),
   restaurarlo en un `postgres:18-alpine` desechable, levantar el servidor contra él y comparar el reporte de
   septiembre con las cifras del spec. Se borra al terminar.
4. Capturas con Edge sin interfaz (memoria `rdmotors-verificar-pantallas`), desde PowerShell.
5. Romper a propósito, por fase.
6. Cada fase: commit solo de sus archivos, push, esperar a Render y comprobar en producción (solo lectura).

## Bitácora de decisiones

| Fecha | Fase | Decisión | Por qué |
|---|---|---|---|
| 2026-09-28 | — | **Fase 1 primero, aunque el spec la puso de último** | Es lo que el dueño usa hoy al registrar gastos, es chica y no depende de lo cobrado |
| 2026-09-28 | — | **El reparto de lo cobrado va por acumulado** | Repartir cada cobro por separado deja residuos de un peso que no cierran cuando la venta se termina de pagar; por acumulado, lo repartido en total es siempre el redondeo de la proporción cobrada |
| 2026-09-28 | 1 | **`Gasto.repartir` es `Boolean` y la base lo amarra con `CHECK (del_mes = (repartir IS NOT NULL))`**, en vez de un enum de tres valores | Un gasto del día no tiene nada que escoger, y uno del mes siempre lo tiene: el `CHECK` lo hace imposible de romper también desde la base. El dominio da un mensaje claro antes de llegar ahí |
| 2026-09-28 | 1 | **`ModoGastosDelMes` se borró**; el controlador ya no lee `gastosDelMes`, y una pantalla vieja que lo mande no rompe | Lo decide cada gasto. El aviso del reporte dice cuánto entró de gastos del mes y cuánto quedó para el reporte de su mes |
| 2026-09-28 | 1 | Cierre de la fase: 557 del dominio y 225 contra Postgres con `clean install`; 311 de pantalla, lint y build; capturas del formulario (del mes sin escoger no se registra; *en un día* manda `repartir: false`) | — |
| 2026-09-28 | 2 | **Las fases 2, 3 y 4 se suben juntas** | La fase 2 cambia la respuesta del reporte (`cifras.fiado` pasa a `cifras.deAbonos`, y lo vendido fiado a la cartera): subida sola, la pantalla de producción habría dicho *"no cuadra"* hasta la fase 3 |
| 2026-09-28 | 2 | **Lo cobrado se reparte entre los renglones con el mismo reparto del descuento (`RepartoDeDescuento.netos(netos, total − cobrado)`)** y el costo y el bruto con redondeo de la proporción, todo por acumulado | Reusar el reparto que ya existe da, en cada cobro, partes que suman lo cobrado al peso; el acumulado hace que al completar la venta sumen exacto el renglón y su costo (probado con tres abonos de $33.333 y costos impares) |
| 2026-09-28 | 2 | **Una venta de $0** (regalada con el descuento) tiene un cobro de $0 y se completa en él, con todo su costo | Sin eso, su costo no entraba nunca: nadie la abona |
| 2026-09-28 | 2 | **El SQL arma los cobros de tres fuentes** (lo pagado al cobrar, la venta de $0, cada aplicación vigente de un abono a una deuda de venta) y trae todos los de las ventas con alguno en el período, hasta su final | Hacen falta los de antes para el acumulado. El momento del abono es `greatest(venta, abono)`: al anular una venta, su abono pasa a otra y cuenta cuando entró la plata, no cuando se volvió a aplicar (probado contra Postgres, y roto a propósito con `aplicada_en`) |
| 2026-09-28 | 2 | **`RepositorioReportes` pierde `ventasCobradas` y `renglonesVendidos`** y gana `cobrosDeVentas`, `ventasPorId` y `renglonesDe`; la cartera suma `vendidoFiado` y `cobradoDelCuaderno` | Las ventas se leen por id porque una venta de agosto puede cobrarse en septiembre |
| 2026-09-28 | 2 | Pruebas con **los datos de producción del 26 al 28 de septiembre**: el mes da $426.850 (no $560.850), el 28 $361.850, el 27 $65.000 (el abono de la venta 2) | Es el criterio de aceptación del spec, con los montos reales |
| 2026-09-28 | 4 | **Cada categoría trae sus gastos** (`GastoCargado`: lo que es, lo que carga en el período, y si se reparte, cuántos días de cuántos). El enlace lleva a *Reportes › Gastos* en el día del gasto, con `?gasto=<id>` resaltado y a la vista | No hay una dirección de un gasto solo, y abrirlo en su día muestra también lo que hay alrededor |
| 2026-09-28 | 4 | **La P3 (enlace a las ventas del período desde *Ventas netas*) queda pendiente** | La lista de ventas es del turno y no acepta fechas en la dirección: hace falta un filtro nuevo, y la P3 es deseable |
| 2026-09-28 | 2–4 | Cierre: 563 del dominio y la suite de Postgres con `clean install`; 315 de pantalla, lint y build; capturas de *Resultados* con los datos de septiembre, *Ver cálculo* con la nómina y el envío (con su compra), y el gasto resaltado. Romper a propósito: el abono contado por `aplicada_en` (atrapado contra Postgres) y la venta contada al primer abono (4 pruebas) | — |
