# Plan 0008 — Fiado a clientes y cartera

**Traduce:** `docs/specs/0008-fiado-a-clientes/spec.md` (versión 2), que el usuario mandó implementar el 2026-09-21
con las recomendaciones del spec:

| Decisión | Resolución |
|---|---|
| 1. Cómo se lleva la deuda | **A**: por venta, como el car‑wash, con **abono libre** que se aplica a lo más viejo o a la venta que se escoja |
| 2. Datos para fiar | **Nombre, cédula o NIT y celular**, obligatorios. Dirección y nota, opcionales |
| 3. Fiar una parte | **Sí** |
| 4. ¿El fiado es forma de pago? | **No**: es su propia parte de la venta |
| 5. Quién puede qué, sin cupo | La tabla de la decisión 5: el cajero fía, crea clientes, completa datos y recibe abonos; corregir datos, cerrar el fiado, el saldo del cuaderno y anular abonos son del administrador |
| 6. Venta fiada anulada con abonos | Lo abonado pasa a las otras ventas pendientes; si no hay, queda **a favor**. Sigue abierta la aclaración de si se devuelve en efectivo: **se implementa que queda para la próxima compra** (se aplica solo al próximo fiado) y el sistema no saca plata del cajón por eso |

**Estado:** 5 fases · en implementación desde el 2026-09-21 · fase 1 hecha

---

## Contexto

Hoy una venta solo se cobra si los pagos suman el total (`Venta.java:194-217`), no existe el cliente, el arqueo
suma por forma de pago (`ArqueoDeTurno.java:53-100`) y el reporte comprueba `efectivo + transferencia = ventas
netas` en el servidor (`ResultadosDelPeriodo.java:22-27`) **y en la pantalla** (`frontend/src/utils/resultados.js:83-85`).
El detalle está en el §3 del spec.

Lo que se reusa:

| Pieza | Dónde |
|---|---|
| Llave contra el doble clic, con la segunda mirada después de bloquear | `ventas/aplicacion/CobrarVenta.java:82-97` · `caja/aplicacion/RegistrarRetiro.java:40-59` |
| Contador sin huecos para numerar (fila `consecutivo`) | `pos/…/db/migration/V9__venta.sql:11-18` · `RepositorioVentasJpa.java:40-48` |
| Anular solo mientras el turno siga abierto, con su mensaje | `caja/aplicacion/AnularGasto.java:33,53-60` |
| Auditoría con antes, después y motivo | `compartido/dominio/EventoAuditoria.java` |
| Buscar sin tildes ni mayúsculas, igual en Java y en Postgres | `compartido/dominio/TextoDeBusqueda.java` y la función `sin_tildes` |
| Nombres de quién, en una sola consulta | `RepositorioUsuarios.nombresDe` · `compartido/dominio/Persona.java` |
| El comprobante de 80 mm y la impresión que nunca lanza | `frontend/src/utils/ticket.js` · `papel80mm.js` · `imprimir.js` |
| La cartera del car‑wash: lista, tarjeta por cliente, detalle por orden con abonos debajo, historial completo, filtros | `CAR-WASH-SYSTEM/frontend/src/pages/app/CarteraPage.jsx` · `components/credit/CreditHistoryModal.jsx` (se porta la pantalla, no el código: allá el cobro es por orden completa, §3 del spec) |

---

## Resumen

| Fase | Qué | Migración | Se ve en |
|---|---|---|---|
| 1 | Fiar: el cliente con sus datos obligatorios, fiar todo o una parte al cobrar, el comprobante, anular una venta fiada, y el reporte cuadrando con lo fiado | V20 | Cobrar, comprobante, ventas del turno, *Resultados* |
| 2 | La Cartera: quién debe, desde cuándo, la ficha con cada venta fiada y su estado, el historial completo, corregir datos y cerrar el fiado | — | *Cartera* (módulo nuevo) |
| 3 | Abonos: abono libre aplicado a lo más viejo o a una venta, el efectivo al cajón y al cierre, el comprobante del abono, anular un abono | V21 | *Cartera*, *Caja*, cierre |
| 4 | Lo de antes y lo que se deshace: el saldo del cuaderno, anular una venta fiada que ya tenía abonos, el saldo a favor, las compras del cliente y la venta de contado a su nombre | — | Ficha del cliente, cobrar |
| 5 | Reportes y extras: cobrado en abonos y por cobrar en *Resultados*, filtrar la cartera por fechas, exportar a PDF, deuda vieja | — | *Resultados*, *Cartera* |

