# Plan 0010 — El correo del cierre de caja

**Spec:** [`spec.md`](spec.md) · **Implementado el 2026-09-22**

---

## Dónde cae cada pieza

| Pieza | Dónde cae |
|---|---|
| `Correo` (entidad: tipo, turno, destinatarios, estado, intentos, `noAntesDe`, último error, id en Brevo), `AjustesDeCorreo` (singleton), `Destinatarios`, `PoliticaDeReintentos`, `CorreoArmado`, `EnvioFallidoException`, `EstadoCorreo`, `TipoCorreo` | `correo/dominio/` |
| `EnviadorDeCorreos` (`estaConfigurado`, `loQueFalta`, `remitente`, `enviar`), `RepositorioCorreos` (con `porMandar` bajo candado), `RepositorioAjustesDeCorreo` | `correo/dominio/puerto/` |
| `EncolarCorreoDelCierre` (sin transacción propia: la del cierre), `Cartero` (arma, manda y anota; nunca lanza), `CorreoDelCierre` y `CorreoDePrueba` (el contenido), `MandarCorreosPendientes`, `ConsultarCorreos`, `AdministrarCorreos` | `correo/aplicacion/` |
| `EnviadorBrevo` (RestClient sobre la API HTTP), `RepositorioCorreosJpa` (`for update skip locked`), `RepositorioAjustesDeCorreoJpa`, `TareaDeCorreos` (`@Scheduled` cada minuto), `CorreoController`, `ConfiguracionDeCorreo` | `pos/…/correo/infraestructura/` |
| **V24**: tablas `correo` y `ajustes_correo` | `pos/…/db/migration/` |
| `CerrarTurno` recibe `EncolarCorreoDelCierre`; `ConsultarTurnos` gana `detalleDelCierre(turnoId)` (lo lee el sistema, no una persona) | `caja/aplicacion/` |
| *Ajustes › Correos*: destinatarios, estado de la cuenta, últimos correos, prueba y reintentar | `frontend/src/paginas/Correos.jsx`, `utils/correos.js` |

## Decisiones tomadas

| Decisión | Por qué |
|---|---|
| **Cola en la base, no envío directo** como el car-wash | Allá el servidor vive en la nube; aquí, en la tienda. Un cierre sin internet perdería el correo en silencio. La fila se escribe en el commit del cierre y la tarea la manda cuando pueda |
| **El cuerpo se arma al mandarlo**, no al encolar | Las observaciones de un cierre que no cuadró se escriben *después* de cerrar (`ModalCerrarTurno.jsx:73-108`); armándolo 3 minutos más tarde, llegan. Las cifras del cajón son siempre las firmadas |
| **Reintentos 1, 5, 15, 30 min y luego cada hora, sin rendirse** | Lo normal es un corte corto; pero una tienda puede pasar un fin de semana sin internet y el correo del viernes tiene que llegar el lunes |
| **Se distingue el error que se arregla solo del que no** | Sin internet o Brevo caído: esperar. Llave inválida o remitente sin verificar: insistir cada hora sería golpear una pared, así que queda *falló* y a la vista |
| **La llave de Brevo no vive en la base ni en la pantalla** | Es un secreto. Va en `BREVO_API_KEY`; la API solo dice si está puesta y qué falta. Una prueba lo verifica (`SeguridadIntegracionTest`) |
| **Los destinatarios sí viven en la base**, editables por el administrador | Cambiar a quién le llega no debería exigir tocar el servidor. Vacío = apagado |
| **Un turno deja un solo resumen** (índice único parcial) | Si el cierre se reintentara, no habría dos correos del mismo turno |
| **`for update skip locked`** al tomar los pendientes | Dos vueltas de la tarea a la vez no se quedan con el mismo correo, y ninguna espera a la otra |
| **Entrega «al menos una vez»** | Si el servidor se cae justo después de que Brevo aceptó, ese correo puede repetirse. Es un resumen, no un pago |
| El contenido son los **mismos cuatro bloques** de la Caja (producido, cómo lo pagaron, cartera, el cajón) | Portados del corte de caja del car-wash. Lo que llega al correo dice lo mismo que la pantalla |

## Verificación

- **Dominio (19 pruebas):** `CorreoTest` (destinatarios, la cola, los reintentos, lo que no se arregla solo) y
  `CorreoDelCierreTest` con la caja entera: cerrar encola en el mismo commit, no sale antes de 3 minutos, lo que
  llega dice las cifras del turno real, las observaciones escritas después llegan, lo que escribe una persona se
  escapa, sin internet se reintenta, con llave inválida queda fallido, sin configurar espera sin gastar intentos.
- **Integración (7 pruebas):** `CorreoIntegracionTest` contra Postgres y contra un **Brevo de mentira**
  (`HttpServer` del JDK): la llave viaja en su cabecera, el cuerpo tiene la forma que Brevo espera, un 503 se
  reintenta y un 401 no, la base no deja dos resúmenes del mismo turno, y la llave no sale en ningún error.
- **Seguridad:** el cajero recibe 403 en todo `/api/correos`, y la llave no aparece en la respuesta.
- **Pantalla:** `utils/correos.test.js` y el recorrido con capturas.

## Lo que queda por hacer, y es del dueño

1. Crear la cuenta en **Brevo** y verificar el remitente (un dominio o una dirección).
2. Poner la llave en el servidor: variable de entorno `BREVO_API_KEY`, y `RDMOTORS_CORREO_REMITENTE` con la
   dirección verificada.
3. En *Ajustes › Correos*, escribir a qué correos llega y **mandar uno de prueba**.

## Bitácora

| Fecha | Decisión | Por qué |
|---|---|---|
| 2026-09-22 | Se portó de Brevo la **forma de hablarle** (API HTTP, `api-key` en la cabecera, HTML + texto), no su forma de mandar | El car-wash manda y olvida porque vive en la nube; aquí eso perdería correos |
| 2026-09-22 | `ConsultarTurnos.detalleDelCierre(turnoId)` sin actor | El correo lo arma el sistema, no una persona; a quién le llega ya lo decidió el administrador |
