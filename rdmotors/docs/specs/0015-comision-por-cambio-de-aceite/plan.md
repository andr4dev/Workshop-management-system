# Plan 0015 · La comisión por cambio de aceite

**Traduce:** [`spec.md`](spec.md), **versión 2** (2026-09-29): el cajero escoge *se cambia / no se cambia* en cada
aceite; *no se cambia* baja el precio la comisión; *se cambia* saca la comisión del cajón al cobrar, como un gasto de
costo a nombre de la persona. Sin pantalla de pagar.

**Estado:** fase 1 hecha · fases 2 y 3 en implementación · **todo en local** (rama `spec-0015-comisiones`); a
producción solo cuando el dueño lo diga. En `main` quedó el revert de la fase 1 sin subir (la V29 se queda).

---

## Dónde vive cada pieza

| Pieza | Carpeta | Qué es |
|---|---|---|
| `Variante.comisionCambio` (hecha) | `domain/…/inventario/dominio/` | La marca del repuesto |
| `CambioDeAceite` (enum: `SE_CAMBIA`, `NO_SE_CAMBIA`) | `domain/…/ventas/dominio/` | La elección de un renglón |
| `LineaVenta`: `cambio`, `cambioPorId`, `comision`, `comisionGastoId` | `domain/…/ventas/dominio/` | El renglón guarda lo que se escogió, a quién, cuánto y su gasto |
| `PagarComisionDeCambio` | `domain/…/caja/aplicacion/` | Registra el gasto del cajón de la comisión; lo llama `CobrarVenta` en su transacción, como `FiarVenta` |
| Categoría del sistema *"Comisión cambio de aceite"* (costo) | V30 | La siembra la migración; el caso de uso la busca por nombre |

**La comisión es un gasto del cajón**: el arqueo, el cierre, su comprobante, su correo, *Reportes › Gastos* y *Ver
cálculo* ya lo cuentan. Por eso no hay tabla de comisiones ni pantalla nueva de reportes: el renglón sabe su gasto, y
el gasto dice la venta y la persona.

---

## Fase 1 · Qué repuestos pagan comisión — hecha (commit abab464, rama)

Marca y monto por repuesto, V29, `CambiarComisionDeCambio`, la tarjeta y su ventana en la ficha.

## Fase 2 · Escoger en la venta y pagar al cobrar (RF-002 a RF-008)

- **V30**: en `linea_venta`, `cambio` (`SE_CAMBIA`/`NO_SE_CAMBIA`, nulo en los que no pagan comisión),
  `cambio_por_id` → usuario, `comision numeric(14,2)`, `comision_gasto_id` → gasto; `CHECK`: *se cambia* lleva
  persona, comisión y gasto; *no se cambia* lleva comisión y no persona ni gasto. La categoría *"Comisión cambio de
  aceite"* (costo), si no existe.
- `ComandoCobrarVenta.Renglon` + `cambio` y `cambioPorId` (un constructor sin ellos para lo de antes).
- `LineaVenta.de(variante, cantidad, cambio, cambioPorId)`: si el repuesto paga comisión, **exige** la elección; *no
  se cambia* → precio unitario = precio − comisión (una comisión mayor o igual al precio no se deja); *se cambia* →
  precio del repuesto y guarda la comisión × cantidad. Si el repuesto no paga, la elección no puede venir.
- `CobrarVenta`: el precio visto se compara con el precio **según la elección**; ya guardada la venta, por cada
  renglón que se cambia, `PagarComisionDeCambio` registra el gasto del cajón (sin pedir confirmar), con la
  descripción *"venta N.º 12 · MOTUL 7100 10W30 · Gustavo"*, y el renglón guarda su id. La persona tiene que ser un
  usuario activo.
- `GET /api/ventas/personas`: los usuarios activos (id y nombre), para escoger quién cambió (el cajero no ve
  `/api/usuarios`).
- Pantalla (`Vender`, `utils/venta.js`): el renglón de un repuesto con comisión muestra *¿Se cambia aquí?* **[Sí] [No
  (−$3.000)]**, sin nada escogido; con *Sí*, *¿Quién?* (de entrada quien registra). El precio y el total siguen la
  elección. Sin escoger, *Cobrar* no se habilita y dice cuál falta. Viaja en el borrador y en el comando.
- Comprobante: el renglón *no se cambia* dice *"sin cambio"*.

**Pruebas:** `LineaVentaTest` (precio sin cambio; exige elección; sin marca no acepta), `CobrarVentaTest` (se cambia →
un gasto por renglón con la comisión × cantidad; no se cambia → cobra menos y sin gasto; precio visto según la
elección; persona inactiva), `CajaYVentasIntegracionTest` o uno nuevo contra Postgres (venta y gasto en una
transacción; el esperado del cierre baja), `MigracionesIntegracionTest` (V30), `venta.test.js` (el renglón, el total,
el comando, el borrador, cobrar sin escoger).
**Romper:** cobrar sin escoger; *no se cambia* a precio lleno; *se cambia* sin gasto; la comisión de hoy en vez de la
del cobro.
**Checkpoint (local):** cobrar un MOTUL 7100 *se cambia · Gustavo* deja un gasto de $3.000 en el cajón a su nombre;
*no se cambia* cobra $62.000.

## Fase 3 · Anular y ver (RF-009 a RF-011)