**El sistema es usable al final de cada fase.** Desde la fase 1 se puede fiar y la deuda queda guardada, aunque
se vea recién en la fase 2 y se abone en la 3.

---

## Decisiones tomadas al planear

1. **Módulo `clientes` propio** (`clientes/dominio`, `clientes/dominio/puerto`, `clientes/aplicacion`,
   `pos/…/clientes/infraestructura`). La venta sigue en `ventas`: guarda a quién y cuánto quedó fiado, y la deuda
   la lleva `clientes`.
2. **Tres agregados, tres puertos.** `Cliente` (sus datos), `Deuda` (una por venta fiada o por el saldo del
   cuaderno) y `Abono` (con sus *aplicaciones*: cuánto de ese abono fue a cada deuda). Puertos
   `RepositorioClientes`, `RepositorioDeudas`, `RepositorioAbonos`. Su segunda implementación son los falsos en
   memoria de las pruebas del dominio, y la nube de la rebanada 5.
3. **Las reglas de plata viven en `CarteraDelCliente`** (`clientes/dominio/`, no es entidad): se arma con las
   deudas y los abonos de un cliente y sabe cuánto debe, cuánto tiene a favor, desde cuándo debe, cómo se reparte
   un abono (a lo más viejo, o primero a la venta escogida), qué pasa al anular una deuda o un abono. Se prueba
   sin base de datos.
4. **Un candado por cliente.** Todo lo que cambia lo que debe un cliente (fiar, abonar, anular) toma primero la
   fila del cliente (`buscarParaModificar`). **Orden de bloqueo:** turno → venta → repuestos (por id) → cliente.
   El cliente siempre al final, así ningún camino se traba con otro (la lección del car‑wash,
   `CreditServiceImpl.java:114-122`).
5. **La deuda guarda `abonado`** además de las aplicaciones: la lista y la ficha se leen sin sumar aplicaciones.
   La regla `abonado = Σ aplicaciones vigentes` la mantiene `CarteraDelCliente` y la prueba la integración.
6. **Las aplicaciones no se borran: se anulan.** Anular un abono o una venta fiada marca sus aplicaciones; si lo
   liberado vuelve a aplicarse a otra venta, es una aplicación nueva. Así la ficha puede decir *"lo abonado a la
   N.º 41 pasó a la N.º 57"*.
7. **El saldo a favor no es una tabla**: es la parte de los abonos vigentes que no quedó aplicada a ninguna
   deuda. Cuando se fía algo nuevo, se aplica sola a esa deuda (decisión 6).
8. **Fotos para el comprobante.** La deuda y el abono guardan `debe_despues`: cuánto debía el cliente en total
   justo después. Al reimprimir, el comprobante dice lo que decía, no lo de hoy.
9. **Los abonos se numeran** con la misma fila contadora de las ventas (`consecutivo`, fila `ABONO`): *Recibo de
   abono N.º 7*, sin huecos.
10. **La venta cambia poco:** `cliente_id` (opcional) y `fiado` (≥ 0). Regla del dominio: `Σ pagos + fiado =
    total`; si hay fiado, hay cliente. El comando de cobro gana `clienteId` y `fiado` con un constructor que
    mantiene la forma de hoy, para que el cobro de contado no cambie.
