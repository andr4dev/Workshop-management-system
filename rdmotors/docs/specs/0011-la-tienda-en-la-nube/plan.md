# Plan 0011 — La tienda en la nube

El **cómo** del [spec 0011](spec.md). Cinco fases, cada una con su checkpoint demostrable, su prueba y su
"romper a propósito". La bitácora del final se llena mientras se implementa.

**Orden de las fases y por qué ese.** Las cuatro primeras se hacen y se prueban **contra la base local**, en el
computador de siempre. Solo la quinta toca cuentas y dinero de nadie. Así, el día que se despliegue, lo único nuevo
es la nube: todo lo demás ya estará probado. Si algo falla ese día, se sabe dónde mirar.

> **La fase 5 no la puedo ejecutar yo.** Crear las cuentas, pegar los secretos y apretar "desplegar" es del usuario.
> Lo que sí hago es dejar los comandos exactos, verificar cada paso con él y probar el resultado. Está dicho aquí
> para que no sorprenda al llegar.

---

## Dónde cae cada pieza nueva

La regla de las cuatro carpetas, aplicada a este spec. Casi todo es **infraestructura**, y eso es una buena señal:
un cambio de dónde corre el sistema no debería tocar las reglas del negocio.

| Pieza | Carpeta | Por qué ahí |
|---|---|---|
| Sacar la copia y registrarla | `domain/…/respaldo/aplicacion/` | Coordina: abre transacción, pide el volcado, guarda la fila |
| Cuándo avisar que hace mucho no se baja una copia | `domain/…/respaldo/dominio/` | Es una regla, con cuerpo y sin Spring |
| Pedirle al mundo un archivo temporal | `domain/…/respaldo/dominio/puerto/` | Lo que el dominio **pide** de afuera; su segunda implementación ya existe (la falsa, en memoria) |
| Servir la pantalla, el Dockerfile, el perfil de nube | `pos/…/infraestructura/` y la raíz | Empaque y arranque: no hay nada de negocio |
| La dirección de salud y la de las tareas | `pos/…/infraestructura/` | Adaptadores de entrada. **El reloj es infraestructura**: el negocio no sabe qué hora es, se la dicen |
| La llave de las tareas | `pos/…/infraestructura/` | Un secreto de despliegue, no una regla |

**Nada nuevo entra a `domain/…/dominio/` salvo el cambio de la regla del aviso.** Si al implementar aparece la
tentación de meter algo de la nube en el dominio, está mal puesto.

---

## Fase 1 · Un solo servidor: la pantalla adentro

Hoy hay dos cosas corriendo: el servidor en un puerto y la pantalla en otro. En la nube hay **una sola dirección**.

### Qué se construye

**Raíz del proyecto**

- `Dockerfile` — en tres etapas, y cada una existe por una razón:
  1. **Node** construye la pantalla (`npm ci && npm run build`).
  2. **Maven** construye el jar, con el resultado de la etapa 1 copiado a `pos/src/main/resources/static/`.
  3. **Java solo** para correr, más `pg_dump` de Postgres 17 instalado (lo necesita la fase 4).
  
  Que Node no esté en la etapa final es el punto: lo que se despliega no lleva herramientas de construcción.
- `.dockerignore` — fuera `node_modules`, `target`, `respaldos`, `.git`. Sin esto la imagen pesa cientos de megas
  de basura y cada despliegue tarda de más.

**`pos` — infraestructura**

- `compartido/infraestructura/ConfiguracionDeLaPantalla` (`WebMvcConfigurer`) — sirve los archivos de la pantalla y,
  **para cualquier ruta que no empiece por `api` y no tenga extensión**, devuelve `index.html`.

  > La trampa está en el "no empiece por `api`". Si la regla se escribe de más, una dirección de la API que no
  > existe devuelve la pantalla con estado 200, y el navegador se queda esperando un JSON que nunca llega. El error
  > aparece meses después, en la pantalla equivocada, y cuesta un día encontrarlo. Por eso tiene prueba propia.
