# Plan 0007 — Reportes de resultados: ganancia, utilidad bruta y utilidad operativa

**Traduce:** `docs/specs/0007-reportes-de-resultados/spec.md`. Las decisiones del usuario (2026-09-16):

| Decisión | Resolución |
|---|---|
| 1. Los gastos del mes en rangos cortos | **Cambia la recomendación.** Un gasto se marca **"del mes"** (arriendo, nómina). En el reporte, un selector: **repartidos día por día** o **solo en el mes** (no aparecen en un día ni en una semana). Los demás gastos cuentan en su fecha |
| 2. Venta anulada otro día | **A**: sale del reporte del día en que se vendió; el cierre de caja no cambia |
| 3. Descuento en el ranking de repuestos | **A**: se reparte entre los renglones de su venta, en proporción a su valor |
| 4. Datos de QA | **Se limpia la base de QA y se empieza de cero**, con respaldo, al terminar |

**Estado:** aprobado el 2026-09-16 · **las 6 fases hechas el 2026-09-17**: 1 a 5 implementadas y verificadas; la 6 limpió la base de QA con la orden del usuario

---

## Contexto

El dueño no tiene forma de saber cuánto ganó en un día, una semana, un mes o un rango. Los datos ya
existen: cada venta guarda su total y su descuento (`V9__venta.sql:28-47`), cada renglón apunta a la salida
del kardex con **el costo que tenía el repuesto al venderse** (`V9:76`, `MovimientoKardex.java:175-181`, en
blanco si no tenía costo), y cada gasto dice si es costo o gasto por su categoría (`V12__gastos.sql:12,54`).
Falta juntarlos en un reporte que cuadre al peso, con su memoria de cálculo, al estilo del car-wash
(`CAR-WASH-SYSTEM/frontend/src/utils/reportCalculation.js`, `MonthlyDashboard.jsx:417-445`).

Lo que ya existe y se reusa:

| Pieza | Dónde |
|---|---|
| Módulo *Reportes* con pestañas y la lista de gastos | `frontend/src/componentes/reportes/PestanasReportes.jsx` · `paginas/Gastos.jsx` · `App.jsx:94-95` |
| Ayuda (?) portada del car-wash, con portal y versión móvil | `frontend/src/componentes/Ayuda.jsx` |
| Resumen del inventario hoy (valor, sin costo, stock bajo) | `GET /api/inventario/resumen` · `InventarioController.java:73` |
| Totales de compras por fecha de factura y estado | `GET /api/compras/totales` · `RepositorioComprasJpa.totales` |
| Gastos con categoría, naturaleza, fecha y anulación; modal y categorías | `caja/dominio/Gasto.java` · `CategoriaGasto.java` · `componentes/caja/ModalGasto.jsx` · `ModalCategoriasGasto.jsx` |
| Turnos cerrados con su diferencia | `GET /api/turnos?estado=CERRADO` · `V14__cierre_de_turno.sql` |
| Filtros en la URL, clave de consulta con reintento, tablas y paginación | `paginas/HistorialCompras.jsx` · `Listado.module.css` · `componentes/AvisoCarga.jsx` |
| Formato COP, fechas | `utils/formato.js` (`formatoCOP`, `fechaDia`) · `utils/inventario.js` (`fechaLocal`) |
| Hora de Colombia en el servidor | `RelojSistema.java:19` (`America/Bogota`) |

---

## Resumen

| Fase | Qué | Pantalla | Migración |
|---|---|---|---|
| 1 | Gastos del mes: la marca en el gasto y la sugerencia en la categoría | Caja (modal) · Reportes › Gastos | V15 |
| 2 | El cálculo de resultados en el servidor: período, cifras, día por día, sin costo | `GET /api/reportes/resultados` | — |
| 3 | La pantalla de resultados (P1) | Reportes › Resultados | — |
| 4 | Repuestos, categorías, período anterior y control (P2) | Reportes › Resultados | — |
| 5 | Inventario de hoy y compras del período (P3) | Reportes › Resultados | — |
| 6 | Limpiar la base de QA y empezar de cero | — | — |

---

## Decisiones tomadas al planear

1. **Módulo `reportes` propio**, de solo lectura: `reportes/dominio`, `reportes/dominio/puerto`,
   `reportes/aplicacion`, `pos/…/reportes/infraestructura`. Lee ventas, kardex, gastos y turnos por SQL con
   `JdbcTemplate`, sin cargar entidades.
