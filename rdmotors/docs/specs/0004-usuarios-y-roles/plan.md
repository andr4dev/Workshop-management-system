# Plan 0004 — Usuarios, entrada y roles

**Traduce:** `docs/specs/0004-usuarios-y-roles/spec.md`, aprobado el 2026-09-19 con las decisiones del
usuario:

| Decisión | Resolución |
|---|---|
| 1. ¿El cajero ve costos? | **A**: no, en ninguna parte. El servidor no se los manda |
| 2. ¿Quién opera en un turno? | **A**: quien lo abrió, y el administrador |
| 3. La sesión | **Spring Security**. Contraseñas con **hash**. Sesión como **token firmado que vence a las 24 h**, que **no se manda a mano en la petición**: viaja solo, en una cookie que la página no puede leer |
| 4. Primer administrador | **A**: la primera vez, la pantalla ofrece crearlo |

**Estado:** aprobado el 2026-09-19 · 5 fases · ninguna empezada · se arranca cuando el usuario lo diga

---

## Contexto

Hoy el navegador manda un "quién" fijo (`frontend/src/api/cliente.js:11,57`) que leen 12 acciones, y
las consultas no piden nada; el servidor escucha en toda la red de la tienda
(`application.properties:30`). Las columnas de "quién" existen sin tabla de usuarios detrás. El detalle
está en el §3 del spec.

Lo que se reusa:

| Pieza | Dónde |
|---|---|
| Texto sin tildes ni mayúsculas, para comparar usuarios | `compartido/dominio/TextoDeBusqueda.java` |
| Auditoría con antes, después y motivo | `compartido/dominio/EventoAuditoria.java` · `RepositorioAuditoriaJdbc.java` |
| Errores de negocio con su código HTTP | `pos/…/compartido/infraestructura/ManejadorDeErrores.java` |
| La intención de bloqueo en el puerto (`buscarParaModificar`) | patrón de todos los repositorios |
| Los falsos en memoria y el reloj fijo | `domain/src/test/…/compartido/Falsos.java` |
| La línea *"Atendió"* del comprobante, ya preparada | `frontend/src/utils/ticket.js:37,127` |
| Seguridad de Spring y token firmado, de la versión que ya usamos | Spring Boot 4.1.1 trae Spring Security **7.1.1**: `spring-boot-starter-security` y `spring-boot-starter-security-oauth2-resource-server` (firma y lectura de tokens), y `spring-boot-starter-security-test` (verificado en el BOM de `~/.m2`) |

---

## Resumen

| Fase | Qué | Migración | Se ve en |
|---|---|---|---|
| 1 | Entrar: usuarios, contraseña con hash, token firmado de 24 h en cookie, primer administrador; el "quién" sale de la sesión | V16 | *Entrar*, *Crear el administrador*, *Cambiar contraseña*, la barra |
| 2 | El "quién" de verdad: cada "quién" es un usuario real; los casos de uso reciben a la persona con su rol; la venta a medias por persona; *"Atendió"* en el comprobante | V17 | Vender, comprobante |
| 3 | Roles: lo del administrador, bloqueado en el servidor; el cajero sin costos; el turno con dueño; el aviso de pérdida desde el servidor | — | Menú por rol, inventario, venta |
| 4 | Administrar usuarios: crear, rol, activar y desactivar, restablecer; el último administrador; restablecer desde el servidor | V18 | *Usuarios* |
| 5 | P2 y P3: el nombre de quién en cada detalle; registro de entradas | V19 | Detalles de venta, turno, gasto, retiro y compra |

**El sistema es usable al final de cada fase.** Desde la fase 1 hay que entrar; el primer administrador
de QA se crea en su checkpoint y es con el que se hace el QA del final.

---

## Decisiones tomadas al planear

1. **Módulo `usuarios` propio** (`usuarios/dominio`, `usuarios/dominio/puerto`, `usuarios/aplicacion`,
   `pos/…/usuarios/infraestructura`). La seguridad de Spring vive en `pos/…/compartido/infraestructura/seguridad/`:
   es un adaptador, el dominio no sabe que existe.
2. **`Actor` y `Rol` van en `compartido/dominio`**: quién opera y con qué rol lo necesitan todos los
   módulos. `Actor` es un valor (id, nombre, rol) que arma la seguridad con cada petición. `Usuario` (la
   entidad, con su hash, intentos y versión) vive en `usuarios/dominio` y no sale de ahí.
3. **El token.** Firmado con **HS256** y una clave de 256 bits que solo tiene el servidor de la tienda,
   con la firma y la lectura de Spring (`spring-boot-starter-security-oauth2-resource-server`, que trae
   Nimbus). No se escribe criptografía propia (el car-wash tiene su `JwtService` a mano; aquí no).
   - Dentro: `sub` = id del usuario, `ver` = versión de su sesión, `iat`, `exp` = +24 h, `iss`.
   - **Va en una cookie `HttpOnly`, `SameSite=Strict`, `Path=/`, de 24 h.** La página nunca lo ve: el
     navegador lo manda solo. Spring lo lee de la cookie con un `BearerTokenResolver` propio.
   - **Sin `Secure`**: la tablet entra por `http://192.168.x.x`. Se deja configurable para cuando haya
     HTTPS.
   - **La clave de firma** sale de `rdmotors.seguridad.clave-token`. Si no está, el servidor la genera
     la primera vez y la guarda en `~/.rdmotors/clave-token`, para que las sesiones sobrevivan a un
     reinicio. Las pruebas usan una fija.
