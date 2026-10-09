# Spec 0017 — Abono mixto (parte en efectivo, parte por transferencia)

**Estado:** implementado · 2026-10-09 · [plan](plan.md) · migración V33

## 1. Objetivo de negocio

En el mostrador un cliente que debe llega y dice *«le doy $20.000 en efectivo y le transfiero $30.000»*. Hoy la
ventana de *Abonar* solo deja escoger **Efectivo** o **Transferencia**: el cajero tiene que hacer dos abonos seguidos,
repetir lo que marcó y entregar dos recibos. Al cobrar una venta sí existe *Mixto*; al abonar, no.

## 2. Caso de uso

- **P1 — Abonar mixto.** En *Abonar* aparece *Mixto*: se escribe cuánto abona en total y cuánto de eso es en
  efectivo; lo demás es transferencia. Un solo botón, un solo recibo que muestra las dos partes. El efectivo entra al
  cajón del turno; la transferencia no. *Se demuestra solo:* abonar $50.000 ($20.000 + $30.000) a un producto marcado
  y ver en Caja $20.000 de abonos en efectivo y $30.000 por transferencia, y en la ficha el producto pagado.
- **P2 — Verlo después.** En la ficha del cliente el pago mixto se ve como uno solo, con sus dos partes; en cada
  producto se ve qué parte lo pagó, como hoy. *Ver recibo* y *Reimprimir* dan el mismo recibo con las dos partes.

## 3. Qué existe hoy

| Hecho | Dónde |
|---|---|
| Un abono tiene **una** forma de pago (`EFECTIVO` o `TRANSFERENCIA`), obligatoria; el efectivo exige turno abierto | `domain/…/clientes/dominio/Abono.java:108-124` |
| La pantalla ofrece solo esas dos | `frontend/src/componentes/clientes/ModalAbono.jsx:10` |
| Al registrar, el efectivo exige que quien recibe pueda operar el turno; una transferencia de otro cajero no se cuelga al turno | `domain/…/clientes/aplicacion/RegistrarAbono.java:69-78` |
| Lo que entra al cajón se decide por la forma del abono | `Abono.java:155-157` (`entraAlCajon`) |
| La Caja separa abonos en efectivo y por transferencia | `caja/dominio/ArqueoDeTurno.java:46-47` |
| Los reportes leen la forma de **cada aplicación** a una deuda a través de su abono | `pos/…/reportes/infraestructura/RepositorioReportesJdbc.java:67` y `:213-217` |
| Cada abono tiene su llave de idempotencia (reintentar no duplica) | `RegistrarAbono.java:65-68` |
| V32 *transferencias en la caja* (`transf_abonos` en el turno): estaba en curso al escribir esto y salió a producción el 2026-10-09 | `pos/src/main/resources/db/migration/V32__transferencias_en_la_caja.sql` |

Lo que esto dice: **todo lo que lee abonos supone una sola forma por abono**: la Caja, los reportes (que reparten lo
cobrado por forma y por venta) y el cuadre de transferencias que se está construyendo.

## 4. La decisión

**¿Un abono mixto es un abono con dos partes, o dos abonos que nacen juntos?**

| | A. Dos abonos que nacen juntos (**recomendada**) | B. Un abono con dos formas |
|---|---|---|
| Qué es | Un solo botón registra, en la misma transacción, el abono en efectivo y el de transferencia, unidos por un *pago* común. El efectivo se aplica primero a lo marcado; la transferencia sigue donde quedó | El abono guarda `efectivo` y `transferencia`; la forma pasa a `MIXTO` |
| Caja, reportes, cuadre de transferencias | **No cambian**: cada abono sigue teniendo una forma | Hay que cambiarlos todos: cada aplicación a una deuda tendría que partirse entre efectivo y transferencia (¿en qué proporción? pesos sueltos) |
| Base | Una columna nueva que une los dos (`pago_id`, opcional) | Columnas nuevas y reescribir las consultas de reportes y caja |
| Recibo | Uno, con las dos partes (se arma con los dos abonos del pago) | Uno |
| Lo que se ve | En la ficha, un pago con sus dos partes | Un abono |
| Anular | Se anula **el pago**: los dos juntos (no se deja anular una parte sola) | Uno |
| Riesgo con el trabajo en curso (V32) | Ninguno: el cuadre ya cuenta los abonos por transferencia | Choca: `transf_abonos` tendría que leer la parte de transferencia de cada abono mixto |

