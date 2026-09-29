# Plan 0015 · La comisión por cambio de aceite

**Traduce:** [`spec.md`](spec.md). El usuario pidió el plan el 2026-09-29 sin cambiar el spec: se toman las seis
recomendaciones.

**Estado:** aprobado el 2026-09-29 · en implementación · fase 1 hecha

## Lo que se da por hecho (las tres aclaraciones del spec)

El plan arranca con estas respuestas. Cualquiera se puede cambiar antes de la fase que la usa, sin rehacer las demás.

| Pregunta | Se toma | Se usa en |
|---|---|---|
| Una moto con dos botellas, ¿uno o dos cambios? | **Por unidad**, y en el cobro se corrige cuántos cambios fueron (de 0 a la cantidad) | Fase 2 |
| ¿Cambia aceite alguien sin usuario? | **No**: se escoge entre los usuarios activos (hoy Ruben, Deibis y Gustavo) | Fase 2 |
| ¿Se registran las 6 ventas de aceite de antes? | **No**: arranca el día que se sube la fase 2. Si el dueño las quiere, se cargan a mano después, con un guion | — |

---

## Dónde vive cada pieza

Un módulo nuevo, `comisiones`, al lado de `clientes`: la venta le pide que registre, como hoy le pide a la cartera que
fíe (`FiarVenta`).

| Pieza | Carpeta | Qué es |
|---|---|---|
| `Variante.comisionCambio` (monto, o nulo si no paga) | `domain/…/inventario/dominio/` | La marca del repuesto (decisión 1) |
| `Comision` | `domain/…/comisiones/dominio/` | Un cambio: venta, renglón, repuesto, persona, monto, pago y anulación |
| `PagoDeComisiones` | `domain/…/comisiones/dominio/` | Lo que se le pagó a una persona: cuánto, de dónde, qué comisiones cubrió |
| `SaldoDeComisiones` | `domain/…/comisiones/dominio/` | Por persona: registrado − anulado = por pagar + pagado (la regla de plata del spec) |
| `RepositorioComisiones`, `RepositorioPagosDeComisiones` | `domain/…/comisiones/dominio/puerto/` | Lo que el dominio pide de afuera |
| `RegistrarComisiones` | `domain/…/comisiones/aplicacion/` | Lo llama `CobrarVenta` dentro de su transacción; y `alAnular`, `AnularVenta` |
| `ConsultarComisiones`, `PagarComisiones`, `AnularPagoDeComisiones` | `domain/…/comisiones/aplicacion/` | Los casos de uso de la pantalla |
| Adaptadores JPA y `ComisionController` (`/api/comisiones`) | `pos/…/comisiones/infraestructura/` | — |
| `RenglonVendido.comision` y su reparto en `LoCobrado` | `domain/…/reportes/dominio/` | La comisión entra al reporte como el costo (decisión 6) |

**Pagar desde el cajón reusa el retiro** (`RegistrarRetiro`): el pago guarda el id de su retiro, con el motivo
*"Pago de comisiones a Gustavo"*. Así el arqueo, el cierre, su comprobante y su correo ya lo cuentan sin tocar las
columnas del cierre ni sus `CHECK` (lo que costó la V21 con los abonos). Un retiro no cuenta en los reportes, que es
justo lo que pide el RF-010: pagar no vuelve a restar. La línea en el cierre dice su motivo; el spec pedía *"su línea
propia"*, y esto la da sin una columna nueva.

---

## Fase 1 · Qué repuestos pagan comisión (decisión 1, RF-001)

- **V29**: `variante.comision_cambio numeric(14,2)` nulo, con `CHECK (comision_cambio IS NULL OR comision_cambio > 0)`.
- `Variante`: `comisionCambio` (`Dinero`, nulo), con su regla: mayor que $0 y en pesos enteros.
- `ActualizarRepuesto`: lo recibe; cambiarlo es del administrador y queda en la auditoría como `CORREGIR_REPUESTO`, con
  el antes y el después. Si tiene comisiones por pagar, no las toca (se avisa en la pantalla, fase 3).
- La búsqueda y la ficha del repuesto lo devuelven; el cajero ve si un repuesto paga comisión, no le hace falta más.
- Pantalla: en `FichaRepuesto`, *"Paga comisión por cambio de aceite"* con su monto (de entrada $3.000 al marcarlo).
- Al subirla: marcar en producción MOTUL 5100 (104081), MOTUL 7100 (104089), KIXX 10W40 (706207) y KIXX 20W50 (706209)
  con $3.000, por la API.

