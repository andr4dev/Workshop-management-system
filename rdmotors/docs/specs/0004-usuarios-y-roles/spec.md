# Spec 0004 — Usuarios, entrada y roles

**Estado:** cerrado · aprobado por el usuario el 2026-09-19, con la sesión que pidió (ver §4) · [plan](plan.md)
**Rebanada:** 2 y 3 (lo que falta para usar el sistema en la tienda) · ver [`PLAN_DE_TRABAJO.md`](../../PLAN_DE_TRABAJO.md)
**Depende de:** specs [0003](../0003-venta-de-mostrador/spec.md), [0006](../0006-cierre-de-caja/spec.md) y [0007](../0007-reportes-de-resultados/spec.md), implementados
**Va antes de:** usar el sistema en la tienda (orden confirmado el 2026-09-16).

> **Convención:** lo que dice *"hoy"* está verificado con `archivo:línea`. Lo que dice *"debe"* es
> propuesta y todavía no existe. Las dos cosas nunca se mezclan.

---

## 1. Objetivo de negocio

Hoy el sistema no sabe quién lo está usando. Cada venta, anulación, gasto, retiro y cierre de caja
guarda un "quién", pero ese "quién" es **el mismo número fijo para todos**, y lo manda el navegador.
Con eso:

- **El arqueo no señala a nadie.** El cliente pidió que *"el cajero responde por su turno"*, y hoy no
  hay forma de saber de quién es un turno.
- **La auditoría no sirve.** Una anulación queda registrada "con motivo", pero no se sabe quién la hizo.
- **Cualquiera ve todo.** Cualquier persona que abra el sistema ve costos, márgenes y la ganancia del
  negocio, y puede cambiar precios, anular compras o cerrar la caja.

El cliente lo pidió como P1: *"Multiusuario con roles"* y *"usuario y contraseña por persona y rol"*
(`SPEC_Sistema_Ventas_Repuestos (3).md:172,243`).

Este spec resuelve tres cosas:
1. **Cada persona entra con su usuario y contraseña**, y el sistema sabe quién hizo cada cosa.
2. **El cajero y el administrador no pueden lo mismo**, y el bloqueo es real (no solo esconder botones).
3. **El turno tiene dueño**: el cajero responde por lo que pasó en su turno.

---

## 2. Caso de uso

**Ejemplo que atraviesa todo el spec.** RD MOTORS tiene a **don Rubén** (el dueño, administrador) y a
**Carolina** (cajera).

- A las 8:00 a. m. Carolina entra con su usuario, abre su turno con $100.000 de fondo y vende toda la
  mañana. Da un descuento a un cliente frecuente y anula una venta que cobró dos veces.
- A mediodía don Rubén entra desde el computador de atrás: registra la factura de Jotapartes, sube el
  precio de dos repuestos y mira el reporte del mes.
- Carolina intenta abrir *Reportes*: no aparece en su menú, y si escribe la dirección, el sistema le dice
  que es del administrador.
- A las 6:00 p. m. Carolina cierra su turno: faltan $1.400. El cierre dice **"Turno de Carolina · faltaron
  $1.400"**, y la venta anulada dice **"Anuló: Carolina · motivo: se cobró dos veces"**.

### P1 — bloquean

**H1 · Entrar y salir.**
Carolina abre el sistema y lo primero que ve es *Entrar*: usuario y contraseña. Al terminar, *Salir*.
*Se demuestra solo:* sin entrar no se ve ni se puede hacer nada; con la contraseña mal, no entra.

**H2 · El sistema sabe quién hizo cada cosa.**
La venta dice quién la vendió; la anulación, quién la anuló; el turno, quién lo abrió y quién lo cerró; el
gasto, quién lo registró. **Lo decide el servidor por la persona que entró, no el navegador.**
*Se demuestra solo:* una venta de Carolina dice "Carolina"; una de don Rubén, "Rubén".

**H3 · El cajero no puede lo que es del administrador.**
Carolina no ve reportes ni costos, no cambia precios, no registra ni anula compras y no administra
usuarios. Si lo intenta por fuera de la pantalla, el servidor se lo niega.
*Se demuestra solo:* cada acción de administrador, pedida con la sesión de Carolina, responde "no
permitido" con el porqué, y no cambia nada.

