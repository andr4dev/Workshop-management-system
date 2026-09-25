# Plan 0012 — Cargar inventario desde la factura, con pre-carga y precios ajustables

El **cómo** del [spec 0012](spec.md). Seis fases, cada una con su checkpoint demostrable, su prueba y su "romper a
propósito". La bitácora del final se llena mientras se implementa.

**Lo que ya se sabe antes de empezar**, porque se probó con la factura real (2026-09-25, prototipo desechable fuera
del repositorio): el PDF de la MAG477 trae el texto con la posición exacta de cada columna, y leyéndolo por
posiciones salieron **592 renglones, los 592 cuadrando su propia cuenta, y la suma igual al sub-total impreso**
($14.729.523). El riesgo más grande del spec —leer el PDF— está medido, no supuesto.

---

## Dos reglas que valen para todas las fases

**1. La factura real no entra al repositorio.** El repositorio es **público** (spec 0011) y la MAG477 trae el nombre,
la cédula, la dirección y el teléfono del dueño, además de los precios del proveedor. Entonces:

- Las pruebas que van al repositorio usan **facturas sintéticas que la misma prueba fabrica** con PDFBox, con el
  diseño de Jotapartes y sus trampas (descripción partida, renglón repetido entre páginas, cuadro de totales), pero
  con datos inventados.
- La prueba con la **MAG477 real** existe, pero **solo corre si se le dice dónde está el archivo** (variable
  `RDMOTORS_FACTURA_REAL`). En cualquier otro equipo se salta diciendo por qué.
- El `.gitignore` excluye `*.pdf` en todo el proyecto: si alguien la copia a la carpeta de pruebas por comodidad, no
  se sube por accidente.

**2. Nada se prueba contra la base de QA** (instrucción del usuario, 2026-09-23). Las pruebas usan Testcontainers,
como siempre; las mediciones de rendimiento usan un Postgres desechable levantado para eso y borrado después.

---

## Dónde cae cada pieza nueva

Un módulo nuevo, `carga`, porque la carga masiva tiene su propio ciclo de vida (se sube, se revisa días, se confirma
o se descarta) que no es el de una compra. **Pero no reimplementa la compra: la usa.**

| Pieza | Carpeta | Por qué ahí |
|---|---|---|
| El borrador y sus reglas: recalcular, ajustar, quitar, qué problemas tiene, si se puede confirmar | `domain/…/carga/dominio/` | Es el sustantivo del spec y tiene cuerpo |
| La regla del precio: *costo + IVA + ganancia*, y el redondeo | `domain/…/carga/dominio/` | Una cuenta de plata, pura |
| El reparto del IVA para que las partes sumen el total | `domain/…/carga/dominio/` | Mismo algoritmo que `RepartoDeDescuento`; ver bitácora sobre por qué no se comparte todavía |
| Proponer marca y categoría desde la descripción | `domain/…/carga/dominio/` | Reglas de negocio, sin Spring |
| **Leer una factura** | `domain/…/carga/dominio/puerto/` | Lo que el dominio **pide** de afuera. **Tres implementaciones hoy**: el PDF de Jotapartes, el Excel y el CSV. Es un puerto de los que sí se ganan su lugar |
| Guardar el borrador | `domain/…/carga/dominio/puerto/` | Postgres y el falso de las pruebas |
| Subir, editar, consultar, confirmar, descartar | `domain/…/carga/aplicacion/` | Cada uno abre su transacción |
| El lector del PDF (PDFBox), del Excel (fastexcel-reader), del CSV, y los números en formato colombiano | `pos/…/carga/infraestructura/` | Importan librerías de afuera |
| El controlador, el repositorio JPA | `pos/…/carga/infraestructura/` | HTTP y JPA |
| Buscar variantes por varios códigos, proveedor por NIT, marcas en uso | puertos **existentes** de inventario y compras, un método nuevo cada uno | Hoy se busca de a un código: serían 592 consultas |

---

## Fase 1 · Las cuentas

Solo dominio: todo lo que mueve plata, probado con los números de la factura real antes de que exista una pantalla.

### Qué se construye

**`domain/…/carga/dominio/`**

- `ReglaDePrecio` (IVA %, ganancia %, redondeo) → `costoConIvaPorUnidad(valorTotal, cantidad)` y
  `sugerido(costoConIva)`. Costo por unidad con 4 decimales, como el resto del sistema; el precio en pesos enteros y
  **redondeado hacia arriba** al múltiplo configurado (100 por defecto).
