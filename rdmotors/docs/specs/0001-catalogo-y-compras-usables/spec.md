# Spec 0001 — Catálogo y compras usables

**Estado:** aprobado — sin preguntas abiertas
**Rebanada:** 1 de 5 · ver [`PLAN_DE_TRABAJO.md`](../../PLAN_DE_TRABAJO.md)

> **Convención:** lo que dice *"hoy"* está verificado con `archivo:línea`. Lo que dice
> *"debe"* es propuesta y todavía no existe. Las dos cosas nunca se mezclan.

---

## 1. Objetivo de negocio

**Hoy el sistema no se puede encender.** Existe un endpoint para registrar compras, pero exige que
el proveedor y el repuesto ya estén en la base — y no hay ninguna forma de crearlos. Para registrar
la primera compra habría que escribir `INSERT` a mano.

Al terminar esta rebanada, RD Motors puede tomar una factura real de Importadora Jotapartes,
registrarla en el sistema, y ver su inventario existir por primera vez. Es lo primero que se le
puede **enseñar al cliente**.

---

## 2. Caso de uso

### P1 — bloquean la rebanada

**H1 · Registrar un proveedor.**
El administrador da de alta a Importadora Jotapartes con su nombre, NIT (opcional) y teléfono (opcional).
*Se demuestra solo:* aparece en la lista de proveedores. Se debe poder regoistrar una compra y crear el proveedor desde ahi mismo, si ya existe aparecer o auto-completar

**H2 · Registrar una compra creando repuestos que no existían.**
El administrador tiene la factura enfrente. Va renglón por renglón: si el repuesto ya está, lo
busca por código o nombre; si no está, **lo crea sin salirse de la compra** — nombre, marca, categoría,
código y precio de venta. Captura la cantidad y el costo, por total o por unidad.
*Se demuestra solo:* registra una factura real y el stock aparece.

**H3 · Buscar un repuesto.**
El administrador escribe un código o parte de un nombre y ve los repuestos que coinciden, con su
stock, su precio y su costo promedio.
*Se demuestra solo:* busca "filtro" y salen los filtros con sus existencias.

**H4 · Ver el historial de un repuesto.**
Abre la ficha de un repuesto y ve cada movimiento: qué pasó, cuándo, cuántas unidades, a qué costo,
y con cuánto saldo quedó.
*Se demuestra solo:* después de dos compras a distinto precio, el historial muestra cómo se movió
el costo promedio.

### P2 — importantes, no bloquean

**H5 · Corregir un repuesto ya creado.** Cambiar nombre, categoría, precio o stock mínimo. dejar auditoria


### P3 — deseables

**H7 · Margen objetivo.** Al fijar el precio, escribir "40%" y que proponga el valor. Sugiere, no
impone.

---

## 3. Qué existe hoy

Esta es la sección que evita construir lo ya construido.

| Qué | Dónde | Estado |
|---|---|---|
| Registrar una compra completa | `compras/aplicacion/RegistrarCompra.java` | **funciona**, 14 pruebas |
| Los dos modos de captura, total y unitario | `compras/dominio/LineaCompra.java` | **funciona** |
| Costo promedio ponderado al comprar | `inventario/dominio/Variante.java:143` | **funciona**, 8 pruebas |
| Fijar precio de venta desde la compra | `inventario/dominio/Variante.java:177` | **funciona** |
| Crear un repuesto (en código) | `inventario/dominio/Variante.java:103` | existe, **nadie lo llama** |
| Crear un producto (en código) | `inventario/dominio/Producto.java:63` | existe, **nadie lo llama** |
| Crear un proveedor (en código) | `compras/dominio/Proveedor.java:44` | existe, **nadie lo llama** |
| Buscar repuesto por código | `inventario/dominio/puerto/RepositorioVariantes.java:32` | declarado e implementado, **nadie lo llama** |
| Endpoint de compras | `compras/infraestructura/CompraController.java` | **funciona** |
| Tablas de catálogo, compras y kardex | `db/migration/V1__esquema_inicial.sql` | **creadas** |
| Las 16 categorías | `db/migration/V2__categorias_semilla.sql` | **sembradas** |

