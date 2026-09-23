# Plan 0003 — Venta de mostrador y comprobante

**Traduce:** `docs/specs/0003-venta-de-mostrador/spec.md` (cerrado, sin valor fiscal)

**Aprobado el 2026-09-14.** Al aprobarse:
- [x] este plan se guardó como `docs/specs/0003-venta-de-mostrador/plan.md`;
- [x] el spec ganó RF-031 a RF-033 (ver *Decisiones*, 13 a 15);
- [x] se actualizaron el índice de specs y `PLAN_DE_TRABAJO.md`;
- **se para**. La fase 1 arranca cuando el usuario lo diga.

---

## Contexto

El sistema compra pero no vende: el stock solo sube, no hay caja y no hay comprobante. El spec 0003
cerró qué construir:
- abrir turno;
- vender buscando por código o nombre;
- cobrar en efectivo, transferencia (sin cuenta) o mixto;
- descuento sobre el total con motivo;
- ticket impreso por el navegador del computador del mostrador (ticketera POS de 80 mm, modo
  kiosco);
- buscar y reimprimir, anular, retomar una venta interrumpida;
- que una venta anulada no bloquee corregir compras.

Fuera: login (spec 0004), cierre de caja (rebanada 3), valor fiscal.

Lo que ya existe y se reusa, verificado:

| Pieza | Dónde |
|---|---|
| `Variante.descontar` (rechaza sin stock, no toca el promedio) y `reponerPorReversion` | `inventario/dominio/Variante.java:123`, `:222` |
| `MovimientoKardex.porVenta` al promedio vigente | `inventario/dominio/MovimientoKardex.java:175` |
| Bloqueo de fila `buscarParaModificar` | `inventario/dominio/puerto/RepositorioVariantes.java:33` |
| Kardex `agregar` / `buscar` / `huboSalidasDespuesDe` | `inventario/dominio/puerto/RepositorioKardex.java` |
| `StockInsuficienteException` → 409 | `ManejadorDeErrores.java:29` |
| Buscar por código exacto y por texto sin tildes | `BuscarRepuestos.java:43`, `:72` · `repuestosApi.porCodigo/porTexto` en `api/cliente.js` |
| Auditoría (`EventoAuditoria.nuevo`, `RepositorioAuditoria`) y `ModalMotivo` | `compartido/…` · `frontend/src/componentes/ModalMotivo.jsx` |
| Motivo obligatorio | `Compra.exigirMotivo`, `compras/dominio/Compra.java:269` |
| Impresión 80 mm por iframe + `window.print`, y guía de ticketera | car-wash `frontend/src/utils/printPaymentReceipt.js` · `scripts/SETUP-TICKETERA.md` |
| Cableado de casos de uso sin `@Service` | `compartido/infraestructura/ConfiguracionCasosDeUso.java` |

Ojo: `findByCodigo` no filtra repuestos inactivos (`RepositorioVariantesJpa.java:86`). Vender
tiene que exigir `activa` en el caso de uso.

---

## Resumen

Cinco fases, cada una termina en algo que se demuestra.

| Fase | Qué | Pantalla | Migración |
|---|---|---|---|
| 1 | Turno de caja | Vender (abrir turno) | V8 |
| 2 | Cobrar una venta en el backend: stock, kardex, número, llave, descuento | — (`.http`) | V9 |
| 3 | Pantalla de venta: buscador, renglones, cobro, descuento, borrador | Vender | — |
| 4 | Comprobante, datos de la tienda y ventas del turno | Vender, Ventas, Tienda | V10 |
| 5 | Anular venta, y que la venta anulada no bloquee compras | Detalle de venta | — |

Paquetes nuevos: `caja/` y `ventas/` en `domain` y en `pos`, con las mismas cuatro carpetas de
siempre (`dominio`, `dominio/puerto`, `aplicacion`, `infraestructura`).

---

## Decisiones tomadas al planear

1. **El número sale de una fila contador, no de una secuencia de Postgres.**
   - `UPDATE consecutivo SET ultimo = ultimo + 1 WHERE nombre = 'VENTA' RETURNING ultimo`, dentro de
     la transacción del cobro.
   - Una secuencia no retrocede con un rollback: un cobro fallido dejaría un hueco, y un hueco en
     la serie se lee como una venta borrada (`SPEC_Modelo_Datos.md:294-301`).
   - La fila queda bloqueada hasta el commit y serializa los cobros. Con un cajero, no cuesta nada.
   - El primer número es el `ultimo` sembrado (0 → arranca en 1). Si el cliente trae un talonario,
     se ajusta antes de la primera venta.
2. **Orden fijo de bloqueos.** Cobrar bloquea las variantes **ordenadas por id** y después el
   contador. Anular bloquea la venta y sus variantes por id, y no toca el contador. Dos ventas con
   los mismos repuestos en distinto orden no se traban.
3. **Llave contra doble envío en la propia venta** (`venta.llave_idempotencia uuid UNIQUE`), no una
   tabla genérica: hoy es el único endpoint que suma.
   - La llave **nace con el borrador en el navegador y viaja dentro de él**. Reintentar tras un
     corte usa la misma llave.
   - Con la llave repetida: si ya existe, se devuelve esa venta (200).
   - Si llegan dos a la vez: la segunda choca con el índice único, el adaptador lo traduce a
     `VentaRepetidaException`, y el controlador responde con la venta ya guardada.
4. **El precio que vio el cajero viaja en el comando.** El servidor lo compara con el precio vigente
   y, si difiere, rechaza con la lista de cambios (409). Nunca se cobra un precio distinto al visto.
5. **Todo se valida antes de mover nada**: activo, precio > 0, precio visto y stock de **todos** los
   renglones. El error lista todos los renglones con problema (como `RenglonesBloqueadosException`).
6. **`FormaPago` se mueve a `compartido/dominio/`**: la usan compras y ventas. Cambio mecánico de
   imports; en la base se guarda como texto, así que no hay migración.
7. **`Compra.exigirMotivo` se mueve a `compartido/dominio/Motivo`**: compras y ventas, misma regla
   (obligatorio, sin espacios de sobra, hasta 300).
