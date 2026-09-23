# Plan 0005 — Ver el catálogo en el mostrador

**Traduce:** `docs/specs/0005-catalogo-en-el-mostrador/spec.md`. La única pregunta abierta, qué
cuentan los números de las categorías, queda resuelta con la recomendación A: **lo buscado**. Se
aceptó al pedir el plan.

**Estado:** aprobado el 2026-09-15 · **3 de 3 fases ✅ completas** · falta la verificación manual del usuario.

---

## Contexto

El cliente dice *"muéstreme qué aceites tiene"* y hoy el cajero solo tiene una lista flotante de 8
que se cierra al agregar (`BuscadorVenta.jsx:36`). El spec pide dos cosas:

- un **catálogo con F2** dentro de Vender, con categorías, buscador y conteo por categoría de lo
  buscado;
- que **todo repuesto nuevo nazca con categoría**. En QA, 6 de 8 no tienen, porque hoy es opcional
  (`CrearRepuesto.java:66-68`) y corregir la ficha deja quitarla (`ActualizarRepuesto.java:80-81`).

Lo que ya existe y se reusa:

| Pieza | Dónde |
|---|---|
| Listado de inventario por páginas, sin tildes, trae categoría, precio, stock y costo | `BuscarRepuestos.inventario` (`domain/…/inventario/aplicacion/BuscarRepuestos.java:63`) → `RepositorioVariantes.listar` → `RepositorioVariantesJpa.java:117-137` |
| Única llamada al `listar` del puerto | `BuscarRepuestos.java:68`; `inventario(texto, soloStockBajo, …)` lo llaman 18 sitios |
| `RepuestoEncontrado` con `categoriaId` y `categoria` | `inventario/aplicacion/RepuestoEncontrado.java` |
| Categorías activas en orden | `GET /api/categorias` (`CatalogoController.java:34`) · `Categoria.nueva(nombre, orden)` |
| Agregar a la venta con las reglas de precio y stock | `Vender.jsx` `agregar()` → `utils/venta.js:56` (`problemaParaAgregar`), `:66` (`agregarRenglon`) |
| Foco por efecto al volver al buscador | `Vender.jsx` `enfocarBuscador` (`pedidoDeFoco`) |
| Aviso de carga ámbar/rojo con Reintentar | `componentes/AvisoCarga.jsx` |
| Formulario de repuesto (crear, corregir borrador, editar ficha) | `componentes/compra/ModalRepuestoNuevo.jsx` (categoría en `:215-221`), usado en `Compra.jsx:714` y `FichaRepuesto.jsx:292` |
| Filtros del inventario en la URL (`?q=`, `?bajo=1`) | `paginas/Inventario.jsx:23-26`, `cambiarParametro` |

---

## Resumen

| Fase | Qué | Pantalla | Migración |
|---|---|---|---|
| 1 | Categoría obligatoria al crear y al corregir | Compras, Ficha | — |
| 2 | Filtrar por categoría, ordenar con stock primero, contar por categoría; filtro *Sin categoría* | Inventario | — |
| 3 | El catálogo con F2 en Vender | Vender | — |

---

## Decisiones tomadas al planear

1. **Los números cuentan lo buscado** (spec §4, opción A).
2. **La regla de la categoría vive en `Producto`**, en `nuevo()` y en `corregir()`, no en el
   formulario ni suelta en un caso de uso.
   - Así la exigen crear, corregir y registrar una compra con repuesto nuevo, sin repetirla.
   - Costo: las pruebas que construyen productos sin categoría ganan una. Son 21 `Producto.nuevo` y
     24 `conConceptoNuevo`, cambios de construcción; ninguna aserción cambia.
3. **Sin migración.** La columna sigue admitiendo nulos porque hay filas viejas sin categoría. La
   entidad que carga JPA no pasa por `nuevo()`, así que leer los viejos no revienta.
4. **Un solo listado para el inventario y el catálogo**: el mismo endpoint gana filtros.
   - Así el texto sin tildes y las páginas no se escriben dos veces.
   - La condición de texto se escribe **una vez**, como constante, y la usan el listado, su conteo
     de páginas y el conteo por categoría. Sin eso, los números de las categorías podrían no sumar
     lo que muestra la lista.
