# SPEC — Modelo de Datos · RD MOTORS

> Documento de diseño del modelo de datos. Deriva de `SPEC_Sistema_Ventas_Repuestos (3).md`
> (alcance de negocio) y del análisis de la lista de precios real de Importadora Jotapartes
> (8.824 referencias, agosto 2026).
>
> **Convención:** lo marcado como **[DECIDIDO]** está cerrado. Lo marcado como **[SUPUESTO]** se
> implementa así salvo que el cliente diga otra cosa. Lo de la sección 9 está **abierto** y necesita
> respuesta antes de construir esa parte.

---

## 1. Contexto de arquitectura

Decisiones ya cerradas que condicionan todo el modelo:

- **Monolito modular, hexagonal pragmático, dominio transaccional.** Ver skill `backend`.
- **La PC de la tienda es la fuente de verdad.** La nube es un espejo de solo lectura para el
  panel del propietario, alimentado por un outbox de una sola vía.
- **Sin multi-tenant en el local** (una sola tienda, sin multi-sucursal). El esquema de la nube
  lleva `store_id` desde el día uno.
- **Sin requisitos fiscales.** El comprobante es un ticket interno.
- Todo PK es `UUID`. Toda escritura que SUMA lleva llave de idempotencia.

---

## 2. El núcleo: producto ≠ lo que se vende

**[DECIDIDO]** Dos niveles, y esto está validado contra el dato real del cliente:

```
FILTRO ACEITE  |  PULSAR NS 200/FI/AS 200-DUKE 200
    IMPORTADO    $2.779
    INOKI        $2.977
    FACTORY      $6.362      ← 229% del más barato
```

Mismo repuesto, misma aplicación, tres marcas, tres precios. En el catálogo de Jotapartes esto
pasa en 353 de 8.456 grupos (4%), y subirá al comprarle a varios importadores.

```
producto  ─────────►  el CONCEPTO
                      nombre, categoría, compatibilidad, aplicación original
     │ 1..N
     ▼
variante  ─────────►  lo VENDIBLE
                      código, marca del repuesto, precio, stock, costo promedio
```

### Regla que no se puede romper

> **Stock, costo y precio viven en la VARIANTE.**
> **La compatibilidad vive en el PRODUCTO.**

Poner stock en el producto rompe el kardex. Poner compatibilidad en la variante obliga a repetir
la misma lista de modelos en cada marca, y esas copias divergen.

### Consecuencia de interfaz

El 96% de los productos tendrá **una sola variante**. La pantalla de venta no puede obligar al
cajero a pasar por una selección de variante en ese caso: si hay una, se agrega directo.

---

## 3. Tablas

### 3.1 Catálogo

**`producto`** — el concepto
| Campo | Notas |
|---|---|
| `id` | UUID |
| `nombre` | "FILTRO ACEITE" |
| `descripcion` | opcional |
| `categoria_id` | FK a `categoria` — tabla editable, ver sección 6.1 |
| `aplicacion_original` | **texto crudo del proveedor, verbatim** |
| `es_universal` | booleano — cubre `VARIAS`/`UNIVERSAL` (~593 filas del catálogo) |
| `activo`, `creado_en`, `actualizado_en`, `borrado_en` | |

`aplicacion_original` se conserva intacto: es la fuente para auditar el matcher de compatibilidad
y para resolver a mano lo que no cruce. **Nunca se sobrescribe con el resultado del parseo.**

**`variante`** — lo vendible
| Campo | Notas |
|---|---|
| `id` | UUID |
| `producto_id` | FK |
| `codigo` | **único** — el código interno de RD Motors |
| `codigo_barras` | nullable, único cuando existe. Ver sección 8 |
| `marca_repuesto` | INOKI, T.K.R.J, IMPORTADO… (55 valores en el catálogo) |
| `calidad` | nullable — nivel si aplica |
| `precio` | entero COP. **Precio final, IVA incluido, sin desglose** |
| `stock` | entero. Nunca null |
| `stock_minimo` | entero, default 5 |
| `costo_promedio` | **decimal(14,4)**, nullable = nunca se compró |
| `activo`, `borrado_en` | |

**[DECIDIDO] Sin granel.** El análisis del catálogo confirmó que todas las cantidades y precios
son enteros. `UNID` en la lista de Jotapartes no es unidad de medida: es **empaque de compra**
(bujías de a 10, tornillos de a 100), y eso es un atributo del proveedor, no de la venta.

**[DECIDIDO] Precio final sin IVA desglosado.** Un solo campo. El ticket muestra el total. Cero
configuración de impuestos. Si algún día hay que discriminarlo, es una columna aditiva.

### 3.2 Compatibilidad vehicular

**[DECIDIDO] Se normaliza. Sin años.**

La propuesta inicial incluía `año_desde`/`año_hasta`. **Se elimina**: solo 163 de 8.824 filas
(1,8%) del catálogo real tienen algo parecido a un año, porque en motos la generación va dentro
del nombre del modelo (`AK125 S/SL/NKD/TT/EVO`). El año sería redundante y no se puede llenar.
Agregarlo después es una columna nullable; arrastrar hoy un campo obligatorio que nadie puede
poblar, no.

