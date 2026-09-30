# Plan 0016 · Abonar por producto

## Context

El dueño pidió (2026-09-30) que al abonar un fiado **se vea la compra con sus productos** y se pueda **pagar por
producto**, no solo por venta: en el mostrador el cliente dice *«le pago el aceite»*. El spec está en
`docs/specs/0016-abonar-por-producto/spec.md` (commit `6ef3b19`) y el usuario pidió el plan sin cambiarlo, con una
respuesta a la aclaración de la decisión 2: **cuando paga una parte al llevárselo, el cajero escoge qué productos cubre
ese pago** (no se reparte en proporción).

Todo se hace **en local**, en la rama `spec-0015-comisiones` (encima de lo del aceite, sin desplegar), con el mismo
cuidado que el 0015: nada a producción sin la orden del dueño.

### Las decisiones que manda el plan
1. **Una deuda por producto** (recomendación A del spec): cada producto fiado es su propia deuda, con la venta de
   referencia. Las reglas de hoy (a lo más viejo, a favor, anular abono, anular venta) no cambian: cambia la unidad.
2. **Lo que se paga al llevárselo lo escoge el cajero** (respuesta del usuario): en *Cobrar › Fiado* marca qué productos
   paga ahora y *Paga ahora* se llena con su valor. Lo pagado cubre primero lo marcado y, si sobra, los demás en el orden
   de la venta; **sin marcar nada, en el orden de la venta** (no en proporción). El **descuento** sí se sigue repartiendo
   en proporción al valor de cada producto (no hay qué escoger: es la regla del comprobante y de los reportes).
3. **Abono sin marcar: a lo más viejo, producto por producto** en el orden de la venta.
4. **Lo que ya existe**: una deuda de un solo producto pasa a ese producto; una de varios productos **sin abonos y sin
   nada pagado al llevárselo** se parte (solo con el reparto del descuento); cualquier otra queda *por venta* (no se sabe
   qué cubrió lo pagado). En producción: todas pasan; la N.º 18 se parte en dos.
5. **Los reportes no cambian** (siguen leyendo lo cobrado por venta).

### Hechos verificados que mandan el diseño
- Fiar crea una sola deuda: `domain/…/clientes/aplicacion/FiarVenta.java:58-66`; la base lo exige:
  `V20__clientes_y_fiado.sql:83` (`ux_deuda_venta`).
- **Riesgo de caída**: `ConsultarVentas.java:108` arma `toMap(Deuda::getVentaId, …)`; con dos deudas de la misma venta
  revienta *Ventas del turno* y el detalle de la venta. Hay que juntarlas.
- Anular busca **una** deuda: `FiarVenta.java:72-84` (`deudas.deLaVenta` → `Optional`).
- `abonar(abono, primeroA, …)` acepta **una** deuda: `CarteraDelCliente.java:118-143`.
- Orden de pago por fecha, registro e id (`ORDEN_DE_PAGO`): los productos de una venta nacen en el mismo instante →
  hace falta la posición para que el orden no sea al azar.
- `deuda.monto > 0` (`V20:65`): un producto que quede en $0 fiado **no** crea deuda.
- Los reportes suman los abonos por `deuda.venta_id` (`RepositorioReportesJdbc.java:67-73, 219-230`): con varias deudas
  por venta dan lo mismo.
- El reparto del descuento ya existe: `reportes/dominio/RepartoDeDescuento.netos` (piso y el sobrante al más caro).
- La pantalla de cobro ya recibe el pedido desglosado (`desgloseDelCobro`, commit `5b24c04`) y la ficha ya trae qué se
  llevó (`VentaDeLaDeuda`, commit `63df2a1`).

## Dónde vive cada pieza

| Pieza | Carpeta |
|---|---|
| `RepartoDeDescuento` (se **mueve** de `reportes/dominio`) | `compartido/dominio/` — lo usan reportes y clientes |
| `Deuda.porProducto`, orden por posición, `nombre()` con el producto | `clientes/dominio/` |
| `CarteraDelCliente.registrarDeudas`, `anularDeudas`, `abonar(…, List<UUID> primero, …)` | `clientes/dominio/` |
| `RepositorioDeudas.deLaVenta` → lista | `clientes/dominio/puerto/` |
| `FiarVenta.registrar` con los renglones y lo que paga primero | `clientes/aplicacion/` |
| `ComandoCobrarVenta.pagaPrimero`, `CobrarVenta` le pasa los renglones | `ventas/aplicacion/` |
| V31, JPA, SQL de la cartera, controladores | `pos/…/infraestructura/`, `pos/…/db/migration/` |

