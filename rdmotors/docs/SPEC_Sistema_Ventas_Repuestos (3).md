# SPEC — Sistema de Ventas e Inventario para Local de Repuestos

> **Instrucciones de uso:** Lleva este documento a la reunión con el cliente. Cada sección tiene preguntas guía — anota las respuestas directamente debajo de cada una. Al final, marca cada requisito como **P1 (Crítico) / P2 (Importante) / P3 (Deseable)** para definir el alcance del MVP y cotizar por fases.

---

## 1. Objetivos de Negocio (¿Por qué construimos esto?)

- ¿Cómo lleva el control hoy? (cuaderno, Excel, otro sistema)
- ¿Cuál es el dolor principal que quiere resolver? (pérdidas de stock, no saber ganancia real, desorden en caja, fraude de cajeros, lentitud al vender)
- ¿Qué meta de negocio busca en los próximos 6-12 meses? (abrir otra sucursal, formalizarse fiscalmente, reducir mermas, etc.)
- ¿Tiene o planea tener más de una sucursal?

**Respuestas:**
```



```

---

## 2. Caso de Uso (¿Cómo interactúa el usuario?)

- ¿Cuántos cajeros/usuarios usarán el sistema, y usan el sistema al mismo tiempo o por turnos?
- Describe un día típico de venta paso a paso (desde que el cliente pregunta por un repuesto hasta que se va).
- ¿Venden solo en mostrador, o también por teléfono/WhatsApp/redes?
- ¿Venden al detalle, al por mayor, o ambos? ¿Cambia el precio según cantidad?
- ¿Cómo buscan un repuesto: por código, por nombre, o por marca/modelo/año del vehículo?
- ¿Existen roles distintos? (dueño/administrador vs cajero) ¿Qué debe ver y qué NO debe poder tocar cada uno?

**Respuestas:**
```
- un solo cajero estara ak mismo tiempo
- Cliente entra, pregunta por el accesorio etc, mas optimo se busca sin escanear codigo etc se busca si hay disponible, revisa precio y se paga inmediatamente, stock reduzca.
- meramente local se atenderan
- al detallepor el momento
- por codigo nombre modelo marca
- Administradores y cajero
- Cajero responde por su turno, sistema de caja



```

---

## 3. Requisitos Funcionales (RF) — Qué debe HACER el sistema

### 3.1 Módulo de Ventas / Cajero
- ¿Qué tipo de comprobante emiten: boleta, factura, ticket interno, o los tres?
- ¿Necesitan capturar datos del cliente (RUC/NIT, nombre) en la venta?
- ¿Qué formas de pago aceptan? (efectivo, tarjeta, transferencia, mixto)
- ¿Aplican descuentos? ¿Quién los autoriza?
- ¿Necesitan imprimir en impresora térmica de tickets o impresora normal?

**Respuestas / Prioridad (P1/P2/P3):**
```
- Quiere imprimir comprobante de pago
- Solamente comprobante de pago — SIN requisitos fiscales (no factura electrónica,
  no numeración legal, no integración con DIAN/SUNAT). Es un ticket interno.
- Formas de pago: efectivo, QR, mixto. Sin datáfono.
  - QR: SIN integración real con pasarela de pago. El cajero solo marca
    "pagó por QR" como registro manual (no valida el pago automáticamente).
- Aplicación de descuentos, el cajero tiene que autorizarlos
- Impresora térmica tipo ticketera POS

**Nota de riesgo:** al no validar el QR automáticamente, el control de que
el pago realmente llegó depende 100% de la honestidad/verificación manual
del cajero. Vale la pena mencionárselo al cliente como limitación conocida
del MVP (posible fase 2: integración real con Nequi/Yape/Bancolombia API).
```

### 3.2 Módulo de Inventario
- ¿Cómo identifican cada repuesto? (código interno, código de barras, SKU del proveedor)
- ¿Necesitan relacionar un repuesto con varias marcas/modelos/años de vehículo compatibles?
- ¿Quieren alertas de stock mínimo o próximo a agotarse?
- ¿Manejan variantes del mismo producto? (ej. mismo filtro, distinta marca/calidad/precio)
- ¿Necesitan registrar el costo de compra para calcular ganancia por producto?

**Respuestas / Prioridad:**
```
-Kardex de inventario
-Relacionarlos con marcas de moto, vehiculos, años compatibles.
-Alertas de stock bajo
-Manejaran mismos productos con diferentes marcas preciosmcalidad
- Registrar costo de compra calcular ganancia



```

### 3.3 Módulo de Compras / Proveedores
- ¿Registran las compras a proveedores dentro del sistema para actualizar stock y costo automáticamente, o eso queda fuera del alcance por ahora?
- ¿Necesitan historial de precios de compra por proveedor?