- `RepartoDelIva` — el IVA total (`subtotal × IVA %`, redondeado) repartido entre los renglones en proporción a su
  valor total: cada parte hacia abajo, y los pesos que sobran, de a uno, a los renglones a los que más les faltó
  (residuo mayor). **La suma de los renglones con IVA es exactamente el total de la factura.** Es el mismo patrón de
  `reportes/dominio/RepartoDeDescuento.java:26-57` (ver bitácora).
- `ProponedorDeMarca` — si la descripción termina en una marca conocida (lista sembrada con las de la MAG477: INOKI,
  NGK, KOYO JAPON, KANUNI, ARX, SUN, CBI, NIRIN, DARROW, LEO, JAPAN, NAL.… más las que ya estén en uso en el
  sistema), la propone y **devuelve la descripción sin ella** para el nombre del repuesto.
- `ProponedorDeCategoria` — una tabla de palabras iniciales hacia las 16 categorías sembradas (*EMPAQUE* → Empaques
  y sellos, *BALINERA* → Rodamientos y bujes, *BANDAS/PASTILLAS FRENO* → Frenos, *GUAYA* → Controles y guayas…). Si
  la categoría fue renombrada o desactivada, no propone nada: mejor vacío que equivocado.

### Prueba

- `ReglaDePrecioTest`: la bujía `524XRE3IJ` — 8 unidades, $308.274 → costo $45.855,7575 → sugerido **$66.491**, y
  con redondeo a 100, **$66.500**. Un renglón de 1 unidad y uno de 60. IVA en 0% da el costo sin IVA (la salida que
  el spec deja por si el contador dice otra cosa).
- `RepartoDelIvaTest`: con los totales de la MAG477 la suma es **$17.528.132** al peso. Y una prueba de propiedad:
  para cualquier lista de totales al azar, la suma repartida es siempre `subtotal + IVA`.
- `ProponedorDeMarcaTest`, `ProponedorDeCategoriaTest`: con descripciones reales de la factura, incluidas las que no
  traen marca (`EMPAQUE CULATA LAMINA ACERADA CB110`) y las de marca de dos palabras (`KOYO JAPON`).

**Romper a propósito:** quitar la división por la cantidad → la bujía sale a $505.569 y falla. Sumar los porcentajes
en vez de encadenarlos → sale $63.196 y falla. Quitarle el sobrante al reparto → la suma se queda corta y falla.

### Checkpoint

Las pruebas en verde con los números de la MAG477. Todavía no hay nada que ver en pantalla, y es a propósito: si las
cuentas están mal, que se sepa aquí.

---

## Fase 2 · Leer la factura

La parte que el prototipo ya probó, ahora en serio y con pruebas.

### Qué se construye

**`domain/…/carga/dominio/`**: `RenglonLeido`, `FacturaLeida` (renglones + sub-total, IVA, NIT, número y fecha
impresos, si los hay), `FacturaNoReconocidaException`.

**`domain/…/carga/dominio/puerto/`**: `LectorDeFactura` — `reconoce(nombreArchivo, contenido)` y `leer(contenido)`.

**`pos/…/carga/infraestructura/`**

- `LectorPdfJotapartes` (PDFBox 3.0.8). Las reglas que el prototipo validó:
  - **Reconoce** la factura por el NIT de Jotapartes (900576528) en el encabezado **y** por los títulos de las ocho
    columnas. Si falta cualquiera de las dos, no es su diseño y no la lee.
  - **Columnas por posición**, no por orden del texto: el encabezado de cada página dice dónde empieza cada columna
    (ITEM x≈14, DESCRIPCION x≈81, CANT. x≈315, UM x≈349, PRECIO x≈387, % x≈453, VALOR TOTAL x≈483, IVA% x≈546, en la
    MAG477). Los límites de las columnas quedan **medidos una vez** con la MAG477, y en **cada página** se comprueba
    que los títulos sigan donde se midieron (ver bitácora: se cambió de "deducirlos de cada página").
  - **Renglón = misma altura**, con tolerancia. Una línea sin código es **continuación** del renglón anterior: más
    descripción, o el descuento y el total que quedaron un poco más abajo.
  - **La tabla va entre su encabezado y el pie de página**, y en la última página termina en el cuadro de totales —
    sin eso, *"RETENCIÓN"* se lee como un renglón (le pasó al prototipo).
  - **El renglón repetido entre páginas**: si el primero de una página tiene el código del último de la anterior,
    es la repetición escondida del PDF y se descarta **junto con las líneas de continuación que lo siguen**, porque
    la página anterior ya las traía. *(El prototipo descartaba la repetición pero no su continuación, y
    `196H17K` quedó con "TRAIL-XL200-XR200 INOKI" dos veces.)*
  - Del cuadro final se leen **sub-total, IVA y total**; del encabezado, **número (MAG477) y fecha**.