- `src/main/resources/application-nube.properties` — **nuevo perfil**, el que se enciende en la nube:
  - el puerto lo dice el proveedor (`${PORT}`), no el archivo;
  - la base, el usuario y la contraseña salen de variables de entorno;
  - **se apaga el arranque de Docker Compose** — hoy la aplicación intenta levantar el contenedor de Postgres al
    arrancar (`application.properties:38-39`); en la nube ese archivo no existe y no hay Docker;
  - el pool de conexiones baja a 3: la base gratis limita cuántas acepta.
  
  `application.properties` **no se toca**: sigue siendo el de la máquina de desarrollo. Ahí está media
  historia H7 resuelta — volver atrás es no encender el perfil.

### Prueba

- `PosIntegracionTest` nuevo (`pos`): `GET /` y `GET /reportes` devuelven la pantalla; **`GET /api/loquesea`
  devuelve 404 en JSON y no la pantalla**; `GET /assets/nada.js` devuelve 404.
  - En las pruebas no existe `static/` (lo mete el Dockerfile), así que la prueba pone su propio
    `pos/src/test/resources/static/index.html` de mentira. Es suficiente: lo que se está probando es el enrutado,
    no el contenido.

**Romper a propósito:** hacer que la regla del `index.html` también cubra `/api/**` → la prueba del 404 falla.

### Checkpoint

`docker build` y `docker run` en el computador, apuntando a la base local, **en el puerto 8082** (el 8080 es del
car-wash y el 8081 el del desarrollo; nada se toca). Se abre `http://localhost:8082`, se entra, se vende, y
**estando en Reportes se recarga la página y no da "no encontrado"**.

---

## Fase 2 · Los secretos, y la sesión que sobrevive a un reinicio

Esta es la fase corta y la que evita el problema más caro del spec: que el dueño quede por fuera cada pocas horas
sin saber por qué.

### Qué se construye

**`pos` — infraestructura**

- `compartido/infraestructura/seguridad/ClaveDelToken` — nueva propiedad `rdmotors.seguridad.clave-obligatoria`
  (apagada por defecto, encendida en el perfil de nube). Si está encendida y la clave viene vacía, **el sistema no
  arranca** y el mensaje **nombra la variable que falta**.

  > Hoy, si nadie la configura, se inventa una y la guarda en un archivo (`ClaveDelToken.java:37`). Eso es correcto
  > en un computador que siempre es el mismo y hay que **dejarlo como está** para no dañar la instalación local:
  > lo que se agrega es la exigencia, no un cambio de comportamiento.
- `application-nube.properties` — la clave sale de una variable de entorno, y **la cookie de la sesión exige
  HTTPS** (`rdmotors.seguridad.cookie-segura=true`, una propiedad que ya existe y está apagada,
  `TokenDeSesion.java:45`).

### Prueba

- `ClaveDelTokenTest` (`pos`, sin contenedor): con la clave obligatoria y vacía, no arranca y **el mensaje nombra
  la variable**; con la clave puesta, arranca; una clave corta se sigue rechazando.
- `SeguridadIntegracionTest` — un caso más: con la cookie segura encendida, la cookie de la sesión sale marcada
  para HTTPS. Y el que ya existe, que **no sale marcada** por defecto, se queda: es el de la tablet del almacén.

**Romper a propósito:** quitar la exigencia y dejar que invente la clave → la prueba de arranque falla.

### Checkpoint

Se arranca el contenedor con el perfil de nube **sin** la clave: no arranca y la línea del registro dice qué falta.
Se arranca con la clave, se entra desde el celular por la red local, **se reinicia el contenedor**, y el celular
sigue adentro sin volver a escribir la contraseña.

---

## Fase 3 · El reloj de afuera

Un servidor que duerme no cuenta las horas. El reloj se saca del servidor y se pone en un servicio de afuera.

### Qué se construye

**`pos` — infraestructura**

- `compartido/infraestructura/SaludController` — `GET /api/salud`, público, sin sesión. Pregunta a la base si
  responde y contesta sí o no. **No dice nada más**: ni versión, ni nombre de la base, ni cuántas ventas hay. Es la
  dirección que va a estar llamando un servicio de afuera cada cinco minutos, a la vista de internet entero.
