---
name: solid
description: Cómo aplicar SOLID y diseño orientado a objetos en RD MOTORS, y —más importante— dónde cada principio se aplica de más y hace daño. Úsala al crear o refactorizar clases del dominio, al decidir si algo merece una interfaz nueva, al revisar una clase que creció demasiado, o cuando aparezca un modelo anémico (clases con solo getters y la lógica en servicios). No es un manual de definiciones: es criterio para decidir.
---

# SOLID y POO — RD MOTORS

Las definiciones de SOLID no hacen falta aquí. Lo que hace falta es **saber cuándo cada
principio paga y cuándo cobra**, porque los cinco se pueden aplicar de más y cuatro de ellos,
llevados al extremo, producen exactamente el diseño que pretendían evitar.

Regla de encuadre: SOLID sirve al dominio, no al revés. Si aplicar un principio hace más difícil
leer "cómo se registra una venta", el principio está mal aplicado.

## El error número uno de este proyecto: el modelo anémico

No es un principio de SOLID, es la trampa que más probablemente vamos a caer, así que va primero.

Un modelo anémico es una clase que solo tiene datos —getters y setters— mientras toda la lógica
vive en servicios que la manipulan desde afuera. Compila, funciona, pasa las pruebas. Y convierte
la arquitectura hexagonal en capas con nombres nuevos: el dominio deja de ser el centro y pasa a
ser un contenedor de campos.

```java
// ANÉMICO — el que va a salir solo si nadie lo vigila
venta.setTotal(venta.getTotal().add(linea.getSubtotal()));
venta.setEstado("ANULADA");
if (producto.getStock() < cantidad) throw new ...;
producto.setStock(producto.getStock() - cantidad);
```

```java
// CON COMPORTAMIENTO — la regla vive donde viven los datos
venta.agregar(linea);
venta.anular(motivo, usuario);
producto.descontar(cantidad);   // adentro valida stock y lanza si no alcanza
```

La diferencia práctica: en la primera versión, "no vender sin stock" está escrito en un servicio,
y el día que alguien descuente stock desde otro caso de uso (una anulación, un ajuste, una compra
devuelta) **la validación no viaja con el dato**. En la segunda es imposible saltársela.

Señal de alarma: un caso de uso lleno de `get`/`set` encadenados sobre objetos de dominio. Si el
caso de uso está haciendo aritmética con los campos de la venta, esa aritmética pertenece a la
venta.

Contrapeso honesto: los DTO de entrada y salida **sí** son bolsas de datos y está bien que lo
sean. La regla aplica al dominio, no a los bordes.

## S — Una razón para cambiar, no "una sola cosa"

La formulación popular ("una clase, una responsabilidad") es la que produce clases de tres líneas
y proyectos con 400 archivos. La útil es la original: **una clase debe tener una sola razón para
cambiar**, y "razón" significa *un actor del negocio que pide el cambio*.

Aplicado aquí:

| Clase | Cambia cuando... | ¿Se separa? |
|---|---|---|
| `Venta` | cambian las reglas de vender | una sola razón — se queda junta |
| `Venta` + su formato de ticket | ...o cuando el cliente quiere otro diseño de ticket | **sí**: son dos actores distintos |
| `CierreDeCaja` + cálculo del arqueo | cambia la regla contable | una sola razón — junto |

El caso concreto que vamos a enfrentar: `RegistrarVenta` descuenta stock, escribe kardex, enlaza
caja y publica el evento. Parecen cuatro responsabilidades. **No lo son**: son un solo motivo de
cambio ("cómo se registra una venta") y además deben ocurrir bajo el mismo commit. Partirlo en
cuatro servicios que se llaman entre sí no mejora el diseño y sí dispersa la transacción.

Separar por sustantivos es contar cosas; separar por motivos de cambio es diseñar.

## O — Abierto a extensión, pero solo donde ya sabes que va a variar

Aplicar OCP en todas partes es construir puntos de extensión para variaciones que nunca llegan.
Cada uno cuesta una indirección permanente.

**Dónde sí, en este sistema** — porque la variación está escrita en el spec:

- **Formas de pago**: efectivo, QR y mixto hoy; pasarela real en fase 2. Agregar una forma no
  debería obligar a tocar `RegistrarVenta`.
- **Emisión de comprobante**: ticketera térmica en la tienda, nada en la nube.
- **Movimientos de kardex**: compra, venta, ajuste, reversión, y los que falten.

**Dónde no**: el cálculo del costo promedio ponderado. Es una regla contable, no una variación.
Hacerlo "extensible" invita a que alguien enchufe una segunda forma de costear y el inventario
deje de ser auditable.

