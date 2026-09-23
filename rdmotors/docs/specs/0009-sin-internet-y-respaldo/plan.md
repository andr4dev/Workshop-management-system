# Plan 0009 — Sin internet: el respaldo, la llave de compras y la prueba de que ya funciona

**Spec:** [`spec.md`](spec.md) · **Estado del spec:** aprobado (2026-09-21)
**Rebanada:** 4 · **Depende de:** todo hasta el spec 0008

---

## Qué se construye ahora y qué espera

El spec lo dice en su §4: **la bandeja hacia la nube no tiene a dónde mandar mientras la nube no exista**. Así que
este plan implementa lo que vale solo, hoy:

| Historia | En este plan | Por qué |
|---|---|---|
| **H1** · se cae internet y la tienda ni se entera | **Sí**, como prueba y como guía | Ya funciona; lo que falta es dejarlo demostrado y que nadie lo rompa sin enterarse |
| **H2** · el respaldo se hace solo | **Sí**, entero (RF-003 a RF-008) | Es el riesgo #1 del spec: hoy un disco dañado se lleva la historia del negocio, y con el fiado, también lo que le deben |
| **RF-009** · llave en compras | **Sí** | Es la única escritura que suma y no tiene llave: un doble clic suma el stock dos veces |
| **H3, H4, H5** · bandeja hacia la nube | **No**, van con la rebanada 5 | Construir eventos que nadie consume es una forma que nadie probó contra el otro lado (riesgo #2 del spec) |
| **H6** · copia del respaldo en la nube | **No** | Necesita la nube |

**El alcance de este plan son tres fases.** Al terminar, la tienda puede instalarse sin internet y sin miedo a
perder la base.

---

## Verificado antes de planear

Todo esto se leyó en el código, no se supuso:

| Hecho | Dónde |
|---|---|
| La pantalla **no pide nada de internet**: la fuente es la del sistema, no hay `@font-face`, ni CDN, ni Google Fonts | `frontend/src/index.css:76-77`; `frontend/index.html` (solo `/src/main.jsx`) |
| El `dist` ya construido tampoco: las únicas URL que quedan son espacios de nombres de SVG/MathML y enlaces de error de React | `frontend/dist/assets/index-*.js` (búsqueda de `http`) |
| Registrar una compra **no lleva llave**: la petición no la tiene y el caso de uso tampoco | `CompraController.java:167-181`, `ComandoRegistrarCompra.java:25-33` |
| Cuatro tablas ya llevan llave, con índice único, y su caso de uso mira la llave **antes y después** de tomar candados | `V9__venta.sql:38,60`, `V12__gastos.sql:58,75`, `V13__retiros_y_compras_de_caja.sql:18,28`, `V20__clientes_y_fiado.sql:105,120`; `RegistrarAbono.java:63-75` |
| El navegador ya sabe hacer llaves sin `crypto.randomUUID` (la tablet entra por `http://192.168.x.x` y allí no existe) | `frontend/src/utils/venta.js:26-31` |
| La base va en `localhost:5433` en desarrollo (Docker) y **nativa** en la tienda | `application.properties:5`, `compose.yaml` |
| `pg_dump` 17.9 está instalado en el equipo | `C:\Program Files\PostgreSQL\17\bin\pg_dump.exe --version` |
| **No hay nada programado en el tiempo**: ni `@Scheduled` ni `@EnableScheduling` en todo `pos` | búsqueda en `pos/src/main/java` sin resultados |
| El único respaldo que existe es uno sacado a mano | `respaldos/qa-2026-09-17.sql` |

**Lo que esto cambia del spec:** RF-002 estaba marcado *«sin verificar»*. Ya está verificado y **se cumple hoy**:
en la fase 3 queda una prueba que lo vigila, no trabajo por hacer.

---

## Decisiones tomadas al planear

1. **El respaldo lo hace `pg_dump`, no Java.** Un volcado hecho a mano leyendo tablas se desincroniza con el
   esquema en la primera migración. `pg_dump` es la herramienta del motor, viene con Postgres y produce un archivo
   que `psql` restaura de una. El dominio no lo sabe: pide `Volcador.volcar(destino)`.
2. **`Volcador` es un puerto de verdad**, no una interfaz decorativa: hoy tiene dos implementaciones —`pg_dump` y
   el falso de las pruebas— y mañana la tercera es la copia a la nube (H6).
3. **El respaldo nunca corre dentro de una venta ni dentro de una transacción.** Es una tarea programada; lo único
   transaccional es anotar el resultado en una fila.
4. **Formato `custom` (`-Fc`), no SQL plano.** Pesa menos, se restaura con `pg_restore` en paralelo y no se puede
   editar a mano por error. El archivo que ya existe (`respaldos/qa-2026-09-17.sql`) se queda como está.
5. **La política vive en el dominio** (`PoliticaDeRespaldo`): cuántos días se guardan, cuáles sobran, si falta el
   de hoy y cómo se llama el archivo. Son reglas con ejemplos y se prueban sin disco.
6. **La segunda copia (la USB) no es obligatoria y no puede tumbar el respaldo.** Si la carpeta no existe, el
   respaldo local queda *hecho* y la fila dice que la segunda falló. Es lo que pide el spec (§6).
   [NECESITA ACLARACIÓN del spec, sigue abierta: ¿en la tienda hay memoria USB o disco externo para la segunda
   copia? Mientras no se sepa, viene apagada y se enciende con una línea de configuración.]
7. **La clave de las sesiones viaja con el respaldo** (RF-008): se copia `~/.rdmotors/clave-token` a la carpeta de
   respaldos en cada corrida. Sin ella, al restaurar nadie puede entrar aunque los datos estén.
8. **La llave de compras se guarda en la compra**, como en venta, gasto, retiro y abono: columna `llave_idempotencia`
   con índice único, y el caso de uso la busca antes y después de tomar candados.
9. **La llave de compras es obligatoria en el servidor.** Ninguna compra vieja necesita migrarse hacia atrás: la
   columna nace `NOT NULL` con un valor por fila existente (`gen_random_uuid()`), que es distinto para cada una.
10. **Ver los respaldos es del administrador.** El cajero no tiene nada que hacer ahí, y el aviso de *«el respaldo
    de anoche falló»* solo le sale al administrador.

---

## Fase 1 · La llave de compras (RF-009)

| Pieza | Dónde cae |
|---|---|
| `Compra.llaveIdempotencia` (columna nueva) y `Compra.registrar(..., llave)` | `compras/dominio/` |
| `RepositorioCompras.buscarPorLlave(UUID)` | `compras/dominio/puerto/` + adaptador JPA |
| `ComandoRegistrarCompra` con `llave`; `RegistrarCompra` mira la llave **antes** de pedir el turno y **otra vez** después de tomar los candados | `compras/aplicacion/` |
| `PeticionCompra.llave` (`@NotNull`) | `pos/…/compras/infraestructura/` |
| **V22**: `compra.llave_idempotencia uuid NOT NULL DEFAULT gen_random_uuid()` + `ux_compra_llave` | `pos/…/db/migration/` |

**Frontend:** `Compra.jsx` arma la llave al abrir la pantalla (`llaveNueva()`), la manda y la renueva **solo cuando
la compra queda registrada**; si el servidor no contesta, reintentar manda la misma. *(La pantalla de compra no
guarda borrador en el navegador —eso es de la venta—, así que la llave vive en el estado de la pantalla.)*
**Pruebas:** `RegistrarCompraTest` (misma llave dos veces → una compra, el stock sube una vez, el costo promedio
se recalcula una vez); integración: **dos registros a la vez con la misma llave** (uno entra, el otro devuelve el
mismo id) y `MigracionesIntegracionTest` de V22 sobre una base con compras viejas (cada una recibe una llave
distinta y el índice único aguanta).
**Romper:** quitar la segunda consulta de la llave; poner un `DEFAULT` fijo en la migración (dos compras viejas
chocarían).
**Checkpoint:** con la red lenta, dos clics en *Registrar compra* dejan **una** compra y el stock sube una vez.

---

## Fase 2 · El respaldo automático (RF-003, RF-004, RF-005, RF-008)

| Pieza | Dónde cae |
|---|---|
| `Respaldo` (entidad: cuándo, archivo, bytes, cuánto tardó, estado, error, segunda copia, origen), `EstadoRespaldo {HECHO, FALLO}`, `OrigenRespaldo {AUTOMATICO, A_MANO}` | `respaldo/dominio/` |
| `PoliticaDeRespaldo`: `nombreDeArchivo(instante)`, `sobran(List<Respaldo>, hoy)` (deja los últimos N días, nunca borra el último bueno), `faltaElDeHoy(...)`, `ultimoFallo(...)` | `respaldo/dominio/` |
| `Volcador` (`volcar(Path) → long bytes`, lanza `RespaldoFallidoException` con lo que dijo el motor), `Archivos` (`copiar`, `borrar`, `existeCarpeta`, `tamano`), `RepositorioRespaldos` | `respaldo/dominio/puerto/` |
| `HacerRespaldo` (vuelca, copia la clave de sesiones, copia a la segunda carpeta si está configurada, borra lo que sobra, anota la fila; **cualquier falla se anota, no se propaga**), `ConsultarRespaldos` (lista + estado; del administrador) | `respaldo/aplicacion/` |
| `VolcadorPgDump` (`ProcessBuilder` con la ruta configurada, `PGPASSWORD` por entorno, salida de error recortada), `ArchivosDelDisco`, `RepositorioRespaldosJpa`, `TareaDeRespaldo` (`@Scheduled(cron)`), `ConfiguracionDeTareas` (`@EnableScheduling`), `RespaldoController` (`GET /api/respaldos`, `GET /api/respaldos/estado`, `POST /api/respaldos`) | `pos/…/respaldo/infraestructura/` |
| **V23**: tabla `respaldo` | `pos/…/db/migration/` |

**Configuración** (`application.properties`, con valores que sirven tal cual):
`rdmotors.respaldo.habilitado=true` · `carpeta=${user.home}/.rdmotors/respaldos` · `segunda-carpeta=` (vacía) ·
`cron=0 0 2 * * *` (2 a. m., sin clientes) · `dias-que-se-guardan=14` ·
`pg-dump=C:/Program Files/PostgreSQL/17/bin/pg_dump.exe`. En las pruebas, `habilitado=false`.

**Pruebas:** `PoliticaDeRespaldoTest` (14 días, el último bueno nunca se borra, el nombre del archivo, *falta el de
hoy*), `HacerRespaldoTest` con `Volcador` falso (el feliz; el volcado falla → fila `FALLO` con el error y **no se
borra ninguna copia vieja**; la segunda carpeta no existe → local hecho y aviso; el disco lleno → falla sin tumbar
nada), integración: `VolcadorPgDump` contra el Postgres de Testcontainers **volcando y restaurando de verdad** en
una base vacía, y `MigracionesIntegracionTest` de V23.
**Romper:** que una falla del volcado se propague (frenaría la tarea); borrar las viejas antes de que la nueva
esté hecha; guardar la fila antes de que el archivo exista.
**Checkpoint:** `POST /api/respaldos` deja un archivo en la carpeta, la clave de sesiones al lado, y la fila dice
cuánto pesó y cuánto tardó. Con el cron a un minuto, aparece solo.

---

## Fase 3 · Que se vea, que se restaure y que sin internet quede probado (RF-006, RF-007, RF-001, RF-002)

| Pieza | Dónde cae |
|---|---|
| `ConsultarRespaldos.estado()` → último respaldo, si el de anoche falló, cuántas copias hay y cuánto ocupan | `respaldo/aplicacion/` |
| Pantalla *Respaldo* (en el ⚙, solo administrador): último respaldo y su peso, la lista de copias, *Hacer uno ahora*, y qué hacer si falla | `frontend/src/paginas/Respaldo.jsx` + `utils/respaldo.js` |
| El aviso al entrar: *«El respaldo de anoche falló»* con enlace a la pantalla (solo administrador) | `App.jsx` / la barra |
| `docs/RESPALDO_Y_RESTAURAR.md`: cómo restaurar en un computador nuevo, paso por paso, con la clave de sesiones | `docs/` |
| `docs/SIN_INTERNET.md`: la lista de lo que se prueba con el cable desconectado (RF-001) y por qué la pantalla no pide nada afuera (RF-002) | `docs/` |

**Pruebas:** `utils/respaldo.test.js` (cuánto pesa en palabras, *hace 2 días*, el aviso); una prueba de pantalla
que recorre el `dist` construido y **falla si aparece una URL externa** (RF-002, para que nadie meta una fuente de
Google sin darse cuenta); la restauración de verdad **se hace una vez a mano** siguiendo el documento, contra una
base vacía, y se anota en la bitácora (RF-007).
**Romper:** meter un `@import url(https://fonts.googleapis.com/…)` en el CSS → la prueba del `dist` tiene que
fallar.
**Checkpoint:** con el cable de internet desconectado: abrir turno, vender, fiar, abonar, anular, gasto, retiro,
compra, cerrar turno y ver reportes. Y el respaldo de anoche restaurado en una base vacía, con el sistema
arrancando encima.

---

## Riesgos

| Riesgo | Qué se hace |
|---|---|
| `pg_dump` no está, o es de otra versión que el servidor | El adaptador dice exactamente qué pasó (*«no encontré pg_dump en…»*), la fila queda en `FALLO` y el administrador lo ve. La ruta es configuración, no código |
| El respaldo se cruza con el cierre de caja de la noche | Corre a las 2 a. m. y no toma candados: `pg_dump` lee una foto coherente sin bloquear a nadie |
| Se llena el disco | La copia nueva se hace primero y solo después se borran las viejas; si no cabe, queda `FALLO` y las viejas siguen ahí |
| Un respaldo que nunca se restauró no es un respaldo | RF-007: se restaura una vez de verdad en la fase 3, y queda escrito |
| Una compra a medio capturar y una recarga de la pantalla | La llave vive en la pantalla: al recargar nace otra, y la compra se registra una vez igual. La pantalla de compra no guarda borrador (a diferencia de la venta), así que no hay nada viejo que migrar |

---

## Verificación final

1. Backend abajo → `./mvnw clean install` (dominio, Postgres, migraciones).
2. `cd frontend && npx eslint src/ && node --test src/utils/*.test.js && npx vite build`.
3. Romper a propósito de las tres fases, con el bash de Git.
4. Capturas de la pantalla *Respaldo* y del aviso, en claro, oscuro y 390 px, con respuestas simuladas.
5. La restauración de verdad, a mano, en una base vacía.
6. Backend arriba otra vez (aplica V22 y V23 en QA).

---

## Bitácora de decisiones

Se llena **durante** la implementación, con fecha, fase, decisión y porqué.

| Fecha | Fase | Decisión | Por qué |
|---|---|---|---|
| 2026-09-23 | — | **El nombre del archivo lleva segundos** (`rdmotors-2026-09-23-112234.dump`), y las copias que comparten archivo se cuentan una sola vez | Lo encontró el dueño usándolo: tres respaldos a mano seguidos cayeron en el mismo minuto, **se pisaron el archivo** y la pantalla mostraba tres copias donde había una, sumando su peso tres veces |
| 2026-09-23 | — | **La pantalla de Respaldo avisa al de arriba cuando la copia queda hecha** (evento `rdmotors:respaldo-hecho`) | El aviso leía el estado al cargar la página y se quedaba diciendo *«hace 2 días»* con la copia recién hecha a la vista, en la misma pantalla |
| 2026-09-21 | 1 | **La llave de compras se mira dos veces: antes de tocar nada y otra vez con los repuestos ya bloqueados** | La primera evita que un reintento se quede esperando un repuesto ocupado o pida un turno que ya se cerró; la segunda es la que atrapa dos registros simultáneos. La segunda tiene que ir **antes** de mover inventario: más adelante los repuestos ya están cambiados en memoria y salir a medias los dejaría sumados igual |
| 2026-09-21 | 1 | **El `DEFAULT` de la columna nueva es `gen_random_uuid()`, y se quita enseguida** | Se evalúa fila por fila: un valor fijo habría hecho chocar la segunda compra vieja contra el índice único. Y quitarlo después obliga a que, de aquí en adelante, la llave la ponga quien registra |
| 2026-09-21 | 1 | **Tres pruebas que ya existían tuvieron que aprender la llave**: la de seguridad (rol en compras), la de V17 y la del cierre del cajón | Sin la llave el servidor responde 400 por validación antes de llegar a la regla que esas pruebas miraban. Que se rompieran es justo lo que se quería: nadie puede registrar una compra sin llave |
| 2026-09-21 | 2 | **El respaldo lo saca `pg_dump` en formato `custom`, por un puerto (`Volcador`)** | Un volcado escrito a mano se desincroniza con el esquema en la primera migración y se descubre el día que hay que restaurar. El puerto tiene hoy dos implementaciones —`pg_dump` y el falso de las pruebas— y mañana la de la nube (H6) |
| 2026-09-21 | 2 | **`pg_dump` se conecta a la base de verdad (`JdbcConnectionDetails`), no a `spring.datasource.url`** | Lo encontró la prueba de integración: con el contenedor de pruebas, leer la propiedad devolvía la base de la tienda y **el respaldo copiaba otra base**. En producción las dos coinciden; así no se puede equivocar |
| 2026-09-21 | 2 | **Nada de lo que falle en el respaldo sale hacia afuera**: queda una fila con el error | Una excepción mataría la tarea de la madrugada y nadie se enteraría hasta el día que hiciera falta la copia. El administrador lo ve al entrar (RF-006) |
| 2026-09-21 | 2 | **Primero la copia nueva, después la poda**, y la última copia buena nunca se borra | Podar antes de tener la nueva es cambiar lo que había por nada. Y si el respaldo lleva semanas fallando, la copia vieja es lo único que queda: borrarla por vieja sería el peor momento posible |
| 2026-09-21 | 2 | **La fila del respaldo se queda aunque su archivo se pode** (`archivo_borrado_en`) | Sin ella no se distinguiría "ese día no hubo respaldo" de "ese día sí hubo y la copia ya se podó", y el intento de borrar se repetiría todas las noches |
| 2026-09-21 | 2 | **La segunda copia y la clave de sesiones avisan, no tumban** | Que la memoria USB no esté puesta no invalida la copia local; que no esté la clave tampoco. Los dos avisos se acumulan en la misma fila |
| 2026-09-21 | 3 | **El aviso del respaldo va arriba de todo, no dentro de su pantalla**, y se puede cerrar por esta sesión | Nadie entra a mirar el respaldo: se entra a vender. Y un aviso que sale en cada recarga se vuelve ruido, al que se le deja de hacer caso |
| 2026-09-21 | 3 | **RF-002 ya se cumplía; lo que se agregó es la prueba que lo vigila** (`utils/sinInternet.js` y su prueba) | Verificar una vez no sirve de nada si mañana alguien mete una fuente de Google. La prueba recorre el proyecto y falla antes de que el problema llegue a la tienda |
| 2026-09-21 | 3 | **La tabla de copias se desliza dentro de su marco en el celular** | La captura de 390 px mostró el documento en 877 px: las rutas de Windows no caben. Es la misma solución de las otras listas (`scroll-x`) |
