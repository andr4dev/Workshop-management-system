# Spec 0005 — Ver el catálogo en el mostrador

**Estado:** cerrado · [plan](plan.md) aprobado el 2026-09-15 · **implementado: 3 de 3 fases** · falta la verificación manual
**Rebanada:** extiende la 2 · ver [`PLAN_DE_TRABAJO.md`](../../PLAN_DE_TRABAJO.md)
**Depende de:** [spec 0001](../0001-catalogo-y-compras-usables/spec.md) (catálogo y categorías) y
[spec 0003](../0003-venta-de-mostrador/spec.md) (pantalla de venta), implementados

> **Convención:** lo que dice *"hoy"* está verificado con `archivo:línea`. Lo que dice *"debe"* es
> propuesta y todavía no existe. Las dos cosas nunca se mezclan.

---

## 1. Objetivo de negocio

El cliente llega y dice *"muéstreme qué aceites tiene"* o *"¿qué pastillas de freno hay?"*. Hoy el
cajero tiene que escribir, y ve una lista flotante de ocho que se cierra en cuanto agrega uno. No
puede **mostrarle al cliente lo que hay** de un tipo de repuesto, con marcas, precios y stock lado a
lado, para que elija.

Eso vende: con las tres marcas de pastillas a la vista, el cliente escoge la que le sirve en vez de
irse porque "no había la que pedí".

---

## 2. Caso de uso

### P1 — bloquean

**H1 · Mostrar lo que hay de un tipo.**
El cliente pide ver las pastillas de freno. El cajero oprime **F2**: se abre el catálogo con las
categorías arriba. Toca *Frenos* y ve todas las pastillas con marca, moto, precio y cuántas hay.
Agrega la que el cliente escoge con **[+]** o **Enter**, y **Esc** lo devuelve a la venta con el
total a la vista.
*Se demuestra solo:* con F2 y un clic en *Frenos* se ven todas las pastillas; se agrega una y queda
en la venta.

**H2 · Buscar dentro del catálogo.**
*"¿Qué aceites tiene?"*: en el catálogo se escribe *aceite*. La lista muestra todo lo que tiene esa
palabra, en cualquier categoría, y las categorías dicen cuántos hay en cada una: *Filtros 2*,
*Lubricantes y químicos 5*. Así el cajero ve de un vistazo que hay filtros de aceite y aceites de
motor, y toca la que era.
*Se demuestra solo:* se escribe *aceite*; *Filtros* dice 2 y *Lubricantes y químicos* dice 5; al
tocar *Lubricantes* quedan solo los 5.

**H3 · Todo repuesto nuevo nace con categoría.**
Al crear un repuesto (desde una compra), la categoría es obligatoria. En una factura de veinte
renglones nuevos, el formulario recuerda la última categoría elegida para no escogerla veinte veces.
*Se demuestra solo:* crear un repuesto sin categoría no deja guardar y dice por qué; el siguiente
repuesto de la misma factura arranca con la categoría del anterior.

### P2 — importantes, no bloquean

**H4 · Encontrar y arreglar los que no tienen categoría.**
Los repuestos creados antes de este cambio pueden no tener categoría. En el catálogo salen en
*Sin categoría*, y en el inventario se filtran por *Sin categoría* para corregirlos desde su ficha.
*Se demuestra solo:* el filtro *Sin categoría* del inventario muestra los 6 de la base de QA; se
corrige uno y deja de salir.

**H5 · Lo que no hay también se ve.**
*"¿Tiene la FACTORY?"* — "no, pero tengo la INOKI". Los repuestos sin stock salen al final de la
lista, apagados y con *sin stock*, y no se pueden agregar.
*Se demuestra solo:* en *Filtros*, el FACTORY sin stock sale último, apagado, sin botón de agregar.

### P3 — deseables

**H6 · Filtrar por moto.** *"Lo que tenga para la NS 200."* Hoy la moto es texto libre del proveedor
("PULSAR NS 200/FI/AS 200-DUKE 200"), así que escribir *ns 200* en el buscador del catálogo ya lo
encuentra. Un filtro de moto de verdad necesita normalizar modelos: queda fuera (§10).

---

## 3. Qué existe hoy

