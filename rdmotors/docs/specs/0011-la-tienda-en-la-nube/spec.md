# Spec 0011 — La tienda en la nube

> **Este spec le da la vuelta a la decisión más vieja del proyecto.** Hasta hoy la verdad del negocio vivía en el
> computador del almacén y la nube era, cuando llegara, un espejo de solo lectura. Aquí la verdad se muda a la nube
> y el almacén pasa a ser un cliente más. No es un ajuste de despliegue: cambia qué pasa cuando se cae el internet.
> La sección 4 lo dice con todas sus consecuencias.

---

## 1. Objetivo de negocio

El dueño pidió usuario y contraseña **para cargar su inventario base desde el celular**, en las noches, cuando el
almacén está cerrado. Hoy eso es imposible: el sistema solo existe mientras el computador del almacén esté
encendido, y ese computador se apaga al cerrar.

Cargar el inventario base no es un capricho: es el trabajo que tiene que estar hecho **antes** de que el almacén
pueda vender con el sistema. Son cientos de referencias con su costo y su precio. Mientras no exista, el sistema no
se puede estrenar.

Y hay una segunda razón, más de fondo. El almacén **todavía no vende con el sistema**. No hay un mostrador que
proteger, ni ventas que se pierdan si algo falla. Es el único momento del proyecto en que mudar la fuente de verdad
cuesta barato: hoy no hay nada que migrar. Dentro de tres meses, sí.

El internet del almacén es **estable, casi nunca falla** (respuesta del dueño, 2026-09-23). Ese dato es el que hace
viable la mudanza — y el que hay que vigilar, porque pasa a ser el eslabón más débil de todo el sistema.

---

## 2. Casos de uso

| # | Historia | Prioridad | Qué se puede demostrar solo |
|---|---|---|---|
| **H1** | El dueño entra desde su celular, en la calle, con datos móviles, y carga repuestos | **P1** | Se abre la dirección en un celular fuera del almacén, se entra y se crea un repuesto con su costo y precio |
| **H2** | El sistema responde cuando alguien lo usa, aunque lleve horas sin que nadie lo toque | **P1** | Se abre la dirección a las 9 p. m. y a las 7 a. m. y responde sin que nadie haya hecho nada |
| **H3** | El dueño se puede llevar una copia de su negocio cuando quiera | **P1** | Se baja el archivo desde la pantalla y se restaura en un Postgres vacío: mismos repuestos, misma cartera |
| **H4** | Lo que tiene que pasar solo, pasa solo, aunque no haya nadie despierto | **P1** | Un cierre de caja de las 8 p. m. llega por correo aunque el servidor se haya dormido después |
| **H5** | Nadie pierde la sesión porque el servidor se reinició | **P1** | Se reinicia el servidor y el celular que estaba adentro sigue adentro |
| **H6** | Quien opera el sistema sabe qué se cae con el internet y qué no | **P2** | Los documentos dicen la verdad: hoy dicen lo contrario |
| **H7** | Si la nube no sirve, el sistema se puede devolver al computador del almacén | **P3** | El mismo empaque arranca contra la base local sin tocar código |

---

## 3. Qué existe hoy

Todo lo de esta tabla está verificado en el código, hoy, 2026-09-23.

### Lo que hay que cambiar