- `tareas/infraestructura/TareasController` — `POST /api/tareas/correos`, que hace lo mismo que hace hoy la tarea
  del minuto. Pide una llave en un encabezado.
- `tareas/infraestructura/LlaveDeTareas` — compara la llave **en tiempo constante**, y si no cuadra responde
  **"no existe"**, no "no autorizado".

  > La diferencia importa. "No autorizado" le dice a quien esté probando que ahí sí hay algo y que solo le falta la
  > llave. "No existe" no le dice nada. Es gratis y cierra una puerta.
- `ConfiguracionSeguridad` — las dos direcciones nuevas son públicas, y la de tareas **queda fuera de la revisión
  contra peticiones falsificadas**: quien la llama es una máquina sin navegador y sin cookie, no puede tener ese
  token.
- La tarea del minuto (`correo/infraestructura/TareaDeCorreos.java:29`) **se queda**. Cuando el servidor está
  despierto, manda sola; cuando estuvo dormido, la llamada de afuera la despierta. Las dos hacen lo mismo y hacerlo
  dos veces no duplica nada (`MandarCorreosPendientes.java:34-40`) — por eso se puede tener cinturón y tirantes sin
  pagar nada.

### Prueba

- `TareasIntegracionTest` (`pos`, con contenedor y cliente HTTP de verdad):
  - sin llave → "no existe", y **ningún correo se movió**;
  - con llave equivocada → lo mismo;
  - con la llave → manda, y lo dice;
  - **llamarla dos veces seguidas no manda el mismo correo dos veces** (el criterio de aceptación);
  - `GET /api/salud` sin sesión → responde, y **su respuesta no contiene ningún dato del negocio**.

**Romper a propósito:** responder "no autorizado" en vez de "no existe" → falla la prueba. Comparar la llave con
una comparación normal → falla la prueba de tiempo constante.

### Checkpoint

Con el contenedor corriendo, desde la consola: la llamada sin llave no hace nada, la llamada con llave saca los
correos pendientes, y la dirección de salud responde sin haber entrado.

---

## Fase 4 · El respaldo que se baja

La fase más grande, y la única que **quita** más de lo que pone.

### Qué se borra

| Se va | Por qué |
|---|---|
| La tarea de las 2 a. m. (`respaldo/infraestructura/TareaDeRespaldo`) | Ya no hay dónde dejar el archivo |
| La poda de copias viejas, la segunda carpeta y la copia de la clave de sesiones | No hay carpeta, no hay USB, y la clave ahora es una variable de entorno (fase 2) |
| `respaldo/dominio/DondeVaElRespaldo` | Se queda sin nada que decir |
| De la configuración: la carpeta, la segunda carpeta, los días que se guardan, el archivo de la clave | Sobra todo salvo dónde está `pg_dump` |

### Qué se construye

**Esquema**

- **V25** — quita de la tabla de respaldos lo que dejó de existir: la segunda copia, su aviso, y la marca de
  archivo borrado, con su regla asociada. La columna del archivo se queda, pero ahora guarda **el nombre del
  archivo que se bajó**, no una ruta de un disco.
  - Migración nueva, nunca editar una aplicada.
  - **Funciona sobre una base con filas**: las de QA tienen datos en esas columnas y se pierden a propósito — son
    el registro de copias que ya no existen.

**`domain`**

- `respaldo/dominio/Respaldo` — pierde tres campos y sus métodos.
- `respaldo/dominio/PoliticaDeRespaldo` — pierde la poda y los días que se guardan; **gana la regla nueva**: se
  avisa si hace más de **siete días** que no se baja una copia. El nombre del archivo se queda como está.
- `respaldo/dominio/puerto/Archivos` — pierde copiar y mirar carpetas; gana pedir un archivo temporal y saber
  cuánto pesa.