| Qué | Dónde | Estado |
|---|---|---|
| El buscador de la venta pide **8** resultados y los muestra en una lista flotante | `frontend/src/componentes/venta/BuscadorVenta.jsx:36` y `:124` | funciona; no sirve para mostrar lo que hay |
| Busca a partir de 2 letras; sin texto no muestra nada | `BuscadorVenta.jsx:6` y `:28` | no hay forma de ver sin escribir |
| El listado del inventario busca por código, nombre, marca y aplicación, **sin tildes**, por páginas | `pos/…/inventario/infraestructura/RepositorioVariantesJpa.java:117-131` · `InventarioController.java:36-40` | funciona; **no filtra por categoría** |
| Ordena por nombre, marca y código | `RepositorioVariantesJpa.java:130` | los sin stock salen mezclados |
| Tope de 100 por página | `domain/…/inventario/aplicacion/BuscarRepuestos.java:35` | funciona |
| Cada repuesto del listado ya trae su categoría, precio, stock y costo | `RepuestoController.java:211-215` | funciona |
| 16 categorías sembradas, con su orden, incluida *Lubricantes y químicos* | `db/migration/V2__categorias_semilla.sql:12-30` | sembradas |
| Listar las categorías activas | `CatalogoController.java:34-37` (`GET /api/categorias`) | funciona |
| **La categoría es del concepto, no de la marca**: FILTRO ACEITE INOKI y FACTORY comparten la del concepto "FILTRO ACEITE" | `domain/…/inventario/dominio/Producto.java:36-37` · `V1__esquema_inicial.sql:22` | así es el modelo |
| **La categoría es opcional al crear**: sin categoría se guarda igual | `CrearRepuesto.java:66-68` · formulario con *"Sin clasificar"* en `ModalRepuestoNuevo.jsx:215-221` | es lo que cambia H3 |
| El spec 0001 listaba la categoría como parte del concepto, sin decir si era opcional | `docs/specs/0001-…/spec.md:170` (RF-004) | se implementó opcional |
| Corregir la ficha deja cambiar la categoría | `ActualizarRepuesto.java:29` | funciona: es como se arreglan los viejos (H4) |
| **Corregir la ficha también deja quitarla**: sin categoría en la corrección, queda sin categoría | `ActualizarRepuesto.java:80-81` | es lo que cambia RF-011 |
| El inventario muestra *"Sin clasificar"* cuando no hay categoría | `frontend/src/paginas/Inventario.jsx:270` | funciona |
| **En la base de QA, 6 de los 8 repuestos no tienen categoría** | consulta a la base de QA del 2026-09-15 | es la razón de H3 y H4 |
| Agregar un repuesto a la venta revisa precio y stock, y suma si ya está | `frontend/src/utils/venta.js:56` (`problemaParaAgregar`) y `:66` (`agregarRenglon`) | se reusa tal cual |
| Atajos de la venta: F4 descuento, F9 cobrar; los modales devuelven el foco al cerrar | `frontend/src/paginas/Vender.jsx:233-238` · `componentes/Modal.jsx` | F2 está libre |

### Con qué choca

- **Con los datos que ya existen.** La categoría no puede volverse obligatoria en la base: hay
  repuestos sin ella, y una restricción `NOT NULL` fallaría al migrar. La regla vive en **crear**, y
  los viejos se ubican con *Sin categoría* (H4).
- **Con la velocidad de registrar una compra.** Una factura de Jotapartes trae muchos repuestos
  nuevos; una categoría obligatoria sin recordar la anterior se vuelve un clic de más por renglón
  (H3 lo resuelve recordando la última).
- **"Aceite" no es una categoría.** Está en el nombre de filtros (*Filtros*) y de aceites de motor
  (*Lubricantes y químicos*). Por eso el catálogo necesita **buscador y categorías juntos**, con el
  conteo por categoría de lo buscado (H2).

---

## 4. Las decisiones

### Resueltas con el usuario el 2026-09-15

| Qué | Resolución |
|---|---|
| Cómo se abre el catálogo | **Panel con F2** encima de los renglones; el total queda visible a la derecha; **Esc** vuelve. El mostrador sigue igual de rápido cuando el código se sabe |
| ¿Categoría obligatoria al crear? | **Sí.** Los viejos salen en *Sin categoría* y se corrigen desde su ficha |
| Orden del trabajo | Historial (fase 8 del 0002) → **este catálogo** → cierre de caja → login (0004) |

### Decisión · Qué cuentan los números de las categorías — [RESUELTO] A: lo buscado (2026-09-15)