Regla operativa: la primera vez, escríbelo directo. La segunda variante, extrae el punto de
extensión. La tercera confirma que acertaste. Abstraer en la primera es adivinar.

## L — Donde de verdad muerde: los adaptadores

Liskov suena teórico hasta que tienes dos implementaciones del mismo puerto, que es exactamente
nuestro caso: el mismo dominio corre en la tienda y en la nube.

El escenario real que rompe: el puerto `EmitirComprobante` tiene un adaptador de ticketera en la
tienda y en la nube... no hay impresora. Si el adaptador de la nube **hace un no-op silencioso**,
el caso de uso cree que imprimió y nadie se entera. Si **lanza excepción**, el caso de uso
revienta en un entorno donde no debería.

Ninguna de las dos es sustituible. La salida es que **el contrato del puerto sea honesto desde el
principio**: si emitir puede no ocurrir, el puerto lo dice en su firma y el dominio decide qué
hacer. La sustituibilidad no se arregla en el adaptador; se diseña en el puerto.

La versión corta: **un adaptador que miente sobre lo que hizo rompe el sistema más que uno que
falla.** El silencio es peor que el error.

## I — El principio que más fácil se vuelve su contrario

Interfaces pequeñas y específicas, sí. Pero llevado a hexagonal produce el fenómeno clásico:
un puerto por método, treinta interfaces de un método cada una, y para entender un caso de uso
hay que abrir treinta archivos.

Equilibrio para este proyecto: **un puerto por colaborador real, no por operación.**
`RepositorioProductos` con buscar / buscarParaModificar / guardar es un puerto. Tres interfaces
separadas para eso es ceremonia.

La pregunta que decide: *¿existe algún consumidor que necesite una parte y no la otra?* Si no
existe, es una sola interfaz. Y ojo — la respuesta no puede ser "algún día"; ver la skill
`backend`, sección de qué merece un puerto.

## D — Aquí no es un principio extra: es la arquitectura

La inversión de dependencias **es** la regla de hexagonal, dicha con otras palabras. Si ya se
cumple "las dependencias apuntan hacia adentro", DIP está cumplido y no hay nada más que hacer.

Lo único que hay que vigilar es la mitad que se olvida: **la interfaz pertenece a quien la usa,
no a quien la implementa.** El puerto vive en `dominio/puerto/`, no junto al adaptador JPA. Si la
interfaz está en el paquete de infraestructura, no invertiste nada: solo pusiste una interfaz.

```
dominio/puerto/RepositorioProductos.java              ← el contrato, lo define el dominio
infraestructura/jpa/RepositorioProductosJpa.java      ← lo implementa quien obedece
```

## Herencia: casi nunca

En este dominio, casi todo lo que parece herencia es composición o un enum con comportamiento.

- Formas de pago: **no** una jerarquía `Pago` → `PagoEfectivo` / `PagoQR`. Un enum con la regla
  de si entra o no al cajón físico, o una estrategia. Ver la skill `patrones`.
- Tipos de movimiento de kardex: enum, no subclases.
- Reutilizar código entre dos clases que "se parecen": composición. La herencia por conveniencia
  es la que produce jerarquías que nadie puede modificar tres meses después.

Herencia solo cuando hay una relación de sustitución genuina y estable. En un POS eso es raro.

## Etiquetas y textos: en el enum, nunca en un switch de servicio

Cicatriz real del car wash. Un `switch` de etiquetas dentro de un servicio con `default -> code`
**no falla, miente**: cubría 4 de 10 categorías y las otras seis se imprimían en crudo en un PDF
que veía el cliente. El `default` silencioso lo mantuvo oculto.

El nombre para mostrar viaja **con** el valor, en el enum. Al agregar un valor nuevo la etiqueta
viaja con él y ningún servicio queda desactualizado. Esto es OCP aplicado donde sí paga.

## Cómo saber si el diseño quedó bien

No por revisar la lista de principios. Por estas tres señales:

1. **Puedes probar una regla de negocio sin levantar Postgres ni Spring.** Si para verificar "el
   costo promedio se recalcula así" necesitas infraestructura, la regla no está en el dominio.
2. **Agregar una forma de pago no toca `RegistrarVenta`.**
3. **Leer el caso de uso te cuenta la historia del negocio**, no la de la base de datos. Si dice
   `guardar`, `flush`, `mapear`, la infraestructura se filtró hacia adentro.

Si las tres se cumplen, no importa qué principio quedó "incompleto". Si alguna falla, el problema
está ahí y no en la lista.