4. **Revocar de verdad (RF-015).** Un token firmado vale hasta que vence; para que desactivar o
   restablecer saque a alguien en el acto, **cada petición carga al usuario por su id** (una lectura por
   llave primaria) y exige que esté activo y que la `ver` del token sea la suya. Desactivar, restablecer
   o cambiar la contraseña suben la versión. El rol también sale de la base, no del token: cambiarle el
   rol aplica en la siguiente petición.
5. **Hash de contraseñas**: el `DelegatingPasswordEncoder` de Spring (BCrypt hoy, con prefijo `{bcrypt}`,
   así se puede cambiar de algoritmo sin invalidar las guardadas). Detrás de un puerto `Contrasenas`:
   su segunda implementación es el falso rápido de las pruebas del dominio (BCrypt tarda a propósito).
6. **Peticiones falsificadas (CSRF).** Como la cookie viaja sola, las escrituras llevan además el token
   anti-CSRF de Spring: cookie `XSRF-TOKEN` que la página sí lee, y el encabezado `X-XSRF-TOKEN` en cada
   `POST`, `PUT` y `DELETE`. Junto con `SameSite=Strict`, una página de afuera no puede escribir con la
   sesión de nadie.
7. **Respuestas de "no".** Sin sesión → **401** `{"codigo":"SIN_SESION"}`; sin permiso → **403**
   `{"codigo":"NO_PERMITIDO"}`; turno ajeno → **403** `{"codigo":"TURNO_AJENO"}`. El frontend reacciona por
   el código, no por el texto.
8. **El bloqueo va en el caso de uso** (regla del proyecto): cada caso de uso de administrador empieza con
   `actor.exigirAdministrador()`. La seguridad de Spring solo exige *haber entrado*; los roles no se
   repiten en anotaciones de los controladores, para que haya un solo sitio que mirar.
9. **Qué ve cada rol en las consultas.** `Rol.veCostos()` decide; los controladores arman la respuesta sin
   costos para el cajero (el campo no viaja). Una prueba recorre todas las consultas con sesión de cajero y
   falla si encuentra un campo de costo.
10. **"No lo puedes ver" no es "sin costo".** Hoy un costo `null` quiere decir *"nunca se compró"* y la
    pantalla dice "Sin costo". Para el cajero la pantalla esconde las columnas de costo **por su rol**
    (lo dice la sesión), no porque el campo venga vacío.
11. **El turno con dueño es una regla del turno**: el turno sabe si un actor puede operar en él. La
    usan cobrar, anular venta, gasto y retiro del cajón (y sus anulaciones), cerrar y escribir
    observaciones. El mensaje del servidor es genérico; la pantalla, que sabe de quién es el turno, dice
    *"El turno abierto es de Carolina"*.
12. **El aviso de venta a pérdida pasa al servidor**: la regla de `utils/venta.js:128-140` se porta al
    dominio de ventas y la pantalla la pide con los renglones y el descuento. El administrador recibe las
    cifras; el cajero, solo si queda por debajo.

---

## Fase 1 · Entrar (P1: H1, H6, H8; RF-001 a RF-007, RF-014, RF-017 propio, RF-018)

| Pieza | Dónde cae |
|---|---|
| `Rol { ADMINISTRADOR, CAJERO }` con `veCostos()`; `Actor(id, nombre, rol)` con `esAdministrador()` y `exigirAdministrador()` | `compartido/dominio/` |
| `NoPermitidoException` (→ 403) | `compartido/dominio/` |
| `Usuario`: usuario (normalizado sin tildes ni mayúsculas), nombre, hash, rol, activo, `debeCambiarContrasena`, `versionSesion`, intentos fallidos y `bloqueadoHasta`. Reglas: `registrarIntentoFallido(ahora)` (5 → 5 minutos), `estaBloqueado(ahora)`, `cambiarContrasena(...)` (mínimo 6), `actor()` | `usuarios/dominio/` |
| `RepositorioUsuarios` (`buscar`, `buscarPorUsuario`, `buscarParaModificar`, `hayUsuarios`, `bloquearAltaDelPrimero`, `guardar`); `Contrasenas` (`hash`, `coincide`) | `usuarios/dominio/puerto/` |
| `CrearPrimerAdministrador` (solo si no hay usuarios, bajo candado), `Entrar` (mismo error para usuario y contraseña; compara un hash de mentira si el usuario no existe, para que no se note por el tiempo), `CambiarContrasena` (pide la actual; sube la versión) | `usuarios/aplicacion/` |
| `RepositorioUsuariosJpa`; `ContrasenasSpring` | `pos/…/usuarios/infraestructura/` |
| `SesionController`: `POST /api/sesion` (entrar: pone la cookie), `DELETE /api/sesion` (salir: la borra), `GET /api/sesion` (quién soy, con rol y si debe cambiar la contraseña), `PUT /api/sesion/contrasena` | `pos/…/usuarios/infraestructura/` |
| `InstalacionController`: `GET /api/instalacion` (¿hay usuarios?), `POST /api/instalacion/administrador` | `pos/…/usuarios/infraestructura/` |
| `ConfiguracionSeguridad` (todo `/api/**` exige sesión menos entrar e instalación; CSRF; 401 y 403 en JSON), `TokenDeSesion` (emitir y leer, cookie), `LectorDeCookie` (`BearerTokenResolver`), `ActorDeLaSesion` (carga al usuario y revisa activo y versión), `ClaveDelToken`, `@ActorActual` para los controladores | `pos/…/compartido/infraestructura/seguridad/` |
| Las 12 acciones dejan de leer `X-Usuario-Id`: reciben el id del actor de la sesión | los 6 controladores del §3 del spec |

