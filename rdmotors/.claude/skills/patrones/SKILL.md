---
name: patrones
description: Catálogo curado de patrones de diseño para RD MOTORS — solo los que este sistema necesita de verdad, con el problema concreto que resuelve cada uno y cuándo NO usarlo. Úsala cuando aparezca un problema estructural repetido: manejar dinero, buscar repuestos con filtros combinados, agregar una forma de pago, modelar estados de una venta, o cuando estés a punto de crear una clase Manager, Helper o Utils. Incluye los antipatrones que hay que rechazar.
---

# Patrones de diseño — RD MOTORS

Un patrón mal elegido cuesta más que no usar ninguno. Este catálogo está filtrado: solo lo que
resuelve un problema que **este** sistema tiene. Todo lo demás es peso muerto.

Orden de lectura: primero el problema que tienes, después el patrón. Nunca al revés.

---

## Dinero: objeto de valor — el más importante de todos

**Problema:** el sistema entero existe para contar plata. Un peso perdido en un redondeo es un
arqueo que no cuadra, y un arqueo que no cuadra es un cajero al que acusan.

**Regla absoluta: nunca `double` ni `float` para dinero.** `0.1 + 0.2` no da `0.3` en punto
flotante. En un POS eso se manifiesta como una venta de $47.000 que el reporte muestra como
$46.999,99999.

Y `BigDecimal` suelto tampoco alcanza: se puede sumar a un `BigDecimal` que representa una
cantidad de unidades, un porcentaje o un costo unitario. El tipo no te protege de nada.

```java
public record Dinero(BigDecimal monto) {
    public Dinero {
        monto = monto.setScale(0, RoundingMode.HALF_UP);  // COP no tiene centavos
    }
    public Dinero mas(Dinero otro) { ... }
    public Dinero por(int cantidad) { ... }
    public Dinero menosDescuento(Porcentaje p) { ... }
}
```

**Decisiones que hay que tomar una sola vez y quedan encerradas aquí:**

- **El peso colombiano no tiene decimales.** El redondeo se decide en un lugar, no en 30.
- **Redondear al final, nunca en cada línea.** Redondear línea por línea y luego sumar da un
  total distinto que sumar y redondear. Con descuentos porcentuales la diferencia aparece rápido
  y el ticket no cuadra con el reporte.
- **El costo promedio ponderado sí necesita decimales** (el car wash usa escala 4). Son dos tipos
  distintos: `Dinero` para lo que se cobra, mayor precisión para lo que se costea. Mezclarlos es
  cómo se pierde plata en el kardex.

**Cuándo NO:** nunca hay un "cuándo no". Si un `BigDecimal` representa pesos, va envuelto.

---

## Puerto y adaptador

**Problema:** el mismo dominio corre en la tienda y en la nube, con bordes distintos.

Es la base de la arquitectura, no un patrón opcional. Los detalles de qué merece puerto y qué no
están en la skill `backend` — no los repito aquí.

**Cuándo NO:** cuando no puedes nombrar la segunda implementación. Ver `backend`.

---

## Repositorio orientado a colección, no CRUD genérico

**Problema:** el dominio necesita guardar y recuperar agregados sin saber que existe SQL.

```java
// BIEN — habla el lenguaje del negocio
Optional<Producto> buscarPorCodigo(Codigo codigo);
Optional<Producto> buscarParaModificar(ProductoId id);
List<Producto> conStockBajo();
```

```java
// MAL — un CRUD genérico filtrado hacia adentro
<T> T save(T entity);
<T> List<T> findAll(Class<T> type, Map<String, Object> filtros);
```

El repositorio genérico parece que ahorra código y lo que hace es mover las consultas al que
llama. Termina con `findAll` en un caso de uso filtrando en memoria, y el día que haya 8.000
repuestos la búsqueda tarda tres segundos con el cliente esperando en el mostrador.

**Uno por agregado, no uno por tabla.** Las líneas de una venta no tienen repositorio propio: se
guardan y se cargan con su venta. Si una entidad no tiene sentido sin su padre, no es un agregado.

---

## Especificación: para la búsqueda de repuestos

**Problema:** el spec pide buscar por código, nombre, marca, modelo y año, combinables. Escribir
un método por combinación da la explosión clásica: `buscarPorMarcaYModelo`,
`buscarPorMarcaYModeloYAnio`, `buscarPorNombreYMarca`...

**Solución:** criterios componibles que se combinan en tiempo de ejecución y se traducen a una
sola consulta.

```java
var criterio = Criterio.texto("filtro aceite")
        .y(Criterio.compatibleCon(marca, modelo, anio))
        .y(Criterio.activo());
```

**Cuándo NO:** si solo hay dos filtros y no se combinan, dos métodos son más claros. Este patrón
paga a partir de tres dimensiones combinables — que es justo lo que pide el spec.