8. **El descuento se captura en pesos o en porcentaje y se guarda en pesos.** El porcentaje se guarda
   con 2 decimales como registro. La regla de `HALF_UP` al peso vive en un valor del dominio,
   `Descuento`. Ejemplos de prueba: 10% de $38.500 = $3.850; 7,5% de $13.333 = $1.000.
9. **El borrador va en `localStorage`, no en IndexedDB** como decía el modelo (`:519`).
   - Es un solo objeto chico, y la escritura síncrona en cada cambio es justo lo que sobrevive a un
     apagón.
   - IndexedDB llega con la cola sin internet de la rebanada 4.
   - Si el almacenamiento está bloqueado (navegación privada), la venta funciona igual y avisa que
     no se guardará el borrador.
10. **El comprobante imprime cifras del backend.** El HTML de 80 mm se arma en el frontend con la
    respuesta del servidor y **no recalcula totales** (skill `frontend`: "el total del ticket y el
    del reporte salen del mismo cálculo"). Una función pura verifica que los renglones sumen el
    total y avisa si no.
11. **El turno tiene columnas de cierre vacías desde ya**, para la rebanada 3. No hay endpoint de
    cierre.
12. **Hasta el spec 0004, "quién" es `X-Usuario-Id`**, el mismo `TODO(auth)` de compras.
13. **RF-031 · Efectivo por defecto y botones rápidos.** Exacto, y los billetes que superan el total
    ($20.000, $50.000, $100.000). El cambio sale solo.
14. **RF-032 · Motivos de descuento frecuentes** de un toque ("Cliente frecuente", "Negociación",
    "Producto con detalle"), más texto libre. El motivo sigue obligatorio.
15. **RF-033 · Datos de la tienda editables en pantalla** (nombre comercial, NIT, dirección,
    teléfono, mensaje al pie). Reemplaza la aclaración pendiente del encabezado. Se guarda como una
    sola fila, y `PUT` es reemplazo, idempotente por construcción.

---

## Fase 1 · Turno de caja — ✅ COMPLETA

**Cubre:** RF-001, RF-002 (parte de turno), RF-003 (enlace)

| Pieza | Dónde cae |
|---|---|
| `TurnoCaja` — `abrir(fondo, usuario, cuando)`; `EstadoTurno` ABIERTO/CERRADO | `caja/dominio/` |
| `RepositorioTurnos` — `abierto()`, `guardar` | `caja/dominio/puerto/` |
| `AbrirTurno` — fondo ≥ 0; si ya hay uno abierto, dice quién y desde cuándo | `caja/aplicacion/` |
| `V8__turno_de_caja.sql` | `pos/…/db/migration/` |
| `RepositorioTurnosJpa`: traduce la violación del índice único al mismo mensaje | `caja/infraestructura/` |
| `TurnoController`: `POST /api/turnos`, `GET /api/turnos/abierto` (404 si no hay) | `caja/infraestructura/` |

**V8:**
- `turno_caja` con `id`, `abierto_por_id`, `abierto_en`, `fondo`, `estado`, y `cerrado_*` nulos;
- `CHECK` de fondo ≥ 0;
- `CREATE UNIQUE INDEX ux_turno_abierto ON turno_caja ((estado)) WHERE estado = 'ABIERTO'`.

**Frontend:**
- Ruta `/vender`, primera en la navegación ("Vender").
- Si no hay turno, un aviso con **Abrir turno** que abre `componentes/venta/ModalAbrirTurno.jsx`.
- `turnosApi` en `api/cliente.js`.

**Pruebas:**
- `AbrirTurnoTest`: abre; fondo negativo; segundo turno rechazado con quién y cuándo.
- `Falsos.TurnosEnMemoria`.
- Integración:
  - el índice único rechaza un segundo turno abierto **saltándose el caso de uso**;
  - dos aperturas simultáneas dejan una.

**Checkpoint:** desde el navegador se abre un turno con $100.000 y un segundo intento se rechaza.

**Cómo quedó (2026-09-14):**

- Dominio 175 pruebas (5 nuevas), Postgres 39 (4 nuevas en `CajaYVentasIntegracionTest`), frontend
  73 (4 nuevas). Lint y build en verde.
- V8 aplicada a la base de QA.
- En el navegador:
  - `/` abre **Vender**;
  - sin turno, la pantalla lo dice y ofrece abrirlo;
  - se abrió con $100.000 y la franja muestra desde cuándo y el fondo.
- Por la API: un segundo turno responde 409 con `abiertoEn`.
- Roto a propósito: sin traducir el choque con el índice único, falla `dosAperturasSimultaneas`.
- En la base de QA quedó **un turno abierto** con $100.000: es en el que se venderá en las fases
  siguientes.

---

## Fase 2 · Cobrar una venta (backend completo, sin pantalla) — ✅ COMPLETA

**Cubre:** del lado del servidor, RF-007 a RF-016 (precio, stock, cobro, número, llave y
descuento), más las dos acciones de auditoría nuevas

| Pieza | Dónde cae |
|---|---|
| `FormaPago` y `Motivo` movidos | `compartido/dominio/` |
| `Venta` — `cobrar(...)` valida: al menos un renglón; repuestos distintos; subtotal = Σ renglones; descuento ≤ subtotal y con motivo; total = subtotal − descuento; pagos = total; como máximo un pago por forma; recibido ≥ monto en efectivo. `fotografia()` | `ventas/dominio/` |
| `LineaVenta` (precio como foto, total = precio × cantidad, `movimientoSalidaId`); `PagoVenta` (forma, monto, recibido); `EstadoVenta`; `Descuento` (valor del dominio) | `ventas/dominio/` |
| `PreciosCambiadosException`, `StockInsuficienteEnVentaException` (con todos los renglones), `VentaRepetidaException` | `ventas/dominio/` |
| `RepositorioVentas` — `siguienteNumero()`, `guardar`, `buscar`, `buscarPorLlave`, `buscarPorNumero`, `buscarParaModificar`, `delTurno` | `ventas/dominio/puerto/` |
| `ComandoCobrarVenta` (llave, renglones con varianteId, cantidad y precioVisto; descuento; pagos; usuario) | `ventas/aplicacion/` |
| `CobrarVenta` y `ConsultarVentas` (`delTurno`, `porNumero`, `detalle` → `DetalleVenta`) | `ventas/aplicacion/` |
| `AccionAuditada` + `ANULAR_VENTA`, `APLICAR_DESCUENTO` | `compartido/dominio/` |
| `V9__venta.sql` | migración |
| `RepositorioVentasJpa`: contador con `UPDATE … RETURNING`, índice único de llave traducido | `ventas/infraestructura/` |
| `VentaController`: `POST /api/ventas` (201 nueva / 200 repetida), `GET /api/ventas?turno=abierto`, `GET /api/ventas/numero/{n}`, `GET /api/ventas/{id}` | `ventas/infraestructura/` |
| `ManejadorDeErrores`: 409 precios y stock con su lista | `compartido/infraestructura/` |

**`CobrarVenta`, en orden:**
1. Si la llave ya existe, devuelve esa venta.
2. Exige turno abierto.
3. Bloquea las variantes ordenadas por id.
4. Valida todos los renglones (activo, precio > 0, precio visto, stock) y falla listando todos.
5. Pide `siguienteNumero()`.
6. Arma `Venta.cobrar(...)`.
7. Por renglón: `descontar`, `MovimientoKardex.porVenta`, anota el movimiento en el renglón.
8. Si hay descuento, registra el evento `APLICAR_DESCUENTO`.
9. Guarda.

**V9:**
- `consecutivo(nombre PK, ultimo)`, sembrado con `('VENTA', 0)`.
- `venta`:
  - `numero` UNIQUE, `turno_id` FK, `vendido_por_id`;
  - `subtotal`, `descuento_monto`, `descuento_modo`, `descuento_porcentaje`, `descuento_motivo`,
    `total`;
  - `estado`, `cobrada_en`, `llave_idempotencia` UNIQUE, `version`;
  - `anulada_en`, `anulada_por_id`, `anulada_en_turno_id`, `motivo_anulacion`.
- `CHECK`s de la venta:
  - total = subtotal − descuento;
  - motivo si hay descuento;
  - datos de anulación según el estado.
- `linea_venta` (`posicion`, `variante_id`, `cantidad`, `precio_unitario`, `total`,
  `movimiento_salida_id`).
- `pago_venta` (`forma`, `monto`, `recibido`), con `CHECK`: `recibido` solo en EFECTIVO y ≥ monto.
- Recrear el `CHECK` de `evento_auditoria.accion` con las dos acciones nuevas.

**Pruebas:**
- `VentaTest`: las partes suman; pagos que no cuadran; recibido menor; descuento sin motivo o mayor
  que el total; repuesto repetido.
- `DescuentoTest`: redondeo HALF_UP y tope del 100%.
- `CobrarVentaTest` (con dobles):
  - stock baja la cantidad exacta y el promedio no cambia;
  - kardex VENTA al promedio vigente;
  - números corridos 1, 2, 3;
  - stock insuficiente y precio cambiado listan todo y **no aplican nada**;
  - precio $0 e inactivo;
  - sin turno;
  - misma llave devuelve la misma venta sin descontar dos veces;
  - evento de descuento con quién.
- `Falsos.VentasEnMemoria` con contador.
- Integración contra Postgres:
  - **sin huecos**: un cobro que falla por stock y otro que pasa dejan números seguidos;
  - **última unidad**: dos cobros simultáneos dejan una venta;
  - **orden de bloqueo**: ventas A,B y B,A simultáneas terminan sin trabarse;
  - **llave repetida simultánea**: una sola venta y un solo descuento de stock;
  - **atomicidad**: si falla el segundo renglón, no queda nada;
  - `CHECK`s de la base saltándose el caso de uso;
  - `MigracionesIntegracionTest`: el `CHECK` de acciones recreado.
- `http/03-ventas.http` con camino feliz e infeliz.

**Checkpoint:**
- Por `.http`: se cobran dos ventas (N.º 1 y 2), el stock baja, reenviar la misma llave devuelve la
  N.º 2.
- Romper a propósito el orden de bloqueo, la llave y el contador: fallan exactamente sus pruebas.

**Cómo quedó (2026-09-14):**

- Dominio 204 pruebas (29 nuevas: `VentaTest`, `DescuentoTest`, `CobrarVentaTest`), Postgres 46 (7
  nuevas de venta). V9 aplicada a la base de QA.
- Por la API contra la base de QA:
  - la venta N.º 1 (2 × PRUEBA-144927, $1.000 de descuento, mixto con $50.000 recibidos) respondió
    201 con $30.000 de cambio;
  - repetirla respondió 200 con la misma venta y el stock bajó una sola vez (12 → 10);
  - precio viejo dio 409 con el renglón, y pagos que no cuadran dieron 422 con cuánto falta;
  - tras ese 422, que falló después de pedir número, el contador siguió en 1: sin hueco;
  - el descuento quedó en `evento_auditoria`.
- Rotas a propósito, y fallaron exactamente sus pruebas:
  - sin ordenar los bloqueos → `sinTrabasPorOrden` (Postgres detectó la traba y abortó un cobro);
  - sin la segunda mirada a la llave → `mismaLlaveALaVez` (el doble clic de la última unidad respondía "sin stock");
  - el contador fuera de la transacción, como una secuencia → `numeracionSinHuecos` (esperaba 2, fue 3).

---

## Fase 3 · Pantalla de venta y cobro — ✅ COMPLETA

**Cubre:** H2, H3, H5, H6, H9; RF-004 a RF-011, RF-015 a RF-017 (pantalla), RF-028, RF-029,
RF-031, RF-032

**Frontend** (`frontend/src`):
- `paginas/Vender.jsx` y `Vender.module.css`: buscador arriba, renglones, resumen, atajos visibles
  (**Enter** agrega, **F4** descuento, **F9** cobrar, **Esc** cancelar).
- `componentes/venta/`:
  - `BuscadorVenta.jsx` — cascada: código exacto con `repuestosApi.porCodigo` agrega y el foco
    vuelve; si no, `porTexto` en lista con flechas y Enter. Blur pasivo y Enter explícito (skill
    `frontend`).
  - `RenglonVenta.jsx` — cantidad, precio, stock y quitar.
  - `ModalCobro.jsx` — efectivo por defecto y botones rápidos; transferencia; mixto; Enter cobra.
  - `ModalDescuento.jsx` — pesos o %, motivos frecuentes y texto.
- `utils/venta.js`, puro y con `venta.test.js`:
  - `agregarRenglon` (suma cantidad si ya está), `quitar`, `cambiarCantidad` (tope de stock);
  - `totales` (subtotal, descuento desde % con HALF_UP, total), igual a `Descuento` del backend;
  - `billetesSugeridos(total)`, `cambio`, `problemasDelCobro` (pagos, recibido);
  - `comandoDeCobro(borrador)`;
  - `aviso de pérdida` (RF-017), solo con costos conocidos;
  - `serializarBorrador` / `restaurarBorrador` (con la llave) y `revalidarBorrador` contra precio y
    stock actuales.
- `ventasApi` y el borrador en `localStorage` con la clave `rdmotors:venta-en-curso`.
- Errores:
  - 409 de stock: marca los renglones;
  - 409 de precio: actualiza y avisa antes de cobrar;
  - red: aviso ámbar, el borrador y la llave quedan, y *Reintentar* usa la misma llave.
- Tras cobrar: borrador nuevo con llave nueva y foco en el buscador.

**Checkpoint:**
- Con solo teclado se venden dos repuestos: cambio de $12.000 sobre $50.000, y un cobro mixto.
- Cerrar la pestaña con tres repuestos y reabrir ofrece retomar.

**Cómo quedó (2026-09-14):**

- Frontend 93 pruebas (20 nuevas en `venta.test.js`, con la misma tabla de redondeo que
  `DescuentoTest`). Lint y build limpios.
- Verificado en Edge contra la base de QA, en claro y oscuro, a 1360, 700 y 390 px:
  - por teclado: código exacto + Enter, "filtro" + flechas + Enter, F4 con "Cliente frecuente",
    F9 con el botón de $100.000 → "Venta N.º 2 cobrada · Cambio $80.000";
  - tres repuestos, salir de la página y volver: "Hay una venta sin terminar con 3 repuestos
    ($68.000)", Retomar, y cobro mixto por teclado (efectivo $20.000 con $50.000 recibidos,
    $48.000 por transferencia) → la N.º 10 quedó así en el servidor, con $30.000 de cambio;
  - **respuesta perdida**: se dejó llegar el cobro al servidor (201) y se cortó la respuesta. La
    venta quedó bloqueada y marcada `enviada`. Reintentar, en la misma pestaña y también tras
    reabrirla, devolvió la misma venta: las ventas y el stock subieron y bajaron una sola vez;
  - con un 422 simulado el cursor vuelve a "Con cuánto paga"; sin red queda en "Reintentar cobro"
    y Enter reintenta. Esa prueba no creó ventas.