- `LectorExcel` (fastexcel-reader 0.20.2): columnas por **título**, con o sin tilde y sin importar mayúsculas.
- `LectorCsv`: separado por `;` o `,` —se detecta—, con comillas.
- `NumerosColombianos`: `$12.943` → 12943; `1.234,56` → 1234,56; celdas numéricas de Excel tal cual.

### Prueba

- `LectorPdfJotapartesTest`, **con una factura sintética que la prueba fabrica**: 25 renglones inventados en el
  diseño de Jotapartes, repartidos en dos páginas, con una descripción partida, un renglón repetido en el borde de
  página y el cuadro de totales. Tiene que leer 25, sin repetir, con las descripciones enteras y los totales bien.
- `LectorPdfJotapartesRealTest` — **solo si `RDMOTORS_FACTURA_REAL` apunta a la MAG477**: 592 renglones, 592
  cuadrando su cuenta, suma $14.729.523, 2.269 unidades, `196H17K` con su descripción **una sola vez**, número
  MAG477 y fecha 31-08-2026.
- Un PDF sintético de otro diseño → `FacturaNoReconocidaException`. Un PDF sin texto → lo mismo.
- `LectorExcelTest` y `LectorCsvTest` con archivos sintéticos (sí se suben: no tienen datos reales). CSV con `;` y
  con `,`. Una columna faltante → error que la nombra.
- `NumerosColombianosTest`: `$12.943`, `12.943`, `12943`, `1.234,56`, vacío, basura.

**Romper a propósito:** no descartar el renglón repetido → aparecen 593 y un código dos veces. Leer por orden del
texto en vez de por posición → la descripción partida se come el total. Leer `12.943` como decimal → los totales no
cuadran con el impreso.

### Checkpoint

La prueba real, en este equipo, contra la MAG477 que está en `Downloads`: **592 de 592**.

---

## Fase 3 · La pre-carga, guardada en el servidor

### Qué se construye

**V26** — `carga_inventario` y `renglon_carga`:

- `carga_inventario`: id, estado (`BORRADOR`, `CONFIRMADA`, `DESCARTADA`), origen (`PDF_JOTAPARTES`, `EXCEL`, `CSV`),
  nombre del archivo, proveedor, número y fecha de la factura, sub-total impreso, IVA %, ganancia %, redondeo,
  forma de pago y cuenta, quién y cuándo la creó, quién y cuándo la confirmó, la compra que resultó, versión.
  - `CHECK`: confirmada ⇔ tiene compra; porcentajes ≥ 0.
- `renglon_carga`: carga, posición, código, descripción, cantidad, unidad, precio unitario, descuento, valor total,
  marca (y si es propuesta), categoría (y si es propuesta), precio final, si se ajustó a mano, si se quitó, y si se
  aplica el precio nuevo a la reposición. *(Si es reposición no se guarda: se mira cada vez — ver bitácora.)*
  - Único `(carga, posición)`. Sin `CHECK` de cantidad > 0: **un borrador tiene que poder guardar renglones malos**,
    para mostrarlos marcados; lo que no puede es confirmarse.
- El archivo subido **no se guarda**: se lee y se descarta. Lo que queda es lo leído.

**`domain/…/carga/dominio/`**: `CargaDeInventario` y `RenglonDeCarga` (entidades JPA, como el resto del dominio), con
sus reglas:

- `cambiarPorcentajes` recalcula **solo** los precios no ajustados a mano.
- `ajustarPrecio`, `volverAlSugerido`, `cambiarMarca`, `cambiarCategoria` (uno o varios), `quitar`, `restaurar`.
- `problemas()`: por renglón (sin código, cantidad inválida, no cuadra su cuenta, código repetido, sin marca, sin
  categoría) y de la carga (la suma no cuadra con el sub-total). **Precio bajo el costo es aviso, no problema.**
- Todo lo que edita exige `BORRADOR`.

