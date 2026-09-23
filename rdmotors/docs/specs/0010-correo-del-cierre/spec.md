# Spec 0010 — El correo del cierre de caja

**Estado:** pedido por el dueño el 2026-09-22 (*«implementemos correos por Brevo de cierre de caja como en el
car-wash»*) · en implementación ([plan](plan.md))
**Depende de:** spec 0006 (cierre de caja), 0008 (fiado y abonos), 0009 (sin internet)

> **Convención:** lo que dice *"hoy"* está verificado con `archivo:línea`. Lo que dice *"debe"* es propuesta.

---

## 1. Objetivo de negocio

Que el dueño sepa cómo cerró cada turno **sin estar en la tienda**: cuánto se vendió, cómo lo pagaron, qué quedó
fiado, cuánto se cobró de cartera y si el cajón cuadró. Hoy eso solo se ve entrando al sistema desde el
computador de la tienda (spec 0004: el servidor no se publica en internet).

## 2. Caso de uso

**H1 (P1) · Al cerrar, el resumen le llega al dueño.** *Demo:* Carolina cierra con un faltante de $5.000 y
escribe por qué; a los pocos minutos, al correo del dueño llega *"Cierre de caja · faltan $5.000"* con el
producido, las formas de pago, la cartera, el cajón y la observación.

**H2 (P1) · Sin internet no se pierde.** *Demo:* se cierra con el cable desconectado; el correo espera. Al volver
internet, sale solo. El cierre nunca espera al correo.

**H3 (P2) · El administrador ve si salieron.** En *Ajustes › Correos*: a quién se manda, si la cuenta de Brevo
está lista, los últimos correos con su estado, *mandar uno de prueba* y *reintentar* uno que falló.

## 3. Qué existe hoy

| Qué | Dónde | Qué significa |
|---|---|---|
| El car-wash manda el resumen del cierre por la **API HTTP de Brevo** (`POST https://api.brevo.com/v3/smtp/email`, cabecera `api-key`, cuerpo `{sender, to, subject, htmlContent, textContent}`) | `CAR-WASH-SYSTEM/backend/…/service/impl/EmailServiceImpl.java:29,44-47,100-117` | La forma de hablar con Brevo se porta tal cual |
| Allá se manda **después** de confirmar el cierre, en segundo plano, al dueño del negocio; si falla, **solo queda en el log** | `…/EmailServiceImpl.java:192-212`; `…/CashSessionServiceImpl.java:201-225` | Correcto en la nube. **Aquí no**: el servidor está en la tienda y puede no tener internet (§4) |
| Su contenido tiene cuatro bloques: producido, formas de pago, arqueo y cartera | `CAR-WASH-SYSTEM/frontend/src/utils/cashSessionUtils.js:10-31` | Los mismos cuatro que ya muestra nuestra Caja (`frontend/src/utils/producido.js`) |
| El cierre calcula y firma las cifras del cajón con el turno bloqueado | `domain/…/caja/aplicacion/CerrarTurno.java:53-78` | El correo se engancha ahí, en la misma transacción |
| Las observaciones de un cierre que no cuadró se escriben **después** de cerrar, en la misma ventana | `frontend/src/componentes/caja/ModalCerrarTurno.jsx:73-108` | Un correo armado en el instante del cierre saldría sin la explicación del faltante |
| El detalle del turno ya trae todo lo que el correo necesita | `domain/…/caja/aplicacion/ConsultarTurnos.java:68-80` | No hay que calcular nada nuevo |
| Ya hay tareas programadas (el respaldo) | `pos/…/respaldo/infraestructura/ConfiguracionDeTareas.java` | La tarea de los correos usa el mismo reloj |
| Los usuarios no tienen correo | `domain/…/usuarios/dominio/Usuario.java` | Los destinatarios se configuran aparte |

## 4. La decisión · ¿Qué pasa si al cerrar no hay internet?

| Opción | Qué implica |
|---|---|
| A. Como el car-wash: mandar al cerrar, en segundo plano, y si falla anotarlo en el log | Sin internet el correo **se pierde en silencio**, justo el día que el dueño más lo querría ver |
| **B. Cola en la base.** El cierre deja el correo *por mandar* en la misma transacción; una tarea lo manda y reintenta hasta que Brevo lo acepte | Sobrevive a días sin internet y a reinicios. El administrador ve cuáles salieron y cuáles no |