11. **Cobrar llama a `clientes` por su caso de uso, no por su repositorio**: `FiarVenta` (en
    `clientes/aplicacion/`, **sin transacción propia**, como `CalcularArqueo`) bloquea y revisa al cliente, y
    registra o anula la deuda dentro de la transacción del cobro o de la anulación.
12. **Documento y celular se normalizan para comparar**: el documento sin puntos, guiones ni espacios
    (`1.234.567-8` = `12345678`), y el celular solo con dígitos. El documento normalizado es único en la base.
13. **Datos de los clientes, por rol, en el caso de uso**: el cajero crea y **completa** lo que falta; si intenta
    cambiar un dato ya escrito, `NoPermitidoException`. El administrador **corrige** y queda auditado.
14. **El reporte aprende lo fiado en la fase 1**, no en la 5: desde la primera venta fiada la pantalla de
    *Resultados* comprueba `efectivo + transferencia = ventas netas` y diría que no cuadra.
15. **Auditoría nueva**, de una vez en la V20: `CORREGIR_CLIENTE`, `CERRAR_FIADO`, `ABRIR_FIADO`,
    `CARGAR_SALDO_CUADERNO`, `ANULAR_ABONO`.

---

## Fase 1 · Fiar (P1: H1, H2; RF-001 a RF-010; RF-027 sin abonos; RF-024 en lo fiado)

| Pieza | Dónde cae |
|---|---|
| `Cliente` (entidad): datos, normalizados, fiado cerrado; `nuevo`, `faltaParaFiar`, `exigirQueSePuedaFiar`, `completar`, `corregir`, `fotografia` | `clientes/dominio/` |
| `DatosCliente` (nombre, documento, celular, dirección, nota), `ClienteRepetidoException` | `clientes/dominio/` |
| `Deuda` (entidad): `porVenta`, `pendiente`, `estado`, `anular`; `OrigenDeuda`, `EstadoDeuda` | `clientes/dominio/` |
| `CarteraDelCliente`: `debe`, `desdeCuando`, `registrarDeuda`, `anularDeuda` (sin abonos todavía) | `clientes/dominio/` |
| `RepositorioClientes`, `RepositorioDeudas` | `clientes/dominio/puerto/` |
| `CrearCliente`, `ActualizarCliente`, `BuscarClientes`, `FiarVenta` | `clientes/aplicacion/` |
| `Venta`: `clienteId`, `fiado`, la regla `pagos + fiado = total` | `ventas/dominio/` |
| `ComandoCobrarVenta` (+`clienteId`, `fiado`), `CobrarVenta`, `AnularVenta`, `DetalleVenta` (+cliente, fiado, debe después), `ConsultarVentas` | `ventas/aplicacion/` |
| `VentaCobrada` (+fiado) y `Cifras` (+fiado); `efectivo + transferencia + fiado = ventas netas` | `reportes/dominio/` |
| `RepositorioClientesJpa`, `RepositorioDeudasJpa`, `ClienteController` (`GET /api/clientes?q=`, `POST`, `PUT /{id}`) | `pos/…/clientes/infraestructura/` |
| `VentaController` (cliente y fiado en la petición y la respuesta), `RepositorioReportesJdbc` (fiado), `ReporteController`, `ManejadorDeErrores` (409 `CLIENTE_REPETIDO` con el cliente que ya existe) | `pos/…` |
| **V20**: `cliente`, `venta.cliente_id` y `venta.fiado`, `deuda`, `abono`, `aplicacion_abono`, fila `ABONO` del consecutivo, la auditoría con las 5 acciones | `pos/…/db/migration/` |

**Frontend:** `api/cliente.js` (`clientesApi`); `utils/clientes.js` (+pruebas): qué falta para fiar, normalizar,
textos; `componentes/clientes/SelectorCliente.jsx` (buscar por nombre, cédula o celular; crear uno nuevo;
completar lo que falta); `ModalCobro` con la forma **Fiado** (quién, cuánto paga ahora y cómo, cuánto queda fiado
y cuánto quedará debiendo); `utils/venta.js` (pagos y fiado del cobro, el comando); `utils/ticket.js` (*Fiado a*,
el monto fiado y *Debe en total*; los pagos más lo fiado suman el total); el aviso de *Venta cobrada* en Vender;
lo fiado en *Ventas del turno*, en el detalle de la venta y en el turno; `utils/resultados.js` y *Resultados* con
lo fiado.