**Puertos existentes, un método cada uno**: `RepositorioVariantes.buscarPorCodigos` y
`RepositorioVariantes.marcasEnUso`. *(El proveedor por NIT no necesitó método: ver bitácora.)*

**`domain/…/carga/aplicacion/`**: `SubirFactura` (elige el lector que reconoce el archivo, marca las reposiciones con
**una sola consulta**, propone marca y categoría, busca al proveedor por el NIT impreso), `EditarCarga`,
`ConsultarCarga`, `DescartarCarga`. Todos exigen administrador.

**`pos/…/carga/infraestructura/`**: `RepositorioCargasJpa`, `CargaController` — subir (multipart, límite 5 MB),
listar, consultar, editar (un endpoint por gesto), descartar. `ManejadorDeErrores`: la factura no reconocida → 422
con su mensaje.

### Prueba

- `CargaDeInventarioTest`: cambiar el % respeta los ajustados; un renglón malo bloquea confirmar y quitarlo lo
  desbloquea; la suma que no cuadra bloquea; el mismo código dos veces marca los dos; lo confirmado o descartado no
  se edita.
- `SubirFacturaTest`, con los falsos: la reposición se marca con el precio y stock actuales; el proveedor se
  encuentra por NIT; el cajero no puede.
- `CargaIntegracionTest` (Testcontainers + HTTP): se sube la factura sintética, se edita, se relee y lo editado
  sigue ahí.
- `MigracionesIntegracionTest`: V26 sobre una base con filas.

**Romper a propósito:** que cambiar el porcentaje pise los ajustados a mano → falla. Dejar editar una carga
confirmada → falla.

### Checkpoint

Por la API, contra un Postgres desechable: se sube la factura, se cambia la ganancia a 50%, se ajusta la bujía a
$68.500, se vuelve a consultar **desde otra sesión**, y los dos cambios están.

---

## Fase 4 · Confirmar

### Qué se construye

**`domain/…/carga/aplicacion/ConfirmarCarga`**:

1. Bloquea la carga y exige `BORRADOR` sin problemas.
2. **Vuelve a mirar los códigos**: si alguien creó uno a mano mientras se revisaba, ese renglón pasa a reposición —
   y se avisa antes de terminar, no después.
3. Reparte el IVA (fase 1) y arma **una** compra: proveedor, número, fecha, forma de pago y cuenta de la carga;
   cada renglón por su **total con IVA** (el modo "me llegaron 15 y pagué $200.000" que ya existe); los nuevos con
   su repuesto adentro (`ComandoCrearRepuesto.conConceptoNuevo`: la descripción sin la marca, su categoría, su
   código, su marca, su precio final, stock mínimo 0); las reposiciones con su variante y, si se eligió, el precio
   nuevo.
4. **La llave de la compra es el id de la carga.** Confirmar dos veces, o desde dos equipos a la vez, lo resuelve la
   compra misma: la segunda vez la encuentra por su llave y la devuelve (`RegistrarCompra.java:76-79` y `:110-113`).
5. Llama a `RegistrarCompra` **dentro de la misma transacción**: o entra la compra y la carga queda confirmada, o no
   pasa ninguna de las dos.

**No se toca `RegistrarCompra`** — el spec lo pide (§9). Si la medición de abajo dice que es lenta con 592 renglones,
se anota en la bitácora y se decide ahí.

### Medir antes de dar la fase por hecha

Con 592 renglones sintéticos, el contenedor con los límites del plan gratis (`--memory=512m --cpus=0.1`, como en el
spec 0011) y un **Postgres desechable**:

- Si confirma en menos de **60 segundos**, queda así.
- Si no, la confirmación pasa a correr **en segundo plano**: el botón responde enseguida, la pantalla muestra
  *"Registrando 592 renglones…"* y se actualiza sola. La carga ya tiene el estado para eso.

### Prueba

- `ConfirmarCargaTest`: la compra queda por **exactamente** subtotal + IVA; los nuevos nacen con su precio final; la
  reposición suma stock y conserva su precio salvo que se elija lo contrario; confirmar dos veces deja una compra;
  el código creado a mano a mitad de camino se vuelve reposición; el cajero no puede.
- `CargaIntegracionTest`: confirmar la factura sintética y comprobar stock, costo promedio, kardex y el total de la
  compra contra la base. Anular esa compra deja el inventario como estaba.