- `respaldo/aplicacion/HacerRespaldo` → **`BajarRespaldo`**: saca la copia a un archivo temporal, comprueba que el
  motor terminó bien, registra la fila y **entrega el archivo**. Si falla, registra el fallo, borra lo que quedó a
  medias y no entrega nada.
- `respaldo/aplicacion/ConsultarRespaldos` y `EstadoDelRespaldo` — sin carpetas, sin bytes en disco, sin conteo de
  copias. Lo que queda: cuándo fue la última, cuántos días van, y si hay que avisar.

**`pos` — infraestructura**

- `respaldo/infraestructura/RespaldoController` — el `POST` que hacía la copia se convierte en la descarga:
  transmite el archivo al navegador con su nombre y **lo borra al terminar**, salga bien o mal.

  > **Por qué a un archivo temporal y no directo al navegador.** Mandar lo que va saliendo del motor sería más
  > elegante y usaría menos memoria. Pero si el motor falla a la mitad, el navegador ya recibió media respuesta
  > "correcta": queda un archivo truncado que parece un respaldo. El spec lo prohíbe explícitamente (§6). Se paga
  > con memoria —el contenedor necesita 512 MB— y se compra no poder entregar una copia rota.
- `respaldo/infraestructura/ConfiguracionDeRespaldo` — se reduce a dónde está `pg_dump` y en qué carpeta trabajar.
- `respaldo/infraestructura/ArchivosDelDisco` — se reduce con el puerto.
- `compartido/infraestructura/ConfiguracionCasosDeUso` — el cableado, que cambia con los casos de uso.

**`frontend`**

- `utils/respaldo.js` (+ su prueba) — el aviso pasa a ser *"hace N días que no bajas una copia"*; fuera los textos
  de carpeta, poda y segunda copia.
- `paginas/Respaldo.jsx` — de "lista de copias en el disco" a **un botón grande que baja el archivo** y una lista
  que es historia: cuándo se bajó cada una, cuánto pesó, y las que fallaron con su motivo.
- `componentes/AvisoDeRespaldo.jsx` — el mismo aviso de arriba, con el texto nuevo.

**Documentos**

- `docs/RESPALDO_Y_RESTAURAR.md` — reescrito: ya no hay carpeta ni 2 a. m.; hay un botón, y la restauración se
  prueba con el archivo bajado.

### Prueba

- `BajarRespaldoTest` (`domain`, con los falsos): entrega el archivo y registra la fila; **si el volcado falla, no
  entrega nada, registra el fallo y borra lo que quedó**; el cajero no puede.
- `PoliticaDeRespaldoTest` — las pruebas de poda se borran; entra la de los siete días. Las de nombre de archivo y
  la de "dos en el mismo segundo no se pisan" se quedan.
- `ConsultarRespaldosTest` — sin carpetas ni conteo de disco; con los días sin bajar.
- `RespaldoIntegracionTest` (`pos`, con contenedor) — **la descarga trae un archivo que `pg_restore` sabe leer**, y
  el temporal no queda en el equipo después.
- `frontend`: `respaldo.test.js` actualizado.

**Romper a propósito:** entregar el archivo sin mirar si el motor terminó bien → falla la prueba del volcado
fallido. No borrar el temporal → falla la de integración.

### Checkpoint

**El de verdad, el que el spec exige:** se baja el archivo desde la pantalla, se levanta un Postgres vacío aparte,
se restaura ahí, y **el conteo de repuestos, el stock total y la cartera por cobrar son idénticos** a los que
mostraba la pantalla antes de bajarlo. Si no coinciden, la fase no está hecha.

---

## Fase 5 · A la nube de verdad

Lo que no se puede probar en el computador. **Esta fase la ejecuta el usuario**; yo dejo los pasos exactos,
verifico cada uno y pruebo el resultado.

### El orden, que importa

1. **La base primero**, vacía. Se guardan sus datos de conexión.
2. **Los secretos**: la clave que firma las sesiones (generada al azar, una sola vez), la llave de las tareas, los
   datos de la base, y los del correo que ya existían.
