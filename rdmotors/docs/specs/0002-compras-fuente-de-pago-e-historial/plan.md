# Plan 0002 — Compras: forma de pago, historial y corrección

**Traduce:** [`spec.md`](spec.md) (cerrado)
**Estado:** aprobado el 2026-09-13 · fases 1 a 5 ✅ completas (todo P1) · fase 6 (P2 y P3) pendiente, arranca cuando se diga

---

## Contexto

Al usar la rebanada 1 salieron tres huecos en compras:

- No se registra con qué se pagó.
- Una compra no se puede volver a ver.
- Una compra mal registrada deja stock y costo promedio mal para siempre.

El spec 0002 cerró qué construir:

- forma de pago Efectivo o Transferencia con su cuenta;
- historial;
- corregir datos de la factura;
- corregir renglones **por renglón**;
- anular;
- auditoría, incluida la de corregir ficha.

Crédito y caja quedaron fuera.

Con qué choca, verificado:

| Qué | Dónde |
|---|---|
| La reversión que existe **nunca toca el promedio**; fue pensada para ventas | `TipoMovimiento.java:18`, `Variante.java:166-170` |
| El precio anterior a una compra **no se guarda** | `RegistrarCompra.java:98-100` |
| **No existe** tabla de auditoría | — |
| El kardex se ordena por `creado_en`, y una corrección crea la reversión y la entrada nueva del mismo repuesto **en el mismo instante** | — |
| Boot 4 trae **Jackson 3** (paquete `tools.jackson`) | `~/.m2` |

---

## Resumen

Seis fases. Las cinco primeras cubren todo P1; la sexta, P2 y P3. La séptima llegó con la
ampliación del spec del 2026-09-14 (H9), pedida en el QA.

- **La fase 4 va sola y sin pantalla.** Es la matemática de revertir una compra: el único cambio
  delicado, y el que más cuesta arreglar tarde.
- **Una migración por fase** (V3 a V7; la fase 2 también necesitó la suya, ver bitácora). Cada fase deja la base en un estado desplegable.

| Fase | Qué | Pantalla | Migración |
|---|---|---|---|
| 1 | Forma de pago y cuentas | registrar compra | V3 |
| 2 | Historial y detalle (solo lectura) | Compras › Historial | V4 |
| 3 | Auditoría + corregir datos de la factura (H3) | detalle + corregir cabecera | V5 |
| 4 | Revertir una compra en el dominio | — | V6 |
| 5 | Corregir renglones (H4) y anular (H5) | corregir completo + anular + kardex | V7 |
| 6 | Totales (H6), desactivar cuenta, auditoría de ficha (H7), kardex → compra (H8) | varias | — |
| 7 | Buscar las compras de un repuesto (H9) | historial + detalle | — |

Recordatorio de dónde cae cada cosa:

| Carpeta | Qué va | Cómo se reconoce |
|---|---|---|
| `domain/…/dominio/` | reglas y sustantivos | tiene cuerpo, hace algo |
| `domain/…/dominio/puerto/` | lo que el dominio pide | todo termina en `;` |
| `domain/…/aplicacion/` | casos de uso | abren la transacción, no calculan |
| `pos/…/infraestructura/` | adaptadores y endpoints | importan Spring, JPA o HTTP |

---

## Decisiones tomadas al planear

1. **Un solo botón "Corregir".** Abre la pantalla de registrar compra cargada con la factura:
   cabecera y renglones juntos. Detrás hay un solo caso de uso, `CorregirCompra`; si los renglones
   llegan `null`, significa "no tocar renglones" (así la fase 3 funciona antes que la 5).
2. **Cambiar solo el precio de un renglón no mueve inventario.** No genera kardex y no se bloquea
   por ventas: el precio no cambia stock ni costo. Queda una versión nueva del renglón y se aplica
   la regla del precio.
3. **El kardex gana `secuencia`, el orden sin empates.** La reversión y la entrada corregida
   comparten instante; con `creado_en` una segunda corrección tomaría el promedio equivocado. Los
   `antes de` / `después de` se preguntan por secuencia, y el historial se ordena por ella.
4. **Dos tipos de movimiento nuevos: `CORRECCION_COMPRA` y `ANULACION_COMPRA`.** Son salidas que
   **sí** recalculan el promedio. `REVERSION` queda para ventas y su javadoc lo dice. La entrada
   corregida es un `COMPRA` normal que lleva el motivo.
5. **Auditoría: el dominio arma el antes y el después; la infraestructura los vuelve JSON.** El
   dominio los arma como mapas de valores simples; el adaptador los guarda como `jsonb` con el
   `JsonMapper` de Jackson 3. El dominio no importa Jackson. El puerto `RepositorioAuditoria` se
   justifica por eso y por su doble en memoria.
6. **Concurrencia en dos capas.**
   - Bloqueo de fila sobre la compra (`buscarParaModificar`).
   - `@Version Long version`, que la pantalla envía. Si no coincide: 409 *"La compra cambió
     mientras la corregías"*.
7. **`InventarioDeCompra`, una sola pieza para entrar y revertir.** La usan registrar, corregir y
   anular, para no repetir la regla del promedio, el precio anterior y el kardex tres veces. La
   creación de un repuesto nuevo sigue delegada a `CrearRepuesto`.
8. **La cuenta vive en `compras/`.** Hoy solo la usan las compras; si la caja o los gastos la
   necesitan, se mueve entonces.
9. **Corregir y anular son `POST`.** Son hechos nuevos, que **suman**; en la rebanada 4 llevarán
   llave de idempotencia.
10. **El historial arma su consulta según los filtros presentes.** No se usa
    `(:param is null or …)`. *Corregido en la fase 2 tras verificarlo:* esa forma aguanta ids y
    fechas nulos, pero revienta con la búsqueda de texto (`lower(bytea)`). Ver bitácora.

---

## Fase 1 · Forma de pago y cuentas — ✅ COMPLETA

> **Cerrada el 2026-09-13.** 96 pruebas de dominio (eran 85), 18 contra Postgres real (eran 13)
> y 29 del frontend (eran 26). Lint y build limpios.
>
> **V3 aplicada sobre la base de QA:** sus 12 compras quedaron en Efectivo y la columna quedó sin
> valor por defecto. Verificado en vivo: transferencia sin cuenta → 422 con su mensaje; sin forma
> de pago → 400; nada se guardó.
>
> Se rompieron a propósito la regla de la cuenta desactivada, la de efectivo con cuenta y el
> relleno de V3: fallaron exactamente las pruebas que las guardan. Ver bitácora.
>
> **Pendiente del checkpoint, en el navegador:** registrar una compra en efectivo y otra por
> transferencia creando "Nequi del dueño". Las capturas muestran la pantalla, pero sin hacer clic.

**Cubre:** RF-001, RF-002, RF-003

| Pieza | Dónde cae |
|---|---|
| `FormaPago` (EFECTIVO, TRANSFERENCIA) | `compras/dominio/` |
| `CuentaPago` — `nueva(nombre)`, `desactivar()` | `compras/dominio/` |
| `RepositorioCuentas` — buscar, porNombre, activas, guardar | `compras/dominio/puerto/` |
| `Compra` gana `formaPago` y `cuenta`; `registrar(...)` exige transferencia ⇔ cuenta | `compras/dominio/` |
| `ComandoRegistrarCompra` gana `formaPago`, `cuentaId`, con validación | `compras/aplicacion/` |
| `RegistrarCompra` resuelve la cuenta y la exige activa | `compras/aplicacion/` |
| `RegistrarCuenta` — nombre obligatorio, sin duplicados ignorando mayúsculas y espacios | `compras/aplicacion/` |
| `V3__forma_de_pago.sql` | `pos/…/db/migration/` |
| `RepositorioCuentasJpa`, `CuentaController` (`GET` y `POST /api/cuentas`) | `compras/infraestructura/` |
| `CompraController` recibe y devuelve forma de pago y cuenta | `compras/infraestructura/` |

**V3:**

