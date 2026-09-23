---
name: backend
description: Convenciones del backend de RD MOTORS (Spring Boot, Java, PostgreSQL, arquitectura hexagonal). Úsala al escribir o tocar casos de uso, puertos, adaptadores, entidades, transacciones, el outbox de sincronización, reportes o cualquier endpoint de escritura. Cubre dónde va la frontera transaccional, qué merece un puerto y qué no, y las trampas contables que ya costaron caro en el car wash del que se porta código.
---

# Backend — RD MOTORS

Punto de venta de repuestos. **Monolito modular, hexagonal pragmático, dominio transaccional.**
La PC de la tienda es la fuente de verdad. La nube es un espejo de solo lectura para el dueño.

## Cicatrices de este proyecto

Todo lo de esta sección ya pasó aquí. No son advertencias de manual.

### Spring Boot 4 modularizó las autoconfiguraciones

`flyway-core` es **solo la librería**. La integración que hace correr las migraciones al arrancar
vive en `spring-boot-flyway`. Con la librería sola, Flyway está en el classpath y **no hace nada,
sin avisar**.

El síntoma fue engañoso: `Schema validation: missing table [categoria]` — parece un problema de
entidades, y en realidad las migraciones nunca se ejecutaron.

**Regla general:** en Boot 4, tener la librería de algo no significa tenerlo integrado. Si una
capacidad "no hace nada" y no da error, revisa si falta su módulo `spring-boot-*`.

### `ddl-auto=validate` NO comprueba la precisión numérica

Se cambió `costo_promedio` de `numeric(14,4)` a `(14,2)` y `validate` lo dejó pasar. Postgres
truncó los decimales en silencio.

Lo único que lo atrapó fue una aserción de ida y vuelta contra base real:

```
expected: 13333.3333
 but was: 13333.33
```

**Regla:** `validate` cubre tablas y columnas, no precisión ni escala. **Toda columna de dinero o
de costo necesita su prueba de ida y vuelta contra Postgres de verdad.**

### Un puerto no se puede escalonar entre fases

Agregar un método a una interfaz de puerto **rompe la compilación de `pos` al instante**: el
adaptador deja de cumplir el contrato.

No es un problema, es el compilador haciendo su trabajo. Pero significa que **tocar un puerto
arrastra su adaptador en el mismo commit, siempre.** No se puede planear "el puerto hoy, el
adaptador mañana".

### Los dobles en memoria permiten estados que Postgres no

Los falsos son mapas independientes: se puede sembrar una variante cuyo concepto no está en el
repositorio de productos. **La clave foránea real lo haría imposible.**

No es un defecto que arreglar — es el límite de los dobles, y la razón de que la prueba de
integración exista. Cuando una prueba con falsos pase y la de integración falle, sospecha primero
de un estado imposible en el fixture.

### La atomicidad solo se puede probar contra base real

Se le puso `REQUIRES_NEW` a un caso de uso anidado y el registro creado **sobrevivió al rollback**
del que lo llamaba, quedando huérfano. Las 66 pruebas de dominio siguieron en verde: los dobles en
memoria no tienen transacciones que deshacer.

**Regla:** un caso de uso que llama a otro usa propagación por defecto. Si alguna vez hace falta
`REQUIRES_NEW`, hay que poder explicar por qué esa parte debe sobrevivir al fallo del todo.

### Versiones y renombres que muerden

| Qué | Detalle |
|---|---|
| Testcontainers 2.x | renombró los módulos: `postgresql` → `testcontainers-postgresql` |
| Spring 7 | `HttpStatus.UNPROCESSABLE_ENTITY` deprecado → `UNPROCESSABLE_CONTENT` |
| Boot 4 | `@EntityScan` ya no está donde estaba; con paquete raíz compartido no hace falta |

### Un endpoint que informa de plata muestra LAS DOS PUNTAS

La respuesta de la compra traía solo el costo. Ver solo el costo deja al administrador sin saber
si el precio que acaba de fijar tiene sentido; ver solo el precio esconde si la compra fue buena.

**Regla:** donde haya margen, van costo y precio juntos, o ninguno.

## La única regla que define la arquitectura

**Las dependencias apuntan hacia adentro.** El dominio no importa nada de Spring, HTTP, Jackson
ni de la impresora.

**La excepción, decidida a propósito: `jakarta.persistence` SÍ se permite en el dominio.** Las
anotaciones de mapeo son metadatos inertes — un `@Entity` no impide construir el objeto en una
prueba y llamarle un método sin base de datos (las 28 pruebas del dominio lo hacen). Lo que sí
costaría es duplicar cada clase del dominio con su mapper, que es donde estos proyectos se ahogan.
El `pom.xml` de `domain` deja esto por escrito.