**V16:**
- `usuario`: `id`, `usuario` y `usuario_normalizado` (único), `nombre`, `hash`, `rol` (`CHECK`), `activo`,
  `debe_cambiar_contrasena`, `version_sesion`, `intentos_fallidos`, `bloqueado_hasta`, `creado_en`,
  `ultima_entrada`.
- `evento_auditoria`: el `CHECK` de acciones suma `CREAR_USUARIO`, `CAMBIAR_ROL`, `DESACTIVAR_USUARIO`,
  `ACTIVAR_USUARIO`, `RESTABLECER_CONTRASENA` (se reescribe con la lista completa de la última migración).

**Frontend:**
- `api/cliente.js`: fuera `USUARIO_PROVISIONAL` y `X-Usuario-Id`; en las escrituras, `X-XSRF-TOKEN` leído
  de su cookie; un 401 avisa a la app (sin perder la pantalla en la que se estaba). `sesionApi`,
  `instalacionApi`.
- `componentes/sesion/`: `ProveedorDeSesion` (quién está adentro), `Entrar`, `CrearAdministrador`,
  `CambiarContrasena` (obligatoria si `debeCambiarContrasena`; también desde la barra).
- `App.jsx`: sin sesión, *Entrar* (o *Crear el administrador* si no hay usuarios); la barra con nombre, rol,
  *Cambiar contraseña*, *Cambiar de usuario* y *Salir*.
- `utils/sesion.js` + pruebas: qué pantalla toca según la sesión y la instalación; validación de contraseña.
- `http/`: cada archivo empieza entrando; fuera el encabezado `X-Usuario-Id`.

**Pruebas:**
- `UsuarioTest`: 5 intentos → bloqueado 5 minutos, el sexto a los 5 minutos entra; contraseña corta;
  usuario sin tildes ni mayúsculas.
- `EntrarTest`: el mismo error para usuario inexistente, contraseña mal y usuario desactivado; un
  acierto borra los intentos; guarda la última entrada.
- `CrearPrimerAdministradorTest`: con usuarios ya creados, no crea nada; el primero queda administrador
  y no tiene que cambiar la contraseña (la eligió él).
- `SeguridadIntegracionTest` (servidor real en puerto al azar, con Postgres):
  - sin cookie, cualquier `/api/**` → 401; entrar → cookie `HttpOnly` y `SameSite=Strict`, con token de 24 h;
  - **un token con la firma alterada → 401**; **uno vencido → 401**;
  - un `X-Usuario-Id` falso se ignora;
  - una escritura sin `X-XSRF-TOKEN` → 403;
  - el primer administrador dos veces a la vez: queda uno.
- `sesion.test.js`.
- Romper a propósito: aceptar el token sin revisar la firma; quitar el CSRF; aceptar un token vencido.

**Checkpoint:** en QA, el sistema pide entrar; se crea el primer administrador (el del QA del final);
`curl` sin cookie → 401.

---

## Fase 2 · El "quién" de verdad (P1: H2; RF-007, RF-008, RF-021, RF-023)

| Pieza | Dónde cae |
|---|---|
| Los casos de uso que escriben reciben **`Actor`** en vez de `UUID usuarioId`: `AbrirTurno`, `CerrarTurno`, `AnularGasto`, `AnularRetiro`, `RegistrarRetiro`, `AnularVenta`, `AnularCompra`; y los comandos `ComandoRegistrarGasto`, `ComandoRegistrarCompra`, `ComandoCorregirCompra`, `ComandoCobrarVenta` y el de `ActualizarRepuesto` | `*/aplicacion/` de caja, ventas, compras e inventario |
| Las entidades siguen guardando el id (`actor.id()`): no cambian | `*/dominio/` |
| Los controladores reciben `@ActorActual Actor` | los 6 del §3 del spec |

**V17:** llave foránea a `usuario(id)` en todas las columnas de "quién": `compra.registrado_por_id`,
`compra.anulada_por_id`, `movimiento_kardex.registrado_por_id`, `evento_auditoria.usuario_id`,
`turno_caja.abierto_por_id`, `turno_caja.cerrado_por_id`, `venta.vendido_por_id`, `venta.anulada_por_id`,
`gasto.registrado_por_id`, `gasto.anulado_por_id`, `retiro_caja.registrado_por_id`,
`retiro_caja.anulado_por_id` (la lista se confirma contra el esquema al empezar). **Antes de escribirla,
comprobar en QA que no hay filas con un "quién" sin usuario**; con la base limpia del 2026-09-17 no debería
haber ninguna.

