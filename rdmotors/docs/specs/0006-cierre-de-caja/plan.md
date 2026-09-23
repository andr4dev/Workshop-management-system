# Plan 0006 — Cierre de caja, gastos y retiros

**Traduce:** `docs/specs/0006-cierre-de-caja/spec.md`. Las decisiones quedaron así:

| Decisión | Resolución (2026-09-16) |
|---|---|
| 1. Compras en efectivo | Se marca si la plata salió del cajón |
| 2. Conteo que no cuadra | El turno se cierra igual y se escriben observaciones |
| 3. Gasto o retiro mayor que lo que debería haber | Se pide confirmación antes de registrarlo |
| 4. Categorías de gasto | Vienen por defecto, cada una costo o gasto; el cliente crea las suyas |
| 5. Gastos que no salen del cajón | **Entran en este spec** |
| Orden | Caja (0006) → reportes (0007) → login (0004), los tres antes de usar el sistema en la tienda |

**Estado:** aprobado el 2026-09-16 · **las 5 fases implementadas el 2026-09-16** · falta el recorrido en el navegador contra QA y la verificación manual del usuario

---

## Contexto

El turno se abre y nunca se cierra. Lo que sale del cajón (flete, almuerzo, lo que se lleva el dueño,
una compra pagada con esos billetes) no queda en ninguna parte, y los gastos del negocio que no pasan
por el cajón (arriendo por Nequi) tampoco. Sin eso no hay arqueo, y el reporte de utilidad neta del
spec 0007 no tendría con qué calcularse.

Lo que ya existe y se reusa:

| Pieza | Dónde |
|---|---|
| Turno con fondo, un solo abierto, columnas de cierre vacías | `caja/dominio/TurnoCaja.java` · `V8__turno_de_caja.sql:18-23` · `RepositorioTurnosJpa` (`saveAndFlush` + traducción del índice) |
| Venta apunta a su turno; la anulada guarda en qué turno se anuló | `ventas/dominio/Venta.java:231-243` · `V9__venta.sql` |
| Pagos por forma, y solo el efectivo entra al cajón | `pago_venta` (V9) · `FormaPago.entraAlCajon()` |
| Llave contra doble envío, revisada antes y después de bloquear, con choque del índice traducido | `ventas/aplicacion/CobrarVenta.java` · `RepositorioVentasJpa` |
| Compra con forma de pago y cuenta, reglas en `Compra.exigirPagoCoherente` y `corregirDatos` | `compras/dominio/Compra.java:143-215,319-330` · `ComandoRegistrarCompra` · `ComandoCorregirCompra` |
| Lista con nombre único sin mayúsculas, alta y desactivación, modal de administración | `CuentaPago` · `RegistrarCuenta` · `DesactivarCuenta` · `frontend/.../compra/ModalCuentas.jsx` |
| Motivo obligatorio, auditoría con antes y después | `compartido/dominio/Motivo` · `EventoAuditoria` · `AccionAuditada` |
| Documento de 80 mm e impresión por iframe | `frontend/src/utils/ticket.js` (`esc`, `fila`, estilos) · `utils/imprimir.js:39` · `componentes/venta/Ticket.jsx` |
| Pestañas del módulo Vender, páginas de lista con clave y reintento | `componentes/venta/PestanasVenta.jsx` · `paginas/Ventas.jsx` |

---

## Trabajo previo, ya especificado en otros specs

Dos cambios chicos pendientes que conviene hacer antes de la fase 1 (se hacen si el usuario los pide
junto con esta):

- **Spec 0002, fase 9** (plan escrito): sin buscar, cada factura del historial muestra sus primeros
  repuestos.
- **Spec 0003, RF-028 revisado:** la venta a medias se retoma sola, sin el aviso que bloquea.
  - `Vender.jsx`: al montar, si hay borrador, se carga directo; si estaba `enviada`, abre el cobro.
  - Se quita el aviso *Retomar / Descartar* y el bloqueo por `pendiente` en el buscador y el catálogo.

