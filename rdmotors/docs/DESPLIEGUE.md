# Despliegue — RD MOTORS

Dónde corre el sistema, qué secreto va en cada variable, cómo se sube una versión nueva y **cómo se vuelve atrás**.

> **Estado: el despliegue en la nube está pendiente** (fase 5 del [plan 0011](specs/0011-la-tienda-en-la-nube/plan.md)).
> Todo lo de este documento ya funciona y está probado **contra la base local, en la máquina de desarrollo**. Lo que
> falta es crear las cuentas y apretar el botón, y eso lo hace el dueño del proyecto. Las secciones que dependen del
> proveedor están marcadas.

---

## 1. Qué se despliega

Una sola imagen de contenedor. Adentro va **todo**: la pantalla ya construida, el servidor, y `pg_dump` para bajar
la copia de la base. Afuera queda solo la base de datos.

```
Dockerfile        tres etapas: Node construye la pantalla · Maven construye el jar · Java la corre
.dockerignore     lo que no viaja (node_modules, target, respaldos, secretos)
```

Que Node y Maven no lleguen a la imagen final no es limpieza: cada programa instalado ahí es algo que hay que
actualizar cuando le salga un problema de seguridad.

```bash
docker build -t rdmotors .
```

---

## 2. Los secretos

**Ninguno está escrito en el repositorio ni dentro de la imagen.** Todos entran por variables de entorno.

| Variable | Qué es | Cómo se consigue |
|---|---|---|
| `RDMOTORS_BD_URL` | La base, completa y con `jdbc:` adelante | La da el proveedor de la base. Lleva `?sslmode=require` al final |
| `RDMOTORS_BD_USUARIO` | El usuario de la base | Igual |
| `RDMOTORS_BD_CONTRASENA` | Su contraseña | Igual |
| `RDMOTORS_CLAVE_SESIONES` | **La clave que firma las sesiones** | `openssl rand -base64 32`. **Se genera una vez y no se cambia nunca más** |
| `RDMOTORS_LLAVE_TAREAS` | La llave del reloj de afuera | Cualquier texto largo al azar. `openssl rand -hex 24` sirve |
| `BREVO_API_KEY` | La llave del correo | De la cuenta de Brevo del dueño |
| `RDMOTORS_CORREO_REMITENTE` | Desde qué correo salen los cierres | Tiene que estar **verificado** en Brevo |
| `PORT` | En qué puerto escuchar | **La pone la nube sola.** No se toca |

### La clave de las sesiones merece un párrafo aparte

Es la única que, si se pierde o cambia, **saca a todo el mundo del sistema sin decir por qué**. En el computador
del almacén el sistema se la inventaba y la guardaba en un archivo; en la nube ese archivo se borra en cada
reinicio, y en el plan gratis hay varios reinicios al día.

Por eso, con el perfil `nube` encendido, **el sistema no arranca si falta**, y el registro dice cuál es. Es
deliberado: un sistema que arranca mal y falla raro tres horas después cuesta mucho más que uno que no arranca.

**Guárdala donde guardas las cosas importantes.** Perderla no pierde datos —las contraseñas siguen sirviendo— pero
obliga a que todos vuelvan a entrar.

---

## 3. Correrlo

El perfil `nube` se enciende con `SPRING_PROFILES_ACTIVE=nube`. Ese perfil **no reemplaza** la configuración de
siempre: se suma encima y solo cambia lo que tiene que cambiar (el puerto, la base, la cookie por HTTPS, la clave
obligatoria, y apagar el arranque de Docker Compose).

### En la nube — los pasos, en orden

Sirve cualquier proveedor que corra un contenedor y le pase variables de entorno. Estos pasos son para
**Neon** (la base) y **Render** (el servidor): las dos tienen plan gratis sin pedir tarjeta.

> **Los planes gratis cambian.** Lo que dice aquí era cierto al escribirlo; al crear cada cuenta, mirar los
> límites vigentes antes de seguir.

#### Paso 1 · La base, en Neon

1. Crear cuenta en **neon.tech** (entra con GitHub o con correo; **no pide tarjeta**).
2. Crear un proyecto. Región: la más cercana a Colombia de las que ofrezca, normalmente `AWS us-east-1`.
3. Nombre de la base: `rdmotors`.
4. Copiar la cadena de conexión. **La DIRECTA, no la "pooled"** — el nombre del servidor de la pooled lleva
   `-pooler`. Esto importa de verdad y se explica abajo.