Viabilidad medida sobre el catálogo real:

| Familias de modelo curadas | Filas cubiertas |
|---|---|
| 50 | 70,8% |
| 100 | 87,7% |
| **200** | **95,1%** |
| 300 | 96,6% |
| 800 | 97,5% ← la curva se aplana |

No hay que parsear 2.487 cadenas: hay que **curar ~250 nombres de modelo** y dejar que el matcher
cruce. De 300 en adelante la cola es ruido, no modelos.

**`marca_vehiculo`** — Bajaj, Yamaha, Honda, AKT… + `tipo` (MOTO/AUTO)

**`modelo_vehiculo`** — ~250 curados: `marca_id`, `nombre`
Semilla ya extraída del catálogo: AK 125 (678 filas), BOXER (343), PULSAR (272), YBR 125 (227),
DISCOVER 125, FZ 16, GN 125, DT 125, AX 100, CB 110, XTZ 125…

**`compatibilidad`** — `producto_id` + `modelo_id`. Muchos a muchos. Sin años.

**Regla de la importación:** lo que el matcher no cruce va a una **cola de revisión manual**, no
se inventa. Un producto mal enlazado es peor que uno sin enlazar — el cajero le vende al cliente
la pieza equivocada y eso destruye la confianza en el sistema.

### 3.3 Inventario — Kardex

**`movimiento_kardex`** — append-only, **nunca se edita ni se borra**
| Campo | Notas |
|---|---|
| `id`, `variante_id` | |
| `tipo` | COMPRA, VENTA, DEVOLUCION, AJUSTE, REVERSION |
| `cantidad_delta` | entero, con signo |
| `costo_unitario` | decimal(14,4) |
| `costo_total` | entero COP — **autoritativo**, ver 3.4 |
| `saldo_despues` | entero — snapshot del stock tras el movimiento |
| `costo_promedio_despues` | decimal(14,4) |
| `origen_tipo`, `origen_id` | de qué documento vino |
| `movimiento_revertido_id` | nullable |
| `motivo` | obligatorio en AJUSTE y REVERSION |
| `registrado_por`, `creado_en` | |

**El kardex es la auditoría de inventario.** No hace falta otra tabla para eso.

Reglas portadas del car-wash, cada una por una razón que ya costó caro allá:

- Se escribe **con bloqueo de fila** sobre la variante. Sin eso, dos peticiones simultáneas leen
  el mismo saldo y ambas venden la última unidad.
- Una **reversión de venta nunca toca el costo promedio** — refleja el promedio vigente real.
- Un **ajuste de entrada sobre un producto sin costo promedio se rechaza**: un promedio de 0 haría
  que el producto reportara 100% de margen.
- **No se puede anular una compra si después se vendió stock.** Con costeo por promedio no se
  puede saber si esas unidades salieron de esa compra.

### 3.4 Compras y proveedores

**`proveedor`** — nombre, NIT, teléfono, activo

**`compra`**
| Campo | Notas |
|---|---|
| `proveedor_id` | |
| `fecha_documento` | la de la factura del proveedor |
| `fecha_registro` | cuándo se capturó |
| `total` | entero COP |
| `registrado_por`, `creado_en` | |

**Dos fechas a propósito.** La factura puede ser vieja y registrarse hoy. Confundirlas mete
compras fantasma en meses ya cerrados — cicatriz literal del car-wash.

**`linea_compra`**
| Campo | Notas |
|---|---|
| `variante_id`, `cantidad` | |
| `costo_total_linea` | entero COP — **fuente de verdad** |
| `costo_unitario` | **decimal(14,4)** — derivado |
| `modo_captura` | TOTAL o UNITARIO |

#### [DECIDIDO] Dos modos de captura por línea

Es la funcionalidad nueva respecto al car-wash, que solo acepta costo unitario.

```
┌─ Modo TOTAL ────────────────┐   ┌─ Modo UNITARIO ─────────────┐
│ Cantidad:  20               │   │ Cantidad:  20               │
│ Pagué:     $200.000         │   │ C/u:       $10.000          │
│ ─────────────────────────   │   │ ─────────────────────────   │
│ Sale a:    $10.000 c/u  ←   │   │ Total:     $200.000     ←   │
└─────────────────────────────┘   └─────────────────────────────┘
```

El interruptor es **por línea, no por compra**: la misma factura trae renglones de las dos formas.

#### El problema del redondeo — resuelto por diseño

```
$200.000 ÷ 15 = $13.333,333...
```

Con unitario redondeado a entero: `15 × 13.333 = $199.995`. **Se perdieron $5**, y el reporte de
compras deja de cuadrar con lo pagado. Eso viola la regla del skill `backend`: *las partes tienen
que sumar el total.*

> **El total es autoritativo. El unitario se guarda con 4 decimales.**
> `15 × 13.333,3333 = $199.999,995` → $200.000. Cuadra.

Se persisten **siempre los dos campos**, en cualquier modo. Nunca se recalcula uno al leer.