**H4 · El turno tiene dueño.**
El turno que abrió Carolina es de Carolina: ella vende, registra gastos y retiros, y lo cierra. Otro
cajero no puede vender en su turno. Don Rubén, como administrador, sí puede ayudar en el mostrador y
cerrarlo si ella no está.
*Se demuestra solo:* con el turno de Carolina abierto, otro cajero ve "El turno abierto es de Carolina" y
no puede vender.

**H5 · El administrador maneja los usuarios.**
Don Rubén crea a Carolina (nombre, usuario, rol y una contraseña inicial), la desactiva si se va de la
tienda, y le restablece la contraseña si la olvida.
*Se demuestra solo:* Carolina desactivada no puede entrar, y sus ventas siguen diciendo "Carolina".

**H6 · El primer administrador.**
Al instalar el sistema no hay nadie. La primera vez, la pantalla de entrar ofrece **crear el
administrador**. Después de eso, esa opción desaparece para siempre.
*Se demuestra solo:* con la base vacía se crea el primer administrador; con uno ya creado, la opción no
existe ni en la pantalla ni en el servidor.

### P2 — importantes, no bloquean

**H7 · Ver quién, en pantalla y en el comprobante.**
El detalle de la venta, del turno, del gasto, del retiro y de la compra dice el nombre de quien hizo cada
cosa. El comprobante impreso dice *"Atendió: Carolina"*.

**H8 · Contraseñas.**
Cada uno cambia la suya. La contraseña inicial y la restablecida se cambian obligatoriamente al entrar.

**H9 · Cambiar de usuario en el mostrador.**
Don Rubén quiere cobrar una venta mientras Carolina almuerza: *Cambiar de usuario*, entra él, y al
volver Carolina encuentra **su** venta a medias donde la dejó.

### P3 — deseables

**H10 · Registro de entradas.** El administrador ve quién entró, cuándo, desde qué equipo, y los
intentos fallidos.

---

## 3. Qué existe hoy

| Qué | Dónde | Estado |
|---|---|---|
| El navegador manda un "quién" fijo en cada escritura | `frontend/src/api/cliente.js:9-11` (`USUARIO_PROVISIONAL = '1111…'`) y `:57` (encabezado `X-Usuario-Id`) | **provisional**, marcado *"NO puede quedar así"* |
| 12 acciones leen ese encabezado: abrir y cerrar turno, cobrar y anular venta, registrar y anular gasto y retiro, registrar, corregir y anular compra, corregir repuesto | `TurnoController.java:55,101` · `VentaController.java:50,91` · `GastoController.java:50,95` · `RetiroController.java:37,51` · `CompraController.java:68,138,148` · `RepuestoController.java:77` | **funciona**, pero cualquiera puede mandar el "quién" que quiera |
| Las consultas (inventario, reportes, historiales, turnos) no piden nada | todos los `@GetMapping` | **abiertas** |
| El servidor escucha en toda la red local, para la tablet y el celular | `pos/src/main/resources/application.properties:29-30` (`server.address=0.0.0.0`) | **hoy cualquiera conectado al Wi-Fi de la tienda llega a la API** |
| Columnas de "quién", sin tabla de usuarios detrás | `vendido_por_id`, `anulada_por_id` (V9); `abierto_por_id`, `cerrado_por_id` (V8); `registrado_por_id`, `anulado_por_id` (V1, V7, V12, V13); `usuario_id` de auditoría (`V5__auditoria.sql:12`) | **existen**, sin llave foránea: no hay tabla `usuario` |
| El modelo de datos ya previó la tabla | `SPEC_Modelo_Datos.md:503` (`usuario`: usuario, hash, nombre, rol ADMIN/CAJERO, activo) · `:597` (*"un ADMIN inicial"*) | **diseñado, no construido** |
| Ajustes de inventario, solo del administrador | `SPEC_Modelo_Datos.md:756-759` | **decidido**; los ajustes todavía no existen |
| Un solo turno abierto en toda la tienda | `V8__turno_de_caja.sql:11,29` (`abierto_por_id`, índice único de turno abierto) | **funciona**; el turno no tiene dueño real |
| La búsqueda del mostrador y el catálogo traen el costo de cada repuesto | `BuscadorVenta.jsx:36` y `Catalogo.jsx:60` usan `GET /api/inventario`, que responde `costoPromedio` (`RepuestoController.java:214,221`) | **el cajero recibe los costos** aunque no se vean en pantalla |
| El aviso de venta a pérdida se calcula en el navegador con esos costos | `frontend/src/utils/venta.js:128-140` | **funciona**; depende de que el navegador tenga el costo |
| El comprobante tiene preparada la línea *"Atendió"* | `frontend/src/utils/ticket.js:34-37,127` | **espera este spec**: hoy no sale |
| La venta a medias se guarda en el navegador, una por equipo | `frontend/src/paginas/Vender.jsx:24-31` (`CLAVE_BORRADOR`) | **funciona**; no distingue personas |
| Ninguna pantalla muestra quién hizo algo | (ningún `.jsx` usa `vendidoPorId`, `abiertoPorId`, `registradoPorId`) | **no existe** |
| Las pruebas contra Postgres usan un "quién" inventado al azar | `CajaYVentasIntegracionTest.java:101`, `CajaIntegracionTest.java:116` | **funciona**; ver *Con qué choca* |
| El car-wash ya resolvió la entrada, con token en el navegador | `CAR-WASH-SYSTEM/backend/.../security/` (`JwtService`, `SecurityConfig`) · `frontend/src/api/apiClient.js:31` (`localStorage.getItem('token')`) | **referencia**; ver RNF de seguridad |