- Encontrado en el navegador y corregido:
  - **tras cobrar, el cursor quedaba en la nada** y lo que el cajero escribía se perdía (bitácora:
    `Modal` y pedido de foco);
  - a 390 px el total del renglón quedaba cortado: la grilla angosta lo metía en una columna de
    32 px;
  - en transferencia se mostraba "Cambio $0".
- Las ventas N.º 2 a 10 de la base de QA son de estas pruebas.

---

## Fase 4 · Comprobante, datos de la tienda y ventas del turno — ✅ COMPLETA (falta probar con la ticketera real)

**Cubre:** H4, H7, H10; RF-018 a RF-024, RF-033

| Pieza | Dónde cae |
|---|---|
| `DatosTienda`, `RepositorioDatosTienda`, `ActualizarDatosTienda` (nombre obligatorio, largos) | `compartido/dominio/`, `…/puerto/`, `compartido/aplicacion/` |
| `V10__datos_de_la_tienda.sql` — una fila, sembrada con "RD MOTORS" provisional | migración |
| `TiendaController`: `GET` y `PUT /api/tienda` | `compartido/infraestructura/` |

**Frontend:**
- `utils/ticket.js` + `ticket.test.js`:
  - arma las filas del ticket desde `DetalleVenta` y `DatosTienda`;
  - `cuadra(ticket)`: los renglones suman el total;
  - marca ANULADA;
  - textos largos que bajan de línea en vez de cortarse.