#### El precio de venta viaja en la compra

Portado del car-wash con sus detalles ya aprendidos:

- `precio_venta` **vacío = no tocar el precio del producto.** Reponer stock no debe reescribir en
  silencio un precio que nadie quiso cambiar.
- Al elegir el producto se **precarga su precio actual**, editable.
- **Margen en vivo por línea**: *"Vendes a $18.000 · utilidad $7.500 · margen 42%"*, y si el costo
  supera el precio, avisa *"Pérdida de $2.000 por unidad"*.
- **Costo cero rechazado**: sin costo real el producto reportaría 100% de utilidad.
- **[SUPUESTO]** Botón de margen objetivo: se escribe 40% y propone el precio. Sugiere, no impone.

#### El producto nace en la compra

**[DECIDIDO] No se importan las 8.824 referencias.** RD Motors solo adquiere ciertos productos del
catálogo. El producto se crea **durante el registro de la compra**, y ahí recibe su precio de venta.

Eso resuelve la respuesta del cliente —"precio a mano, producto por producto"— sin convertirlo en
una tarea de 8.824 filas: es un campo en el flujo de compra, uno a la vez, cuando importa.

**`producto_proveedor`** — pendiente de la sección 9, punto 1
| Campo | Notas |
|---|---|
| `proveedor_id`, `variante_id` | |
| `codigo_proveedor` | `370PUL2N` — el de Jotapartes |
| `empaque` | el `UNID` del catálogo: viene de a 10, de a 100 |
| `ultimo_costo` | |

Representa **la misma pieza vista por cada proveedor**. Responde: ¿con qué código se lo pido?
¿de a cuántos viene? ¿quién me lo vende más barato? ¿cómo ha variado su costo? — esta última es
el "historial de precios por proveedor" del spec de negocio.

Si Jotapartes resulta ser el único proveedor, estos campos van en la variante con el proveedor
como FK simple y la tabla se difiere (regla de tres, skill `patrones`).

### 3.5 Ventas

**`venta`**
| Campo | Notas |
|---|---|
| `numero` | consecutivo interno, sin validez fiscal |
| `sesion_caja_id` | **obligatorio** — sin caja abierta no hay venta |
| `vendido_por` | |
| `total_bruto`, `total_neto` | enteros COP |
| `descuento_monto` | entero COP — **el hecho** |
| `descuento_modo` | PORCENTAJE o MONTO — cómo se capturó |
| `descuento_porcentaje` | nullable, solo si se capturó así |
| `descuento_motivo` | **obligatorio cuando el descuento > 0** |
| `descuento_aplicado_por` | FK usuario |
| `estado` | EN_CURSO, COBRADA, ANULADA |
| `anulada_en`, `anulada_por`, `motivo_anulacion` | motivo obligatorio |

**`linea_venta`** — `variante_id`, `cantidad`, `precio_al_vender`, `total`

`precio_al_vender` es **una foto, no una referencia**. Si mañana sube el precio, el ticket de ayer
no puede cambiar.

#### 3.5.1 El consecutivo del ticket — [DECIDIDO]

**Entero corrido que nunca reinicia**: `1, 2, 3… 4521, 4522…`

Es el número impreso en el comprobante. Sirve para que el cliente vuelva con su ticket y el cajero
encuentre la venta al instante, para que las anulaciones y devoluciones apunten a algo legible, y
para que el cliente lo pueda dictar por teléfono.

Sin reinicio por año ni por turno: **único para siempre significa que el número solo identifica la
venta**, sin necesidad de acompañarlo de una fecha. Con una sola tienda y un solo terminal tampoco
hace falta prefijo de caja.

Dos reglas que importan más que el formato:

- **Se asigna al COBRAR, no al abrir la venta.** Si se asignara al iniciar el carrito, cada venta
  abandonada se comería un número y dejaría un hueco en la serie — y **un hueco en una serie de
  comprobantes se lee como si alguien hubiera borrado una venta.** Como el borrador vive en
  IndexedDB y no en la base (sección 3.10), esto sale gratis.
- **Un número anulado no se reutiliza.** Si se anula la 4521, ese número queda anulado para siempre
  y la siguiente es 4522. Reciclarlo haría que existieran dos ventas 4521 y rompería el rastro de
  auditoría justo donde más se necesita.

**No es la llave primaria.** La llave es UUID, que es lo que usa la sincronización con la nube. El
consecutivo es una etiqueta secuencial para humanos, única, generada localmente — y puede generarse
sin riesgo de colisión porque la PC de la tienda es el único escritor.

#### [DECIDIDO] Descuentos: por porcentaje o por monto, siempre auditados

El car-wash solo acepta monto (`discount_amount` + `discount_reason`, topado al total). Aquí se
añade la captura por porcentaje.

> **Se guarda siempre el MONTO en pesos. El porcentaje es el modo de captura, no el dato.**

Misma lección que los $5 de la sección 3.4: **se guarda el hecho, no la fórmula.** Si solo
guardaras "15%", cualquier cambio posterior en el total alteraría el descuento en silencio. El
monto en pesos es inmutable; el porcentaje queda como registro de la intención.

