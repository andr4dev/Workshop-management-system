# Spec 0015 · La comisión por cambio de aceite

**Estado:** versión 2, 2026-09-29: el dueño cambió las decisiones 3 a 6 después del plan (ver *Cambios de la
versión 2*) · [plan](plan.md) · en implementación, **solo en local** (rama `spec-0015-comisiones`)

**Pedido del usuario (2026-09-29):** *«cada venta de aceite son 3000 pesos que se deben sacar aparte para el que
cambió el aceite, es decir el aceite cuesta 64000, de esos 64000 salen 3000 para el que cambió el aceite o atendió,
y debe quedar el registro»*.

**Cambios de la versión 2** (pedidos el mismo día, al verla): *«hay que agregar el caso en que el cliente desea no
cambiarlo, entonces se le hace el descuento a 62.000 […] siempre que el cajero escoja […]; si el cliente escoge
cambiarlo, poder seleccionar a quién se le acumulan esos 3000, y que automáticamente se registre que se le va a pagar,
no que toque pagarlos y dar un paso adicional; que se vea en el reporte; hay que generar menos fricción»*. Y al
preguntarle: **el cajero escoge siempre** (no hay respuesta de entrada) y **los $3.000 salen del cajón al cobrar**.

---

## 1. Objetivo de negocio

El aceite se vende **con el cambio incluido**: $65.000 el MOTUL 7100, de los que $3.000 son de quien lo cambia. Si el
cliente se lo lleva sin cambiar, paga $62.000. Hoy eso se hace de memoria: nadie sabe cuánto se le pagó a quién, ni
cuánto de la ganancia del aceite se va en eso.

El dueño quiere que **cada cambio quede registrado a nombre de la persona**, que la plata **salga del cajón sola** al
cobrar (sin un paso de "pagar" aparte) y que **se vea en los reportes**. Sin fricción: un toque por aceite.

## 2. Historias

| | Historia | Se demuestra sola cuando… |
|---|---|---|
| **P1** | Como cajero, al agregar un aceite a la venta escojo **se cambia aquí** o **no se cambia**; si no, el precio baja $3.000. | Un MOTUL 7100 *no se cambia* entra a $62.000; *se cambia*, a $65.000. Sin escoger no deja cobrar. |
| **P1** | Como cajero, si se cambia, escojo **quién lo cambió** (de entrada, yo), y al cobrar los $3.000 **salen del cajón a su nombre**, solos. | Se cobra con *se cambia · Gustavo*: el cierre espera $3.000 menos, y en Gastos aparece *"venta N.º 12 · MOTUL 7100 · Gustavo"*. |
| **P1** | Como dueño, los reportes restan esas comisiones de la ganancia y dicen de quién es cada una. | La utilidad bruta del día baja $3.000 por cambio; *Ver cálculo* lista cada comisión con su venta y la persona. |
| **P2** | Como dueño, escojo qué repuestos pagan comisión y cuánto. | *Hecho en la fase 1*: el KIXX 10W40 se marca con $3.000; el lubricante de cadena no. |
| **P2** | Como dueño, veo cuánto se le pagó a cada uno en el período. | En *Reportes › Gastos* filtrando la categoría, cada comisión con la persona, y el total. |

## 3. Qué existe hoy

| Qué | Dónde | Hecho verificado |
|---|---|---|
| La marca por repuesto (fase 1) | rama `spec-0015-comisiones`: `inventario/dominio/Variante.java` (`comisionCambio`), V29 | Hecha y probada; en producción solo la columna, vacía |
| La venta guarda **quién la registró**, no quién hizo el trabajo | `domain/…/ventas/dominio/Venta.java:66` | Un solo "quién" por venta |
| Cada renglón tiene su precio unitario y su posición | `domain/…/ventas/dominio/LineaVenta.java:36-47` | El precio sale de la variante al cobrar |
| El cobro no deja cobrar con un precio distinto del que vio la pantalla | `ComandoCobrarVenta.Renglon.precioVisto` (`…/ventas/aplicacion/ComandoCobrarVenta.java:67`) | *"no se cobra"* si cambió |
| Un gasto del cajón resta de lo que debería haber al cerrar | `domain/…/caja/dominio/ArqueoDeTurno.java:22, 98-99` | Y se ve en *Lo que salió del cajón* |
| Un gasto de naturaleza **costo** resta de la utilidad bruta, y *Ver cálculo* lo lista con su enlace | spec 0014 (fase 4) | El envío de la WE-10238 ya sale así |
| Un gasto del cajón solo se anula mientras su turno siga abierto | el caso de uso de anular gasto | La plata ya salió del cajón de ese turno |

## 4. Las decisiones

### Decisión 1 · ¿Qué es "un aceite"? — hecha

Una marca en cada repuesto con su monto (*"paga comisión por cambio: $3.000"*), que pone el administrador en la
ficha. Por categoría no sirve: los aceites están en dos, y con uno de ellos está el lubricante de cadena.

### Decisión 2 · Por renglón, no por unidad suelta

Cada renglón de aceite se escoge entero: *se cambia* (todas sus unidades) o *no se cambia*. Dos aceites del mismo
repuesto, uno cambiado y otro no, en la misma venta: se cobran en dos ventas. [Es raro; si pasa seguido, se parte el
renglón en una versión futura.]

### Decisión 3 · ¿Se cambia aquí? — el cajero escoge siempre

> ✅ **Decidido por el dueño:** sin respuesta de entrada. Un renglón de aceite sin escoger no deja cobrar.

- **Se cambia aquí:** el precio es el del repuesto ($65.000) y hay que decir quién lo cambió.
- **No se cambia:** el precio baja la comisión: $65.000 − $3.000 = **$62.000** por unidad.