2. **El SQL trae filas; las sumas y las reglas viven en el dominio.** El puerto devuelve los renglones
   vendidos del período (con el descuento y el subtotal de su venta, el repuesto, su categoría, la cantidad,
   el total y el costo al vender), los pagos por día y forma, y los gastos que tocan el período.
   - `ResultadosDelPeriodo.calcular(...)` produce las cifras grandes, el día por día, las categorías de costo
     y gasto, los renglones sin costo y (fase 4) el ranking y las categorías de repuesto.
   - Todo sale **del mismo cálculo**: las partes suman el total por construcción y cada igualdad tiene
     prueba. La pantalla no suma plata; solo ordena.
3. **El día se corta en Colombia dentro del SQL** (`(cobrada_en AT TIME ZONE 'America/Bogota')::date`). El
   rango se filtra por instantes (medianoche de Colombia del primer día, a medianoche del día siguiente al
   último), para que use `idx_venta_cobrada_en`.
4. **Gastos del mes** (decisión 1):
   - **Qué es uno.** `gasto.del_mes`, que el modal sugiere con `categoria_gasto.mensual` y el cajero puede
     cambiar. Se siembra `mensual` en Arriendo, Nómina, Servicios públicos, e Internet y teléfono. El mes de un
     gasto es el de su fecha.
   - **Repartidos** (por defecto). Cada día del mes carga `monto ÷ días del mes`, y los pesos que sobran van a
     los primeros días: septiembre, $800.000 → 20 días de $26.667 y 10 de $26.666. Un período carga las cuotas
     de sus días, aunque la fecha del gasto quede fuera del rango.
   - **Solo en el mes.** Un gasto del mes cuenta entero si el período cubre su mes del día 1 al último día (o
     hasta hoy, si es el mes en curso). Si no, queda fuera, y el reporte dice cuánto quedó fuera y por qué.
     En el día por día va en una fila aparte, *"Gastos del mes"*, para que los totales sigan cuadrando.
   - **Dónde vive el selector.** En la dirección (`gastosDelMes=REPARTIDOS|SOLO_EN_EL_MES`).
5. **Un solo endpoint**: `GET /api/reportes/resultados?desde&hasta&gastosDelMes`. Devuelve el período, las
   cifras, los desgloses, las filas, los sin costo y, desde la fase 4, `anterior`, `repuestos`, `categorias`
   y `control`.
6. **El período anterior lo decide el dominio** (`Periodo.anterior()`):
   - un mes completo → el mes anterior completo;
   - del 1 a un día del mismo mes → el mes anterior del 1 al mismo día (recortado a su último día);
   - cualquier otro → los mismos días justo antes.
7. **Sin librería de gráficas**: barras en SVG propias (ventas netas y utilidad operativa; la negativa debajo
   del eje). Es una gráfica; una dependencia de ~100 kB no se justifica.
8. **La fase 5 no toca el backend**: usa `GET /api/inventario/resumen` y `GET /api/compras/totales`
   (`estado=VIGENTE`, por fecha de factura).
9. **Limpiar QA** es borrar el esquema y dejar que Flyway lo cree de nuevo con sus semillas. Antes, un
   `pg_dump` completo. **No se ejecuta sin la orden del usuario en ese momento**: es irreversible.

---

## Fase 1 · Gastos del mes (decisión 1; RF-008a nuevo)

| Pieza | Dónde cae |
|---|---|
| `CategoriaGasto.mensual` (`nueva(nombre, naturaleza, mensual)`, `marcarMensual(boolean)`) | `caja/dominio/` |
| `Gasto.delMes` en `delCajon(...)` y `porFuera(...)`; entra en `fotografia()` | `caja/dominio/` |
| `RenombrarCategoriaGasto` → `ActualizarCategoriaGasto(id, nombre, mensual)` (PUT reemplaza los dos) | `caja/aplicacion/` |
| `ComandoRegistrarGasto`, `DetalleGasto` y los controladores ganan `delMes` y `mensual` | `caja/aplicacion/` · `caja/infraestructura/` |