---

## Resumen

| Fase | Qué | Pantalla | Migración |
|---|---|---|---|
| 1 | Categorías de gasto y gastos (del cajón y por fuera) | Vender › Gastos | V12 |
| 2 | Retiros, compras de caja, lo que debería haber, y el bloqueo del turno | Vender › Caja · Compras | V13 |
| 3 | Cerrar el turno a ciegas, con observaciones | Vender › Caja | V14 |
| 4 | Comprobante del cierre y turnos anteriores | Caja › Turnos | — |
| 5 | Contar por billetes y fondo sugerido (P3) | Caja | — |

---

## Decisiones tomadas al planear

1. **Todo vive en el módulo `caja/`**: turnos, retiros, gastos, categorías y arqueo.
   - Los gastos por fuera del cajón no son "de caja", pero ponerlos en un módulo aparte deja una
     dependencia circular: el gasto del cajón necesita el arqueo (confirmación de la decisión 3) y el
     arqueo necesita los gastos.
   - Los reportes (0007) leen los gastos por su puerto.
2. **Gasto y retiro son dos tablas.** El modelo decía `movimiento_caja` con tipo, pero sus reglas no se
   parecen:
   - un gasto tiene categoría y puede no tocar el cajón;
   - un retiro siempre es del turno y no es gasto en ningún reporte.
3. **Nada entra a un turno mientras se cierra: se bloquea la fila del turno** (RF-017, RF-018).
   - Todo lo que mueve plata del turno toma `FOR SHARE` sobre él **antes que cualquier otro bloqueo**:
     cobrar, anular venta, gasto del cajón, retiro, y registrar, corregir o anular una compra de caja.
   - Cerrar toma `FOR UPDATE`: espera a que terminen, y quien llega después ve el turno cerrado.
   - Puerto `RepositorioTurnos.abiertoParaMover()` (`@Lock(PESSIMISTIC_READ)`) y
     `buscarParaCerrar(id)` (`PESSIMISTIC_WRITE`).
   - Como el turno va primero en todos, el orden de bloqueos del spec 0003 (variantes por id, luego el
     contador) no cambia, y no hay traba.
4. **Lo que debería haber lo calcula un solo caso de uso, `CalcularArqueo`**, que devuelve
   `ArqueoDeTurno(fondo, ventasEfectivo, ventasTransferencia, descuentos, devolucionesEfectivo,
   gastosCajon, retiros, comprasCajon)` con `esperado()`.
   - Cada suma la da el puerto de quien tiene el dato: ventas, gastos, retiros y compras, cada una en
     una consulta.
   - Lo usan el cierre y la confirmación de la decisión 3. Ninguna pantalla lo pide antes de cerrar.
5. **El cierre guarda el desglose completo, no solo el esperado.**
   - Columnas en `turno_caja` con un `CHECK` de que las partes suman el esperado y de que
     `diferencia = contado − esperado`.
   - Así el detalle de un turno viejo no cambia si después se anula una de sus ventas (RF-013).
6. **La confirmación de la decisión 3 la decide el servidor**, porque el frontend no conoce lo que
   debería haber (arqueo a ciegas).
   - El comando trae `confirmado`. Si el monto supera lo que debería haber y no viene confirmado, el
     servidor responde 409 con código `CONFIRMAR_MONTO` y un mensaje sin cifra.
   - La pantalla pregunta *"Es más de lo que debería haber en el cajón. ¿Seguro?"* y reenvía con la
     misma llave.
7. **Gasto y retiro llevan llave** (`llave_idempotencia UNIQUE`), revisada antes y después del bloqueo,
   como la venta.
8. **Una compra de caja cuyo turno ya cerró no cambia ni la marca ni la forma de pago.** Sus renglones
   sí se corrigen: el arqueo cerrado no se recalcula.
