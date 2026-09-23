---
name: spec
description: Redacta un spec y su plan en docs/specs/ ANTES de implementar cualquier funcionalidad nueva de RD MOTORS. Se entrega en dos tiempos: primero solo el spec, para que el usuario lo revise y lo corrija; el plan se escribe después. Úsala cuando pidan una feature ("quiero agregar", "necesito que el sistema", "vamos a hacer"), cuando el trabajo toque plata, inventario, caja o esquema, y siempre antes de escribir un caso de uso nuevo. No la uses para arreglar un bug puntual ni para cambios de una línea.
---

# Spec — RD MOTORS

Un spec aquí no es un documento de intenciones: es **la investigación puesta por escrito antes de
tocar código**. Su valor no está en describir lo que se va a construir —eso el código ya lo dirá—
sino en descubrir con qué choca antes de chocar.

## La regla que sostiene todo lo demás

**Hecho verificado y propuesta nunca se mezclan.** Cada afirmación sobre cómo funciona hoy el
sistema lleva su `archivo:línea`. Lo que aún no existe va aparte, marcado como propuesta.

Un spec que mezcla ambas cosas se lee como si todo estuviera confirmado, y entonces se implementa
sobre supuestos inventados. Si no lo verificaste, escribe "sin verificar" — un hueco declarado es
información útil; fingir certeza no.

## No escribas hasta haber leído el código

Antes de la primera línea del documento, responder con evidencia:

1. **¿Qué de esto ya existe?** Casi siempre hay más de lo que parece. `Variante.descontar()` ya
   valida stock y lanza `StockInsuficienteException` con los números; media venta está construida.
2. **¿De qué cuelga lo que voy a tocar?** Es la pregunta cara. Un campo de fecha que se toca mueve
   plata entre días; el costo promedio lo leen los reportes de margen.
3. **¿Cuál es la decisión que cambia el alcance?** Suele haber exactamente una. Encuéntrala y
   ponla al frente.

```bash
# El concepto que vas a tocar, ¿quién más lo usa?
grep -rn "costoPromedio\|reponerPorCompra" domain/src pos/src --include=*.java
```

## Lo que ESTE proyecto obliga a revisar en cada spec

Si alguna no aplica, dilo explícitamente — el lector necesita saber que la consideraste.

- **¿Toca plata?** Todo monto es `Dinero` (COP entero). Todo costo unitario o promedio va con
  4 decimales. **Las partes tienen que sumar el total.** Ver skill `patrones`.
- **¿Toca inventario?** El kardex es append-only: corregir es agregar, nunca editar. Vender no
  toca el costo promedio; solo la compra lo recalcula. Mover stock exige `buscarParaModificar`.
- **¿Toca caja?** La sesión es por **turno**, no por día. Solo el efectivo entra al esperado del
  cajón. El arqueo tiene que poder señalar a una persona.
- **¿Necesita esquema?** Flyway, migración nueva numerada. Y la pregunta que la suite no puede
  hacer: **¿esto funciona en una base que YA tiene filas?**
- **¿Funciona sin internet?** El POS es la fuente de verdad y opera offline. Si la operación se
  encola, ¿es **SUMA o REEMPLAZO**? Si suma, necesita llave de idempotencia.
- **¿Va al outbox?** ¿El propietario lo ve en su panel? El evento se escribe en la misma
  transacción, con secuencia monotónica — nunca ordenado por fecha.
- **¿Quién lo ve?** ADMIN y CAJERO no pueden lo mismo. Los ajustes de inventario son solo ADMIN.
  **Esconder un botón no protege nada**: el bloqueo va en el caso de uso con su prueba.
- **¿Qué queda auditado?** Anular, descontar y ajustar generan `evento_auditoria` con motivo.

### Y la pregunta propia de la arquitectura

**¿Dónde vive cada pieza?** Todo spec de RD Motors tiene que decirlo, porque es donde más fácil
se equivoca uno:

| Va en | Qué es | Prueba mecánica |
|---|---|---|
| `dominio/` | los sustantivos y sus reglas | tiene cuerpo, hace algo |
| `dominio/puerto/` | lo que el dominio **pide** de afuera | todos los métodos terminan en `;` |
| `aplicacion/` | el guion que coordina | abre la transacción, no calcula |
| `pos/…/infraestructura/` | quien cumple los puertos | importa Spring, JPA o HTTP |

Y antes de proponer un puerto nuevo: **¿puedes nombrar su segunda implementación?** Si no, no es
un puerto — es una interfaz decorativa. Ver skill `backend`.

## Se entrega en dos tiempos