3. **El primer despliegue**, con el perfil de nube. Las migraciones corren solas sobre la base vacía.
4. **El primer administrador**, desde la pantalla, como en una instalación nueva. Y ahí mismo, **el usuario del
   dueño**, que es el objetivo de todo esto.
5. **Las llamadas programadas**, al final: dos servicios distintos tocando la dirección de salud cada cinco
   minutos en horario de almacén, y uno llamando la de correos con su llave.
6. **Los datos de la tienda** (nombre, NIT, dirección) y los destinatarios del correo del cierre.

### Las pruebas, contra el sitio de verdad

- Desde un **celular con datos móviles, fuera del almacén**: entrar y crear un repuesto con costo y precio.
- Desde otro dispositivo: ese repuesto está.
- Recargar estando en Reportes: no da "no encontrado".
- Con sesión de **cajero**: recorrer las consultas y confirmar que **no aparece ni un costo** — lo mismo que ya se
  probó en local, repetido en la nube porque es lo que más caro sale si se rompió al mudarse.
- Una venta a las **11 de la noche hora de Colombia** aparece en el reporte de ese día.
- Un **cierre de caja de noche** llega al correo del dueño después de que el servidor se durmió.
- Bajar una copia **desde el celular** y comprobar que el archivo llegó completo.
- Revisar los registros del servidor y las respuestas de la API: **ningún secreto**.

### Los documentos que dejan de mentir

- `docs/SIN_INTERNET.md` — se reescribe. Hoy su primera línea dice lo contrario de lo que será cierto.
  **No se borra**: pasa a decir qué se cae exactamente sin internet y cómo se sigue vendiendo (el cuaderno, el plan
  de datos del celular).
- `docs/RESPALDO_Y_RESTAURAR.md` — ya reescrito en la fase 4; aquí se verifica contra el sitio real.
- `docs/DESPLIEGUE.md` — **nuevo**: dónde vive el sistema, qué secreto va en cada variable, cómo se despliega una
  versión nueva, qué hacer si algo se cae, y **cómo se vuelve atrás al computador del almacén** (RF-018).
- `docs/specs/0009-…/spec.md` — una nota arriba: esto se pensó para un sistema local y explica por qué el código
  está como está. **No se reescribe**: fue cierto.
- `docs/PLAN_DE_TRABAJO.md` — la rebanada 5 cambia de papel y la nube deja de ser un espejo.
- `docs/specs/README.md` — el estado de este spec.
- La memoria del proyecto: la arquitectura ya no es "verdad local y espejo en la nube".

### Checkpoint

El dueño carga repuestos desde su celular, de noche, con el almacén cerrado. Que es lo que pidió.

---

## Verificación, al cerrar cada fase

1. Backend abajo, `./mvnw clean test` — dominio, Postgres de verdad, migraciones y las pruebas de integración.
   **Antes de probar `pos`, instalar `domain`**: `./mvnw -o -pl domain install -DskipTests`, o `pos` compila contra
   un `domain` viejo y las fallas no tienen sentido.
2. `cd frontend && npx eslint src/ && node --test src/utils/*.test.js && npx vite build`.
3. El checkpoint de la fase, en el navegador de verdad.
4. El "romper a propósito" de la fase: cada cambio hace fallar **su** prueba, y solo esa.
5. Capturas de la pantalla que cambió, en claro, oscuro y 390 px de ancho.

---

## Riesgos conocidos de la implementación

| Riesgo | Cómo se ve si pasa | Qué hacer |
|---|---|---|
| **`pg_dump` de otra versión que el motor** | La descarga falla con un mensaje del motor sobre versiones | La etapa final del contenedor instala el cliente 17, igual que el motor. Verificarlo en la fase 1, no en la 4 |
| **El contenedor intenta levantar Docker Compose al arrancar** | No arranca, y el error habla de un archivo que no existe | Apagarlo en el perfil de nube (fase 1). Es la primera cosa que falla al desplegar |
| **La regla del `index.html` se come las direcciones de la API** | Peticiones que devuelven una página en vez de un JSON, en pantallas al azar | La prueba del 404 de la fase 1 |
| **La base gratis limita las conexiones** | Errores intermitentes de "no hay conexiones" con dos personas usando | Pool de 3 en el perfil de nube |
| **El archivo temporal de la copia gasta memoria del contenedor** | El contenedor se reinicia solo al bajar una copia | 512 MB de memoria y vigilar cuánto pesa la copia cuando el inventario esté cargado |
| **La cookie marcada para HTTPS rompe el desarrollo local** | Nadie puede entrar en el computador | La propiedad solo se enciende en el perfil de nube; `application.properties` no se toca |