**Media rebanada ya está construida.** Lo que falta no es la lógica de negocio: son las **puertas
de entrada** — endpoints y pantallas para lo que el dominio ya sabe hacer.

Lo que **no** existe hoy:

- Ningún endpoint salvo el de compras (verificado: un solo `@PostMapping` en todo `pos`)
- Ninguna búsqueda de repuestos expuesta
- La tabla que guarda cómo llama cada proveedor a cada repuesto
- Cualquier prueba contra una base de datos real — las 28 usan dobles en memoria
- Proyecto de frontend

---

## 4. La decisión

**¿Cómo nace un repuesto nuevo sin duplicar el concepto?**

El modelo separa el **concepto** ("filtro de aceite para Pulsar NS 200") de lo **vendible** (ese
filtro, marca INOKI, a $2.977). En la lista real de Jotapartes el mismo filtro existe en tres
marcas, y el más caro cuesta **229%** del más barato.

Si la creación es ingenua, el mes que viene hay tres conceptos "filtro de aceite para Pulsar"
distintos. Y entonces pasan dos cosas malas: la compatibilidad vehicular queda escrita tres veces
y diverge, y el cajero que busca "filtro pulsar" recibe tres resultados sueltos en vez de uno con
tres opciones de precio. **Eso destruye la razón de ser del modelo.**

| **B.** Buscar el concepto primero; si existe, solo agregar la marca nueva | Un paso más para el administrador. Preserva el modelo |

**Recomendación: B.** El paso extra lo paga el administrador una sola vez por referencia nueva; el
cajero lo cobra en cada venta durante años. Y es el único camino que hace que la búsqueda por
compatibilidad (P2 del negocio) sea posible después.

En la práctica el formulario pregunta primero por el concepto —buscándolo o creándolo— y después
por los datos de la marca concreta.

### Carga inicial — [RESUELTO] no hay

El cliente **sí tiene un catálogo**, pero va a abrir operando en papel, así que ese catálogo **no
es el inventario final** ni una fuente confiable de lo que hay en estantería.

Conclusión: **no se construye ningún importador de carga inicial.** El inventario nace dentro del
sistema, repuesto por repuesto, a medida que se registran compras. Es coherente con lo ya decidido
—que el producto nace en la compra y el precio se fija ahí— y evita construir un importador para
datos que van a estar desactualizados el día uno.

Cuando quieran cuadrar contra lo que de verdad hay en el estante, eso es una **toma física con
ajustes de inventario**, que es la rebanada 3.

Evaluar a futuro importaciones a excel con formato y que se haga la carga fuera del MVP

### El código del repuesto — [RESUELTO] obligatorio, y es el buscador

El código **no es un dato de adorno**: es la llave que hace rápida cada factura siguiente. La
segunda compra a Jotapartes trae otra vez `370PUL2N`; con el código guardado, el administrador lo
teclea y el sistema encuentra la pieza al instante en vez de buscarla por nombre y arriesgarse a
crear un duplicado.

La fricción que preocupaba —tener que escribirlo— se evita haciendo que **el campo de código sea
el buscador**, no un campo aparte:

```
Renglón de la compra
┌────────────────────────────────┐
│ Código:  370PUL2N              │
└────────────────────────────────┘
        ├─ existe     → lo selecciona; solo faltan cantidad y costo
        └─ no existe  → abre la creación CON EL CÓDIGO YA PUESTO
```

Se teclea **una sola vez**, nunca dos. Y es trabajo que se concentra en las primeras semanas:
después la mayoría de renglones ya existen y el flujo es *código → cantidad → costo*.

La alternativa descartada era un código autogenerado tipo `RD-0042`: quita el tecleo inicial pero
obliga al administrador a mantener de cabeza la equivalencia con lo que el proveedor llama esa
pieza, cada vez que quiera volver a pedirla. Cambia una molestia acotada por una permanente.

**Consecuencia asumida:** hay **un solo** código por repuesto, y es el del proveedor principal. El
día que un segundo importador venda la misma pieza con otro código, ese código no cabe. Ahí habrá
que decidir si entra la tabla de códigos por proveedor — **diferida, no cancelada** (RF-019).