| Hecho | Dónde | Por qué estorba en la nube |
|---|---|---|
| La base está escrita a mano y apunta al mismo computador | `pos/src/main/resources/application.properties:5-7` | En la nube la base está en otra parte y su dirección es un secreto |
| El puerto está fijo en 8081 | `application.properties:32` | El proveedor de la nube dice en qué puerto escuchar; no se elige |
| **La pantalla la sirve una herramienta de desarrollo**, no el servidor | `frontend/vite.config.js:6-19` | En la nube no hay dos cosas corriendo: el servidor tiene que servir la pantalla |
| El servidor **no lleva la pantalla adentro**: solo tiene la configuración, las migraciones y nada más | `pos/src/main/resources/` (solo `META-INF`, `application.properties`, `db`) | Hay que empacarla |
| **No hay empaque de contenedor en ningún lado del repositorio** | ningún `Dockerfile` en el proyecto | Es lo primero que pide cualquier nube |
| **La clave que firma las sesiones se inventa al azar y se guarda en un archivo del disco** si nadie la configuró | `pos/…/seguridad/ClaveDelToken.java:34-43` y `:49-59` | En la nube ese archivo se borra en cada reinicio: **cada reinicio sacaría a todo el mundo** |
| La cookie de la sesión **no exige HTTPS** por defecto | `pos/…/seguridad/TokenDeSesion.java:45` y `:76` | Por internet, una cookie de sesión sin HTTPS se puede robar en el camino |
| El respaldo **escribe archivos en una carpeta del disco** y los va podando | `pos/…/respaldo/infraestructura/ConfiguracionDeRespaldo.java:26`, `ArchivosDelDisco.java` | El disco del servidor en la nube es prestado y se borra: la pantalla diría "14 copias" y no habría ninguna |
| La copia la saca **un programa aparte** que tiene que estar instalado en el equipo | `pos/…/respaldo/infraestructura/VolcadorPgDump.java:47-51` | Hay que meterlo en el empaque, y con la misma versión del motor |
| Las dos cosas que pasan solas **dependen del reloj de adentro del servidor** | `pos/…/respaldo/infraestructura/TareaDeRespaldo.java:33`, `pos/…/correo/infraestructura/TareaDeCorreos.java:29` | Un servidor dormido no tiene reloj: a las 2 a. m. no hay nadie que cuente la hora |
| Hacer el respaldo automático **no pregunta si ya hay uno de hoy** | `domain/…/respaldo/aplicacion/HacerRespaldo.java:65-67` | Si lo dispara algo de afuera y se repite, salen dos copias |

### Lo que ya está bien y hay que no dañar

| Hecho | Dónde |
|---|---|
| La copia se conecta **a la base de verdad de la aplicación**, no a la que diga un archivo de texto | `pos/…/respaldo/infraestructura/DatosDeConexion.java:27-43` |
| Mandar los correos pendientes **se puede repetir sin daño**: toma de a diez, con candado, y cada uno guarda su estado | `domain/…/correo/aplicacion/MandarCorreosPendientes.java:34-40` |
| La regla "**falta la copia de hoy**" ya existe y ya está probada | `domain/…/respaldo/dominio/PoliticaDeRespaldo.java`, probada en `PoliticaDeRespaldoTest.faltaLaDeHoy` |
| Toda la API exige haber entrado, salvo entrar, salir y crear el primer administrador | `pos/…/seguridad/ConfiguracionSeguridad.java:71-78` |
| Las escrituras exigen además el token contra peticiones falsificadas | `ConfiguracionSeguridad.java:65` |
| **La hora de Colombia está escrita, no heredada** del sistema operativo: un servidor en otro país calcula igual | `pos/…/RelojSistema.java:19`, `domain/…/reportes/dominio/Periodo.java:25`, `domain/…/correo/aplicacion/CorreoDelCierre.java:35` |
| La pantalla **no trae nada de internet** —ni fuentes, ni íconos, ni librerías— y hay una prueba que lo vigila | `frontend/src/utils/sinInternet.test.js` |
| El secreto del correo sale de una variable de entorno, nunca del archivo ni de la base | `application.properties:67` |
| La sesión no vive en la memoria del servidor: es un token firmado que viaja en la cookie | `TokenDeSesion.java:52-63` |

> **Lo último de esa tabla es lo que hace posible todo este spec.** Un sistema que guardara la sesión en la memoria
> del servidor no podría dormirse nunca. Este se puede apagar y prender sin que nadie se entere — siempre que la
> clave que firma no cambie, que es justamente el problema de la fila roja de arriba.