**Pruebas:** `ClienteTest` (datos, normalizar, qué falta, completar contra corregir), `VentaTest` (pagos + fiado,
fiado sin cliente, fiado mayor que el total), `FiarTest` (cobrar fiado: deuda, cajón que no lo espera, datos que
faltan, fiado cerrado, llave repetida no fía dos veces; anular devuelve el stock y anula la deuda; el cajero no
corrige datos), `ResultadosDelPeriodoTest` (la igualdad con fiado); integración `ClientesYFiadoIntegracionTest`
(ida y vuelta, cédula repetida con puntos, cobrar fiado de punta a punta, dos fiados a la vez al mismo cliente) y
`MigracionesIntegracionTest` (V20 sobre una base con ventas: quedan con `fiado = 0` y sin cliente).
**Romper a propósito:** fiar sin exigir el celular; que el fiado cuente en el cajón; no anular la deuda al anular
la venta; quitar el fiado de la igualdad del reporte.
**Checkpoint:** fiar $50.000 a un cliente nuevo exige su cédula y su celular; baja el stock; el cajón no lo
espera; el comprobante dice *Fiado a* y *Debe en total*; *Resultados* cuadra.

## Fase 2 · La Cartera (P1: H3; P2: H5, H11; RF-018 a RF-021, RF-004, RF-005)

| Pieza | Dónde cae |
|---|---|
| `ResumenDeCliente`, `FiltroCartera` (vista *Deben* o *Historial*, texto) | `clientes/dominio/` |
| `RepositorioClientes.resumen(filtro)` (una consulta con lo que debe cada uno, cuántas pendientes, desde cuándo, lo fiado y lo pagado en total, el último movimiento) | `clientes/dominio/puerto/` + adaptador JDBC |
| `ConsultarCartera` (`lista`, `ficha`), `FichaCliente`; `CerrarFiado`, `AbrirFiado` (administrador, auditados) | `clientes/aplicacion/` |
| `CarteraController` (`GET /api/cartera`), `ClienteController` (`GET /{id}`, `POST /{id}/cierre-del-fiado`, `POST /{id}/apertura-del-fiado`) | `pos/…/clientes/infraestructura/` |

**Frontend:** *Cartera* en el menú para los dos roles (`utils/permisos.js`); `/cartera`: arriba cuántos deben y el
total por cobrar; *Deben* | *Historial completo*; buscar; una tarjeta por cliente con lo que debe, cuántas ventas
pendientes y desde cuándo, o *Al día*; `/cartera/:id`: la ficha con sus datos, lo que debe y desde cuándo, cada
venta fiada con su estado (*Pendiente · Abonada · Pagada · Anulada*), editar datos (el cajero completa, el
administrador corrige), cerrar y abrir el fiado; `utils/cartera.js` (+pruebas): estados, *desde cuándo* en
palabras, orden.
**Pruebas:** `ConsultarCarteraTest` (orden, *Al día*, desde cuándo, historial con los que ya pagaron), `ClienteTest`
(cerrar y abrir), integración del resumen contra Postgres.
**Romper:** que la lista cuente una deuda anulada; que el cajero pueda cerrar el fiado.
**Checkpoint:** la Cartera muestra a Juan con lo que debe y desde cuándo; su ficha muestra la venta fiada
*Pendiente*; el historial completo lo encuentra por su cédula.

## Fase 3 · Abonos (P1: H4; RF-011 a RF-017, RF-019 la línea de tiempo)