## Fase 0 · Los documentos
- `spec.md`: la decisión 2 pasa a *el cajero escoge*; RF-001 lo dice; un RF nuevo para marcar en el cobro y su error.
  Estado → *con plan*. `plan.md` con esto. Línea del índice (`docs/specs/README.md`).

## Fase 1 · La deuda por producto (servidor)
- **Mover** `RepartoDeDescuento` y su prueba a `compartido/dominio` (lo importan `LoCobrado` y `FiarVenta`).
- **V31** `deuda_por_producto`:
  - `deuda` + `linea_venta_id` (→ `linea_venta`), `posicion int`, `descripcion varchar(200)`; CHECK: los tres juntos, y
    solo en origen `VENTA`.
  - Quitar `ux_deuda_venta`; poner `ux_deuda_linea (linea_venta_id)` y `ux_deuda_venta_entera (venta_id) WHERE
    linea_venta_id IS NULL`: una venta tiene **o** una deuda entera **o** una por producto.
  - Llenar (decisión 4): venta de 1 renglón → se anota el renglón; varios renglones, **sin aplicaciones, sin anular y
    con fiado = total** → la fila original queda como el primer producto y se insertan los demás, con el neto de cada
    uno por el reparto del descuento hecho en SQL (piso y el sobrante al más caro, el primero si empatan). El resto
    queda igual. Idempotente y sin tocar montos de lo que no se parte.
- `Deuda.porProducto(…, lineaVentaId, posicion, descripcion, monto)`; `nombre()` → *"el MOTUL 7100 10W30 de la venta
  N.º 18"*; `ORDEN_DE_PAGO` = fecha, registro, **posición**, id.
- `CarteraDelCliente.registrarDeudas(List, cuando)`: agrega todas, aplica lo que haya a favor una vez y anota a todas el
  mismo *debe después*. `anularDeudas(List, …)`: libera todo primero, anula todas, y reparte una sola vez (anularlas de a
  una movería lo abonado de un producto al otro de la misma venta).
- `FiarVenta.registrar(cliente, venta, renglones, pagaPrimero, …)`: neto de cada renglón con `RepartoDeDescuento.netos`;
  lo pagado (`total − fiado`) cubre los marcados en su orden y luego los demás por posición; una deuda por producto con
  lo que quede > 0. `alAnular` anula todas las de la venta.
- `ComandoCobrarVenta` + `pagaPrimero` (ids de variante; solo con fiado; uno que no está en la venta → error).
  `CobrarVenta` pasa los renglones guardados. `VentaController.PeticionCobro` + `pagaPrimero`.
- `RepositorioDeudas.deLaVenta` → `List`; `ConsultarVentas.java:108` junta las de una venta (mismo *debe después*).
- La lista de la Cartera cuenta **ventas** pendientes: `ResumenDeCliente` y `ConsultasDeCarteraJdbc.resumen`
  (`count(distinct coalesce(venta_id, id))`).
- **Pruebas** (dominio): fiar dos productos sin pagar nada (precios exactos); con pago marcando el filtro (el MOTUL
  queda en su precio); con pago sin marcar (cubre el primero); con descuento; un producto en $0 no crea deuda;
  anular la venta anula todas y lo abonado va a las otras deudas sin pasar por el hermano; orden por posición.
  **Integración**: `MigracionesIntegracionTest` V31 con filas de antes (1 renglón, varios sin abono, varios con abono,
  con pago al llevárselo, anulada, cuaderno) y el reparto SQL comparado con `RepartoDeDescuento`; *Ventas del turno* con
  una venta de dos productos fiada no revienta. **Romper**: quitar la posición del orden; anular de a una.