5. **Filtro de categoría con un modo** (`TODAS` / `UNA` / `SIN`) y un id de relleno cuando no aplica.
   Postgres no infiere el tipo de un parámetro UUID nulo; es el mismo truco de `:texto = ''` que ya
   usa el listado.
6. **El catálogo no es un modal.** Reemplaza la columna izquierda de Vender (buscador y renglones)
   mientras está abierto. El resumen con el total y *Cobrar* sigue a la vista, como se decidió con el
   usuario, y no hay fondo oscuro.
7. **La respuesta del catálogo trae el costo, pero no se muestra.** El aviso de venta a pérdida lo
   necesita al agregar, igual que con el buscador hoy. Ocultarlo de la respuesta es trabajo de roles
   (spec 0004).
8. **"La última categoría elegida" se deriva de los renglones de la compra** (el último `repuestoNuevo`
   con categoría), sin estado nuevo. Se reinicia sola con cada compra.
9. **`BuscarRepuestos.inventario(texto, soloStockBajo, pagina, tamano)` se conserva y delega.**
   Tiene 18 llamadas; solo cambia la firma del puerto, que tiene una.

---

## Fase 1 · Categoría obligatoria (H3; RF-009, RF-010, RF-011) — ✅ COMPLETA

| Pieza | Dónde cae |
|---|---|
| `Producto.nuevo` y `Producto.corregir` exigen categoría: *"Escoge la categoría: es como se encuentra en el catálogo del mostrador"* | `inventario/dominio/Producto.java:63` y `:76` |
| `CrearRepuesto` y `ActualizarRepuesto` no cambian: un `categoriaId` nulo llega como `null` al dominio y el dominio lo rechaza. Una marca nueva de un concepto existente hereda su categoría | `inventario/aplicacion/` |

**Frontend:**
- `ModalRepuestoNuevo.jsx`:
  - la categoría es obligatoria (`requerido`), con el marcador *"Escoge la categoría"* en vez de
    *"Sin clasificar"*;
  - se valida al confirmar cuando no se eligió un concepto existente;
  - nueva prop `categoriaSugerida`, que se usa solo al **crear** (sin `datosPrevios` ni `existente`).
- `utils/compra.js`: `ultimaCategoriaElegida(renglones)`, con su prueba.
- `Compra.jsx`: pasa esa categoría al modal.
- `FichaRepuesto` usa el mismo modal: una ficha vieja sin categoría pide escogerla para guardar.
- `http/02-errores.http`:
  - las altas sin `categoriaId` ganan una;
  - se agrega *"concepto nuevo sin categoría → 422"*.

**Pruebas:**
- `CrearRepuestoTest`:
  - concepto nuevo sin categoría → 422 y no se guarda nada;
  - marca nueva sobre concepto existente sin categoría → se crea con la del concepto.
- `ActualizarRepuestoTest`:
  - quitar la categoría → 422 y la ficha queda igual;
  - una ficha vieja sin categoría no se guarda sin escoger una.
- `RegistrarCompraTest`: una compra con un repuesto nuevo sin categoría no registra nada.
- Ajuste de construcción en las pruebas existentes:
  - en dominio, con `Categoria.nueva(...)` o sembrada en `CategoriasEnMemoria`;
  - en integración, con una categoría sembrada (`categorias.activas().getFirst()`).
- Integración: un repuesto viejo sin categoría, insertado por SQL, se sigue listando y leyendo.
- `compra.test.js`: `ultimaCategoriaElegida`.

**Checkpoint:**
- Crear un repuesto sin categoría no deja, ni desde la pantalla ni por la API.
- El segundo repuesto nuevo de una compra arranca con la categoría del primero.
- Corregir una ficha no deja la categoría vacía.

**Cómo quedó (2026-09-15):**

- Por la API contra QA: un concepto nuevo sin categoría responde 422 con el mensaje, y no queda
  nada creado.