- `AnularVenta`: por cada renglón con gasto de comisión, lo anula si su turno sigue abierto (motivo *"Se anuló la
  venta N.º 12"*); si no, lo deja y el resultado lo avisa.
- *Ver cálculo* y *Reportes › Gastos* ya muestran los gastos de costo con su enlace (spec 0014): se revisa que la
  comisión salga con la persona, y en *Gastos* el filtro por la categoría.
- Pruebas: anular en el mismo turno (el gasto se anula, el esperado vuelve) y en otro (se queda); el reporte resta la
  comisión como costo.
**Checkpoint (local):** anular la venta del MOTUL devuelve los $3.000 al esperado del cajón.

---

## Verificación

1. `./mvnw clean install` y en el frontend `eslint`, `node --test` y `vite build`.
2. En local, con el servidor en 8081 y la pantalla en `http://127.0.0.1:5174` (usuario `ruben`): una venta de cada
   caso, el cierre y el reporte.
3. Capturas con Edge sin interfaz, desde PowerShell.
4. Romper a propósito por fase.
5. **Nada a producción** sin la orden del dueño.

## Al desplegar (solo con la orden del dueño)

1. **Fuera del horario de la tienda**: Render tarda ~2 minutos en arrancar y en ese tiempo la app no responde.
2. **Subir la rama encima de `origin/main`** (avance directo, comprobado). El revert local de `main` (`6a90baf`) **no
   se sube ni se mezcla**: nunca salió del computador y, mezclado, borraría en silencio lo de la fase 1.
3. Esperar a Render y comprobar en solo lectura `/api/salud` y que Flyway quedó en la V30.
4. **Que todos recarguen la app (Ctrl+F5)** antes de marcar aceites: una pantalla vieja no deja escoger y, con el
   aceite ya marcado, no lo deja cobrar.
5. Marcar los aceites de motor que pagan comisión (en producción son 12, de 9 marcas; no el lubricante de cadena ni
   el Mercury), con su monto. Con la orden del dueño, por la API.

Lo vendido antes del despliegue **no se toca** (ver la bitácora, 2026-09-30).

Ensayado el 2026-09-29 sobre una copia de producción: la V30 tardó 66 ms, y los reportes, las 14 ventas, los turnos,
la cartera y los gastos se vieron **idénticos** antes y después (25 vistas comparadas campo por campo).

## Bitácora de decisiones

| Fecha | Fase | Decisión | Por qué |
|---|---|---|---|
| 2026-09-29 | 1 | **La marca va por un endpoint propio** (`PUT /api/repuestos/{id}/comision`) | No obliga a reenviar la ficha entera y no cambia el comando de corregirla |
| 2026-09-29 | 1 | Cierre de la fase 1: 566 del dominio y la suite de Postgres; 316 de pantalla; captura de la ficha | — |
| 2026-09-29 | — | **Nada a producción sin la orden del dueño**: la fase 1 se había subido; en `main` quedó un revert **sin subir** que deja la V29 | La tienda trabaja con la app en vivo. Borrar el archivo de una migración ya aplicada no deja arrancar el servidor |
| 2026-09-29 | — | **Versión 2 del spec: la comisión es un gasto del cajón de costo, registrado al cobrar**, no una tabla con pagos | El dueño no quiere un paso de "pagar": sale del cajón al cobrar. Siendo un gasto, el cierre, los reportes y *Ver cálculo* ya la cuentan, y las fases 3 a 5 del plan anterior (pagar, reportes, lo del cajero) dejan de hacer falta |
| 2026-09-29 | 2 | **En el catálogo, el aceite se pregunta al agregarlo**, en su misma fila: un toque dice que sí y quién lo cambia (el que vende va primero), o que no con el precio sin la comisión. Bajo *Cobrar* se dice qué falta, con *Ver en la venta* si el catálogo lo tapa | El dueño lo probó: desde el catálogo no se ven los renglones, así que había que volver a la venta para escoger y *Cobrar* se quedaba apagado sin decir por qué; en el celular, peor. Se sigue escogiendo siempre (nada viene escogido) |
| 2026-09-29 | 2 | **En el catálogo, tocar la fila solo la marca; se agrega con el + o con Enter**. Una unidad más de un aceite ya escogido sigue lo escogido y el mensaje lo dice | El dueño tocó la fila y se agregó sin preguntar (ese aceite ya estaba escogido en la venta). Un repuesto va en un solo renglón (el servidor rechaza repetirlo), así que no puede tener dos elecciones |
| 2026-09-29 | 2 | **Prueba de punta a punta** (`CambioDeAceiteIntegracionTest`): por HTTP contra el servidor y un Postgres 18 propio, un día entero (con cambio, sin cambio, descuento, fiados con abono total y parcial, anulada en el turno y en el siguiente, reintento, seis cobros a la vez) y las cifras escritas a mano: arqueo 495.000, cierre sin diferencia, reporte (ventas netas 445.000, costo 270.714, comisiones 30.000, utilidad bruta 144.286) y en la base cada comisión con su renglón. Rota a propósito dos veces (sin devolver la comisión al anular; sin multiplicar por la cantidad) y las dos se atrapan | El dueño pidió comprobar que todo cuadre y revisar lo transaccional |
| 2026-09-29 | 2 | **La categoría «Comisión cambio de aceite» no se renombra ni se desactiva** | El cobro la busca por su nombre: renombrarla o desactivarla dejaba sin cobrar toda venta de un aceite que se cambia, con un mensaje que el cajero no puede resolver ("elige otra") |
| 2026-09-30 | — | **Se cuenta desde el despliegue; lo vendido antes no se toca**: ni un enlace para actualizar las ventas pasadas ni comisiones cargadas hacia atrás. Si a alguien se le deben comisiones de antes, se registran a mano como un gasto de la categoría «Comisión cambio de aceite» | Lo dijo el dueño. Esas ventas ya se cobraron a precio completo y una venta cobrada no se edita; marcarlas ahora solo movería reportes de días ya revisados (12 renglones, 14 unidades, del 26 al 30 de septiembre) |
