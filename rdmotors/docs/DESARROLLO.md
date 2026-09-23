# Cómo levantar RD Motors en desarrollo

## Requisitos

- Java 25
- Docker (para la base de datos y para las pruebas de integración)

## Puertos — RD Motors no usa los de siempre

Esta máquina ya corre el car-wash, que tiene tomados el 5432 y el 8080. Cada proyecto con su
carril, sin pelearse:

| | Car-wash | **RD Motors** |
|---|---|---|
| Postgres | 5432 | **5433** |
| Backend | 8080 | **8081** |

## Arrancar

```bash
# 1. Una sola vez (o cada vez que cambie el módulo domain):
#    instala 'domain' en el repositorio local para que 'pos' lo encuentre
./mvnw install -DskipTests

# 2. Levantar la aplicación. La base se levanta sola.
./mvnw -pl pos spring-boot:run
```

Queda en `http://localhost:8081`.

El `compose.yaml` lo arranca Spring Boot solo, gracias a `spring-boot-docker-compose`. Con
`lifecycle-management=start_only`, **la base no se apaga** al detener la app — reiniciar la
aplicación treinta veces al día no debería reiniciar Postgres treinta veces.

### La base, a mano

```bash
docker compose up -d       # levantar
docker compose down        # apagar (los datos se conservan)
docker compose down -v     # apagar y BORRAR todo, para empezar de cero
```

Conectarse a mirar:

```bash
docker exec -it rdmotors-postgres psql -U rdmotors -d rdmotors
```

## Pruebas

```bash
./mvnw clean test
```

Son **dos niveles distintos** y hacen falta los dos:

| | Dónde | Qué prueba | Velocidad |
|---|---|---|---|
| Dominio, con dobles | `domain` | que las **reglas** sean correctas | milisegundos |
| Integración, con Postgres real | `pos` | que la **infraestructura** cumpla | ~15 s |

Las de integración levantan su propio Postgres con Testcontainers — **no usan la base de
desarrollo** y no hay que tener nada encendido. Pero sí necesitan Docker corriendo: **fallan** si
no está, a propósito. Una prueba que se salta sola deja de proteger sin que nadie se entere.

### Por qué hacen falta las dos

Un doble en memoria demuestra que el costo promedio se calcula bien. **No puede** demostrar que
Postgres lo guarde bien. Cuando se rompió a propósito la precisión de `costo_promedio` de
`numeric(14,4)` a `(14,2)`, las 56 pruebas de dominio siguieron en verde y solo la de integración
lo vio:

```
expected: 13333.3333
 but was: 13333.33
```

Ojo con esto: **`ddl-auto=validate` tampoco lo detectó.** Valida tablas y columnas, no precisión.
Toda columna de dinero o de costo necesita su prueba de ida y vuelta contra base real.

## Esquema

Flyway manda. Las migraciones están en `pos/src/main/resources/db/migration/` y corren solas al
arrancar. Hibernate solo verifica que las entidades cuadren (`ddl-auto=validate`).

**Nunca se edita una migración ya aplicada.** Todo cambio va en una nueva, numerada.

## Entrar: la clave, el primer administrador y cómo restablecer (spec 0004)

### La clave con la que se firman las sesiones

La sesión es un token firmado que vence a las 24 horas y viaja en una cookie que la página no puede
leer. La clave de la firma sale de:

1. la propiedad `rdmotors.seguridad.clave-token` (en base64, mínimo 256 bits), si está puesta; o
2. el archivo `~/.rdmotors/clave-token`, que el servidor **genera solo** la primera vez.

Si ese archivo se pierde, no se pierde nada de la tienda: todos vuelven a entrar, y ya. En la tienda
vale la pena respaldarlo con la base; en desarrollo, no importa. Las pruebas usan una clave fija en
`pos/src/test/resources/config/application.properties`, así que **no** tocan el archivo de la máquina.

### El primer administrador

Con la base sin usuarios, la pantalla de entrar ofrece *Crear el administrador* y esa es la única vez
que existe esa opción (`GET /api/instalacion` dice si falta). No hay contraseña por defecto: la escoge
quien instala. Desde ahí, ese administrador crea a los demás en *Usuarios* (⚙ › Usuarios).

### El único administrador olvidó su contraseña

No hay "olvidé mi contraseña" por correo: el sistema funciona sin internet. Se hace en el computador
de la tienda, **con el servidor apagado** (si sigue arriba, el puerto 8081 está ocupado y no arranca):

```bash
# En desarrollo
./mvnw -pl pos spring-boot:run -Dspring-boot.run.arguments=--rdmotors.restablecer-administrador=ruben

# En la tienda, sobre el jar instalado
java -jar rdmotors.jar --rdmotors.restablecer-administrador=ruben
```

Le pone una contraseña temporal, la muestra en la consola y se apaga. Al entrar con ella, el sistema
pide cambiarla de una vez. Solo funciona sobre un usuario con rol **administrador**: a un cajero se la
restablece un administrador desde *Usuarios*, sin tocar la consola. Queda en la auditoría, a nombre de
quien la recibió y con el motivo "Desde el computador de la tienda".

## Esto NO es como se instala en la tienda

El `compose.yaml` es solo para desarrollo. En la tienda Postgres va **nativo**, como servicio de
Windows que arranca con el equipo: un punto de venta no puede depender de que alguien abra Docker
antes de vender.

La dependencia `spring-boot-docker-compose` va marcada `optional` justamente para que no viaje al
jar que se instala allá.
