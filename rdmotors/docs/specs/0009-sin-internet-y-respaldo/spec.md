# Spec 0009 — Sin internet: lo que ya funciona, la bandeja hacia la nube y el respaldo

**Estado:** aprobado el 2026-09-21 · en implementación ([plan](plan.md))
**Rebanada:** 4 (operación sin internet) · ver [`PLAN_DE_TRABAJO.md`](../../PLAN_DE_TRABAJO.md)
**Depende de:** todo lo implementado hasta el spec 0004. **Va de la mano con:** la rebanada 5 (panel en la nube)

> **Convención:** lo que dice *"hoy"* está verificado con `archivo:línea`. Lo que dice *"debe"* es
> propuesta y todavía no existe. Las dos cosas nunca se mezclan.

---

## 0. Lo primero: vender sin internet ya funciona

La rebanada 4 se escribió pensando en el car‑wash, que vivía en la nube: si se caía internet, el navegador se
quedaba sin servidor y había que guardar todo en el navegador (`frontend/src/offline/`, 7 archivos y 2.634
líneas). **En RD MOTORS el servidor y la base viven en el computador de la tienda**
(`pos/src/main/resources/application.properties:30`; `docs/INSTALAR_TICKETERA.md:24-26`). Internet no está en
el camino de una venta.

Entonces, hoy, **sin internet se vende, se cobra, se cierra caja y se ven los reportes igual que con internet**.
Lo único que necesita internet todavía no existe: mandarle los datos a la nube para que el dueño los vea desde
su casa.

Lo que este spec deja por hacer es poco y concreto:

1. **La bandeja de salida hacia la nube** (lo que el cliente describió como *"el evento se guarda en una cola
   local y se sincroniza en cuanto vuelve la conexión"*, `SPEC_Sistema_Ventas_Repuestos (3).md:189-190`).
2. **El respaldo automático**, que el cliente pidió (`:169-170` y `:200-201`) y **hoy no existe**: el único
   respaldo de la base es el que se sacó a mano el 2026-09-17 (`respaldos/qa-2026-09-17.sql`).
3. Una llave contra el doble clic en **compras**, la única escritura que suma sin llave.

---

## 1. Objetivo de negocio

- Que **nada** de lo que pase con internet frene el mostrador. (Ya se cumple; este spec lo deja probado.)
- Que el día que exista el panel de la nube, **no se pierda ni se repita nada** de lo que se vendió sin internet.
- Que un disco dañado o un computador robado **no se lleve el negocio**: hoy toda la historia —ventas, deudas,
  inventario, costos— está en un solo disco, sin copia automática.

---

## 2. Caso de uso

### P1 — bloquean

**H1 · Se cae internet y la tienda ni se entera.** *Demo:* con el cable de internet desconectado (el Wi‑Fi de la
tienda encendido), se abre turno, se vende, se anula, se registra un gasto y se cierra. Todo igual.

**H2 · El respaldo se hace solo.** Cada día, sin que nadie se acuerde, queda una copia completa de la base en otro
lugar del computador (y, si se quiere, en una memoria USB), y se guardan las últimas copias. *Demo:* al día
siguiente hay un archivo nuevo de respaldo, y se restaura en una base vacía con el procedimiento escrito.

### P2 — importantes

**H3 · La bandeja de salida.** Cada venta, anulación, cierre de turno, gasto, retiro, compra, fiado y abono deja
un **evento** en la misma operación en que se guarda: si la venta se guarda, su evento existe; si no, tampoco.
Los eventos se numeran en orden y esperan a ser enviados. *Demo:* después de vender sin internet, el
administrador ve *"12 eventos esperando a la nube"*.

**H4 · El envío cuando vuelve internet.** Los eventos salen en orden, reintentando si falla, y se marcan enviados
solo cuando la nube confirma. Repetir un envío no duplica nada allá. *Demo:* se reconecta internet y la bandeja
queda en cero. **Requiere la nube de la rebanada 5**: sin ella no hay a quién mandar.

**H5 · Saber si está al día.** El administrador ve cuándo fue el último envío, cuántos esperan y el último error.

### P3 — deseables

**H6 · Copia del respaldo en la nube**, una vez exista (lo menciona el cliente, `:200-201`).

---

## 3. Qué existe hoy

| Qué | Dónde | Qué significa |
|---|---|---|
| El servidor escucha en la red de la tienda; los demás equipos entran por su IP | `pos/src/main/resources/application.properties:30`; `docs/INSTALAR_TICKETERA.md:24-26` | Internet no está en el camino de una venta |
| La base, en la tienda, va **nativa** como servicio de Windows | `docs/DESARROLLO.md` (*Esto NO es como se instala en la tienda*) | Arranca con el equipo; no depende de nada externo |
| La venta a medias se guarda en el navegador y se retoma sola; un cobro sin respuesta se reintenta con su misma llave | spec 0003 RF-028; `frontend/src/paginas/Vender.jsx` | Un corte de luz o del Wi‑Fi a mitad de una venta ya está resuelto |
| Sin respuesta del servidor, la pantalla dice *"No hay conexión con el servidor"* | `frontend/src/api/cliente.js:76` | Si el Wi‑Fi de la tienda se cae, un **celular** deja de llegar al servidor; el computador del mostrador no |
| Cobrar, registrar un gasto y un retiro llevan llave contra el doble clic | `VentaController.java:131`, `GastoController.java:110`, `RetiroController.java:55` | Reintentar es seguro |
| **Registrar una compra no lleva llave** | `pos/…/compras/infraestructura/CompraController.java` (la petición no tiene llave) | Un doble clic con la red lenta puede registrar la compra dos veces y **sumar el stock dos veces** |
| No hay bandeja de salida ni eventos | búsqueda de *outbox* en `domain/`, `pos/` y migraciones sin resultados | Todo lo de H3–H5 es nuevo |
| Ya está pensada su forma: un id que la nube usa para no repetir, **secuencia en orden y nunca por fecha**, versión del evento, poda desde el día uno | `docs/SPEC_Modelo_Datos.md:508-525` | Se implementa esa forma, no otra |
| **No hay respaldo automático.** El único es uno manual | `respaldos/qa-2026-09-17.sql` | H2 es nuevo |
| El módulo offline del car‑wash repite **llamadas propias del car‑wash** (órdenes, cafetería, operarios) | `CAR-WASH-SYSTEM/frontend/src/offline/syncQueue.js:3-7` | "Portarlo" sería reescribirlo; y resuelve un problema que aquí casi no existe (§4) |

---

## 4. La decisión · ¿De qué corte nos protegemos?

| Corte | ¿Pasa hoy? | Qué haría falta |
|---|---|---|
| **Se cae internet** | Nada: se sigue vendiendo | Solo la bandeja hacia la nube (H3–H5) |
| **Se cae el Wi‑Fi de la tienda** | El computador del mostrador sigue; **un celular o tablet se queda sin servidor** | Una cola en el navegador de cada celular (portar el módulo del car‑wash) |
| **Se va la luz del computador** | Nada funciona hasta que vuelve; la venta a medias se retoma | Una UPS (hardware), no software |

| Opción | Qué implica |
|---|---|
| **A. Se vende desde el computador del mostrador.** Internet y Wi‑Fi pueden caerse | Solo la bandeja (H3–H5) y el respaldo (H2). Los celulares son pantallas extra: si el Wi‑Fi se cae, esperan |
| B. Además, vender desde un celular con el Wi‑Fi caído | Cola en el navegador. **El celular vendería sin que el servidor revise stock, precio ni turno**: al reconectar pueden aparecer ventas de algo que ya no había, precios viejos, y un cajón que no cuadra. Y es reescribir 2.600 líneas |

**Recomendación: A.** El cliente definió **un solo dispositivo activo a la vez** para vender
(`SPEC_Sistema_Ventas_Repuestos (3).md:178-180` y `:276-278`), y ese dispositivo es el computador de la tienda,
donde el servidor está al lado. B paga mucho para cubrir un caso que el propio cliente descartó, y trae los
conflictos que la arquitectura de una sola vía evita a propósito. **Si se va la luz, la respuesta es una UPS**
(unos minutos para cerrar la venta y apagar bien); va como recomendación de instalación, no como software.

**Y una consecuencia:** la bandeja sin la nube no tiene a dónde mandar. Por eso **se propone construir H3–H5 junto
con la rebanada 5**, y adelantar ya lo que vale solo: el respaldo automático (H2), la prueba de H1 y la llave de
compras.

---

## 5. Requisitos funcionales

### Sin internet (H1)
- **RF-001** · Todo el sistema funciona con el computador de la tienda **sin internet**: vender, anular, caja,
  compras, inventario, reportes, usuarios. Queda escrito como prueba de aceptación.
- **RF-002** · Nada de la pantalla carga cosas de internet para funcionar (letras, íconos, librerías): todo viene
  del servidor de la tienda. **Verificado el 2026-09-21: ya se cumple.** La fuente es la del sistema y no hay
  `@font-face` ni CDN (`frontend/src/index.css:76-77`, `frontend/index.html`); en el `dist` construido las únicas
  URL que quedan son espacios de nombres de SVG/MathML y enlaces de error de React. Queda una prueba que lo vigila

### Respaldo (H2)
- **RF-003** · Una vez al día, a una hora sin clientes, se saca una **copia completa** de la base a una carpeta
  del computador que no sea la de la base.
- **RF-004** · Se guardan las **últimas copias** (propuesta: 14 días) y las más viejas se borran solas.
- **RF-005** · Si se configura una segunda carpeta (una memoria USB, un disco externo), la copia también va allá.
- **RF-006** · Si un respaldo falla, **el administrador lo ve** al entrar (*"El respaldo de anoche falló"*).
- **RF-007** · Queda escrito **cómo se restaura** una copia en un computador nuevo, y se prueba una vez.
- **RF-008** · Los respaldos también guardan la clave de las sesiones (`~/.rdmotors/clave-token`), para que al
  restaurar no haga falta nada más. [NECESITA ACLARACIÓN: ¿en la tienda hay un disco o memoria USB para la
  segunda copia?]

### Compras con llave
- **RF-009** · Registrar una compra lleva llave, como cobrar: repetirla con la misma llave devuelve la que ya
  existe y **no suma el stock dos veces**.

### Bandeja hacia la nube (H3–H5, con la rebanada 5)
- **RF-010** · Cada operación que el dueño va a ver deja su **evento en la misma transacción**: venta, anulación,
  cierre de turno, gasto y su anulación, retiro y su anulación, compra y su corrección o anulación, fiado y abono
  (spec 0008).
- **RF-011** · Los eventos llevan **secuencia en orden**, un id que la nube usa para no repetirlos y su **versión**.
  Se ordenan por secuencia, **nunca por fecha** (`SPEC_Modelo_Datos.md:520-521`).
- **RF-012** · Se envían en orden cuando hay internet; si uno falla, se reintenta más tarde (cada vez esperando
  un poco más), sin frenar nada del mostrador.
- **RF-013** · Un evento se marca enviado **solo** cuando la nube confirma. Mandarlo dos veces no duplica nada allá.
- **RF-014** · Los enviados se podan después de un tiempo (propuesta: 30 días); los pendientes nunca.
- **RF-015** · El administrador ve la última sincronización, cuántos esperan y el último error.

---

## 6. Manejo de errores

| Situación | Qué pasa |
|---|---|
| Se cae internet a mitad de un envío | El evento sigue pendiente y se manda después; la venta no se entera |
| La nube rechaza un evento (versión que no conoce) | Queda pendiente con su error a la vista; los que siguen **esperan** (se respeta el orden) |
| Se llena el disco y el respaldo no cabe | Falla, se avisa al administrador, y **la venta sigue**: el respaldo nunca frena el mostrador |
| La memoria USB no está puesta | Se hace la copia local y se avisa que la segunda faltó |
| Doble clic al registrar una compra con la red lenta | Se registra una vez (RF-009) |
| El Wi‑Fi de la tienda se cae con un celular vendiendo | El celular dice *"No hay conexión con el servidor"*; la venta a medias queda guardada en él y se sigue en el computador o cuando vuelva el Wi‑Fi |

---

## 7. Requisitos no funcionales

- **El mostrador nunca espera a internet.** Ni el envío a la nube ni el respaldo corren dentro de una venta.
- **Mismo commit.** El evento y la venta se guardan juntos; nunca una sin la otra (RF-010).
- **Orden.** Por secuencia, no por reloj: en la tienda el reloj del computador puede estar mal.
- **Esquema.** La bandeja es una tabla nueva, con migración nueva. El respaldo no toca el esquema.
- **Roles.** Ver la bandeja y el estado del respaldo es del administrador.
- **Auditoría.** No aplica: ni la bandeja ni el respaldo cambian datos del negocio.

---

## 8. Criterios de aceptación

- [ ] Con internet desconectado, en el computador de la tienda: abrir turno, vender, anular, gasto, retiro,
      compra, cerrar turno y ver reportes, sin un solo error
- [ ] El respaldo se hizo solo durante la noche; hay a lo sumo 14 copias; una copia se restaura en una base vacía
      siguiendo el procedimiento escrito, y el sistema arranca con ella
- [ ] Si el respaldo falla, el administrador lo ve al entrar
- [ ] Una compra mandada dos veces con la misma llave queda una sola vez y suma el stock una vez
- [ ] *(con la rebanada 5)* Vender sin internet deja eventos pendientes en orden; al volver internet la bandeja
      queda en cero y la nube no tiene repetidos
- [ ] `./mvnw clean test` en verde; lint, pruebas y build del frontend en verde

---

## 9. Qué no se toca y fuera de alcance

**No se toca:** cómo se vende, se cobra o se cierra; la venta a medias en el navegador (ya resuelve el corte a
mitad de una venta).

**Fuera de alcance:**
- Vender desde un celular con el Wi‑Fi caído (opción B del §4).
- La nube y el panel del dueño: rebanada 5, con su propio spec (hosting, login del propietario, espejo).
- Sincronizar en las dos direcciones: la nube **no escribe** en la tienda (decisión del cliente, `:184-188`).
- La UPS: es compra de hardware; queda como recomendación en la guía de instalación.

---

## 10. Riesgos

| Riesgo | Qué hacer |
|---|---|
| **Hoy no hay respaldo automático**: un disco dañado se lleva toda la historia de la tienda, y con el fiado (spec 0008) también lo que le deben | Adelantar H2 antes de instalar en la tienda, aunque lo demás espere a la nube |
| Construir la bandeja antes que la nube: eventos que nadie consume y una forma que nadie probó contra el otro lado | H3–H5 se hacen con la rebanada 5, probados de punta a punta |
| Una semana sin internet son miles de eventos | Poda desde el día uno (RF-014) y envío por tandas |
| Un respaldo que nunca se probó restaurar no es un respaldo | RF-007 exige restaurarlo una vez de verdad |
