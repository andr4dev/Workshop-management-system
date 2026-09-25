# Cargar una factura entera — RD MOTORS

Para cuando llega una factura grande —la MAG477 de Jotapartes tiene 592 renglones— y teclearla renglón por renglón
en *Registrar compra* sería la noche entera. Se sube el archivo, se revisa una **pre-carga** y, al confirmar, todo
entra como **una sola compra**, igual que si se hubiera tecleado.

> **Nada entra al inventario hasta que confirmes.** Puedes subir, revisar, cerrar el navegador y seguir mañana desde
> otro equipo: la pre-carga queda guardada tal como la dejaste.

---

## 1. Subir el archivo

*Compras › **Cargar factura***.

| Qué archivo | Cuándo |
|---|---|
| **El PDF de Importadora Jotapartes**, tal como llega por correo | Siempre que la factura sea de Jotapartes. No hay que convertirlo a nada |
| **La plantilla** en Excel (`.xlsx`) o `.csv` | Otro proveedor, o mercancía que no viene de una factura. Se baja desde la misma pantalla: tiene las columnas *CODIGO, DESCRIPCION, CANTIDAD, VALOR TOTAL, MARCA, CATEGORIA* |

- Una factura de 30 páginas tarda unos segundos en leerse.
- Un PDF de **otro proveedor** no se lee: el sistema solo conoce el diseño de Jotapartes y no adivina columnas. Se
  pasa a la plantilla.
- Un PDF **escaneado** (una foto) tampoco: no trae texto que leer.
- La **misma factura dos veces** no se deja: el sistema dice que ya existe y ofrece abrirla. Si se descartó, o si la
  compra que dejó se anuló, sí se puede volver a subir.

---

## 2. Revisar la pre-carga

### Lo primero: ¿cuadra?

Arriba a la derecha, el recuadro **¿Cuadra con la factura?** compara la suma de lo leído con el sub-total impreso
abajo en la factura. En verde, no falta ni sobra nada. En ámbar dice cuánto falta o sobra, y **no deja confirmar**:
se perdió, se repitió o se leyó mal un renglón.

Con la plantilla de Excel no hay sub-total impreso que leer: se escribe a mano, mirando la factura en papel.

### El precio sugerido

**Costo por unidad = valor total del renglón ÷ cantidad, más el IVA.** Sobre eso, la ganancia, y se redondea hacia
arriba a los $100. Con la bujía NGK de la MAG477:

> 8 unidades por $308.274 → **$45.856 cada una con IVA** → + 45% → **$66.500**.

- El IVA (19%), la ganancia (45%) y el redondeo se cambian arriba, y mueven **solo los precios que no has tocado**.
- Cualquier precio se cambia a mano en su renglón. Queda marcado como *ajustado a mano* y ya no se mueve solo.
  *Volver al sugerido* lo devuelve.
- Cada renglón dice dos porcentajes con nombre distinto: **"le ganas 45% a lo que pagaste"** (lo que tú decides) y
  **"31% del precio es ganancia"** (el margen que el sistema ya mostraba en otras pantallas). Son la misma plata
  vista desde lados distintos; no es un error.
- Vender por debajo del costo se avisa en ámbar y **no se impide**: a veces es a propósito.

### Lo que hay que completar

Los renglones en rojo tienen algo que arreglar, y el botón de confirmar dice qué falta. Lo más común:

| Qué dice | Qué hacer |
|---|---|
| *Falta la marca* | En la MAG477, 176 renglones no traen marca en la descripción. Escríbela en el renglón, o arriba en **Marca › A todos los que no tienen** |
| *Falta la categoría* | Igual: en el renglón, o **Categoría › A todos los que no tienen** |
| *8 × $46.993 − 18% da $308.274, pero la factura dice $380.274* | Se leyó mal un número. **Corregir lo leído** y escribirlo mirando el papel |
| *Este código está dos veces* | No se suman solos: quita el que sobra |

La marca y la categoría que **propuso el sistema** leyendo la descripción llevan el sello *propuesta*. Revísalas:
*Está bien* les quita el sello. El filtro **Propuestos** muestra solo esas.

### Lo que ya existía

Un código que ya está en el inventario sale como **Ya existe · stock 4 · $12.000**. Al confirmar **suma stock** a
ese repuesto y recalcula su costo, sin crear otro. **Conserva su precio** salvo que marques *Cambiarle el precio al
que ya existe*.

### Quitar un renglón

*Quitar de la carga* lo deja por fuera de la compra (algo que no llegó, por ejemplo). Su valor **sigue contando**
para el cuadre con la factura: quitar es decidir que no entra, no arreglar una lectura.

---

## 3. Confirmar

Arriba, los datos de la compra: **proveedor** (si el NIT de la factura está en un proveedor, ya viene elegido),
número, fecha y **forma de pago** —que nunca viene marcada por omisión—.

**Confirmar** muestra un resumen antes del último clic: cuántos repuestos, cuántas unidades, y el total, que es
**lo que de verdad se pagó, con IVA** ($17.528.132 en la MAG477). Con una factura grande tarda casi un minuto.

- Entra **todo o nada**: si algo falla, no queda nada a medias.
- Confirmar dos veces —un doble clic, dos equipos a la vez, reintentar tras un corte— deja **una sola compra**.
- Si mientras revisabas alguien creó a mano uno de esos códigos, el sistema **avisa antes de registrar** y pide
  revisar de nuevo.
- Después es una compra como cualquier otra: en *Compras › Historial* se corrige o se anula.

---

## 4. La misma regla en las compras a mano

Desde esta funcionalidad, **el costo de una compra se escribe con el IVA incluido**, también en *Registrar compra*.
El IVA que se le paga al proveedor se recupera cobrándolo en el precio de venta, así que es costo. Si una factura
trae el IVA aparte, el botón **"La factura trae el IVA aparte: sumar 19%"** se lo suma a los costos escritos (y se
puede deshacer).

Si algún día el contador dice que RD Motors sí recupera el IVA por otro lado, es poner el IVA en 0% en la
pre-carga: un número, no rehacer nada.