- Tabla `cuenta_pago`, con índice único sobre `lower(nombre)` (los espacios los normaliza el dominio; ver bitácora).
- `compra.forma_pago`:
  - se agrega `NOT NULL DEFAULT 'EFECTIVO'` con su `CHECK`, así las compras de QA quedan en
    Efectivo;
  - después se hace `DROP DEFAULT`, para que ninguna inserción nueva lo herede sin decidirlo.
- `compra.cuenta_id`, con FK a `cuenta_pago`.
- `CHECK ((forma_pago = 'TRANSFERENCIA') = (cuenta_id IS NOT NULL))`.

**Frontend:**

- En la cabecera de `Compra.jsx`: selector Efectivo | Transferencia, y un select de cuenta con
  "+ Nueva" que abre `componentes/compra/ModalCuentaNueva.jsx`. Mismo patrón que
  `ModalProveedorNuevo.jsx`.
- `cuentasApi` en `api/cliente.js`.
- `problemasDelPago()` y `textoDelPago()` en `utils/compra.js`, con sus pruebas.
- La vista de éxito muestra la forma de pago.

**Pruebas:**

- `RegistrarCompraTest`:
  - Solo cambia el helper `registrar(...)`: gana `EFECTIVO`. Es construcción, **ninguna aserción
    se toca**; es el mismo guardarraíl de la fase 4 del plan 0001.
  - Casos nuevos: sin forma de pago, transferencia sin cuenta, efectivo con cuenta, cuenta
    inactiva, cuenta inexistente, persiste forma y cuenta.
- `RegistrarCuentaTest`.
- `Falsos.CuentasEnMemoria`.
- Integración:
  - las 9 llamadas existentes se actualizan (construcción);
  - una compra por transferencia hace ida y vuelta;
  - el `CHECK` de la base rechaza transferencia sin cuenta, saltándose el caso de uso;
  - **nuevo `MigracionesIntegracionTest`**: Flyway hasta V2 sobre un contenedor limpio, inserta una
    compra por JDBC, migra hasta la última, y verifica que queda `EFECTIVO`.
- `.http`: los `POST /api/compras` ganan `formaPago`; se agregan las peticiones de cuentas.

**Checkpoint:** desde el navegador se registran una compra en efectivo y otra por transferencia,
creando "Nequi del dueño" al vuelo. La base de QA arranca y sus compras quedan en Efectivo.

---

## Fase 2 · Historial y detalle (solo lectura) — ✅ COMPLETA

> **Cerrada el 2026-09-13.** 104 pruebas de dominio (eran 96), 21 contra Postgres real (eran 18)
> y 37 del frontend (eran 29). Lint y build limpios.
>
> **Necesitó una migración que el plan no preveía** (V4, la posición de cada renglón): la
> numeración de las siguientes corre un lugar. Y **la decisión 10 se corrigió al verificarla**
> contra Postgres. Ver bitácora.
>
> Verificado en vivo sobre la base de QA: el historial trae las 12 compras, FV-9912 se encuentra
> por número parcial y su detalle cuadra al peso ($238.172); rango al revés → 422; compra que no
> existe → 404. Se rompió a propósito el filtro "hasta" y la prueba de integración falló justo ahí.

**Cubre:** RF-005, RF-006 y RF-008 (sin estado ni rastro, que llegan en las fases 3 y 5), RF-007,
RF-009

| Pieza | Dónde cae |
|---|---|
| `FiltroCompras` — proveedor, desde, hasta, forma, cuenta, factura; valida desde ≤ hasta | `compras/dominio/` |
| `ResumenCompra` — fila del historial | `compras/dominio/` |
| `RepositorioCompras` + `historial(filtro, pagina, tamano): Pagina<ResumenCompra>` y `buscarConLineas(id)` | `compras/dominio/puerto/` |
| `ConsultarCompras` — `historial` (sanea la página, igual que `BuscarRepuestos.inventario`) y `detalle(id)` | `compras/aplicacion/` |
| `DetalleCompra` — cabecera y renglones con el resumen actual del repuesto | `compras/aplicacion/` |
| Consulta armada según los filtros presentes, conteo aparte, orden `fecha_documento desc, fecha_registro desc, id` | `compras/infraestructura/RepositorioComprasJpa` |
| `GET /api/compras?...` y `GET /api/compras/{id}` | `compras/infraestructura/CompraController` |

**Frontend:**

- Rutas `/compras/historial` y `/compras/historial/:id`.
- `componentes/compra/PestanasCompras.jsx`, con Registrar | Historial.
- `paginas/HistorialCompras.jsx`:
  - filtros en la URL, como `Inventario.jsx`;
  - reutiliza `paginaDeLaUrl` y `rangoDePagina` de `utils/inventario.js`.
- `paginas/DetalleCompra.jsx`: cabecera, renglones con margen, total.
- `utils/historial.js` (filtros ⇄ URL, rango de fechas) y su prueba.

**Pruebas:**

- `ConsultarComprasTest`: filtro inválido, página saneada, detalle inexistente.
- Integración:
  - cada filtro contra Postgres, **incluidos los nulos**;
  - el conteo cuadra con el listado;
  - el total del detalle es la suma de sus renglones.

**Checkpoint:** se busca FV-9912, se abre, y se ven todos sus renglones con el total cuadrando.

---

## Fase 3 · Auditoría y corregir los datos de la factura (H3) — ✅ COMPLETA

> **Cerrada el 2026-09-13, junto con las fases 4 y 5** (se pidieron las tres a la vez). La
> migración quedó como **V5**, no V4: la fase 2 usó la V4.
>
> Además de lo planeado: `compra.modificada_en`. Sin ella, corregir **solo un precio** no cambia la
> fila de la compra y la versión no sube, así que dos correcciones de precio a la vez no chocarían.
> Ver bitácora.

**Cubre:** RF-008 (rastro), RF-012, RF-013

| Pieza | Dónde cae |
|---|---|
| `EventoAuditoria` (record, no entidad JPA) y `AccionAuditada` (CORREGIR_COMPRA, ANULAR_COMPRA, CORREGIR_REPUESTO) | `compartido/dominio/` |
| `RepositorioAuditoria` — `registrar`, `historialDe(tipo, id)` | `compartido/dominio/puerto/` |
| `Compra`: `@Version Long version`, `corregirDatos(...)`, `fotografia()` → mapa del antes/después | `compras/dominio/` |
| `CompraModificadaException` | `compras/dominio/` |
| `ComandoCorregirCompra` (renglones `null` = no tocar) y `CorregirCompra` | `compras/aplicacion/` |
| `V5__auditoria.sql` | `pos/…/db/migration/` |
| `RepositorioAuditoriaJpa` — `jsonb` con el `JsonMapper` que ya configura Spring | `compartido/infraestructura/` |
| `POST /api/compras/{id}/correcciones`; 409 en `ManejadorDeErrores` | infraestructura |

**V5:**

- Tabla `evento_auditoria`, con índices `(entidad_tipo, entidad_id, ocurrido_en)` y
  `(usuario_id, ocurrido_en)`.
- `compra.version bigint NOT NULL DEFAULT 0`.

**`CorregirCompra`, en orden:**

1. Bloquea la compra.
2. Revisa que la versión coincida.
3. Exige motivo.
4. Resuelve proveedor y cuenta. Una cuenta inactiva solo se acepta si ya era la de la compra.
5. Toma la foto del antes.
6. Aplica `corregirDatos`.
7. Si no cambió nada, responde "No hay cambios que guardar".
8. Toma la foto del después.
9. Registra el evento y guarda.

**Frontend:**

- `/compras/:id/corregir` reutiliza `Compra.jsx` en modo corrección. En esta fase la cabecera es
  editable y los renglones se ven solo de lectura.
- `componentes/ModalMotivo.jsx`: lista los cambios y exige el motivo.
- `utils/auditoria.js` — `cambiosEntre(antes, despues)` convierte las fotos en frases legibles. La
  misma función alimenta el modal y el rastro del detalle. Con su prueba.
- 409: mensaje con "Volver a abrir".

**Pruebas:**