### Lo que pidió el cliente

| Qué dijo | Dónde |
|---|---|
| *"Administradores y cajero"* · *"Cajero responde por su turno, sistema de caja"* | `SPEC_Sistema_Ventas_Repuestos (3).md:39-40` |
| *"un solo cajero estará al mismo tiempo"* · *"1 (cajero) + acceso posterior del admin"* | `:34`, `:280` |
| *"Usuario y contraseña por persona y rol"* | `:172` |
| Cerrar caja: *"Cajero y admin"* | `:113` |
| Descuentos: *"el cajero tiene que autorizarlos"* | `:65` |
| Anulaciones: *"cajero pero que quede historial o constancia auditoría"* | `:217` |
| El propietario tiene **otro** login, en la nube, separado del local | `:139-140` |

### Con qué choca

- **Con la red de la tienda.** El servidor está abierto a la red local para que la tablet y el celular
  entren (`application.properties:29-30`). Sin entrada, cualquier celular conectado al Wi-Fi de la tienda
  (un cliente, un vecino) puede leer los costos y la ganancia, o registrar un gasto. Este spec cierra eso.
- **Con los costos en la venta.** Hoy el navegador del cajero recibe el costo de cada repuesto para
  calcular el aviso de venta a pérdida. Si el cajero no debe ver costos (decisión 1), **no basta con
  esconderlos**: el servidor tiene que dejar de mandarlos, y el aviso de pérdida tiene que decidirlo el
  servidor.
- **Con el turno único.** Hoy hay un solo turno abierto en toda la tienda y no tiene dueño real. Darle
  dueño cambia quién puede vender en él (decisión 2).
- **Con la venta a medias.** El borrador es uno por equipo: si Carolina deja una venta a medias y entra
  don Rubén en el mismo computador, hoy la heredaría y la cobraría a su nombre.
- **Con las pruebas.** Las pruebas contra Postgres inventan un "quién" al azar. Si la base exige que cada
  "quién" sea un usuario que existe, esas pruebas tienen que crear usuarios primero.
- **Con los datos que ya hay: nada.** La base de QA se limpió el 2026-09-17 y está vacía; ninguna fila
  guarda el "quién" provisional. Hacer que cada "quién" apunte a un usuario real **cuesta poco ahora**, y
  costaría una migración de datos después.

---

## 4. Las decisiones

### Resueltas con el usuario (2026-09-19)

| Qué | Resolución |
|---|---|
| Decisión 1 · ¿El cajero ve costos? | **A**: no ve costos ni ganancia en ninguna parte |
| Decisión 2 · ¿Quién opera en un turno abierto? | **A**: el cajero que lo abrió, y el administrador |
| Decisión 3 · ¿Cuánto dura la sesión? | **Cambia la recomendación**: la sesión es un **token firmado que vence a las 24 horas**. El token no lo maneja la página: viaja solo, en una cookie que el código de la página no puede leer. Las contraseñas se guardan con hash. Ver RF-005 y §8 |
| Decisión 4 · El primer administrador | **A**: la primera vez, la pantalla ofrece crearlo |
| ¿El cajero ve los turnos anteriores de otros? | No: solo los suyos (§5) |
| ¿Desde dónde se entra? ¿Se puede vender desde el celular? | Ver §8, *Desde dónde se entra* |

### Decisión 1 · ¿El cajero ve costos? — la que cambia el alcance

El cajero necesita el precio y el stock para vender. ¿Necesita el **costo**?