Reglas:

- El descuento va **sobre el total de la venta, no por línea.** En una tienda de repuestos el
  descuento es "te dejo el total en $50.000", no línea por línea. Por línea es complejidad que
  nadie pidió; si algún día se necesita, es aditivo.
- **Topado al total** — el descuento nunca puede dejar la venta en negativo (el car-wash ya lo hace).
- **Motivo obligatorio** cuando hay descuento. Sin motivo no se puede cobrar.
- Queda registrado **quién** lo aplicó, y genera un evento en `evento_auditoria` (sección 3.7).

Esto es lo único que contiene el riesgo que el propio cliente nombró en la sección 1 de su spec
—fraude de cajeros— dado que el cajero autoriza sus propios descuentos: no se le impide, se le
registra.

**`pago`** — `venta_id`, `medio_pago_id`, `monto`, `referencia`

**Tabla aparte porque el spec pide pago mixto**: una venta, N pagos. Como columna en `venta`,
mixto sería imposible sin inventar campos.

#### [DECIDIDO] Los medios de pago son un catálogo editable, nunca una pasarela

> **[REVISADO 2026-09-14 en el spec 0003, decisión 2]** No hay catálogo de medios de pago. La venta
> se cobra en **Efectivo** o **Transferencia** (el QR cuenta como transferencia), sin cuenta, o
> mixto. Solo el efectivo entra al cajón, así que la regla del arqueo se mantiene. Lo que sigue en
> esta sección queda como historia de la decisión original. **Sigue vigente: nunca una pasarela.**

**`medio_pago`**
| Campo | Notas |
|---|---|
| `nombre` | Efectivo, Nequi, Daviplata, QR Bancolombia… |
| `entra_a_caja` | **booleano — el único campo que de verdad importa** |
| `activo`, `orden` | |

`entra_a_caja` es lo que hace que el arqueo cuadre: el efectivo suma al esperado del cajón, todo lo
demás no. Es la misma regla del car-wash (`esperado = fondo + efectivo cobrado`), expresada como
dato en vez de como enum.

Que sea tabla permite al cliente agregar el medio que quiera sin tocar código ni esperar despliegue
—igual que las categorías (sección 6.1)—, y las mismas reglas aplican: no se borra un medio con
pagos registrados, se desactiva.

**No habrá integración con pasarela de pago, ni ahora ni en fase 2.** El cajero registra
manualmente por qué medio le pagaron. Consecuencia asumida y ya reconocida en el spec de negocio:
que el pago electrónico haya llegado de verdad depende de la verificación manual del cajero.

**Esto elimina un puerto.** La lista original incluía "Cobro" como puerto por la posible pasarela
futura; sin segunda implementación, deja de serlo. Aplicación directa de la regla de la sección
"qué merece un puerto" del skill `backend`: si no puedes nombrar el segundo adaptador, es una
interfaz decorativa.

#### [DECIDIDO] Se puede vender desde tablet o celular — con una condición

La PC de la tienda es **el sistema**; la tablet y el celular son **pantallas** que se conectan a
ella por la red local escribiendo su IP. No necesitan internet: necesitan **WiFi local**, que es
distinto — basta un router aunque no haya servicio contratado.

```
        ┌──────────────────────────────────┐
        │  PC o mini-PC de la tienda       │
        │  Spring Boot + Postgres          │  ← EL CEREBRO
        │  + ticketera térmica             │     siempre encendido
        └────────────────┬─────────────────┘
                         │   WiFi local — SIN internet
            ┌────────────┼────────────┐
            ▼            ▼            ▼
        Navegador     Tablet       Celular
        en la PC    (pasillos)   (respaldo)
                    ── PANTALLAS ──
```

Condiciones: la PC tiene que estar encendida (la tablet no es autónoma), todos en el mismo WiFi
(no se vende desde fuera de la tienda, consistente con "meramente local se atenderán" del spec),
y **tiene que existir una PC** — un Android no corre Spring Boot con Postgres. Puede ser una
mini-PC bajo el mostrador, pero tiene que haber una.

**Consecuencia obligatoria: la impresión del ticket vive en el BACKEND, no en el navegador.**

> **[REVISADO 2026-09-14 en el spec 0003, decisión 3]** El ticket lo imprime **el navegador del
> computador del mostrador**, con la ticketera POS instalada en Windows y el navegador en modo
> kiosco, como el car-wash en producción. Consecuencia aceptada: una venta hecha desde tablet o
> celular no imprime en el momento, y su ticket se reimprime desde el computador del mostrador. El
> resto de esta sección (vender desde tablet o celular por WiFi local) sigue vigente.

Si se imprimiera con `window.print()` desde el navegador, solo el equipo que tiene la impresora
instalada podría emitir comprobantes, y la tablet quedaría inservible para vender. Con la impresión
como puerto del backend (ESC/POS por USB desde la PC), se vende desde el pasillo con la tablet y el
ticket sale por la ticketera del mostrador. Es más trabajo, y es lo único que hace real el uso en
tablet.