---

## 5. Requisitos funcionales

### Proveedores

- **RF-001** · Crear un proveedor con nombre obligatorio; NIT y teléfono opcionales.
- **RF-002** · Listar los proveedores activos.
- **RF-003** · Desactivar un proveedor. No se borra: desactivar conserva el histórico de compras.

### Catálogo

- **RF-004** · Crear un concepto de repuesto: nombre, categoría y —opcional— el texto de
  aplicación vehicular tal como lo trae el proveedor.
- **RF-005** · Buscar conceptos por nombre, para reutilizarlos en vez de duplicarlos (ver §4).
- **RF-006** · Crear un repuesto vendible sobre un concepto: código, marca, precio de venta y
  stock mínimo.
- **RF-007** · El código es obligatorio y **único en todo el sistema**.
- **RF-007b** · El campo de código **hace doble función**: si el código existe selecciona el
  repuesto; si no existe abre la creación con el código ya puesto. Nunca se teclea dos veces.
- **RF-008** · Listar las categorías activas, ordenadas.
- **RF-009** *(P2)* · Corregir nombre, categoría, precio y stock mínimo de un repuesto existente.

### Búsqueda

- **RF-010** · Buscar repuestos por **código exacto**.
- **RF-011** · Buscar repuestos por **texto parcial** en nombre, marca o aplicación.
- **RF-012** · Cada resultado muestra código, nombre, marca, precio, stock y costo promedio.
- **RF-013** · Un repuesto sin costo conocido muestra "—", **nunca cero**. Un cero inventado se
  lee como un hecho y haría reportar 100% de margen.

### Compras

- **RF-014** · Registrar una compra con proveedor, fecha de la factura, número y varios renglones.
- **RF-015** · Cada renglón acepta el costo **por total** o **por unidad**, y el interruptor es
  **por renglón**: la misma factura trae las dos formas.
- **RF-016** · Crear un repuesto **durante** el registro de la compra, sin perder lo ya capturado.
- **RF-017** · Mostrar el margen por renglón mientras se captura, y avisar si el costo supera al
  precio de venta.
- **RF-018** · El precio de venta en blanco **no toca** el precio del repuesto.
- **RF-019** *(P2)* · Guardar por proveedor: su código para ese repuesto y las unidades del empaque.
- **RF-020** *(P3)* · Proponer precio a partir de un margen objetivo.

### Kardex

- **RF-021** · Ver el historial de movimientos de un repuesto, del más reciente al más antiguo.
- **RF-022** · Cada movimiento muestra tipo, fecha, unidades, costo unitario, saldo resultante y
  costo promedio resultante.

---

## 6. Manejo de errores

| Situación | Qué debe pasar |
|---|---|
| Código de repuesto repetido | Se rechaza diciendo **cuál** repuesto ya lo usa. No se crea nada |
| Costo cero o negativo en un renglón | Se rechaza: sin costo real el repuesto reportaría 100% de utilidad |
| Precio de venta negativo | Se rechaza |
| El mismo repuesto dos veces en una compra | Se rechaza pidiendo sumarlos en un renglón |
| Proveedor inexistente | Se rechaza |
| Se corta la captura de una compra larga | **[DECIDIDO] se pierde.** El borrador de compra queda fuera del MVP — ver §10 |
| La división del costo no da exacta | No es un error: el total es el dato bueno y el unitario se guarda con cuatro decimales |
| Búsqueda sin resultados | Se dice que no hay, y se ofrece crear el repuesto desde ahí |

---

## 7. Requisitos no funcionales

- **Roles.** Todo lo de esta rebanada es de **administrador**. El cajero no crea repuestos ni
  registra compras. La seguridad todavía no existe, así que el bloqueo queda declarado y se
  implementa en la rebanada 2 — **no se cierra esta rebanada fingiendo que está protegida**.
- **Sin conexión.** Fuera de alcance aquí: el administrador registra compras con calma y con
  internet. Toda la operación offline es la rebanada 4.