**Frontend:**
- `Vender.jsx`: la venta a medias se guarda **por persona** (`rdmotors:venta-en-curso:<id>`). Otra
  persona en el mismo equipo no la hereda; su dueño la encuentra al volver (RF-021). Una mandada sin
  respuesta sigue bloqueada para su dueño.
- `ticket.js`: *"Atendió: <nombre>"* con el nombre de quien vendió (RF-023). La respuesta de la venta
  trae `vendidoPor` (id y nombre).

**Pruebas:**
- Las pruebas del dominio pasan a un `Actor` de prueba (cambio mecánico en `EscenarioCaja`,
  `EscenarioCompras` y las que usan un `UUID` de cajero).
- **Las pruebas contra Postgres crean sus usuarios** (`UsuariosDePrueba`: un administrador y dos cajeros
  por clase), en vez del `UUID.randomUUID()` de hoy (`CajaYVentasIntegracionTest.java:101`,
  `CajaIntegracionTest.java:116` y las demás).
- `MigracionesIntegracionTest`: V17 sobre una base con filas y usuarios; un "quién" inventado → la base lo
  rechaza.
- `SeguridadIntegracionTest`: una venta cobrada con la sesión de Carolina guarda a Carolina aunque el
  navegador mande otro "quién".
- `venta.test.js` (borrador por persona) y `ticket.test.js` (*Atendió*).
- Romper a propósito: guardar el "quién" que llega del navegador → falla la prueba de la sesión.

**Checkpoint:** en QA, una venta guarda al administrador que entró; el comprobante dice *"Atendió"*.

---

## Fase 3 · Roles (P1: H3, H4; RF-009 a RF-012)

| Pieza | Dónde cae |
|---|---|
| `actor.exigirAdministrador()` al empezar cada caso de uso de administrador del §5 del spec: gasto por fuera y categorías de gasto; crear y corregir repuesto; compras (registrar, corregir, anular), proveedores y cuentas; reportes; datos de la tienda | los casos de uso de cada módulo |
| El cajero ve **sus** turnos anteriores y las ventas de **su** turno; el administrador, todos | `ConsultarTurnos`, `ConsultarVentas` |
| El turno con dueño: `TurnoCaja.exigirQuePuedaOperar(actor)` → `TurnoAjenoException` (403 `TURNO_AJENO`). Lo llaman cobrar, anular venta, gasto y retiro del cajón y sus anulaciones, cerrar y observaciones | `caja/dominio/` y esos casos de uso |
| `AvisoDePerdida.calcular(renglones con su costo, descuento)`, portado de `utils/venta.js:128-140`, con sus pruebas | `ventas/dominio/` |
| `RevisarPerdida` (lee los costos por el puerto de variantes); `POST /api/ventas/aviso-de-perdida` → `{ bajoCosto, sinCosto }` y, para el administrador, `costo`, `cobrado` y `diferencia` | `ventas/aplicacion/` · `VentaController` |
| **Sin costos para el cajero** en inventario, ficha, kardex, búsqueda del mostrador, catálogo y resumen del inventario | `InventarioController`, `RepuestoController` (con `Rol.veCostos()`) |
| `GET /api/turnos/abierto` dice de quién es (`abiertoPor`: id y nombre) | `TurnoController` |

**Frontend:**
- Menú y rutas por rol: el cajero no ve *Compras* ni *Reportes* ni el ⚙; si escribe la dirección, sale
  *"Es del administrador"*.
- Inventario y ficha: sin las columnas ni las cifras de costo para el cajero, **por su rol** (decisión 10).
- Vender: el aviso de pérdida lo pide al servidor al cambiar los renglones o el descuento (con un respiro
  de medio segundo); con un turno ajeno, *"El turno abierto es de Carolina: lo cierra ella o un
  administrador"* y no deja vender.
- `utils/permisos.js` + pruebas: qué ve cada rol en el menú y las rutas.

**Pruebas:**
- Por cada caso de uso de administrador, una prueba con un cajero → `NoPermitidoException`, y nada cambia.
- `TurnoCajaTest` / `CobrarVentaTest` / `RegistrarGastoTest` / `CerrarTurnoTest`: otro cajero → turno
  ajeno; el administrador → sí, y queda a su nombre.
- `AvisoDePerdidaTest`: los casos de `venta.test.js` que hoy prueban la regla en el navegador, ahora en el
  dominio.
- `SeguridadIntegracionTest`: **recorre todas las consultas con sesión de cajero y ninguna respuesta
  trae `costo`, `costoPromedio`, `valor` ni `costoTotal`**; y cada escritura de administrador → 403.
- `permisos.test.js`.
- Romper a propósito: quitar un `exigirAdministrador()`; devolver el costo al cajero en una sola consulta;
  quitar la regla del turno ajeno. Cada uno hace fallar su prueba.

