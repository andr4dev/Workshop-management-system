# Spec 0014 · Lo fiado cuenta en los reportes cuando se cobra, los gastos del mes se escogen uno por uno, y *Ver cálculo* dice de dónde sale

**Estado:** implementado el 2026-09-28 (decisiones 1 a 5 tomadas por el usuario) · [plan](plan.md) · pendiente la P3

**Pedido del usuario (2026-09-28):** *«560.000 en ventas incluye los 134.000 fiados de reportes? No debería: en
reportes solo entra hasta que la plata se cobre, así el repuesto haya salido.»* Y: *«la idea es que cuando le dé
ver cálculo me salga el enlace a ver qué fue el gasto, etc.»*

**Al revisarlo:** *«lo fiado no entra en reportes de lo cobrado ya que es un error, pero sí debe mostrar en reportes
cuánto hay fiado»*. Y los gastos del mes: *«cliente escoge del mes: se le pregunta: repartir día a día, registrarlo
en un día… algo así como está el car‑wash»* (decisión 5).

---

## 1. Objetivo de negocio

El dueño lee *Resultados* como **"cuánta plata me entró y cuánto me ganó"**. Hoy una venta fiada cuenta completa el
día que se vende (spec 0008, decisión tomada entonces), así que el reporte le muestra ventas y utilidad que todavía
no están en la caja ni en la cuenta. Con $134.000 por cobrar, el mes dice $560.850 de ventas cuando lo que entró es
$426.850.

Y cuando una cifra no le cuadra —los $46.668 de gastos de hoy— no tiene cómo ver **qué gasto** la forma sin irse a
otra pantalla y adivinar filtros.

## 2. Historias

| | Historia | Se demuestra sola cuando… |
|---|---|---|
| **P1** | Como dueño, en *Resultados* **las ventas y la utilidad son solo de lo cobrado**: lo de contado el día de la venta, y lo fiado a medida que lo abonan. | El mes de hoy dice ventas netas **$426.850**, no $560.850, y al abonar Julio Motors sus $134.000 el reporte de ese día sube en $134.000. |
| **P1** | Como dueño, lo fiado que falta **se sigue viendo**, aparte, para no olvidarlo. | La tarjeta de la cartera sigue diciendo *Vendido fiado*, *Cobrado en abonos* y *Por cobrar hoy*. |
| **P2** | Como dueño, en *Ver cálculo* cada categoría de gasto y de costo **se abre en sus gastos**, cada uno con enlace. | En *Gastos del local*, *Nómina* muestra el pago de Gustavo, y tocarlo lleva a ese gasto. |
| **P3** | Como dueño, en *Ver cálculo* de las ventas, un enlace a **las ventas del período** y a **los abonos** que las forman. | Tocarlo lleva a la lista con esas mismas ventas. |
| **P1** | Como dueño, al registrar un gasto escojo **del día** o **del mes**, y si es del mes, **repartirlo día a día** o **registrarlo en un día**: entero, solo en el reporte del mes. | Un arriendo "del mes, en un día" no aparece en el reporte del 5 de octubre y sí, entero, en el de octubre. |

## 3. Qué existe hoy

