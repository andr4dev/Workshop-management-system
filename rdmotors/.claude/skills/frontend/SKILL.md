---
name: frontend
description: Convenciones del frontend de RD MOTORS (React + Vite): la pantalla de venta, el modo offline con IndexedDB, el panel remoto del propietario y la impresión de tickets térmicos. Úsala al crear o modificar componentes, hooks, estilos o utilidades del cliente. Cubre por qué el POS es primero teclado, cómo no mentirle al usuario cuando no hay datos, y los errores que ya se colaron en el car wash del que se porta el módulo offline.
---

# Frontend — RD MOTORS

React + Vite, CSS Modules, sin TypeScript. **Vite no verifica tipos ni imports**: un build verde
no prueba que la pantalla no reviente. El lint sí lo prueba.

Dos clientes muy distintos comparten este código:

| Pantalla | Quién | Condición |
|---|---|---|
| Punto de venta | el cajero, con un cliente esperando enfrente | PC de la tienda, puede estar sin internet |
| Panel remoto | el dueño, desde el celular | siempre con internet, solo lectura |

Lo que sirve para uno estorba en el otro. Cuando dudes, la pantalla de venta gana.

## Cicatrices de este proyecto

Todo lo de esta sección son bugs que **ya pasaron aquí**, no advertencias de manual.

### Una etiqueta NO envuelve a un `<select>`

`<label><select/></label>` hace que el navegador **reenvíe el clic** al control: el desplegable
se abre por el clic nativo y se cierra por el reenviado. El síntoma es "a veces no abre", que es
lo peor posible — parece cosa del usuario.

```jsx
// MAL — el desplegable parpadea y no abre
<label>Categoría <select>…</select></label>

// BIEN — la etiqueta apunta, no envuelve
<label htmlFor={id}>Categoría</label>
<select id={id}>…</select>
```

`Campo` ya lo hace bien e inyecta el `id` al control que reciba. **No revertirlo a envolver.**

### `crypto.randomUUID()` no existe en la tablet

Solo está disponible en **contexto seguro** (https o localhost). El punto entero de este sistema
es que la tablet del pasillo entre por `http://192.168.x.x:5174`, que **no** lo es: ahí la función
es `undefined` y la pantalla revienta al abrir.

Se probó en el portátil —donde localhost sí es seguro— y por eso no se vio. Usar `idLocal()` de
`utils/formato.js`, que trae respaldo.

**La regla general:** toda API de navegador que dependa de contexto seguro hay que probarla
entrando por la IP de red, no por localhost. Lo mismo aplicará a la cámara o al portapapeles.

### Los elementos de React son inmutables

Inyectar una prop copiando el objeto del elemento **no funciona**:

```jsx
{ ...children, props: { ...children.props, id } }   // MAL, silenciosamente
cloneElement(children, { id })                       // BIEN
```

### Un mensaje de error tiene que poder limpiarse

Si el usuario corrige la causa, el mensaje debe irse. Un error pegado en un campo ya vacío hace
que el usuario crea que el sistema está roto — y no tiene forma de salir salvo borrar la fila.

**Regla:** el `onChange` que puede corregir un error, lo limpia.

### Un fallo de red NO es un error del formulario

Enrojecer el campo dice *"corrige esto"*, y no hay nada que corregir: el servidor no estaba.

| Qué pasó | Color | Qué ofrece |
|---|---|---|
| Dato inválido o regla de negocio | rojo | qué corregir |
| Sin conexión / servidor caído | **ámbar** | **botón de reintentar** |

Y un fallo de red **no debe marcar la operación como ya intentada**: cuando vuelva el servidor
hay que poder reintentar lo mismo sin reteclearlo.

### Ciclo foco ↔ búsqueda: blur pasivo, Enter explícito

Un modal que se abría al no encontrar un código **se reabría solo al cerrarlo**: el modal
devuelve el foco al campo, el campo pierde el foco, `onBlur` vuelve a buscar, no encuentra, abre
el modal. Sin salida.

La solución no es quitar el `onBlur` —hace falta para poder tabular— sino distinguir la intención:

- **blur** es pasivo: no repite una búsqueda ya hecha. Requiere recordar qué se consultó.
- **Enter** es explícito: siempre busca, aunque sea lo mismo.

### `JSON.parse` sobre lo que responda el servidor

Una página de error del proxy, un 502, un intermediario con timeout: nada de eso es JSON. Sin
`try`, el `SyntaxError` se escapa del manejo de errores y la pantalla muere con un mensaje que
nadie entiende. Ya está resuelto en `api/cliente.js`.

## La pantalla de venta es primero teclado

El spec lo dice sin decirlo: buscan **sin escanear código**, por código/nombre/marca/modelo, con
el cliente parado en el mostrador. Eso significa que el flujo completo —buscar, elegir, cantidad,
cobrar— tiene que hacerse sin soltar el teclado.