| Opción | Qué implica |
|---|---|
| **A. El cajero no ve costos ni ganancia en ninguna parte** | El servidor no le manda costos en ninguna respuesta: inventario, ficha, kardex, búsqueda del mostrador ni catálogo. Compras y reportes son del administrador. **El aviso de venta a pérdida sigue**, pero sin cifras (*"el total queda por debajo de lo que costaron los repuestos"*) y lo decide el servidor. Toca casi todas las pantallas de consulta y la venta |
| B. El cajero ve costos en inventario y en la venta (como hoy), pero no reportes ni compras | Toca poco: se cierran reportes, compras y administración. Pero con el costo y el precio a la vista, el cajero calcula el margen de cada repuesto él mismo |
| C. El cajero ve todo menos los reportes | Casi no toca nada. Es lo que hay hoy con un candado en *Reportes* |

**Recomendación: A.** En un almacén de repuestos el costo es lo que el dueño negoció con el proveedor, y
el margen es lo que decide cuánto descuento se puede dar; es la información que el cliente quiere
proteger cuando pide *"qué NO debe ver cada uno"* (`SPEC_Sistema_Ventas_Repuestos (3).md:30`). Con B, el
candado de *Reportes* no protege nada que el cajero no pueda sacar de la ficha de cada repuesto. El
aviso de pérdida se conserva, así que el cajero sigue protegido de vender a pérdida sin saber cuánto.

**Resuelto el 2026-09-19: A.** Si el cajero tiene que recibir mercancía cuando el dueño no está, la
compra la registra el administrador después: una compra es precio de costo.

### Decisión 2 · ¿Quién puede operar en un turno abierto?

| Opción | Qué implica |
|---|---|
| **A. El cajero que lo abrió, y el administrador** | Otro cajero no puede vender, registrar gastos ni cerrar en ese turno. El administrador sí (ayuda en el mostrador, o lo cierra si el cajero se fue). Cada venta dice quién la hizo; el turno dice de quién es |
| B. Cualquiera que haya entrado | Más flexible, pero el cajero respondería por plata que movió otro |

**Recomendación: A.** Es literalmente lo que pidió el cliente: *"el cajero responde por su turno"*
(`:40`). Con un solo cajero a la vez (`:34`), la restricción casi nunca estorba, y la excepción del
administrador cubre el caso real (don Rubén cobra mientras Carolina almuerza). **Cambio de turno sigue
siendo cierre y apertura**, como ya se decidió (`SPEC_Modelo_Datos.md:432`).

### Decisión 3 · ¿Cuánto dura la sesión? — [RESUELTO] token firmado de 24 horas

| Opción | Qué implica |
|---|---|
| **A. Hasta *Salir*, o 12 horas** | Un turno completo sin volver a escribir la contraseña. Si el computador queda solo, alguien podría usar la sesión abierta |
| B. Se cierra tras 15 minutos sin uso | Más seguro, pero el cajero escribe la contraseña varias veces al día, con el cliente esperando |

**Recomendación: A**, con *Cambiar de usuario* siempre a mano. En un mostrador donde casi siempre está
la misma persona, pedir la contraseña cada rato se vuelve pegar la contraseña en un papel junto al
teclado. Y la venta a medias se guarda: si la sesión se acaba, no se pierde nada.

**Resolución del usuario (2026-09-19):** la sesión dura **24 horas**, como un token firmado por el
servidor: nadie puede fabricarlo ni alargarlo sin la clave de la tienda. El token no se manda a mano en
cada petición ni lo guarda la página; va en una cookie que la página no puede leer.

### Decisión 4 · ¿Cómo nace el primer administrador?

| Opción | Qué implica |
|---|---|
| **A. La primera vez, la pantalla ofrece crearlo** | Solo mientras no exista ningún usuario. Quien instala el sistema lo crea en el momento |
| B. Viene sembrado con una contraseña conocida | Más simple, pero en muchos sistemas instalados esa contraseña nunca se cambia |

**Recomendación: A.** Una contraseña por defecto que se olvida cambiar es la puerta más común. La
ventana de riesgo de A es solo el rato entre instalar y crear el administrador, y lo hace quien instala.

---

## 5. Qué puede cada rol (propuesta, con la decisión 1 en A)

Dos roles fijos: **Administrador** y **Cajero**. No hay permisos sueltos por persona (ver *Fuera de alcance*).