**Pruebas:** `VarianteTest` (monto inválido), `ActualizarRepuestoTest` (el cajero no puede, la auditoría),
`MigracionesIntegracionTest` (V29 sobre una base con repuestos: quedan sin marca), `inventario.test.js`.
**Romper:** dejar que el cajero lo cambie; aceptar $0.
**Checkpoint:** en producción, los cuatro aceites marcados; el lubricante de cadena no.

## Fase 2 · Registrar al cobrar (decisiones 2 a 4, RF-002 a RF-004, RF-008)

- **V30**: `comision` (id, venta_id, posicion del renglón, variante_id, persona_id → usuario, monto > 0 o < 0 en un
  ajuste, registrada_en, pago_id, anulada_en, anulada_por_id, origen `CAMBIO`/`AJUSTE`, ajusta_a_id) y
  `pago_comisiones` (id, persona_id, monto, forma, cuenta_id, turno_id, retiro_id, pagado_por_id, pagado_en,
  llave_idempotencia única, anulado_en, anulado_por_id, motivo_anulacion). Los `CHECK` de las dos, y el de la auditoría
  con `ANULAR_PAGO_COMISIONES`.
- `ComandoCobrarVenta.Renglon` suma `cambios` (cuántas unidades se cambiaron aquí) y el comando suma `cambioPorId`
  (quién). `CobrarVenta`, ya guardada la venta, llama `RegistrarComisiones.alCobrar`: una comisión por unidad
  cambiada de cada renglón cuyo repuesto paga, con el monto **de ese momento** (RF-001). Un renglón sin marca que
  diga `cambios` → error de lectura; la persona tiene que ser un usuario activo.
- `AnularVenta` llama `RegistrarComisiones.alAnular`: las pendientes se anulan; por cada una ya pagada, un **ajuste**
  de −$monto pendiente con la misma persona (RF-008: se descuenta del próximo pago). Todo en la misma transacción.
- `GET /api/comisiones/personas`: los usuarios activos, id y nombre, para el cobro (el cajero no ve `/api/usuarios`).
- Pantalla: en `ModalCobro`, si algún renglón paga comisión, *"Cambios de aceite"*: cada uno con *se cambió aquí*
  (cuántos, de entrada la cantidad) y *¿quién hizo el cambio?* (de entrada quien registra). Viaja en el borrador de la
  venta, para que un reintento cobre lo mismo. `utils/venta.js` lo manda en el comando. Sin aceite, nada cambia.

**Pruebas:** `RegistrarComisionesTest` (por unidad; desmarcado no deja nada; monto del momento; anular pendiente y
pagada con su ajuste), `CobrarVentaTest` (la venta con comisión sigue valiendo lo mismo), un
`ComisionesIntegracionTest` contra Postgres (cobro y anulación en una transacción; dos cobros a la vez),
`MigracionesIntegracionTest` (V30), `venta.test.js` (el comando y el borrador).
**Romper:** comisión por venta en vez de por unidad; anular sin ajuste de la pagada; usar el monto de hoy y no el del
cobro.
**Checkpoint:** en producción, cobrar un MOTUL 7100 con Gustavo deja $3.000 por pagar a Gustavo; la venta sigue en su
precio.

## Fase 3 · Ver y pagar (decisión 5, RF-005 a RF-007)

- `ConsultarComisiones`: por persona, por pagar (pendientes, ajustes incluidos) y pagado en el período, con cada una
  (fecha, venta N.º, repuesto, monto). Administrador: todas las personas.
- `PagarComisiones` (administrador): paga las pendientes de una persona (todas, o las escogidas), con llave. **Del
  cajón**: exige turno abierto propio (spec 0004), registra el retiro con motivo *"Pago de comisiones a <nombre>"* y
  pide confirmar si pasa de lo que debería haber, como un gasto. **Por fuera**: efectivo o transferencia con su
  cuenta, sin cajón. Un pago de $0 o negativo (solo ajustes) no se registra: se avisa.
- `AnularPagoDeComisiones` (administrador, con motivo): sus comisiones vuelven a pendientes; si salió del cajón, anula
  su retiro (con las reglas del retiro: el mismo turno abierto). Auditado.
- Pantalla *Reportes › Comisiones*: una tarjeta por persona con lo por pagar y *Pagar*; el detalle con cada cambio;
  los pagos con *Anular*. Y en la ficha del repuesto, el aviso si tiene comisiones por pagar al cambiar el monto.

