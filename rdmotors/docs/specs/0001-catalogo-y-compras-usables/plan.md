# Plan 0001 — Catálogo y compras usables

**Traduce:** [`spec.md`](spec.md) (aprobado)
**Estado:** ✅ completo — las seis fases cerradas el 2026-09-12

---

## Resumen

Seis fases. Las cuatro primeras son backend y terminan en checkpoints verificables con archivos
`.http`; las dos últimas son la pantalla y terminan en el demo que se le enseña al cliente.

El orden tiene una razón: **la prueba contra Postgres real va en la fase 2, no al final.** Es lo
primero que puede comprobar que el esquema cuadra con las entidades. Descubrir que `Dinero` no
mapea bien con tres adaptadores escritos es barato; descubrirlo con diez, no.

---

## Contexto técnico

Media rebanada ya existe (ver `spec.md` §3). Las reglas de negocio —crear repuesto, crear producto,
crear proveedor, costo promedio— **están escritas y probadas**. Lo que falta son los puertos que
nadie declaró, los casos de uso que nadie coordinó, y las puertas de entrada.

Recordatorio de dónde cae cada cosa:

| Carpeta | Qué va | Cómo se reconoce |
|---|---|---|
| `domain/…/dominio/` | reglas y sustantivos | tiene cuerpo, hace algo |
| `domain/…/dominio/puerto/` | lo que el dominio pide | todo termina en `;` |
| `domain/…/aplicacion/` | casos de uso | abren la transacción, no calculan |
| `pos/…/infraestructura/` | adaptadores y endpoints | importan Spring, JPA o HTTP |

---

## Fase 1 · El dominio del catálogo — ✅ COMPLETA

**Solo `domain`. Nada visible todavía — es la fase que habilita las demás.**

> **Cerrada el 2026-09-11.** 56 pruebas en verde (eran 28). Se verificó que la prueba de la
> decisión §4 muerde: al romper la reutilización del concepto a propósito, fallaron exactamente las
> dos pruebas que la guardan. Los tres chequeos de arquitectura siguen en cero.
>
> **Se adelantó trabajo de la fase 2:** `buscarPorTexto` en el adaptador JPA, porque agregar el
> método al puerto rompe la compilación de `pos` al instante. Ver bitácora.

| Pieza | Dónde cae |
|---|---|
| `RepositorioProductos` — buscar por nombre, guardar | `inventario/dominio/puerto/` |
| `RepositorioCategorias` — listar activas | `inventario/dominio/puerto/` |
| `buscarPorTexto(...)` añadido al puerto de variantes | `inventario/dominio/puerto/` |
| `CodigoDuplicadoException` | `inventario/dominio/` |
| `CrearRepuesto` — valida código único, reutiliza o crea el concepto | `inventario/aplicacion/` |
| `BuscarRepuestos` | `inventario/aplicacion/` |
| `RegistrarProveedor` | `compras/aplicacion/` |

**Cubre:** RF-001, RF-004 a RF-008, RF-010 a RF-013.

**Pruebas:** dobles nuevos en `Falsos`. Código duplicado rechazado diciendo cuál lo tiene;
concepto existente reutilizado en vez de duplicado (la decisión §4 del spec); repuesto sin compras
devuelve costo `null`, nunca cero.

**Checkpoint:** `./mvnw -pl domain test` en verde con las pruebas nuevas.

---

## Fase 2 · Adaptadores y la primera prueba contra Postgres real — ✅ COMPLETA

**`pos`.** Aquí se descubre si el modelo y el esquema cuadran de verdad.

> **Cerrada el 2026-09-11.** 56 pruebas de dominio + 6 de integración contra Postgres 17 en Docker.
> Flyway aplica las 2 migraciones y `ddl-auto=validate` pasa.
>
> **Encontró dos cosas que ninguna prueba con dobles podía encontrar** — ver bitácora: Flyway no
> estaba corriendo, y `validate` no detecta cambios de precisión.