| Acción | Cajero | Administrador |
|---|:---:|:---:|
| Entrar, salir, cambiar su contraseña | ✓ | ✓ |
| **Vender** (buscar, catálogo, descuento con motivo, cobrar, reimprimir) | ✓ en su turno | ✓ en cualquier turno |
| **Anular una venta** (con motivo, auditada) | ✓ en su turno | ✓ |
| Abrir turno | ✓ | ✓ |
| Cerrar turno | ✓ el suyo | ✓ cualquiera |
| Gasto del cajón, retiro (y anularlos) | ✓ en su turno | ✓ |
| Gasto por fuera del cajón (arriendo por Nequi), categorías de gasto | — | ✓ |
| Ver inventario: existencias y precio | ✓ | ✓ |
| Ver costos, valor del inventario, kardex con costos | — | ✓ |
| Crear repuesto, corregir su ficha (precio, nombre, categoría) | — | ✓ |
| Compras: registrar, corregir, anular; proveedores y cuentas | — | ✓ |
| Ventas del turno | ✓ su turno | ✓ cualquier turno |
| Turnos anteriores y sus cierres | ✓ los suyos | ✓ todos |
| Reportes (resultados y gastos) | — | ✓ |
| Datos de la tienda (lo que sale en el comprobante) | — | ✓ |
| Usuarios | — | ✓ |
| Ajustes de inventario (cuando existan) | — | ✓ |

El cajero ve solo sus turnos anteriores (resuelto el 2026-09-19).

---

## 6. Requisitos funcionales

### Entrar

- **RF-001** · Se entra con **usuario y contraseña**. El usuario no distingue mayúsculas ni tildes
  (`Carolina` y `carolina` son el mismo).
- **RF-002** · La contraseña tiene **al menos 6 caracteres** y se guarda **con hash**: ni el
  administrador ni quien lea la base puede leerla, y dos personas con la misma contraseña no guardan lo
  mismo.
- **RF-003** · Tras **5 intentos fallidos seguidos** de un usuario, ese usuario espera **5 minutos**. El
  mensaje nunca dice si lo que falló fue el usuario o la contraseña.
- **RF-004** · Sin haber entrado **no se ve ni se hace nada**: toda la API responde "tienes que entrar",
  salvo entrar y crear el primer administrador (RF-018). La pantalla lleva a *Entrar* y, al entrar,
  vuelve a donde se estaba.
- **RF-005** · La sesión dura **hasta *Salir* o 24 horas** (decisión 3). Es un token **firmado** por el
  servidor que vence a las 24 horas; si alguien lo altera, deja de valer. **La página nunca lo ve ni lo
  manda a mano**: viaja solo, en una cookie que el código de la página no puede leer. Al vencer, la
  próxima acción pide entrar otra vez; **la venta a medias no se pierde** (RF-021).
- **RF-006** · *Salir* y *Cambiar de usuario* están siempre en la barra, con el nombre y el rol de quien
  está adentro. Pueden estar abiertas varias sesiones a la vez (el administrador desde su celular, el
  cajero en el mostrador).

### Quién hizo cada cosa

- **RF-007** · **El servidor decide quién hizo cada cosa por la sesión.** El navegador deja de mandarlo.
  Un "quién" que llegue del navegador se ignora.
- **RF-008** · Cada "quién" que se guarda (venta, anulación, turno, gasto, retiro, compra, corrección,
  kardex, auditoría) **apunta a un usuario que existe**. La base lo exige.

### Roles

- **RF-009** · Dos roles: **Administrador** y **Cajero**, con lo que puede cada uno según §5.
- **RF-010** · **El bloqueo está en el servidor.** Una acción que el rol no puede responde *"No
  permitido: es del administrador"* y no cambia nada. La pantalla además esconde lo que no se puede
  (menú, botones), pero esconder no es proteger: cada acción tiene su prueba pedida con un cajero.
- **RF-011** · **(Decisión 1, opción A)** Con sesión de cajero, **ninguna respuesta del servidor trae
  costos**: ni costo del repuesto, ni valor del inventario, ni costos del kardex, ni compras, ni reportes.
  El aviso de venta a pérdida lo calcula el servidor y le dice al cajero **si** el total queda por debajo
  del costo, sin decir cuánto costó. El administrador lo ve como hoy, con las cifras.
- **RF-012** · **(Decisión 2, opción A)** El turno es de quien lo abrió. En un turno ajeno, un cajero **no
  vende, no registra gastos ni retiros, no anula y no cierra**; ve *"El turno abierto es de Carolina:
  lo cierra ella o un administrador"*. El administrador puede operar en cualquier turno, y todo lo que
  hace queda a su nombre.