| Qué | Dónde | Hecho verificado |
|---|---|---|
| Lo fiado es venta el día que se vende | `domain/…/reportes/dominio/ResultadosDelPeriodo.java:34` | *"baja el stock y deja su costo ese día, así que su ganancia también es de ese día. Lo que después se abona es un cobro, no otra venta"* |
| La igualdad de los pagos | `ResultadosDelPeriodo.java:26` y `frontend/src/utils/resultados.js:84` | *efectivo + transferencia + fiado = ventas netas* |
| Las ventas del reporte se leen por el día en que se vendieron | `pos/…/reportes/infraestructura/RepositorioReportesJdbc.java:45-58` | Por `cobrada_en`, con su `fiado` completo |
| Los renglones (y su costo del kardex) también | `RepositorioReportesJdbc.java:72-85` | Mismo filtro: el costo cae el día de la venta |
| Lo abonado y lo por cobrar | `RepositorioReportesJdbc.java:157` y `reportes/dominio/CarteraDelPeriodo.java:8-10` | Aparte de las cifras: *"lo vendido y lo cobrado no se mezclan"* |
| Qué parte de cada abono pagó qué venta | `pos/…/db/migration/V20__clientes_y_fiado.sql:125-134` | `aplicacion_abono`: abono → deuda → venta, con monto. Al anular una venta fiada, lo aplicado se marca y se vuelve a aplicar en una fila nueva |
| La tarjeta de la cartera | `frontend/src/paginas/Resultados.jsx:184-191` | *Vendido fiado*, *Cobrado en abonos*, *Por cobrar hoy* |
| La ayuda de la pantalla | `frontend/src/utils/ayudaReportes.js:44-45` | *"Lo fiado es venta el día que se vende, aunque se pague después"* |
| *Ver cálculo* baja hasta la categoría, no hasta el gasto | `resultados.js:18` y `componentes/reportes/PanelCalculo.jsx:50` | Cada hijo es `{categoría, monto}`: sin gastos ni enlaces |
| Los gastos del mes se reparten por día | `ResultadosDelPeriodo.java:241-255` | Una cuota igual por día del mes: $50.000 del mes, del 1 al 28, son $46.668 |
| *Gastos* filtra por fechas y categoría, desde la dirección | `frontend/src/paginas/Gastos.jsx:31`, `:135-147` | Pero **no hay dirección de un gasto solo** (`App.jsx:161`) |
| El cierre de caja cuenta lo fiado como producido del turno | `domain/…/correo/aplicacion/CorreoDelCierre.java:23` | *"cuánto se vendió, fiado incluido"*: es lo que salió del mostrador en ese turno |

**Las cifras de producción el 2026-09-28** (leídas, sin tocar nada):

| | 1 al 28 de septiembre | Solo el 28 |
|---|---|---|
| Ventas netas hoy | $560.850 | $495.850 |
| · de contado (efectivo) | $215.850 | $215.850 |
| · fiado | $345.000 | $280.000 |
| Abonado de lo fiado | $211.000 (146.000 efectivo + 65.000 transferencia) | $146.000 |
| Por cobrar | $134.000 (Julio Motors: venta 9 debe $44.000, venta 10 $90.000) | — |
| **Ventas netas con este spec** | **$426.850** | **$361.850** |

## 4. Las decisiones

### Decisión 1 · El costo de lo fiado, ¿cuándo cuenta?

Si solo se mueve la venta al día del abono y el costo se queda el día de la venta, el día que se fía mucho sale
**con pérdida** y el día que abonan sale con ganancia sin costo. Ninguno de los dos días dice la verdad.

| Opción | Qué pasa |
|---|---|
| **A. Proporcional a lo cobrado** | Cada peso cobrado de una venta trae su parte del costo. Venta 9: $55.000, abonados $11.000 → entra el 20 % de la venta, el 20 % de su costo y el 20 % de cada renglón. |
| B. La venta entera, el día que se termina de pagar | Simple, pero una venta pagada al 90 % no aparece en ningún reporte. |
| C. La venta al cobrarse, el costo al venderse | Los días de fiado dan pérdida; los de abono, ganancia pura. |

**Recomendación: A.** Es la única en que *utilidad = lo que entró − lo que costó eso que entró*. Las partes se
redondean como el IVA de las cargas (el residuo mayor): el 20 % de cada renglón suma, al peso, el 20 % de la venta.

> ✅ **Decidido el 2026-09-28: A**, el costo entra junto con lo cobrado.

### Decisión 2 · Lo cobrado, ¿qué día cuenta?

**Recomendación: el más tardío entre el día de la venta y el día del abono** (tomada junto con la decisión 1). Así:

- un abono de hoy a una venta de ayer cuenta **hoy**;
- lo que el cliente tenía **a favor** y se aplica a una venta nueva cuenta el día **de esa venta**;
- si se anula una venta fiada y su abono pasa a otra venta (como pasó el 28 con la venta 6), cuenta el día en que
  entró la plata, no el de la anulación.

Consecuencia que hay que aceptar: **anular una venta fiada puede cambiar un día que ya pasó**, porque su abono pasa
a pagar otra venta. Ya pasa hoy con las anulaciones, y el reporte se recalcula siempre desde los datos.

### Decisión 3 · Lo que se cobra del saldo del cuaderno

El saldo del cuaderno (spec 0008) es deuda de antes del sistema: no tiene renglones ni costo.

**Recomendación: no es venta.** Se ve en la tarjeta de la cartera como *Cobrado del cuaderno* y queda fuera de
ventas y utilidad, porque no hay con qué calcular su ganancia.

> ✅ **Decidido el 2026-09-28: aparte**, como se recomendó.