- `CorregirCompraTest` (cabecera):
  - cambia la forma de pago y deja el evento con antes y después;
  - **no mueve kardex ni stock**;
  - sin motivo; sin cambios; versión vieja;
  - transferencia sin cuenta; cuenta inactiva nueva; proveedor inexistente.
- `Falsos.AuditoriaEnMemoria`.
- Integración:
  - el `jsonb` conserva los 4 decimales;
  - **conflicto real de versión**: dos correcciones con la misma versión, la segunda da 409.

**Checkpoint:** una compra pasa de Efectivo a Transferencia desde su detalle. Se ve el rastro con
quién, cuándo, motivo, antes y después, y el kardex queda igual.

---

## Fase 4 · Revertir una compra en el dominio (sin pantalla) — ✅ COMPLETA

> **Cerrada el 2026-09-13.** Migración **V6**. El guardarraíl se sostuvo: `RegistrarCompra` pasó a
> usar `InventarioDeCompra` y **sus 31 pruebas pasaron sin tocar una aserción**.
>
> Se rompieron a propósito cinco reglas y cada una hizo fallar las pruebas que la guardan: la resta
> del costo en el promedio, el bloqueo por ventas posteriores, la vuelta del precio, "otra compra
> fijó el precio después", y la numeración de la secuencia en V6. **La del bloqueo por ventas no
> la detectaba ninguna prueba al principio**: ver bitácora.
>
> **La tolerancia de redondeo se volvió real:** corregir 100 → 10 no deja un promedio idéntico al
> diezmilésimo al del gemelo ($1.666,6670 contra $1.666,6667). Lo que se garantiza es el valor del
> repuesto con menos de $1 de diferencia, como declaraba el riesgo del plan.

**Cubre:** RF-016, RF-017, RF-019, y la base de RF-015

| Pieza | Dónde cae |
|---|---|
| `TipoMovimiento` + `CORRECCION_COMPRA`, `ANULACION_COMPRA` | `inventario/dominio/` |
| `Variante.revertirEntradaDeCompra(cantidad, costoUnitario, promedioAntes)` | `inventario/dominio/` |
| `MovimientoKardex.porReversionDeCompra(...)`, `porCompra(...)` con motivo, y `secuencia` mapeada de solo lectura | `inventario/dominio/` |
| `RepositorioKardex` + `buscar(id)`, `huboSalidasDespuesDe(variante, secuencia)`, `ultimoAntesDe(variante, secuencia)` | `inventario/dominio/puerto/` |
| `LineaCompra` + `precioAnterior`, `movimientoEntradaId`, `vigente`, `reemplazadaEn`; `anotarEntrada(...)`, `darDeBaja(...)` | `compras/dominio/` |
| `RepositorioCompras` + `otraCompraFijoPrecioDespues(variante, secuencia)` | `compras/dominio/puerto/` |
| `InventarioDeCompra`: `darEntrada(...)`, `bloqueados(lineas)`, `revertirEntrada(...)` | `compras/aplicacion/` |
| `RegistrarCompra` pasa a usar `InventarioDeCompra.darEntrada` | `compras/aplicacion/` |
| `V6__reversion_de_compra.sql` | `pos/…/db/migration/` |
| Consultas por secuencia; `historialDe` ordena por secuencia | `inventario/infraestructura/RepositorioKardexJpa` |
| `DocumentosDeCompra`: precio por `movimiento_entrada_id`, no por compra + variante | `compras/infraestructura/` |

**La regla de `revertirEntradaDeCompra`:**

- Si la cantidad es mayor que el stock, se rechaza.
- `resto = stock − cantidad`.
- Si `resto == 0`, el promedio queda en `promedioAntes`, que es `null` si nunca se había comprado.
- Si no, el promedio queda en `(stock·promedio − cantidad·costoUnitario) / resto`, a 4 decimales
  con `HALF_UP`.
- Si el resultado es ≤ 0, se rechaza.

**La regla del precio (RF-017):** se revierte al `precioAnterior` solo si se cumplen las tres:

- el precio vigente es el que fijó ese renglón;
- `precioAnterior` es conocido;
- ninguna compra posterior fijó precio a ese repuesto.

Si no, se deja el precio vigente y el resultado lleva un **aviso**.

**V6:**

- `movimiento_kardex`:
  - `DROP CONSTRAINT movimiento_kardex_tipo_check` y se recrea con los dos tipos nuevos;
  - `secuencia`: columna nueva, rellenada con `row_number() over (order by creado_en, id)`, luego
    `NOT NULL`, identidad que arranca en máximo + 1 (bloque `DO`), e índice
    `(variante_id, secuencia)`.
- `linea_compra`:
  - `precio_anterior`, `vigente` (default true) y `reemplazada_en`;
  - `movimiento_entrada_id`, rellenado emparejando cada renglón con su movimiento `COMPRA` por
    compra y variante, con `row_number()` por si hay duplicados viejos de QA.

**Pruebas (el corazón del plan):**

- `VarianteTest`:
  - reversión exacta: 10 a $1.000 + 10 a $2.000, se quita la primera y quedan 10 a $2.000;
  - stock a cero vuelve al promedio anterior;
  - a cero y nunca comprado queda en `null`;
  - más unidades que el stock se rechaza;
  - resultado ≤ 0 se rechaza.
- `InventarioDeCompraTest`:
  - **gemelo**: 100 → 10 deja stock y promedio iguales a los de registrar 10;
  - con cifras incómodas ($200.000 / 15), **el valor del repuesto difiere en menos de $1**: es la
    tolerancia declarada;
  - una salida **después** de la compra bloquea, una **antes** no;
  - el precio vuelve al anterior; no vuelve si otra compra lo fijó después; no vuelve si el precio
    anterior es desconocido (con aviso);
  - **empate de instante**: se corrige dos veces el mismo renglón y la segunda toma el promedio
    correcto.
- `RegistrarCompraTest`:
  - nuevos: guarda el precio anterior y el movimiento de entrada;
  - **guardarraíl: todas las anteriores pasan sin tocar aserciones** tras el refactor.
- `Falsos`: el kardex usa el orden de la lista como secuencia.
- `MigracionesIntegracionTest` (V6 sobre datos de V5):
  - el emparejamiento del renglón con su movimiento;
  - la secuencia sigue el orden de `creado_en`;
  - el `CHECK` acepta los tipos nuevos.

**Checkpoint:**

- `./mvnw -q clean test` en verde.
- **Romper a propósito** la resta del promedio en `revertirEntradaDeCompra` y la regla del empate:
  tienen que fallar exactamente las pruebas que los guardan.

---

## Fase 5 · Corregir renglones (H4) y anular (H5) — ✅ COMPLETA

> **Cerrada el 2026-09-13.** Migración **V7**. 150 pruebas de dominio, 29 contra Postgres real y 52
> del frontend. Lint y build limpios.
>
> **V5, V6 y V7 aplicadas sobre la base de QA:** los 15 renglones encontraron su movimiento de
> entrada, el kardex quedó numerado del 1 al 15 y las 12 compras quedaron vigentes.
>
> **Demo hecho por API en la base de QA**, con compras marcadas `PRUEBA-`: una factura de 100
> unidades corregida a 10 (el kardex muestra Compra +100 · Corrección −100 · Compra +10, stock 10,
> promedio $2.000), una duplicada anulada (el stock volvió a 10), 409 al reusar una versión, y 422
> al anular dos veces. Pantallas revisadas en capturas; **falta hacerlo con clics en el navegador**.
>
> Se rompió a propósito la atomicidad (`noRollbackFor`) y la prueba falló con "expected 10 but was
> 8": media corrección aplicada.

**Cubre:** RF-005 y RF-006 (estado), RF-008 (renglones reemplazados), RF-014, RF-015, RF-018,
RF-020 a RF-023