| Pieza | Dónde cae |
|---|---|
| `RepositorioProductosJpa`, `RepositorioCategoriasJpa` | `inventario/infraestructura/` |
| Búsqueda por texto en `RepositorioVariantesJpa` | `inventario/infraestructura/` |
| Testcontainers + primera prueba de integración | `pos/src/test/` |

**La prueba de integración hace lo que ningún doble puede:** levanta Postgres en Docker, corre las
migraciones de Flyway, deja que `ddl-auto=validate` compare entidades contra tablas, y registra una
compra completa verificando el stock y el kardex en la base real.

**Cubre:** el RNF de "pruebas contra base real".

**Checkpoint:** la prueba de integración pasa. Si `validate` se queja del mapeo de `Dinero`
embebido o de una precisión, **este es el momento correcto de enterarse**.

---

## Fase 3 · Endpoints y QA de contrato — ✅ COMPLETA

**`pos`.** Sin pantalla todavía.

> **Cerrada el 2026-09-11.** Los 6 endpoints funcionando, ejercitados contra la app corriendo:
> **15 verificaciones del flujo feliz y 10 del camino infeliz, todas en verde.**
>
> Los archivos `.http` quedaron en [`http/`](../../../http/) — son el QA de contrato de la
> rebanada, versionados en el repo.
>
> Verificado en vivo: la decisión §4 se sostiene (dos marcas, **un solo concepto**), el total de
> la compra cuadra al peso (`238.172 = 200.000 + 6 × 6.362`), el costo unitario viaja con sus
> cuatro decimales, el precio en blanco no toca el precio, y el promedio ponderado se movió a
> `16.000` tras la segunda compra.

| Endpoint | Para qué |
|---|---|
| `POST` / `GET /api/proveedores` | RF-001, RF-002 |
| `GET /api/categorias` | RF-008 |
| `GET /api/productos?q=` | RF-005 — buscar el concepto antes de crear |
| `POST /api/repuestos` | RF-006, RF-007 |
| `GET /api/repuestos?codigo=` y `?q=` | RF-010, RF-011, RF-012 |
| `GET /api/repuestos/{id}/kardex` | RF-021, RF-022 |

Más archivos `.http` versionados en el repo, que son el QA de contrato de esta rebanada.

**Checkpoint:** desde los `.http` se crea un proveedor, se crea un repuesto, se busca por código y
por texto, y se ve su kardex vacío.

---

## Fase 4 · Crear el repuesto durante la compra — ✅ COMPLETA

**`domain` + `pos`.** Es el cambio más delicado de la rebanada.

> **Cerrada el 2026-09-11.** 66 pruebas de dominio + 8 de integración.
>
> **El guardarraíl se cumplió: las 14 pruebas originales pasan sin tocarse.** Solo cambió una
> línea de cableado en el `@BeforeEach` —el caso de uso ganó una dependencia—, ninguna aserción
> de comportamiento.
>
> Se verificó que la prueba de atomicidad muerde: con `REQUIRES_NEW` en `CrearRepuesto`, el
> repuesto sobrevivió al rollback y quedó huérfano. **Las 66 pruebas de dominio siguieron en
> verde** — los dobles en memoria no tienen transacciones que deshacer.

Hoy `RegistrarCompra` lanza *"El repuesto no existe"*. Pasa a aceptar, en cada renglón, **o** el id
de un repuesto existente **o** los datos para crearlo.

> **Cuidado declarado:** las 14 pruebas de `RegistrarCompraTest` tienen que seguir pasando **sin
> tocarse**. Si alguna hay que modificar, es señal de que el cambio rompió el contrato que ya
> funcionaba — y eso se revisa antes de seguir, no después.

**Cubre:** RF-014 a RF-018, y el "crear proveedor desde la compra" de H1.

**Checkpoint:** una compra de dos renglones —uno con repuesto existente, otro que lo crea— deja
ambos con el stock correcto y sus movimientos de kardex.