**Recomendación: A.** Es lo que el mostrador necesita (un botón, un recibo) sin tocar cómo se calcula la plata en
ningún lado. B es más "limpio" en el papel, pero mueve los reportes y la caja, que son lo delicado.

**Decisión del dueño (2026-10-09):** *«pagos mixtos en el total, por producto en todo como tal»*. El mixto se escribe
sobre **el total** del abono (cuánto es en efectivo; lo demás, transferencia), no producto por producto; marcar qué
productos paga (spec 0016) funciona igual en todo. Es lo que da la A en pantalla: se sigue la A.

## 5. Requisitos funcionales

- **RF-001** *Abonar* ofrece *Efectivo · Transferencia · Mixto*. En *Mixto*: *Cuánto abona* (el total, como hoy) y
  *Parte en efectivo*; se muestra *Por transferencia: $X* (total − efectivo). Referencia opcional para la parte de
  transferencia.
- **RF-002** Lo marcado (spec 0016) se paga con el total: primero el efectivo y luego la transferencia, en el orden de
  lo marcado; sin marcar, a lo más viejo. Para el cliente es un solo pago.
- **RF-003** El efectivo entra al cajón del turno abierto, con las mismas reglas que un abono en efectivo; la
  transferencia, con las de una transferencia.
- **RF-004** Un recibo con el total y las dos partes. *Ver recibo* y *Reimprimir* (en el aviso de *Abono recibido*,
  donde se ven hoy) dan el mismo.
- **RF-005** Anular un pago mixto anula sus dos partes juntas, con un solo motivo.
- **RF-006** Reintentar (sin respuesta del servidor) no duplica ninguna de las dos partes.
- **RF-007** En la ficha, el pago mixto es **una línea** con el total y sus dos partes. En cada producto se ve, como hoy,
  qué abono lo pagó y de qué forma (la parte en efectivo o la de transferencia).

## 6. Manejo de errores

| Situación | Qué pasa |
|---|---|
| Parte en efectivo vacía, en $0 o igual/mayor al total | No se deja recibir: *"En mixto, el efectivo es una parte: menos que el total"* |
| Sin turno abierto (o el turno es de otro cajero) | No se recibe el mixto: *"El efectivo no tiene a qué cajón entrar…"*, como hoy con el efectivo |
| El total supera lo que debe | El mismo error de hoy |
| Falla una de las dos partes | No se guarda ninguna (misma transacción) |

## 7. No funcionales

- Roles: igual que abonar hoy; anular, solo administrador (como hoy).
- Auditoría: cada parte deja su evento como un abono; el pago se ve en ambos.
- Sin internet: no aplica (la app vive en la nube).

## 8. Criterios de aceptación

- [ ] Abonar $50.000 mixto ($20.000 efectivo) marcando un producto de $50.000: el producto queda *Pagado*.
- [ ] En Caja: abonos en efectivo +$20.000 (el esperado sube $20.000) y por transferencia +$30.000.
- [ ] Reportes del día: efectivo +$20.000, transferencia +$30.000, ventas netas +$50.000.
- [ ] Un solo recibo con las dos partes; reimprimirlo da lo mismo.
- [ ] Anular el pago devuelve los $50.000 a la deuda y saca los $20.000 del esperado.
- [ ] Reintentar el mismo envío no crea abonos de más.
- [ ] Los abonos de antes se ven y se reimprimen igual que hoy.

## 9. Qué no se toca / fuera de alcance

- Cómo calculan la Caja y los reportes: no cambia.
- El cobro *Mixto* de una venta: ya existe, no cambia.
- El *Paga ahora* de un fiado en el cobro sigue siendo efectivo **o** transferencia (se puede pedir después).