**V15:**
- `categoria_gasto.mensual boolean NOT NULL DEFAULT false`, y `true` en las cuatro sembradas.
- `gasto.del_mes boolean NOT NULL DEFAULT false`.
- **V12 a V14 no se tocan**: están aplicadas y Flyway valida su checksum.

**Frontend:**
- `ModalGasto`: casilla *"Es un gasto del mes (arriendo, nómina…)"*, marcada según la categoría elegida hasta
  que el cajero la toque.
- `ModalCategoriasGasto`: *"Se paga cada mes"* al crear, y un interruptor en cada fila.
- `Reportes › Gastos`: etiqueta *"Del mes"* en la lista.
- `utils/gastos.js` gana `delMes` en `gastoNuevo`, `comandoDelGasto` y la sugerencia por categoría, con
  pruebas.

**Pruebas:**
- `CategoriasGastoTest`: crear mensual y cambiarla.
- `RegistrarGastoTest`: del mes, del cajón o por fuera.
- `MigracionesIntegracionTest`: V15 sobre una base con gastos, que quedan `del_mes = false`, y las cuatro
  categorías quedan mensuales.
- `gastos.test.js`.

**Checkpoint:** en Caja, al elegir *Arriendo* la casilla sale marcada; el gasto queda "Del mes" en
Reportes › Gastos.

---

## Fase 2 · El cálculo de resultados (H1–H6; RF-001 a RF-018)

| Pieza | Dónde cae |
|---|---|
| `Periodo(desde, hasta)`: exige desde ≤ hasta, ≤ 366 días, hasta ≤ hoy; `dias()`, `agrupacion()` (DIA hasta 62 días, si no SEMANA lunes–domingo recortada), `anterior()`, `cubreElMes(YearMonth, hoy)`; `ZONA = America/Bogota` | `reportes/dominio/` |
| `ModoGastosDelMes { REPARTIDOS, SOLO_EN_EL_MES }` | `reportes/dominio/` |
| Filas de lectura: `RenglonVendido`, `PagoDelDia(dia, forma, monto)`, `GastoDelPeriodo(id, fecha, categoriaId, categoria, naturaleza, delMes, monto)` | `reportes/dominio/` |
| `RepartoDelMes.cuotas(gasto)` → mapa día → pesos, que suma el monto | `reportes/dominio/` |
| `RepartoDeDescuento.netos(renglonesDeUnaVenta)` → cada renglón con su parte del descuento; el resto al renglón más caro | `reportes/dominio/` (lo usa la fase 4; se escribe aquí porque las cifras no lo necesitan) |
| `ResultadosDelPeriodo.calcular(periodo, renglones, pagos, gastos, modo, hoy)`: `Cifras` (renglones, descuentos, ventas netas, ventas, unidades, ticket promedio, costo vendido, costos adicionales, utilidad bruta, margen bruto, gastos, utilidad operativa, margen operativo, efectivo, transferencia, ventas con descuento), costos y gastos por categoría, `SinCosto` (renglones, unidades, vendido, repuestos), `Fila` por día o semana, fila de gastos del mes sin repartir, y lo que quedó fuera en "solo en el mes" | `reportes/dominio/` |
| `RepositorioReportes`: `renglonesVendidos(Instant, Instant)` (solo ventas `COBRADA`), `pagosPorDia(Instant, Instant)`, `gastos(LocalDate desde, LocalDate hasta)` (los de fecha en el período, más los del mes de los meses que toca el período; sin anulados) | `reportes/dominio/puerto/` |
| `ConsultarResultados` (readOnly): arma el período con el `Reloj`, pide las filas y calcula | `reportes/aplicacion/` |
| `RepositorioReportesJdbc` (SQL nativo con la zona) y `ReporteController` | `pos/…/reportes/infraestructura/` |
| Bean en `ConfiguracionCasosDeUso`; `ReglaDeNegocioException` del período → 422 | `pos/…/compartido/infraestructura/` |

**Reglas que el cálculo cumple (cada una con prueba):**
- Ventas netas = Σ totales; renglones − descuentos = ventas netas; efectivo + transferencia = ventas netas.
- El costo vendido suma solo los renglones con costo. Los sin costo se cuentan y se listan, nunca como $0.
- La utilidad bruta resta los costos adicionales (categorías de costo); la operativa, los gastos.
- Las filas suman las cifras, al peso, contando la fila de gastos del mes en "solo en el mes".
- Márgenes con un decimal; `null` sin ventas. Ticket promedio redondeado al peso.