---

## Fase 5 · Frontend: el proyecto y la pantalla de compra — ✅ COMPLETA

> **Cerrada el 2026-09-12.** Se registraron compras reales desde el navegador y el stock subió.
> Además de lo planeado entró: crear proveedor desde la compra, corregir la ficha de un repuesto
> (RF-009), relleno con la última compra, aviso de repuesto repetido y bloqueo de renglones
> incompletos. Ver bitácora.

**Nuevo: `frontend/`.** React + Vite, CSS Modules, sin TypeScript — mismo stack que el car-wash,
para que el módulo offline se pueda portar tal cual en la rebanada 4.

- Proyecto, cliente HTTP, tema claro/oscuro por tokens
- Pantalla de compra: proveedor con autocompletado y creación al vuelo; renglones con el **campo de
  código que hace de buscador** (RF-007b); los dos modos de captura; margen en vivo; creación de
  repuesto sin salir de la compra

**Checkpoint — este es el demo.** Se registra una factura real de Jotapartes desde el navegador y
el stock aparece.

---

## Fase 6 · Frontend: inventario y kardex — ✅ COMPLETA

> **Cerrada el 2026-09-12.** 85 pruebas de dominio, 13 de integración y 26 del frontend en verde.
> Lint y build limpios. Las dos pantallas se revisaron renderizadas con datos reales, en claro, en
> oscuro y en angosto.
>
> Checkpoint cumplido con `352B59K`: tres compras, y el promedio se ve pasar de $13.333 a $16.000 y
> luego a $12.285, cada movimiento con su proveedor y su factura.
>
> **Encontró un bug de la fase 5:** corregir la ficha de un repuesto le borraba la categoría y le
> devolvía el stock mínimo a 5. Ver bitácora.

- Navegación por módulos: **Compras** e **Inventario** (`react-router-dom`, que ya estaba en
  `package.json`)
- Inventario: totales, búsqueda por código, nombre, marca o moto, filtro "por pedir" y paginación
- Ficha del repuesto: cifras, corregir ficha, y el kardex con documento, entradas, salidas, saldo y
  promedio

| Pieza | Dónde cae |
|---|---|
| `Pagina<T>` — página sin importar Spring | `compartido/dominio/` |
| `ResumenInventario` | `inventario/dominio/` |
| `listar(...)` y `resumen()` añadidos al puerto de variantes | `inventario/dominio/puerto/` |
| `BuscarRepuestos.inventario(...)` y `porId(...)` | `inventario/aplicacion/` |
| `GET /api/inventario`, `GET /api/inventario/resumen`, `GET /api/repuestos/{id}` | `inventario/infraestructura/` |
| `DocumentosDeCompra` — de qué factura salió cada movimiento | `compras/infraestructura/` |

**Checkpoint:** buscar "filtro", abrir uno, y ver los dos movimientos con el costo promedio
moviéndose entre ellos.

---

## Riesgos conocidos

| Riesgo | Cuándo aparece | Qué hacer |
|---|---|---|
| `ddl-auto=validate` rechaza el mapeo de `Dinero` embebido o una precisión | fase 2 | Es justo para eso que la fase 2 va temprano. Se ajusta la migración, nunca la entidad para "que pase" |
| Docker no está corriendo | fase 2 | Las pruebas de integración se **saltan**, no fallan. Un rojo por falta de Docker entrena a ignorar rojos |
| Cambiar la firma de `RegistrarCompra` rompe sus 14 pruebas | fase 4 | Ver el cuidado declarado arriba |
| `ILIKE '%texto%'` no usa índice | fase 2-3 | Con unos miles de repuestos da igual. Pero en la rebanada 2 lo usa el cajero con un cliente enfrente: si se queda corto, se pasa a `pg_trgm`. Declarado ahora para no descubrirlo en el mostrador |
| El concepto se duplica igual, porque el admin no busca antes | fase 5 | La pantalla tiene que empujar a buscar primero. Si en el demo se duplica un concepto, la decisión §4 no quedó bien implementada |

