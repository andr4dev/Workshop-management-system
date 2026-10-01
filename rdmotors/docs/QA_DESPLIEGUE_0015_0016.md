# QA y despliegue · aceite (0015) y fiado por producto (0016)

Rama `spec-0015-comisiones`, sin desplegar. Verificado el 2026-10-01 sobre **una copia de producción** con **la
misma imagen de Docker que construye Render**.

## 1. Lo que ya se verificó

| Qué | Resultado |
|---|---|
| La imagen de Docker (el `Dockerfile` de Render) | Se construye sin errores |
| Arranque con los límites de Render gratis (0,1 CPU, 512 MB) | **158 s**; las migraciones V30 y V31, **0,33 s**; 275 MB de memoria |
| Lo que muestra la app, antes y después de migrar (61 vistas: reportes de cada día y de cada mes, las 22 ventas, el turno, la cartera, cada ficha y cada recibo, los gastos) | **Iguales en cada peso.** Lo único distinto: los recibos nombran el producto (*"MOTUL 7100 10W30 (venta N.º 2)"* en vez de *"la venta N.º 2"*) y aparece la categoría *Comisión cambio de aceite* |
| La base, tabla por tabla (29 tablas, 5.584 filas, con una huella de cada una) | 25 idénticas. `deuda`: solo la venta N.º 18 se parte en dos productos (GUAYA $26.120 + MOTUL $62.880, que suman sus $89.000). `categoria_gasto`: la categoría nueva. `entrada` y `usuario`: los inicios de sesión de la prueba |
| Un día entero con la versión nueva sobre los datos reales (25 controles) | **Todo cuadra**: comisiones, aceite sin cambio, fiado pagando un producto, abono por producto, abono desde una pantalla vieja, anular (de hoy y de antes), el cajón, la cartera, los reportes y el cierre del turno sin diferencia |
| Volver a la versión anterior **sin** restaurar la base | Arranca, pero el detalle de la venta N.º 18 da **error 500** → si hubiera que volver, se restaura la copia de la base (paso 2 del despliegue) |
| Suite completa | 622 de lógica y 233 del servidor, sin fallas; 327 de pantalla |

**Encontrado de paso, de antes de esto (no lo cambia este despliegue):** en *Reportes*, un día con un abono a una venta
que tuvo descuento muestra *"Descuentos $1.011 · No se dieron descuentos"*. Es la parte del descuento que le toca a ese
abono (spec 0014); el texto se contradice. Se puede arreglar aparte.

## 2. Qué cambia para quien usa la app

- **Vender**: cada aceite marcado pregunta *¿Se cambia aquí?* al agregarlo (en el catálogo, en su misma fila). *Sí* →
  se escoge quién lo cambió y al cobrar salen $3.000 del cajón a su nombre. *No* → el precio baja $3.000. Sin escoger
  no se cobra, y debajo de *Cobrar* dice qué falta. En el catálogo, tocar la fila ya no agrega: se agrega con **+**.
- **Cobrar**: arriba del total se ve el pedido renglón por renglón. En *Fiado*, se marca *qué paga ahora*.
- **Cartera › ficha**: cada venta fiada muestra sus productos, cuál está pagado, abonado o pendiente.
- **Abonar**: en vez de *"A cuál se aplica"*, las ventas con sus productos y una casilla en cada uno.
- **Caja y Reportes**: las comisiones son gastos de costo *"venta N.º 23 · MOTUL 7100 10W30 · Deibis"*; restan de la
  utilidad bruta.
- **Lo de antes no cambia de valor**: ninguna venta vieja paga comisión; los fiados viejos se ven por producto con las
  mismas cifras.
- **Compras › Repuesto nuevo**: la casilla *"Es un aceite que paga comisión por cambio"* con su monto. **Inventario**:
  cada aceite que paga dice *"Cambio $3.000"*, y el filtro *Pagan comisión por cambio* los lista.
- **En el celular** (toda la app): cada renglón de una tabla es una tarjeta con sus datos a la vista (antes el monto, el
  total o el precio quedaban escondidos a la derecha); las cifras grandes van de a dos; los filtros y las pestañas caben.
  En el computador nada cambia.

## 3. QA manual en local (antes de desplegar)

Pantalla: `http://127.0.0.1:5174` (Ctrl+F5) · usuario `ruben` / `rdmotors2026` · servidor local en el 8081.
Marque cada casilla; si algo no da lo esperado, anótelo con la hora.

### A. El aceite
- [ ] **Inventario** › abrir *MOTUL 7100* › *Comisión por cambio* › *Cambiar*: $3.000. Abrir el *lubricante de cadena*:
      que **no** esté marcado.
- [ ] **Vender** › *Catálogo* (F2) › tocar la **fila** del MOTUL: solo se marca, no se agrega.
- [ ] Tocar **+** del MOTUL: sale *¿Se cambia aquí?*. Tocar *Carolina Ruiz* → entra a $65.000.
- [ ] Otro **+** del mismo MOTUL: no pregunta y el mensaje dice *"Otra unidad, igual que la que ya estaba…"*.
- [ ] Agregar otro aceite y escoger *No se cambia*: entra con $3.000 menos.
- [ ] Agregar un aceite **desde el buscador** (Enter) sin escoger, abrir el catálogo: bajo *Cobrar* sale *⚠ Escoge si el
      … se cambia aquí* y *Ver en la venta*.