**La otra excepción: `spring-tx` en el paquete `aplicacion`, y solo por `@Transactional`.** La
transacción tiene que declararse en algún lado y el caso de uso es su dueño. Prohibido en
`dominio/`.

Pruebas de humo, las tres tienen que dar cero:

```bash
# 1. Spring, web o Jackson dentro de dominio/
grep -rn "org.springframework\|com.fasterxml\|jakarta.servlet" domain/src/main/java --include=*.java | grep "/dominio/"

# 2. El dominio importando infraestructura
grep -rnE "^import .*infraestructura" domain/src/main/java --include=*.java

# 3. Un caso de uso importando algo de Spring que no sea la transacción
grep -rn "org.springframework" domain/src/main/java --include=*.java | grep -v "transaction.annotation.Transactional"
```

Y la cuarta la impone Maven sola: como `domain` no declara dependencia de `pos`, el compilador
hace imposible importar infraestructura desde el dominio.

El resto de las reglas de este archivo son consecuencias de esa.

## Estado del código

```
domain/     compartido (Dinero, Reloj) · inventario (Producto, Variante, Kardex, Categoria)
            compras (Proveedor, Compra, LineaCompra)
            aplicacion: RegistrarCompra · CrearRepuesto · BuscarRepuestos · RegistrarProveedor
pos/        adaptadores JPA · 6 endpoints · Flyway V1 + V2 · Testcontainers
frontend/   React + Vite · kit (Boton, Campo, Modal) · pantalla de compra
```

**66 pruebas de dominio** (sin base de datos, milisegundos) + **8 de integración** contra Postgres
real en Docker. La rebanada 1 va por la fase 5 de 6: falta el inventario en pantalla.

Después: ventas, caja, el outbox y el módulo `cloud`.

## Estructura: por capacidad de negocio, no por capa técnica

```
ventas/
  dominio/          reglas puras: Venta, LineaVenta, Dinero, puertos
  aplicacion/       casos de uso: RegistrarVenta, AnularVenta
  infraestructura/  adaptadores: JPA, REST, impresora
inventario/
caja/
compras/
```

**No** `entidades/`, `servicios/`, `repositorios/` en la raíz. Agrupar por capa es lo que
convierte un monolito en una bola de lodo: para tocar "vender" hay que abrir cuatro carpetas
lejanas y nada te dice qué pertenece a qué.

Un módulo habla con otro **por su caso de uso o por un puerto**, nunca alcanzando su repositorio
directo. `ventas` no importa el repositorio de `inventario`: pide un puerto `DescontarStock`.

## La frontera transaccional: el caso de uso es el dueño

Una transacción por caso de uso, abierta y cerrada en `aplicacion/`. Ni en el controlador
(que no sabe de negocio) ni en el dominio (que no debe saber de infraestructura).

Todo lo de una venta va bajo el **mismo commit**: la venta, sus líneas, el descuento de stock,
el movimiento de kardex, el enlace a la sesión de caja y el evento del outbox. Si algo queda a
medias, el negocio ve esto:

| Lo que se rompe | Lo que ve el dueño |
|---|---|
| Bajó stock, no quedó la venta | mercancía "desaparecida" — acusan al cajero de un robo inexistente |
| Quedó la venta, no bajó el stock | sobreventa después, cliente sin repuesto |
| Venta sin enlazar a la caja | el arqueo no cuadra y el cajero paga un faltante que no es suyo |

Esos tres renglones son literalmente los dolores del spec (sección 1). Aquí la transaccionalidad
no es preferencia técnica: es el requisito.

### La trampa: el dominio puro no sabe bloquear filas

"No vender sin stock" necesita un bloqueo de fila sobre el producto, que es un concepto de base
de datos. Si el dominio queda purísimo y las transacciones flotan afuera, terminas cargando el
producto, mutándolo y guardándolo **sin nada que impida que otro proceso lo cambie en el medio**.
Con un solo cajero suena improbable; el doble clic en "cobrar" y el reintento de la cola lo
vuelven cotidiano.

Solución: el puerto declara la **intención**, no la técnica.

```java
// dominio/puerto/RepositorioProductos.java  — sin una sola anotación
public interface RepositorioProductos {
    Optional<Producto> buscar(ProductoId id);
    Optional<Producto> buscarParaModificar(ProductoId id);  // el adaptador usa SELECT ... FOR UPDATE
}
```

El dominio dice "lo voy a modificar"; el adaptador decide que eso es un `PESSIMISTIC_WRITE`.
Nadie pierde su rol y la fila queda serializada.

## Qué merece un puerto y qué no