**Checkpoint:** con un cajero de prueba creado por SQL en QA: no ve costos ni compras ni reportes; otro
cajero no vende en su turno. (La pantalla de usuarios llega en la fase 4.)

---

## Fase 4 · Administrar usuarios (P1: H5; RF-013 a RF-017, RF-019, RF-020)

| Pieza | Dónde cae |
|---|---|
| Reglas de `Usuario`: `desactivar`, `activar`, `cambiarRol`, `restablecerContrasena` (queda inicial y sube la versión) | `usuarios/dominio/` |
| `CrearUsuario`, `CambiarRol`, `DesactivarUsuario`, `ActivarUsuario`, `RestablecerContrasena`, `ConsultarUsuarios`. **Siempre queda un administrador activo** (bajo candado, para que dos cambios a la vez no dejen cero); nadie se desactiva a sí mismo. Todo auditado | `usuarios/aplicacion/` |
| `UsuarioController`: `GET/POST /api/usuarios`, `PUT /api/usuarios/{id}/rol`, `POST …/desactivacion`, `POST …/activacion`, `POST …/restablecimiento` | `pos/…/usuarios/infraestructura/` |
| **Restablecer desde el servidor (RF-019):** arrancar con `--rdmotors.restablecer-administrador=<usuario>` pone una contraseña temporal, la muestra en la consola, obliga a cambiarla y termina | `pos/…/usuarios/infraestructura/` |

**V18:** un índice parcial de administradores activos, para contar rápido y bloquear.

**Frontend:**
- *Usuarios* (en el ⚙, solo administrador): lista con rol, activo y última entrada; crear;
  cambiar rol; desactivar y activar; restablecer contraseña (muestra la temporal una sola vez).
- `utils/usuarios.js` + pruebas.

**Pruebas:**
- `UsuariosTest`: crear uno repetido; el último administrador no se desactiva ni pasa a cajero; uno mismo
  no se desactiva; restablecer obliga a cambiar; cada acción queda auditada con quién.
- `SeguridadIntegracionTest`: desactivar a Carolina **con su sesión abierta** → su siguiente petición es 401;
  restablecerle la contraseña → su sesión vieja no sirve; dos administradores se quitan el rol a la vez →
  queda uno.
- La prueba del arranque que restablece.
- `usuarios.test.js`.
- Romper a propósito: no revisar la versión del token → la prueba de la sesión vieja falla.

**Checkpoint:** en QA, el administrador crea a un cajero, entra con él (le pide cambiar la contraseña), lo
desactiva y la sesión del cajero se cae.

Queda documentado en `docs/DESARROLLO.md` y en la guía de instalación: la clave del token, el primer
administrador y cómo restablecer al único administrador.

---

## Fase 5 · Ver quién y registro de entradas (P2: H7, H9; P3: H10; RF-022, RF-024, RF-025)

| Pieza | Dónde cae |
|---|---|
| Los detalles traen el nombre de quién: venta (vendió, anuló), turno (abrió, cerró), gasto y retiro (registró, anuló), compra (registró, anuló) y el rastro de correcciones. Una sola consulta de nombres por respuesta (`nombresDe(ids)`) | puerto `RepositorioUsuarios` · `Detalle*` de cada módulo |
| **Registro de entradas (P3):** cada intento de entrar, con usuario escrito, si entró, cuándo y desde qué equipo (IP y navegador). Solo el administrador lo ve | `usuarios/…` · V19 |

**V19:** `entrada` (`usuario_id` si existe, `usuario_escrito`, `exito`, `momento`, `ip`, `navegador`) con
índice por fecha.

**Frontend:** los nombres en *Detalle de venta*, *Detalle de turno*, *Gastos*, *Retiros* y *Detalle de
compra*; *Usuarios › Entradas* para el administrador.

**Pruebas:**
- Cada detalle con dos personas distintas dice los dos nombres.
- La consulta de nombres es una sola por respuesta.
- Un intento fallido queda en el registro sin decir si el usuario existe.

**Checkpoint:** en QA, el detalle de una venta anulada por otra persona dice los dos nombres.

---

## Riesgos

| Riesgo | Fase | Qué hacer |
|---|---|---|
| La API de Spring Security 7 para CSRF en páginas de una sola pantalla no es la que se espera | 1 | Se verifica al empezar; si no está, se arma con `CookieCsrfTokenRepository` y un manejador propio. Se anota en la bitácora |
| La cookie de sesión no llega desde la tablet por la red local | 1 | Es mismo origen (Vite reenvía `/api` al servidor), así que viaja; se prueba en el checkpoint entrando desde la IP de la red |
| Cambiar la firma de ~15 casos de uso rompe muchas pruebas | 2 | Es mecánico y va en su propia fase; las pruebas se ajustan en el mismo cambio, sin tocar lo que prueban |
| Las pruebas contra Postgres dejan de pasar por la llave foránea | 2 | `UsuariosDePrueba` en cada clase, antes de la migración |
| El cajero ve "Sin costo" en vez de no ver la columna | 3 | Decisión 10: se esconde por rol, con prueba |
| Una consulta nueva olvida quitar el costo | 3 y después | La prueba que recorre todas las consultas; una consulta nueva entra sola al recorrido |
| Se pierde el archivo de la clave del token | 1 | Todos vuelven a entrar; nada más. Documentado |
| La contraseña viaja sin cifrar por el Wi-Fi de la tienda | todas | Declarado en el spec (§11). Recomendación: el Wi-Fi del mostrador no es el de los clientes |