**Pruebas:**
- `PeriodoTest`: validaciones, 62 y 63 días, semanas recortadas, y `anterior()` en semana, mes a la fecha,
  mes completo y rango.
- `RepartoDelMesTest`: $800.000 en septiembre suma exacto; la semana 14–20 carga 7 cuotas.
- `RepartoDeDescuentoTest`: $24.000 + $12.000 con $3.600 → $21.600 y $10.800; tres de $10.000 con $1.000
  suman exacto.
- `ResultadosDelPeriodoTest`:
  - **el ejemplo del §2** con filas armadas a mano: $1.220.000, $420.000 (34,4 %), $100.000 y
    $320.000 (26,2 %), 48 ventas, ticket de $25.417;
  - las filas suman; un renglón sin costo;
  - un período con gastos y sin ventas; los márgenes en `null`;
  - el arriendo repartido en un día (−$26.667) y "solo en el mes" en una semana (fuera, con el monto) y en el
    mes (entero, en su fila);
  - 90 días van por semanas.
- `ConsultarResultadosTest` con el doble `ReportesEnMemoria`.
- `ReportesIntegracionTest` (clase y contenedor propios), contra Postgres y con los casos de uso reales:
  - compras, cobros, un descuento, una anulación y gastos → las cifras;
  - **una venta cobrada a las 7:30 p. m. de Colombia** (`cobrada_en` ajustado por SQL) cae en su día;
  - la anulada no suma; **una compra posterior más cara no cambia el costo vendido**;
  - el gasto del mes repartido.
  - **Rendimiento**: 20.000 ventas y 50.000 renglones sembrados con `generate_series` responden en menos de
    2 s, con el tiempo impreso.
- Romper a propósito:
  - quitar el filtro de anuladas;
  - cortar el día en hora universal;
  - usar el costo promedio de hoy en vez del de la salida;
  - quitar el resto del reparto;
  - que las filas no sumen.
- `.http`: `http/05-reportes.http`.

**Checkpoint:** con el `.http` contra QA, la semana del 14 al 20 devuelve cifras cuyo desglose suma.

---

## Fase 3 · La pantalla de resultados (P1: H1–H6; RF-001, RF-007, RF-012 a RF-018, RF-027)

**Frontend:**
- `PestanasReportes`: **Resultados** primero; `/reportes` lleva a `/reportes/resultados`.
- `utils/periodo.js` + pruebas:
  - Hoy, Ayer, Esta semana, Semana pasada, Este mes, Mes pasado y Rango;
  - flechas anterior y siguiente, sin pasar de hoy;
  - el período ↔ la URL;
  - problemas (al revés, más de 366 días, futuro); el título ("del 14 al 20 de sept").
- `utils/resultados.js` + pruebas:
  - `calculoDe(cifra, respuesta)` → filas con signo, hijos por categoría, `cuadra`;
  - `textoDeMargen`, el aviso de gastos del mes según el modo, `hayDatos`.
- `utils/ayudaReportes.js`: qué es, fórmula y qué no incluye de cada cifra, en un solo sitio.
- `utils/grafica.js` + pruebas: escala con negativos y ancho de barras.
- `paginas/Resultados.jsx` + `Resultados.module.css`:
  - selector de período y de gastos del mes;
  - **cuatro cifras grandes** con margen, *Ver cálculo*, asterisco y *¿Cuáles?* de los sin costo;
  - cifras que importan: ventas, unidades, ticket, descuentos y cómo pagaron;
  - aviso de gastos del mes; tabla día por día con totales y la gráfica.
- Componentes en `componentes/reportes/`: `SelectorPeriodo`, `CifraGrande`, `PanelCalculo` (modal),
  `TablaPorDia`, `GraficaPorDia` (SVG), `SinCosto`.
- `api/cliente.js`: `reportesApi.resultados(consulta)`, con la clave de consulta y el reintento de
  `HistorialCompras`. Si falla, **no** deja a la vista cifras de otro período.

**Checkpoint (Edge contra QA, claro, oscuro y 390 px):**
- con datos armados por la API para la semana, las cifras cuadran;
- *Ver cálculo* dice que cuadra; el día por día suma;
- el selector de gastos del mes cambia el día con arriendo;
- un rango al revés no consulta.