---

## Bitácora

Las decisiones que se toman **durante** la implementación, con su fecha y su porqué. Se llena al andar.

| Fecha | Fase | Decisión | Por qué |
|---|---|---|---|
| 2026-09-23 | — | Plan escrito y entregado | — |
| 2026-09-23 | 1 | La pantalla se sirve con un resolutor propio, no forzando `spring.web.resources` | Hacía falta distinguir tres casos: un archivo que existe, una ruta de la pantalla, y una dirección de la API que no existe. Lo último es la trampa: `/api/instalacion/**` es público, así que una petición ahí llega hasta el final sin controlador y habría devuelto la página con estado 200 |
| 2026-09-23 | 2 | El "romper a propósito" de la comparación en tiempo constante **no se puede escribir como prueba** | El plan lo prometía y era falso: una prueba no puede medir microsegundos de forma fiable. Se prueba que la llave equivocada se rechaza; que se rechace *tardando lo mismo* queda como propiedad del código, escrita en `LlaveDeTareas` |
| 2026-09-23 | 3 | La respuesta sin llave es "no existe", no "no autorizado" | "No autorizado" le confirma a quien prueba que ahí hay algo y que solo le falta la llave. Cuesta lo mismo no decirlo |
| 2026-09-23 | 3 | La tarea del minuto se queda, además del disparo de afuera | Hacen lo mismo y repetirlo no duplica correos, así que tener las dos no cuesta nada: una cubre el servidor despierto y la otra el dormido |
| 2026-09-23 | 4 | `BajarRespaldo` **no abre transacción**, al revés que el resto de casos de uso | Si la abriera, al fallar el volcado se desharía justo la fila que dice que falló — lo único que hace que el dueño se entere. De paso se evita tener una conexión tomada durante todo el volcado, con un pozo de tres |
| 2026-09-23 | 4 | **Bug encontrado al implementar:** bajar el respaldo se negaba a sí mismo a media descarga | Entregar el archivo mientras se lee hace que el servidor atienda en dos tramos; el guardia de permisos de Spring corre en los dos, pero nuestro filtro de sesión solo en el primero. El archivo llegaba completo porque la respuesta ya iba en camino, y el error solo salía en el registro. Con una copia más pequeña o con otra red, al dueño le habría llegado un "no permitido" en vez de su respaldo. Corregido en `FiltroDeSesion`, con su prueba |
| 2026-09-23 | 4 | El enum `OrigenRespaldo` conserva `AUTOMATICO` aunque nada lo escriba | Las filas de antes de la V25 lo tienen, y era verdad cuando se escribieron. Quitarlo obligaría a reescribir historia por cosmética |
| 2026-09-23 | 4 | **Bug encontrado al correr la imagen:** bajar la copia fallaba con *"no such file"* | El perfil de nube no cambiaba la ruta de `pg_dump`, y adentro del contenedor no existe la de Windows. Era el primer riesgo de la tabla de arriba y apareció exactamente ahí. Corregido en `application-nube.properties` |
| 2026-09-23 | 4 | El fallo del respaldo responde **503 con el mensaje del motor**, no un 500 pelado | Lo destapó el bug anterior: el dueño habría visto *"El servidor falló (500)"*, que no dice nada y no deja arreglar nada. Manejador nuevo en `ManejadorDeErrores`, con su clase de prueba (`RespaldoQueFallaIntegracionTest`) |
| 2026-09-23 | — | **Prueba vieja y frágil, arreglada de paso:** `ClientesYFiadoIntegracionTest.idaYVuelta` | Falló en la suite completa sin tener nada que ver con este spec. Probaba que los comodines de búsqueda no se interpretan usando `"100%_"`, pero eso también se lee como cédula y ahí los signos se quitan y queda `100`: fallaba sola el día que a una cédula al azar le tocaba empezar por 100. Ahora usa letras (`"jos%"`), que no se parecen a ningún documento. Se corrió tres veces seguidas para confirmarlo |
| 2026-09-23 | 5 | **La estimación de arranque del spec estaba mal por un orden de magnitud** | Se dijo "5 a 15 segundos" pensando en Cloud Run. Simulando Render gratis (`--memory=512m --cpus=0.1`) contra Neon el arranque fue de **258 s**; con banderas de JVM en el `Dockerfile` (`TieredStopAtLevel=1`, `UseSerialGC`…) baja a **139 s**. Consecuencia: el cron de salud va las 24 horas, no solo en horario de almacén (el dueño carga inventario de noche) |
| 2026-09-23 | 5 | Render en la región **Ohio**, no Oregon | La base de Neon está en `us-east-2` (Ohio). Servidor y base en la misma región: milisegundos por consulta en vez de decenas |
| 2026-09-23 | 5 | Hay que poner **Root Directory = `rdmotors`** en Render | El repositorio tiene el proyecto en una subcarpeta; sin eso Render no encuentra el `Dockerfile` |
| 2026-09-23 | 5 | El repositorio de GitHub es **público** | Se revisó que no haya secretos ni contraseñas de QA (ninguna). Queda la decisión de si el código de un cliente debería ser privado; Render funciona con los dos |
| 2026-09-23 | 5 | Casi se excluyen las migraciones de Flyway del repositorio | Un `*.sql` en el `.gitignore` de la raíz habría dejado fuera las 25. Se detectó antes del commit y se acotó a `/respaldos/` |
| 2026-09-23 | 5 | **El riesgo nº 1 de la tabla se cumplió, y en el peor sitio**: `pg_dump` 17 contra una base 18.6 | Neon resultó correr PostgreSQL **18.6**, no 17. El plan decía "verificarlo en la fase 1, no en la 4" y no se hizo: se descubrió al bajar la primera copia en producción. Corregido a `postgresql-client-18` en el `Dockerfile`. La regla real no es "misma versión mayor" sino **el cliente nunca más viejo que el servidor** — al revés sí funciona |
| 2026-09-23 | 5 | Las copias de producción **solo se restauran con `pg_restore` 18 o superior** | Consecuencia de lo anterior: el formato lo marca el servidor. El computador de desarrollo tiene el 17, así que para restaurar una copia de producción hay que instalar el 18. Documentado en `RESPALDO_Y_RESTAURAR.md` |