- `utils/imprimir.js`: iframe oculto + `window.print`, portado del car-wash. La impresión nunca
  lanza hacia afuera.
- `componentes/venta/Ticket.jsx` (HTML 80 mm con `@page` propio) y `ModalTicket.jsx` (ver en
  pantalla, *Reimprimir*).
- Al cobrar, imprime solo; si falla, aviso con *Reimprimir*.
- `paginas/Ventas.jsx` (`/ventas`):
  - ventas del turno con total en efectivo y en transferencia (H10);
  - buscar por número;
  - `paginas/DetalleVenta.jsx` (`/ventas/:id`) con *Reimprimir*.
- `paginas/Tienda.jsx` (`/tienda`): el formulario de datos, enlazado desde la cabecera.
- `docs/INSTALAR_TICKETERA.md` (RF-023), portado de `SETUP-TICKETERA.md`:
  - driver;
  - ticketera como predeterminada;
  - papel de 80 mm;
  - acceso directo de Edge o Chrome con `--kiosk-printing --app=<dirección del POS>`.

**Pruebas:**
- `ActualizarDatosTiendaTest`.
- Integración: `PUT` repetido deja el mismo estado.
- Utilidades del ticket.

**Checkpoint:**
- Al cobrar aparece el ticket de 80 mm (diálogo en desarrollo) con número, renglones, pagos y
  cambio, y cuadra.
- Desde `/ventas` se busca la N.º 1 y se reimprime igual.
- Con el acceso directo en modo kiosco, sale sin diálogo. La verificación con la ticketera real la
  hace el usuario.

**Cómo quedó (2026-09-14):**