- En el navegador, contra QA, en oscuro a 1360 px y en claro a 390 px:
  - al abrir la ficha de ABC123, que no tenía categoría, arranca en *"Escoge la categoría"*. Guardar
    sin escogerla muestra el mensaje y el modal sigue abierto. Con FILTROS se guardó. **ABC123
    quedó en FILTROS en la base de QA**;
  - en *Registrar compra*: el repuesto nuevo CAT-PRUEBA-1 sin categoría no se agrega y dice por qué,
    y el aviso se va al escogerla. El siguiente, CAT-PRUEBA-2, arrancó con FRENOS. Se canceló sin
    registrar: no quedó nada.

---

## Fase 2 · Filtrar y contar por categoría (RF-002, RF-003, RF-005, RF-007, RF-012) — ✅ COMPLETA

| Pieza | Dónde cae |
|---|---|
| `FiltroCategoria` — `todas()`, `de(UUID)`, `sinCategoria()`, y `desde(categoriaId, sinCategoria)`, que rechaza pedir las dos | `inventario/dominio/` |
| `ConsultaInventario(texto, soloStockBajo, categoria, conStockPrimero)` | `inventario/dominio/` |
| `ConteoCategoria(categoriaId, nombre, repuestos)` — `categoriaId` nulo = sin categoría | `inventario/dominio/` |
| Puerto: `listar(ConsultaInventario, pagina, tamano)` reemplaza la firma de 4 argumentos; nuevo `contarPorCategoria(texto)` (orden de la categoría, *sin categoría* al final, solo las que tienen algo) | `inventario/dominio/puerto/RepositorioVariantes` |
| `BuscarRepuestos.inventario(ConsultaInventario, …)` y `conteoPorCategoria(texto)`; la firma vieja delega | `inventario/aplicacion/` |
| Consultas: condición de texto como constante compartida; `left join p.categoria c` con el modo; orden `case when :conStockPrimero = true and v.stock <= 0 then 1 else 0 end, p.nombre, v.marcaRepuesto, v.codigo`; conteo con `group by c.id, c.nombre, c.orden order by c.orden nulls last` | `pos/…/inventario/infraestructura/RepositorioVariantesJpa` |
| `GET /api/inventario` gana `categoriaId`, `sinCategoria`, `conStockPrimero`; nuevo `GET /api/inventario/categorias?q=` → `[{categoriaId, nombre, repuestos}]` | `InventarioController` |
| El doble imita filtro, orden y conteo | `Falsos.VariantesEnMemoria` |

**Frontend:**
- `api/cliente.js`: `inventarioApi.listar` con los filtros nuevos, e `inventarioApi.categorias(texto)`.
- `Inventario.jsx`: filtro **Sin categoría** (`?sin=1`) junto a *Stock bajo*, con su *quitar filtro*.

**Pruebas:**
- `BuscarRepuestosTest`:
  - una categoría;
  - sin categoría;
  - con stock primero, y desempate por nombre;
  - el conteo con texto sin tildes: suma lo que lista *Todas*, sin las categorías en cero y con
    *sin categoría* al final;
  - pedir categoría y *sin categoría* a la vez → 422.
- Integración contra Postgres:
  - **acuerdo**: para varias búsquedas, el conteo por categoría suma exactamente el total del listado
    con el mismo texto, y cada categoría suma lo que lista filtrada por ella;
  - un repuesto sin categoría sale con `sinCategoria`;
  - paginación con filtro y orden estable.
- `.http`: los dos endpoints.

**Checkpoint:**
- `/inventario?sin=1` muestra los 6 de QA; se corrige uno y sale de la lista.
- `GET /api/inventario/categorias?q=aceite` suma lo mismo que el listado con `q=aceite`.

**Cómo quedó (2026-09-15), fases 1 y 2:**

- **Pruebas:**
  - dominio 242 (7 nuevas: 2 en `CrearRepuestoTest`, 2 en `ActualizarRepuestoTest` y 1 en
    `RegistrarCompraTest` para la regla; 4 en `BuscarRepuestosTest`, menos 2 que decían lo contrario);
  - Postgres 55 (2 nuevas);
  - frontend 128 (1 nueva);
  - `./mvnw clean test`, lint y build en verde.