9. **Las tres acciones de auditoría nuevas entran juntas en V12**: `ANULAR_GASTO`, `ANULAR_RETIRO` y
   `CERRAR_CAJA_CON_DIFERENCIA`. Así el `CHECK` se recrea una sola vez.
10. **Un solo documento de 80 mm.** De `ticket.js` sale `utils/papel80mm.js` (`esc`, `fila`, estilos y
    envoltura) para el ticket de venta y el del cierre. `Ticket.jsx` recibe el HTML ya armado.
11. **Un gasto por fuera del cajón se anula con motivo en cualquier momento.** RF-006 ata la anulación
    al turno abierto porque el arqueo se firma al cerrar; un gasto por fuera no está en ningún arqueo.
12. **Montos en `numeric(14,2)`**, como `venta` y `pago_venta` (V9), con valores enteros en `Dinero`.

---

## Fase 1 · Categorías de gasto y gastos (H1, H10, H11; RF-001, RF-002, RF-002a, RF-005, RF-006 gastos, RF-007, RF-007a)

| Pieza | Dónde cae |
|---|---|
| `CategoriaGasto` (`nueva(nombre, naturaleza)`, `renombrar`, `desactivar`), `NaturalezaGasto` {COSTO, GASTO} | `caja/dominio/` |
| `Gasto`: `delCajon(turnoId, …)` o `porFuera(formaPago, cuenta, fecha, …)`; `anular(motivo, usuario, cuando)` | `caja/dominio/` |
| `RepositorioCategoriasGasto` (`buscar`, `buscarPorNombre` sin mayúsculas ni tildes, `activas`, `guardar`) | `caja/dominio/puerto/` |
| `RepositorioGastos` (`guardar`, `buscar`, `buscarPorLlave`, `buscarParaModificar`, `listar(filtro, página)`, `totales(filtro)`, `delCajonEnTurno(turnoId)`) | `caja/dominio/puerto/` |
| `RepositorioTurnos.abiertoParaMover()` | puerto y `RepositorioTurnosJpa` |
| `RegistrarCategoriaGasto`, `RenombrarCategoriaGasto`, `DesactivarCategoriaGasto` | `caja/aplicacion/` |
| `RegistrarGasto`: llave → si es del cajón, turno `FOR SHARE` → llave otra vez → valida y guarda. Por fuera: la cuenta la exige la transferencia, como en compras | `caja/aplicacion/` |
| `AnularGasto`: el del cajón solo con su turno abierto; el de por fuera siempre; motivo; evento `ANULAR_GASTO` | `caja/aplicacion/` |
| `ConsultarGastos` (lista con filtros de fecha y categoría, totales con lo que salió del cajón) | `caja/aplicacion/` |
| `CategoriaGastoController` y `GastoController` (`POST`, `GET` con filtros, `/totales`, `/{id}/anulacion`) | `caja/infraestructura/` |
| `AccionAuditada` gana las tres acciones (decisión 9) | `compartido/dominio/` |

**V12:**
- `categoria_gasto`:
  - `id`, `nombre`, `naturaleza CHECK ('COSTO','GASTO')`, `activa`;
  - índice único sobre `lower(nombre)`;
  - sembrada con las 11 del RF-002, todas GASTO.
- `gasto`:
  - `id`, `categoria_id` FK, `monto > 0`, `descripcion` no vacía, `del_cajon`, `turno_id` FK;
  - `forma_pago`, `cuenta_id`, `fecha`, `registrado_por_id`, `registrado_en`;
  - `llave_idempotencia UNIQUE` y datos de anulación.
- `CHECK`s del gasto:
  - `del_cajon` ↔ `turno_id NOT NULL` y `forma_pago = 'EFECTIVO'` sin cuenta;
  - transferencia ↔ cuenta;
  - datos de anulación completos o vacíos.
- Se recrea el `CHECK` de `evento_auditoria.accion`.