- `CargaIntegracionTest`, dos confirmaciones **a la vez** desde dos hilos → una sola compra.

**Romper a propósito:** no usar el id de la carga como llave → la prueba de dos a la vez deja dos compras. Saltarse
la comprobación de problemas → falla.

### Checkpoint

Con la MAG477 real y un Postgres desechable: se confirma y queda **una** compra por **$17.528.132**, con **2.269
unidades** en inventario, y la bujía con costo promedio **$45.855,75** (su parte del IVA repartido es $58.572:
($308.274 + $58.572) ÷ 8).

---

## Fase 5 · La pantalla

### Qué se construye

**`frontend/src/`**

- `paginas/Cargas.jsx` — las cargas en borrador y las recientes, el botón **Subir factura** (PDF, Excel o CSV) y el
  enlace a la plantilla. Se llega desde **Compras**. Ruta `/cargas`, del administrador.
- `paginas/PreCarga.jsx`:
  - Arriba: proveedor (ya elegido si se reconoció el NIT), número, fecha, forma de pago y cuenta; **IVA %**,
    **ganancia %** y redondeo; y el cuadro que dice si **cuadra**: sub-total leído, sub-total impreso, total con IVA.
  - Los renglones: en el celular como **tarjetas**, no como tabla ancha; en el computador, tabla. De a 50, con
    **filtros**: con problema, propuestos, reposiciones, ajustados a mano, y búsqueda.
  - Cada renglón: código, descripción, cantidad, costo por unidad con IVA, sugerido, **precio final editable**, y
    *"le ganas 45% a lo que pagaste · 31% del precio es ganancia"*. Marca y categoría con su sello de "propuesta".
    Quitar y restaurar. Marcar varios —o "todos los que no tienen"— y ponerles una **marca** o una **categoría**:
    176 de los 592 renglones de la MAG477 no traen marca reconocible, y de a uno serían 176 cambios.
  - **Se guarda solo**: cada cambio viaja al servidor medio segundo después de dejar de escribir, y la pantalla dice
    *"Guardado"*.
  - **Confirmar** deshabilitado mientras haya problemas, con el porqué a la vista. Al confirmar, un resumen antes del
    último clic: *"592 repuestos, 2.269 unidades, compra por $17.528.132"*.
- `utils/carga.js` (+ `carga.test.js`): los textos de ganancia con sus dos porcentajes, los filtros, el resumen de
  problemas.
- `api/cliente.js`: `cargasApi`, con la subida del archivo (el resto de la API manda JSON; esta manda el archivo).
- `utils/permisos.js`: `/cargas` es del administrador.

**RF-020, en la pantalla de compras a mano** (`paginas/Compra.jsx`): la columna dice *"Costo con IVA"*, y un botón
*"La factura trae el IVA aparte: sumar 19%"* que lo suma a los costos escritos.

### Prueba

- `carga.test.js`: los dos porcentajes con la bujía (45% y 31%); un precio bajo el costo da aviso; los filtros.
- Capturas con Edge sin interfaz (memoria del proyecto), **sin tocar la base de QA**: la pre-carga en claro, oscuro y
  a 390 px, con respuestas simuladas.

**Romper a propósito:** mostrar el margen sobre el precio con el nombre de "ganancia" → la prueba del texto falla.

### Checkpoint

El recorrido completo en el navegador con la factura sintética: subir, filtrar los problemas, arreglar uno, ajustar
un precio, cambiar la ganancia, recargar la página, confirmar.

---

## Fase 6 · A producción

1. La suite completa en verde, el `Dockerfile` construye con las dos librerías nuevas, y `verificar-despliegue.mjs`
   pasa contra el contenedor.
2. Subir a GitHub; Render despliega solo. La V26 corre sola al arrancar.
3. **La primera carga real la hace el dueño**, con la MAG477: es su inventario y su decisión de precios. Antes de que
   confirme, se revisa con él que la pre-carga diga 592 renglones, que cuadre, y que el total sea $17.528.132.
4. Anular la compra de prueba que quedó en producción (el usuario lo confirmó el 2026-09-25).
5. Documentos: `docs/specs/README.md`, `PLAN_DE_TRABAJO.md`, y una sección en `docs/` de cómo se carga una factura.

---

## Verificación, al cerrar cada fase

1. `./mvnw -o -pl domain install -DskipTests` y luego `./mvnw clean test` — **instalar `domain` antes**, o `pos`
   compila contra una versión vieja.