Los datos que se creen se borran en la fase 6.

---

## Fase 4 · Repuestos, categorías, período anterior y control (P2: H7–H10; RF-019 a RF-024)

| Pieza | Dónde cae |
|---|---|
| `ResultadosDelPeriodo` gana `repuestos`: por repuesto, unidades, ventas netas con el descuento repartido, costo, utilidad, margen (null sin costo) y `conPerdida`. Y `categoriasDeRepuesto`, que suman las ventas netas, con *Sin categoría* | `reportes/dominio/` |
| `ConsultarResultados` calcula también `anterior` (solo las cifras) con `Periodo.anterior()` | `reportes/aplicacion/` |
| `RepositorioReportes.control(Instant, Instant)`: ventas anuladas del período por fecha de cobro (cuántas y monto) y turnos cerrados en el período con su diferencia | puerto y adaptador |

**Frontend:**
- **Repuestos**: los 20 primeros por utilidad, unidades o ventas (el orden lo elige la pantalla; las cifras
  vienen hechas), con enlace a la ficha. *Vendidos con pérdida* aparte.
- **Por categoría**.
- **Variación** en cada cifra grande (▲▼, pesos y %), con `utils/resultados.js` `variacion` y sus pruebas.
- **Control**: anuladas, descuentos (% de las ventas), y turnos cerrados con su diferencia, que enlazan a
  `/vender/caja/turnos/:id`.
- **Gastos por categoría**, que enlaza a `/reportes/gastos?desde&hasta&categoriaId`.

**Pruebas:**
- `ResultadosDelPeriodoTest`: el ranking suma las ventas netas; con pérdida; las categorías suman.
- `ConsultarResultadosTest`: el anterior de *Este mes*.
- Integración: `control` contra Postgres.
- `resultados.test.js`.

**Checkpoint:** en QA, un repuesto vendido por debajo del costo sale en *Vendidos con pérdida*; el ranking y
las categorías suman las ventas netas; *Este mes* compara con el mes pasado hasta el mismo día.

---

## Fase 5 · Inventario de hoy y compras del período (P3: H11, H12; RF-025, RF-026)

- `Resultados.jsx`: tarjeta *"Inventario hoy"* con `inventarioApi.resumen()`; dice "hoy" y no cambia con el
  período.
- *"Mercancía comprada en el período"* con `comprasApi.totales({ desde, hasta, estado: 'VIGENTE' })`, y la
  aclaración de que no resta.
- Si fallan, su tarjeta dice por qué y se puede reintentar; el resto del reporte no se afecta.

**Checkpoint:** la tarjeta de inventario no cambia al pasar de semana; la de compras sí.

---

## Fase 6 · Limpiar la base de QA (decisión 4)

1. **Respaldo**: `docker exec rdmotors-postgres pg_dump -U rdmotors rdmotors` →
   `Desktop/workshop-management-system/respaldos/qa-AAAA-MM-DD.sql`. Se comprueba que el archivo no esté vacío.
2. **Pedir la orden al usuario** con lo que se va a borrar: repuestos, compras, ventas, turnos, gastos, cuentas
   y proveedores de prueba.
3. Bajar el backend; `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` en `rdmotors`; subir el backend.
   Flyway aplica de V1 a V15 con sus semillas: categorías de repuesto y de gasto, contador en 0, tienda
   *RD MOTORS*.
4. Comprobar: sin turno abierto, *Inventario* vacío, las 11 categorías de gasto, la venta siguiente con N.º 1.
5. Recordar en el navegador de la tienda borrar el borrador de venta (`localStorage`
   `rdmotors:venta-en-curso`), si quedó.

No toca los contenedores ni los puertos del car-wash (5432, 8080, 5173).

---

## Riesgos

| Riesgo | Fase | Qué hacer |
|---|---|---|
| Un año de filas por JDBC tarda más de 2 s | 2 | La prueba lo mide. Si falla, se agregan en SQL las cifras por día y solo los renglones con descuento viajan a Java; se anota en la bitácora |
| El corte por zona horaria no usa el índice | 2 | El filtro va por instantes; se revisa el `EXPLAIN` en la prueba de rendimiento |
| Un gasto del mes registrado en otro mes (el arriendo de septiembre pagado el 2 de octubre) | 1 | Cuenta en el mes de su fecha. Se dice en la ayuda: registrarlo por fuera con la fecha del mes que se paga. Un "mes al que corresponde" aparte, si hace falta, va después |
| El cajero ve costos y ganancia hasta el spec 0004 | todas | Declarado en el spec |
| Limpiar QA borra algo que el usuario quería conservar | 6 | Respaldo antes, y la orden explícita en ese momento |

