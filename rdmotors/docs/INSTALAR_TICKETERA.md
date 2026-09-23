# Instalar la ticketera en el computador del mostrador

Guía de una sola vez por computador (spec 0003, RF-023). Con esto, **al cobrar el comprobante sale solo
por la ticketera, sin preguntar nada**.

Hacen falta dos cosas, y las dos se configuran en Windows, no en RD MOTORS:

1. La ticketera instalada y puesta como **impresora predeterminada**, con papel de **80 mm**.
2. RD MOTORS abierto desde un **acceso directo del navegador en modo kiosco de impresión**
   (`--kiosk-printing`).

> Ninguna página web puede elegir la impresora ni cambiar cómo se abrió el navegador: es una barrera de
> seguridad de los navegadores. Por eso este paso es manual, y por eso se hace una sola vez.

**La venta nunca depende de la impresora.** Si la ticketera está apagada o sin papel, la venta queda
cobrada igual y el comprobante se reimprime desde **Vender → Ventas del turno**.

---

## Antes de empezar

Anota la **dirección de RD MOTORS** en la tienda: es la que se escribe en el navegador para abrirlo.

- Si el servidor corre en este mismo computador: `http://localhost:5174`.
- Si corre en otro computador de la tienda: `http://<IP del servidor>:5174`, por ejemplo
  `http://192.168.1.50:5174`. La da quien instaló el servidor.

Ábrela una vez en el navegador normal y comprueba que carga la pantalla de Vender.

---

## Paso 1 · Instalar la ticketera y probarla

1. Conecta la ticketera por USB e instala su **driver** (viene en el CD o en la página del fabricante).
2. Windows → **Configuración → Bluetooth y dispositivos → Impresoras y escáneres**.
3. Abre la ticketera → **Imprimir página de prueba**.

Si la página de prueba no sale, revisa el cable y el driver antes de seguir. Eso no es un problema de
RD MOTORS, y ningún paso siguiente lo arregla.

## Paso 2 · Papel de 80 mm y predeterminada

1. En **Impresoras y escáneres**, abre la ticketera → **Preferencias de impresión**.
2. Tamaño de papel: **80 mm** (según el driver: "80 x 297 mm", "Roll 80mm" o parecido).
3. Márgenes: **ninguno** o **0**, si el driver deja elegirlos.
4. De vuelta en **Impresoras y escáneres**, desactiva **"Permitir que Windows administre mi impresora
   predeterminada"**. Si no, Windows la cambia solo y el ticket sale por otra impresora.
5. Abre la ticketera → **Establecer como predeterminada**.

## Paso 3 · El acceso directo en modo kiosco

**Opción A, automática (recomendada).** Doble clic en
[`scripts/crear-acceso-directo-pos.bat`](../scripts/crear-acceso-directo-pos.bat). Pide la dirección de
RD MOTORS y crea en el Escritorio el acceso directo **"RD MOTORS POS"** con Microsoft Edge, que viene en
todo Windows 10 y 11.

**Opción B, a mano.** Clic derecho en el Escritorio → **Nuevo → Acceso directo**, y en la ubicación
escribe, cambiando la dirección por la de la tienda:

```
"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" --kiosk-printing --user-data-dir=C:\Users\Public\RDMotorsPOS --no-first-run --app=http://localhost:5174
```

Qué hace cada parte:

| Parte | Para qué |
|---|---|
| `--kiosk-printing` | Imprime en la predeterminada **sin mostrar el diálogo** |
| `--user-data-dir=...` | Un perfil propio: la ventana del POS es su propio proceso y el modo kiosco aplica aunque haya otras ventanas de Edge abiertas |
| `--no-first-run` | Sin asistentes de bienvenida en ese perfil nuevo |
| `--app=...` | Abre RD MOTORS en una ventana limpia, sin barra de direcciones |

Con Chrome es igual, cambiando la ruta por
`"C:\Program Files\Google\Chrome\Application\chrome.exe"`.

## Paso 4 · Probar

1. Cierra RD MOTORS si estaba abierto y ábrelo **desde el acceso directo "RD MOTORS POS"**.
2. Abre el turno si no hay uno abierto.
3. Vende un repuesto y cóbralo.
4. El comprobante de 80 mm **sale solo**, con el número, los repuestos, el total, el pago y el cambio.
   Revisa que los renglones sumen el total impreso.

Para revisar el formato antes de configurar el kiosco, cobra desde el navegador normal: aparece el
diálogo de impresión, eliges la ticketera e imprimes.

---

## Datos del encabezado

El nombre, NIT, dirección, teléfono y mensaje al pie se cambian en RD MOTORS, en el botón **⚙ Datos de
la tienda** de la barra de arriba. Al lado del formulario se ve cómo queda el comprobante.

## Si algo no sale bien

| Qué pasa | Por qué | Qué hacer |
|---|---|---|
| Al cobrar aparece el diálogo de impresión | RD MOTORS no se abrió desde el acceso directo | Cierra y ábrelo con **"RD MOTORS POS"** (Paso 3). La venta ya quedó cobrada: imprime desde el diálogo |
| Sale por otra impresora | La ticketera no es la predeterminada | Paso 2, puntos 4 y 5 |
| No sale nada y no hay diálogo | Driver, cable o ticketera apagada | Paso 1: página de prueba de Windows. Luego **Reimprimir** |
| Sale cortado o muy angosto | El papel del driver no es de 80 mm | Paso 2: 80 mm y márgenes en 0 |
| Se cobró desde un celular o una tablet y no imprimió | Así está pensado: solo imprime el computador del mostrador | En ese computador: **Vender → Ventas del turno**, abre la venta e **Imprimir** |
| El cliente vuelve por una copia | — | **Ventas del turno → Buscar venta N.º**, e **Imprimir** |