### Lo que hoy dice el proyecto y va a dejar de ser cierto

| Documento | Qué afirma | Después de esto |
|---|---|---|
| `docs/SIN_INTERNET.md:3-4` | "Sin internet, la tienda trabaja igual" | Falso: sin internet no se vende |
| `docs/SIN_INTERNET.md:13-18` | La base y el servidor viven en el computador del almacén | Falso |
| `docs/RESPALDO_Y_RESTAURAR.md:3-5` | "Todo el negocio vive en una sola base de datos, en el computador de la tienda" | Falso |
| `docs/specs/0009-…/spec.md` | La rebanada entera se pensó para un sistema local | Se marca, no se borra: fue cierto y explica por qué el código está como está |
| `docs/PLAN_DE_TRABAJO.md:14` | La nube llega en la rebanada 5, como espejo | Se adelanta y cambia de papel |

---

## 4. Las decisiones

### Decisión 1 — Qué es un respaldo cuando la base ya no es nuestra · **la que cambia el alcance**

Hoy el respaldo es: *un programa saca una copia de la base y la deja en una carpeta del computador, todas las
noches, y se guardan catorce días*. En la nube, cada pieza de esa frase deja de sostenerse.

| Camino | Qué cuesta |
|---|---|
| **A. Dejarlo igual**, escribiendo en el disco del servidor | **No sirve, y miente.** El disco de un servidor que duerme es prestado: se borra en cada reinicio, y en el proveedor recomendado ni siquiera es disco, es memoria del propio servidor. La pantalla mostraría catorce copias y no existiría ninguna. Un respaldo que no está es peor que no tener respaldo: nadie busca lo que cree tener |
| **B. La copia se baja en el momento**: el administrador aprieta el botón y el archivo llega a su computador o a su celular. En el servidor no queda nada | Se reescribe la pantalla de Respaldo y desaparece la poda de copias viejas. **Quita código, no lo agrega** |
| **C. Guardar las copias en un almacén de archivos en la nube** | Otra cuenta, otro secreto, otro adaptador y otra cosa que puede fallar de noche sin que nadie mire |

**Recomendación: B**, y hay que decir por qué se puede.

La pregunta de fondo no es "¿dónde guardamos el archivo?" sino **"¿de qué nos está protegiendo el respaldo?"**. En
el computador del almacén protegía de que ese computador se dañara, se lo robaran o se quemara — y era lo único que
protegía de eso. En la nube, el proveedor de la base ya guarda **su propia historia** y sabe devolverla a un punto
en el tiempo: eso cubre el disco dañado, el borrado por error y la base corrupta, que es de lo que protegía la copia
de las 2 a. m.

Lo que el proveedor **no** cubre es distinto: que se acabe la cuenta gratis, que el proveedor cierre, o que el dueño
simplemente quiera llevarse sus datos. Para eso sirve una copia en las manos del dueño — y esa no tiene que ser
automática de madrugada: tiene que ser **fácil de bajar** y tiene que estar **probada al restaurar**.

Entonces el respaldo automático de cada noche **se elimina** y en su lugar queda:

- Un botón que baja la copia completa, al momento.
- Un aviso al administrador si lleva más de una semana sin bajar ninguna.
- El documento de restauración corregido y **probado de verdad** con un archivo bajado.

> Esto es lo que más incomoda de este spec: estamos **quitando** una funcionalidad terminada hace dos días. Pero la
> alternativa es dejarla puesta sabiendo que no funciona, y eso no es una funcionalidad: es un adorno que dice
> "estás respaldado" cuando no es cierto.

### Decisión 2 — Quién despierta al sistema y quién dispara lo de la madrugada

El plan gratis del proveedor apaga el servidor cuando nadie lo usa, y lo prende cuando alguien llega: la primera
persona después de un rato espera entre cinco y quince segundos. Y mientras duerme **no hay reloj adentro**, así que
la tarea de las 2 a. m. y la de los correos sencillamente no ocurren.