**El spec va solo primero.** No se escribe el plan hasta que el usuario haya revisado el spec y lo
haya corregido.

| Tiempo | Qué se escribe | Cuándo termina |
|---|---|---|
| **1 — Spec** | `spec.md` y su línea en el índice. Nada más. | Se entrega y **se para**. |
| **2 — Plan** | `plan.md` | Solo cuando el usuario diga que el spec ya está como lo quiere. |

El motivo no es ceremonia: el plan traduce el spec a fases y archivos, y el usuario casi siempre
cambia algo —un requisito, el alcance, una decisión—. Un plan escrito antes de esa revisión
traduce un documento que ya no existe.

**Al empezar el tiempo 2, releer `spec.md` completo.** El usuario pudo editarlo directamente y sus
cambios no están en la conversación.

## Estructura

**QUÉ y CÓMO van en archivos separados.** Si en `spec.md` aparece un nombre de clase, está en el
archivo equivocado: el spec debe poder leerlo alguien que no conoce el código.

**`spec.md` — QUÉ**

1. **Objetivo de negocio** — por qué se construye. Si no se puede responder, quizá no toca aún.
2. **Caso de uso** — historias priorizadas (P1 bloquea · P2 secundaria · P3 deseable), cada una
   con lo que se puede **demostrar sola** al terminarla.
3. **Qué existe hoy** — tabla con `archivo:línea`. La sección más valiosa: este ya no es un
   proyecto vacío.
4. **La decisión** — la que cambia el alcance, con opciones, costo y **recomendación**.
5. **Requisitos funcionales** — `RF-001`, numerados para citarlos en el plan.
6. **Manejo de errores** — el camino infeliz. Un requisito sin su error es medio requisito.
7. **Requisitos no funcionales** — offline, roles, auditoría, rendimiento.
8. **Criterios de aceptación** — condiciones **binarias**. Es la Definition of Done.
9. **Qué no se toca** y **Fuera de alcance**.

**`plan.md` — CÓMO** *(tiempo 2)*: fases con checkpoint, archivos por módulo (`domain` / `pos`),
qué prueba cubre cada fase, y la bitácora de **decisiones tomadas** durante la implementación.

En este proyecto el plan además dice, por cada pieza nueva, **en cuál de las cuatro carpetas cae**.
Esa asignación es una decisión de diseño, no un detalle: si termina en el sitio equivocado, la
regla de dependencias se rompe y el compilador no siempre lo atrapa.

## Lo que no se sabe se marca

`[NECESITA ACLARACIÓN: la pregunta]` se queda escrito en el documento. Un hueco declarado es
información útil; uno rellenado a ojo es una decisión inventada que nadie tomó y que aparecerá
como bug tres semanas después.

## Reglas de redacción

**Recomienda, no enumeres.** Presentar tres opciones sin escoger le traslada al usuario un trabajo
que era tuyo. Da la recomendación y su porqué.

**Cada fase termina en un checkpoint demostrable.** Si una fase no termina en algo que se pueda
probar o enseñar, no es una fase: es la mitad de otra.

**Los casos límite salen del sistema real.** «¿Y si falla la red?» es relleno. «$200.000 entre 15
da $13.333,3333 y redondearlo pierde $5» es un caso límite: sale del dato del cliente.

**Sin estimaciones en horas.** El costo se expresa en lo que toca: «solo dominio», «migración +
puerto + adaptador + endpoint».

## Dónde vive

```
docs/specs/
├── README.md          índice; una línea por funcionalidad
└── NNNN-slug/
    ├── spec.md
    └── plan.md
```

Los archivos **crecen con el trabajo**: un cambio pequeño lleva `spec.md` y ya. El número es un
identificador estable, se cita en commits, y nunca se reusa aunque la funcionalidad se descarte.
Al crear uno, **añadir su línea al índice** — una carpeta fuera del índice no existe.

## Dónde para la skill

Hay **dos paradas**, y las dos son en firme.

**Primera — entregado el `spec.md`.** No se escribe el plan, no se toca código. El mensaje al
usuario dice tres cosas y ninguna más: dónde quedó el archivo, cuál es la decisión que debe tomar,
y qué riesgo encontraste que él no esperaba. Después se espera.

**Segunda — entregado el `plan.md`.** Solo cuando el usuario diga que el spec está como lo quiere,
y releyendo el archivo antes por si lo editó. Al entregarlo no se empieza a implementar: la
decisión de arrancar también es suya.

Si mientras investigas descubres que la feature ya está construida, o que choca con algo que mueve
plata, **eso es el entregable** — dilo de una y no escribas el spec completo para justificar el
esfuerzo.
