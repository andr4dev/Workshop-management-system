# Spec 0012 — Cargar inventario desde un archivo, con pre-carga y precios ajustables

---

## 1. Objetivo de negocio

El dueño está cargando su inventario base y es **lento y tedioso**. La factura MAG477 de Importadora Jotapartes
(31-ago-2026) tiene **32 páginas y 592 renglones** (2.269 unidades); hoy cada uno se registra a mano en la pantalla de compras,
abriendo el formulario de repuesto nuevo, escribiendo código, nombre, categoría, marca, cantidad, costo y precio.
Seiscientas veces. Desde el celular, de noche.

Lo que se pide:

1. **Subir un archivo** (Excel, preferiblemente) con los renglones de la factura.
2. Ver una **pre-carga**: todo leído y calculado, **sin guardar nada todavía**.
3. Que el **precio de venta se sugiera solo** a partir del costo, con un porcentaje.
4. Que el socio **ajuste a mano** el precio de cualquier renglón ($2.000 o $3.000 más, o lo que quiera).
5. **Confirmar**, y que entonces entre todo al inventario.

La idea es buena y el sistema ya tiene casi todas las piezas (ver §3). Pero el pedido tal como está escrito tiene
**dos trampas de plata** que hay que resolver antes de construir nada. Están en la §4.

---

## 2. Casos de uso

| # | Historia | Prioridad | Qué se puede demostrar solo |
|---|---|---|---|
| **H1** | El dueño sube el archivo de la factura y ve la pre-carga: cada renglón con su código, descripción, cantidad, costo por unidad y precio sugerido. **Nada entra al inventario todavía** | **P1** | Se sube el archivo de la MAG477 y aparecen los ~600 renglones; el inventario sigue igual |
| **H2** | Ajusta a mano el precio de venta de cualquier renglón, y ve cuánto gana en ese repuesto | **P1** | Se cambia el precio de una bujía y la ganancia del renglón se recalcula en el acto |
| **H3** | Cambia el porcentaje y se recalculan los precios que **no** tocó a mano | **P1** | Se pasa del 64% al 60%: cambian los sugeridos, los ajustados a mano se quedan |
| **H4** | Confirma, y entra **todo junto o nada**: stock, costo de cada repuesto, historia del kardex | **P1** | Se confirma y el stock de los 600 aparece; si algo falla, no entra ninguno |
| **H5** | Lo que ya existe (mismo código) **suma stock** en vez de duplicarse, y se ve antes de confirmar | **P1** | Un código que ya estaba aparece marcado como "reposición" y al confirmar no crea otro repuesto |
| **H6** | Los renglones con problemas se señalan y no dejan confirmar hasta arreglarlos o quitarlos | **P1** | Un renglón sin cantidad sale en rojo y el botón de confirmar no se habilita |
| **H7** | Retoma la pre-carga donde la dejó, aunque cambie de celular a computador | **P2** | Se ajustan 50 precios en el celular, se abre en el computador y ahí siguen |
| **H8** | Baja una plantilla de Excel con las columnas que el sistema entiende | **P2** | Se baja el archivo y se llena sin adivinar el formato |

---

## 3. Qué existe hoy

Todo verificado en el código el 2026-09-25.

### Lo que ya resuelve media funcionalidad

| Hecho | Dónde |
|---|---|
| Una compra **ya puede crear el repuesto dentro del renglón**, sin salir de la compra | `domain/…/compras/aplicacion/ComandoRegistrarCompra.java:69-73` |
| Una compra acepta el costo como **el total del renglón** y deduce ella sola el costo por unidad ("me llegaron 15 y pagué $200.000") | `ComandoRegistrarCompra.java:74-75` y `:113-116` |
| El código del repuesto **es el del proveedor** — se descartó inventar códigos propios | `docs/specs/0001-…/spec.md:150-151` |
| El código es **único** y se guarda en mayúsculas | `domain/…/inventario/dominio/Variante.java:48` y `:113` |
| Un repuesto nuevo exige **código, marca, precio** y un **concepto** (uno existente, o un nombre nuevo con su categoría) | `domain/…/inventario/aplicacion/ComandoCrearRepuesto.java:26-50` |
| La **categoría es obligatoria**; hay 16 sembradas (MOTOR, EMPAQUES Y SELLOS, FRENOS, FILTROS, RODAMIENTOS Y BUJES, LLANTAS…) | `pos/…/db/migration/V2__categorias_semilla.sql`; `ModalRepuestoNuevo.jsx:27-28` |
| La pantalla de compras ya **propone la categoría del último repuesto nuevo** de la misma compra | `frontend/…/compra/ModalRepuestoNuevo.jsx:30-31` |
| Una compra no se registra dos veces aunque se mande dos veces | llave de la compra, V22 (spec 0009) |