**Frontend:**
- `PestanasVenta`: *Vender · Ventas del turno · Caja · Gastos*.
- `paginas/Gastos.jsx` (`/vender/gastos`): filtros en la URL, totales (todo y lo que salió del cajón),
  lista con categoría y naturaleza, anulados tachados, y *Registrar gasto*.
- `componentes/caja/ModalGasto.jsx`:
  - categoría, monto, descripción y *¿Salió del cajón?* (sí por defecto si hay turno abierto);
  - si no salió del cajón: efectivo o transferencia con cuenta (`cuentasApi`) y fecha;
  - la llave nace al abrir y el 409 de confirmación se maneja (decisión 6, activo desde la fase 2).
- `componentes/caja/ModalCategoriasGasto.jsx`: crear con naturaleza, renombrar y desactivar, siguiendo
  el patrón de `ModalCuentas`.
- `utils/gastos.js` + pruebas: filtros ↔ URL, comando del gasto y texto del origen.
- `api/cliente.js`: `gastosApi` y `categoriasGastoApi`.

**Pruebas:**
- `CategoriaGastoTest` y los casos de uso de categorías: nombre repetido sin mayúsculas ni tildes,
  renombrar a uno existente, desactivar.
- `RegistrarGastoTest`:
  - del cajón exige turno;
  - por fuera no lo exige;
  - la transferencia exige cuenta;
  - misma llave, un solo gasto;
  - sin descripción, monto $0 o categoría desactivada: no se registra.
- `AnularGastoTest`: con turno abierto sí; turno cerrado no; por fuera sí; queda el evento.
- `Falsos`: `CategoriasGastoEnMemoria`, `GastosEnMemoria`; `TurnosEnMemoria` cuenta
  `vecesBloqueadoParaMover`.
- Integración:
  - `CHECK`s saltándose el caso de uso;
  - misma llave a la vez deja uno;
  - la siembra de las 11 categorías.
- `MigracionesIntegracionTest`: V12 sobre una base con turno abierto y compras.
- `.http`: `04-caja.http` con gastos y categorías.

**Checkpoint:**
- En QA, un flete de $15.000 del cajón y un arriendo de $800.000 por transferencia desde una cuenta
  aparecen en *Gastos* con sus totales.
- Se crea *Publicidad* (gasto) y se desactiva.
- Doble clic registra uno.

---

## Fase 2 · Retiros, compras de caja y lo que debería haber (H2, H4; RF-003, RF-004, RF-006 retiros, RF-008 a RF-011, RF-017 parcial)

| Pieza | Dónde cae |
|---|---|
| `Retiro` (`registrar(turnoId, monto, motivo, …)`, `anular`) | `caja/dominio/` |
| `ArqueoDeTurno` (record con las partes y `esperado()`) | `caja/dominio/` |
| `RepositorioRetiros` (`guardar`, `buscarPorLlave`, `buscarParaModificar`, `delTurno`, `sumaDelTurno`) | `caja/dominio/puerto/` |
| Sumas por turno en los puertos que tienen el dato. Ventas: `efectivoCobradoEnTurno`, `transferenciaCobradaEnTurno`, `descuentosEnTurno`, `efectivoDevueltoEnTurno` (anuladas en el turno, solo la parte en efectivo). Compras: `pagadoDeCajaEnTurno` | `ventas/dominio/puerto/`, `compras/dominio/puerto/` y sus adaptadores |
| `CalcularArqueo` | `caja/aplicacion/` |
| `RegistrarRetiro`, `AnularRetiro` (evento `ANULAR_RETIRO`) | `caja/aplicacion/` |
| La confirmación de la decisión 3 en `RegistrarGasto` (del cajón) y `RegistrarRetiro` → `MasDeLoQueDeberiaHaberException` → 409 `CONFIRMAR_MONTO` | `caja/`, `ManejadorDeErrores` |
| `CobrarVenta` y `AnularVenta`: el turno con `abiertoParaMover()` en vez de `abierto()`, y primero | `ventas/aplicacion/` |
| `Compra`: `pagadaDeCaja` y `turnoId`; `registrar` y `corregirDatos` los reciben y aplican la decisión 8 | `compras/dominio/` |
| `RegistrarCompra`, `CorregirCompra` y `AnularCompra`: si es (o queda) de caja, turno `FOR SHARE` primero; los comandos ganan `pagadaDeCaja` | `compras/aplicacion/` |
| `RetiroController`; `CompraController` recibe y devuelve `pagadaDeCaja` | infraestructura |
| `GET /api/turnos/abierto/movimientos`: gastos del cajón, retiros y compras de caja del turno, **sin sumas** | `TurnoController` |