- Dominio 218 pruebas (14 nuevas en `ActualizarDatosTiendaTest`), Postgres 48 (2 nuevas: la fila
  única con reemplazo repetido, y los `CHECK` de la tabla). Frontend 124 (31 nuevas en `ticket.test.js`
  y `ventasDelTurno.test.js`). V10 aplicada a la base de QA.
- Rutas: `/vender/ventas` (ventas del turno), `/vender/ventas/:id` (detalle con el comprobante al lado)
  y `/tienda` (botón ⚙ de la cabecera). Guía en `docs/INSTALAR_TICKETERA.md` y el acceso directo en
  `scripts/crear-acceso-directo-pos.bat`.
- Verificado en Edge contra la base de QA, en claro y oscuro, a 1360, 1024, 390 y 360 px:
  - **al cobrar en el mostrador** (N.º 11, 10% de descuento, $50.000 recibidos) salió solo a imprimir
    el documento con encabezado, renglones, subtotal, "Descuento (10 %) −$3.300", total $29.700,
    recibido y cambio $20.300, y el pie legal. El aviso dice "Comprobante enviado a la impresora" y el
    cursor quedó en el buscador;
  - **en una tablet** (táctil emulado) la N.º 12 no se imprimió sola: el aviso manda al computador del
    mostrador y el botón dice Imprimir, no Reimprimir;
  - *Ver comprobante* muestra el mismo ticket; al cerrarlo el cursor vuelve al buscador;
  - las ventas del turno cuadran con la API ($304.700: efectivo $193.700 y transferencia $111.000), y
    las 7 ventas con descuento llevan su etiqueta;
  - buscar "999999" dice que no existe; "N.º 1" abre la venta 1 con su comprobante mixto;
  - en *Datos de la tienda* la muestra cambia mientras se escribe, Guardar sin cambios está apagado,
    y sin nombre se avisa y no deja guardar. Por la API: sin nombre y NIT largo dan 422.
- El ticket impreso a PDF con su `@page` de 80 mm: nada cortado, cifras alineadas a la derecha.
- El acceso directo: una copia del `.bat` apuntando a una carpeta de prueba creó "RD MOTORS POS" con
  Edge, `--kiosk-printing`, perfil propio y la dirección escrita.
- Encontrado en el navegador y corregido:
  - el papel en pantalla tenía barras de desplazamiento (80 mm son 302,36 px y el marco redondea a 302);
  - en el detalle, la tabla de renglones se desplazaba y escondía el total (el mínimo de 860 px de las
    listas);
  - a 390 px el botón ⚙ sacaba el del tema de la pantalla, y a 360 px la cabecera ya se pasaba desde la
    fase 1.
- Rotas a propósito, y fallaron exactamente sus pruebas: sin revisar que los pagos sumen el total;
  imprimir solo también en táctiles; sin escapar `<` en el ticket; asignar el nombre antes de validar
  el NIT.
- Los datos de la tienda en QA quedaron como los siembra V10 (solo "RD MOTORS"). Las ventas N.º 11 y 12
  son de estas pruebas.
- **Falta, del checkpoint:** probar con la ticketera real y el acceso directo en el computador del
  mostrador (lo hace el usuario con la guía).

---

## Fase 5 · Anular una venta — ✅ COMPLETA

**Cubre:** H8; RF-025 a RF-027, RF-030

| Pieza | Dónde cae |
|---|---|
| `Venta.anular(motivo, usuario, turnoDeHoy, cuando)`; `exigirCobrada` | `ventas/dominio/` |
| `MovimientoKardex.porReversionDeVenta` — REVERSION, `movimiento_revertido_id` = salida, mismo costo unitario, motivo | `inventario/dominio/` |
| `AnularVenta`: exige turno abierto → bloquea la venta → bloquea las variantes por id → `reponerPorReversion` + kardex → evento `ANULAR_VENTA` con fotos | `ventas/aplicacion/` |
| `POST /api/ventas/{id}/anulacion` | `VentaController` |
| RF-030: la salida de venta no cuenta si tiene reversión (`not exists` sobre `movimiento_revertido_id`) | `RepositorioKardexJpa.contarSalidasDespuesDe` y `Falsos.KardexEnMemoria` |

**Frontend:**
- *Anular* en `DetalleVenta` con `ModalMotivo` (variante peligro).
- El ticket de una venta anulada sale con la marca ANULADA.

**Pruebas:**
- `AnularVentaTest`:
  - el stock vuelve y el promedio no cambia;
  - reversión enlazada a su salida;
  - evento con motivo;
  - sin motivo, ya anulada, sin turno;
  - queda en qué turno se anuló.
- `InventarioDeCompraTest`: vender y anular ya no bloquea revertir la compra; vender sin anular sí.
- Integración:
  - anular contra Postgres;
  - la siguiente venta tras anular la N.º 2 es la N.º 3;
  - una compra de un repuesto con venta anulada se corrige.

**Checkpoint:**
- Se anula la N.º 2: el stock vuelve, el kardex muestra la reversión y la siguiente es la N.º 3.
- La compra de ese repuesto se puede corregir.

**Cómo quedó (2026-09-14):**

- Dominio 232 pruebas (nuevas: `AnularVentaTest`, dos de `VentaTest` y tres de RF-030 en
  `InventarioDeCompraTest`), Postgres 53 (5 nuevas). Frontend 124. V11 aplicada a la base de QA.
- `POST /api/ventas/{id}/anulacion` y el botón **Anular venta** en el detalle, con `ModalMotivo`, que ahora
  recibe un ejemplo de motivo propio de cada acción.
- Verificado en Edge contra la base de QA:
  - se cobró la N.º 13 (2 × ABC23, stock 11 → 9) y se anuló desde el detalle. Sin motivo se pidió el
    motivo; con motivo el stock volvió a 11, el promedio siguió en $14.000, el kardex muestra la
    *Reversión* +2 con el motivo y el mismo costo de la salida, y el comprobante sale marcado ANULADA;
  - anularla otra vez por la API respondió 422 "Esta venta ya fue anulada" y el stock no se movió;
  - la siguiente venta fue la N.º 14. Anulada en otra pestaña, la pantalla dijo "Esta venta ya estaba
    anulada" y la mostró como quedó;
  - en las ventas del turno, las anuladas salen tachadas y con su etiqueta, y los totales no las cuentan.