---

## Verificación final

Los catorce criterios de aceptación del `spec.md` §8, uno por uno, con una factura real de
Jotapartes en la mano. Y:

```bash
./mvnw clean test        # dominio + pos, incluida la integración
cd frontend && npx eslint src/ && npx vite build
```

---

## Bitácora de decisiones

Se llena **durante** la implementación, no antes. Cada vez que haya que decidir algo que el spec no
previó, se anota aquí con su porqué — es lo que evita re-litigarlo en tres semanas.

| Fecha | Fase | Decisión | Por qué |
|---|---|---|---|
| 2026-09-11 | 1 | **Una consulta lleva caso de uso solo si mapea o aplica una regla.** `BuscarRepuestos` lo lleva (mapea a un resultado y sostiene la regla de que el costo desconocido es `null`, no cero). Listar proveedores o categorías no: el controlador usa el puerto directo | Un caso de uso que solo reenvía al repositorio es ceremonia. La línea queda en si hay algo que proteger |
| 2026-09-11 | 1 | **No se puede escalonar un puerto entre fases.** Agregar `buscarPorTexto` al puerto rompió la compilación de `pos` de inmediato | El compilador exige que el adaptador cumpla el puerto. Se implementó el adaptador en la fase 1 aunque el plan lo ponía en la 2. **Lección para el resto del plan:** tocar un puerto arrastra su adaptador en el mismo commit, siempre |
| 2026-09-11 | 1 | El código se valida **antes** de crear el concepto | Al revés dejaría conceptos huérfanos sin ningún repuesto colgando cada vez que alguien repite un código. Cubierto por `duplicadoNoDejaBasura` |
| 2026-09-11 | 1 | `buscarPorTexto` usa `join fetch` sobre el concepto | Sin él, cincuenta resultados son cincuenta consultas extra. El mismo buscador lo usará el cajero en la rebanada 2 |
| 2026-09-11 | 2 | **`flyway-core` NO basta en Spring Boot 4: hace falta `spring-boot-flyway`** | Boot 4 modularizó las autoconfiguraciones. Con solo la librería, Flyway está en el classpath y **no hace nada, sin avisar**. El síntoma fue `Schema validation: missing table [categoria]` — que parece un problema de entidades y era que las migraciones nunca corrieron. La primera prueba de integración lo destapó en su primer intento |
| 2026-09-11 | 2 | **`ddl-auto=validate` NO comprueba la precisión numérica** | Se rompió `costo_promedio` de `numeric(14,4)` a `(14,2)` y `validate` lo dejó pasar. Lo atrapó la aserción de ida y vuelta: `expected 13333.3333 but was 13333.33`. **Lección:** validate cubre tablas y columnas, no precisión. Toda columna de dinero o costo necesita su prueba de ida y vuelta contra base real |
| 2026-09-11 | 2 | Testcontainers 2.x renombró los módulos: `postgresql` → `testcontainers-postgresql` | Spring Boot 4.1.1 gestiona la versión (2.0.5), pero con los nombres viejos el POM ni siquiera se lee |
| 2026-09-11 | 2 | Las pruebas de integración **fallan** sin Docker, no se saltan | El plan contemplaba saltarlas. Se decidió lo contrario: una prueba que se salta sola deja de proteger sin que nadie se entere. Docker está disponible en la máquina de desarrollo |
| 2026-09-11 | 2 | **RD Motors usa Postgres en 5433 y backend en 8081** | El car-wash tiene tomados el 5432 y el 8080 en esta máquina. Cada proyecto con su carril en vez de pelearse el puerto. Documentado en `docs/DESARROLLO.md` |
| 2026-09-11 | 2 | Base de desarrollo por `compose.yaml` + `spring-boot-docker-compose` con `start_only` | Arranca sola al hacer `spring-boot:run` y **no se apaga** al detener la app. La dependencia va `optional`: en la tienda Postgres es nativo y no habrá Docker |
| 2026-09-11 | 3 | **Código duplicado devuelve 409, no 422**, con el nombre del que lo tiene | No es un dato inválido: es un conflicto con algo que ya existe. Y el nombre permite que la pantalla ofrezca *"¿te referías a este?"* en vez de solo negarse — que es justo lo que evita que el admin cree un duplicado por no encontrar lo que buscaba |
| 2026-09-11 | 3 | **Un solo endpoint de búsqueda** con `?codigo=` o `?q=`, y devuelve lista en los dos casos | El frontend tiene un solo campo (RF-007b). Una sola forma de respuesta le evita dos caminos de pintado. Si vienen los dos parámetros manda el código: es el más específico |
| 2026-09-11 | 3 | `HttpStatus.UNPROCESSABLE_ENTITY` está deprecado en Spring 7 → `UNPROCESSABLE_CONTENT` | Solo un rename del estándar HTTP. Anotado porque va a reaparecer en cada controlador nuevo |
| 2026-09-11 | 3 | El kardex se guarda cronológico y **se invierte al presentar** | El libro mayor es cronológico por naturaleza; que la pantalla muestre lo más reciente primero (RF-021) es una decisión de presentación, no de almacenamiento |
| 2026-09-11 | 4 | **`RegistrarCompra` delega en `CrearRepuesto`; no reimplementa la creación** | Ese caso de uso es dueño de la regla del código único y de la decisión §4. Copiarla sería condenarlas a divergir — exactamente el error que la skill `backend` documenta como "el mismo cálculo corregido tres veces" |
| 2026-09-11 | 4 | El guardarraíl era sobre **comportamiento, no sobre construcción** | Añadir una dependencia obligó a cambiar una línea del `@BeforeEach`. Las 14 aserciones de comportamiento quedaron intactas, que es lo que el guardarraíl protegía |
| 2026-09-11 | 4 | El precio de un repuesto nuevo va en sus datos de creación, **nunca en el renglón** | Dos fuentes para el mismo dato es la receta para que diverjan. El comando rechaza que vengan las dos |
| 2026-09-11 | 4 | **Los dobles en memoria permiten estados que Postgres no** | Se sembró una variante cuyo concepto no estaba en el repositorio de productos — imposible con la clave foránea real. Costó un fallo de prueba. **Es el límite de los dobles y la razón de que la integración exista** |
| 2026-09-12 | 5 | **El precio de venta vive solo en el renglón de la compra**; el modal de repuesto no lo muestra | Estaba en dos sitios y llegó a mostrar $190.000 en uno y $180.000 en el otro. Se decide en el renglón porque ahí están el costo y el margen. La trazabilidad del cambio queda en `linea_compra.precio_venta`, con fecha, proveedor y factura |
| 2026-09-12 | 5 | **Al enganchar un repuesto existente, el renglón se rellena con el costo de la última compra y el precio actual** | Casi todas las compras repiten costo y precio. El costo relleno se ve en gris e itálica hasta que se toca, porque un costo viejo registrado sin mirar la factura entra al promedio sin que nadie lo note. Si el precio no se cambió se manda `null` (no tocar) y no el mismo valor: así `linea_compra.precio_venta` solo tiene valor en las compras donde el precio sí cambió |
| 2026-09-12 | 5 | **Repuesto repetido en la compra: aviso modal al teclear el código.** **Ningún renglón se descarta en silencio al registrar.** **"Cambiar repuesto" deja el renglón en blanco** | El aviso en línea dejaba seguir escribiendo en el renglón repetido, y como ese renglón nunca quedaba enganchado, al registrar se descartaba sin avisar: la compra se guardaba sin esas unidades. Ahora la cantidad y el costo están deshabilitados hasta que hay repuesto, y si al registrar un renglón tiene datos pero está incompleto no se registra y se marca qué falta. "Cambiar repuesto" limpia cantidad, costo y precio porque eran del repuesto anterior, y así el nuevo se rellena desde su última compra |
| 2026-09-12 | 6 | **Inventario es un módulo propio, no una sección de Compras** | Hoy su kardex solo tiene compras, pero con el mostrador tendrá ventas, devoluciones y ajustes. Es de inventario, no de quien le da entrada. Mismo corte que el car-wash (`PurchasesPage` y `AccessoriesPage` separadas). El historial de compras sí es de Compras, y llega con el spec 0002 |
| 2026-09-12 | 6 | **`GET /api/inventario` es aparte del buscador `GET /api/repuestos?q=`** | Son dos gestos distintos. En la compra, un campo vacío no debe traer el catálogo entero; en el inventario, vacío es "muéstrame todo". Además el inventario encuentra por código parcial y pagina. Con un solo endpoint, uno de los dos quedaba mal |
| 2026-09-12 | 6 | **El resumen se suma en la base y el valor no cuenta como cero lo que no tiene costo** | `sum()` en SQL ignora los null, que es justo lo correcto; `sinCosto` dice cuántos quedaron fuera para que la pantalla lo avise. Los `coalesce` hacen falta: sobre una tabla vacía `sum` da null, y la primera vez que se abre la tienda la tabla está vacía |
| 2026-09-12 | 6 | **La consulta de conteo del inventario va escrita a mano, y el orden termina en el código** | Contar no necesita traer concepto ni categoría. Y sin un desempate único, dos repuestos con igual nombre y marca pueden cambiar de página entre peticiones: uno sale dos veces y otro nunca. La integración compara el conteo contra el listado completo |
| 2026-09-12 | 6 | **El proveedor y la factura de cada movimiento se leen en `compras/infraestructura`, sin puerto** | El kardex guarda `origen_id`, no nombres. Es solo lectura para pintar: no hay regla que proteger ni una segunda implementación que nombrar. Vive en compras porque las tablas son de compras; inventario pide descripciones por id. Una sola consulta para todo el kardex, y **por variante**, porque la misma factura pudo cambiarle el precio a un repuesto y no a otro. Se rompió el filtro por variante a propósito y la prueba falló |
| 2026-09-12 | 6 | **Bug de la fase 5: corregir la ficha borraba la categoría y ponía el stock mínimo en 5** | El formulario arrancaba con la categoría vacía y el mínimo en 5 porque la búsqueda no los devolvía. Como el `PUT` reemplaza la ficha entera, guardar sin tocar nada los pisaba. Ahora la respuesta trae `categoriaId`, `categoria` y `stockMinimo`, y el formulario arranca con ellos |
| 2026-09-12 | 6 | **Los filtros del inventario viven en la URL** (`?q=`, `?bajo=1`, `?p=`) | Al abrir un repuesto y volver, la lista está donde se dejó. Y "lo que hay que pedir" queda como un enlace: `/inventario?bajo=1` |
| 2026-09-12 | 6 | **"Agotado" y "Por pedir" son dos avisos distintos**; con stock suficiente no se pinta nada | Piden cosas distintas: uno es "no se lo prometas al cliente", el otro "anótalo en el pedido". Una etiqueta verde en cada fila sería ruido: el ojo tiene que encontrar las excepciones |
| 2026-09-12 | 6 | **La fecha de la factura se lee como fecha local** (`fechaLocal`) | `new Date('2026-09-01')` es medianoche UTC, que en Colombia es el 31 de agosto: toda factura aparecería un día antes. Probado con `TZ=America/Bogota` |
| 2026-09-12 | 6 | **"Cargando" se deriva: cada respuesta recuerda para qué filtros llegó** | Comparar esa clave con la actual evita prender y apagar estado dentro del efecto (la regla `set-state-in-effect`), y de paso impide que una respuesta que llega tarde pise una búsqueda más nueva |