| Pieza | Dónde cae |
|---|---|
| `EstadoCompra` y los datos de anulación | `compras/dominio/` |
| `Compra`: `lineasVigentes()`, `reemplazarLinea`, `quitarLinea`, `agregarLinea`, total desde las vigentes, `anular(...)`, `exigirVigente()`, mínimo un renglón | `compras/dominio/` |
| `RenglonesBloqueadosException` (con los códigos) | `compras/dominio/` |
| `ComandoCorregirCompra.Linea(lineaId, …)` y `CorregirCompra` completo | `compras/aplicacion/` |
| `exigirRepuestosDistintos` y la resolución del repuesto, compartidas | `compras/aplicacion/InventarioDeCompra` |
| `AnularCompra` | `compras/aplicacion/` |
| `ConsultarCompras`: filtro de estado (vigentes por defecto), renglones reemplazados | `compras/aplicacion/` |
| `V7__anulacion_de_compra.sql` — `estado`, `anulada_en`, `anulada_por`, `motivo_anulacion` | `pos/…/db/migration/` |
| `POST /api/compras/{id}/anulacion`; 422 con la lista de bloqueados | infraestructura |

**`CorregirCompra` con renglones:**

1. Clasifica cada renglón: sin cambio · solo precio · cambia la entrada · quitado · nuevo.
2. Revisa que no se repitan repuestos en el resultado final.
3. Junta **todos** los renglones bloqueados. Si hay alguno, falla sin aplicar nada y dice cuáles.
4. Hace primero todas las reversiones (`CORRECCION_COMPRA`) y después las entradas.
5. Registra un evento con las dos fotos.
6. Devuelve el detalle y los avisos.

Los renglones viejos **no se sacan de la colección**, porque `orphanRemoval=true` los borraría: se
marcan como no vigentes.

**Frontend:**

- `Compra.jsx` en modo corrección, completo. La lógica pura va en `utils/correccion.js`, con su
  prueba:
  - `renglonesDesdeDetalle`;
  - `lineasParaCorregir`, que conserva `lineaId` y compara el precio contra el del renglón, no
    contra el precio actual. Sin eso, un renglón intacto que fijó $25.000 se enviaría como "no
    tocar" y **revertiría el precio**;
  - `cambiosDeLaCorreccion`.
- `DetalleCompra`: botón Anular (variante peligro) con `ModalMotivo`, aviso de anulada, renglones
  reemplazados, rastro.
- `HistorialCompras`: filtro y marca de estado.
- `FichaRepuesto`: etiquetas Corrección y Anulación, y el motivo.

**Pruebas:**

- `CorregirCompraTest` (renglones):
  - gemelo 100 → 10; solo costo;
  - solo precio: sin kardex y sin bloqueo;
  - quitar un renglón con precio; agregar uno que crea repuesto;
  - cambiar A→B; intercambiar A↔B;
  - **los demás renglones no generan movimientos**;
  - un bloqueo lista todos y no aplica nada;
  - sin renglones, repetido, anulada, renglón de otra compra;
  - el renglón viejo queda no vigente; el total es la suma de los vigentes.
- `AnularCompraTest`:
  - la duplicada anulada queda igual que una sola;
  - bloqueada no anula nada;
  - ya anulada; sin motivo; deja evento.
- Integración:
  - **atomicidad**: falla el segundo renglón y no queda nada (stock, kardex, renglones,
    auditoría);
  - anular contra Postgres;
  - filtro de estado;
  - precio por movimiento después de corregir.
- `.http`: corregir y anular, incluidos los caminos infelices.

**Checkpoint — el demo:**

1. Registrar FV-DEMO con 100 unidades.
2. Corregirla a 10 con motivo. La ficha muestra Compra +100 · Corrección −100 · Compra +10, con
   stock y promedio correctos.
3. Registrar otra factura dos veces y anular una.

---

## Fase 6 · P2 y P3 — ✅ COMPLETA

| Qué | Piezas |
|---|---|
| **H6 · Totales** (RF-010) | `RepositorioCompras.totales(filtro)` agrupado en la base, anuladas fuera, más el total general; `GET /api/compras/totales`; barra en el historial. Integración: **las partes suman el total** |
| **RF-004 · Desactivar cuenta** | `DesactivarCuenta`; `POST /api/cuentas/{id}/desactivacion`; modal "Administrar cuentas" desde la cabecera de la compra. No existe endpoint de borrar |
| **H7 · Auditoría de ficha** (RF-024) | `ActualizarRepuesto` gana `RepositorioAuditoria` y `Reloj`; evento `CORREGIR_REPUESTO` solo si algo cambió. El `PUT` recibe `X-Usuario-Id` (el cliente ya lo manda). `ActualizarRepuestoTest`: cambia el cableado y hay 2 pruebas nuevas. `FichaRepuesto` muestra sus correcciones con `cambiosDeFicha` (la foto de la ficha no es la de una compra) |
| **H8 · Kardex → compra** (RF-011) | `RespuestaMovimiento.compraId`; en la ficha, la factura es un enlace a `/compras/historial/:id` |

**Checkpoint:**

- Los totales de septiembre suman el total.
- Una cuenta desactivada deja de ofrecerse.
- Corregir una ficha deja su evento.
- Un clic en la factura del kardex abre la compra.

**Cómo quedó (2026-09-13):**

- Dominio 157 pruebas, Postgres 32 (3 nuevas: totales, desactivar cuenta, auditoría de ficha),
  frontend 60.
- Contra la base de QA:
  - los totales cuadran: $7.237.772 = efectivo $7.234.772 + PRUEBA Nequi $3.000;
  - `estado=ANULADA` da cero;
  - una cuenta desactivada sale de `GET /api/cuentas`, y desactivarla otra vez responde lo mismo;
  - el `PUT` de la ficha sin `X-Usuario-Id` da 400;
  - guardar la ficha igual dos veces deja un solo evento.
- Rotas a propósito, y fallaron exactamente sus pruebas:
  - quitar "solo si algo cambió" → `sinCambiosNoDejaEvento`;
  - dejar pasar el estado pedido a los totales → `anuladasNoCuentan`.
- Datos de QA nuevos: cuentas "PRUEBA Nequi" (activa) y "PRUEBA Banco viejo" (desactivada), y la
  compra PRUEBA-F6-TRANSF por transferencia.

---

## Fase 7 · Buscar las compras de un repuesto (H9) — ✅ COMPLETA

**Cubre:** RF-025, RF-026

**El flujo, en la pantalla:**

1. En *Compras › Historial* se escribe *inoki* en el filtro **Repuesto**.
2. La lista deja solo las facturas donde vino un repuesto con ese texto. Cifras y totales, los de
   siempre.
3. Se abre FV-9912: arriba dice *«Resaltado lo que buscaste: inoki»*, y el renglón del filtro INOKI
   se ve marcado, con *INOKI* subrayado en el texto. El FACTORY de la misma factura no.
4. *← Historial* vuelve a la lista con la búsqueda puesta.

**La decisión técnica: la regla de "coincide" vive en el dominio y la base la imita.** Se necesita
en dos sitios: filtrar la lista (consulta en Postgres) y marcar los renglones del detalle. Si la
pantalla hiciera su propia comparación, podría salir una factura en la lista sin nada resaltado al
abrirla. Así que el backend dice qué renglones coinciden, y una prueba contra Postgres verifica que
la lista y el detalle están de acuerdo.