### Lo que condiciona el diseño

| Hecho | Dónde | Consecuencia |
|---|---|---|
| **Solo la compra calcula el costo promedio.** No existe un movimiento de "inventario inicial"; el ajuste manual no toca el costo | `domain/…/inventario/dominio/TipoMovimiento.java:6-7`, `OrigenMovimiento.java:4-9` | La carga **tiene que entrar como compra**, o los repuestos quedan sin costo y los reportes de utilidad no sirven |
| El **precio de venta es final, con IVA incluido, sin desglose** | `docs/SPEC_Modelo_Datos.md:92` y `:102` | El sistema nunca separó el IVA. Pero sobre **el costo** no hay nada decidido — ver decisión 1 |
| El **margen** que muestra el sistema se calcula **sobre el precio de venta**, no sobre el costo | `frontend/src/utils/formato.js:39-42` | Tu "45% de ganancia" es sobre el costo. Son dos porcentajes distintos del mismo repuesto — ver decisión 2 |
| La variante **no tiene unidad de medida** | `Variante.java:41-81` | "PAR", "JGO", "KIT", "SET" de la factura no tienen dónde ir. Se venden como una unidad |
| **No hay ninguna librería de Excel** en el proyecto | `frontend/package.json`, `pos/pom.xml` | Leer el archivo es nuevo |
| El formulario de repuesto nuevo **no sabe de precios**: el precio vive en el renglón de la compra, junto al costo y al margen | `ModalRepuestoNuevo.jsx:20-25` | Una sola fuente de verdad para el precio. La pre-carga tiene que respetarlo |

### Lo que dice la factura real (MAG477)

| Columna | Qué es de verdad | Ejemplo: bujía iridium NGK |
|---|---|---|
| CANT. | Unidades | 8 |
| PRECIO UNIT | Precio de lista, **antes** del descuento | $46.993 |
| % DESCTO | Descuento del proveedor (15, 18 o 20%) | 18 |
| **VALOR TOTAL** | **El renglón entero** —las 8 unidades—, después del descuento, **sin IVA** | **$308.274** |
| IVA% | 19%, que se cobra **abajo**, sobre el total de la factura | 19% |

Totales de la factura: sub-total (la suma de VALOR TOTAL) **$14.729.523** + IVA **$2.798.609** = pagado
**$17.528.132**.

---

## 4. Las decisiones

### Primero, la trampa que no es decisión: el valor total es de todo el renglón

La idea dice *"valor total es el precio de costo"*. Leído al pie de la letra, sale esto:

| | Bujía iridium NGK (8 unidades, valor total $308.274) |
|---|---|
| Tomando el valor total como costo de **una** | costo $308.274 → precio sugerido **$505.569** |
| Dividiendo entre las 8 unidades | costo $38.534 → precio sugerido **$63.196** |

**El valor total hay que dividirlo por la cantidad, siempre.** En los renglones de 1 unidad da igual, y por eso
es fácil no notarlo; en los de 10, 20 o 60 unidades (bombillos, balineras, bujías) el precio sale **8, 20 o 60
veces más caro**. La compra ya hace esa división sola (§3), así que esto no es trabajo nuevo: es no saltársela.

Lo que sí acierta la idea es **usar el valor total y no el precio unitario**: el valor total ya trae el descuento
del proveedor. Tomar el precio unitario de lista inflaría el costo entre 15 y 20%.

### Decisión 1 — ¿El costo del repuesto lleva el IVA? · **la que mueve plata**

La factura cobra el IVA aparte, abajo. RD Motors pagó **$17.528.132**; la suma de los renglones es **$14.729.523**.
La diferencia —$2.798.609— es IVA, y **el sistema nunca decidió si es parte del costo o no**.