**Un llamado desde afuera, cada pocos minutos, resuelve las dos cosas a la vez** (decisión del usuario, 2026-09-23):

- **Para que no se duerma**: un servicio gratuito de llamadas programadas toca una dirección barata del sistema cada
  cinco minutos, en horario de almacén. Mientras le lleguen llamadas, el servidor no se apaga, y quien llega a
  vender no espera.
- **Para lo que tiene que pasar solo**: ese mismo servicio llama, con una llave, la dirección que saca los correos
  pendientes. **El reloj deja de estar adentro del servidor y pasa a estar afuera**, que es como se hace cuando el
  servidor puede dormirse.

Lo que **no** garantiza, dicho sin adornos: si una llamada se pierde justo cuando llega un cliente, ese cliente
espera. Se reduce poniendo **dos servicios distintos** —que fallen los dos a la vez ya es raro— pero no desaparece.
La alternativa que sí lo garantiza cuesta unos 25.000 a 35.000 pesos al mes y **es cambiar un número**, no rehacer
nada: el trabajo de disparar las tareas desde afuera sigue sirviendo igual.

Lo que se construye queda documentado como **una decisión reversible**, no como una restricción del diseño.

### Decisión 3 — De dónde sale la clave que firma las sesiones

Hoy, si nadie la configura, el sistema **se inventa una** y la guarda en un archivo (`ClaveDelToken.java:37`). Eso
está bien en un computador que siempre es el mismo. En la nube ese archivo se borra en cada reinicio, y en el plan
gratis los reinicios son varios al día: **el dueño quedaría por fuera cada pocas horas**, sin ningún mensaje que
explicara por qué.

**Decisión: en la nube la clave es obligatoria y el sistema no arranca sin ella.** Nada de inventarla en silencio.
Un sistema que arranca mal y falla raro tres horas después cuesta mucho más que uno que no arranca y dice qué le
falta.

### Decisión 4 — Con qué datos arranca la nube

La base de QA tiene datos de prueba: proveedores inventados, un cliente llamado "Julio motors", ventas que no
existieron. **No se migra nada.** La nube arranca vacía, se crea el primer administrador desde la pantalla —como en
una instalación nueva— y se le crea su usuario al dueño. El inventario base lo carga él, que es exactamente lo que
pidió.

---

## 5. Requisitos funcionales

**Entrar por internet**

- **RF-001** — El sistema se abre desde cualquier teléfono o computador con internet, sin instalar nada y sin
  configurar una red privada.
- **RF-002** — La pantalla y el servidor son **una sola dirección**. Recargar la página estando en cualquier
  pantalla del sistema —caja, reportes, inventario— vuelve a esa pantalla; no da "no encontrado".
- **RF-003** — La dirección es HTTPS y **la cookie de la sesión solo viaja por HTTPS**.
- **RF-004** — Reiniciar el servidor **no saca a nadie**: quien estaba adentro sigue adentro.
- **RF-005** — Si falta un secreto necesario, el sistema **no arranca** y deja escrito cuál falta. No inventa
  ninguno.

**Que esté despierto y que lo de la madrugada pase**

- **RF-006** — Hay una dirección pública, sin sesión, que responde si el sistema y la base están vivos. **No dice
  nada del negocio**: ni cuántas ventas hay, ni qué versión corre, ni qué base usa.
- **RF-007** — Las tareas que antes corrían solas se pueden **disparar desde afuera con una llave**. Sin llave, o
  con una llave equivocada, no pasa nada y la respuesta no explica por qué.
- **RF-008** — Disparar una tarea **dos veces no duplica nada**: ni correos repetidos, ni copias de más.
- **RF-009** — Los correos del cierre siguen saliendo aunque el servidor se haya dormido después del cierre.

**La copia del negocio**

- **RF-010** — El administrador baja la copia completa de la base desde la pantalla, en el momento, a su equipo.
- **RF-011** — La pantalla dice **cuándo fue la última vez que se bajó una copia**, y avisa si lleva más de una
  semana sin bajar ninguna.