| Pieza | Dónde cae |
|---|---|
| `Abono` (entidad) con `AplicacionAbono`; `CarteraDelCliente.abonar` (a lo más viejo o primero a la escogida), `anularAbono`, `aFavor` | `clientes/dominio/` |
| `RepositorioAbonos` (por llave, por cliente, del turno, siguiente número) | `clientes/dominio/puerto/` |
| `RegistrarAbono` (llave, turno bloqueado si es efectivo, cliente bloqueado, llave otra vez), `AnularAbono` (administrador; en efectivo, solo con su turno abierto) | `clientes/aplicacion/` |
| `ArqueoDeTurno` (+abonos en efectivo al esperado; lo fiado y los abonos por transferencia, aparte), `TurnoCaja` (guarda las partes nuevas al cerrar), `CalcularArqueo`, `DetalleTurno` (+abonos del turno), `ConsultarTurnos` | `caja/…` |
| `AbonoController` (`POST /api/abonos`, `GET /{id}`, `POST /{id}/anulacion`), `TurnoController` (partes nuevas) | `pos/…` |
| **V21**: `turno_caja.ventas_fiado`, `abonos_efectivo`, `abonos_transferencia`; los turnos cerrados en $0; las reglas del cierre reescritas: `esperado = fondo + ventas en efectivo + abonos en efectivo − devoluciones − gastos − retiros − compras` | `pos/…/db/migration/` |

**Frontend:** `ModalAbono` en la ficha (monto hasta lo que debe, forma, referencia si es transferencia, nota, *a lo
más viejo* o una venta); el **recibo de abono** de 80 mm (`utils/reciboAbono.js` + pruebas) impreso al registrar;
la línea de tiempo de abonos con quién los recibió; los abonos debajo de cada venta; anular (administrador);
`utils/arqueo.js`, `DesgloseArqueo`, el comprobante del cierre y el detalle del turno con *Abonos de clientes*.
**Pruebas:** `CarteraDelClienteTest` ($60.000 sobre $50.000 y $30.000; dirigido a la más nueva; nunca más de lo
que falta; las partes suman el abono; más que la deuda no), `AbonosTest` (efectivo sin turno no, transferencia
sin turno sí, llave, turno ajeno, anular solo con el turno abierto y del administrador), `ArqueoDeTurnoTest` /
`CalcularArqueoTest` / `CerrarTurnoTest` (el abono en efectivo sube el esperado y queda firmado);
integración: dos abonos a la vez al mismo cliente (nunca debe menos de cero), cierre con abonos, `MigracionesIntegracionTest`
(V21 sobre turnos cerrados: siguen cuadrando).
**Romper:** aplicar a una venta más de lo que le falta; no sumar los abonos al esperado; no tomar el candado del
cliente; aceptar efectivo sin turno.
**Checkpoint:** Juan debe la 41 ($50.000) y la 57 ($30.000), abona $60.000 en efectivo: la 41 *Pagada*, la 57
*Abonada* con $20.000, el esperado del cajón sube $60.000 y el cierre dice *Abonos de clientes*.

## Fase 4 · Lo de antes y lo que se deshace (P2: H6, H9, H10; RF-023, RF-027, RF-028; decisión 6)

| Pieza | Dónde cae |
|---|---|
| `Deuda.delCuaderno`; `CarteraDelCliente.anularDeuda` con abonos (lo liberado a las otras pendientes; el resto a favor); lo a favor aplicado al fiar | `clientes/dominio/` |
| `CargarSaldoDelCuaderno` (administrador, una vez por cliente, auditado) | `clientes/aplicacion/` |
| `RepositorioVentas.delCliente` (sus compras, paginadas); `CobrarVenta` con cliente en una venta de contado | `ventas/…` |
| `ClienteController` (`POST /{id}/saldo-del-cuaderno`, `GET /{id}/ventas`) | `pos/…` |