> **Por qué la directa y no la pooled.** La conexión "pooled" de Neon reparte una misma conexión real entre
> varias peticiones. Con eso se rompen dos cosas de este sistema: **Flyway**, que toma un candado de sesión
> mientras aplica las migraciones, y **`pg_dump`**, que necesita una sola sesión coherente para sacar la copia.
> Los dos fallarían de formas raras y difíciles de leer. Este sistema pide tres conexiones: no necesita pooler.

Neon da algo así:

```
postgresql://rdmotors_owner:AbC123@ep-cool-name-123456.us-east-1.aws.neon.tech/rdmotors?sslmode=require
```

Que se parte en las tres variables así:

```
RDMOTORS_BD_URL=jdbc:postgresql://ep-cool-name-123456.us-east-1.aws.neon.tech/rdmotors?sslmode=require
RDMOTORS_BD_USUARIO=rdmotors_owner
RDMOTORS_BD_CONTRASENA=AbC123
```

Es la misma cadena con `jdbc:` adelante y sin el usuario ni la contraseña adentro.

#### Paso 2 · Los secretos

Se generan **una vez** y se guardan donde se guardan las cosas importantes:

```bash
openssl rand -base64 32   # RDMOTORS_CLAVE_SESIONES  — si se pierde, todos vuelven a entrar
openssl rand -hex 24      # RDMOTORS_LLAVE_TAREAS    — la que usarán los crones
```

#### Paso 3 · El servidor, en Render

**Render.com no pide tarjeta.** Su plan gratis apaga el servicio a los 15 minutos sin uso — que es justo lo que
resuelven los dos crones del paso 5 — y despliega directo desde un repositorio de Git, sin CLI.

> Alternativa si más adelante conviene: **Cloud Run**, que sí pide tarjeta de facturación (aunque no cobre dentro
> del plan gratis) pero da un poco más de margen de cómputo. El `Dockerfile` es el mismo para los dos; cambiar de
> proveedor no toca una línea de código.

1. **El código tiene que estar en un repositorio de Git** (GitHub, GitLab o Bitbucket) — Render construye desde
   ahí, no se le sube un jar ni una imagen a mano.
2. render.com → cuenta con GitHub → **New › Web Service** → conectar el repositorio de RD MOTORS.
3. **Runtime: Docker**, y en **Root Directory** (dentro de *Advanced*) escribir **`rdmotors`**.

   > El repositorio tiene el proyecto dentro de la carpeta `rdmotors/`, no en la raíz. Sin este campo Render
   > busca el `Dockerfile` en la raíz, no lo encuentra, y la construcción falla con un error que no menciona la
   > carpeta. Además, todas las rutas del `Dockerfile` (`frontend/`, `domain/`, `pos/`) son relativas a esa carpeta:
   > es lo que Render usa como contexto de construcción.
4. **Region: `Ohio (US East)`**, no la más cercana a Colombia. La base de Neon está en `us-east-2`, que **es**
   Ohio: en la misma región cada consulta tarda un par de milisegundos; con el servidor en Oregon serían decenas,
   y una sola pantalla del sistema hace varias consultas. La distancia que importa es la del servidor a la base, no
   la de la persona al servidor.
5. **Instance Type: Free**.
6. **Environment Variables** — ahí van las siete, una por una:

   | Variable | Valor |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `nube` |
   | `RDMOTORS_BD_URL` | la de Neon, con `jdbc:` adelante |
   | `RDMOTORS_BD_USUARIO` | `neondb_owner` (o el que corresponda) |
   | `RDMOTORS_BD_CONTRASENA` | la de Neon |
   | `RDMOTORS_CLAVE_SESIONES` | la generada en el paso 2 |
   | `RDMOTORS_LLAVE_TAREAS` | la generada en el paso 2 |
   | `BREVO_API_KEY` | la de Brevo |
   | `RDMOTORS_CORREO_REMITENTE` | el remitente verificado |

   Render las marca como secretas automáticamente: no vuelven a mostrarse en texto plano después de guardarlas.
7. **Health Check Path**: `/api/salud`. Así Render sabe distinguir "el contenedor arrancó" de "el sistema ya
   puede atender".
8. **Create Web Service.** La primera construcción tarda varios minutos: Render clona el repositorio, corre las
   tres etapas del `Dockerfile` (Node, Maven, la imagen final) y la despliega.

**Si no arranca, el primer sitio a mirar son los "Logs" de la pantalla del servicio en Render.** Los dos errores
típicos están escritos para que se entiendan: falta la clave de sesiones, o la base no responde.

#### Paso 4 · El primer administrador

Abrir la dirección que dio Render (algo como `https://rdmotors.onrender.com`). Como la base está vacía, la
pantalla pide **crear el primer administrador**, igual que en una instalación nueva. Ahí mismo, en
*Ajustes › Usuarios*, se crea **el usuario del dueño** — que es el objetivo de todo esto.