### Decisión 4 · ¿A quién le toca?

Al escoger *se cambia*, **quién** viene con el que registra la venta, y se cambia con un toque a otra persona de la
tienda (los usuarios activos: hoy Ruben, Deibis y Gustavo).

### Decisión 5 · ¿Cuándo se paga? — al cobrar, del cajón

> ✅ **Decidido por el dueño:** los $3.000 salen del cajón **al cobrar**, solos. No hay un paso de "pagar".

Al cobrar, cada renglón que *se cambia* registra **un gasto del cajón** en la categoría del sistema *"Comisión cambio
de aceite"*, por su comisión × cantidad, con la descripción *"venta N.º 12 · MOTUL 7100 10W30 · Gustavo"* y enlazado a
su renglón. El cierre ya descuenta los gastos del cajón: el efectivo de ese turno espera $3.000 menos, y la persona se
lleva los $3.000 en ese momento. Vale igual si la venta se pagó por transferencia o se fió: la comisión se paga en
efectivo del cajón.

### Decisión 6 · En los reportes — como costo, el día que se pagó

La categoría es de naturaleza **costo**: resta de la utilidad bruta el día del gasto (el día que salió la plata), y
*Ver cálculo* la lista con su enlace. Como la plata salió ese día, contarla ese día es lo que pasó, también en una venta
fiada.

### Decisión 7 · Si se anula la venta

- Si el turno del gasto sigue abierto, **el gasto se anula** con la venta: la persona devuelve los $3.000 al cajón.
- Si el turno ya se cerró, **el gasto se queda**: la plata salió en ese turno, que ya cuadró. La anulación lo avisa.

## 5. Requisitos funcionales

**La marca (fase 1, hecha)**
- **RF-001** · Un repuesto puede pagar comisión por cambio, con su monto. Lo marca el administrador en la ficha.

**En la venta (P1)**
- **RF-002** · Un renglón de un repuesto marcado pide *¿se cambia aquí?* con dos respuestas y ninguna escogida. Sin
  escoger, *Cobrar* no se habilita y dice por qué.
- **RF-003** · *No se cambia*: el precio unitario del renglón es el del repuesto menos su comisión, y así se cobra, se
  ve en el total y sale en el comprobante (*"sin cambio"*).
- **RF-004** · *Se cambia aquí*: el precio es el del repuesto, y el renglón lleva quién (de entrada, el que registra).
- **RF-005** · La elección viaja en el borrador de la venta: un reintento cobra exactamente lo mismo.

**Al cobrar (P1)**
- **RF-006** · Por cada renglón que se cambia, un gasto del cajón en *"Comisión cambio de aceite"* (costo) por
  comisión × cantidad, con la venta, el repuesto y la persona en la descripción, enlazado a su renglón. En la misma
  transacción que la venta: o quedan los dos o ninguno.
- **RF-007** · Ese gasto no pide confirmar aunque pase de lo que hay en el cajón: la venta ya se cobró.
- **RF-008** · La comisión es la del repuesto **en el momento del cobro**.

**Anular (P1)**
- **RF-009** · Al anular la venta, sus gastos de comisión se anulan si su turno sigue abierto; si no, se quedan y la
  pantalla lo dice.

**Reportes (P1 y P2)**
- **RF-010** · Las comisiones restan de la utilidad bruta como costo, y *Ver cálculo* las muestra con su enlace.
- **RF-011** · (P2) En *Reportes › Gastos*, filtrando *"Comisión cambio de aceite"*, se ve cada una con la persona;
  el total es lo que se pagó en comisiones en el período.

## 6. Manejo de errores

- Un renglón de aceite sin escoger → no deja cobrar: *"Escoge si el MOTUL 7100 se cambia aquí"*.
- *Se cambia* con una persona que ya no está activa → no deja cobrar; se escoge otra.
- Un repuesto que dejó de pagar comisión entre armar la venta y cobrarla → el precio visto no cuadra y no se cobra,
  como hoy con un precio que cambió.
- Anular una venta cuyo turno de comisión ya se cerró → se anula la venta y se avisa que la comisión se queda.

## 7. No funcionales

- **Plata:** pesos enteros; precio sin cambio = precio − comisión, nunca negativo (una comisión mayor que el precio no
  se deja cobrar).
- **Caja:** la comisión es un gasto del cajón: entra al arqueo, al cierre, a su comprobante y a su correo sin nada nuevo.
- **Roles:** marcar es del administrador; escoger y cobrar, del que vende.
- **Esquema:** la categoría del sistema y, en el renglón, la elección, la persona, la comisión y el gasto.

## 8. Criterios de aceptación

- [ ] Un MOTUL 7100 ($65.000, comisión $3.000) *no se cambia*: la venta cobra $62.000 y no hay gasto.
- [ ] *Se cambia · Gustavo*: la venta cobra $65.000, hay un gasto del cajón de $3.000 a nombre de Gustavo, y el
      esperado del cierre baja $3.000.
- [ ] 2 × MOTUL 5100 *se cambia*: un gasto de $6.000.
- [ ] Sin escoger no se cobra; el lubricante de cadena (sin marca) no pregunta nada.
- [ ] La utilidad bruta baja $3.000 por cambio, y *Ver cálculo* muestra la comisión con la venta y la persona.
- [ ] Anular la venta en el mismo turno anula el gasto; en otro turno lo deja y lo avisa.

## 9. Qué no se toca

- La nómina: la comisión es aparte.
- Los reportes y el cierre: ya cuentan los gastos del cajón de costo.

## Fuera de alcance

- Partir un renglón (unas unidades con cambio y otras sin).
- Comisiones de ventas pasadas.
- Otras comisiones (mano de obra general): la marca del repuesto sirve igual para cualquier repuesto.
