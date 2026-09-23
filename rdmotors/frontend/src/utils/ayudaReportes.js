/**
 * La ayuda (?) de cada cifra de los reportes (spec 0007, RF-016): qué es, cómo se calcula y qué NO incluye.
 *
 * En un solo sitio para que la misma cifra se explique igual en todas partes. El car-wash tuvo la misma explicación
 * en cuatro pantallas y terminaron diciendo cosas distintas.
 */
export const AYUDA = {
  ventasNetas: {
    titulo: 'Ventas netas',
    que: 'Lo que pagaron los clientes por lo que se vendió en el período, ya con los descuentos.',
    formula: 'Renglones vendidos − descuentos dados. Cuenta el día en que se cobró, en hora de Colombia.',
    noIncluye: 'Las ventas anuladas, se hayan anulado cuando se hayan anulado: una venta del lunes anulada el miércoles sale del lunes.',
  },
  utilidadBruta: {
    titulo: 'Utilidad bruta',
    que: 'Lo que dejó la mercancía: lo vendido menos lo que costó.',
    formula: 'Ventas netas − costo de los repuestos vendidos − costos adicionales (las categorías de gasto que son costo, como un flete de mercancía). El costo es el que tenía cada repuesto el día que se vendió.',
    noIncluye: 'Los gastos del local. Y comprar mercancía no resta: el costo entra cuando se vende.',
  },
  gastos: {
    titulo: 'Gastos del local',
    que: 'Lo que costó tener la tienda abierta: almuerzos, aseo, papelería, arriendo.',
    formula: 'Los gastos no anulados de categorías de gasto con fecha en el período, del cajón y por fuera. Los gastos del mes, según cómo se lean (arriba, junto al período).',
    noIncluye: 'Los retiros del dueño, las compras de mercancía ni las diferencias de caja: no son gasto.',
  },
  utilidadOperativa: {
    titulo: 'Utilidad operativa',
    que: 'La ganancia: lo que quedó después de pagar la mercancía vendida y sostener el local.',
    formula: 'Utilidad bruta − gastos del local.',
    noIncluye: 'La plata del cajón. Esto es lo vendido y lo gastado en el período; lo que entró y salió de la caja es el cierre de caja.',
  },
  ticket: {
    titulo: 'Ticket promedio',
    que: 'Lo que dejó, en promedio, cada venta.',
    formula: 'Ventas netas ÷ número de ventas, redondeado al peso.',
  },
  descuentos: {
    titulo: 'Descuentos',
    que: 'Lo que se regaló en descuentos, y en cuántas ventas.',
    formula: 'La suma de los descuentos de las ventas cobradas. El porcentaje es sobre los renglones vendidos: renglones − descuentos = ventas netas.',
  },
  pagos: {
    titulo: 'Cómo pagaron',
    que: 'Cómo entró la plata de esas mismas ventas, y cuánto quedó fiado.',
    formula: 'Efectivo + transferencia + fiado = ventas netas. Lo fiado es venta el día que se vende, aunque se pague después.',
    noIncluye: 'Lo que el cajón devolvió al anular: eso es del cierre de caja.',
  },
  gastosDelMes: {
    titulo: 'Gastos del mes',
    que: 'El arriendo, la nómina y los demás gastos marcados "del mes" al registrarlos.',
    formula: 'Repartidos: cada día de su mes carga una parte igual (el arriendo de $800.000 en octubre carga $25.807 el día 1). Solo en el mes: cuentan enteros cuando el período cubre su mes, y no aparecen en un día ni en una semana.',
    noIncluye: 'Un gasto del mes cuenta en el mes de su fecha: el arriendo de septiembre pagado el 2 de octubre se registra con fecha de septiembre.',
  },
  diaPorDia: {
    titulo: 'Día por día',
    que: 'Cada día del período; si pasa de 62 días, cada semana de lunes a domingo.',
    formula: 'La fila de totales es igual a las cifras de arriba, al peso.',
  },
  sinCosto: {
    titulo: 'Sin costo',
    que: 'Repuestos que se vendieron sin costo conocido: nunca se compraron por el sistema.',
    formula: 'Su costo no se cuenta como $0: queda por fuera, y la utilidad sale más alta de lo que es. Registra su compra para que el próximo reporte lo tenga.',
  },
}