- **Por la API, contra QA:**
  - `sinCategoria=true` trae los 6 sin categoría;
  - pedir categoría y *sin categoría* a la vez responde 422;
  - los conteos por categoría suman el total del listado en «» (8), «aceite» (3) y «filtro» (4);
  - FILTROS con stock primero trae ABC23.
- **En el navegador:** el botón *Sin categoría* del inventario deja las 6 filas (`?sin=1`). Tras
  corregir ABC123 quedan 5. A 390 px no hay desborde.
- **Rotas a propósito, y fallaron exactamente sus pruebas:**
  - un concepto nuevo sin la regla → `categoriaObligatoria` y `repuestoNuevoSinCategoria`;
  - el doble sin "stock primero" → `stockPrimero`;
  - el conteo sin la condición de texto → `conteoPorCategoriaDeAcuerdoConLaLista`;
  - `join` en vez de `left join` → `sinCategoriaYStockPrimeroContraPostgres`;
  - la consulta sin "stock primero" → la misma.

---

## Fase 3 · El catálogo en Vender (H1, H2, H5; RF-001 a RF-008) — ✅ COMPLETA

**Frontend:**
- `utils/catalogo.js` + `catalogo.test.js`, lógica pura:
  - `chipsDeCategorias(conteos)`: *Todas* con la suma, luego en orden, *Sin categoría* al final;
  - `consultaDelCatalogo({ texto, categoria, pagina })`: páginas de 50, con stock primero;
  - `juntarPaginas(anteriores, nueva)`: *Ver más* sin duplicados;
  - `moverResaltado(indice, largo, tecla)`;
  - `sePuedeAgregar(repuesto, bloqueado)`: sin stock o venta bloqueada, no.
- `componentes/venta/Catalogo.jsx` + css:
  - **controles:** buscador propio con pausa de 250 ms, categorías como botones y lista con nombre,
    marca, moto, código, *hay N* o *sin stock* (apagado), precio y **[+]**;
  - **teclado:** flechas, **Enter** agrega, **Esc** cierra;
  - **agregar:** usa `onAgregar`, que es `agregar()` de Vender y devuelve el problema; el mensaje se
    muestra en una línea `role="status"` y el catálogo sigue abierto;
  - **Ver más:** trae los siguientes 50;
  - **sin red:** `AvisoCarga`.
- `paginas/Vender.jsx`:
  - estado `viendoCatalogo`; **F2** lo abre y lo cierra;
  - botón *Catálogo F2* junto al buscador, y el subtítulo dice *F2 catálogo*;
  - abierto, la columna izquierda muestra `<Catalogo>` en vez de buscador y renglones, y el resumen
    queda igual;
  - al cerrar, `enfocarBuscador()`;
  - F4 y F9 cierran el catálogo y abren su modal;
  - con la venta bloqueada o sin turno, el catálogo se ve pero no agrega.

**Pruebas:** `catalogo.test.js`. En el navegador, con Edge y CDP, contra QA, en oscuro, claro y 390 px:
- F2 abre y Esc vuelve con el cursor en el buscador;
- *Filtros* deja solo filtros, con precio y stock, sin costo;
- *aceite*: los números de las categorías coinciden con `GET /api/inventario/categorias`;
- los sin stock salen al final, apagados, sin [+];
- [+] y Enter agregan, el resumen suma, y agregar la última unidad dos veces dice cuántas hay;
- con un cobro sin respuesta simulado, no agrega;
- sin desborde horizontal a 390 px.

*Ver más* con más de 50 lo cubren la prueba de `juntarPaginas` y la de paginación contra Postgres:
QA no tiene 50 repuestos en una categoría.

**Checkpoint:** los criterios de *Catálogo* del spec §8, desde el navegador.

**Cómo quedó (2026-09-15):**