- Contra Postgres: anular de punta a punta; dos anulaciones a la vez dejan una sola reversión; la compra
  de un repuesto vendido no se anula y, anulada la venta, sí; con una compra en el medio sigue
  bloqueada; y la base rechaza una segunda reversión del mismo movimiento.
- Rotas a propósito, y fallaron exactamente sus pruebas: bloquear los repuestos sin ordenarlos; la reversión al promedio de hoy en vez del costo de la salida; dejar anular dos veces; RF-030 sin mirar el promedio (en el doble y en la consulta contra Postgres); y RF-030 sin la excepción de la venta anulada.
- Las ventas N.º 13 y 14 de la base de QA son de estas pruebas, y están anuladas.

---

## Riesgos

| Riesgo | Fase | Qué hacer |
|---|---|---|
| Traba entre cobros concurrentes | 2 | Orden fijo por id, y prueba A,B / B,A contra Postgres |
| Huecos en la numeración | 2 | Contador en fila; prueba con un cobro fallido en el medio |
| Doble cobro por la misma llave llegando a la vez | 2 | Índice único + traducción; prueba con dos hilos |
| El nombre automático del `CHECK` de acciones no es el esperado | 2 | Falla ruidosa; `MigracionesIntegracionTest` |
| El ticket se ve bien en pantalla y sale cortado en papel | 4 | Estilos de impresión propios; el usuario prueba con el papel real antes de cerrar la fase |
| `Vender.jsx` crece demasiado | 3 | Lógica en `utils/venta.js` con pruebas; componentes por pieza |
| `localStorage` bloqueado | 3 | La venta sigue; avisa que no hay borrador |
| El precio del frontend y del backend divergen en el descuento por % | 2, 3 | La misma tabla de casos en `DescuentoTest` y `venta.test.js` |

---

## Verificación final

1. Los criterios del §8 del spec, uno por uno, desde el navegador.
2. Backend abajo y `./mvnw -q clean test`: dominio, Postgres y migraciones.
3. `cd frontend && npx eslint src/ && node --test src/utils/*.test.js && npx vite build`.
4. Capturas con Edge, en claro, oscuro y angosto:
   - vender y cobrar;
   - descuento;
   - ticket;
   - ventas del turno;
   - detalle anulado.
5. `http/03-ventas.http` al día.
6. Romper a propósito, y ver fallar su prueba, cada regla nueva:
   - stock;
   - precio visto;
   - contador sin huecos;
   - llave;
   - orden de bloqueo;
   - atomicidad;
   - RF-030.

---

## Bitácora de decisiones

Se llena **durante** la implementación, con fecha, fase, decisión y porqué.