| Opción | Qué implica |
|---|---|
| **A. Cuántos repuestos de lo buscado hay en cada categoría** | Con *aceite*: *Filtros 2 · Lubricantes 5*, y las categorías sin nada no salen. Es lo que responde "¿dónde está lo que busco?". Una consulta que agrupa por categoría |
| B. Cuántos repuestos tiene cada categoría, siempre | Números fijos; con *aceite* escrito, *Frenos 40* no dice nada útil |
| C. Sin números | Lo más simple, pero hay que tocar cada categoría para saber si hay algo |

**Recomendación: A.** Es justamente el caso de "aceite": el número dice en cuál categoría está lo que
el cliente pide.

---

## 5. Requisitos funcionales

### El catálogo en la venta

- **RF-001** · En Vender, **F2** o el botón *Catálogo* abren el catálogo encima de la venta. El
  resumen con el total y *Cobrar* sigue visible. **Esc** o *Volver a la venta* lo cierran y el cursor
  vuelve al buscador de la venta.
- **RF-002** · Arriba del catálogo, las categorías como botones, en su orden, con **cuántos repuestos
  activos de lo buscado** tiene cada una (decisión A). Una categoría sin nada de lo buscado no se
  muestra. *Todas* va primero y *Sin categoría* al final, si hay alguno.
- **RF-003** · Un buscador dentro del catálogo filtra por código, nombre, marca o moto, sin tildes,
  igual que el inventario. Sin texto se ve todo lo de la categoría elegida.
- **RF-004** · Cada repuesto muestra nombre, marca, moto (aplicación), código, **precio** y
  **cuántos hay**. **No muestra el costo**: la pantalla de venta es del cajero.
- **RF-005** · Orden: primero los que tienen stock, luego por nombre y marca. Los sin stock salen al
  final, apagados, con *sin stock*, y no se pueden agregar.
- **RF-006** · Agregar con **[+]**, con clic en la fila o con **Enter** sobre la fila marcada (flechas
  para moverse). Aplica exactamente las mismas reglas que agregar desde el buscador de la venta: sin
  precio no se agrega, no pasa de lo que hay, y si ya estaba suma una unidad. El catálogo queda
  abierto para seguir agregando; la venta muestra el renglón nuevo detrás.
- **RF-007** · Por páginas: se ven 50 y *Ver más* trae los siguientes, sin perder lo ya visto.
- **RF-008** · Con la venta bloqueada (un cobro sin respuesta) o sin turno abierto, el catálogo se
  puede ver pero no agregar, igual que el buscador.

### La categoría obligatoria

- **RF-009** · **Crear un concepto de repuesto exige categoría**, y lo exige el servidor, no solo el
  formulario. Crear una marca nueva de un concepto que ya existe no la pide: la hereda del concepto.
- **RF-010** · En el formulario de repuesto nuevo, la categoría arranca con **la última elegida en la
  misma compra**. En la primera, arranca vacía y hay que escogerla.
- **RF-011** · Corregir la ficha tampoco deja quitar la categoría: se puede cambiar por otra, no
  dejarla vacía.
- **RF-012** *(P2)* · En el inventario, un filtro *Sin categoría* deja solo los repuestos sin
  categoría, para corregirlos desde su ficha.

---

## 6. Manejo de errores

| Situación | Qué pasa |
|---|---|
| Se agrega desde el catálogo un repuesto sin precio | No se agrega: *"No tiene precio de venta. Fíjalo en su ficha."* (el mismo mensaje del buscador) |
| Se agrega más de lo que hay | No se agrega; dice cuántas hay |
| Se crea un repuesto sin categoría | No se guarda: *"Escoge la categoría: es como se encuentra en el catálogo del mostrador"* |
| Se intenta dejar sin categoría una ficha que tenía | No se guarda, con el mismo mensaje |
| Sin conexión con el servidor al abrir o filtrar el catálogo | Aviso ámbar con *Reintentar*; la venta armada no se toca |
| Una búsqueda sin resultados | *"No hay repuestos con «xyz»"*, y *Todas* con 0 |
| Mientras el catálogo estaba abierto se vendió la última unidad en otro equipo | Se agrega igual (el catálogo mostraba 1); al cobrar se rechaza como siempre (spec 0003, RF-009 y §6) |

---

## 7. Requisitos no funcionales

