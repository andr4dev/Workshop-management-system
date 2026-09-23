# Sin internet — RD MOTORS

**Resumen: sin internet, la tienda trabaja igual.** No hay un "modo sin conexión" que encender. El servidor y la
base viven en el computador de la tienda, así que internet nunca está en el camino de una venta.

Este documento existe para dos cosas: dejar **probado** que es así (spec 0009, RF-001 y RF-002) y decir qué es lo
único que sí necesita internet.

---

## 1. Por qué funciona

| Pieza | Dónde vive |
|---|---|
| La base de datos | En el computador de la tienda (`application.properties`, `localhost`) |
| El servidor | En el mismo computador, en el puerto 8081 |
| La pantalla | Se sirve desde ese servidor; **no descarga nada de internet** |
| La ticketera | Por USB o red local ([`INSTALAR_TICKETERA.md`](INSTALAR_TICKETERA.md)) |

La tablet y el celular del pasillo son pantallas que entran por **Wi-Fi a la red local**. Wi-Fi no es internet: el
Wi-Fi de la tienda puede funcionar sin internet, y esas pantallas seguirían trabajando.

**Nada de la pantalla se trae de afuera** —ni fuentes, ni íconos, ni librerías— y hay una prueba que lo vigila:
`frontend/src/utils/sinInternet.test.js`. Si alguien mete una fuente de Google o un CDN, esa prueba falla antes de
que el problema llegue a la tienda.

---

## 2. La prueba de aceptación (RF-001)

Se hace **desconectando el cable de internet del módem** (o apagando los datos del router), dejando el Wi-Fi de la
tienda encendido. Desde el computador del mostrador, en este orden:

- [ ] Entrar con usuario y contraseña
- [ ] Abrir turno con su fondo
- [ ] Vender de contado, en efectivo y en transferencia; imprimir el comprobante
- [ ] Vender **fiado** a un cliente y recibirle un **abono**
- [ ] Anular una venta con su motivo
- [ ] Registrar un gasto del cajón y un retiro
- [ ] Registrar una compra y ver que el stock sube
- [ ] Cerrar el turno y comparar el arqueo
- [ ] Ver *Reportes › Resultados* del día y la *Cartera*
- [ ] *Ajustes › Respaldo › Hacer uno ahora*

**Todo tiene que funcionar igual que con internet.** Si algo falla, no es "por el internet": es un error que hay
que arreglar.

Desde la tablet o el celular, con el Wi-Fi de la tienda encendido, lo mismo: entrar por `http://<ip>:5174` y
vender. Si lo que se cae es el **Wi-Fi**, esas pantallas dicen *"No hay conexión con el servidor"* y la venta a
medias queda guardada en ese dispositivo; se termina en el computador del mostrador.

---

## 3. Lo único que necesitará internet

El **panel del dueño en la nube** (rebanada 5), para mirar el negocio desde la casa. Cuando exista:

- Cada operación dejará su **evento** en la misma transacción en que se guarda (spec 0009, H3).
- Los eventos esperan en orden y se envían cuando hay internet (H4); repetir un envío no duplica nada.
- El mostrador **nunca espera** a ese envío.

Mientras tanto, la tienda no manda nada a ninguna parte.

---

## 4. Lo que sí tumba la tienda, y qué hacer

| Se cae | Qué pasa | Respuesta |
|---|---|---|
| Internet | Nada | — |
| El Wi-Fi de la tienda | El computador del mostrador sigue; la tablet y el celular no | Vender desde el computador |
| **La luz** | Todo se detiene hasta que vuelva | **Una UPS**: da unos minutos para cerrar la venta y apagar bien |
| El computador o su disco | Todo se detiene | El [respaldo](RESPALDO_Y_RESTAURAR.md) en otro computador |

La UPS es compra de hardware, no software, y es la que más barato sale por lo que evita: un corte de luz a mitad
de una venta con la base escribiendo es la forma más común de dañar una base de datos.