### Usuarios

- **RF-013** · El administrador **crea usuarios**: nombre (el que sale en el comprobante), usuario, rol y
  contraseña inicial. No se repite un usuario (sin distinguir mayúsculas ni tildes).
- **RF-014** · Una contraseña **inicial o restablecida** se cambia obligatoriamente al entrar: hasta
  cambiarla, no se puede hacer otra cosa.
- **RF-015** · **No se borran usuarios: se desactivan.** Un usuario desactivado no puede entrar, su
  sesión abierta se cierra en su próxima acción, y todo lo que hizo lo sigue nombrando. Se puede volver a
  activar.
- **RF-016** · **Siempre queda al menos un administrador activo.** No se puede desactivar ni pasar a
  cajero al último; tampoco desactivarse uno mismo.
- **RF-017** · El administrador **restablece la contraseña** de otro (queda como inicial, RF-014). Cada
  uno **cambia la suya** dando la actual.
- **RF-018** · **Primera vez (decisión 4):** mientras no exista ningún usuario, la pantalla de entrar
  ofrece *Crear el administrador*. Con un usuario creado, esa opción no existe, ni en la pantalla ni en el
  servidor.
- **RF-019** · Si **el único administrador** olvida su contraseña, se restablece desde el computador de la
  tienda con un procedimiento documentado. No hay *"olvidé mi contraseña"* por correo: el sistema
  funciona sin internet.
- **RF-020** · Crear un usuario, cambiarle el rol, desactivarlo, activarlo y restablecer su contraseña
  quedan en la **auditoría**, con quién lo hizo.

### La venta a medias

- **RF-021** · **La venta a medias es de quien la armó.** Si en el mismo equipo entra otra persona, no
  hereda la venta del anterior; cuando el primero vuelve a entrar, la encuentra como la dejó (spec 0003,
  RF-028). Una venta mandada a cobrar sin respuesta sigue bloqueada hasta que **su** dueño la reintente.

### Ver quién (P2)

- **RF-022** · El detalle de la venta (vendió, anuló), del turno (abrió, cerró), del gasto y del retiro
  (registró, anuló), de la compra (registró, corrigió, anuló) y el historial de correcciones dicen el
  **nombre** de quien lo hizo.
- **RF-023** · El comprobante impreso dice *"Atendió: Carolina"*.
- **RF-024** · El administrador ve la lista de usuarios con su rol, si están activos y cuándo entraron
  por última vez.

### P3

- **RF-025** · Registro de entradas: quién entró, cuándo y desde qué equipo, y los intentos fallidos.
  Solo lo ve el administrador.

---

## 7. Manejo de errores

| Situación | Qué pasa |
|---|---|
| Usuario o contraseña mal | *"Usuario o contraseña incorrectos"*. Nunca dice cuál de los dos |
| Quinto intento fallido | *"Demasiados intentos. Espera 5 minutos"*. La espera es por usuario, no por equipo |
| Usuario desactivado intenta entrar | El mismo mensaje de usuario o contraseña incorrectos |
| La sesión venció a mitad de una venta | Pide entrar; al volver, la venta está como la dejó (RF-021) |
| La sesión venció justo al cobrar | El cobro no se manda: pide entrar y se cobra después. Si ya se había mandado sin respuesta, se reintenta el mismo cobro, con la misma llave (spec 0003) |
| Cajero pide una acción de administrador (por la dirección o por fuera de la pantalla) | *"No permitido: es del administrador"*, y no cambia nada |
| Otro cajero intenta vender en el turno de Carolina | *"El turno abierto es de Carolina: lo cierra ella o un administrador"* |
| Desactivar al último administrador, o a uno mismo | *"Tiene que quedar al menos un administrador activo"* / *"No puedes desactivarte a ti mismo"* |
| Crear un usuario que ya existe | *"Ya existe el usuario «carolina»"* |
| Crear el primer administrador cuando ya hay usuarios | No se crea; la opción no existe |
| Contraseña de menos de 6 caracteres | *"La contraseña tiene que tener al menos 6 caracteres"* |
| Se desactiva a Carolina mientras tiene la sesión abierta | Su próxima acción le pide entrar, y no puede. Su turno abierto sigue abierto: lo cierra un administrador |

---

## 8. Requisitos no funcionales