| | Costo sin IVA | Costo con IVA |
|---|---|---|
| Costo de la bujía | $38.534 | $45.856 |
| Valor del inventario de esta factura | $14.729.523 | $17.528.132 (**lo que de verdad se pagó**) |
| Utilidad en los reportes | **Inflada**: cuenta como ganancia el IVA que se pagó al proveedor | La real |

**Depende de si RD Motors recupera ese IVA:**

- **Si NO lo recupera** (lo usual en un almacén pequeño, y coherente con que el precio de venta se maneje *"final,
  sin desglose de IVA"*): el IVA que paga al proveedor es costo, como el flete. **El costo va con IVA.**
- **Si SÍ lo recupera** (descontándolo del IVA que cobra): no es costo. **El costo va sin IVA.**

> ✅ **Decidido el 2026-09-25: el costo va con IVA.** Lo dijo el usuario con la razón exacta: *"en el precio de
> costo ya viene incluido el IVA, por eso lo colocamos en el precio para saldarlo"*. El IVA pagado al proveedor se
> recupera cobrándolo en el precio de venta, no por ningún otro lado — así que es costo.
>
> Si algún día el contador dice lo contrario, es **cambiar el 19% por 0%**: un número, no rehacer nada.

**Tres consecuencias que salen de esta decisión**, y ninguna es solo de la carga masiva:

1. **Las compras a mano siguen la misma regla.** Si la carga masiva mete el costo con IVA y las compras a mano lo
   meten sin IVA, el costo promedio de un repuesto mezcla las dos cosas y deja de significar algo. La pantalla de
   compras tiene que decir claramente *"costo con IVA incluido"*.
2. **El total de la compra cuadra con lo que salió del banco.** La MAG477 queda registrada por **$17.528.132**, que
   es lo que se pagó — y no por $14.729.523. Los totales por cuenta del spec 0002 cuadran contra el extracto.
3. **Es el momento de fijarla.** En producción hay una sola compra, de prueba, y el usuario la anula (confirmado el
   2026-09-25). No queda nada registrado con la otra regla.

### Decisión 2 — La fórmula del precio sugerido

La idea dice *"sumarle el 64%, o sea 19% de IVA más 45% de ganancia"*. Hay dos maneras de leerlo, y con la bujía
se ve la diferencia:

| Forma | Cuenta | Precio sugerido | Lo que ganas sobre lo que pagaste (con IVA) |
|---|---|---|---|
| **A.** Sumar los porcentajes | $38.534 × 1,64 | **$63.196** | **37,8%** |
| **B.** Primero el IVA, después la ganancia | $38.534 × 1,19 × 1,45 | **$66.491** | **45%** |

**Con la forma A no ganas 45%: ganas 37,8%.** El 45% se calcula sobre el costo sin IVA, pero lo que salió del
bolsillo fue el costo con IVA. La forma B es la que de verdad deja 45% sobre lo pagado, y cuesta unos $3.300 más
en esta bujía (5%).

Y hay un segundo "porcentaje" que confunde: el sistema ya muestra un **margen** en la pantalla de compras y en la
ficha del repuesto, pero **sobre el precio de venta**, no sobre el costo (§3). Con la forma A, donde tú pusiste
"64%", esa pantalla diría **"Margen 39%"** — y alguien va a creer que el sistema calcula mal.

> ✅ **Decidido el 2026-09-25: la forma B.** Palabras del usuario: *"al valor total se le debe sumar el 19% y luego
> el 45%"*. Primero el IVA, después la ganancia.

Cómo queda:

1. **Dos números por carga, los dos editables**: el **IVA** (arranca en 19%) y la **ganancia** (arranca en 45%).
   *Precio sugerido = costo por unidad × (1 + IVA) × (1 + ganancia)*. Con la bujía: $38.534 × 1,19 × 1,45 =
   **$66.491**.
2. Como el costo ya se guarda con IVA (decisión 1), en pantalla la cuenta se lee más simple: **costo $45.856 + 45% =
   $66.491**.
3. La pre-carga **nombra los dos porcentajes distintos** y muestra los dos en cada renglón: *"le ganas 45% a lo que
   pagaste · 31% del precio de venta es ganancia"*. El segundo es el "margen" que el sistema ya muestra en compras y
   en la ficha del repuesto; sin nombrarlos distinto, alguien va a ver 31% donde puso 45% y va a creer que el
   sistema calcula mal.

### Decisión 3 — ¿De dónde sale el archivo?

| Fuente | A favor | En contra |
|---|---|---|
| **A. Excel** con las columnas de la plantilla | Es lo que se pidió. Sirve también para mercancía que no viene de una sola factura | **Alguien lo tiene que llenar.** Si el proveedor no lo manda en Excel, se convierte el PDF — y la conversión de esta factura sale sucia (abajo) |
| **B. El XML de la factura electrónica** | Toda factura electrónica en Colombia viene con un XML donde cada renglón ya está separado: código, descripción, cantidad, descuento, valor total, IVA. **Cero tecleo, cero errores de conversión** | Hay que confirmar que el dueño lo recibe. Normalmente llega en el mismo correo que el PDF, dentro de un ZIP |
| **C. Leer el PDF directamente** | Es lo que el dueño tiene en la mano | **Frágil.** En la MAG477 las descripciones largas se parten en dos renglones y corren las columnas (`093AKTCLKI`, `094CBFTA`, `127RTRJ`), y los renglones que caen en el borde de una página salen **repetidos** en la siguiente (`196H17K`, `203B59ITK`, `245B30K`) |

~~Recomendación: Excel primero; el PDF no se lee, porque un sistema que a veces lee mal una factura es peor que uno
que no la lee.~~

> 🔄 **La recomendación cambió el 2026-09-25**, por dos hechos nuevos:
>
> 1. **El proveedor solo manda PDF** (lo confirmó el usuario). Entonces "subir un Excel" en realidad es *"convertir
>    el PDF a Excel y después subirlo"*: el paso tedioso sigue ahí, y el convertidor rompe esta factura en los
>    mismos sitios de la tabla de arriba.
> 2. **Cada renglón de esta factura se comprueba solo.** *Cantidad × precio unitario − descuento* da el valor total
>    impreso, al peso. Se verificó en renglones de todo tipo: de 1 unidad, de 40, con descuento del 15, 18 y 20%, y
>    con la descripción partida en dos (`093AKTCLKI`: 1 × $7.280 − 15% = $6.188, lo impreso). Si el lector se
>    equivoca en **cualquier** número —la cantidad, el precio, el descuento o el total—, el renglón deja de cuadrar
>    y sale marcado. Y encima está el sub-total impreso al final, que valida la suma entera.
>
> Con esas dos comprobaciones, un error de lectura **no puede pasar en silencio**, que era toda mi objeción.

**Recomendación nueva: leer el PDF de Jotapartes directamente, y el Excel para todo lo demás.**

- **PDF de Jotapartes**: se sube tal cual llega por correo. La factura trae el texto adentro (no es una foto
  escaneada), así que se lee sin adivinar letras. Cada renglón se verifica con su propia cuenta; el sub-total se
  lee de la factura misma en vez de pedírselo a nadie.
- **Excel**, con la plantilla: para otros proveedores, cuyo PDF tiene otro diseño, y para mercancía que no viene de
  una factura.
- **Solo se lee el diseño de factura que se conoce.** Un PDF de otro proveedor no se intenta leer "a ver qué sale":
  se dice que no se reconoce y se ofrece la plantilla de Excel. Si otro proveedor se vuelve frecuente, se le
  enseña su diseño aparte.

> ✅ **Decidido el 2026-09-25: el PDF de Jotapartes directo**, y el Excel con la plantilla para lo demás. Es más
> trabajo de construir una vez y menos trabajo cada vez que llega una factura.

Queda afuera el XML de la factura electrónica: por norma de la DIAN el proveedor lo manda junto al PDF —normalmente
en un ZIP adjunto—, y sería la fuente perfecta. Si algún día aparece en el correo, es un spec corto aparte.

### Decisión 4 — ¿Dónde vive la pre-carga mientras se revisa?

Seiscientos renglones no se revisan de una sentada, y el socio y el dueño pueden hacerlo desde equipos distintos.

| | En el navegador | En el servidor |
|---|---|---|
| Cómo | Como la venta a medias de hoy | Un borrador guardado, que se confirma después |
| Si se cierra el navegador o se cambia de equipo | **Se pierde lo ajustado** | Sigue ahí |
| Cuesta | Casi nada | Guardar el borrador (una tabla nueva) |

**Recomendación: en el servidor.** El motivo entero del spec 0011 fue que el dueño cargue desde el celular; perder
una hora de precios ajustados porque se cerró una pestaña es el peor resultado posible de esta funcionalidad.

> ✅ **Decidido el 2026-09-25: en el servidor.**

### Decisión 5 — Entra como compra, siempre

No es una decisión abierta, es una consecuencia de §3: solo la compra fija el costo promedio. Entonces cada carga
**es una compra**, con su proveedor, su número de factura, su fecha y su forma de pago — exactamente como si se
hubiera tecleado a mano. De eso sale gratis todo lo que ya tienen las compras: se puede **corregir** y **anular**,
queda en el historial del proveedor, y no se registra dos veces.

---

## 5. Requisitos funcionales

**Leer el archivo**

- **RF-000** — Se sube **la factura en PDF de Jotapartes tal como llega**. De cada
  renglón se leen código, descripción, cantidad, unidad, precio unitario, descuento y valor total; del final, el
  sub-total y el IVA.
  - **Cada renglón se comprueba con su propia cuenta**: *cantidad × precio unitario − descuento = valor total*, con
    un peso de tolerancia por redondeo. Si no cuadra, el renglón sale marcado con los dos números a la vista.
  - Las **descripciones partidas en dos renglones** se unen, y los renglones que el PDF **repite al cambiar de
    página** se reconocen como el mismo y se cuentan una sola vez.
  - Un PDF que **no tiene el diseño de Jotapartes**, o que es una foto escaneada, **no se intenta leer**: se dice que
    no se reconoce y se ofrece la plantilla de Excel.
- **RF-001** — Se sube un archivo Excel (`.xlsx`) o de texto separado (`.csv`). La plantilla trae, en este orden:
  **código, descripción, cantidad, valor total**, y opcionalmente **marca** y **categoría**. Los nombres de columna
  se reconocen con o sin tilde, en mayúsculas o minúsculas.
- **RF-002** — Los números se leen en **formato colombiano**: `$12.943` son doce mil novecientos cuarenta y tres, no
  doce coma nueve. Y el archivo `.csv` que guarda un Excel en español viene separado por punto y coma, no por coma.
- **RF-003** — El **costo por unidad** es siempre el **valor total dividido entre la cantidad, más el IVA**
  (decisión 1). El IVA se escribe una vez por carga y arranca en 19%.
- **RF-004** — La pre-carga toma el **sub-total de la factura** (el que dice abajo, antes del IVA) —del PDF mismo si
  se subió el PDF, o pidiéndolo si se subió un Excel— y lo compara con la suma de los valores totales leídos. **Si no cuadran, no deja confirmar**, y dice la diferencia. Es la
  forma de enterarse de que se perdió, se repitió o se leyó mal un renglón. Al lado muestra el **total con IVA**
  que resulta, para compararlo con el total de la factura en papel.
- **RF-004a** — **Las partes suman el total.** Sumarle el 19% a cada renglón por separado y redondear a pesos no da
  exactamente el total de la factura: en 600 renglones, los redondeos se acumulan en unos cientos de pesos. Los
  costos se reparten para que la suma de la compra sea **exactamente** lo que se pagó ($17.528.132 en la MAG477),
  ni un peso más ni uno menos.

**La pre-carga**

- **RF-005** — Cada renglón muestra: código, descripción, cantidad, costo por unidad, **precio sugerido**, precio
  final, y la ganancia en pesos y en los dos porcentajes (decisión 2).
- **RF-006** — El **precio final** arranca igual al sugerido y **se puede cambiar a mano** en cualquier renglón.
- **RF-007** — Cambiar el porcentaje **recalcula los precios que no se tocaron a mano**. Los que se tocaron se
  respetan, y se ven marcados como ajustados.
- **RF-008** *(P2)* — El precio sugerido **se redondea hacia arriba** a los $100 (o a lo que se configure). Hacia
  arriba, porque redondear hacia abajo le quita ganancia a cada venta.
- **RF-009** — Un código que **ya existe** en el sistema se marca como **reposición**: se muestran su stock y su
  precio actuales. Al confirmar suma stock y recalcula su costo promedio; **su precio se conserva** salvo que se
  elija aplicar el nuevo.
- **RF-010** — La **marca** se toma de su columna. Si viene vacía, se **propone** a partir del final de la
  descripción (INOKI, NGK, KOYO, KANUNI, JAPAN…) y queda marcada como propuesta. Si no hay cómo proponerla, el
  renglón queda con problema.
- **RF-011** — La **categoría** se toma de su columna. Si viene vacía, se **propone** a partir de la descripción
  (*"EMPAQUE…"* → Empaques y sellos, *"BALINERA…"* → Rodamientos y bujes, *"FILTRO…"* → Filtros…) y queda marcada
  como propuesta. Se puede cambiar **una por una o a varias a la vez**.
- **RF-012** — Se puede **quitar un renglón** de la carga sin quitarlo del archivo.
- **RF-013** — Se puede **filtrar** la pre-carga: solo los que tienen problema, solo los propuestos, solo las
  reposiciones, o por texto. En un celular, con 600 renglones, es la diferencia entre usarla y no.
- **RF-014** *(P2)* — La pre-carga **se guarda sola** y se retoma desde cualquier equipo (decisión 4). Una pre-carga
  confirmada o descartada ya no se puede editar.

**Confirmar**

- **RF-015** — Confirmar registra **una sola compra** con todos los renglones: proveedor, número de factura, fecha
  y forma de pago se piden igual que en una compra a mano.
- **RF-016** — **Entra todo o no entra nada.** Si un renglón falla al confirmar, no queda ninguno a medias.
- **RF-017** — **Confirmar dos veces deja una sola compra**, aunque sea desde dos equipos a la vez.
- **RF-018** — Lo confirmado se puede **corregir y anular** como cualquier compra.

**La misma regla en las compras a mano**

- **RF-020** — La pantalla de compras de siempre dice claramente que el costo se escribe **con el IVA incluido**, y
  ofrece sumárselo cuando la factura lo trae aparte. Sin esto, la regla de la decisión 1 vale para la carga masiva y
  no para las compras a mano, y el costo promedio mezcla las dos.

**Quién**

- **RF-019** — Todo esto es **del administrador**. El cajero no ve la pre-carga, que está llena de costos.

---

## 6. Manejo de errores

| Qué pasa | Qué hace el sistema | Qué ve la persona |
|---|---|---|
| El archivo no es PDF, Excel ni CSV, o está dañado | No lo lee | *"Ese archivo no se puede leer. Sube la factura en PDF, un .xlsx o un .csv"* |
| Un PDF de otro proveedor, o escaneado como foto | No lo intenta | *"No reconozco el diseño de esta factura. Pásala a la plantilla de Excel"* — con el botón para bajarla |
| Un renglón del PDF cuya cuenta no cuadra | Lo marca y sigue | El renglón en rojo: *"8 × $46.993 − 18% da $308.274, pero la factura dice $380.274"*. Se corrige a mano mirando el papel |
| Falta una columna obligatoria | No arma la pre-carga | *"Falta la columna CANTIDAD"* — nombrando la que falta |
| Un renglón sin código, con cantidad cero o no numérica, o con un valor total que no se entiende | Lo marca y sigue con los demás | El renglón en rojo, con el porqué. **No deja confirmar** mientras siga ahí |
| El **mismo código dos veces** en el archivo | Marca los dos | *"Este código está dos veces"*. Pasa justo con los renglones del borde de página al convertir el PDF, y **no se suman solos**: sumarlos duplicaría stock |
| Falta la marca o la categoría y no hay cómo proponerla | Lo marca | No deja confirmar hasta completarla |
| La suma de los valores totales no cuadra con el sub-total escrito | No deja confirmar | *"Faltan $12.943: el archivo suma $14.716.580 y la factura dice $14.729.523"* |
| Un precio final **menor que el costo** | Lo avisa, **no lo impide** | El renglón en ámbar: *"vendes a pérdida: $4.200 por unidad"*. A veces es a propósito |
| Mientras se revisaba, alguien creó a mano uno de esos códigos | Al confirmar, ese renglón pasa a reposición | Se avisa antes de terminar, no después |
| Se cae la conexión a mitad de la confirmación | Entró todo o no entró nada | Al reintentar, no se duplica |
| Un cajero intenta entrar | No lo deja | *"Es del administrador"* |

---

## 7. Requisitos no funcionales

- **Tamaño**: una factura como la MAG477 (unos 600 renglones) se lee, se revisa y se confirma sin que se corte.
  **Hay que medirlo en el servidor de verdad**, que en el plan gratis tiene poca CPU (spec 0011, §7): si una
  confirmación de 600 renglones tarda más de lo que aguanta la conexión, se parte en lotes sin que el usuario lo
  note.
- **El archivo no sale del sistema**: se lee sin mandarlo a ningún servicio de afuera. Trae todos los costos del
  negocio.
- **Límite de tamaño** del archivo, para que un archivo equivocado de 50 MB no tumbe el servidor.
- **Usable en el celular**: editar un precio con el pulgar, filtrar, y confirmar sin tener que desplazarse de lado
  por una tabla ancha.
- **Las partes suman el total** (regla del proyecto): la suma de los costos confirmados es igual al sub-total de la
  factura, más el IVA si así se decide.

---

## 8. Criterios de aceptación

- [ ] Se sube **el PDF de la MAG477 tal como llegó** y aparecen sus **592** renglones, con
      **todos** cuadrando su propia cuenta y la suma igual al sub-total impreso ($14.729.523). Los renglones
      repetidos entre páginas (`196H17K`, `203B59ITK`, `245B30K`) aparecen **una sola vez**.
- [ ] Un PDF de otro proveedor se rechaza diciendo que no se reconoce, y ofrece la plantilla.
- [ ] Se sube un Excel con la plantilla y aparecen sus renglones; **el inventario no cambia** hasta confirmar.
- [ ] La bujía `524XRE3IJ` (8 unidades, valor total $308.274) muestra costo por unidad de **$45.856** (con IVA) y
      precio sugerido de **$66.491**, que con el redondeo de RF-008 queda en **$66.500** — **nunca un costo de
      $308.274**.
- [ ] `$12.943` en el archivo se lee como doce mil novecientos cuarenta y tres.
- [ ] El precio sugerido es *costo con IVA + 45%*, y se cambia a mano en cualquier renglón.
- [ ] Al confirmar la MAG477, la compra queda por **exactamente $17.528.132**, lo que se pagó.
- [ ] La pantalla de compras a mano dice que el costo va con IVA incluido.
- [ ] Cambiar el porcentaje mueve los precios no tocados y **respeta los ajustados a mano**.
- [ ] La suma de los valores totales cuadra con **$14.729.523**; si se borra un renglón del archivo, no deja confirmar.
- [ ] Un renglón con problema no deja confirmar; al quitarlo o arreglarlo, sí.
- [ ] Un código repetido en el archivo se marca y **no se suma solo**.
- [ ] Un código que ya existía **suma stock** y recalcula su costo promedio, sin crear otro repuesto.
- [ ] Al confirmar queda **una sola compra** con todos los renglones, y se puede anular como cualquier otra.
- [ ] Confirmar dos veces deja una sola compra.
- [ ] Se ajustan precios en un equipo y se ven en otro.
- [ ] El cajero no puede abrir la pre-carga.

---

## 9. Qué no se toca

- **Cómo se registra una compra**: sus reglas, su costo promedio, su kardex, su corrección y su anulación. La carga
  masiva **usa** la compra, no la reemplaza.
- **La pantalla de compras a mano**, salvo decir que el costo va con IVA incluido (RF-020).
- **Ventas, caja, cartera y reportes.**

## 10. Fuera de alcance

- **Leer PDFs de otros proveedores.** Solo el diseño de Jotapartes; los demás, por la plantilla de Excel hasta que
  alguno se vuelva frecuente.
- **El XML de la factura electrónica**, mientras no aparezca en el correo. Si aparece, es un spec corto aparte.
- **Agrupar marcas del mismo repuesto.** Hoy el sistema puede decir *"esta pastilla existe en NGK y en KANUNI"*
  (spec 0001). La carga masiva crea cada renglón como un repuesto aparte: `BUJIA CR7HSA NGK` y `BUJIA CR7HSA KANUNI`
  quedan sin relacionar. Adivinarlo por la descripción se equivocaría demasiado. Se pueden agrupar después, a mano.
- **Unidad de medida** (PAR, JGO, KIT, SET). Se vende como una unidad; la descripción ya suele decirlo (*"(PAR)"*,
  *"(JUEGO X 5)"*).
- **Cambiar precios en masa de lo que ya está cargado** (subir una lista de precios nueva). Es otra funcionalidad.
- **Stock mínimo**: entra en cero y se ajusta después en la ficha.