**V13:**
- `retiro_caja`: `turno_id NOT NULL`, `monto > 0`, `motivo`, `llave UNIQUE` y datos de anulación.
- `compra`:
  - gana `pagada_de_caja boolean NOT NULL DEFAULT false` y `turno_id` FK;
  - `CHECK`: `pagada_de_caja` ↔ `turno_id NOT NULL` y `forma_pago = 'EFECTIVO'`;
  - las existentes quedan en `false`.

**Frontend:**
- `paginas/Caja.jsx` (`/vender/caja`):
  - el turno abierto (desde, quién y fondo) y sus movimientos del cajón (gastos, retiros y compras de
    caja), con anular;
  - *Registrar gasto*, *Registrar retiro* y, desde la fase 3, *Cerrar turno*;
  - sin turno, ofrece abrirlo.
- `componentes/caja/ModalRetiro.jsx`: monto y motivo, con la confirmación.
- `Compra.jsx`: en efectivo, casilla *"Se pagó con plata del cajón"*. Sin turno abierto se deshabilita,
  con el porqué.
- `utils/compra.js` `textoDelPago` dice *"Efectivo · del cajón"*; se ve en el historial y el detalle.

**Pruebas:**
- `ArqueoDeTurnoTest`: el ejemplo del §2 da $88.400, y las partes suman.
- `CalcularArqueoTest` con dobles:
  - la venta de otro turno anulada en este resta;
  - la mixta anulada resta solo el efectivo;
  - gastos y retiros anulados no restan;
  - un gasto por fuera no resta;
  - una compra de caja anulada no resta.
- `RegistrarRetiroTest`: sin motivo, sin turno, misma llave, y confirmación requerida cuando supera.
- `RegistrarCompraTest` y `CorregirCompraTest`:
  - de caja exige turno y efectivo;
  - con el turno cerrado no se cambia la marca.
- `CobrarVentaTest` y `AnularVentaTest`: toman el turno con `abiertoParaMover`.
- Integración:
  - las sumas por turno contra Postgres coinciden con el cálculo en memoria para el mismo escenario;
  - `CHECK`s de V13;
  - V13 sobre base con filas.

**Checkpoint:**
- En QA, un retiro de $100.000 y una compra de $50.000 marcada de caja aparecen en *Caja*.
- Un retiro de $10.000.000 pide confirmación.
- Ninguna pantalla muestra lo que debería haber.

---

## Fase 3 · Cerrar el turno (H3; RF-012 a RF-018)

| Pieza | Dónde cae |
|---|---|
| `TurnoCaja.cerrar(arqueo, contado, usuario, cuando)` guarda las partes, `esperado`, `contado` y `diferencia`; `escribirObservaciones(texto)` una sola vez; `TurnoYaCerradoException(cerradoEn, cerradoPor)` | `caja/dominio/` |
| `RepositorioTurnos.buscarParaCerrar(id)` (`FOR UPDATE`), `buscar(id)` | puerto y adaptador |
| `CerrarTurno`: bloquea → exige abierto → `CalcularArqueo` → `cerrar` → si la diferencia no es cero, evento `CERRAR_CAJA_CON_DIFERENCIA` con las cifras → guarda | `caja/aplicacion/` |
| `EscribirObservaciones` | `caja/aplicacion/` |
| `POST /api/turnos/{id}/cierre` (`{contado}` → el cierre con desglose); `PUT /api/turnos/{id}/observaciones` (409 si ya tiene); 409 de turno ya cerrado con quién y cuándo | `TurnoController`, `ManejadorDeErrores` |