#### Paso 5 · Los dos crones

En **cron-job.org** (gratis, sin tarjeta) y, para que no dependan de uno solo, repetir el primero en
**UptimeRobot**:

| Cada | Qué llamar | Método | Encabezado |
|---|---|---|---|
| 5 min, **las 24 horas** | `https://…/api/salud` | GET | — |
| 10 min | `https://…/api/tareas/correos` | POST | `X-RDMOTORS-LLAVE: <RDMOTORS_LLAVE_TAREAS>` |

Comprobar que el segundo responde **200**. Si responde 404, la llave está mal escrita — y responde 404 a
propósito, para no confirmarle a nadie que ahí hay algo.

**Las 24 horas, no solo el horario del almacén.** El dueño carga el inventario **de noche**, justo cuando el
almacén está cerrado: con el cron apagado a esa hora, el servicio estaría dormido y su primera pantalla tardaría
minutos en abrir (ver "Cuánto tarda en despertar", abajo).

**El costo de tenerlo despierto siempre son las horas gratis de Render**: el plan da 750 horas de instancia al mes,
y un servicio encendido todo el mes gasta 744. Alcanza para **uno solo**. Si más adelante se agrega otro servicio
gratis en la misma cuenta, uno de los dos se queda sin horas a fin de mes.

#### Paso 6 · Lo del negocio

*Ajustes › Datos de la tienda* (nombre, NIT, dirección, para el comprobante) y *Ajustes › Correos* (a quién le
llega el cierre de caja). Para esto último el dueño tiene que haber creado su cuenta de Brevo y **verificado el
remitente**.

#### Paso 7 · Comprobar que quedó bien

```bash
SITIO=https://rdmotors-xxxx.run.app \
LLAVE_TAREAS=<RDMOTORS_LLAVE_TAREAS> \
USUARIO=<el administrador> CLAVE=<su contraseña> \
node scripts/verificar-despliegue.mjs
```

Son 18 comprobaciones: que la pantalla se sirva y que recargar adentro no dé "no encontrado", que una dirección de
la API que no existe **no** devuelva la pantalla, que la de salud responda sin sesión y sin contar nada del
negocio, que las tareas se nieguen sin llave y corran con ella, y que la copia de la base **se baje entera**.

Este mismo script se corre contra el contenedor local antes de desplegar (`SITIO=http://localhost:8082`), así que
un fallo después del despliegue señala a la nube y no al código.

Y lo que el script no puede hacer solo, a mano y desde un celular con datos móviles, fuera del almacén:

- [ ] Entrar y **crear un repuesto** con su costo y precio; verlo después desde otro dispositivo.
- [ ] Con sesión de **cajero**: recorrer inventario y reportes y confirmar que **no aparece ni un costo**.
- [ ] Una venta a las **11 de la noche** aparece en el reporte de **ese** día, no del siguiente.
- [ ] Un **cierre de caja de noche** llega al correo del dueño después de que el servidor se durmió.
- [ ] **Bajar una copia desde el celular** y que el archivo llegue completo.
- [ ] Revisar los registros del servidor: **ningún secreto aparece**.

### En el computador, para probar la imagen

```bash
docker run --rm -p 8082:8080 \
  -e SPRING_PROFILES_ACTIVE=nube \
  -e RDMOTORS_BD_URL='jdbc:postgresql://host.docker.internal:5433/rdmotors' \
  -e RDMOTORS_BD_USUARIO=rdmotors \
  -e RDMOTORS_BD_CONTRASENA=rdmotors \
  -e RDMOTORS_CLAVE_SESIONES="$(openssl rand -base64 32)" \
  -e RDMOTORS_LLAVE_TAREAS=llave-de-prueba \
  -e RDMOTORS_SEGURIDAD_COOKIE_SEGURA=false \
  rdmotors
```

Dos cosas de esa orden:

- **El 8082** a propósito: el 8080 es del car-wash y el 8081 el del desarrollo. Nada se pisa.
- **`RDMOTORS_SEGURIDAD_COOKIE_SEGURA=false`** solo aquí. En la nube la cookie de la sesión exige HTTPS; en
  `http://localhost` eso dejaría a todos por fuera. Es la misma perilla que se usa para volver atrás.

---

## 4. El reloj de afuera

El servidor de la nube gratis **se apaga cuando nadie lo usa**, y un servidor apagado no cuenta las horas. Así que
el reloj se saca afuera: un servicio gratuito de llamadas programadas.