---

## Verificación final

1. Backend abajo, `./mvnw clean test`.
2. `cd frontend && npx eslint src/ && node --test src/utils/*.test.js && npx vite build`.
3. Los criterios del §9 del spec, uno por uno, con un administrador y dos cajeros en QA, en el computador y
   desde el celular por la red de la tienda (claro, oscuro y 390 px).
4. Romper a propósito lo de cada fase y ver fallar su prueba.
5. `http/` al día: cada archivo entra primero.

---

## Bitácora de decisiones

Se llena **durante** la implementación, con fecha, fase, decisión y porqué.

| Fecha | Fase | Decisión | Por qué |
|---|---|---|---|
| 2026-09-19 | 1 | **La sesión se lee con un filtro propio (`FiltroDeSesion`) y no con `oauth2ResourceServer()` de Spring.** Usa el mismo decodificador firmado de Spring (Nimbus, HS256) | **Lo encontró la prueba de CSRF**: el configurador del *resource server* supone que el token viaja en el encabezado `Authorization` y apaga la protección CSRF en toda petición que traiga token. Aquí viaja en una cookie que el navegador manda solo, así que esa exención dejaba pasar una escritura sin token anti-CSRF (abrió un turno). Con el filtro propio se exigen los dos |
| 2026-09-19 | 1 | **La cookie de sesión no se lee en las rutas públicas** (entrar, salir, instalación) | Una cookie vencida o rota haría fallar la petición antes de llegar: con una sesión vieja no se podría ni volver a entrar |
| 2026-09-19 | 1 | **La contraseña obligatoria se exige en el servidor**: quien la tiene pendiente recibe el permiso `CONTRASENA_TEMPORAL` y solo puede ver quién es, cambiarla y salir; lo demás responde 403 `DEBE_CAMBIAR_CONTRASENA` | RF-014 dice "no se puede hacer otra cosa": esconder la app en la pantalla no alcanza |
| 2026-09-19 | 1 | **Entrar guarda los intentos fallidos aunque responda error** (`noRollbackFor`) y compara contra un hash de mentira si el usuario no existe | Sin lo primero, la transacción deshacía el intento y el bloqueo nunca llegaba. Sin lo segundo, lo que tarda la respuesta diría qué usuarios existen |
| 2026-09-19 | 1 | **Códigos distintos**: 401 `SIN_SESION` (la app vuelve a *Entrar*), 401 `CREDENCIALES` (usuario o contraseña mal), 429 `BLOQUEADO`, 409 `YA_INSTALADO`, 403 `CSRF`, `DEBE_CAMBIAR_CONTRASENA` y `NO_PERMITIDO` | La pantalla reacciona por el código: una contraseña mal no puede llevar a la pantalla de entrar como si la sesión hubiera vencido |
| 2026-09-19 | 1 | **Cambiar la contraseña sube la versión y devuelve una cookie nueva** | Los tokens que la persona tenía en otros equipos dejan de valer; quien la cambió sigue adentro |
| 2026-09-19 | 1 | **La clave de firma de las pruebas va en `pos/src/test/resources/config/application.properties`** | Spring la suma a la configuración principal sin reemplazarla, y las pruebas no crean `~/.rdmotors/clave-token` en la máquina que las corre |
| 2026-09-19 | 1 | **La página reintenta una vez si le falta la cookie anti-CSRF**, y ante `SIN_SESION` vuelve a *Entrar* sin cambiar de dirección | La primera escritura de una pestaña nueva podía no tener todavía la cookie; y al volver a entrar se sigue donde se estaba |
| 2026-09-19 | 1 | **Cierre de la fase.** Suite completa en verde (353 del dominio, 85 contra Postgres). Romper a propósito, las 4 fallan su prueba: aceptar un token vencido, quitar CSRF, no revisar la versión de la sesión, leer la cookie en las rutas públicas | — |
| 2026-09-19 | 1 | **El primer administrador de QA no lo creo yo: queda para el QA final.** QA arrancó con la V16 y responde `faltaAdministrador: true`; sin cookie todo da 401 (también por el proxy de Vite) y una escritura sin token anti-CSRF, 403 | Crearlo exigía inventar una contraseña por el dueño, y esa pantalla es justo lo que él tiene que probar. Las capturas de *Entrar*, *Crear el administrador*, la contraseña obligatoria y el menú se tomaron con las respuestas de `/api` simuladas dentro del navegador: QA no se tocó |
| 2026-09-19 | 2 | **Los comandos llevan el `Actor` entero** (no solo su id) y los casos de uso sin comando lo reciben como parámetro; las entidades siguen guardando el id | Es lo que la fase 3 necesita para `exigirAdministrador()` y la regla del turno sin volver a tocar las firmas |
| 2026-09-19 | 2 | **V17 sin índices nuevos**, y las pruebas de migraciones de V3 a V16 migran solo hasta la V16 | Las llaves foráneas solo pedirían índice para borrar usuarios, y los usuarios no se borran: se desactivan. Esas pruebas insertan filas con un "quién" inventado, como eran antes; la V17 tiene su propia prueba con usuarios de verdad. QA no tenía filas huérfanas (estaba vacía) |
| 2026-09-19 | 2 | **`nombresDe(ids)` se adelantó de la fase 5**, solo para las ventas; y la respuesta de la venta cambia `vendidoPorId` por `vendidoPor: { id, nombre }` (valor `Persona` en `compartido/dominio`) | El comprobante necesita el nombre ya (RF-023), también al reimprimir desde el detalle. Una consulta por respuesta, no una por venta. La pantalla no usaba `vendidoPorId` |
| 2026-09-19 | 2 | **Las pruebas contra Postgres usan `UsuariosDePrueba`** (traída con `@Import`, sin `@Component`), y el cajero de cada clase llega por el constructor | Con la V17 la base rechaza un "quién" al azar. Sin `@Component` el escaneo no la registra dos veces. Por el constructor porque JUnit no garantiza el orden entre dos `@BeforeEach`, y uno de ellos abre el turno con el cajero |
| 2026-09-19 | 2 | **El borrador viejo (`rdmotors:venta-en-curso`, sin persona) ya no se lee** | Una venta a medias de antes de los usuarios no es de nadie: dársela al primero que entre es justo lo que prohíbe RF-021. QA está limpia, así que no se pierde nada real |
| 2026-09-19 | 2 | **Cierre de la fase.** Dominio 353, contra Postgres 87 (V17 y la venta a nombre de quien entró, nuevas), pantalla 220. Romper a propósito, las 5 fallan su prueba: guardar el "quién" del navegador, V17 sin una llave, el detalle sin el nombre, el borrador compartido, el comprobante sin *Atendió*. QA quedó en la V17 con sus 12 llaves | El checkpoint "una venta en QA guarda al administrador" queda para el QA final (QA no tiene administrador todavía); lo cubre de punta a punta `SeguridadIntegracionTest.laVentaEsDeQuienEntro`, por HTTP |
| 2026-09-19 | 2 | **Arreglado de paso: la vista previa de *Datos de la tienda* salía en blanco** (`Tienda.jsx` le pasaba `ticket=` al componente `Ticket`, que desde el spec 0006 recibe el `html`) | Se encontró al revisar *Atendió* en esa vista previa. No lo había causado este plan |
| 2026-09-19 | 3 | **El bloqueo va en el caso de uso; donde no hay caso de uso, en el controlador con la misma regla** (`actor.exigirAdministrador()`). Solo un sitio: la lista de proveedores, que el controlador lee directo del puerto | Crear un caso de uso solo para reenviar al repositorio es la ceremonia que la bitácora del plan 0001 ya descartó. La prueba HTTP que recorre todo lo del administrador con un cajero cubre los dos sitios |
| 2026-09-19 | 3 | **La lista de cuentas la lee cualquiera que haya entrado**; crearlas y desactivarlas es del administrador | Son nombres ("Nequi del dueño"), no costos, y el formulario de gastos del cajero la carga. Los proveedores sí quedan del administrador: solo los usa Compras |
| 2026-09-19 | 3 | **Al cajero, el servidor le responde con registros sin los campos de costo**, no con los mismos campos en `null` | Una prueba que busque la clave `costoPromedio` no se engaña con un `null`, y un cambio futuro que llene el campo no lo filtra sin querer. La pantalla esconde las columnas por el rol que dice la sesión (`veCostos`), no por si llega el dato |
| 2026-09-19 | 3 | **El kardex del cajero va sin costos y sin los documentos de compra** (proveedor, factura, enlace a la compra) | Las compras son del administrador; el enlace llevaría a "Es del administrador" |
| 2026-09-19 | 3 | **El total de una compra pagada con el cajón sigue saliendo en el turno del cajero** | Es plata que salió de su cajón: sin ella no cuadra el arqueo. No lleva renglones ni costos unitarios |
| 2026-09-19 | 3 | **El turno ajeno responde 403 `TURNO_AJENO` con el nombre, y el texto no supone "ella" o "él"**: *"El turno abierto es de Carolina: lo cierra Carolina o un administrador"* (el spec decía "lo cierra ella") | El sistema no sabe si la persona es él o ella. El nombre lo pone el manejador de errores: el turno solo conoce el id |
| 2026-09-19 | 3 | **El aviso de pérdida se pide al servidor medio segundo después del último cambio, y se conserva el anterior mientras llega**; si el servidor no responde, no hay aviso | Sin el respiro, una pregunta por tecla. Sin conservarlo, el aviso parpadea al sumar unidades. El cobro no depende del aviso |
| 2026-09-19 | 3 | **Arreglado de paso: un comentario de `ManejadorDeErrores` quedó separado de su método en la fase 1** | Al meter el manejador de `NoPermitido` se partió el Javadoc del de `ReglaDeNegocio` |
| 2026-09-19 | 3 | **Ojo con la extensión de Java del editor: compila en el mismo `target/`**. Un `test-compile` de Maven "pasó" con clases que el editor había dejado a medias. Para verificar, siempre `clean` | Lo encontró una corrida que falló con "Unresolved compilation problems", un mensaje que Maven no produce |
| 2026-09-19 | 3 | **Cierre de la fase.** Desde cero (`clean install`): dominio 369, contra Postgres 90, pantalla 226 y el build. Romper a propósito, las 4 fallan su prueba: quitar un `exigirAdministrador()`, devolverle el costo al cajero, quitar la regla del turno ajeno, dejar que el cajero abra cualquier dirección. Capturas con sesión de cajero simulada: menú, inventario y ficha sin costos, turno ajeno, "Es del administrador", aviso de pérdida sin cifras | El checkpoint con un cajero en QA queda para el QA final: crearlo exige la pantalla de usuarios (fase 4) o escribir en QA. Lo cubren `SeguridadIntegracionTest` (13) por HTTP y las capturas |
| 2026-09-20 | 4 | **Desactivar y cambiar de rol suben la versión de la sesión**, aunque para desactivar bastara con `activo = false` | Al reactivar a alguien, la cookie que tenía antes no revive; y quien cambia de rol vuelve a entrar y ve las pantallas de su rol nuevo, en vez de una pantalla vieja llena de "no permitido". Lo encontró el romper a propósito: la prueba no cubría la versión, y se agregó |
| 2026-09-20 | 4 | **Desactivar o activar a alguien que ya estaba así no es un error**: no cambia nada y no queda en la auditoría | Un doble clic no puede dejar dos renglones en la auditoría ni un 422 sin sentido |
| 2026-09-20 | 4 | **La contraseña temporal la inventa el servidor** (8 caracteres, sin `l`, `1`, `o`, `0` ni `i`) y viaja **una sola vez**, en la respuesta de restablecer | Se dicta por teléfono o se lee en una pantalla: los caracteres que se confunden sobran. No se guarda en claro: si se cierra la ventana, se restablece otra vez |
| 2026-09-20 | 4 | **El candado de administradores es otro** (`pg_advisory_xact_lock(4005)`), aparte del alta del primero (4004) | Crear el primer administrador y cambiar roles no se frenan entre sí |
| 2026-09-20 | 4 | **Restablecer desde la tienda pide el servidor apagado**: el arranque con `--rdmotors.restablecer-administrador=<usuario>` levanta el puerto 8081 | Si está arriba, el arranque falla por el puerto, no a medias. Queda documentado en `docs/DESARROLLO.md` |
| 2026-09-20 | 4 | **Cierre de la fase.** Desde cero: dominio 379, contra Postgres 92, pantalla 233. Romper a propósito, las 5 fallan su prueba. Capturas de *Usuarios* (lista, crear, restablecer y la temporal) con respuestas simuladas | El checkpoint contra QA —crear un cajero, entrar con él y desactivarlo— es parte del QA final: la base de QA sigue sin administrador a propósito |
| 2026-09-20 | 5 | **Los detalles llevan `Persona` (id y nombre) en vez del id suelto**: venta (cobró, anuló), turno (abrió, cerró), gasto y retiro (registró, anuló), compra (registró, anuló) y los dos rastros de auditoría | El id no le dice nada a nadie. Cada consulta pide los nombres **una sola vez por respuesta** (`nombresDe`), y una prueba cuenta esas llamadas: un turno con veinte movimientos no puede ser veinte consultas |
| 2026-09-20 | 5 | **El rastro de auditoría se muestra como `CambioAuditado`** (acción, cuándo, quién, motivo, antes y después), no como el `EventoAuditoria` del dominio | Es lo mismo que ya se enseñaba, pero con el nombre. Sirve igual para el rastro de una compra y para el de la ficha de un repuesto |
| 2026-09-20 | 5 | **El registro de entradas guarda el usuario tal cual se escribió**, aunque no exista nadie así (ahí `usuario_id` va vacío), y nunca la contraseña | Un montón de intentos con "admin" de madrugada es justo lo que hay que poder ver. La IP y el navegador se toman de la conexión, no de un encabezado que cualquiera escribe |
| 2026-09-20 | 5 | **Un intento fallido también queda registrado**, aprovechando el `noRollbackFor` que ya tenía `Entrar` | Si el intento se deshiciera con la transacción, el registro solo tendría las entradas buenas: justo las que no interesa vigilar |
| 2026-09-20 | 5 | **Cierre de la fase y del plan.** Desde cero: dominio 385, contra Postgres 94, pantalla 233 y el build. Romper a propósito, las 4 fallan su prueba | Falta el QA del usuario, que se hará sobre QA completo |
| 2026-09-20 | 5 | **Repaso final contra el §9 del spec:** faltaba mostrar en el detalle de la compra quién la registró y quién la anuló (el servidor ya los mandaba). Agregado | Los criterios de aceptación se revisaron uno por uno; el resto ya estaba cubierto con pruebas |