- **RF-012** — El archivo bajado se restaura en un Postgres vacío y queda el mismo negocio: mismos repuestos, mismo
  stock, misma cartera, mismos usuarios.
- **RF-013** — Ya no quedan copias guardadas en el servidor, ni carpetas, ni poda de copias viejas: lo que el
  administrador no baja, no existe.

**Que nada se rompa por mudarse**

- **RF-014** — Los días siguen siendo **días de Colombia** aunque el servidor esté en otro país: una venta de las
  11 p. m. cuenta en el día que la hizo el cajero, no en el siguiente.
- **RF-015** — Ningún secreto —la llave del correo, la contraseña de la base, la clave que firma las sesiones—
  aparece en la pantalla, en una respuesta de la API ni en los registros del servidor.
- **RF-016** — Quién puede qué **no cambia**: lo del administrador sigue siendo del administrador y el cajero sigue
  sin ver costos.

**Decir la verdad**

- **RF-017** — Los documentos que hoy afirman que el sistema trabaja sin internet se corrigen, y dicen qué se cae
  exactamente cuando el almacén se queda sin internet.
- **RF-018** — Queda escrito, en un solo lugar, **cómo se vuelve atrás**: qué se cambia para que el mismo empaque
  corra contra la base del computador del almacén.

---

## 6. Manejo de errores

| Qué pasa | Qué hace el sistema | Qué ve la persona |
|---|---|---|
| El servidor estaba dormido y alguien llega | Se prende y atiende | La pantalla tarda unos segundos en abrir. **No un error**: si tarda, tiene que verse que está cargando, no que se rompió |
| La base estaba dormida | Se conecta igual, esperando lo que tarde en despertar | Lo mismo: demora, no error |
| La base no responde | Responde "no disponible" y **no pierde la operación a medias** | *"El sistema no está disponible en este momento. Intenta de nuevo en unos segundos"* |
| Falta la clave que firma las sesiones | **No arranca** | En el registro del arranque, una línea que dice qué falta y dónde ponerlo |
| Alguien llama la dirección de las tareas sin llave | No hace nada y no explica | Una respuesta seca, sin pista de qué había que mandar |
| El programa que saca la copia no está o es de otra versión | La descarga falla con el mensaje del motor | *"No se pudo sacar la copia: …"*. **No se baja un archivo a medias**: un archivo incompleto que parece un respaldo es peor que no tener ninguno |
| La copia tarda demasiado | Se corta en un tiempo máximo | *"La copia tardó más de lo esperado"* |
| Se cae el internet del almacén | **Todo se detiene** | La pantalla dice que no hay conexión con el servidor. **Esto es nuevo y es el costo de la mudanza** |
| Se cae el internet en el celular del dueño mientras carga inventario | Lo que ya guardó está guardado; lo que estaba escribiendo, no | *"No hay conexión con el servidor"* |

---

## 7. Requisitos no funcionales

- **Costo: cero pesos al mes.** Hosting, base de datos y las llamadas programadas, todo en plan gratis. Sin dominio
  propio: se usa la dirección que da el proveedor.
- **Primer golpe después de dormir:** ~~hasta quince segundos~~ **corregido el 2026-09-23 con una medición:
  unos dos minutos y medio en el plan gratis de Render** (simulado con 512 MB y 0,1 de CPU; ver
  [`DESPLIEGUE.md`](../../DESPLIEGUE.md), §7). Los quince segundos eran para Cloud Run, que da más CPU. Por eso las
  llamadas programadas **van las 24 horas** y no solo en horario de almacén: el dueño carga el inventario de
  noche. **No hay compromiso de nadie de que no pase.**
- **Cargar inventario desde el celular** tiene que ser usable con una mano y con datos móviles: la pantalla ya es
  responsive y no trae nada de internet, así que esto es verificar, no construir.