**Respuestas / Prioridad:**
```
-Registrar compras para actualizar costo auto
-Historial de precios por compra por proveedor


```

### 3.4 Módulo de Caja
- ¿Necesitan apertura y cierre de caja diario (arqueo)?
- ¿Quién puede cerrar caja? ¿Se compara lo contado físicamente contra lo que dice el sistema?
- ¿Necesitan registrar gastos/retiros de caja durante el día (ej. compra menor, propina, etc.)?

**Respuestas / Prioridad:**
```
-Apertura y cierre de caja si
-Cajero y admin 
-Necesitan registrar gastos y retiros de caja



```

### 3.5 Reportes y Ganancias
- ¿Qué reportes son indispensables desde el primer día? (ventas del día, ganancia por período, productos más vendidos, stock bajo, cierre de caja)
- ¿Necesitan comparar ganancia bruta vs neta (con costos)?
- ¿Necesitan exportar reportes (Excel, PDF) o solo verlos en pantalla?

**Respuestas / Prioridad:**
```
-Ventas del dia, cuanto se vendio, ganancia por periodo, productos mas vendidos, stock bajo, 
-Comparar ganancia bruta vs neta 
-Exportar pdf, excel


```

### 3.6 Panel Remoto del Propietario (nuevo — sincronización en tiempo real)
- Vista web/móvil de solo lectura para el propietario: ventas del día, ganancia,
  estado de caja, en tiempo real cuando hay conexión.
- No permite registrar ventas ni modificar datos desde el panel remoto en el
  MVP (evita conflictos de escritura); es solo consulta.
- Requiere login propio del propietario (separado del usuario/contraseña local
  del cajero/admin).

**Prioridad:** P1 (fue el requisito que originó el cambio de arquitectura).

### 3.7 Clientes (fase 2 — confirmado)
- ¿Manejan crédito o "fiado" a clientes frecuentes?
- ¿Necesitan historial de compras por cliente?

**Respuestas / Prioridad:**
```
-Credito a clientes
-Historial de compras por cliente

**Decisión final:** queda en fase 2 (P2). No forma parte del MVP.
```

---

## 4. Requisitos No Funcionales (RNF) — Restricciones de calidad

- **Conectividad:** ¿El sistema debe funcionar 100% sin internet (local en la PC de la tienda), o necesita acceso remoto/nube para ver reportes desde el celular del dueño?
-HÍBRIDO: el cajero opera sin wifi/internet (offline-first), pero el sistema
 sincroniza en tiempo real a la nube cuando hay conexión, para que el
 propietario vea métricas (ventas, ganancia, caja) desde su celular en
 cualquier momento. Ver decisión de arquitectura al final de esta sección.
- **Dispositivos:** ¿En cuántas PCs/tablets se instalará? ¿Windows, Android, o ambos?
-Computador, celular etc
- **Usuarios concurrentes:** ¿Cuántas personas usarán el sistema al mismo tiempo como máximo?
1-2
- **Respaldo de datos:** ¿Con qué frecuencia debe respaldarse la información? ¿Automático o manual?
automatico
- **Seguridad:** ¿Necesitan login con usuario y contraseña por persona? ¿Permisos distintos por rol?
Usuario y contraseña por persona y rol
- **Velocidad:** ¿Hay un tiempo máximo aceptable para registrar una venta (para no hacer esperar al cliente)?
NO

**Decisión de arquitectura (actualizada — híbrida offline + nube):**
```
- El punto de venta (cajero) sigue siendo OFFLINE-FIRST: opera sin internet,
  un solo dispositivo activo a la vez para evitar conflictos de sincronización
  de datos en la operación de venta.
- Se agrega un canal de SINCRONIZACIÓN EN TIEMPO REAL hacia la nube: cada venta,
  cierre de caja y movimiento relevante se envía al servidor en el momento en
  que ocurre, siempre que haya conexión a internet disponible.
- Sincronización de UNA SOLA VÍA (local → nube). El dispositivo local sigue
  siendo la única fuente de verdad que escribe datos; la nube es un espejo de
  solo lectura para el panel del propietario. Esto evita la necesidad de
  resolución de conflictos bidireccional (ventaja clave: reduce complejidad
  frente a un sync completo de dos vías).
- Si no hay internet en el momento de la venta, el evento se guarda en una cola
  local y se sincroniza automáticamente en cuanto vuelve la conexión.
- Panel remoto (dashboard web) para que el propietario visualice métricas
  (ventas del día, ganancia, cierre de caja) desde cualquier dispositivo con
  internet, en tiempo real.
- Requiere backend en la nube (base de datos + API) — ver sección de hosting
  en la cotización comercial. Costo mensual de hosting a cargo del cliente,
  independiente del pago único de desarrollo.
- Reutiliza el patrón offline ya implementado en el sistema de car wash para
  la parte local; el canal de sincronización y el panel remoto son
  desarrollo nuevo.
- Respaldo automático: local (en el dispositivo) + copia en la nube como
  respaldo adicional una vez sincronizado.
```