- El foco arranca en el buscador y **vuelve solo** ahí después de agregar un ítem.
- `Enter` sobre el resultado resaltado lo agrega. Flechas para moverse.
- Atajos para cobrar y para cancelar, visibles en pantalla, no secretos.
- Nada crítico detrás de un `hover`: no existe en la tablet ni ayuda con las manos ocupadas.

Un POS que obliga a apuntar y hacer clic para cada línea se siente lento aunque responda en 50 ms.
La percepción de velocidad la da no tener que cambiar de dispositivo de entrada.

## Nunca dejar cobrar sin stock — y nunca solo en pantalla

El spec es explícito: no se permite vender más unidades de las que hay. En pantalla eso es
deshabilitar y explicar por qué. Pero **el bloqueo de verdad va en el backend** (ver la skill
`backend`): esconder el botón no protege nada, y menos con una cola offline que reenvía.

Igual con las anulaciones y los descuentos por encima del tope: la pantalla guía, el endpoint
decide.

## Modo offline: portado del car wash, con las mismas reglas

El módulo de `src/offline/` del car wash (IndexedDB, `queue`, `syncQueue`, cachés por dominio) se
porta y sirve casi tal cual. Con él vienen sus tres cicatrices, que se repiten solas si no se
vigilan:

**1. Tras una mutación, invalidar o sobrescribir el caché que quedó viejo.**
No basta con recargar la vista: si el dato cacheado sigue ahí, la pantalla vuelve a leerlo. En el
car wash una compra reponía stock y la venta directa seguía diciendo "Agotado" hasta recargar a
mano — el merge offline "conserva el stock más bajo" y confundía una compra reciente con una venta
sin sincronizar. La función que lo arreglaba **ya existía y nadie la llamaba ahí**.

Antes de escribir una función de caché nueva, buscar si ya está.

**2. Distinguir "sin datos" de "cero".** Un caché vacío no es un día sin ventas. Si no hay datos
locales hay que decirlo, no pintar $0. Un cero inventado se lee como un hecho y en este sistema
ese hecho es "no vendimos nada hoy".

**3. Marcar lo que requiere conexión.** Exportar a PDF/Excel o consultar el histórico del servidor
no funciona sin internet: el botón avisa en vez de fallar en silencio.

**Probarlo de verdad**: DevTools → Network → Offline, ejecutar la operación, volver a online,
dejar sincronizar, y confirmar que quedó consistente **sin recargar a mano**. Recargar enmascara
justo el bug que buscas.

## El panel del dueño tiene que decir cuándo se actualizó

Regla propia de este proyecto, y no es cosmética.

El panel remoto es **eventualmente consistente**: si el dueño lo abre a las 3pm y la tienda lleva
sin internet desde mediodía, ve las cifras de mediodía. Sin un letrero de *"última actualización:
12:04"* va a concluir que dejaron de vender y va a llamar a reclamarle al cajero.

Ese letrero es la diferencia entre que confíe en el sistema o desconfíe de su empleado. Va visible
en el encabezado, no escondido en un tooltip, y **cambia de aspecto cuando el dato está viejo**.

Corolario: el panel nunca muestra una cifra sin contexto temporal. "Ventas de hoy: $840.000" sin
decir hasta cuándo es una afirmación que el sistema no puede sostener.

## Dinero en pantalla

- Toda columna de montos lleva `font-variant-numeric: tabular-nums`. Sin él los dígitos bailan y
  la columna deja de poder verificarse a ojo — y aquí que **la columna sume leída de arriba abajo**
  es un requisito del negocio, no un detalle estético.
- Un solo helper de formato COP, compartido. Sin decimales.
- El total del ticket y el total del reporte salen del **mismo** cálculo. Si el frontend recalcula
  por su cuenta lo que el backend ya calculó, van a divergir; y cuando diverjan, el cliente le va a
  creer al papel.

## Venta interrumpida: se retoma, no se pierde

El spec pregunta qué pasa si se corta la luz a mitad de una venta. La respuesta es que al volver,
el cajero encuentra el carrito donde lo dejó y decide si lo retoma o lo descarta.

El borrador se persiste localmente en cada cambio del carrito, no al final. Un borrador que solo
se guarda al cobrar no sirve para nada — el corte ocurre antes de cobrar, que es todo el punto.

## Ticket térmico

Impresora POS de 80 mm. Eso es una hoja de ~72 mm de ancho útil, monoespaciada y sin color.

- Estilos de impresión propios (`@media print` con ancho fijo), no reusar los de pantalla.
- Probar con el papel real antes de darlo por hecho: lo que se ve bien en la vista previa del
  navegador se corta en la ticketera.
- Si el navegador no puede hablar con la impresora directamente, eso es un puerto del backend, no
  un problema del frontend. Ver la skill `backend`.

## Modo oscuro y responsive — siempre