| Cada cuánto | Qué llama | Para qué |
|---|---|---|
| 5 minutos, de 7 a. m. a 9 p. m. | `GET /api/salud` | Que no se duerma en horario de almacén |
| 10 minutos | `POST /api/tareas/correos` con el encabezado `X-RDMOTORS-LLAVE` | Que los correos del cierre salgan aunque el servidor se haya dormido |

- `/api/salud` es pública y **no dice nada del negocio**: responde sí o no. Es la única dirección abierta a internet
  entero que alguien va a estar llamando todo el día.
- `/api/tareas/correos` **sin la llave responde "no existe"**, no "no autorizado": no le confirma a nadie que ahí
  hay algo.
- Llamarla dos veces **no manda ningún correo dos veces**. Por eso se puede poner más de un servicio de cron sin
  miedo — y conviene: que fallen dos a la vez ya es raro.

**Poner dos servicios distintos** (por ejemplo cron-job.org y UptimeRobot) es lo que convierte "casi siempre
despierto" en "despierto".

---

## 5. Subir una versión nueva

1. Correr las pruebas **antes**: `./mvnw clean test` y, en `frontend/`, `npx eslint src/ && node --test src/utils/*.test.js && npx vite build`.
2. Construir la imagen.
3. Desplegarla. **Las migraciones de la base corren solas al arrancar**; no hay paso manual.
4. Abrir el sistema y entrar. Si algo salió mal, el registro del arranque lo dice.

---

## 6. Volver atrás: correrlo en el computador del almacén

Esto dejó de ser el camino principal, pero sigue siendo posible y **no hace falta tocar código**. Es la salida si
la nube resulta no servir, o si el internet del almacén resulta menos estable de lo que se creía.

**No encender el perfil `nube`.** Eso es todo. `application.properties` sigue apuntando a la base local, al puerto
8081, sin exigir HTTPS en la cookie y sin exigir la clave de sesiones — exactamente como estaba antes.

Hace falta:

1. **PostgreSQL 17 nativo** en el equipo, con la base `rdmotors` y su usuario.
2. **Los datos**: restaurar la última copia bajada, siguiendo [`RESPALDO_Y_RESTAURAR.md`](RESPALDO_Y_RESTAURAR.md).
3. **Arrancar el sistema** contra esa base, con la imagen (sin el perfil) o con el jar.

Lo que se recupera al volver: vender aunque no haya internet. Lo que se pierde: cargar inventario desde el celular
con el almacén cerrado, que es justo por lo que se mudó.

---

## 7. Cuánto tarda en despertar (medido)

Se simuló el plan gratis de Render con `docker run --memory=512m --cpus=0.1` contra la base real de Neon, con la
base ya migrada. **Es una simulación, no Render mismo**: los números reales pueden variar.

| | Arranque | Memoria |
|---|---|---|
| JVM con sus valores por omisión | **258 s** (4 min 18 s) | 359 de 512 MB |
| Con las banderas del `Dockerfile` | **139 s** (2 min 19 s) | 313 de 512 MB |

Dos minutos y medio no son los "5 a 15 segundos" que se estimaron al planear este spec: esa cifra era para Cloud
Run, que da bastante más CPU. Con 0,1 de CPU lo que domina el arranque es cargar clases y levantar Hibernate.

Consecuencias, todas ya reflejadas arriba:

- **El cron de salud es obligatorio y va las 24 horas.** Una persona que llegue con el servicio dormido espera más
  de dos minutos y probablemente vea la página de error del proveedor antes de que abra.
- **Un despliegue nuevo también tarda eso** en quedar atendiendo. Se hace de noche o con el almacén cerrado.
- **Un reinicio inesperado** (el proveedor mueve el servicio, se agota la memoria) deja el sistema fuera unos
  minutos. Con el almacén vendiendo, se siente.

Si eso llega a ser un problema, **la solución es pagar** (una instancia de pago da CPU suficiente para arrancar en
segundos y no se duerme), no ajustar más el código.

---

## 8. Cuando el gratis deje de alcanzar

La molestia del plan gratis es una sola: **el primero que llega después de un rato sin uso espera unos segundos**.
Con las llamadas programadas eso debería ser raro en horario de almacén, pero no hay compromiso de nadie de que no
pase.

La solución es **cambiar un número** —pedirle al proveedor que mantenga siempre una instancia encendida— y cuesta
del orden de 25.000 a 35.000 pesos al mes. **Nada de lo construido se bota**: el reloj de afuera sigue sirviendo
igual, porque disparar las tareas desde afuera es correcto de todos modos.

Las señales de que llegó el momento: que alguien del mostrador se queje de esperas, o que el almacén empiece a
vender de verdad con el sistema.