**Cuidado:** el criterio se traduce a SQL, no se filtra en memoria. Una especificación que carga
todo y filtra en Java es peor que no tenerla.

---

## Estrategia: formas de pago

**Problema:** efectivo, QR y mixto hoy; pasarela real en fase 2. Y cada una responde distinto a la
pregunta que le importa a la caja: **¿esta plata entra al cajón físico?**

Un `if` encadenado en `RegistrarVenta` significa que agregar un medio de pago toca el caso de uso
central del sistema.

```java
public interface MedioDePago {
    boolean entraAlCajon();      // decide el arqueo
    void registrar(Cobro cobro);
}
```

El efectivo entra al cajón; el QR no. Esa única pregunta es la que hace que el arqueo cuadre —
en el car wash es la razón por la que `esperado = fondo + efectivo cobrado` y tarjeta/transferencia
quedan fuera a propósito.

**Cuándo NO:** si las variantes solo cambian un dato y no un comportamiento, un enum con ese
campo basta. La estrategia se justifica cuando cada variante **hace** algo distinto.

---

## Evento de dominio + Outbox

**Problema:** registrar una venta tiene consecuencias (kardex, sincronización a la nube) que no
deben estar cableadas dentro de la venta.

El mecanismo completo —el evento nace dentro de la misma transacción, secuencia monotónica,
dedupe por id, versión— está en la skill `backend`. Aquí solo el criterio de diseño:

**Un evento describe algo que YA pasó, en pasado y en lenguaje del negocio.**
`VentaRegistrada`, `CajaCerrada`, `StockAjustado`. Nunca `ActualizarTablaVentas`.

**Cuándo NO:** para coordinar dos cosas que deben ocurrir bajo el mismo commit. Un evento
asíncrono entre "descontar stock" y "registrar la venta" rompe la atomicidad — justo lo que este
sistema no puede permitirse. Los eventos son para lo que puede llegar tarde; el commit es para lo
que no.

---

## Máquina de estados explícita

**Problema:** una venta puede estar en curso, cobrada o anulada. Una sesión de caja abierta o
cerrada. Un `String estado` con validaciones dispersas permite transiciones imposibles.

El spec pide dos cosas que dependen de esto: retomar una venta interrumpida por corte de luz, y
que una anulación deje rastro. Ambas son transiciones, no campos.

```java
public Venta anular(Motivo motivo, Usuario quien) {
    if (estado != COBRADA) throw new TransicionInvalida(estado, ANULADA);
    ...
}
```

Las transiciones válidas se declaran en un solo sitio. En el car wash hay una regla de este tipo
—`PENDING → COMPLETED` exige pasar por `IN_PROGRESS`— que solo existía en la cabeza de quien la
escribió, y sembrar datos de prueba la violaba sin darse cuenta.

---

## Constructores con nombre en vez de `new` desnudo

**Problema:** `new Venta(a, b, c, d, e, f)` no dice nada, y un objeto de dominio no debería poder
nacer inválido.

```java
Venta.iniciar(cajero, sesionDeCaja);
MovimientoKardex.porCompra(producto, cantidad, costo, quien);
MovimientoKardex.porVenta(producto, cantidad, quien);
```

Cada fábrica exige exactamente lo que ese caso necesita. Un builder con todo opcional permite
construir un movimiento de kardex sin saldo posterior — que es un registro contable roto.

**Cuándo NO:** para DTO y objetos de transporte, un record plano está bien.

---

## Antipatrones — rechazar en revisión

| Antipatrón | Por qué aquí es grave |
|---|---|
| **Modelo anémico** | convierte hexagonal en capas con nombres nuevos. Ver skill `solid` |
| **Clases `Manager`, `Helper`, `Utils`, `Service` genéricas** | el nombre no dice qué hace porque hace de todo. Terminan siendo el basurero del proyecto |
| **Singleton escrito a mano** | Spring ya gestiona el ciclo de vida; un singleton propio es estado global no testeable |
| **Service Locator** | pedir dependencias desde adentro esconde el grafo y rompe la inversión. Inyección por constructor, siempre |
| **Repositorio genérico** | mueve las consultas al que llama (arriba) |
| **Herencia por reutilizar código** | ver skill `solid` |
| **Abstraer en el primer caso** | un puerto sin segunda implementación es una interfaz decorativa |
| **`default` silencioso en un switch** | no falla, miente. Cicatriz real del car wash |

### La regla de tres

**No abstraigas hasta el tercer caso.** Dos usos parecidos suelen ser coincidencia; el tercero
revela el eje real de variación. Abstraer en el segundo produce la abstracción equivocada, y una
abstracción equivocada es más cara que la duplicación que evitó — hay que deshacerla antes de
poder avanzar.

Duplicar dos veces a propósito es una decisión de ingeniería válida y aquí es la recomendada.