**Decidido: B.** Es la regla de toda la rebanada 4: el mostrador nunca espera a internet, y lo que se tiene que
enviar, espera en la base hasta poder salir.

## 5. Requisitos funcionales

- **RF-001** · Al cerrar un turno, si hay destinatarios configurados, queda un correo **por mandar** en la misma
  transacción del cierre. Si el cierre falla, el correo no existe.
- **RF-002** · El correo sale **3 minutos después** del cierre, para que alcance a llevar las observaciones que se
  escriben en la ventana del cierre. Se arma al mandarlo, con las cifras que se firmaron al cerrar.
- **RF-003** · Una tarea revisa cada minuto los correos por mandar. Si Brevo no responde o no hay internet,
  reintenta: a los 1, 5, 15 y 30 minutos y luego cada hora. Nunca frena el mostrador.
- **RF-004** · Si Brevo responde que el correo **no se puede mandar así** (llave inválida, remitente sin
  verificar, dirección mala), no se reintenta solo: queda **fallido** con lo que dijo Brevo, a la vista, y el
  administrador lo reintenta después de arreglarlo.
- **RF-005** · Contenido: el turno (quién lo abrió y lo cerró, cuándo, fondo), **el producido** y cómo lo pagaron,
  la **cartera** del turno (lo fiado y los abonos), **el cajón** (debería haber, contaron, diferencia, con su
  desglose), lo que **salió** del cajón y las **observaciones**. En HTML y en texto plano.
- **RF-006** · El asunto dice si cuadró: *"Cierre de caja · RD MOTORS · 22 sept · Carolina Ruiz · faltan $5.000"*.
- **RF-007** · Los destinatarios (de 1 a 5 correos) los configura el **administrador** en *Ajustes › Correos*. Sin
  destinatarios, no se encola nada.
- **RF-008** · La llave de Brevo y el remitente **no viven en la base ni en la pantalla**: van en la configuración
  del servidor (variable de entorno `BREVO_API_KEY`). La pantalla solo dice si están puestos.
- **RF-009** · *Ajustes › Correos* muestra los últimos correos con su estado (*por mandar*, *enviado*, *falló*),
  cuántos intentos lleva y el último error; deja **mandar uno de prueba** y **reintentar** uno fallido.

## 6. Manejo de errores

| Situación | Qué pasa |
|---|---|
| No hay internet al cerrar | El cierre termina igual; el correo espera y sale cuando vuelva |
| Brevo no responde o responde 5xx / 429 | Se reintenta con esperas cada vez más largas |
| La llave es inválida (401) o el remitente no está verificado (400) | Queda *falló*, con el mensaje de Brevo; no se insiste solo |
| No está configurada la llave | El correo queda *por mandar*, con el aviso *"falta la llave de Brevo"* |
| Se reinicia el servidor con correos por mandar | Siguen en la base; la tarea los retoma |
| Dos tareas a la vez (no debería, pero) | Cada correo se toma con candado: no sale dos veces por eso |

## 7. Requisitos no funcionales

- **El mostrador nunca espera al correo** (RF-001: encolar es una fila más en el cierre).
- **Roles:** configurar y ver los correos es del administrador.
- **Secretos:** la llave nunca se guarda en la base, ni se muestra, ni sale en un log.
- **Entrega:** *al menos una vez*. Si el servidor se cae justo después de que Brevo aceptó y antes de anotarlo,
  ese correo puede llegar dos veces. Se acepta: es un resumen, no un pago.
- **Esquema:** V24, dos tablas nuevas (`correo` y `ajustes_correo`).

## 8. Criterios de aceptación

- [ ] Cerrar un turno con destinatarios deja un correo por mandar, en la misma transacción
- [ ] El correo lleva el producido, las formas de pago, la cartera, el cajón y las observaciones escritas al cerrar
- [ ] Sin internet, el cierre termina igual y el correo sale después solo
- [ ] Un error de Brevo que no se arregla solo deja el correo *falló*, con el mensaje a la vista, y se puede
      reintentar
- [ ] El cajero no ve ni cambia los destinatarios
- [ ] La llave no aparece en la base, ni en la respuesta de la API, ni en los logs

## 9. Fuera de alcance

- Otros correos (restablecer contraseña, resumen del día o de la semana).
- Mandar el comprobante como PDF adjunto: el correo lleva el mismo contenido en el cuerpo.
- Configurar la llave de Brevo desde la pantalla.