**Frontend:** *Saldo del cuaderno* en la ficha (administrador; fecha, monto y motivo); la venta anulada en la ficha
(*lo abonado pasó a…*); *A favor* en la ficha, en el cobro y en el comprobante; la pestaña *Compras* del cliente;
*A nombre de un cliente (opcional)* al cobrar de contado.
**Pruebas:** `CarteraDelClienteTest` (anular con abonos, a favor aplicado al fiar, el cuaderno se paga primero),
`SaldoDelCuadernoTest` (una sola vez, fecha no futura, del administrador), integración de la venta de contado a
nombre de un cliente.
**Romper:** perder lo abonado al anular; dejar cargar dos saldos del cuaderno.
**Checkpoint:** se anula la 41 con $30.000 abonados: pasan a la 57; si no hay otra, quedan *a favor* y el próximo
fiado los usa.

## Fase 5 · Reportes y extras (P2: H7, H8; P3: H12, H13; RF-022, RF-024 completo, RF-025, RF-026)

| Pieza | Dónde cae |
|---|---|
| `CarteraDelPeriodo` (cobrado en abonos por forma, por cobrar hoy, cuántos deben) | `reportes/dominio/` |
| `RepositorioReportes.abonos(desde, hasta)` y `porCobrar()` | `reportes/dominio/puerto/` + JDBC |
| `FiltroCartera` con fecha de la venta o del abono y el período | `clientes/…` |

**Frontend:** en *Resultados*, *Fiado y cartera*: vendido fiado, cobrado en abonos, por cobrar hoy; en la Cartera,
*Filtrar por: Fecha de la venta | Fecha del abono* con *Todo, Hoy, Ayer, Esta semana, Este mes, Fecha específica,
Rango*; *Exportar PDF* (la cartera filtrada, en hoja carta, por el diálogo de impresión: *Guardar como PDF*); la
marca de deuda de más de 30 días.
**Pruebas:** `ResultadosDelPeriodoTest` / `ConsultarResultadosTest` (un abono no sube las ventas), `utils/cartera.test.js`
(períodos y marca de 30 días), integración del filtro por fechas.
**Romper:** contar el abono como venta; filtrar por la fecha equivocada.
**Checkpoint:** *Resultados* del mes: `efectivo + transferencia + fiado = ventas netas` y, aparte, lo cobrado en
abonos y lo que queda por cobrar.

---

## Riesgos

| Riesgo | Qué se hace |
|---|---|
| El abono contado como venta | Se lee de tablas distintas; el reporte los muestra en bloques distintos; prueba en la fase 5 |
| Reescribir la regla del cierre sobre turnos cerrados (V21) | Las partes nuevas nacen en $0 para los cerrados; prueba de migración sobre turnos cerrados |
| `abonado` y las aplicaciones se desincronizan | Una sola clase las mueve (`CarteraDelCliente`); la integración compara las dos |
| Un fiado entre pestañas: dos cobros al mismo cliente a la vez | El candado del cliente; prueba de concurrencia en la fase 1 |
| Clientes repetidos por la cédula escrita distinto | Documento normalizado único; crear con una cédula que existe devuelve 409 con ese cliente y la pantalla lo usa |

## Verificación final

1. Backend abajo, `./mvnw clean install` (dominio, integración, migraciones, seguridad).
2. `cd frontend && npx eslint src/ && node --test src/utils/*.test.js && npx vite build`.
3. Capturas con Edge sin interfaz y respuestas simuladas (sin escribir en QA) de cobrar fiado, la Cartera, la
   ficha, el abono, el cierre y *Resultados*, en claro, oscuro y 390 px.
4. Romper a propósito en cada fase.

---

## Bitácora de decisiones

Se llena **durante** la implementación, con fecha, fase, decisión y porqué.

