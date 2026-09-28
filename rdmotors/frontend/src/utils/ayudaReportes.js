/**
 * La ayuda (?) de cada cifra de los reportes (spec 0007, RF-016): qué es, cómo se calcula y qué NO incluye.
 *
 * En un solo sitio para que la misma cifra se explique igual en todas partes. El car-wash tuvo la misma explicación
 * en cuatro pantallas y terminaron diciendo cosas distintas.
 */
export const AYUDA = {
  ventasNetas: {
    titulo: 'Ventas netas',
    que: 'Lo que entró por ventas en el período, ya con los descuentos: lo de contado y lo que abonaron de lo fiado.',
    formula: 'Renglones cobrados − descuentos. Lo de contado cuenta el día de la venta; lo fiado, en la parte que se abona, el día en que entra (en hora de Colombia).',
    noIncluye: 'Lo fiado que todavía no se paga: está en la tarjeta del fiado. Tampoco las ventas anuladas, se hayan anulado cuando se hayan anulado.',
  },
  utilidadBruta: {
    titulo: 'Utilidad bruta',
    que: 'Lo que dejó la mercancía: lo cobrado menos lo que costó eso que se cobró.',
    formula: 'Ventas netas − costo de los repuestos vendidos − costos adicionales (las categorías de gasto que son costo, como un flete de mercancía). El costo es el que tenía cada repuesto el día que se vendió; de una venta fiada entra la parte que ya se cobró.',
    noIncluye: 'Los gastos del local. Y comprar mercancía no resta: el costo entra cuando se vende.',
  },
  gastos: {
    titulo: 'Gastos del local',
    que: 'Lo que costó tener la tienda abierta: almuerzos, aseo, papelería, arriendo.',
    formula: 'Los gastos no anulados de categorías de gasto con fecha en el período, del cajón y por fuera. Los gastos del mes, como se registró cada uno: repartidos día a día, o enteros en el reporte de su mes.',
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
    que: 'De cuánto es, en promedio, una venta.',
    formula: 'El total de las ventas que se completaron en el período ÷ cuántas son, redondeado al peso. Una fiada cuenta el día que se termina de pagar.',
  },
  descuentos: {
    titulo: 'Descuentos',
    que: 'Lo que se regaló en descuentos, y en cuántas ventas.',
    formula: 'La suma de los descuentos de las ventas cobradas. El porcentaje es sobre los renglones vendidos: renglones − descuentos = ventas netas.',
  },
  pagos: {
    titulo: 'Cómo pagaron',
    que: 'Cómo entró la plata de las ventas netas: de contado y en abonos a lo fiado.',
    formula: 'Efectivo + transferencia = ventas netas. "De abonos a fiados" es la parte que vino de abonos, y ya está sumada arriba.',
    noIncluye: 'Lo que el cajón devolvió al anular: eso es del cierre de caja.',
  },
  ventas: {
    titulo: 'Ventas',
    que: 'Cuántas ventas se completaron en el período, y sus unidades.',
    formula: 'Una de contado cuenta el día que se vende. Una fiada, el día que se termina de pagar: mientras tanto lo que abonan sí entra a las ventas netas, pero la venta no se cuenta.',
  },
  fiado: {
    titulo: 'Fiado',
    que: 'Lo que se vendió fiado en el período, lo que se cobró en abonos y lo que deben hoy.',
    formula: 'Lo fiado entra a las ventas netas a medida que lo abonan, con su parte del costo. Lo que se abona del saldo del cuaderno (deudas de antes del sistema) no es venta: se ve aquí.',
    noIncluye: 'Lo por cobrar no es de un período: es lo que deben hoy todos los clientes.',
  },
  gastosDelMes: {
    titulo: 'Gastos del mes',
    que: 'El arriendo, los servicios y los demás gastos registrados "del mes". Cada uno dice, al registrarlo, si se reparte o va en un día.',
    formula: 'Repartir día a día: cada día de su mes carga una parte igual (el arriendo de $800.000 en octubre carga $25.807 el día 1). Registrarlo en un día: cuenta entero en el reporte que cubre su mes, y no aparece en el de un día ni en el de una semana.',
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