| Merece puerto | Por qué |
|---|---|
| Persistencia | corre contra la base local y contra la de la nube |
| Canal de sincronización | publica en la tienda, consume en el servidor |
| Impresión de comprobante | ticketera en la tienda, **nada** en la nube |
| Reloj | sin él no se pueden probar cierres de caja ni cortes por fecha |

La impresión vive en el **backend**, no en el navegador: con `window.print()` solo el equipo que
tiene la impresora podría vender, y eso inutiliza la tablet del pasillo.

**"Cobro" NO es un puerto.** Se descartó a propósito: no habrá pasarela de pago nunca, así que los
medios de pago son una tabla de catálogo con un booleano `entra_a_caja` — no una interfaz con
adaptadores. Es el ejemplo canónico de la regla de abajo.

| NO merece puerto |
|---|
| Un repositorio que tendrá una sola implementación para siempre |
| Un helper de formato, cálculo o validación pura — eso es dominio, no borde |
| Cualquier cosa cuyo segundo adaptador no puedas nombrar hoy |

Un puerto por cada cosa "porque así se hace" es como mueren estos proyectos: tres archivos para
agregar un campo. **Si no puedes nombrar la segunda implementación, no es un puerto: es una
interfaz decorativa.**

### No dupliques modelo de dominio y modelo de persistencia

Decisión tomada a propósito, en contra del manual. Las mismas tablas existen igual en la tienda
y en la nube, así que el mapeo entidad-a-dominio no compra nada aquí y sí cuesta: es la capa
donde estos proyectos se ahogan en mappers.

El valor de hexagonal en RD Motors está en los **bordes de entrada y salida**, no en duplicar el
modelo. Lo que sí se respeta sin excepción: la lógica de negocio vive en el dominio, no en clases
con anotaciones sirviendo de bolsa de getters. Ver la skill `solid`, sección de modelo anémico.

## El outbox nace dentro de la transacción de la venta

No se puede hacer que "la venta se guarda Y llega a la nube" sea atómico: la red se cae después
del commit y una llamada de red no se deshace. Y si lo forzaras, obtendrías lo peor posible —
**la venta falla porque no hay internet**, que destruye toda la premisa del sistema.

Por eso el evento se **escribe** en la misma transacción y se **envía** después:

```java
// dentro del caso de uso, mismo commit que la venta
repositorioVentas.guardar(venta);
outbox.registrar(EventoVentaRegistrada.de(venta));   // solo INSERT, sin red
```

Si la venta se guarda, el evento existe. Si la venta se cae, el evento tampoco. Nunca se
desincronizan. El envío es otro proceso, con reintentos, y **jamás bloquea al cajero**.

Reglas del evento, todas por una razón concreta:

- **Secuencia monotónica por dispositivo** como clave de orden. Nunca la fecha: las PC de local
  tienen la pila del reloj muerta, y si el replay aplica un movimiento de kardex antes del que lo
  precede, el saldo queda incoherente para siempre.
- **`id` y `version` en cada evento.** El consumidor deduplica por `id` — un reintento sin dedupe
  infla las ventas en el panel del dueño, y ese es el peor bug posible: no falla, miente.
  La `version` existe porque despliegas la nube el martes y nadie fue a la tienda a actualizar
  la PC: en campo las dos puntas **nunca** están en la misma versión.
- **Inmutable y reproducible.** Eventos, nunca diffs de filas. Un diff no permite reconstruir la
  base local si el disco de la tienda muere; una secuencia de eventos sí.
- **Poda.** Una semana sin internet son miles de filas y una avalancha al reconectar. Batch al
  enviar y política de retención desde el día uno.

## Un endpoint que SUMA necesita llave; uno que REEMPLAZA no

Heredado del car wash, y aquí importa más porque todo puede llegar dos veces (reintento de cola,
doble clic, navegador cerrado a mitad del envío).

`POST` que agrega líneas: un reenvío duplica la venta y descuenta stock dos veces. `PUT` que
reemplaza la lista entera: repetirlo deja el mismo estado, idempotente por construcción.

**Al agregar cualquier endpoint de escritura, preguntar si es SUMA o REEMPLAZO.** Si suma, lleva
`clientRequestId` y pasa por el servicio de idempotencia — aunque hoy no se encole. En un sistema
contable un duplicado no es un error de pantalla: descuadra la caja.

Al terminar, probarlo de verdad: cortar la red, ejecutar, restaurar, dejar sincronizar, y
confirmar que quedó **una sola vez**.

## Los términos de una fórmula tienen que medir lo mismo

Cuatro cicatrices en dos días en el car wash. Si una cifra se compone de otras, **todos sus
términos miden lo mismo, en la misma unidad, con el mismo criterio de fecha.**