| Pieza | Dónde cae |
|---|---|
| `BusquedaDeRepuesto` — el texto ya limpio (sin espacios de sobra, vacío = sin búsqueda, máximo 80) y `coincideCon(variante)`: código, nombre, marca o aplicación, sin distinguir mayúsculas | `compras/dominio/` |
| `FiltroCompras` gana `repuesto` (una `BusquedaDeRepuesto` o `null`) | `compras/dominio/` |
| `Condiciones.de`: la compra tiene un renglón **vigente** que coincide (`exists`, así una factura con dos coincidencias no sale dos veces), con `%`, `_` y `\` escapados | `compras/infraestructura/RepositorioComprasJpa` |
| `ConsultarCompras.detalle(id, busqueda)` y `DetalleCompra.Renglon` gana `coincide` (siempre `false` en los renglones antes de corregir) | `compras/aplicacion/` |
| `GET /api/compras`, `/totales` y `/{id}` aceptan `repuesto`; el renglón devuelve `coincide` y `aplicacion` | `compras/infraestructura/CompraController` |
| El doble en memoria filtra con `coincideCon` | `domain/src/test/.../Falsos` |

**Frontend:**

- `HistorialCompras.jsx`:
  - campo **Repuesto** con la misma pausa que el de factura;
  - vive en la URL (`?repuesto=inoki`) y cuenta para *Limpiar filtros*;
  - al abrir una compra la lleva en la URL del detalle (`/compras/historial/:id?repuesto=inoki`), así
    recargar la página no pierde el resaltado.
- `DetalleCompra.jsx`:
  - aviso *«Resaltado lo que buscaste: inoki»* con *Quitar resaltado*;
  - los renglones con `coincide` van con fondo y borde de marca;
  - lo buscado subrayado dentro del código, el nombre o la marca;
  - si coincidió por la aplicación, la aplicación se muestra en ese renglón.
- `utils/historial.js`: el filtro nuevo en la URL y en la consulta, y `partesResaltadas(texto,
  busqueda)`, que solo parte el texto para dibujarlo: **no decide qué coincide**, eso lo dice el
  backend. Con sus pruebas.
- Lista vacía por búsqueda: *"Ninguna compra tiene un repuesto con «inoki»"*.

**Qué NO cambia:** las cifras de la lista y de los totales siguen siendo de facturas completas
(decidido en la revisión). Sin migración, sin inventario, sin auditoría: es solo lectura.

**Pruebas:**

- `BusquedaDeRepuestoTest`:
  - coincide por código, nombre, marca y aplicación;
  - sin distinguir mayúsculas;
  - texto vacío o de espacios = sin búsqueda;
  - tope de largo.
- `ConsultarComprasTest`:
  - filtra por repuesto;
  - un renglón no vigente no cuenta;
  - el detalle marca `coincide` solo en los renglones vigentes que coinciden.
- Integración contra Postgres:
  - por código, nombre, marca y aplicación;
  - una factura con dos renglones que coinciden sale una vez y el conteo de páginas lo respeta;
  - quitar el repuesto en una corrección hace que ya no salga;
  - los totales son los de la lista;
  - `%` y `_` se buscan tal cual;
  - **la lista y el detalle están de acuerdo**: toda compra que sale tiene al menos un renglón con
    `coincide`, y ninguna que no sale lo tiene.
- Romper a propósito:
  - quitar la condición de vigente;
  - quitar el escape de `%`.
- `.http`: buscar por repuesto y el detalle con resaltado.

**Checkpoint:** se escribe *inoki*, sale FV-9912, se abre, y el renglón INOKI está resaltado y el
FACTORY no. Se recarga la página y sigue resaltado.

**Cómo quedó (2026-09-14):**

- Dominio 166 pruebas (9 nuevas), Postgres 34 (2 nuevas), frontend 67 (4 nuevas). Lint y build en
  verde.
- En el navegador, contra la base de QA:
  - escribir *inoki* deja 8 compras y la búsqueda queda en la dirección;
  - al abrir FV-9912 se resalta solo el renglón INOKI, y recargar lo conserva;
  - *duke* resalta los dos filtros y muestra "Aplica a: …";
  - una búsqueda sin resultados lo dice.
- Por la API: `%` da 0 compras y 81 caracteres dan 422.
- Rotas a propósito, y fallaron exactamente sus pruebas:
  - quitar "solo renglones vigentes" → `buscarPorRepuestoContraPostgres` y `listaYDetalleDeAcuerdo`;
  - quitar el escape de comodines → `buscarPorRepuestoContraPostgres` ("_ no vale por cualquier
    letra").

---

## Fase 8 · La lista dice qué repuesto trae cada factura (RF-027) — ✅ COMPLETA

**Pedido el 2026-09-15.** El comportamiento se revisó con el usuario sobre un dibujo de la lista.

**El flujo, en la pantalla:**

1. En *Compras › Historial* se escribe *aceite*.
2. Debajo del proveedor de cada factura sale una línea:
   *↳ FILTRO DE ACEITE INOKI × 10 · FILTRO ACEITE FACTORY × 5*, con *ACEITE* resaltado.
3. Una factura con cinco que coinciden: los tres primeros y *y 2 más*.
4. Al abrir una, están resaltados exactamente esos mismos renglones.

**La decisión técnica: la lista usa la MISMA regla que el detalle, en Java.** El filtro de la lista
sigue siendo la consulta de la base (fase 7). Para saber **cuáles** renglones mostrar, se traen los
renglones vigentes de las facturas de esa página y se pasan por `BusquedaDeRepuesto.coincideCon`, la
misma función que marca el detalle. Así la línea de la lista y el resaltado del detalle no pueden
decir cosas distintas. Es barato: una página son 25 facturas.

| Pieza | Dónde cae |
|---|---|
| `RepositorioCompras.lineasVigentesDe(ids)` — los renglones vigentes de varias compras, con repuesto y concepto | `compras/dominio/puerto/` y `RepositorioComprasJpa` |
| `FilaHistorial(resumen, coinciden)` y `RenglonQueCoincide(codigo, nombre, marca, cantidad)` | `compras/aplicacion/` |
| `ConsultarCompras.historial` devuelve filas: con búsqueda de repuesto llena `coinciden`; sin búsqueda, vacío y sin la consulta extra | `compras/aplicacion/` |
| `GET /api/compras`: cada elemento gana `coinciden` | `CompraController` |
| El doble en memoria responde `lineasVigentesDe` | `Falsos.ComprasEnMemoria` |

**Frontend:**

- `utils/historial.js`: `coincidenciasParaMostrar(coinciden, maximo = 3)` → los que se muestran y
  cuántos quedan, con sus pruebas.
- `HistorialCompras.jsx`: la línea debajo del proveedor, con `Resaltado` sobre nombre y marca.

**De paso, del spec 0003 (RF-017): el texto del aviso de pérdida en Vender.** Pedido en la misma
revisión porque "Queda $3.286 por debajo de lo que costó" no se entendía. Pasa a decir:
*"⚠ Venta a pérdida: estos repuestos te costaron $28.000 y cobras $24.714 (pierdes $3.286)"*, y si
algún repuesto no tiene costo conocido, *"sin contar 1 repuesto sin costo"*. `avisoDePerdida` ya
devuelve costo, cobrado y diferencia (`utils/venta.js:128-137`); gana cuántos quedaron fuera.

**Pruebas:**

- `ConsultarComprasTest`: con búsqueda, cada fila trae solo sus renglones vigentes que coinciden; un
  renglón corregido a otro repuesto no aparece; sin búsqueda, `coinciden` vacío y sin pedir renglones.
- Integración: `listaYDetalleDeAcuerdo` se amplía — para cada búsqueda, los `coinciden` de la lista
  son exactamente los renglones con `coincide` del detalle.
- `historial.test.js` (tres y "y N más") y `venta.test.js` (el aviso con repuestos sin costo).
- Romper a propósito: que la lista muestre renglones no vigentes → falla la de acuerdo.

**Checkpoint:** se escribe *aceite*; FV-UI muestra sus dos filtros de aceite con la cantidad; se abre
y están resaltados los mismos dos.

**Cómo quedó (2026-09-15):**

- Dominio 235 pruebas (3 nuevas en `ConsultarComprasTest`), Postgres 53 (`listaYDetalleDeAcuerdo`
  ampliada), frontend 127 (3 nuevas). Lint y build en verde.
- En el navegador, contra la base de QA, en oscuro a 1360 px y en claro a 390 px (sin desbordar):
  - *aceite*: las 8 facturas muestran debajo su filtro de aceite con la cantidad, con *ACEITE*
    resaltado. FV-9912 muestra los dos: *FILTRO ACEITE INOKI × 15 · FILTRO ACEITE FACTORY × 6*. El
    checkpoint decía FV-UI, pero en la base de QA FV-UI trae un solo filtro de aceite;
  - al abrir la primera, lo resaltado es exactamente lo que decía la lista;
  - *duke* muestra la moto entre paréntesis y *352b* el código, porque no están en el nombre;
  - sin búsqueda, ninguna línea de coincidencias.
- En Vender, un ABC23 con $30.000 de descuento dice: *"Venta a pérdida: estos repuestos te costaron
  $14.000 y cobras $5.000 (pierdes $9.000)"*.
- Rotas a propósito, y fallaron exactamente sus pruebas: la lista sin la regla de coincide →
  `listaTraeLoQueCoincide`; la consulta sin "solo vigentes" → `listaYDetalleDeAcuerdo`.
- El plan pedía que `historial` devolviera las filas; se hizo un método aparte (ver bitácora).

---

## Fase 9 · Sin buscar, cada factura dice qué trae (RF-028) — ✅ hecha el 2026-09-19

**Pedido el 2026-09-16**, sobre la lista del historial en la base de QA: quince facturas, casi todas de
Importadora Jotapartes y *Sin número*, que solo se distinguen abriéndolas.

**El flujo:** en *Compras › Historial*, sin escribir nada, debajo del proveedor de cada factura sale:
*↳ FILTRO DE ACEITE INOKI × 25 · PASTILLAS FRENO CBI × 4 · y 1 más*. Al escribir un repuesto, esa
línea pasa a ser la de la fase 8 (lo que coincide, resaltado).

**Se reusa lo de la fase 8.** `historialConCoincidencias` ya trae en una consulta los renglones vigentes
de las facturas de la página (`lineasVigentesDe`), pero solo cuando hay búsqueda. Ahora los trae
siempre, y de cada factura devuelve también sus primeros tres renglones y cuántos tiene.

| Pieza | Dónde cae |
|---|---|
| `FilaHistorial` gana `primeros` (hasta 3 `RenglonQueCoincide`, en el orden de la factura) y `renglones` ya viene en el resumen | `compras/aplicacion/` |
| `historialConCoincidencias` pide los renglones siempre que la página tenga facturas; `coinciden` sigue vacío sin búsqueda | `compras/aplicacion/ConsultarCompras` |
| `GET /api/compras`: cada elemento gana `primeros` | `CompraController` |

Una anulada no tiene renglones vigentes distintos: `anular` no toca los renglones, así que muestra lo
que traía.

**Frontend:**
- `utils/historial.js`: `lineaDeLaFactura({ primeros, renglones, coinciden }, busqueda)` decide qué se
  muestra: con búsqueda, lo de la fase 8; sin búsqueda, los primeros con "y N más". Con sus pruebas.
- `HistorialCompras.jsx`: la línea sale siempre (hoy solo con búsqueda), sin resaltado cuando no se
  busca.

**Pruebas:**
- `ConsultarComprasTest`:
  - sin búsqueda, cada fila trae sus primeros tres renglones vigentes en orden;
  - un renglón quitado en una corrección no sale;
  - `coinciden` vacío;
  - una sola petición de renglones por página (el doble cuenta `vecesPedidasLineasVigentes`).
- `historial.test.js`: tres y "y N más", una factura de un renglón, y que con búsqueda manda la fase 8.
- Romper a propósito: sin el tope de tres → falla la prueba del dominio.

**Checkpoint:**
- En QA, sin buscar, las facturas de Jotapartes *Sin número* se distinguen por lo que traen.
- Escribir *aceite* sigue mostrando lo que coincide, resaltado.

---

## Riesgos

| Riesgo | Fase | Qué hacer |
|---|---|---|
| `@Version` con id asignado cambia cómo Spring Data decide entre insertar y actualizar | 3 | `Long` (no `long`): nulo = nuevo. La integración registra y corrige contra Postgres |
| Rellenar `secuencia` en una tabla con filas asigna el orden físico, no el cronológico | 4 | `row_number()` explícito y bloque `DO` para la identidad; probado en `MigracionesIntegracionTest` |
| El nombre automático del `CHECK` de `tipo` no es el esperado | 4 | La migración falla ruidosamente; se verifica en la prueba de migración |
| Refactorizar `RegistrarCompra` rompe lo que ya funcionaba | 4 | Guardarraíl: sus pruebas pasan sin tocar aserciones |
| Revertir promedios acumula redondeo | 4 | Tolerancia declarada: el valor del repuesto difiere en menos de $1. Prueba con $200.000 / 15 |
| `Compra.jsx` crece demasiado con el modo corrección | 3, 5 | La lógica va a `utils/` con pruebas; si pasa de ~700 líneas, se extrae `CabeceraCompra` |
| Parámetros nulos en la consulta del historial | 2 | Consulta armada según los filtros presentes, con prueba de cada filtro nulo |

---

## Verificación final

1. Los criterios de aceptación del §8 del spec, uno por uno, desde el navegador.
2. Backend abajo y luego `./mvnw -q clean test`: dominio y Postgres real, incluidas las
   migraciones.
3. `cd frontend && npx eslint src/ && node --test src/utils/*.test.js && npx vite build`.
4. Capturas con Edge sin ventana de historial, detalle, corrección y ficha: claro, oscuro y angosto.
5. `.http` al día con cuentas, historial, corregir y anular.
6. Para cada prueba que protege una regla nueva (reversión, bloqueo por salidas, precio, empate de
   secuencia, atomicidad): **verla fallar** rompiendo la regla, y restaurar.

---

## Bitácora de decisiones

Se llena **durante** la implementación, con fecha, fase, decisión y porqué.

| Fecha | Fase | Decisión | Por qué |
|---|---|---|---|
| 2026-09-13 | 1 | **La regla "transferencia ⇔ cuenta" vive solo en `Compra`**, no en el comando | Al principio quedó escrita en los dos sitios. Se dejó en la entidad, que es la dueña: la corrección de la fase 3 pasa por `Compra` y la hereda sin copiarla |
| 2026-09-13 | 1 | **Una cuenta desactivada se rechaza en el dominio** (`Compra.registrar`), no en el caso de uso | Es una regla del negocio, no de un flujo. Así no hay forma de registrar una compra con una cuenta dada de baja por otro camino |
| 2026-09-13 | 1 | **El nombre de la cuenta se normaliza en el dominio** (sin espacios en las puntas, uno solo entre palabras) y la base tiene índice único sobre `lower(nombre)` | "NEQUI  del dueño" y "Nequi del dueño" son la misma cuenta. El dominio junta los espacios; la base cuida las mayúsculas aunque alguien inserte por otro camino. Probado saltándose el caso de uso |
| 2026-09-13 | 1 | **Cuenta repetida responde 422 con el nombre de la que ya existe**, no 409 | A diferencia del código de repuesto, no hay nada que ofrecer más allá de "elígela en la lista". Un mensaje basta |
| 2026-09-13 | 1 | **La pantalla no trae forma de pago marcada, y la limpia después de registrar**; el proveedor y la fecha sí se conservan | El proveedor suele repetirse entre facturas seguidas; la forma de pago es de cada factura. Un valor que queda puesto de la anterior se registra sin mirar |
| 2026-09-13 | 1 | **`forma_pago` se agrega con `DEFAULT 'EFECTIVO'` y el default se quita en la misma migración** | El default solo rellena las compras que ya existían. Si se quedara, un script que olvide la columna registraría efectivo sin decidirlo. Hay prueba de que insertar sin forma de pago falla |
| 2026-09-13 | 1 | **`MigracionesIntegracionTest` migra por partes** (hasta V2, inserta, migra el resto) | Se rompió V3 a propósito quitando el relleno: la prueba falló con `column "forma_pago" contains null values`. **La suite normal, que arranca con la base vacía, habría pasado en verde** |
| 2026-09-13 | 2 | **Los renglones guardan su posición** (`linea_compra.posicion`, migración V4) | Al armar el detalle apareció que la compra no sabía en qué orden se capturaron sus renglones: la base los devolvería en el orden que quisiera, y el detalle se compara contra el papel renglón por renglón. Las compras viejas se numeran por orden físico de la tabla. **Corre la numeración del plan: la auditoría pasa a V5, la reversión a V6 y la anulación a V7** |
| 2026-09-13 | 2 | **Sin `UNIQUE (compra_id, posicion)`** | En la fase 5 el renglón corregido ocupa el lugar del que reemplaza, y el viejo se queda como historia con la misma posición |
| 2026-09-13 | 2 | **La decisión 10 del plan era cierta a medias, y se corrigió al verificarla** | Se probó `(:param is null or …)` contra Postgres 17 con Hibernate 7: con un id o una fecha nulos **funciona**; con la búsqueda de factura **revienta** (`function lower(bytea) does not exist`: el texto nulo viaja como `bytea`). La consulta armada por partes se mantiene, pero los comentarios dicen ahora exactamente eso. La prueba temporal se borró |
| 2026-09-13 | 2 | **El detalle trae el repuesto como está hoy, además de lo registrado** | La corrección (fases 3 y 5) arranca con los dos, y el detalle ya muestra "hoy $X" cuando el precio cambió después de esa compra |
| 2026-09-13 | 2 | **El detalle compara el total contra la suma de renglones y lo dice en rojo si no cuadran** | No debería verse nunca. Si se ve, un dato quedó mal guardado, y la regla del skill `backend` es que un desglose que no suma su total no se esconde |
| 2026-09-13 | 2 | **`Listado.module.css` y `AvisoCarga` compartidos** para las pantallas de consulta | El historial y el detalle eran la tercera y cuarta copia de las mismas clases. Inventario y la ficha del repuesto se pasan a lo compartido cuando se vuelvan a tocar, no en un cambio aparte |
| 2026-09-13 | 3 | **`compra.modificada_en`**, que el plan no tenía | Toda corrección la toca. Sin ella, cambiar solo el precio de un renglón no modifica la fila de la compra, Hibernate no sube la versión, y dos correcciones de precio simultáneas no chocarían |
| 2026-09-13 | 3 | **La auditoría se escribe con JDBC y `cast(? as jsonb)`, no con una entidad** | El evento del dominio no es entidad JPA. Al leer se activa `USE_BIG_DECIMAL_FOR_FLOATS`: con el valor por defecto de Jackson un costo de $13.333,3333 vuelve como `double`. Hay prueba de ida y vuelta contra Postgres |
| 2026-09-13 | 3 | **El motivo se valida en un solo sitio, `Compra.exigirMotivo`** | Lo usan el comando de corregir y el caso de uso de anular: obligatorio, sin espacios de sobra, máximo 300 como la columna |
| 2026-09-13 | 4 | **`InventarioDeCompra` se construye dentro de cada caso de uso**, no es un bean | Es un colaborador de paquete, sin transacción propia. Así el cableado de Spring y el de las pruebas no cambió, y el guardarraíl de `RegistrarCompraTest` se sostuvo sin tocar aserciones |
| 2026-09-13 | 4 | **"Idéntico" no es alcanzable con 4 decimales: se mide el valor** | Deshacer un promedio redondeado deja residuo (gemelo: $1.666,6670 contra $1.666,6667). Las pruebas del gemelo y de dos correcciones seguidas comparan **valor del repuesto con diferencia menor a $1**, que es la tolerancia que el plan declaró. El criterio de aceptación del spec se aclaró con esa tolerancia |
| 2026-09-13 | 4 | **La rotura del bloqueo por ventas pasaba en verde: faltaba una prueba** | En las pruebas, la venta dejaba menos stock que lo comprado, y la condición "stock < cantidad" bloqueaba igual. El caso que solo esa regla cubre —vendes 3 y otra compra repone el stock— no estaba. Se agregó y ahora falla si se quita la regla |
| 2026-09-13 | 4 | **Las reversiones de compra no bloquean otra reversión del mismo repuesto** | Salen del inventario pero no lo consumen: devuelven justo lo que entró. Sin esta excepción, corregir una compra impediría corregir cualquier otra anterior del mismo repuesto |
| 2026-09-13 | 4 | **El doble del kardex asigna la secuencia con reflexión** | Imita la columna de identidad. Sin eso, las reglas de "antes o después de esta compra" no se podrían probar en memoria |
| 2026-09-13 | 4 | **Empate de instante: qué protege la secuencia, dicho con precisión** | El orden de reversión y entrada corregida (mismo `creado_en`) sale bien en el kardex solo gracias a la secuencia; la integración lo verifica. El caso en que el empate cambiaría una **cifra** es estrecho: que el stock quede en cero justo al revertir un renglón ya corregido, con otras entradas en el medio. La prueba "corregir dos veces" cubre el flujo, no ese caso límite |
| 2026-09-13 | 5 | **La pantalla manda un renglón intacto exactamente como estaba** (`lineaParaCorregir`) | Si se mandara el unitario redondeado o el precio de hoy en vez del que fijó el renglón, el backend lo vería cambiado: revertiría su entrada o devolvería un precio que nadie quiso tocar. Probado en `correccion.test.js` |
| 2026-09-13 | 5 | **"Corregir ficha" desde la corrección pide la ficha completa antes de abrir** | El renglón cargado del detalle no trae categoría ni stock mínimo. Abrir el modal sin ellos repetía el bug de la fase 6 del plan 0001: guardar borraba la categoría |
| 2026-09-13 | 5 | **El kardex toma el precio fijado por movimiento de entrada, y manda el renglón vigente** | Después de una corrección, la misma factura tiene varias versiones del renglón. Si un cambio de precio dejó dos versiones con la misma entrada, la vigente es la que vale |
| 2026-09-13 | 5 | **`ck_compra_datos_de_anulacion`**: una anulada tiene que tener cuándo, quién y motivo; una vigente, nada de eso | Lo exige la base, no solo la aplicación. Probado saltándose el caso de uso |
| 2026-09-13 | 5 | **Un 400 al anular desde curl no era la aplicación** | Git Bash en Windows mandó "registró" mal codificado. Con UTF-8 explícito pasa. Queda anotado en `02-errores.http` para que nadie lo persiga como bug |
| 2026-09-13 | 6 | **Pedir los totales de "solo anuladas" devuelve cero**, no los de las vigentes | La primera versión forzaba `VIGENTE` en cualquier filtro, y con `estado=ANULADA` devolvía la plata de las vigentes bajo un filtro que decía lo contrario. Lo atrapó `anuladasNoCuentan`. La pantalla, con ese filtro, ni los pide: dice que las anuladas no suman |
| 2026-09-13 | 6 | **Los totales se piden con su propia clave, sin página**, y si fallan no se deja la cifra anterior | Cambiar de página no cambia los totales: no se vuelven a pedir. Una lista vieja atenuada ayuda; una cifra de plata vieja al lado de filtros nuevos se lee como la respuesta |
| 2026-09-13 | 6 | **Las partes y el total salen de dos consultas, y la pantalla compara** | Es la regla del desglose que no suma: si alguna vez no cuadran, sale en rojo. La integración lo verifica con un proveedor propio de la prueba |
| 2026-09-13 | 6 | **`usuarioId` va en `ComandoActualizarRepuesto`**, obligatorio | Igual que en corregir y anular una compra. El `PUT` exige el encabezado: una corrección de ficha sin quién no se acepta (400) |
| 2026-09-13 | 6 | **`cambiosDeFicha` aparte de `cambiosEntre`** | Las fotos tienen otra forma (sin renglones ni pago), y las cifras de la ficha se comparan como número: el jsonb puede devolver 5.0 donde se guardó 5 |
| 2026-09-13 | 6 | **Desactivar una cuenta al corregir no suelta la cuenta que ya tenía la compra** | El backend la sigue aceptando inactiva para esa compra (fase 3). Soltarla en pantalla obligaría a cambiar la forma de pago de una compra que se pagó así. Al registrar, sí se suelta |
| 2026-09-13 | 6 | **Desactivar pide confirmar en la misma fila**, y el modal lee la lista cada vez que abre | Es de un clic y no tiene deshacer desde la pantalla. La lista propia evita ofrecer la cuenta de la compra que se corrige como si estuviera activa |
| 2026-09-13 | 6 | **Abrir la compra desde el kardex vuelve al kardex** ("← Kardex de CÓDIGO") | Quien llega desde la ficha está revisando ese repuesto; volver al historial lo sacaría de lo que estaba haciendo |
| 2026-09-13 | 6 | **El filtro de cuenta del historial solo ofrece las activas** | Las compras de una cuenta desactivada se siguen viendo en la lista y en el desglose de totales. Filtrar por ella desde el selector queda para cuando haga falta |
| 2026-09-14 | QA | **"Lo que cambia" pasa de frases a un desglose antes/después** (`desgloseDeCambios` + `DesgloseCambios`), en el modal y en el rastro | Pedido en el QA: "ABC123: cantidad 12 → 17 · pagado $30.000 → $42.500" obligaba a leer la línea entera. Ahora cada dato va en columnas, cada renglón dice qué le pasa al inventario (+5 unidades), el costo por unidad aparece aunque no cambie ("$2.500 · igual") porque es lo que mueve el promedio, y el total lleva la diferencia. `cambiosEntre` se retiró: dejar las dos versiones haría que el modal y el rastro se lean distinto |
| 2026-09-14 | QA | **"Renglones reemplazados" pasa a "Renglones antes de corregir", con la columna "Qué pasó"** (Se cambió / Se quitó) | "Reemplazado" es palabra del modelo, no de la tienda. Se cambió si otra versión ocupa su posición (la vigente o una reemplazada después); si no, se quitó. Funciona porque un renglón agregado nunca reusa la posición de uno quitado. Probado con la cadena 12 → 17 → quitado |
| 2026-09-14 | QA | **La explicación de "Precio fijado" pasa a un ícono de ayuda** (`Ayuda`, portado del `HelpTip` del car-wash) | Pedido en el QA. Mismo comportamiento (hover con mouse, foco con teclado, toque en pantalla táctil, portal que no recorta el scroll de la tabla), sin traer `lucide-react`: el ícono es un SVG en línea |
| 2026-09-14 | 7 | **La regla de "coincide" es `BusquedaDeRepuesto.coincideCon` en el dominio; la consulta la imita y una prueba las pone de acuerdo** | Se necesita para filtrar (Postgres) y para resaltar (detalle). La prueba `listaYDetalleDeAcuerdo` recorre varias búsquedas —código, nombre con tilde y eñe, marca, aplicación, texto sin resultados, un renglón corregido a otro repuesto— y exige: sale en la lista si y solo si al abrirla hay algo resaltado |
| 2026-09-14 | 7 | **Los comodines se escapan con `!`**, no con barra invertida | La barra cambia de sentido entre Java, JPQL y SQL. Sin escape, `%` traía todas las compras y `_` valía por cualquier letra. El filtro de número de factura de la fase 2 sigue sin escapar: no se tocó para no ampliar el alcance |
| 2026-09-14 | 7 | **`FiltroCompras.conEstado`** reemplaza la copia campo por campo en `ConsultarCompras.totales` | Al agregar el campo `repuesto`, esa copia lo habría perdido en silencio y los totales no serían los de la lista. La llamada que solo construye un filtro en las pruebas ganó un `null` (construcción, ninguna aserción tocada) |
| 2026-09-14 | 7 | **La búsqueda viaja en la dirección del detalle** (`?repuesto=`), no en el estado de la navegación | Recargar o compartir el enlace conserva el resaltado. "Quitar resaltado" limpia la dirección conservando el estado, para que *← Historial* no pierda los filtros |
| 2026-09-14 | 7 | **Después de anular se conserva el resaltado** | La respuesta de anular no trae la búsqueda. Anular no cambia los renglones, así que se copian las marcas por renglón en vez de apagarlas |
| 2026-09-14 | 7 | **La pantalla solo dibuja el resaltado** (`partesResaltadas`); no decide qué coincide | Si decidiera por su cuenta, podría no resaltar una compra que sí salió. Sí decide si mostrar "Aplica a": cuando el backend dice que coincide y lo buscado no se ve en código, nombre ni marca |
| 2026-09-14 | 7 | **Resaltado en ámbar** (fondo tenue, franja y subrayado) | Es el color de un resaltador. Se verificó en tema claro y oscuro |
| 2026-09-14 | 7 (QA) | **Todos los buscadores de repuestos ignoran tildes**: historial, inventario, búsqueda por texto y sugerencias de concepto | Pedido en el QA. En los cuatro a la vez: si el historial ignorara tildes y el inventario no, "bujia" encontraría la bujía en una pantalla y no en la otra; y en las sugerencias, "BUJIA" no ofrecería el "BUJÍA" que ya existe y el catálogo se duplicaría (decisión §4 del spec 0001) |
| 2026-09-14 | 7 (QA) | **Una sola tabla de letras (`TextoDeBusqueda`) para Java y para Postgres**, registrada en Hibernate como la función `sin_tildes` = `translate(lower(x), CON_TILDE, SIN_TILDE)` | Java y la base quitan tildes igual por construcción. Se descartó `unaccent`: pide superusuario para instalarse y no coincide con Java en todas las letras, y la lista y el detalle tienen que estar de acuerdo. Roto a propósito (la base con una lista sin eñe): fallaron `listaYDetalleDeAcuerdo`, `buscarPorRepuestoContraPostgres` y `buscadoresIgnoranTildesContraPostgres` |
| 2026-09-14 | 7 (QA) | **La eñe cuenta como tilde** | En un buscador se escribe "nandu" desde un teclado sin eñe. Que "pena" encuentre "PEÑA" no hace daño buscando repuestos |
| 2026-09-14 | 7 (QA) | **El resaltado de la pantalla usa la misma tabla, letra por letra** (`sinTildes`) | Al cambiar una letra por una, las posiciones del texto original y del normalizado coinciden: se busca "bujia" y se marca "BUJÍA" tal como está escrito. Ya no hace falta armar una expresión regular, así que tampoco escapar lo que se escribe |
| 2026-09-15 | 8 | **Método nuevo `historialConCoincidencias`**, en vez de cambiar lo que devuelve `historial` como decía el plan | `historial` lo llaman 14 pruebas que miran facturas, no renglones. El método nuevo llama a `historial` en la misma transacción y le agrega lo que coincide; la pantalla usa ese |
| 2026-09-15 | 8 | **La base decide qué facturas salen; Java decide qué renglones se muestran**, con `BusquedaDeRepuesto.coincideCon` sobre los renglones vigentes de la página | Es la misma función que resalta el detalle, así que la lista y el detalle no pueden decir cosas distintas. Una consulta por página (25 facturas), no una por factura |
| 2026-09-15 | 8 | **Si lo buscado no está en el nombre ni en la marca, se muestra entre paréntesis el código o la moto** | Buscando *duke*, "FILTRO ACEITE INOKI × 10" solo no dice por qué salió. El detalle ya hacía lo mismo con "Aplica a"; la lista lo dice más corto |
| 2026-09-15 | 8 | **El aviso de pérdida dice las dos cifras** ("te costaron $14.000 y cobras $5.000") y cuántos repuestos quedaron fuera por no tener costo | Pedido en la revisión del 2026-09-15: "Queda $3.286 por debajo de lo que costó" no se entendía. Con las dos cifras se ve de dónde sale la pérdida. Es el RF-017 del spec 0003; el texto vive en `textoDePerdida` con su prueba |
| 2026-09-19 | 9 | **Los renglones de la página se piden siempre, en una sola consulta**, y de ahí salen los primeros tres y, buscando, los que coinciden | Una consulta por página, busque o no (la prueba cuenta `vecesPedidasLineasVigentes`). Una página vacía no pide nada |
| 2026-09-19 | 9 | **"y N más" sale de `renglones` del resumen**, que ya cuenta solo los vigentes; los primeros no viajan con el total de renglones | El resumen ya lo traía; no hay dos cuentas que puedan diferir |
| 2026-09-19 | 9 | **Romper a propósito, hecho**: sin el tope de tres, falla `sinBuscarTraeLosPrimeros` | La prueba muerde. El checkpoint en QA queda para el QA general del usuario: la base está limpia y no se crearon facturas de prueba |