### Decisión 4 · Cuántas ventas y cuántas unidades

Son conteos de **lo que salió del mostrador**, no de plata.

**Recomendación: se cuentan el día que se venden**, fiadas o no, y se rotulan *Ventas hechas* y *Unidades que
salieron*.

> ✅ **Decidido el 2026-09-28, distinto de lo recomendado: una venta fiada cuenta cuando se paga completa.** Una
> venta de contado cuenta el día que se vende; una fiada, el día en que entra el último peso (con la regla de la
> decisión 2). Sus unidades y sus renglones cuentan ese mismo día. Mientras no se termine de pagar, su plata cobrada
> sí está en las ventas netas (decisión 1), pero **la venta no se cuenta**.
>
> Consecuencia: el **ticket promedio** ya no puede ser *ventas netas ÷ ventas*, porque las ventas netas traen pedazos
> de ventas que todavía no se cuentan. Pasa a ser *el total de las ventas que se completaron ÷ cuántas son*.

### Decisión 5 · Los gastos del mes: ¿repartidos o enteros? Lo decide cada gasto

**Hoy** (verificado): cada gasto dice si es *del mes* (`pos/…/db/migration/V15__gastos_del_mes.sql:12`), y **el
reporte** decide para todos a la vez con el selector *Repartidos / Solo en el mes*
(`frontend/src/componentes/reportes/SelectorPeriodo.jsx:69-74`, `ResultadosDelPeriodo.java:241-265`). El 2026-09-28
se agregó en cada gasto la opción *"Repartido día a día / Todo el [día]"* (commit c8067ef), que no es lo que el dueño
quería.

> ✅ **Decidido el 2026-09-28 por el usuario, a la manera del car‑wash:**
>
> - Un gasto es **del día** o **del mes**.
> - **Del día:** entero en su fecha, en el reporte del día, de la semana y del mes.
> - **Del mes:** se pregunta, **sin respuesta de entrada**, *¿repartirlo día a día o registrarlo en un día?*
>   - **Repartir día a día:** cada día de su mes carga una parte igual (lo de hoy con *Repartidos*).
>   - **Registrarlo en un día:** **entero, solo en el reporte que cubre su mes**. En el reporte de un día o de una
>     semana no aparece, y el reporte dice cuánto quedó fuera (lo de hoy con *Solo en el mes*).
> - **El selector del reporte se quita:** lo decide cada gasto.
> - Los gastos del mes que ya existen quedan **repartidos**, que es como se veían (el selector arrancaba en
>   *Repartidos*).
> - La categoría sigue dando la respuesta de entrada a *del día / del mes*; a *repartir o no*, nunca.

## 5. Requisitos funcionales

**Lo cobrado (P1)**

- **RF-001** · En *Resultados*, ventas netas, costo vendido, utilidad bruta y utilidad operativa son **de lo cobrado**:
  lo pagado de contado cuenta el día de la venta; lo fiado, en la parte que se abona y el día que dice la decisión 2.
- **RF-002** · Cada peso cobrado de una venta fiada trae su parte **proporcional** de los renglones, del descuento y
  del costo (decisión 1). Las partes suman, al peso, lo cobrado de esa venta.
- **RF-003** · La igualdad de los pagos pasa a ser *efectivo + transferencia de contado + abonos aplicados = ventas
  netas*, con su prueba y su aviso en rojo si no cuadra.
- **RF-004** · El día por día, los repuestos más vendidos y las categorías de repuesto usan la misma regla: cada
  fila y cada tabla suman las cifras de arriba.
- **RF-005** · La tarjeta de la cartera sigue: *Vendido fiado* (del período, informativo), *Cobrado en abonos* y
  *Por cobrar hoy*. Suma *Cobrado del cuaderno* si la decisión 3 queda como se recomienda.
- **RF-006** · Una venta fiada **anulada** no suma nada; sus abonos cuentan donde queden aplicados.
- **RF-007** · La ayuda de la pantalla dice la regla nueva: *"Lo fiado cuenta cuando se cobra, y en la parte que se
  cobra"*.

**Los gastos del mes (P1, decisión 5)**

- **RF-012** · Al registrar un gasto se escoge **del día** o **del mes**. Si es del mes, se escoge **repartir día a
  día** o **registrarlo en un día**, sin nada marcado de entrada: sin escoger no se registra.