- Frontend 138 pruebas (10 nuevas en `catalogo.test.js`); lint y build en verde. El backend no cambió
  en esta fase.
- En Edge, contra QA, en oscuro a 1360 px y en claro a 390 px (sin desborde):
  - **F2 y los números:** F2 abre el catálogo con el cursor en su buscador y el total a la derecha.
    Las categorías (*Todas 8 · Frenos 1 · Filtros 2 · Sin categoría 5*) coinciden con
    `/api/inventario/categorias`, y no se ve ningún costo;
  - **filtros y búsqueda:** *Filtros* deja ABC123 y ABC23, lo mismo que la API. Con *aceite*:
    *Todas 3 · Filtros 1 · Sin categoría 2*, igual que la API;
  - **agregar:** ↓↓ y Enter agregó DEMO-FEAFB y el catálogo siguió abierto. Siete veces [+] en
    PRUEBA-144927, que tiene 6: la séptima dijo *"Ya están en la venta las 6 unidades…"*;
  - **Esc:** vuelve a la venta con el cursor en su buscador y los renglones agregados;
  - **agotado** (respuesta simulada, QA no tiene uno): sale al final, apagado, *sin stock*, con [+]
    deshabilitado, y el clic en la fila dice *"No hay unidades de ABC123."*;
  - **bloqueado:** con una venta pendiente y sin turno (simulado), se ve pero no agrega, y dice por qué;
  - **F9 con el catálogo abierto:** abre el cobro encima; Esc lo cierra y el cursor vuelve al
    catálogo. F2 lo cierra y el cursor vuelve a la venta. No se creó ninguna venta.
- Rotas a propósito, y fallaron exactamente sus pruebas: agregar con la venta bloqueada, la categoría
  elegida sin nada que no vuelve a *Todas*, y *Ver más* que repite.

---

## Riesgos

| Riesgo | Fase | Qué hacer |
|---|---|---|
| El parámetro UUID nulo no tiene tipo en Postgres | 2 | Modo + id de relleno; la integración lo prueba en los tres modos |
| `order by case …` con paginación y su consulta de conteo | 2 | El conteo no ordena; prueba de orden estable entre páginas |
| El conteo por categoría y la lista no suman lo mismo | 2 | Condición de texto compartida y prueba de acuerdo |
| Muchas pruebas construyen productos sin categoría | 1 | Cambio mecánico de construcción, listado en la bitácora; ninguna aserción cambia |
| Corregir el precio de una ficha vieja obliga a escoger categoría | 1 | Es lo que pide RF-011; el mensaje dice por qué |

---

## Verificación final

1. Backend abajo y `./mvnw clean test`.
2. `cd frontend && npx eslint src/ && node --test src/utils/*.test.js && npx vite build`.
3. Criterios del §8 del spec, uno por uno, desde el navegador (capturas en claro, oscuro y 390 px).
4. `.http` al día (`02-errores.http` y los dos endpoints nuevos).
5. Romper a propósito y ver fallar su prueba:
   - quitar la regla en `Producto.nuevo` → `CrearRepuestoTest`;
   - el conteo sin la condición de texto → la integración de acuerdo;
   - `join` en vez de `left join` en *sin categoría* → la integración;
   - sin "stock primero" → `BuscarRepuestosTest`.

---

## Bitácora de decisiones

Se llena **durante** la implementación, con fecha, fase, decisión y porqué.