**V14:**
- `turno_caja` gana `ventas_efectivo`, `ventas_transferencia`, `descuentos`, `devoluciones_efectivo`,
  `gastos_cajon`, `retiros`, `compras_cajon`, `esperado`, `contado`, `diferencia` y `observaciones`.
- `CHECK`s:
  - cerrado ↔ todas las cifras presentes;
  - `contado >= 0`;
  - `esperado = fondo + ventas_efectivo − devoluciones_efectivo − gastos_cajon − retiros − compras_cajon`;
  - `diferencia = contado − esperado`.
- El turno abierto de QA queda válido.

**Frontend:**
- `componentes/caja/ModalCerrarTurno.jsx`:
  - **paso 1**: *"¿Cuánto efectivo contaste?"* y confirmación *"Al confirmar se cierra el turno"*;
  - **paso 2**: el desglose, lo contado y la diferencia (sobrante o faltante); con diferencia pide las
    observaciones, que se pueden dejar para después;
  - si la red se cae y el reintento dice *ya cerrado*, carga el turno y muestra su resultado.
- `utils/arqueo.js` + pruebas:
  - `filasDelDesglose(cierre)`, que se omite la fila en cero pero las partes siempre suman;
  - `textoDeDiferencia`;
  - `problemasDelArqueo`.
- Después de cerrar, Vender y Caja muestran *No hay un turno abierto* y ofrecen abrir otro.

**Pruebas:**
- `TurnoCajaTest`: cerrar guarda las partes; cerrado no se vuelve a cerrar; observaciones una vez;
  contado negativo no.
- `CerrarTurnoTest`:
  - el ejemplo da faltante $1.400 y deja el evento;
  - sin diferencia no deja evento;
  - cerrado, ya no se cobra ni se registra un gasto del cajón.
- Integración contra Postgres:
  - **cobros y cierre a la vez**, repetido 20 veces con latch: cada cobro o queda antes del cierre (y
    `ventas_efectivo` lo incluye) o se rechaza por turno cerrado;
  - dos cierres a la vez dejan uno;
  - anular después una venta del turno cerrado no cambia sus cifras;
  - `CHECK`s de V14;
  - el `@BeforeEach` que cierra turnos por SQL pasa a llenar las cifras.
- Romper a propósito:
  - cobrar sin el `FOR SHARE` → la prueba de cobros y cierre;
  - cerrar sin `FOR UPDATE` → dos cierres;
  - el `CHECK` de la suma → integración.

**Checkpoint:**
- En QA se cierra el turno abierto desde el 14 de septiembre: el conteo a ciegas muestra el desglose y la
  diferencia, las observaciones quedan y no se puede vender hasta abrir otro.
- En un turno nuevo se reproduce el ejemplo del §2 y da faltante $1.400.

---

## Fase 4 · Comprobante del cierre y turnos anteriores (H5, H6; RF-019, RF-020)

| Pieza | Dónde cae |
|---|---|
| `ConsultarTurnos`: `cerrados(página)` del más reciente al más antiguo; `detalle(id)` con cifras guardadas, movimientos y ventas | `caja/aplicacion/` |
| `GET /api/turnos?estado=CERRADO&pagina=`, `GET /api/turnos/{id}` | `TurnoController` |

**Frontend:**
- `utils/papel80mm.js` (decisión 10); `ticket.js` pasa a usarlo sin cambiar su salida (sus pruebas
  siguen igual).
- `utils/comprobanteCierre.js` + pruebas: arma las filas desde el cierre y los datos de la tienda,
  verifica que las partes cuadren y escapa los textos.
- Al cerrar, imprime solo en el computador del mostrador (`esteEquipoEsElMostrador`, `imprimirHtml`); si
  falla, avisa con *Reimprimir*.