- **Sin internet.** La entrada es local: usuarios y contraseñas viven en el servidor de la tienda. El
  login del propietario en la nube es otro, de la rebanada 5 (`SPEC_Sistema_Ventas_Repuestos (3).md:139-140`).
- **Seguridad de la sesión (decisión 3).** Token **firmado** con una clave que solo tiene el servidor de
  la tienda, que **vence a las 24 horas**. Va en una cookie que el código de la página no puede leer (el
  car-wash guarda su token en el navegador, `apiClient.js:31`: aquí no se repite), así que un script
  malicioso en la página no puede robarlo. Como la cookie viaja sola, las escrituras llevan además una
  protección contra peticiones falsificadas desde otra página. Aunque el token siga vigente, **desactivar
  a alguien o restablecer su contraseña lo saca de verdad**: el servidor revisa en cada petición que el
  usuario siga activo.
- **Contraseñas con hash**, con un algoritmo lento a propósito: probar contraseñas contra una copia
  robada de la base tiene que ser caro.
- **La red local va sin cifrar.** La tablet entra por `http://192.168.x.x` (`INSTALAR_TICKETERA.md:26`):
  la contraseña viaja por el Wi-Fi de la tienda sin cifrar. Se declara como riesgo (§11); cifrar la red
  local queda fuera de este spec.
- **Roles en el servidor.** Cada acción de administrador tiene su prueba pedida como cajero, que
  espera "no permitido". Una pantalla que esconde un botón no cuenta como prueba.
- **Auditoría.** Todo lo de usuarios (RF-020) queda en `evento_auditoria`, con quién lo hizo. Lo que ya se
  auditaba (anular, corregir, descontar) pasa a decir quién de verdad.
- **Esquema.** Tabla nueva de usuarios, y las columnas de "quién" que ya existen pasan a exigir un usuario
  real (RF-008). **¿Funciona con filas?** La base de QA está vacía desde el 2026-09-17 y la tienda arranca
  de cero: no hay filas con el "quién" provisional que migrar. `[sin verificar: confirmarlo contra QA al
  empezar el plan]`
- **Nube.** Los usuarios no viajan a la nube en este spec. Si el panel del propietario necesita nombres
  ("vendió Carolina"), se decide en la rebanada 5.

### Desde dónde se entra (pregunta del usuario, 2026-09-19)

*¿Si alguien entra desde el celular, puede ver reportes o atender?* Depende de **dónde** esté el celular:

| Dónde | Qué puede | Por qué |
|---|---|---|
| **En la tienda, conectado a su Wi-Fi** | Lo mismo que en el computador del mostrador, **según su rol**: un cajero vende; el administrador además ve reportes, costos y compras | El celular es una pantalla más del servidor de la tienda (`INSTALAR_TICKETERA.md:26`). Con este spec, primero tiene que entrar con usuario y contraseña |
| **Fuera de la tienda (datos móviles, otra red)** | **Nada** con este spec | El servidor de la tienda no está publicado en internet. No se abre al exterior |
| **Desde la nube (rebanada 5, todavía no existe)** | **Solo mirar**: el propietario verá ventas, ganancia y caja, con **su propio login**. No vende ni cambia nada | Lo decidió el cliente (`SPEC_Sistema_Ventas_Repuestos (3).md:134-140`). La verdad vive en el computador de la tienda, que vende sin internet; si la nube también vendiera, la tienda sin internet y la nube podrían vender a la vez la última unidad del mismo repuesto |

**Atender siempre es en la tienda**, contra el servidor de la tienda. La nube es un espejo para mirar.

**Ver la tienda desde la casa — decidido el 2026-09-19: por el panel de la nube (rebanada 5).** No se
publica el servidor de la tienda en internet (abrir un puerto del router lo expondría a todo el mundo, con la
contraseña viajando sin cifrar) ni se monta una red privada (VPN) por ahora. El panel mostrará lo último
sincronizado aunque el computador de la tienda esté apagado.
- **Rendimiento.** Revisar la sesión en cada petición no puede notarse al vender.

---

## 9. Criterios de aceptación

**Entrar**
- [ ] Sin entrar, el sistema no muestra ni hace nada; la API responde "tienes que entrar" en todo salvo
      entrar y crear el primer administrador
- [ ] Con la base vacía, se crea el primer administrador; con uno creado, esa opción no existe ni por la
      pantalla ni por la API
- [ ] Contraseña mal: no entra, y el mensaje no dice si falló el usuario o la contraseña
- [ ] Cinco intentos fallidos bloquean ese usuario 5 minutos
- [ ] Una contraseña inicial obliga a cambiarla antes de hacer cualquier otra cosa