| Fecha | Fase | Decisión | Por qué |
|---|---|---|---|
| 2026-09-14 | 1 | **`GET /api/turnos/abierto` responde 204 cuando no hay turno**, no 404 como decía el plan | No tener turno es un estado normal de la tienda (antes de abrir, entre turnos), no un error. El cliente HTTP ya convierte una respuesta vacía en `null`, y la pantalla no tiene que tratar un error como si fuera un dato |
| 2026-09-14 | 1 | **El adaptador guarda con `saveAndFlush` y traduce el choque con `ux_turno_abierto`** | Con `save`, el insert llegaría a la base en el commit, fuera del adaptador, como un error genérico. Así dos aperturas simultáneas reciben el mismo `TurnoYaAbiertoException` que la revisión del caso de uso. La excepción de ese caso no trae desde cuándo: la transacción ya está abortada y no puede leer el turno que ganó |
| 2026-09-14 | 1 | **Pruebas de caja y ventas en una clase de integración aparte** (`CajaYVentasIntegracionTest`), con su propio contenedor | Solo puede haber un turno abierto en toda la base. Compartir contenedor con compras haría que el orden de ejecución decidiera qué prueba encuentra un turno abierto. Cada prueba de turno arranca cerrando por SQL los que queden (preparación, no una forma de cerrar) |
| 2026-09-14 | 1 | **Vender es la pantalla de inicio** (`/` y rutas desconocidas llevan a `/vender`) | Es lo que se usa todo el día en el mostrador. Compras e Inventario quedan a un clic |
| 2026-09-14 | 1 | **Anotado para la fase 3: la llave de la venta no puede usar `crypto.randomUUID()` a secas** | Solo existe en contexto seguro (https o localhost). La tablet entra por `http://192.168.x.x` y ahí es `undefined` (ya documentado en `utils/formato.js`, `idLocal`). La llave necesita un UUID v4 generado sin depender de eso, porque el backend la guarda como `uuid` |
| 2026-09-14 | 2 | **Una sola excepción para los renglones, `RenglonesConProblemaException`, con el tipo de cada problema**, en vez de dos (precios y stock) | Un renglón puede tener precio cambiado y otro estar sin stock en la misma venta, y también hay inactivos, sin precio o inexistentes. Una sola respuesta 409 con la lista deja que la pantalla marque todo de una vez. Si fueran dos excepciones, el cajero corregiría el precio y al reintentar le saldría el stock |
| 2026-09-14 | 2 | **La llave se mira dos veces: antes y después de bloquear los repuestos** | Lo encontró el diseño de la prueba de la llave repetida: dos cobros con la misma llave por la última unidad. El segundo espera los bloqueos del primero y, sin la segunda mirada, respondía "ya no quedan unidades" a un doble clic de una venta que sí se cobró. Roto a propósito, la prueba falla |
| 2026-09-14 | 2 | **El renglón no tiene llave foránea a su movimiento de salida** | Igual que `linea_compra.movimiento_entrada_id`: el enlace es un UUID sin relación JPA, y el orden de los inserts lo decide Hibernate. Con llave foránea, un reordenamiento rompería el guardado |
| 2026-09-14 | 2 | **`Dinero.enPesos()` para los mensajes** ("$38.000") | Los mensajes del dominio van tal cual a la pantalla. "$38000" se lee mal; `toString` queda para depurar |
| 2026-09-14 | 2 | **`FormaPago.entraAlCajon()`** al moverla a compartido | Es la regla del arqueo (solo el efectivo suma al esperado), y queda escrita junto a la forma de pago para cuando llegue la rebanada 3 |
| 2026-09-14 | 2 | **La atomicidad se prueba atrasando el contador** para que el número choque con uno existente al guardar | Las demás reglas se validan antes de mover stock, así que ninguna falla "a mitad". El choque de `ux_venta_numero` pasa en el último paso, con el stock y el kardex ya movidos, y la prueba verifica que no queda nada: ni stock, ni kardex, ni el número |
| 2026-09-14 | 2 | **`GET /api/ventas` exige `turno=abierto`** | Es la única consulta de lista que existe hoy. Pedir otra cosa responde 400, en vez de devolver algo que parezca un historial completo |
| 2026-09-14 | 3 | **La venta se marca `enviada` antes de mandarla, y sin respuesta queda bloqueada**: buscador, cantidades y descuento deshabilitados, y el aviso de venta pendiente ofrece Retomar pero no Descartar | Si la red se cae en el medio no se sabe si el servidor la cobró. Editarla y volver a mandarla cobraría otra cosa con la misma llave; descartarla y armarla de nuevo la cobraría dos veces. Solo reintentar lo mismo es seguro: si ya estaba, el servidor devuelve esa venta |
| 2026-09-14 | 3 | **El buscador usa el listado de inventario mientras se escribe, y el código exacto al dar Enter**, en vez de `porTexto` | El listado ya trae stock y precio, que es justo lo que el cajero mira para elegir, y ya ignora tildes. El código exacto va primero al Enter para que un código que también aparece dentro de otros nombres agregue el correcto sin elegir |
| 2026-09-14 | 3 | **Billetes sugeridos: exacto y el redondeo hacia arriba a $10.000, $50.000 y $100.000**, hasta 4 (RF-031 decía $20.000) | Con múltiplos de $20.000, sobre $41.000 salía $60.000 antes que $50.000, un pago que casi nadie hace. El redondeo a $10.000 cubre el caso del billete de $20.000 cuando cuadra ($40.000 sobre $38.000) |
| 2026-09-14 | 3 | **`Modal` lee `onCerrar` de un ref y su efecto depende solo de `abierto`** | Lo encontró la prueba en el navegador: tras cobrar, el cursor quedaba en la página y lo tecleado se perdía. Al pasar a "cobrando" el padre crea otro `onCerrar`, el efecto se rehacía con el buscador deshabilitado, anotaba "nada" como foco previo y al cerrar lo devolvía ahí. Arreglarlo en `Modal` lo arregla para todos los modales, no solo el de cobro |
| 2026-09-14 | 3 | **Volver al buscador es un pedido que cumple un efecto** (`pedidoDeFoco`), no un `setTimeout` | Al retomar, el modal se abre desde "Retomar", un botón que ya no existe cuando se cierra; y un `setTimeout` puede correr antes de que React vuelva a habilitar el buscador. El efecto corre después de pintar y después de que el modal devuelva su foco, así que el cursor siempre termina en el buscador |
| 2026-09-14 | 3 | **`ModalCobro` devuelve el foco cuando llega la respuesta**: al campo si hay que corregir, a "Reintentar cobro" si no hubo respuesta. `Boton` pasa a `forwardRef` para poder enfocarlo | Mientras se manda, todo está deshabilitado y el navegador suelta el cursor. Sin devolverlo, el cajero tendría que tomar el mouse justo en el momento de más tensión |
| 2026-09-14 | 3 | **Los problemas del 409 se aplican a los renglones y el modal se cierra**: con precio cambiado el renglón toma el precio nuevo y lo avisa; los demás marcan el renglón en rojo | Decisión 4: nunca se cobra un precio distinto al visto. El precio nuevo tiene que verse en la venta antes de volver a cobrar, y con el modal abierto quedaría tapado |
| 2026-09-14 | 3 | **Esc no cancela la venta**: borra lo escrito en el buscador y cierra modales. "Cancelar venta" es un botón | El plan listaba "Esc cancelar". Una tecla suelta vaciaría un carrito de diez renglones con el cliente enfrente |
| 2026-09-14 | 3 | **En angosto el renglón sigue mostrando el código**, y en transferencia no se muestra el cambio | El código es lo que el cajero compara con la caja que tiene en la mano. En transferencia no hay vuelto que contar: "Cambio $0" solo era ruido |
| 2026-09-14 | 4 | **Un solo HTML para imprimir y para ver en pantalla**: `htmlDelTicket` va a la impresora y, dentro de un iframe con `srcdoc` y `sandbox`, a la pantalla. `Ticket.jsx` es el marco, no otra plantilla | Con dos dibujos (uno en React y otro para el papel) tarde o temprano dicen cosas distintas, y "la copia es igual al original" dejaría de ser cierto. El `sandbox` sin scripts impide que un dato con código corra |
| 2026-09-14 | 4 | **Rutas `/vender/ventas` y `/vender/ventas/:id`**, no `/ventas` como decía el plan | Igual que compras (`/compras/historial`): son del módulo Vender, con sus pestañas, y la pestaña de arriba sigue marcada |
| 2026-09-14 | 4 | **Solo imprime solo el computador del mostrador: mouse y más de 640 px**, la regla del car-wash. En celular o tablet se avisa, y *Imprimir* queda a mano | Decisión 3: la ticketera está en ese computador. En una tablet, abrir el diálogo de impresión en cada venta solo estorbaría |
| 2026-09-14 | 4 | **Imprimir devuelve el foco a donde estaba** | `print()` puede llevar el foco al iframe, y al quitarlo el cursor quedaría en la nada: el mismo problema de la fase 3, esta vez después de cada venta |
| 2026-09-14 | 4 | **Un comprobante que no cuadra no sale solo**: se avisa en rojo y se ve en *Ver comprobante* | RF-019. La base ya exige que las partes sumen, así que sería un error de programa; entregarle al cliente un papel cuyas cifras no dan es peor que no entregarlo |
| 2026-09-14 | 4 | **Sin descuento, el ticket no imprime "Subtotal"** | RF-018 lista subtotal, descuento si lo hubo y total. Sin descuento el subtotal es la misma cifra que el total, y repetida se lee como si faltara algo |
| 2026-09-14 | 4 | **"Atendió" no sale en el ticket hasta el spec 0004** | Hoy no hay un nombre que poner. "Usuario provisional" en un papel para el cliente no dice nada. `armarTicket` ya recibe `atendio` y la línea aparece cuando llegue |
| 2026-09-14 | 4 | **Los datos de la tienda se toman al imprimir, no se copian en cada venta** | Sin valor fiscal, reimprimir una venta vieja con el teléfono de hoy no engaña a nadie, y copiarlos en cada venta es una columna por dato. Las cifras sí son las guardadas en la venta. Si llega la factura electrónica, esto se revisa |
| 2026-09-14 | 4 | **Un dato opcional vacío se guarda como NULL**, y la base lo exige con `CHECK (btrim(...) <> '')` | Así el ticket no imprime una línea "NIT" sin número, y el mismo "no se ha dado" no tiene dos formas en la base |
| 2026-09-14 | 4 | **Hora de Colombia fija (`America/Bogota`) en el ticket y en la lista del turno**, no la del equipo | Spec §7. Un computador con la zona mal puesta imprimiría otra hora en un papel que se entrega. También deja las pruebas iguales en cualquier máquina |
| 2026-09-14 | 4 | **En efectivo, el total del turno suma el monto del pago, no lo recibido**; las anuladas no suman | Si pagó $38.000 con $50.000, al cajón entraron $38.000: los $12.000 salieron de vuelta. Es la cifra que usará el arqueo |
| 2026-09-14 | 4 | **La etiqueta "Descuento" va en la lista, con el monto y el motivo al pasar encima** | La nota del cliente en H6: cada venta con descuento se ve sin abrirla |
| 2026-09-14 | 4 | **Datos de la tienda desde un botón ⚙ de la cabecera, oculto en teléfonos** | Se tocan pocas veces: no merecen una pestaña al lado de Vender. En el teléfono no cabía y empujaba el botón del tema fuera de la pantalla; la página sigue en `/tienda` |
| 2026-09-14 | 5 | **RF-030 con una condición más: la venta anulada deja de bloquear solo si el costo promedio es el mismo al salir y al volver**, no con solo tener su reversión como decía el plan | Lo encontró el diseño de las pruebas. Si entre la venta y su anulación entra otra compra del repuesto, el promedio se recalcula sin esas unidades, y al volver entran al promedio nuevo (la reversión no lo toca, RF-025). Revertir la compra anterior daría un costo equivocado: en el ejemplo de la prueba, $2.176 en vez de $2.000. Con el mismo promedio, que es el caso normal de anular el mismo día, la venta y su reversión se cancelan al peso |
| 2026-09-14 | 5 | **La reversión lleva el costo con que salió la venta, no el promedio de hoy**, y no toca el promedio | Así, en cualquier reporte de margen, la venta y su anulación se cancelan al peso. Aceptado: si una compra movió el promedio antes de anular, el valor del inventario se desvía en (unidades × diferencia de promedios). Es raro, es chico, y el ajuste de inventario de la rebanada 3 lo corrige |
| 2026-09-14 | 5 | **V11: índice único sobre `movimiento_revertido_id`**; el plan decía que la fase 5 no llevaba migración | Un movimiento se revierte una sola vez: anular dos veces devolvería el stock dos veces. El caso de uso ya lo impide bloqueando la venta; la base lo exige también. Y la pregunta de RF-030 ("¿esta salida tiene su reversión?") pasa a ser una búsqueda por índice |
| 2026-09-14 | 5 | **Anular exige turno abierto, con su propio mensaje** ("Para anular, ábrelo en Vender: lo que se le devuelve al cliente sale del turno de hoy") | El de cobrar decía "Ábrelo para vender", que no se entiende cuando lo que se intenta es anular. `SinTurnoAbiertoException` gana un constructor con mensaje |
| 2026-09-14 | 5 | **Anular sin llave: repetirlo responde 422 "ya fue anulada"**, y la pantalla lo muestra como "ya estaba anulada" y recarga | Como decidió el spec (§7): una anulada no se anula dos veces. Si un reintento tras un corte llega después de la primera, el cajero ve el estado real en vez de un error |
| 2026-09-14 | 5 | **El modal de anular dice cuánto devolverle al cliente y cómo pagó** | Es lo siguiente que hace el cajero con el cliente enfrente. Y recuerda que si había que cobrarla distinto, después se vende de nuevo (RF-027) |
| 2026-09-14 | 4 | **El `.bat` pide la dirección y la pasa a PowerShell por variable de entorno** | La dirección de la tienda no se conoce hoy. Pasarla por variable evita pelear con las comillas entre `cmd` y PowerShell, y el perfil propio (`--user-data-dir`) hace que el modo kiosco aplique aunque haya otras ventanas de Edge abiertas |
| 2026-09-19 | — | **RF-028 revisado, implementado: la venta guardada se retoma sola** (`ventaAlVolver` en `utils/venta.js`). Desaparece el aviso de Retomar o Descartar; al abrir Vender la venta está armada, con una nota que no bloquea, y *Cancelar venta* la descarta. Reemplaza la decisión del 2026-09-14 sobre el aviso de venta pendiente | El aviso bloqueaba el buscador y el catálogo hasta elegir (pedido del usuario el 2026-09-16) |
| 2026-09-19 | — | **Una venta que se mandó a cobrar sin respuesta vuelve bloqueada y sin revisar precios**: se ve el aviso de siempre con *Reintentar cobro* | Es lo que evita cobrar dos veces: solo se reintenta lo mismo, con la misma llave |
| 2026-09-19 | — | **RF-029 pone al día precio y stock por repuesto, no por posición** (`refrescarRenglones`) | Antes se revisaba al pulsar Retomar; ahora se revisa al abrir, y el cajero puede agregar o quitar algo mientras llegan las fichas. Si la venta se cobra en ese lapso, no se toca |