**Pruebas:** `SaldoDeComisionesTest` (registrado − anulado = por pagar + pagado, con ajustes), `PagarComisionesTest`
(del cajón resta del esperado; doble clic deja uno; sin turno; el cajero no puede), `AnularPagoDeComisionesTest`,
integración (el retiro y el arqueo del cierre), `comisiones.test.js`.
**Romper:** pagar dos veces con la misma llave; pagar del cajón sin retiro; que el pago reste en los reportes.
**Checkpoint:** en producción, pagarle a Gustavo desde el cajón baja el esperado y deja sus cambios pagados.

## Fase 4 · En los reportes (decisión 6, RF-009, RF-010)

- `RepositorioReportes.renglonesDe` trae, por renglón, la suma de sus comisiones de cambio vigentes (los ajustes no
  cuentan: son del pago, no de la venta).
- `LoCobrado`: la comisión se reparte como el costo, por acumulado, en la parte cobrada (spec 0014).
- `ResultadosDelPeriodo`: `Cifras.comisiones`; *utilidad bruta = ventas netas − costo vendido − comisiones − costos
  adicionales*, con su prueba; las filas del día por día y los repuestos la restan igual.
- Pantalla: *Ver cálculo* de la utilidad bruta con la línea *Comisiones por cambio de aceite* y cada una (fecha, venta,
  persona, monto); la ayuda lo dice.

**Pruebas:** `LoCobradoTest` (fiado a medias: la mitad de la comisión), `ResultadosDelPeriodoTest` (las partes
suman), integración (un cobro con cambio baja la bruta $3.000; pagarlo no la mueve).
**Romper:** restar el pago en vez de la comisión; contar la comisión entera de un fiado sin cobrar.
**Checkpoint:** en producción, la utilidad bruta del día baja $3.000 por cambio y *Ver cálculo* lo muestra.

## Fase 5 · Lo del cajero (P3, RF-011)

- `ConsultarComisiones` para un cajero: solo las suyas. *Mis comisiones* en el menú del cajero.
- **Pruebas:** el cajero no ve las de otro (servidor, no pantalla). **Checkpoint:** Gustavo entra y ve las suyas.

---

## Verificación

1. `./mvnw clean install` (dominio, Postgres, migraciones) y en el frontend `eslint`, `node --test` y `vite build`.
2. Capturas con Edge sin interfaz y respuestas simuladas (desde PowerShell): el cobro con aceite, *Comisiones*, *Ver
   cálculo*; en claro, oscuro y 390 px.
3. Romper a propósito, por fase.
4. Cada fase: commit solo de sus archivos, push, esperar a Render y comprobar en producción (solo lectura, y las
   escrituras por la API).

## Bitácora de decisiones

| Fecha | Fase | Decisión | Por qué |
|---|---|---|---|
| 2026-09-29 | — | **Pagar desde el cajón es un retiro** con el id del pago, no una salida nueva del cierre | El arqueo, el cierre, su comprobante y su correo ya cuentan los retiros; una salida nueva obligaba a reescribir las columnas y los `CHECK` del cierre, como la V21. Y un retiro no cuenta en los reportes, que es lo que pide el RF-010 |
| 2026-09-29 | — | **Lo pagado de una venta que se anula vuelve como un ajuste negativo** pendiente con la persona | Así el próximo pago lo descuenta solo, y *por pagar* sigue siendo una suma, sin estados especiales |
| 2026-09-29 | — | **La comisión guarda el monto del momento del cobro** | Cambiar el monto del repuesto no puede cambiar lo que ya se le debe a alguien (RF-001) |
| 2026-09-29 | 1 | **La marca va por un endpoint propio (`PUT /api/repuestos/{id}/comision`, caso de uso `CambiarComisionDeCambio`), no dentro de `ActualizarRepuesto`** | Marcar un aceite no obliga a reenviar la ficha entera, y el comando de corregir ficha (que usan la pantalla y varias pruebas) no cambia. Queda auditado igual, como `CORREGIR_REPUESTO` con el antes y el después |
| 2026-09-29 | 1 | Cierre: 566 del dominio y la suite de Postgres (V29, permiso de administrador); 316 de pantalla, lint y build; captura de la ficha marcando .000. Romper: quitar el permiso de administrador (atrapado). De paso, `CorreoIntegracionTest` esperaba el remitente "RD MOTORS": el cambio de nombre del 2026-09-29 se subió sin la suite completa | — |
