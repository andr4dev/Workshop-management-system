# Respaldo y restauración — RD MOTORS

Todo el negocio vive en una sola base de datos: las ventas, el inventario, los costos y lo que deben los clientes.
Este documento dice **cómo llevarse una copia** y, lo que de verdad importa, **cómo recuperarla**.

> **Una copia que nunca se restauró no es un respaldo, es un archivo.** Restáurala una vez, a propósito, el día que
> se instale el sistema. Toma quince minutos y es la diferencia entre un susto y perder el negocio.

---

## 1. Qué cambió, y por qué

Cuando el sistema corría en el computador del almacén, el respaldo se hacía solo a las 2 de la mañana y quedaba en
una carpeta de ese computador. Eso protegía de lo único que podía pasar: que ese computador se dañara.

Desde el [spec 0011](specs/0011-la-tienda-en-la-nube/spec.md) el sistema corre en la nube, y esa forma de respaldar
**dejaría de funcionar sin avisar**: el disco del servidor es prestado y se borra cada vez que el servidor se
reinicia. La pantalla habría mostrado catorce copias sin que existiera ninguna — y eso es peor que no tener
respaldo, porque nadie busca lo que cree tener.

Ahora hay **dos protecciones distintas**, y conviene saber de qué protege cada una:

| Qué protege | Quién lo hace | Qué cubre |
|---|---|---|
| La historia de la base | El proveedor de la base, solo | El disco dañado, el borrado por error, la base corrupta. Se devuelve a un punto en el tiempo |
| **La copia que te bajas** | **Tú, apretando un botón** | Que se acabe la cuenta, que el proveedor cierre, o que quieras llevarte tus datos a otra parte |

La segunda es la que no hace nadie por ti. Por eso el sistema te avisa si llevas **más de una semana** sin bajar
ninguna.

---

## 2. Bajar una copia

*Ajustes › Respaldo › **Bajar una copia***.

- Tarda unos segundos. Sale un archivo `rdmotors-2026-09-23-143012.dump`.
- **En el servidor no queda nada**: el archivo se va contigo y el sistema se olvida de él.
- Guárdalo donde no se pierda: un disco externo, una memoria, o tu nube personal. **No solo en el computador del
  almacén** — si el respaldo y el negocio se pierden juntos, no era un respaldo.
- Se puede bajar desde el celular.

Si algo falla, la pantalla dice **lo que respondió el motor**, y el intento queda anotado en la lista. Lo más
común:

| Dice | Qué pasó | Qué hacer |
|---|---|---|
| `No encontré pg_dump en …` | Falta el cliente de Postgres en el servidor | Es de la instalación; revisar el `Dockerfile` |
| `server version mismatch` | El cliente y el motor son de versiones distintas | Igualar la versión mayor del cliente |
| `pg_dump se quedó colgado…` | La base no respondió a tiempo | Reintentar; si sigue, mirar el estado de la base |

**Nunca se baja un archivo a medias.** Si el motor falla a la mitad, no llega nada: un archivo truncado que parece
un respaldo es la peor de las respuestas posibles.

---

## 3. Restaurar la copia

Lo que hace falta: el archivo `.dump` y PostgreSQL 17 instalado en el equipo donde vas a restaurar.

### 3.1 Crear la base vacía

```bat
"C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -c "CREATE USER rdmotors WITH PASSWORD 'rdmotors'"
"C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -c "CREATE DATABASE rdmotors OWNER rdmotors"
```

### 3.2 Meter la copia

```bat
set PGPASSWORD=rdmotors
"C:\Program Files\PostgreSQL\17\bin\pg_restore.exe" ^
  --host=localhost --port=5432 --username=rdmotors ^
  --dbname=rdmotors --no-owner ^
  "C:\respaldos\rdmotors-2026-09-23-143012.dump"
```

- `--no-owner` evita que se queje si el usuario dueño de la copia no existe con ese nombre en el equipo nuevo.
- Si la base **no está vacía**, agrega `--clean --if-exists`. Léelo dos veces: eso **borra** lo que haya.

### 3.3 Arrancar el sistema contra esa base y comprobar

Se arranca el sistema apuntando a esa base (ver [`DESPLIEGUE.md`](DESPLIEGUE.md), sección de volver atrás) y se
revisan tres cosas, en este orden:

1. **Entrar** con un usuario conocido.
2. **Inventario**: el total de referencias y unidades se parece al del día de la copia.
3. **Cartera**: los clientes que debían, siguen debiendo lo mismo.

Si las tres están, la restauración sirvió.

> **La clave de las sesiones ya no viaja con la copia**, como pasaba antes. Desde el spec 0011 esa clave es una
> variable de entorno del despliegue (`RDMOTORS_CLAVE_SESIONES`) y vive con la configuración, no con los datos. Si
> restauras en un sistema con otra clave, las contraseñas siguen sirviendo: lo único que pasa es que todos tienen
> que volver a entrar.

---

## 4. Qué NO recupera una copia

- Lo que pasó **después** de bajarla. Si bajas una el lunes y algo pasa el viernes, se pierde la semana. Para eso
  está la historia que guarda el proveedor de la base, que llega hasta el momento del daño.
- La **ticketera**, la red ni los programas: la copia es de los datos.

---

## 5. Cada cuánto

No hay una regla universal, pero sí una pregunta que la contesta: **¿cuánto trabajo estoy dispuesto a repetir?**

- Mientras se carga el inventario base: **una copia al terminar cada jornada larga**. Son horas de digitación.
- Con el almacén ya vendiendo: **una por semana** alcanza, porque la historia de la base cubre el resto.
- **Siempre antes de algo riesgoso**: cambiar de proveedor, una actualización grande, mudar el sistema.