**Quién**
- [ ] Una venta cobrada por Carolina guarda a Carolina, aunque el navegador mande otro "quién"
- [ ] La base rechaza un "quién" que no es un usuario
- [ ] El comprobante dice *"Atendió: Carolina"*

**Roles**
- [ ] Con sesión de cajero, cada acción de administrador de §5 responde "no permitido" y no cambia nada
- [ ] Con sesión de cajero, ninguna respuesta trae costos (inventario, ficha, kardex, búsqueda, catálogo)
- [ ] El cajero ve el aviso de venta a pérdida, sin cifras; el administrador, con cifras
- [ ] Otro cajero no puede vender en el turno de Carolina; el administrador sí, y la venta queda a su nombre
- [ ] El administrador cierra el turno de Carolina; el cierre dice quién abrió y quién cerró

**Usuarios**
- [ ] Carolina desactivada no puede entrar, y sus ventas siguen diciendo "Carolina"
- [ ] No se puede desactivar ni pasar a cajero al último administrador activo
- [ ] Crear, cambiar de rol, desactivar y restablecer quedan auditados con quién lo hizo
- [ ] La venta a medias de Carolina no la hereda don Rubén en el mismo computador, y al volver ella la
      encuentra

**General**
- [ ] `./mvnw clean test` en verde; lint, pruebas y build del frontend en verde
- [ ] Se ve bien en el celular (390 px) y en modo oscuro

---

## 10. Qué no se toca y fuera de alcance

**Qué no se toca**
- **Las reglas de la venta, la caja, las compras y los reportes.** Este spec cambia *quién* puede hacer
  cada cosa y *qué* ve, no cómo se hace.
- **El descuento y la anulación siguen siendo del cajero**, con motivo y auditados, como pidió el
  cliente (`:65`, `:217`).

**Fuera de alcance**

| Qué | Por qué |
|---|---|
| Login del propietario en la nube, y ver la tienda desde la casa | Rebanada 5, panel del propietario (`:139-140`). Decidido el 2026-09-19 (§8) |
| Permisos sueltos por persona ("Carolina sí puede registrar compras") | Dos roles fijos alcanzan para una tienda con un cajero. Se agrega si el cliente lo pide |
| Reportes por cajero (cuánto vendió cada uno) | Ahora es posible, pero no se pidió; va en otro spec |
| Recuperar la contraseña por correo o SMS | Sin internet no hay cómo; RF-019 cubre el caso |
| Cifrar la conexión en la red local | Ver riesgo en §11 |
| Entrar con huella, tarjeta o PIN corto | El cliente pidió usuario y contraseña |
| Dos turnos abiertos a la vez | Un cajero a la vez (`:34`); cambio de turno = cierre y apertura |

---

## 11. Riesgos

| Riesgo | Qué hacer |
|---|---|
| El cajero sigue recibiendo costos por una respuesta que se olvidó | La prueba de RF-011 recorre **todas** las consultas con sesión de cajero y busca campos de costo |
| La contraseña viaja sin cifrar por el Wi-Fi de la tienda | Recomendar que el Wi-Fi del mostrador no sea el de los clientes. Cifrar la red local va en otro spec si el cliente lo pide |
| El único administrador olvida su contraseña | RF-019: procedimiento en el computador de la tienda, documentado en la guía de instalación |
| Se olvida *Salir* y otra persona usa la sesión abierta | *Cambiar de usuario* siempre visible; todo queda con el nombre de quien estaba adentro |
| Las pruebas contra Postgres dejan de pasar porque el "quién" al azar ya no es válido | Se prevé en el plan: cada prueba crea sus usuarios |
| Cambia el cliente de opinión sobre los costos después de construir A | A es la más cara de deshacer hacia B; por eso se confirma antes del plan |

---

## 12. Preguntas abiertas

1. ~~¿El cajero no ve costos?~~ **Resuelto el 2026-09-19: A.** Si un día el cajero tiene que recibir
   mercancía, la registra el administrador después. Si el cliente lo pide, va en otro spec.
2. ~~¿El cajero ve los turnos de otros cajeros?~~ **Resuelto: no**, solo los suyos.
3. **¿Cuántos administradores habrá?** No es requisito del sistema. Se recomienda **dos** (don Rubén y
   alguien de confianza), para que olvidar una contraseña no deje la tienda sin administrador.