---

## 5. Manejo de Errores (el "Unhappy Path")

- ¿Qué debe pasar si se corta la luz o se cierra el programa a mitad de una venta?
- Debe aparecerle si retomar la venta pendiente analizar que es mas optimo
- ¿Qué debe pasar si el cajero intenta vender más unidades de las que hay en stock?
 directamente no lo debe permitir
- ¿Cómo se corrige una venta ya registrada por error? (anulación, nota de crédito, reimpresión)

- ¿Qué pasa si dos cajeros intentan editar el mismo producto al mismo tiempo?
No habran dos cajeros, pero no debe permtir dos peticiones en simultaneo que afecten la misma rama
- ¿Quién puede autorizar anulaciones o correcciones?
 cajero pero que quede historial o constancia auditoria

**Respuestas / Prioridad:**
```



```

---

## 6. Criterios de Aceptación (Definition of Done del MVP)

> El "Test MVP": cada funcionalidad del MVP debe poder **desarrollarse, probarse, implementarse y demostrarse de forma independiente**.

Marca qué debe estar sí o sí para decir "ya puedo trabajar con esto" (P1), qué es importante pero no bloqueante (P2), y qué puede quedar para una fase 2 (P3):

| Funcionalidad | Prioridad (P1/P2/P3) |
|---|---|
| Registrar venta y emitir comprobante |P1 |
| Descontar stock automáticamente al vender | P1|
| Cierre de caja diario | P1|
| Alertas de stock bajo |P2 |
| Reporte de ganancias | P1 (basico y minimo funcional) |
| Registro de compras a proveedores |P1 |
| Búsqueda por compatibilidad de vehículo | P2 |
| Multiusuario con roles |P1 |
| Crédito/fiado a clientes | P2 (fase 2 — confirmado) |
| Multi-sucursal | NO |
| Panel remoto de métricas en tiempo real (propietario) | P1 |

---

## 7. Referencias y Contexto Existente

- ¿Tienen algún sistema previo del cual exportar datos (clientes, inventario actual)?
- ¿Tienen catálogo de productos en Excel u otro formato que sirva de base?
Si, me lo enviaran
- ¿Hay alguna app o sistema similar que les guste como referencia visual?

**Respuestas:**
```



```

---

## 8. Resumen para Cotización

- **Alcance MVP (P1):** Ventas + comprobante interno (sin validez fiscal), descuento
  automático de stock, cierre de caja diario con gastos/retiros, reporte básico de
  ganancias, registro de compras a proveedores, búsqueda de repuestos por
  código/nombre/marca/modelo, roles (admin/cajero) con usuario y contraseña,
  manejo de venta interrumpida y bloqueo de venta sin stock, auditoría de
  anulaciones.
- **Fase 2 (P2/P3):** Alertas de stock bajo, búsqueda avanzada por compatibilidad
  de vehículo, exportación de reportes a PDF/Excel, comparación ganancia bruta vs
  neta, integración real de pago QR con pasarela, crédito/fiado a clientes.
- **Plataforma:** Híbrida — punto de venta offline-first en un solo dispositivo
  activo a la vez (PC o celular), con sincronización en tiempo real de una sola
  vía (local → nube) hacia un panel remoto de solo lectura para el propietario.
- **N.º de usuarios concurrentes:** 1 (cajero) + acceso posterior del admin,
  nunca dos sesiones activas al mismo tiempo en el punto de venta local.
- **N.º de dispositivos a instalar:** 1 dispositivo principal para el punto de
  venta, más acceso remoto del propietario vía navegador/celular al panel.
- **Hosting en la nube:** requerido (backend + base de datos). Se aloja en
  servidor propio del desarrollador (no requiere que el cliente contrate un
  proveedor externo). Cuota de mantenimiento y hosting: $50.000 COP/mes,
  facturada directamente por el desarrollador — ver detalle en la cotización
  comercial.

**✅ Alcance 100% cerrado.** Crédito/fiado confirmado en fase 2 (P2). Sin
puntos pendientes de decisión del cliente.

**Ventaja de reuso:** al ya existir el sistema de car wash con caja, inventario,
reportes y arquitectura offline resueltos, el esfuerzo real se concentra en:
adaptar el modelo de datos (repuestos + compatibilidad vehicular), módulo de
proveedores/costos, y el manejo de errores específico de este negocio. Esto
reduce el tiempo de desarrollo frente a un proyecto desde cero.