- `paginas/Turnos.jsx` (`/vender/caja/turnos`): abrió, cerró, lo que debería haber, lo contado y la
  diferencia. Los faltantes van en rojo y los que no tienen observaciones dicen *"sin explicación"*.
- `paginas/DetalleTurno.jsx` (`/vender/caja/turnos/:id`): desglose, movimientos, ventas, observaciones
  (escribirlas si faltan) y el comprobante con *Imprimir*.

**Pruebas:**
- `ConsultarTurnosTest`.
- `comprobanteCierre.test.js`: cuadra, escapa y trae la anulada.
- Integración: la lista y el detalle devuelven las cifras guardadas.

**Checkpoint:** el cierre de la fase 3 aparece en *Turnos* con su diferencia resaltada; su detalle
reimprime el comprobante, que cuadra (PDF de 80 mm como en el spec 0003).

---

## Fase 5 · Contar por billetes y fondo sugerido (P3; RF-021, RF-022)

- `utils/arqueo.js`: `totalPorDenominaciones(conteo)` (billetes de $100.000 a $2.000 y monedas de $1.000
  a $50), con pruebas.
- `ModalCerrarTurno`: *Contar por billetes* opcional; el total sale solo al campo.
- `ModalAbrirTurno`: sugiere el fondo del último turno cerrado (`GET /api/turnos?estado=CERRADO&tamano=1`).

**Checkpoint:** se cuentan $87.000 por billetes y el cierre toma esa cifra; el turno nuevo sugiere el
fondo anterior.

---

## Riesgos

| Riesgo | Fase | Qué hacer |
|---|---|---|
| `PESSIMISTIC_READ` no se traduce a `FOR SHARE` en Hibernate 7 con Postgres | 2 | La prueba de cobros y cierre a la vez lo detecta; si no, `FOR SHARE` con consulta nativa en el adaptador |
| La prueba de concurrencia no falla siempre al romperla | 3 | Repetir 20 veces con latch; anotar en la bitácora cuántas veces falló al romper |
| El `@BeforeEach` que cierra turnos por SQL choca con los `CHECK` nuevos | 3 | Se actualiza en la misma fase |
| El turno de QA tiene catorce ventas de prueba: su primer cierre dará cifras raras | 3 | Esperado: se cierra con observaciones *"datos de prueba"* |
| Una compra de caja vieja de un turno cerrado que se quiere corregir de forma de pago | 2 | La decisión 8 lo impide con un mensaje claro |
| El cajero ve *Ventas del turno* y puede calcular lo que debería haber | todas | Declarado en el spec; se resuelve con roles (0004) |

---

## Verificación final

1. Backend abajo y `./mvnw clean test`: dominio, Postgres y migraciones.
2. `cd frontend && npx eslint src/ && node --test src/utils/*.test.js && npx vite build`.
3. Criterios del §8 del spec, uno por uno, en Edge contra QA (claro, oscuro y 390 px), incluido el
   ejemplo del §2 en un turno nuevo.
4. `http/04-caja.http` al día.
5. Romper a propósito y ver fallar su prueba:
   - llave del gasto;
   - `FOR SHARE` de cobrar;
   - `FOR UPDATE` del cierre;
   - la suma del arqueo (la anulada de otro turno);
   - `CHECK` de la suma en V14;
   - la confirmación de la decisión 3.

---

## Bitácora de decisiones

Se llena **durante** la implementación, con fecha, fase, decisión y porqué.

