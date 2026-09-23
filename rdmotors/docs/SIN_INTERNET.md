# Sin internet — RD MOTORS

**Resumen: sin internet, la tienda no vende.** Desde el [spec 0011](specs/0011-la-tienda-en-la-nube/spec.md), el
sistema vive en la nube y el almacén es un cliente más. Este documento dice qué se cae exactamente, por qué se
decidió así, y qué hacer cuando pase.

> **Este documento decía lo contrario hasta el 2026-09-23.** Y era verdad: el sistema corría en el computador del
> mostrador y vender no dependía de internet. Se dejó escrito el cambio en lugar de borrar la historia, porque
> explica por qué hay código que sigue pensado para funcionar sin conexión.

---

## 1. Por qué se cambió

El dueño pidió cargar su inventario base **desde el celular, de noche, con el almacén cerrado** — y el computador
del mostrador se apaga al cerrar. Con la verdad viviendo en ese computador, eso era imposible.

Se pudo pagar barato porque el almacén **todavía no vendía** con el sistema: no había nada que migrar. Dentro de
tres meses habría sido otra conversación.

El dato que lo hizo viable: el internet del almacén es **estable, casi nunca falla** (respuesta del dueño,
2026-09-23). Ese dato es ahora el eslabón más débil de todo el sistema, y conviene volver a preguntarlo cada tanto.

---

## 2. Qué se cae, exactamente

| Se cae | Qué pasa | Qué hacer |
|---|---|---|
| **El internet del almacén** | **Todo se detiene.** No se vende, no se consulta, no se cobra | Internet de respaldo (abajo) o el cuaderno |
| El internet en el celular del dueño | Lo que ya guardó está guardado; lo que estaba escribiendo, no | Esperar y seguir |
| El Wi-Fi del almacén | Igual que quedarse sin internet, para los equipos que dependan de él | Datos móviles en el celular del mostrador |
| **La luz** | Todo se detiene | Una UPS da minutos para cerrar la venta en curso |
| El computador del mostrador | Se sigue vendiendo desde cualquier otro equipo con internet | Entrar desde el celular o una tablet |
| El proveedor de la nube | Todo se detiene hasta que vuelva | Ver "volver atrás" en [`DESPLIEGUE.md`](DESPLIEGUE.md) |

La fila del computador del mostrador es lo único que **mejoró** con la mudanza: antes, si ese equipo se dañaba, el
negocio se paraba hasta conseguir otro. Ahora cualquier celular sirve.

---

## 3. Qué hacer cuando no haya internet

Ninguna de las tres es software, y ese es el punto: **no hay un "modo sin conexión" que encender**.

1. **Un plan de datos en un celular** que sirva de internet de respaldo, compartido al computador del mostrador.
   Es lo más barato que existe contra esto y resuelve la mayoría de los casos.
2. **Un cuaderno.** Si se cae todo —internet y datos—, se anota la venta a mano y se registra después. Suena
   primitivo y es lo que hacen los almacenes que llevan treinta años abiertos.
3. **Volver atrás**, si resulta que el internet no era tan estable como se creía. Está documentado en
   [`DESPLIEGUE.md`](DESPLIEGUE.md) y **no requiere tocar código**: se apaga el perfil `nube` y el sistema corre
   contra una base local otra vez.

---

## 4. Lo que sigue sin depender de internet

Poco, pero no es nada:

- **La pantalla no trae nada de afuera** —ni fuentes, ni íconos, ni librerías de terceros— y hay una prueba que lo
  vigila: `frontend/src/utils/sinInternet.test.js`. Si alguien mete una fuente de Google o un CDN, esa prueba falla
  antes de que el problema llegue al almacén. Eso significa que una conexión lenta no hace lenta la pantalla, y que
  nadie de afuera sabe qué usa el negocio.
- **Los correos del cierre aguantan cortes.** No se mandan en el momento: se encolan y se reintentan. Un corte de
  media hora no pierde ningún resumen de caja (spec 0010).
- **Las operaciones no se duplican si algo se reintenta.** Venta, gasto, retiro, abono y compra llevan llave de
  idempotencia: mandar dos veces lo mismo no cobra dos veces.

---

## 5. El riesgo, dicho sin adornos

Un corte de internet en el almacén **detiene las ventas**. Antes no. Esa es la factura de poder cargar inventario
desde el celular, y se paga todos los días aunque casi nunca se sienta.

Vender de verdad sin internet —guardar la venta en el navegador y subirla después— es un proyecto entero, no un
ajuste, y hoy está **fuera de alcance** a propósito (spec 0011, §10). Si el internet del almacén resulta menos
estable de lo que se creía, la respuesta no es construirlo a las carreras: es volver atrás, que ya está resuelto.