- **Teclado primero.** Abrir con F2, escribir, flechas, Enter agrega, Esc vuelve. El foco nunca queda
  perdido al cerrar.
- **Plata.** Solo muestra precios guardados; no calcula nada. El costo no sale.
- **Inventario.** Solo lectura: ver el catálogo no mueve stock.
- **Esquema.** **Sin migración.** La categoría sigue pudiendo ser nula en la base, porque ya hay filas
  sin ella; la regla va en crear y corregir.
- **Sin internet.** Rebanada 4. Hasta entonces el catálogo lee del servidor de la tienda, como el
  buscador.
- **Roles.** Hasta el spec 0004 no hay roles. Lo que queda decidido: el cajero ve precio y stock, no
  costo. Corregir fichas sigue siendo del administrador.
- **Rendimiento.** Una categoría grande (*Motor* es el 21% del catálogo de Jotapartes) no se trae
  entera: páginas de 50.
- **Auditoría.** No aplica: ver no se audita, y crear un repuesto no se audita hoy.

---

## 8. Criterios de aceptación

**Catálogo**
- [ ] F2 abre el catálogo desde Vender y Esc vuelve con el cursor en el buscador de la venta
- [ ] Tocar *Frenos* deja solo los repuestos de frenos, con precio y stock, sin costo
- [ ] Escribir *aceite* muestra *Filtros* y *Lubricantes y químicos* con cuántos hay en cada una, y
      las categorías sin aceite no salen
- [ ] Escribir *bujia* encuentra *BUJÍA*
- [ ] Los sin stock salen al final, apagados, y no se pueden agregar
- [ ] Agregar con [+] y con Enter suma a la venta con las mismas reglas del buscador, y el catálogo
      sigue abierto
- [ ] Con más de 50 repuestos en una categoría, *Ver más* trae los siguientes
- [ ] Con un cobro sin respuesta, el catálogo se ve pero no agrega

**Categoría obligatoria**
- [ ] Crear un repuesto nuevo sin categoría no se guarda, **ni desde la pantalla ni directo al
      servidor**
- [ ] Una marca nueva de un concepto existente se crea sin pedir categoría y queda con la del concepto
- [ ] El segundo repuesto nuevo de una compra arranca con la categoría del primero
- [ ] Corregir una ficha no deja la categoría vacía
- [ ] El filtro *Sin categoría* del inventario muestra solo los que no tienen, y uno corregido deja de
      salir

**General**
- [ ] `./mvnw clean test` en verde; lint, pruebas y build del frontend en verde

---

## 9. Qué no se toca

- **Cómo se cobra**: el catálogo solo agrega renglones; cobrar, descontar, el borrador y la llave
  quedan igual (spec 0003).
- **El buscador de la venta** sigue igual: escribir el código y Enter sigue siendo lo más rápido.
- **Las categorías sembradas**: no se crean, renombran ni borran desde la pantalla.
- **Los repuestos existentes sin categoría** no se modifican solos: se corrigen a mano (H4).

## 10. Fuera de alcance

| Qué | Por qué |
|---|---|
| Fotos de los repuestos | El modelo no las tiene; agregarlas es esquema y carga de imágenes |
| Filtro por moto con modelos normalizados | La aplicación es texto libre del proveedor. El buscador ya encuentra *ns 200*; normalizar modelos es un trabajo aparte |
| Crear o renombrar categorías | Las 16 sembradas cubren el catálogo de Jotapartes y lo que vende RD Motors |
| Asignar categoría a muchos repuestos a la vez | Con 6 sin categoría no hace falta. Si al cargar el catálogo real quedan cientos, se pide aparte |

---

## 11. Riesgos

| Riesgo | Qué hacer |
|---|---|
| Al registrar el catálogo real, la categoría obligatoria hace más lenta una factura larga | RF-010 recuerda la última. Si en el uso real sigue pesando, se revisa |
| Un concepto quedó en la categoría equivocada y todas sus marcas salen en otra parte del catálogo | Se corrige una vez en la ficha y aplica a todas sus marcas (la categoría es del concepto) |
| Con el catálogo real, *Motor* tiene cientos de repuestos | Páginas de 50 y el buscador del catálogo |

---

## 12. Preguntas abiertas

Ninguna. Resuelta el 2026-09-15: los números de las categorías cuentan **lo buscado** (§4, opción A).