## Fase 2 · Abonar a los productos marcados (servidor)
- `CarteraDelCliente.abonar(abono, List<UUID> primero, cuando)`: cada marcado en su orden, `menor(queda, pendiente)`;
  lo que sobre, a lo más viejo. Errores del spec §6 con `nombre()` (*"El MOTUL 7100 de la venta N.º 18 ya está
  pagado"*, de otro cliente). La firma de un solo `primeroA` delega.
- `RegistrarAbono` y `AbonoController.PeticionAbono` + `primero` (lista); `primeroA` se sigue aceptando (pantalla vieja).
- **Pruebas**: marcar dos (los dos pagados); menos de lo marcado (el último a medias); más (sobra a lo más viejo); ya
  pagado; de otro cliente; anular ese abono devuelve cada producto; recibo con el nombre del producto.

## Fase 3 · Las pantallas
- **Ficha** (`FichaCliente.jsx`): agrupada por venta — cabecera (número, fecha, lo fiado, estado de la venta) y cada
  producto con *cantidad × precio* (del renglón de `VentaDeLaDeuda`, que suma `lineaId`), lo fiado, abonado, pendiente,
  su estado y sus abonos. Una deuda *por venta* se ve como hoy.
- **Abonar** (`ModalAbono.jsx`): en lugar de la lista *"A cuál se aplica"*, las ventas pendientes con sus productos, una
  casilla por producto y una por venta; *Cuánto abona* se llena con lo marcado y se puede cambiar; manda `primero` en el
  orden de la lista. Cabe a 390 px.
- **Cobrar › Fiado** (`ModalCobro.jsx`): bajo *Paga ahora*, los productos con *"lo paga ahora"*; marcar llena *Paga
  ahora* con su valor (neto del descuento, con el mismo reparto en JS); sin marcar, nada cambia para el caso común.
- `utils/cartera.js` (agrupar, estado de la venta), `utils/venta.js` (reparto del descuento en JS, valor de lo marcado,
  `pagaPrimero` en el comando), `api/cliente.js`; recibo e impresión de la cartera con los nombres nuevos.
- **Pruebas**: `cartera.test.js`, `venta.test.js` (el reparto en JS da lo mismo que el Java en los mismos casos).
  **Capturas** con Edge contra el servidor local, claro y oscuro, 1280 y 390: la ficha, abonar marcando, cobrar fiado
  marcando.

## Fase 4 · De punta a punta y ensayo en una copia de producción
- **Prueba por HTTP** (`FiadoPorProductoIntegracionTest`, Postgres 18 propio): fiar MOTUL + filtro pagando el filtro,
  abonar marcando el MOTUL, anular ese abono, anular la venta; la ficha, la cartera, el arqueo y el reporte del día con
  cifras escritas a mano (las mismas que daría la deuda por venta).
- **Ensayo** como el del 0015: copia de producción en un contenedor desechable (solo lectura sobre producción), foto
  antes (versión de producción) y después (rama): reportes, ventas, turnos, cartera iguales; fichas con los mismos
  totales por venta; la N.º 18 en dos productos; un abono por producto sobre la copia. Se borra todo al terminar.

## Verificación
1. Backend en una copia aparte (`git worktree`, para no tumbar el servidor local): `./mvnw clean verify`.
2. Frontend: `npx eslint src/ && node --test src/utils/*.test.js && npx vite build`.
3. Local: servidor 8081 reiniciado, pantalla en `http://127.0.0.1:5174` (`ruben` / `rdmotors2026`), capturas desde
   PowerShell.
4. Romper a propósito por fase (orden sin posición, anular de a una, marcar sin respetar el orden).
5. **Nada a producción** sin la orden del dueño; commits solo en la rama local.

## Bitácora de decisiones

| Fecha | Fase | Decisión | Por qué |
|---|---|---|---|
| 2026-09-30 | — | **El cajero escoge qué cubre lo que se paga al llevárselo**; sin marcar, en el orden de la venta | Aclaración del usuario a la decisión 2 del spec: cifras redondas en lugar del reparto en proporción |
| 2026-09-30 | 1 | **Una venta tiene o una deuda entera o una por producto**; la base cuida lo primero (`ux_deuda_venta_entera`, `ux_deuda_linea`) y el dominio que no se mezclen | Un índice que prohíba mezclarlas necesita un trigger; fiar nunca crea las dos |
| 2026-09-30 | 1 | **Para los mensajes, "el repuesto X de la venta N.º 18"; para el recibo y la ficha, "X (venta N.º 18)"** (`Deuda.nombre()` y `etiqueta()`) | Con "el repuesto" la frase concuerda con cualquier producto ("ya está pagado"); detrás de un monto se lee mejor sin artículo |
| 2026-09-30 | 1 | `RepartoDeDescuento` se mudó a `compartido/dominio`; su prueba se quedó en `reportes/dominio` | Lo usan reportes y la cartera; la prueba usa las ayudas de los reportes |
| 2026-09-30 | 1 | Rotas a propósito: sin la posición en el orden de pago (falla `sinMarcarYYaPagado`) y anulando de a una (falla `anularLaVenta`, después de fortalecerla: al principio abonaba al segundo producto y no lo veía) | — |
| 2026-09-30 | 4 | **Punta a punta por HTTP** (`FiadoPorProductoIntegracionTest`, Postgres 18): cajón 122.000, ventas netas 22.000, costo 13.026, Juan debe 54.000, escritas a mano; pasó al primer intento | — |
| 2026-09-30 | 4 | **Ensayo sobre una copia de producción** (22 ventas, 11 deudas, 4 abonos): V30 + V31 en 101 ms; **34 vistas y todas las fichas iguales** (por cliente, por venta y cada abono); la N.º 18 en dos productos (GUAYA 26.120 + MOTUL 62.880, con el descuento repartido); un abono marcando la guaya y un fiado nuevo pagando la guaya cuadraron en el cajón. La copia se borró | Es lo que pasará al desplegar |