Efecto secundario bueno: el formato del ticket es idéntico venga del dispositivo que venga.

### 3.6 Caja

**`sesion_caja`**
| Campo | Notas |
|---|---|
| `abierta_por`, `cerrada_por` | |
| `abierta_en`, `cerrada_en` | |
| `monto_apertura` | el fondo |
| `esperado` | snapshot al cerrar |
| `contado` | conteo físico declarado |
| `diferencia` | `contado − esperado`. Positivo = sobrante |
| `estado` | ABIERTA, CERRADA |
| `nota_apertura`, `nota_cierre` | |

> **`esperado = monto_apertura + efectivo cobrado − gastos y retiros en efectivo`**

**El QR no entra al cajón, así que no entra al esperado.** Es la misma regla del car-wash y es lo
único que hace que el arqueo cuadre. Ver `MedioDePago.entraAlCajon()` en el skill `patrones`.

Solo puede haber **una sesión ABIERTA a la vez**, garantizado por índice único parcial.

#### [DECIDIDO] La caja es por TURNO, no por día calendario

Se abre con un fondo, acumula los pagos del turno y se cierra con conteo físico. Consecuencias:

- **Puede haber varias sesiones en un mismo día.** Cambio de turno = cierre y apertura.
- **Un turno puede cruzar la medianoche** sin partirse.
- La pertenencia de un pago a una sesión es por **enlace explícito** (`venta.sesion_caja_id`),
  nunca por comparación de fechas. Es lo que permite las dos cosas anteriores.

Esto resuelve la ambigüedad del spec de negocio, que dice "cierre diario" en un lado y "el cajero
responde por su turno" en otro. Manda el turno: **el arqueo tiene que poder señalar a una persona**,
y un día calendario con dos cajeros no señala a nadie.

**`movimiento_caja`** — `sesion_id`, `tipo` (GASTO/RETIRO), `categoria_gasto_id`, `monto`,
`descripcion`, `registrado_por`

**`categoria_gasto`** — `nombre`, `naturaleza` (COSTO/GASTO)

**[DECIDIDO] El flete es GASTO, no costo de inventario.** Entra como categoría de gasto con
naturaleza GASTO y **no toca el kardex**. Consecuencia aceptada: el **margen bruto por producto
queda optimista** porque el costo no incluye transporte, mientras la **ganancia neta queda
correcta** porque el flete pega abajo de la línea. Para fijar precios es suficiente; solo hay que
no leer el bruto como si fuera el margen real.

**Costo vs gasto se deriva de la categoría, nunca se le pregunta al usuario.** Él elige QUÉ
compró; el sistema sabe dónde cae.

### 3.7 Auditoría — [DECIDIDO]

**`evento_auditoria`** — append-only, nunca se edita ni se borra
| Campo | Notas |
|---|---|
| `id`, `ocurrido_en` | |
| `usuario_id` | quién |
| `accion` | ANULAR_VENTA, APLICAR_DESCUENTO, AJUSTAR_INVENTARIO, CAMBIAR_PRECIO, CERRAR_CAJA_CON_DIFERENCIA |
| `entidad_tipo`, `entidad_id` | sobre qué |
| `antes`, `despues` | JSON — el estado a cada lado del cambio |
| `motivo` | obligatorio en toda acción sensible |

#### Por qué una tabla transversal y no campos en cada entidad

La pregunta que el dueño va a hacer es **"¿qué hizo Juan ayer?"**, y eso tiene que responderse con
una sola consulta. Con la auditoría repartida en cinco tablas —venta, kardex, caja, producto,
ajustes— esa pregunta requiere cinco consultas y un ensamble manual, y en la práctica nadie la hace.

Es la excepción deliberada a la regla de tres del skill `patrones`: aquí la abstracción se
justifica antes del tercer caso porque el valor está justo en **agregar acciones heterogéneas en
un solo lugar**.

Límite honesto: `antes`/`despues` en JSON **no es consultable por campo**. Es un registro para que
lo lea una persona, no una fuente para reportes. Los reportes siguen leyendo los campos concretos
de `venta` y `movimiento_kardex`. Las dos cosas coexisten con propósitos distintos.

#### La anulación de venta — [DECIDIDO] se permite

Sí se puede anular, incluso de una caja ya cerrada. Lo que queda registrado:

```
1. venta        → estado ANULADA + anulada_por / anulada_en / motivo  (obligatorio)
2. kardex       → movimiento de REVERSION por cada línea; el stock vuelve
3. caja         → el efecto va en la sesión de HOY, referenciando la venta original.
                  El cierre viejo NUNCA se toca: ya lo firmó alguien.
4. auditoría    → evento ANULAR_VENTA con el antes y el después
```

**Una venta no se edita nunca.** Si hay que corregirla, se anula y se hace de nuevo. Editar una
venta cobrada obligaría a rastrear qué cambió respecto a qué, y ese es exactamente el rastro que
la anulación + recreación deja gratis.