## Estado al 2026-09-23

**Fases 1 a 4: hechas y verificadas.** La 5 es la del usuario.

| | Estado |
|---|---|
| Pruebas | 487 del dominio + 149 de `pos`, todas en verde · frontend: eslint limpio, 294 pruebas, build bien |
| "Romper a propósito" | 5 rupturas, cada una hizo fallar **su** prueba y solo esa |
| Imagen | `rdmotors:0011`, 680 MB, construida y corriendo |
| Checkpoint | 18 de 18 comprobaciones contra el contenedor de verdad |
| Restaurar | Copia bajada y restaurada en una base vacía: productos, variantes, stock, ventas, usuarios y cartera **idénticos**, con las 25 migraciones |
| Reinicio | Se reinició el contenedor y la misma cookie siguió valiendo: nadie tuvo que volver a entrar |
| Sin la clave | El contenedor **no arranca**, y el registro nombra `RDMOTORS_CLAVE_SESIONES` |

**Lo que falta, todo de la fase 5:** crear las cuentas, desplegar, configurar los dos crones, crear el primer
administrador y el usuario del dueño, probarlo desde un celular con datos, y corregir `SIN_INTERNET.md` y
`PLAN_DE_TRABAJO.md`, que hoy siguen describiendo un sistema que vive en el computador del almacén.