| Lo que divergió allá | Cómo se vio |
|---|---|
| El mismo concepto calculado en dos sitios | el resumen y la serie diaria del mismo mes se contradecían |
| Un canal contado en un término y no en otro | ingreso de mostrador contra costo de todos los canales |
| Un lado por fecha de documento, otro por fecha del hecho | factura vieja = compra fantasma en un mes cerrado |

**Falla en silencio**: el total suele seguir cuadrando. Lo que lo delata es que **las partes no
suman el total**. Aquí aplica directo a ganancia bruta vs neta, que el spec pide comparar: si el
ingreso sale de las ventas y el costo del kardex filtrado por tipo, no están midiendo lo mismo y
un producto rentable puede mostrarse con pérdida.

Al tocar un término, listar los demás y verificar uno por uno.

### Arreglar un cálculo no es arreglarlo: hay que buscar sus gemelos

Un cálculo de reportes vive típicamente en **cuatro** sitios: el resumen del período, la serie
por día, el PDF/exportación, y su gemelo del frontend. La serie diaria es la que más se olvida
porque no aparece en la pantalla que estabas depurando.

La prueba más barata que lo atrapa todo: **que el titular de una tarjeta cuadre con su propio
desglose.** Un desglose que no suma su total significa que dos cifras salen de sitios distintos.

## Construir sumando líneas, no restando

Una cifra obtenida por resta hereda todo lo que la resta da por sentado — y cuando la premisa
vence, **el total sigue cuadrando** y nadie lo ve. En el car wash la plata de accesorios quedó
contada como lavado durante semanas por esto.

Preferir siempre sumar desde las líneas hacia arriba. Si hay que restar, la prueba no es "el
total cuadra" sino **"las partes particionan el total"**: cada peso en exactamente una línea,
ninguna robándole a otra.

## Lo que NO se porta del car wash

El car wash es multi-tenant SaaS. RD Motors es una tienda. **No traer:** `Tenant` y su FK en cada
tabla, `Subscription`, `Plan`, `TenantModule`/`ProductModule`, el medidor de SMS/WhatsApp, el
libro de la plataforma, ni los guardias de módulo (`requireModule`).

**Sí traer, y es lo valioso:** los algoritmos. Costo promedio ponderado, el kardex append-only con
saldo después de cada movimiento, la conciliación de caja (`esperado = fondo + efectivo cobrado`,
tarjeta y transferencia fuera del cajón porque no entran al cajón), y la idempotencia.

Ojo: allá el código está en capas con las entidades JPA haciendo de dominio. **Aquí se porta, no
se copia** — se lee el algoritmo y se reescribe en la forma hexagonal. Copiar y renombrar arrastra
la estructura equivocada, y es exactamente lo que erosiona la ventaja de reuso sin que se note.

## El endpoint manda, no la pantalla

Ocultar un botón no protege nada. Si una operación no aplica, el bloqueo va en el caso de uso con
su prueba. En el car wash ya apareció un endpoint abierto cuyo único guardia era una pestaña
escondida.

Toda anulación deja constancia de auditoría: quién, cuándo, por qué. El spec lo pide explícito, y
es lo único que sostiene el control cuando el mismo cajero autoriza sus propias correcciones.

## Antes de dar algo por terminado

```bash
./mvnw -q clean test
```

**Siempre con `clean`.** La compilación incremental da BUILD SUCCESS con código que no compila.
Y si el backend está corriendo, bajarlo antes: `spring-boot:run` tiene `target/` tomado y el
`clean` falla con un error que parece de código y no lo es.

- **"Compila" no prueba que el cambio esté.** Un `if` que falta no es un error de tipos. Tras
  agregar un filtro o una validación, `grep` que el código existe de verdad.
- **Una prueba nueva sobre un bug debe verse fallar antes de darla por buena.** Revertir el
  arreglo, correrla, confirmar que falla, restaurar. Una prueba escrita después del arreglo que
  nunca falló no prueba nada.
- **Una prueba en verde puede no estar recorriendo lo que su nombre promete.** Ante una prueba que
  falla tras un cambio no relacionado, sospechar primero de la prueba.

## Reglas contables que no se negocian

- **`null` no es `0`.** Si un dato no se conoce se reporta "—" y se dice cuánto queda sin medir.
  Un cero inventado se lee como un hecho.
- **Costo vs gasto se deriva de la categoría, nunca se le pregunta al usuario.** Él elige QUÉ
  compró; el sistema sabe dónde cae.
- **Causación y caja no se mezclan en un mismo reporte.**
- **Una columna debe sumar leída de arriba abajo.** Una columna que no cuadra destruye la
  confianza en el reporte entero.