El punto 3 es la decisión importante: tocar un arqueo cerrado significa que la diferencia que un
cajero firmó ayer cambia hoy sin que él lo sepa. Registrar el efecto en la caja de hoy mantiene
cada turno responsable de lo que pasó durante su turno.

### 3.8 Seguridad

**`usuario`** — `usuario`, `hash`, `nombre`, `rol` (ADMIN/CAJERO), `activo`

**`usuario_propietario`** — **solo en el esquema de la nube.** El spec pide login propio del
propietario, separado del usuario local del cajero/admin.

### 3.9 Sincronización

**`evento_outbox`**
| Campo | Notas |
|---|---|
| `id` | UUID — el consumidor deduplica por este campo |
| `secuencia` | **monotónica por dispositivo. Es la clave de orden** |
| `tipo`, `version` | |
| `payload` | inmutable |
| `enviado_en`, `intentos`, `ultimo_error` | |

- **Se escribe en la misma transacción que la venta.** Si la venta se guarda, el evento existe.
- **Nunca se ordena por fecha.** Las PC de local tienen la pila del reloj muerta; si el replay
  aplica un movimiento de kardex antes del que lo precede, el saldo queda incoherente para siempre.
- **`version` existe porque en campo las dos puntas nunca están en la misma versión** — se
  despliega la nube el martes y nadie fue a la tienda a actualizar la PC.
- **Poda desde el día uno.** Una semana sin internet son miles de filas.

Detalle completo en el skill `backend`.

### 3.10 Lo que NO va en base de datos

**La venta interrumpida.** El spec pregunta qué pasa si se corta la luz a mitad de una venta. El
borrador del carrito vive en **IndexedDB del navegador**, se persiste en cada cambio (no al
cobrar — el corte ocurre antes de cobrar, que es todo el punto) y sobrevive igual al apagón. No
ensucia el modelo con ventas a medias.

---

## 4. Búsqueda: resolución en cascada

Un solo campo. El cajero escribe —o escanea, ver sección 8— y el sistema resuelve en orden:

```
1. coincidencia exacta de código de barras    → agrega al carrito
2. coincidencia exacta de código interno      → agrega al carrito
3. coincidencia exacta de código de proveedor → agrega al carrito
4. texto libre → descripción + nombre + aplicación_original
5. filtro estructurado: marca → modelo (compatibilidad)
```

Si acierta en 1–3, agrega directo y **devuelve el foco al buscador** (flujo primero-teclado, skill
`frontend`).

El nivel 5 es el que justifica normalizar la compatibilidad: el texto libre falla justo donde
duele. El cajero escribe `NS200`, el catálogo dice `NS 200`, y devuelve cero resultados con el
cliente enfrente. Un selector marca → modelo es determinista.

### Índices necesarios

| Tabla | Índice |
|---|---|
| `variante` | único en `codigo`; único parcial en `codigo_barras` |
| `variante` | `producto_id`; parcial en stock bajo |
| `producto` | texto completo sobre `nombre + aplicacion_original` |
| `compatibilidad` | `(modelo_id, producto_id)` y el inverso |
| `movimiento_kardex` | `(variante_id, creado_en)` |
| `venta` | `(sesion_caja_id)`, `(creado_en)` |
| `sesion_caja` | único parcial: una sola ABIERTA |
| `evento_outbox` | `(enviado_en, secuencia)` para el despachador |

---

## 5. Reglas de integridad que no se negocian

1. **No se vende sin stock.** Bloqueo en el caso de uso con bloqueo de fila, no solo en pantalla.
2. **No se vende sin caja abierta.** Si no, hay ventas fuera de todo arqueo.
3. **El kardex no se edita.** Corregir es agregar un movimiento, nunca modificar uno.
4. **Toda anulación deja constancia**: quién, cuándo, por qué, y el antes/después en
   `evento_auditoria`. Es lo único que sostiene el control cuando el mismo cajero autoriza sus
   propias correcciones.
5. **Una venta cobrada no se edita.** Se anula y se hace de nuevo. Editar obligaría a rastrear qué
   cambió respecto a qué; anular y recrear deja ese rastro gratis.
6. **Todo descuento lleva motivo y queda auditado.** Sin motivo no se cobra.
5. **Las partes suman el total.** Aplica a `costo_total_linea` vs unitario × cantidad, y a todo
   desglose de reportes.
6. **`null` no es `0`.** Un producto sin costo promedio reporta "—", no margen del 100%.
7. **Una escritura que SUMA lleva llave de idempotencia.** Vale para el reintento de la cola
   offline y para el doble clic.

---

## 6. Datos iniciales

| Qué | Cómo |
|---|---|
| `marca_vehiculo` + `modelo_vehiculo` | ~250 curados. Semilla extraída del catálogo Jotapartes |
| `categoria_gasto` | incluye TRANSPORTE/FLETE con naturaleza GASTO |
| `categoria` de producto | **16 categorías** — ver sección 6.1 |
| `usuario` | un ADMIN inicial |
| Productos | **ninguno.** Nacen al comprarlos |

### 6.1 Categorías de producto — [DECIDIDO]