| Fecha | Fase | Decisión | Por qué |
|---|---|---|---|
| 2026-09-16 | 2 | **Las reglas del arqueo viven en `ArqueoDeTurno` (dominio)**; los puertos solo traen las filas del turno (`delTurno`, `anuladasEnTurno`, `delCajonEnTurno`, `deCajaEnTurno`). Cambia la decisión 4, que ponía cada suma en una consulta | Qué suma y qué resta (la anulada de otro turno, la mixta, lo anulado) se prueba sin base. `calcular` vuelve a filtrar por el turno: una fila de otro turno que se cuele no mueve el arqueo |
| 2026-09-16 | 2 | Un solo detalle, `GET /api/turnos/{id}`, para el turno abierto (sin `cierre`) y para el cerrado. No existe `/abierto/movimientos` | La sección Caja y el historial muestran lo mismo; lo único que cambia es que el abierto no trae cifras. Menos código y un solo sitio que cuida el arqueo a ciegas |
| 2026-09-16 | 2 | En `CorregirCompra` y `AnularCompra` el turno se bloquea **después** de la compra, no antes | El cierre nunca bloquea compras, así que ese orden no puede trabarse con él; leer la compra sin bloqueo primero la dejaría vieja en memoria y la versión fallaría al guardar |
| 2026-09-16 | 2 | La lista de lo que salió del cajón incluye el efectivo devuelto por ventas anuladas en el turno | Es plata que sale del cajón: sin ella el cajero no entiende el faltante de un día con anulaciones |
| 2026-09-16 | 3 | Escribir observaciones que ya existen responde 422 y no 409 | Es una regla del cierre como cualquier otra; no hay nada que la pantalla deba recargar |
| 2026-09-16 | 3 | La prueba de cobros y cierre a la vez saca el cierre entre 0 y 20 ms después de los cobros | Sin espera, el cierre ganaba casi siempre (8 cobros contados, 72 rechazados) y un bloqueo roto podía pasar. Con la espera: 64 y 16. **Rota** (cobrar sin `FOR SHARE`) falló en la ronda 0 |
| 2026-09-16 | 3 | Romper a propósito, cada una vio fallar su prueba: `FOR SHARE` de cobrar, `FOR UPDATE` del cierre, el `CHECK` de la suma en V14, la devolución de la anulada de otro turno, la confirmación de la decisión 3 y la llave del gasto | Regla del proyecto: una prueba que nunca falló no prueba nada |
| 2026-09-16 | 1 | Al crear una categoría no hay naturaleza por defecto: se elige Gasto o Costo | Como la forma de pago: lo que nadie decidió no se registra como decidido |
| 2026-09-16 | 4 | `utils/papel80mm.js` con los estilos de 80 mm; el ticket de venta sale **byte a byte igual** que antes (comparado) | Decisión 10: el margen que evita que el precio se corte se arregla en un solo sitio para los dos documentos |
| 2026-09-16 | 3 | **Después de probarlo, el usuario pidió ver el desglose antes de cerrar**: lo que debería haber se ve en vivo en Caja todo el turno, y al contar la diferencia sale mientras se escribe. El detalle de un turno abierto trae `arqueo` (calculado al pedirlo); el cerrado sigue trayendo `cierre` (lo guardado). `DesgloseCierre` pasa a `DesgloseArqueo` | Eligió "en vivo en Caja" frente a "solo al contar". Se deja el arqueo a ciegas; con el login (0004) se puede esconder al cajero. El cierre sigue calculando de nuevo con el turno bloqueado: lo guardado es lo que vale |
| 2026-09-16 | 1 | **La lista de gastos pasa a Reportes** (`/reportes/gastos`, módulo nuevo en la cabecera); en Vender queda solo Caja, donde *Registrar gasto* abre el modal. Sin turno, Caja ofrece registrar un gasto por fuera. `/vender/gastos` redirige | Pedido del usuario: la lista es de reportes, registrar es de caja. V14 no se tocó: ya estaba aplicada en QA y cambiarle un comentario rompe la validación de Flyway |
| 2026-09-16 | — | **No se hizo el recorrido en el navegador.** El usuario lo detuvo antes de correrlo: cerraba el turno de QA y creaba gastos de prueba | Queda para la verificación manual. Lo que sí quedó: V12 a V14 aplicadas sobre la base de QA sin errores, con el backend en marcha |