- **Nada de lo que se construya puede estorbar el camino de vuelta** (H7): el mismo empaque tiene que poder correr
  contra una base local cambiando configuración, no código.
- **Ni un solo puerto ni contenedor del car-wash se toca.**

---

## 8. Criterios de aceptación

- [ ] El dueño entra desde su celular, **con datos móviles y fuera del almacén**, y crea un repuesto con costo y
      precio; el repuesto aparece después desde otro dispositivo.
- [ ] La dirección abre por HTTPS y la cookie de la sesión no se manda por HTTP.
- [ ] Se reinicia el servidor a propósito y el celular que estaba adentro **sigue adentro**.
- [ ] Estando en la pantalla de reportes, recargar **no da "no encontrado"**.
- [ ] Se arranca el sistema sin la clave de sesiones y **no arranca**, diciendo cuál falta.
- [ ] Se baja una copia y se restaura en un Postgres vacío: el conteo de repuestos, el stock total y la cartera por
      cobrar son **idénticos** a los de la pantalla antes de bajarla.
- [ ] Un cierre de caja hecho de noche llega al correo del dueño **después** de que el servidor se durmiera.
- [ ] Se llama la dirección de las tareas sin llave y **no pasa nada**; con la llave, pasa.
- [ ] Se llama la dirección de las tareas dos veces seguidas y **no se duplica ningún correo**.
- [ ] Una venta registrada a las 11 p. m. hora de Colombia aparece en el reporte **de ese día**.
- [ ] Se revisan las respuestas de la API y los registros del servidor: **ningún secreto aparece**.
- [ ] El cajero, con su sesión, **no ve ni un costo** en la nube (lo mismo que ya se probó localmente).
- [ ] Se apaga el internet y se confirma que el sistema **no funciona** — y el documento lo dice así.

---

## 9. Qué no se toca

- **El dominio del negocio.** Vender, fiar, cobrar, la caja y los reportes no cambian una línea. Este spec mueve
  dónde corre el sistema, no qué hace.
- **Las migraciones ya aplicadas.** La nube arranca desde la primera y las corre todas.
- **Quién puede qué.** Los roles quedan exactamente como están.
- **El diseño de las pantallas**, salvo la de Respaldo, que cambia porque cambió lo que el respaldo es.
- **El car-wash**: sus puertos, sus contenedores y su base.

## 10. Fuera de alcance

- **Dominio propio y correo con el dominio del negocio.** Se usa la dirección del proveedor. Cuando el dueño quiera
  su dominio, se agrega sin rehacer nada.
- **El panel del propietario** (rebanada 5) y la bandeja de eventos hacia la nube: al mudarse todo, el panel deja de
  necesitar un espejo — pero eso es otro spec.
- **Vender sin internet de verdad** (guardar la venta en el navegador y subirla después). Es un proyecto entero, y
  la respuesta del dueño sobre su internet dice que hoy no se paga ese precio. **Queda anotado como el riesgo
  principal de esta decisión.**
- **Varias tiendas.** Una sola, como hasta ahora.
- **Instalar en el computador del almacén.** Deja de ser el camino principal; queda como salida de emergencia
  documentada (RF-018).

---

## 11. El riesgo que queda escrito

Después de esto, **un corte de internet en el almacén detiene las ventas**. Antes no. Esa es la factura de poder
cargar inventario desde el celular, y se paga todos los días aunque casi nunca se sienta.

Se mitiga con tres cosas, y ninguna es código:

1. **Un plan de datos en un celular** que sirva de internet de respaldo para el computador del mostrador. Es lo más
   barato que existe contra esto.
2. **Un cuaderno.** Si se cae todo, se anota la venta a mano y se registra después. Suena primitivo y es lo que
   hacen los almacenes que llevan treinta años abiertos.
3. **Volver atrás** (RF-018), si resulta que el internet no era tan estable como se creía.

Lo que no se vale es olvidarlo. Por eso está aquí y no en una conversación.