2. `cd frontend && npx eslint src/ && node --test src/utils/*.test.js && npx vite build`.
3. El checkpoint de la fase.
4. El "romper a propósito": cada cambio hace fallar **su** prueba, y solo esa.

---

## Riesgos conocidos

| Riesgo | Cómo se ve | Qué hacer |
|---|---|---|
| **Jotapartes cambia el diseño de su factura** | *"No reconozco el diseño de esta factura"* con un PDF que antes sí se leía | Es lo correcto: no lee a ciegas. Se ajusta el lector con la factura nueva; mientras, el Excel |
| Confirmar 592 renglones tarda demasiado con la CPU del plan gratis | La confirmación se corta o la pantalla se queda esperando | La medición de la fase 4 lo decide antes de llegar a producción |
| La memoria con un PDF de 33 páginas | El contenedor se reinicia al subir | PDFBox con memoria acotada, y el límite de 5 MB. Medirlo en la fase 2 |
| Una categoría propuesta equivocada que nadie revisa | Repuestos en la categoría que no es | Van marcados como "propuesta" y se pueden filtrar; el resumen antes de confirmar dice cuántas propuestas quedan sin revisar |
| Las dos marcas del mismo repuesto quedan sin relacionar | En el mostrador no aparece *"también hay en KANUNI"* | Fuera de alcance a propósito (spec §10); se agrupan a mano después |

---

## Bitácora