**Por sistema del vehículo, un solo nivel.** El cliente llega describiendo un síntoma ("me falla el
freno"), no un tipo de pieza; y con categorías por tipo de pieza, cada referencia rara obliga a
inventar una categoría nueva.

Distribución medida contra las 8.824 filas del catálogo real:

| Categoría | Filas | % |
|---|---|---|
| MOTOR | 1.842 | 20,9% |
| EMPAQUES Y SELLOS | 1.102 | 12,5% |
| CONTROLES Y GUAYAS | 1.069 | 12,1% |
| ELÉCTRICO | 671 | 7,6% |
| ILUMINACIÓN | 474 | 5,4% |
| TRANSMISIÓN Y ARRASTRE | 359 | 4,1% |
| CARROCERÍA | 287 | 3,3% |
| FRENOS | 209 | 2,4% |
| SUSPENSIÓN Y DIRECCIÓN | 198 | 2,2% |
| RODAMIENTOS Y BUJES | 183 | 2,1% |
| FILTROS | 176 | 2,0% |
| TORNILLERÍA Y VARIOS | 176 | 2,0% |
| CARBURACIÓN | 174 | 2,0% |
| LLANTAS | 26 | 0,3% |
| LUBRICANTES Y QUÍMICOS | — | este catálogo no los trae; RD Motors sí los venderá |
| ACCESORIOS | — | cascos, guantes: lo que no es repuesto |

**La categoría no sirve para encontrar una pieza** — eso lo hace la búsqueda en cascada (sección 4).
Sirve para navegar cuando no se sabe qué se busca, y para los reportes por categoría que pide el
spec de negocio. Por eso no necesita precisión quirúrgica: necesita estar balanceada y ser
memorizable. Y es un campo editable, no una estructura.

**Derivación automática:** la categoría sale de la primera palabra de la descripción, que es muy
regular. Eso resuelve el **78,7%**. El 21,3% restante son palabras genuinamente ambiguas —`KIT`
(310 filas), `EJE` (263), `DISCO` (85), `BOMBA` (62), `TAPA`, `BASE`, `PORTA`— que se resuelven con
la **segunda** palabra:

```
DISCO CLUTCH              → TRANSMISIÓN Y ARRASTRE
DISCO FRENO               → FRENOS
BOMBA GASOLINA            → CARBURACIÓN
BOMBA FRENO               → FRENOS
KIT ARRASTRE              → TRANSMISIÓN Y ARRASTRE
KIT REPARACIÓN CARBURADOR → CARBURACIÓN
EJE BALANCÍN              → MOTOR
```

Son **~30 reglas de dos palabras, no 1.878 decisiones** producto por producto.

**Plan:** arrancar con estas 16, derivar el 79% automático, resolver las ~30 palabras ambiguas, y
**revisar después de un mes de uso real.** Si MOTOR al 21% molesta, se parte en MOTOR - INTERNO y
MOTOR - DISTRIBUCIÓN. No antes: partir por adelantado es adivinar.

#### [DECIDIDO] Las categorías las administra el cliente

**`categoria` es una tabla, no un enum.** Las 16 son **semilla, no lista fija**: el ADMIN puede
crear, renombrar, reordenar y desactivar categorías desde la aplicación, sin tocar código ni
esperar un despliegue.

| Campo | Notas |
|---|---|
| `id`, `nombre` | nombre único |
| `orden` | para el menú, editable |
| `activa` | desactivar ≠ borrar |

Reglas:

- **No se borra una categoría con productos.** Se desactiva (deja de ofrecerse al crear productos)
  o se reasignan sus productos primero. Borrar dejaría productos huérfanos y reportes históricos
  con huecos.
- **Desactivar no reclasifica nada.** Los productos que ya la tienen la conservan, y los reportes
  del año pasado siguen cuadrando. Una categoría desactivada sigue existiendo para la historia.
- Renombrar sí afecta a todo lo histórico — es el mismo concepto con otro nombre, y eso está bien.

Que sea tabla es también lo que hace honesto el plan de arriba: "revisar después de un mes" solo
es viable si revisar no requiere programador.

### Oferta pendiente de decisión

El catálogo de Jotapartes puede quedar como **tabla de consulta al crear un producto** —se teclea
`370PUL2N` y se autocompletan descripción, marca y aplicación— **sin que nada entre al inventario
ni aparezca en la búsqueda del cajero.**

Importa porque decidimos normalizar la compatibilidad: sin ese apoyo, alguien tiene que escribir a
mano `PULSAR NS 200/FI/AS 200-DUKE 200` y enlazar los modelos, producto por producto.

Si no se quiere, el modelo funciona igual — solo se teclea más.

---

## 7. Lo que NO se porta del car-wash

`Tenant` y su FK en cada tabla, `Subscription`, `Plan`, `TenantModule`/`ProductModule`, el medidor
de SMS/WhatsApp, el libro de la plataforma, los guardias `requireModule`, y toda la caja doble.

**Sí se porta** —y es lo valioso— el costo promedio ponderado, el kardex append-only, la
conciliación de caja y la idempotencia. **Portar, no copiar**: allá está en capas con las entidades
JPA haciendo de dominio.

---

## 8. Código de barras — [DECIDIDO] queda para el futuro

El campo `codigo_barras` va puesto desde hoy (nullable, único cuando existe) y la búsqueda lo
resuelve en el nivel 1. Con eso, la fase 2 es **aditiva, no una migración**.

Por qué se difiere:

- El código de Jotapartes **no viene en la pieza**. Evidencia del propio catálogo: el mismo filtro
  tiene `370PUL2N` (IMPORTADO), `352B59K` (INOKI) y `370P2NN` (FACTORY) — prefijos distintos para
  la misma pieza. Es un esquema del distribuidor, no una referencia de fabricante.
- Los códigos de barras de fábrica cubrirían tal vez la mitad del inventario, de forma
  impredecible: las marcas reales con caja sí traen EAN, lo importado y genérico a veces, y una
  pieza suelta sin caja no trae nada. Un escáner que funciona a veces es peor que ninguno.
- **El cuello de botella no está ahí.** El tiempo se pierde averiguando cuál pieza necesita el
  cliente, no metiendo el código. El escáner ayuda cuando ya tienes la pieza en la mano.
- Donde sí pagaría es en la **toma física de inventario**. Ese es el argumento honesto para
  comprarlo, no la velocidad en el mostrador.

Nota técnica para cuando llegue: **un escáner USB es un teclado.** Escribe los dígitos y pulsa
Enter. No hay driver ni SDK que integrar — con la búsqueda en cascada ya construida, funciona el
primer día.

La solución real de cobertura es **imprimir etiquetas propias** al recibir la mercancía, en el
flujo de compra ya diseñado. Eso es impresora, consumibles y trabajo de pegar: un proyecto en sí
mismo.

---

## 9. Preguntas abiertas

Ninguna bloquea el modelo; todas se pueden implementar con el supuesto declarado.

**1. Proveedores — [RESUELTO] Jotapartes NO es el único.**
`producto_proveedor` **entra al MVP como tabla** (sección 3.4). Con varios proveedores para la misma
pieza, el código de proveedor, el empaque y el historial de costo necesitan su propia fila por
proveedor — en la variante no caben.

**2. Devoluciones de cliente — [DIFERIDO a la fase de desarrollo].**
Confirmado que deben existir, pero se modelan cuando se esté construyendo el módulo de ventas, no
ahora.

Lo que hay que recordar cuando llegue el momento: **no es lo mismo que anular.** La venta fue
legítima, el dinero ya se cerró en un arqueo de otro día, y la pieza vuelve al estante. Necesita
flujo propio, movimiento de kardex de entrada, y salida de efectivo en la caja **del día en que se
recibe** — nunca tocando el cierre viejo.

Riesgo de diferirlo: si el módulo de ventas se construye sin dejarle sitio, agregarlas después
toca venta, kardex y caja a la vez. Basta con no asumir en ninguna parte que *el stock solo entra
por compras*.

**3. Anulación de venta — [RESUELTO]** se permite, con auditoría completa del antes y el después.
El cierre viejo no se toca; el efecto va en la caja de hoy. Ver sección 3.7.

**4. Categorías de producto — [RESUELTO]** 16 categorías por sistema del vehículo, ver sección 6.1.

**5. Ajustes de inventario — [RESUELTO] solo ADMIN.**
El cajero no ajusta stock. Motivo obligatorio y evento en `evento_auditoria`. Es la contrapartida
necesaria de que el cajero sí pueda anular y descontar: si además pudiera ajustar inventario, un
faltante se podría tapar sin dejar rastro.

**6. Descuentos — [RESUELTO]** por porcentaje o por monto, sobre el total, con motivo obligatorio y
auditado. Ver sección 3.5.

**7. Caja — [RESUELTO]** por turno. Ver sección 3.6.

**8. Consecutivo del ticket — [RESUELTO]** entero corrido que nunca reinicia. Ver sección 3.5.1.

---

## 10. Resumen del modelo

```
                        marca_vehiculo
                              │
                        modelo_vehiculo
                              │
                        compatibilidad
                              │
  categoria ────────────► producto ◄──── aplicacion_original (texto crudo)
                              │
                              │ 1..N
                              ▼
  producto_proveedor ────► variante ◄──── codigo_barras (futuro)
         │                    │
    proveedor           ┌─────┴─────┬──────────────┐
         │              ▼           ▼              ▼
      compra ───► movimiento    linea_venta    linea_compra
         │          kardex           │
         │                           ▼
         │                        venta ────► pago
         │                           │
         └───────────────────► sesion_caja ◄──── movimiento_caja
                                     │                  │
                                     │            categoria_gasto
                                     ▼
                              evento_outbox ──────► nube (espejo R/O)


  usuario ────► evento_auditoria      append-only, transversal
                      ▲               "¿qué hizo Juan ayer?" en UNA consulta
                      │
       anular venta · aplicar descuento · ajustar inventario
       cambiar precio · cerrar caja con diferencia
```