| Fecha | Fase | Decisión | Por qué |
|---|---|---|---|
| 2026-09-21 | 1 | **El dominio de abonos se escribió entero en la fase 1** (`Abono`, `AplicacionAbono`, `CarteraDelCliente` con abonar, anular y lo a favor), con `RepositorioAbonos` y su adaptador; los casos de uso y la pantalla de abonos siguen en la fase 3 | `FiarVenta` arma la cartera con los abonos para aplicar lo que haya a favor, y las reglas de plata se prueban mejor todas juntas: `CarteraDelClienteTest` cubre desde ya el ejemplo del spec ($60.000 sobre la 41 y la 57), el dirigido, anular y el saldo del cuaderno |
| 2026-09-21 | 1 | **`ComandoCobrarVenta` y `VentaCobrada` conservan su forma de antes** con un constructor extra (sin cliente y con $0 fiado) | El cobro de contado no cambia, y las pruebas que ya existían siguen diciendo lo mismo |
| 2026-09-21 | 1 | **`FiarVenta` recibe ids y montos, no la `Venta`** | Así `clientes` no importa `ventas`: la dependencia va en un solo sentido (ventas → clientes, caja → clientes) |
| 2026-09-21 | 1 | **La duplicidad de cédula se revisa antes de cambiar el cliente** | La consulta, con el cliente ya cambiado en memoria, lo mandaba a la base antes de tiempo y el choque salía como error de base en vez de *"Esa cédula es de…"* |
| 2026-09-21 | 1 | **En el cobro, *Fiado* es una cuarta forma**: a quién, *Paga ahora (opcional)* en efectivo o transferencia, y *Queda fiado*. No pide *con cuánto paga* | Cubre *"todo fiado"* y *"paga $20.000 y el resto lo debe"* sin mezclar tres formas en una pantalla con el cliente enfrente. El dominio acepta cualquier combinación |
| 2026-09-21 | 1 | **El cliente escogido viaja en el borrador de la venta** (`venta.cobro.cliente`) | Un cobro que quedó sin respuesta se reintenta con la misma llave y el mismo cliente: no puede fiarse a otro al reintentar |
| 2026-09-21 | 1 | **Tres pantallas que comprobaban `efectivo + transferencia = total` ahora suman lo fiado**: las ventas del turno, *Resultados* y el comprobante | Con la primera venta fiada habrían dicho *"no cuadra"*. Encontrado al planear (`resultados.js:83-85`) y al revisar `ventasDelTurno.js` |
| 2026-09-21 | 1 | Cierre de la fase: 420 pruebas del dominio y 102 contra Postgres con `clean`; 247 de pantalla, lint y build. Romper a propósito: 5 de 5 atrapadas (fiar sin celular, fiado al cajón, no anular la deuda, pagos sin lo fiado, reporte sin lo fiado). V20 aplicada en QA. Capturas del cobro fiado en claro, oscuro y 390 px con respuestas simuladas | — |
| 2026-09-21 | 2 | **La lista de la Cartera se lee por un puerto aparte, `ConsultasDeCartera` (JDBC), y no por `RepositorioClientes`** | Leer la cartera entera es una consulta de reporte —una sola pasada con lo que debe cada uno, cuántas pendientes, desde cuándo, el total fiado y pagado— y armarla cliente por cliente con JPA sería una consulta por fila. El repositorio sigue siendo el de escribir |
| 2026-09-21 | 2 | **La ficha la arma el dominio (`CarteraDelCliente`), la lista la arma SQL**, y una prueba de integración compara las dos | Son dos caminos a la misma cifra: si el SQL se desvía de las reglas, la prueba lo dice en vez de que lo descubra el dueño del almacén |
| 2026-09-21 | 2 | **Las partes de un abono se ordenan por el orden de pago de las deudas, no por cuándo se aplicaron** | Un abono reparte a varias deudas en el mismo instante; ordenar por el instante dejaba el recibo en un orden distinto cada vez |
| 2026-09-21 | 3 | **V21 arranca las columnas nuevas en $0 para los turnos ya cerrados y reescribe los dos `CHECK` del cierre** | Los turnos cerrados antes del fiado tienen que seguir cuadrando: si las columnas nuevas quedaran nulas o sueltas, la regla vieja los volvería inválidos. Probado con `MigracionesIntegracionTest`, que migra hasta V20, cierra un turno a la vieja y luego aplica V21 |
| 2026-09-21 | 3 | **El efectivo de un abono exige turno abierto, y anularlo exige que sea el mismo turno** | Un abono en efectivo entra a un cajón concreto; sin turno no hay a qué cajón entrar, y devolverlo de un turno ya cerrado descuadraría un arqueo firmado. La transferencia no necesita turno |
| 2026-09-21 | 3 | **Los desgloses del cajón toleran que falten las partes nuevas (`?? 0`)** | Un turno cerrado antes de V21 no tiene abonos: sin la tolerancia, la pantalla mostraba `NaN` en vez de $0 |
| 2026-09-21 | 4 | **Lo que queda a favor se aplica solo al registrar una deuda nueva o al anular una** | Es el único momento en que hay algo nuevo a qué aplicarlo; hacerlo en cada lectura habría cambiado cifras sin que nadie registre nada |
| 2026-09-21 | 4 | **El saldo del cuaderno es una deuda como las demás, con origen `CUADERNO`**, una por cliente en el dominio y también con índice único en V20 | Así se paga primero por ser lo más viejo y entra en las mismas reglas de abono; la regla se cuida en los dos lados porque el segundo saldo podría entrar por dos ventanas a la vez |
| 2026-09-21 | 5 | **La cartera del reporte mezcla dos tiempos a propósito: lo cobrado es del período, lo por cobrar es de hoy** | Son dos preguntas distintas del dueño —*cuánto entré esta semana* y *cuánto me deben*— y juntarlas en un solo período daría una cifra que no significa nada. Queda dicho en la tarjeta y en el javadoc |
| 2026-09-21 | 5 | **Exportar la cartera es imprimir en hoja carta desde el diálogo del navegador, sin librería de PDF** | *Guardar como PDF* ya está en el diálogo de impresión; una librería habría pesado más que la pantalla entera. La lista impresa dice qué filtro se estaba viendo, para que el papel no mienta |
| 2026-09-21 | 5 | **El buscador de la Cartera va en su propia línea en el celular** | La captura de 390 px lo mostró estrujado contra el borde: con `flex: 1`, la base 0 le ganaba al `width: 100%` |
| 2026-09-21 | 5 | **Una transferencia se recibe aunque el turno abierto sea de otro cajero**, y entonces no se le cuelga a ese turno; el efectivo sí exige el turno propio | Lo encontró la verificación final: la cajera que no abrió el turno no podía registrar un pago por transferencia, y el error hablaba de cerrar el turno. Una transferencia no toca el cajón |
| 2026-09-21 | — | **Decisión 2 revertida por el dueño al probar el mostrador: fiar ya no exige cédula ni celular.** Solo el nombre es obligatorio; lo demás se pide, se ofrece completar ahí mismo y queda como recordatorio en la ficha. Lo único que impide fiar es el fiado cerrado | Con el cliente enfrente, frenar la venta por un dato que no trae encima es peor que fiar con lo que hay: un conocido del barrio no siempre carga la cédula. Se cambió `Cliente.exigirQueSePuedaFiar`, `faltaParaFiar` pasó a llamarse `datosQueFaltan` (ya no bloquea, informa), y las pruebas que exigían el dato ahora exigen lo contrario |
| 2026-09-21 | 5 | Cierre del spec: **445 pruebas del dominio y 111 contra Postgres** con `clean install` en verde; 273 de pantalla, lint y build. Romper a propósito de las fases 4 y 5: **9 de 9 atrapadas** (dos saldos del cuaderno, el cuaderno del cajero, fecha futura, lo a favor sin aplicar, anular sin repartir, lo cobrado solo en efectivo, el reporte sin cartera, período al revés, media fecha). Capturas de los filtros, la tarjeta *Fiado y cartera* y la cartera en papel, en claro, oscuro y 390 px | — |