| Fecha | Fase | Decisión | Por qué |
|---|---|---|---|
| 2026-09-25 | — | Antes de escribir el plan se probó leer la MAG477 real por posiciones, con un prototipo desechable | Era el riesgo más grande del spec y convenía medirlo, no suponerlo. Resultado: 592 de 592 cuadrando y la suma exacta. Encontró dos trampas que el plan ya incluye: el cuadro de totales leído como renglón, y la continuación repetida del renglón que cruza de página |
| 2026-09-25 | — | La factura real no entra al repositorio; la prueba real solo corre con `RDMOTORS_FACTURA_REAL` | El repositorio es público y la factura trae datos personales del dueño y los precios del proveedor |
| 2026-09-25 | 1 | `RepartoDelIva` repite el algoritmo de `RepartoDeDescuento` en vez de compartirlo | Compartirlo obliga a tocar reportes, que el spec deja fuera. Son dos usos del mismo patrón: si aparece un tercero, se sacan los tres a `compartido` |
| 2026-09-25 | 3 | El archivo subido no se guarda: se lee y se descarta | Lo que importa es lo leído, que queda en el borrador. Guardar el PDF sería guardar otra copia de los costos del negocio sin necesidad |
| 2026-09-25 | 4 | La llave de la compra es el id de la carga | Así "confirmar dos veces" lo resuelve la compra misma, con lo que ya existe desde el spec 0009, sin inventar otro mecanismo |
| 2026-09-25 | 1 | Los pesos que sobran del IVA van de a uno a los de **residuo mayor**, no todos al renglón más caro | Con 592 renglones sobran cientos de pesos; echárselos todos al más caro le cambia el costo a ese solo. Por residuo, ningún renglón se mueve más de un peso de su parte exacta, y la suma sigue siendo el total al peso |
| 2026-09-25 | 1 | Costo de la bujía: **$45.855,7575** para el sugerido, **$45.855,75** en el costo promedio | El sugerido sale de *valor total ÷ cantidad × 1,19* por unidad. La compra, en cambio, entra por el total del renglón con su parte del IVA **en pesos** ($308.274 + $58.572 = $366.846), porque las partes tienen que sumar lo que se pagó. La diferencia es de medio centavo por unidad |
| 2026-09-25 | 1 | El sugerido por defecto sale redondeado a $100: la bujía da **$66.500**, no $66.491 | Es RF-008. El criterio del spec se aclaró para decir los dos números |
| 2026-09-25 | 2 | Los límites de las columnas se midieron una vez en la MAG477; en cada página se comprueba que los títulos estén donde se midieron (±8 puntos) | Los títulos van **centrados** sobre su columna y los valores no: el código a la izquierda, la plata a la derecha. Deducir los límites de los títulos daba cortes que no sirven para los valores. Medidos y vigilados, si Jotapartes mueve una columna la factura se rechaza diciendo cuál, en vez de leerse corrida |
| 2026-09-25 | 2 | Cada página se agrupa apenas termina y sus letras se sueltan | Con las 32 páginas guardadas a la vez, leer la MAG477 no cabía en 32 MB de memoria; ahora cabe (medido: entre 8 y 16 MB por encima de lo que gasta la prueba sin leer nada). En la nube la aplicación entera tiene 128 MB |
| 2026-09-25 | 2 | Romper a propósito: no saltar el repetido → 5 pruebas fallan (código duplicado, total vacío); pegar la continuación del repetido → falla la de la descripción; leer `12.943` como decimal → fallan 16 en los tres lectores | El "leer por orden del texto" del plan no se rompió como cambio de una línea: el lector no tiene ese camino. Lo que lo prueba es la factura sintética con la descripción partida, y el prototipo que sí leía en orden y se comía los totales |
| 2026-09-25 | 2 | 176 de los 592 renglones no traen marca reconocible: la pre-carga pone marca a **varios a la vez**, incluido "todos los que no tienen" | Sin eso son 176 cambios de a uno; la regla de "sin marca" es problema y no deja confirmar |
| 2026-09-25 | 2 | Las pruebas de Excel arman el `.xlsx` a mano (un zip con cinco XML) | fastexcel-reader solo lee; meter una librería que escribe solo para las pruebas no se justifica |
| 2026-09-25 | 3 | **Si un renglón es reposición no se guarda: se mira cada vez que se revisa la carga**, con una sola consulta por todos los códigos | Guardado, se vuelve viejo: si alguien crea a mano uno de esos códigos mientras la carga espera, el renglón seguiría diciendo "nuevo" y chocaría al confirmar. Mirado en vivo, pasa solo a reposición (§6 del spec) |
| 2026-09-25 | 3 | El proveedor se busca por NIT entre los activos, sin método nuevo en el puerto | Son unas decenas de proveedores. En la ficha el NIT se escribe de cualquier forma (`900.576.528-1`, `900576528`): se comparan solo los dígitos, con o sin el de verificación (`Nit.coincide`) |
| 2026-09-25 | 3 | La misma factura (NIT + número) no puede tener dos cargas vivas: la segunda subida responde 409 con el id de la primera. Se puede volver a subir si se descartó, o si su compra se anuló | Confirmar dos cargas de la MAG477 entraría la mercancía dos veces. Anular la compra es justo la salida para cargarla de nuevo bien. Un índice único parcial cubre dos subidas en el mismo instante |
| 2026-09-25 | 3 | Cada gesto responde con la carga entera revisada de nuevo, sin los campos vacíos | Arreglar un renglón puede quitarle el problema a otro (el código repetido) o habilitar la confirmación, y la pantalla no tiene que adivinarlo. Si pesa demasiado en el celular con 592 renglones, se mide en la fase 5 y se decide ahí (compresión, o devolver solo lo que cambió) |
| 2026-09-25 | 3 | Un descuento de 0,18 cuadra también como 18% | Es lo que guarda Excel en una celda con formato de porcentaje. Solo afecta la comprobación del renglón: el costo sale del valor total |
| 2026-09-25 | 3 | Se quitó el problema "repuesto desactivado" que se había escrito | La aplicación no tiene cómo desactivar un repuesto: era código para un estado imposible, sin prueba que lo pudiera ejercer |
| 2026-09-25 | 3 | La plantilla es un CSV con solo los títulos, con la marca de UTF-8 y punto y coma | Excel en español lo abre como hoja sin preguntar. Sin renglón de ejemplo, para que nadie lo cargue por olvido |
| 2026-09-25 | 3 | El límite de 5 MB se prueba en el caso de uso, no por HTTP | Tomcat puede cortar la conexión antes de responder el 413 cuando el archivo es grande, y la prueba saldría intermitente. La pantalla revisa el tamaño antes de subir (fase 5) |
| 2026-09-25 | 3 | Romper a propósito: que cambiar el porcentaje pise los ajustados → fallan 2; dejar editar lo descartado → fallan 2; sumar solo los incluidos (que quitar "arregle" la suma) → fallan 2 | Cada cambio hizo fallar su prueba |
| 2026-09-25 | 4 | **Medido con la MAG477 real** en un contenedor con `--cpus=0.1 --memory=512m` y un Postgres desechable (no QA): subir y armar la pre-carga 16 s; cada gesto sobre los 592 renglones entre 0,6 y 3 s; **confirmar 41 s**; memoria 303 MB de 512. Queda una compra por **$17.528.132**, 592 repuestos, 2.269 unidades, la bujía con costo promedio **$45.855,75** | 41 s está bajo los 60 s del plan: la confirmación se queda síncrona, sin segundo plano. La respuesta de la pre-carga pesa 325 KB (31 KB comprimida): ver fase 5 |
| 2026-09-25 | 4 | Confirmar recibe cuántas reposiciones veía la pantalla; si ahora son otras, responde 409 **antes** de registrar nada | Es el "se avisa antes de terminar, no después" del §6: si alguien creó a mano uno de los códigos, ese renglón suma stock a un repuesto que ya existe, y quien confirma tiene que verlo |
| 2026-09-25 | 4 | Una carga ya confirmada, al confirmarla otra vez, devuelve lo que entró en vez de fallar | Es el reintento tras un corte de conexión (§6), o el segundo de dos equipos: el bloqueo de la carga lo pone a esperar al primero y la encuentra confirmada. La llave de la compra (el id de la carga) queda como segunda protección |
| 2026-09-25 | 4 | Los repuestos nuevos nacen con stock mínimo 0 | La factura no dice cuánto hay que tener; un mínimo inventado encendería el aviso de stock bajo en 600 repuestos a la vez |
| 2026-09-25 | 4 | Romper a propósito: saltarse la comprobación de problemas → falla `conProblemas`; llave al azar → fallan `exacta` y `dosALaVez`; llave al azar y sin bloqueo → las dos confirmaciones a la vez chocan (500) | Cada cambio hizo fallar su prueba |
| 2026-09-25 | 5 | Las pantallas quedan en `/compras/cargas` y `/compras/cargas/:id`, con una pestaña **Cargar factura** en Compras, y no en `/cargas` | Viven donde se piensa en compras, y heredan que Compras es del administrador sin tocar `utils/permisos.js` |
| 2026-09-25 | 5 | El servidor comprime sus respuestas (`server.compression`) | La pre-carga de la MAG477 son 325 KB de JSON y 31 KB comprimidos, y cada gesto la responde entera. Con datos en el celular es la diferencia entre guardar al instante y esperar |
| 2026-09-25 | 5 | Los gestos van en fila: el siguiente sale cuando llega la respuesta del anterior. Lo que se escribe se guarda al salir del campo o con Enter, no con cada tecla | Así la última respuesta que se pinta es la del último gesto, y lo que se está escribiendo no lo pisa una respuesta que llega a mitad |
| 2026-09-25 | 5 | Antes de subir, la pantalla revisa el tamaño y el tipo del archivo | Un archivo de más de 5 MB lo corta Tomcat, a veces cerrando la conexión sin responder: el mensaje tiene que salir antes |
| 2026-09-25 | 5 | RF-020: la columna de *Registrar compra* dice **Costo con IVA**, un aviso lo explica, y el botón *"La factura trae el IVA aparte: sumar 19%"* se lo suma a los costos escritos, con deshacer. No aparece al corregir una compra | Corregir una compra vieja no debe cambiarle la regla con la que se registró |
| 2026-09-25 | 5 | Capturas con Edge sin interfaz y respuestas simuladas (claro, oscuro, 390 px), sin tocar QA: ningún desborde de lado. El Vite de RD Motors se alcanza por `127.0.0.1:5174`: por `localhost` responde otro proyecto que escucha en el mismo puerto por IPv6 | El recorrido con el servidor de verdad se hace en la fase 6, contra el contenedor y un Postgres desechable |
| 2026-09-25 | 6 | Antes de subir: la suite completa tras `clean` (583 de dominio, 222 contra Postgres, 306 del frontend), la imagen construida, y **el recorrido completo en el navegador contra el contenedor** con la MAG477 real y un Postgres desechable: subir (20 s), filtrar los 176 sin marca, ponerles marca de una vez, ajustar la bujía a $68.500, la ganancia al 50%, recargar y ver que todo sigue, confirmar (47 s). Y `verificar-despliegue.mjs`: 18 de 18 | Es el checkpoint de la fase 5 hecho contra el servidor de verdad, no con respuestas simuladas |
| 2026-09-25 | 6 | El recorrido encontró un caso: un precio pegado y el campo dejado en el mismo instante no se guardaba, porque se comparaba contra lo último que React había pintado. Ahora se compara con lo que dice el campo al salir | Con el pulgar no pasa, pero pegar y tocar otro lado rápido sí podía |