- **RF-013** · Un gasto del mes *en un día* cuenta **entero** en el reporte cuyo período cubre su mes, en una fila
  aparte del día por día; en uno que no lo cubre no suma, y el reporte dice cuánto quedó fuera.
- **RF-014** · Un gasto del mes *repartido* carga una parte igual en cada día de su mes, al peso.
- **RF-015** · *Reportes › Gastos* filtra **Todos / Del día / Del mes**, y cada gasto del mes dice si va repartido
  o entero.
- **RF-016** · El selector *Gastos del mes* de *Resultados* se quita. Los gastos del mes que ya existen quedan
  repartidos.

**Ver cálculo (P2 y P3)**

- **RF-008** · En *Ver cálculo*, cada categoría de gasto y de costo **se abre en sus gastos**: fecha, descripción y
  monto. Un gasto del mes dice cuánto de él entra: *"28 de 30 días de $50.000"*.
- **RF-009** · Cada gasto tiene un enlace que lleva **a ese gasto** en *Reportes › Gastos*. Hace falta una dirección
  para un gasto solo, que hoy no existe.
- **RF-010** · Un costo que viene de una compra (el envío de la WE-10238) enlaza también a la compra. Con el
  spec 0013 construido, el enlace sale del gasto; mientras tanto, de la referencia en su descripción.
- **RF-011** (P3) · *Ventas netas* en *Ver cálculo* enlaza a las ventas del período y a los abonos que las forman.
  [sin verificar: si la lista de ventas acepta un rango de fechas en la dirección]

## 6. Manejo de errores

- Un abono cuya aplicación apunta a una deuda de venta sin renglones → no puede pasar (la base lo impide); si
  aparece, cuenta como *sin costo* y lo dice, como un renglón sin costo.
- Las partes proporcionales no suman lo cobrado → el reporte lo dice en rojo; no se corrige en pantalla (regla del
  spec 0007).
- Un gasto del enlace que ya se anuló → la pantalla de gastos lo muestra anulado, con quién y por qué.

## 7. No funcionales

- **Plata:** todo en pesos enteros; las partes suman el total al peso (residuo mayor). Cada igualdad, con su prueba.
- **Roles:** *Resultados* sigue siendo del administrador; el cajero no ve costos.
- **Rendimiento:** una consulta más (las aplicaciones de abonos del período), no una por venta.
- **Esquema:** lo cobrado sale de tablas que ya existen. Los gastos del mes necesitan **una columna nueva** en el
  gasto (si se reparte), con los que ya existen marcados como repartidos.

## 8. Criterios de aceptación

- [ ] Con los datos del 28 de septiembre, el mes dice ventas netas **$426.850** y el 28 **$361.850**.
- [ ] Al registrar un abono de $44.000 a la venta 9, el reporte del día del abono sube en $44.000 y su costo en la
      parte proporcional; el del día de la venta no cambia.
- [ ] *Efectivo + transferencia + abonos = ventas netas* cuadra al peso, en el período y en cada fila.
- [ ] Una venta fiada anulada no suma; su abono cuenta en la venta a la que pasó.
- [ ] En *Ver cálculo* › *Gastos del local* › *Nómina* aparece el pago de Gustavo, y su enlace abre ese gasto.
- [ ] El envío de la WE-10238 en *Costos adicionales* enlaza al gasto y a la compra.
- [ ] Una venta fiada a medio pagar suma su parte cobrada a las ventas netas, pero no cuenta en *ventas*; el día que
      entra el último peso, cuenta como una venta con todas sus unidades.
- [ ] Un gasto del mes *en un día* de $800.000 no aparece en el reporte de un día y sí, entero, en el del mes.
- [ ] Un gasto del mes *repartido* de $50.000 en septiembre carga $1.666 o $1.667 cada día y suma $50.000 en el mes.
- [ ] No se puede registrar un gasto del mes sin escoger si se reparte.

## 9. Qué no se toca

- **El cierre de caja y su correo:** el producido del turno sigue contando lo fiado, porque dice qué salió del
  mostrador en ese turno; y el esperado del cajón sigue siendo solo efectivo.
- **La Cartera** y la ficha del cliente.
- **El inventario:** el repuesto sale el día que se vende, fiado o no.

## Fuera de alcance

- Un reporte "de lo vendido" al lado del "de lo cobrado". Si el dueño lo pide, es otra vista del mismo cálculo.
- Exportar el detalle de *Ver cálculo*.