- [ ] Cobrar en efectivo. En **Caja**: el esperado bajó $3.000 por el aceite cambiado; en *Lo que salió del cajón* está
      *"venta N.º … · MOTUL 7100 10W30 · Carolina Ruiz"*.
- [ ] **Ventas del turno** › anular esa venta: la comisión aparece anulada y el esperado vuelve.
- [ ] **Reportes** › *Hoy*: *costos adicionales* = las comisiones vigentes; *Ver cálculo* las lista con su enlace.
- [ ] **Reportes › Gastos** › categorías: *Comisión cambio de aceite* no se deja renombrar ni desactivar (sale el aviso).

### B. Cobrar
- [ ] Con dos o tres productos y un descuento, abrir *Cobrar*: el pedido arriba suma el total; el cursor sigue en
      *Con cuánto paga*.

### C. Fiado por producto
- [ ] Venta de **MOTUL + filtro**, *Cobrar* › *Fiado* › escoger cliente › marcar **el filtro** en *Qué paga ahora*:
      *Paga ahora* = precio del filtro; el botón dice *Cobrar $… y fiar $65.000*. Cobrar.
- [ ] **Cartera** › ese cliente: la venta muestra *MOTUL · Pendiente · Debe $65.000* y *filtro · Pagado al llevárselo*.
- [ ] Otra venta fiada de dos productos **sin pagar nada**: la ficha muestra los dos pendientes.
- [ ] **Abonar** › marcar solo el **segundo** producto de esa venta: el monto se llena solo; recibir. El recibo dice
      *"filtro… (venta N.º …)"* y la ficha lo muestra *Pagado*.
- [ ] **Abonar** sin marcar nada, $10.000: va a lo más viejo, al primer producto de la venta más vieja.
- [ ] Anular ese abono (administrador): lo pagado vuelve a deberse.
- [ ] Anular la venta fiada de dos productos: los dos quedan anulados; lo que tenían abonado pasa a la otra venta o queda
      a favor.
- [ ] **Cartera** (la lista): el cliente dice cuántas **ventas** debe, no cuántos productos.

### D. Repuesto nuevo con comisión
- [ ] **Compras › Registrar** › un código nuevo › *Repuesto nuevo*: marcar *"Es un aceite que paga comisión por cambio"*
      ($3.000). Registrar la compra. En **Inventario** sale con *"Cambio $3.000"* y aparece en el filtro *Pagan comisión
      por cambio*; al venderlo pregunta *¿Se cambia aquí?*.

### E. En el celular
- [ ] Repetir *A* (agregar un aceite con **+** y escoger quién) y *C* (abonar marcando un producto) en el celular, en
      modo oscuro: todo se lee y se toca con el dedo.
- [ ] **Caja**: en *Lo que salió del cajón* se ve el monto de cada gasto. **Ventas del turno**: el total de cada venta.
      **Inventario**: el stock y el precio de cada repuesto. Nada se desliza de lado.
- [ ] **Ajustes** (Datos de la tienda, Usuarios…): las pestañas se deslizan y la página no se sale de la pantalla.

## 4. Desplegar (solo con la orden del dueño)

1. **Fuera del horario de la tienda.** Avisar que la app puede no responder **unos 3 minutos**.
2. **Bajar una copia de la base** de producción (*Ajustes › Respaldo*, o `pg_dump` en solo lectura) y guardarla: es lo
   que permite volver atrás (ver la sección 1: sin ella, volver da error 500).
3. Confirmar en solo lectura que producción está en la V29 y que `/api/salud` responde.
4. Subir la rama **encima de `origin/main`** (avance directo). El revert local de `main` (`6a90baf`) **no se sube**.
5. Esperar a que Render termine (construye y arranca, ~5 a 8 minutos en total) y comprobar `/api/salud` y que Flyway quedó
   en la **V31**.
6. **Que todos recarguen la app (Ctrl+F5)**, en cada equipo y celular. Una pantalla vieja no deja cobrar un aceite ya
   marcado.
7. Marcar los aceites de motor con su comisión (12 en producción; el dueño confirma los montos). El lubricante de cadena
   y el Mercury, no.

## 5. QA en producción, después de desplegar (10 minutos, sin inventar ventas)

- [ ] Entrar con cada usuario (Ruben, Deibis, Gustavo).
- [ ] **Reportes** › *Este mes* y *Ayer*: las mismas cifras que antes de desplegar (tomar foto antes, en el paso 3).
- [ ] **Cartera** › *Julio Motors*: la venta N.º 18 en dos productos (GUAYA $26.120 + MOTUL $62.880) y lo que debe, igual
      que antes.
- [ ] **Ventas del turno**: abre, y la venta N.º 18 abre su detalle.
- [ ] Un recibo viejo (*Cartera › Abonos › Reimprimir*): mismas cifras, ahora con el nombre del producto.
- [ ] **La primera venta real de aceite** del día: que pregunte *¿Se cambia aquí?* y, si se cambió, que la comisión
      aparezca en Caja con el nombre de quien lo cambió.

## 6. Si algo sale mal

- **Lo primero es arreglar hacia adelante**: cualquier error de pantalla o de servidor se corrige y se despliega otra vez.
- **Volver atrás** solo si la tienda no puede vender: desplegar el commit anterior (`abab464`) **y** restaurar la copia
  del paso 2. Lo que se haya vendido entre el despliegue y la restauración se pierde y hay que volver a registrarlo.