| Fecha | Fase | Decisión | Por qué |
|---|---|---|---|
| 2026-09-15 | 1 | **Tres pruebas que decían lo contrario cambiaron su aserción**: "la categoría es opcional" (`CrearRepuestoTest`), "se puede dejar sin categoría" (`ActualizarRepuestoTest`) y la auditoría de ficha que la vaciaba (integración) | El plan decía que ninguna aserción cambiaba. Estas tres probaban justo la regla que el spec invierte. Las dos del dominio pasan a probar el rechazo; la de integración cambia la categoría por MOTOR en vez de vaciarla |
| 2026-09-15 | 1 | **Las pruebas usan `Categoria.nueva("PRUEBAS", 99)`**, y las de integración una sembrada (`categorias.activas().getFirst()`) | Un cambio de construcción. En la integración la categoría tiene que existir en la base por la llave foránea; en el dominio basta un objeto |
| 2026-09-15 | 1 | **La categoría sugerida solo aplica al crear**, y la ficha vieja sin categoría arranca con *"Escoge la categoría"* (opción deshabilitada), no con la sugerida | Sugerir en una corrección le pondría al repuesto la categoría de otro sin que el administrador la escoja |
| 2026-09-15 | 1 | **Las altas de `02-errores.http` no cambiaron** | El código repetido y el concepto doble se revisan antes que la categoría, así que siguen dando 409 y 422 por su motivo. Se agregaron los dos casos nuevos |
| 2026-09-15 | 2 | **Las condiciones de texto y de categoría son constantes de la interfaz de Spring Data**, concatenadas en las tres anotaciones `@Query` | Las anotaciones solo aceptan constantes de compilación. Así el listado, su conteo de páginas y el conteo por categoría leen el mismo texto, y la prueba de acuerdo lo confirma |
| 2026-09-15 | 2 | **`FiltroCategoria.admite(categoria)` es la regla en memoria**, y el doble filtra con ella | Como `BusquedaDeRepuesto.coincideCon` en compras: el doble no escribe su propia versión del filtro, y la consulta de Postgres se prueba contra la misma idea |
| 2026-09-15 | 2 | **En el inventario, "Sin clasificar" pasa a decir "Sin categoría"** | Es el nombre del filtro nuevo y del botón del catálogo. Dos nombres para lo mismo confunden |
| 2026-09-15 | 2 | **El conteo no se filtra por stock bajo** | Solo lo usa el catálogo, que no tiene ese filtro. Si el inventario lo necesitara, se agrega el parámetro |
| 2026-09-15 | 3 | **Sin turno, el catálogo también se abre** (*Ver catálogo F2* en el aviso de turno), solo para ver | RF-008 dice que sin turno se ve pero no agrega. Sin turno, Vender no muestra el buscador, así que el catálogo tenía que poder abrirse desde el aviso |
| 2026-09-15 | 3 | **F4 y F9 abren su modal encima del catálogo, sin cerrarlo; cobrar sí lo cierra** | Si lo cerraran, el modal anotaría como foco previo un campo que desaparece, y al cancelar el cursor quedaría en la nada. Cobrado, el siguiente cliente arranca en el buscador de la venta |
| 2026-09-15 | 3 | **Un clic en la fila agrega igual que [+], y en un agotado dice por qué** | Pasa por las reglas de la venta y muestra su mensaje ("No hay unidades de…"), en vez de no hacer nada y dejar al cajero dudando si el clic funcionó |
| 2026-09-15 | 3 | **Si la categoría elegida se queda sin nada de lo buscado, se filtra por *Todas*** (`categoriaVigente`) | Con *Frenos* elegida y *aceite* escrito, su botón desaparece; filtrar por ella dejaría la lista vacía sin que se vea por qué |
| 2026-09-15 | 3 | **Los botones y las filas no le quitan el cursor al buscador del catálogo** (`onMouseDown` sin foco) | Se toca una categoría o se agrega con el mouse, y las flechas, Enter y Esc siguen funcionando sin volver a hacer clic en el campo |
| 2026-09-15 | 3 | **Las categorías se muestran con mayúscula inicial** ("Lubricantes y quimicos") | Las sembradas están en mayúsculas y sin tildes; en botones seguidos, las mayúsculas se leen como gritos. No se tocó la base |
| 2026-09-15 | 3 | **El agotado se verificó en el navegador con una respuesta simulada** | QA no tiene ningún repuesto sin stock. El orden de verdad ("stock primero") lo prueba la integración contra Postgres; el navegador prueba cómo se pinta y que no deja agregar |
| 2026-09-15 | 3 | **En el teléfono el resumen queda debajo del catálogo** | Es lo mismo que pasa con los renglones: a 390 px no caben lado a lado. El cajero del mostrador usa el computador |