- **Velocidad de búsqueda.** Debe responder de inmediato con miles de repuestos. Es el mismo
  buscador que en la rebanada 2 usará el cajero con un cliente enfrente, así que se construye
  pensando en eso desde ahora.
- **Pruebas contra base real.** Esta rebanada trae la **primera prueba de integración con Postgres
  de verdad**. Las 28 actuales demuestran que las reglas son correctas, pero ninguna demuestra que
  el esquema cuadre con el modelo ni que el bloqueo de fila realmente bloquee.
- **Esquema.** Migración nueva y numerada. La pregunta que las pruebas no pueden hacer:
  *¿esto funciona en una base que ya tiene filas?*

---

## 8. Criterios de aceptación

Binarios. Es la Definition of Done de la rebanada.

- [ ] Se registra un proveedor desde la pantalla y aparece en la lista
- [ ] Se registra una compra de **al menos tres renglones** de una factura real de Jotapartes
- [ ] Al menos uno de esos renglones crea un repuesto que no existía, sin perder lo capturado
- [ ] Al menos un renglón se captura **por total** y otro **por unidad**
- [ ] El total de la compra **cuadra al peso** con la factura del proveedor
- [ ] Tras la compra, el stock de cada repuesto subió por la cantidad exacta
- [ ] Una segunda compra del mismo repuesto a distinto precio deja el **costo promedio ponderado**
      correcto
- [ ] La ficha del repuesto muestra los dos movimientos con sus saldos y promedios
- [ ] Buscar por código exacto devuelve el repuesto
- [ ] Buscar por texto parcial devuelve los que coinciden
- [ ] Un repuesto sin compras muestra "—" en costo, no `$0`
- [ ] Dos repuestos con el mismo código son rechazados con un mensaje que dice cuál lo tiene
- [ ] Existe al menos una prueba que corre contra Postgres real y verifica que el esquema cuadra
- [ ] `./mvnw clean test` en verde

---

## 9. Qué no se toca

- `RegistrarCompra` y su dominio **funcionan y están probados**. Se les agrega la creación de
  repuestos al vuelo; no se reescribe lo que ya pasa sus 14 pruebas.
- Las reglas de `Variante` —descontar, reponer, promedio ponderado— no se tocan.
- Las migraciones existentes no se editan. Todo cambio de esquema va en una migración nueva.

## 10. Fuera de alcance

| Qué | Por qué |
|---|---|
| Vender | rebanada 2 |
| Caja, usuarios, login | rebanada 2 |
| Operación sin conexión | rebanada 4 |
| Compatibilidad vehicular normalizada | P2 del negocio. Aquí se guarda el texto del proveedor tal cual, que es lo que permite normalizarlo después |
| Importar las 8.824 referencias | decidido: RD Motors solo adquiere ciertas referencias |
| Carga inicial del inventario | el cliente abre en papel; su catálogo no refleja lo que hay. El inventario se construye comprando (ver §4) |
| Borrador al capturar una compra | **fuera del MVP.** Si se corta la captura de una factura larga, se vuelve a empezar. El borrador de **venta** sí entra, en la rebanada 2: ahí hay un cliente esperando y un corte de luz a mitad de un cobro es otra cosa |
| Código de barras | el campo ya existe en la tabla; imprimir etiquetas es futuro |
| Exportar a PDF o Excel | P2 del negocio, rebanada 3 |

---

## Decisiones tomadas en la revisión

| Qué | Resolución |
|---|---|
| ¿Cómo nace un repuesto sin duplicar el concepto? | **Opción B** — se busca el concepto primero; si existe, solo se agrega la marca |
| Carga inicial del inventario | **no hay.** El cliente abre en papel; el inventario se construye comprando |
| Borrador al capturar una compra | **fuera del MVP** |
| Código del repuesto | **obligatorio**, y el campo de código es el buscador |
| Códigos por proveedor | **diferido** (RF-019), no cancelado |
| Crear proveedor desde la compra | **sí**, con autocompletado si ya existe |
| Corregir un repuesto | deja **auditoría** |

**Sin preguntas abiertas.** El spec está cerrado y listo para el plan.