- **Todo color por token** (`var(--text-primary)`, `var(--card-bg)`, `var(--border)`). Nunca un hex
  suelto en un `.module.css`. Al agregar una superficie, comprobar los dos temas.
- Layout con flex/grid y `gap`, nunca márgenes por elemento.
- En grids con inputs, `min-width: 0` es obligatorio: sin él el ancho intrínseco del input desborda
  su columna.
- Tablas y contenido ancho: `overflow-x: auto` en su propio contenedor. El `body` nunca scrollea
  horizontal.
- El panel del dueño se diseña **primero en móvil**: es donde realmente se usa.

## Reglas de hooks: dos límites, no uno

Al insertar un hook hay que respetar las dos fronteras a la vez. Cumplir una y olvidar la otra
rompió dos pantallas en el car wash.

- **Por arriba**: un hook que recibe un valor va DESPUÉS de la línea que lo declara. Antes da
  *"Cannot access X before initialization"*.
- **Por abajo**: va ANTES de cualquier `return` de carga o error. Después da *"Rendered more hooks
  than during the previous render"*.

**Ninguna de las dos la ve el lint.** Compila, construye, pasa las pruebas, y solo revienta al
abrir la pantalla. Y como el error boundary lo atrapa, el síntoma visible fue la página de "sin
conexión" — con la conexión perfectamente bien.

## Acumuladores: sembrar TODOS los campos

Al agregar un campo a un objeto que se acumula por día hay que sembrarlo en los **tres** sitios: la
semilla inicial, la conversión por entrada y el retorno agregado. Olvidar uno da `NaN` o
`undefined` en pantalla. En el car wash pasó cuatro veces en el mismo objeto, incluso arreglando
los otros tres.

Al tocar ese tipo de objeto: **revisar el objeto completo, no solo el campo que motivó el cambio.**

## Un comentario dentro de JSX va entre llaves, o se imprime

`{/* ... */}` es comentario. `/* ... */` suelto entre dos elementos hermanos **es texto**, y React
lo pinta. En el car wash estuvo 18 días visible en la pantalla de reportes.

Lo grave es qué no lo atrapó: el lint pasa, el build pasa, las pruebas pasan. Solo lo delata mirar
la pantalla. Se cuela al **mover** un comentario válido a la zona de marcado durante una
reestructuración grande — al reacomodar JSX, revisar los comentarios que viajaron con él.

## Cascada CSS: una clase, una definición

Añadir un segundo bloque con el mismo selector para cambiar una propiedad **pisa el primero
entero**. Se modifica la regla existente en su sitio. Antes de agregar un selector, comprobar que
no exista ya:

```bash
grep -n "^\.laClase" src/**/*.module.css
```

## Pruebas puras

La lógica de cálculo va en `src/utils/*.js` **sin JSX ni formato**, con su `*.test.js` al lado
(`node:test`). `node --test` corre sin bundler: **los imports necesitan la extensión `.js`**. Vite
la resuelve sin ella, así que el fallo solo aparece al correr las pruebas.

## El diseño: rojo, plata y grafito

Los tokens salen del logo de RD Motors, con los tonos extraídos del PNG (`#F80000`, `#C8C8C8`,
`#000000`). Tres reglas que no se negocian:

- **El rojo es acento, no dominante.** El logo ya lo dice: MOTORS es negro, la R es plata, solo la
  D lleva rojo. Grafito domina, plata separa, rojo acentúa.
- **El rojo de marca NO es el rojo de error.** `--brand` vive en cabecera y botón primario;
  `--danger` es más profundo, vive en el contenido, y **nunca comunica solo con color**: siempre
  icono y borde. Si fueran el mismo, el cajero no distinguiría la app de una alarma.
- **Nada de degradados cromados.** El logo los tiene; la interfaz no. Envejecen mal y perjudican
  la legibilidad, que aquí pesa más: se lee con un cliente enfrente y a veces con reflejo.

Todo color por token. Nunca un hex suelto en un `.module.css`.

## Antes de dar algo por terminado

```bash
npx eslint src/<archivos-que-tocaste>   # no-undef = crash en runtime
node --test "src/utils/*.test.js"
npx vite build
```

El lint es el paso que **no** se salta. En el car wash se usó una función sin importarla, el build
pasó feliz, y la pantalla habría reventado al escribir en el campo.


**Y el build tampoco basta.** De las cicatrices de arriba, **ninguna** la habría atrapado el
build: el `<select>` envuelto compila, el `crypto.randomUUID` compila, el error pegado compila.
Lo único que los delata es abrir la pantalla — y en el caso del UUID, abrirla **desde la IP de
red, no desde localhost**.

```bash
# Mínimo antes de dar por terminada una pantalla:
#   1. Abrirla en claro y en oscuro
#   2. Abrirla desde la IP de red (la tablet), no solo localhost
#   3. Apagar el backend y comprobar que el fallo se ve y se puede reintentar
```