---

## Verificación final

1. Backend abajo, `./mvnw clean test` (dominio, Postgres y migraciones).
2. `cd frontend && npx eslint src/ && node --test src/utils/*.test.js && npx vite build`.
3. Los criterios del §8 del spec, uno por uno, en Edge contra QA (claro, oscuro y 390 px). El ejemplo del §2
   lo cubre la prueba de dominio y la de Postgres.
4. `http/05-reportes.http` al día.
5. Romper a propósito, como en la fase 2, y ver fallar cada prueba.
6. Fase 6: respaldo, orden del usuario, limpieza y comprobación.

---

## Bitácora de decisiones

Se llena **durante** la implementación, con fecha, fase, decisión y porqué.

| Fecha | Fase | Decisión | Por qué |
|---|---|---|---|
| 2026-09-17 | 1 | **La casilla "del mes" sigue a la categoría hasta que el cajero la toca** (`conCategoria` en `utils/gastos.js`, con `delMesTocado`). Al cambiar de *Arriendo* a *Fletes* se desmarca; si el cajero la marcó a mano, se respeta | Es una sugerencia, no una regla: la nómina de un domingo puede ser del día |
| 2026-09-17 | 1 | **El PUT de la categoría reemplaza nombre y `mensual`**; renombrar manda el `mensual` que tenía. `RenombrarCategoriaGasto` pasa a `ActualizarCategoriaGasto` | Un PUT que olvide un campo lo borraría; la pantalla siempre manda los dos |
| 2026-09-17 | 2 | **`PagoDelDia` cambia por `VentaCobrada`**: una fila por venta con su día, total, descuento, efectivo y transferencia | Las ventas por venta dan el número de ventas, el ticket, los descuentos y los pagos de la misma fila, y el día por día sale de ellas. Son 20.000 filas en un año: no pesa |
| 2026-09-17 | 2 | **Las lecturas del reporte van en `REPEATABLE_READ`** (`ConsultarResultados`) | Son tres consultas. Con `READ_COMMITTED`, una venta cobrada entre la de ventas y la de renglones dejaría unas cifras que no suman. Solo lee: en Postgres no hay conflictos de serialización |
| 2026-09-17 | 2 | **Una venta fuera del período, o un renglón de una venta que no vino, es un error** (`IllegalArgumentException`), no se ignora | Si el SQL corta mal el día, el reporte falla en vez de mostrar cifras que mienten. La prueba de romper la hora universal lo confirma |
| 2026-09-17 | 2 | **Gastos del mes que quedan fuera**: los de cualquier mes que el período toca y no cubre, aunque su fecha caiga fuera del período | Con los mismos gastos que carga el modo *repartidos*: el 2 de octubre, *solo en el mes*, dice "no incluye $800.000" aunque el arriendo sea del 1 |
| 2026-09-17 | 2 | **`Periodo.anterior()` gana "esta semana a la fecha"**: de un lunes a un día de la misma semana se compara con la semana anterior hasta el mismo día. **Un solo día se compara siempre con el día anterior**, aunque sea el 1 del mes | Igual que *este mes*. Un día solo no se distingue de *este mes* el día 1: comparar con ayer es lo que se espera de *Hoy* |
| 2026-09-17 | 2 | **Los renglones sin costo cuentan lo cobrado con su parte del descuento** (neto), y `RepartoDeDescuento` se usa ya en la fase 2 | Es lo que de verdad se cobró por ellos |
| 2026-09-17 | 2 | **Rendimiento medido**: un año con 20.000 ventas y 50.000 renglones, 361 ms la primera vez y 268 ms la segunda. Una semana usa `idx_venta_cobrada_en` (la prueba lee el `EXPLAIN`) | El riesgo de pasar de 2 s no se dio: no hace falta agregar en SQL |
| 2026-09-17 | 2 | **Romper a propósito, hecho**: quitar el filtro de anuladas, cortar el día en hora universal, usar el costo promedio de hoy, quitar el resto de los dos repartos y que los gastos no lleguen a su fila: cada uno hace fallar su prueba | Las pruebas muerden. (Una primera corrida dio falsos "falla" porque el `bash` de Python era el de WSL; se repitió con el de Git) |
| 2026-09-17 | 3 | **La dirección lleva el tipo y una fecha** (`?tipo=MES&desde=2026-09-01`), o las dos fechas del rango; no "este mes". El atajo marcado se deduce del período | Un enlace abre siempre el mismo reporte, aunque pasen los días (RF-001). Llegar a *Ayer* con la flecha también lo marca |
| 2026-09-17 | 3 | **Sin nada en la dirección, el reporte abre en *Este mes*** | La ganancia se lee mejor en el mes; *Hoy* está a un toque |
| 2026-09-17 | 3 | **"Hoy" es el día de Colombia** (`hoyEnColombia`), no el del equipo | El servidor corta los días allá; un equipo con otra zona pediría "mañana" y recibiría un 422 |
| 2026-09-17 | 4 | **Repuestos y categorías son el mismo tipo (`Vendidos`)**, y **si algún renglón se vendió sin costo, su utilidad y su margen quedan en blanco** (también en la categoría) | Con un costo a medias, un repuesto o una categoría parecería más rentable de lo que es. Las cifras grandes sí restan lo conocido y llevan asterisco (RF-007): son el total y dicen cuánto falta |
| 2026-09-17 | 4 | **La respuesta trae todos los repuestos vendidos**; la pantalla ordena y muestra los 20 primeros | La lista completa es la que suma las ventas netas (y lo prueba); son a lo más unos miles de filas en un año |
| 2026-09-17 | 4 | **`ConsultarResultados` devuelve `ReporteDeResultados`**: los resultados, el período anterior con sus cifras (el mismo cálculo) y el control. El año de 20.000 ventas pasa a 530 ms en frío | La comparación no tiene su propia forma de sumar |
| 2026-09-17 | 4 | **Los descuentos del control no repiten el porcentaje**: el control dice cuántas ventas y cuánto; el porcentaje de lo vendido está en *Las cifras que importan* | RF-013 y RF-023 piden la misma cifra; se muestra una vez con todo y otra como resumen |
| 2026-09-17 | 4 | **La prueba de control cierra el turno con `CerrarTurno` y lo mueve por SQL a un día pasado**; lee lo que debería haber con `ConsultarTurnos` | `CalcularArqueo` no abre transacción por sí solo: fuera de un caso de uso falla por carga perezosa |
| 2026-09-17 | 6 | **QA limpia con la orden del usuario.** Respaldo `respaldos/qa-2026-09-17.sql` (88 KB, termina en *dump complete*); `DROP SCHEMA public CASCADE` en `rdmotors-postgres` (5433) y Flyway recreó V1 a V15. Comprobado: sin turno abierto, inventario vacío, 11 categorías de gasto (4 del mes), 16 de repuesto, contador de ventas en 0, tienda solo con *RD MOTORS* | Se empieza de cero (decisión 4). Queda borrar en el navegador de la tienda el borrador de venta (`localStorage` `rdmotors:venta-en-curso`), si hay uno |
| 2026-09-17 | 5 | **Las dos tarjetas de P3 se ven aunque el reporte no haya cargado**, cada una con su consulta, su aviso y su *Reintentar*; la de compras no se pide si el período tiene un problema | RF-025 y RF-026 son datos aparte: que falle uno no tumba el reporte. Verificado contra QA: el inventario se queda en $7.096.252 en dos semanas distintas y lo comprado cambia |
| 2026-09-17 | 3 | **Revisión visual con Edge sin interfaz y emulación de dispositivo**, solo lectura contra QA: claro, oscuro, 390 px, *Ver cálculo* y rango al revés (0 consultas). **No se crearon datos**: los casos del arriendo y la semana armada quedan cubiertos por las pruebas de dominio y de Postgres | El usuario no quiere recorridos que ensucien QA. A 390 px la cabecera global de la app se desbordaba (navegación y botón de tema): venía de antes y pasaba en todas las pantallas. **Arreglado el mismo día**: hasta 760 px las pestañas van en su propia fila |
