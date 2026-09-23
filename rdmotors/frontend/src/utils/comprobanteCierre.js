import { diferenciaEnPalabras, filasDelDesglose, sumaDelDesglose } from './arqueo.js'
import { formatoCOP } from './formato.js'
import { documento80mm, esc, fila } from './papel80mm.js'
import { fechaDelTicket } from './ticket.js'

/**
 * El comprobante del cierre de caja, de 80 mm (spec 0006, H6 y RF-019): se imprime al cerrar y se guarda con
 * la plata. Como el de la venta, todo lo que decide qué dice vive aquí, puro y con pruebas.
 *
 *   armarComprobanteCierre(turno, tienda)  → qué dice, con las cifras TAL COMO SE GUARDARON al cerrar
 *   problemasDelComprobanteCierre(c)       → si sus partes no cuadran, por qué
 *   htmlDelComprobanteCierre(c)            → el documento, el mismo para imprimir y para ver en pantalla
 */

export const PIE_CIERRE = 'Documento interno de caja'

const sumar = (lista) => lista.reduce((suma, x) => suma + x.monto, 0)

/**
 * @param turno  el turno cerrado como lo devuelve `GET /api/turnos/{id}`
 * @param tienda los datos de la tienda (`GET /api/tienda`)
 */
export function armarComprobanteCierre(turno, tienda) {
  const cierre = turno.cierre
  const gastos = (turno.gastos ?? []).filter((g) => !g.anuladoEn)
  const porCategoria = new Map()
  for (const g of gastos) {
    const previo = porCategoria.get(g.categoria) ?? { categoria: g.categoria, gastos: 0, monto: 0 }
    porCategoria.set(g.categoria, { ...previo, gastos: previo.gastos + 1, monto: previo.monto + g.monto })
  }

  // Lo que se devolvió en efectivo en este turno: las ventas de este turno anuladas aquí mismo, y las de
  // turnos anteriores que se anularon hoy. Una de hoy anulada mañana no sale: resta mañana.
  const anuladasAqui = [
    ...(turno.ventas ?? []).filter((v) => v.anuladaEnTurnoId === turno.id),
    ...(turno.anuladasDeOtrosTurnos ?? []),
  ].filter((v) => v.efectivo > 0)

  return {
    tienda: {
      nombre: tienda.nombreComercial,
      lineas: [tienda.nit && `NIT ${tienda.nit}`, tienda.direccion, tienda.telefono && `Tel. ${tienda.telefono}`]
        .filter(Boolean),
    },
    titulo: 'CIERRE DE CAJA',
    abrio: fechaDelTicket(turno.abiertoEn),
    cerro: fechaDelTicket(turno.cerradoEn),
    desglose: filasDelDesglose(turno),
    esperado: cierre.esperado,
    contado: cierre.contado,
    diferencia: cierre.diferencia,
    ventasEfectivo: cierre.ventasEfectivo,
    ventasTransferencia: cierre.ventasTransferencia,
    ventasFiado: cierre.ventasFiado ?? 0,
    descuentos: cierre.descuentos,
    abonosEfectivo: cierre.abonosEfectivo ?? 0,
    abonosTransferencia: cierre.abonosTransferencia ?? 0,
    devolucionesEfectivo: cierre.devolucionesEfectivo,
    gastosCajon: cierre.gastosCajon,
    retirosTotal: cierre.retiros,
    comprasCajon: cierre.comprasCajon,
    ventas: (turno.ventas ?? []).length,
    anulaciones: anuladasAqui.map((v) => ({ numero: v.numero, monto: v.efectivo })),
    gastosPorCategoria: [...porCategoria.values()].sort((a, b) => b.monto - a.monto),
    retiros: (turno.retiros ?? []).filter((r) => !r.anuladoEn).map((r) => ({ motivo: r.motivo, monto: r.monto })),
    // Una compra de caja se puede anular después del cierre (su arqueo no cambia): sale marcada.
    compras: (turno.compras ?? []).map((c) => ({
      texto: [c.proveedor, c.numeroFactura].filter(Boolean).join(' · '),
      monto: c.total,
      anulada: c.estado === 'ANULADA',
    })),
    observaciones: turno.observaciones ?? null,
    // Los abonos de clientes del turno (spec 0008): el efectivo entró al cajón, la transferencia no.
    abonos: (turno.abonos ?? []).filter((a) => !a.anuladoEn)
      .map((a) => ({ numero: a.numero, cliente: a.cliente, monto: a.monto, forma: a.forma })),
    pie: [tienda.mensajePie, PIE_CIERRE].filter(Boolean),
  }
}

/**
 * RF-019: las cifras del comprobante cuadran con lo que debería haber. Vacía si cuadra. Las compras no se
 * cruzan con su lista: una anulada después del cierre sigue contada en él.
 */
export function problemasDelComprobanteCierre(c) {
  const problemas = []
  const suma = sumaDelDesglose(c.desglose)
  if (suma !== c.esperado) {
    problemas.push(`El desglose suma ${formatoCOP(suma)} y lo que debería haber es ${formatoCOP(c.esperado)}`)
  }
  if (c.contado - c.esperado !== c.diferencia) {
    problemas.push(`${formatoCOP(c.contado)} contados menos ${formatoCOP(c.esperado)} no da ${formatoCOP(c.diferencia)}`)
  }
  const gastos = sumar(c.gastosPorCategoria)
  if (gastos !== c.gastosCajon) {
    problemas.push(`Los gastos por categoría suman ${formatoCOP(gastos)} y el cierre dice ${formatoCOP(c.gastosCajon)}`)
  }
  const retiros = sumar(c.retiros)
  if (retiros !== c.retirosTotal) {
    problemas.push(`Los retiros suman ${formatoCOP(retiros)} y el cierre dice ${formatoCOP(c.retirosTotal)}`)
  }
  const devuelto = sumar(c.anulaciones)
  if (devuelto !== c.devolucionesEfectivo) {
    problemas.push(`Las anulaciones suman ${formatoCOP(devuelto)} y el cierre dice ${formatoCOP(c.devolucionesEfectivo)}`)
  }
  const abonosEnEfectivo = sumar(c.abonos.filter((a) => a.forma === 'EFECTIVO'))
  if (abonosEnEfectivo !== c.abonosEfectivo) {
    problemas.push(`Los abonos en efectivo suman ${formatoCOP(abonosEnEfectivo)} y el cierre dice `
      + `${formatoCOP(c.abonosEfectivo)}`)
  }
  return problemas
}

const conSigno = (f) => (f.signo < 0 ? `-${formatoCOP(f.monto)}` : formatoCOP(f.monto))

export function htmlDelComprobanteCierre(c) {
  const cabecera = [
    `<div class="centro tienda">${esc(c.tienda.nombre)}</div>`,
    ...c.tienda.lineas.map((linea) => `<div class="centro dato">${esc(linea)}</div>`),
    `<div class="centro documento">${esc(c.titulo)}</div>`,
  ].join('')

  const turno = fila('Abrió', c.abrio) + fila('Cerró', c.cerro)

  const desglose = '<div class="etiqueta">DEBERÍA HABER</div>'
    + c.desglose.map((f) => fila(f.etiqueta, conSigno(f))).join('')
    + fila('DEBERÍA HABER', formatoCOP(c.esperado), 'total')

  const diferencia = diferenciaEnPalabras(c.diferencia)
  const conteo = fila('Contado', formatoCOP(c.contado), 'total')
    + `<div class="${c.diferencia === 0 ? 'centro texto' : 'anulada'}">${esc(diferencia.texto.toUpperCase())}</div>`
    + (c.observaciones ? `<div class="texto">Observaciones: ${esc(c.observaciones)}</div>` : '')

  const ventas = `<div class="etiqueta">VENTAS DEL TURNO (${c.ventas})</div>`
    + fila('En efectivo', formatoCOP(c.ventasEfectivo))
    + fila('Por transferencia (no entra al cajón)', formatoCOP(c.ventasTransferencia), 'detalle')
    + (c.ventasFiado > 0 ? fila('Fiado (lo deben los clientes)', formatoCOP(c.ventasFiado), 'detalle') : '')
    + (c.descuentos > 0 ? fila('Descuentos dados', formatoCOP(c.descuentos), 'detalle') : '')

  const seccion = (titulo, lista, texto) => (lista.length === 0 ? ''
    : `<hr><div class="etiqueta">${titulo}</div>` + lista.map(texto).join(''))

  const detalle = seccion('ABONOS DE CLIENTES', c.abonos,
    (a) => fila(`${a.cliente}${a.forma === 'TRANSFERENCIA' ? ' (transferencia)' : ''}`,
      a.forma === 'TRANSFERENCIA' ? formatoCOP(a.monto) : `+${formatoCOP(a.monto)}`))
    + seccion('ANULACIONES', c.anulaciones, (a) => fila(`Venta N.º ${a.numero}`, `-${formatoCOP(a.monto)}`))
    + seccion('GASTOS DEL CAJÓN', c.gastosPorCategoria,
      (g) => fila(`${g.categoria}${g.gastos > 1 ? ` (${g.gastos})` : ''}`, `-${formatoCOP(g.monto)}`))
    + seccion('RETIROS', c.retiros, (r) => fila(r.motivo, `-${formatoCOP(r.monto)}`))
    + seccion('COMPRAS DEL CAJÓN', c.compras,
      (compra) => fila(`${compra.texto}${compra.anulada ? ' (anulada después)' : ''}`, `-${formatoCOP(compra.monto)}`))

  const pie = c.pie.map((linea) => `<div>${esc(linea)}</div>`).join('')

  return documento80mm(`Cierre de caja · ${c.cerro}`, `${cabecera}
<hr>
${turno}
<hr>
${desglose}
<hr>
${conteo}
<hr>
${ventas}
${detalle}
<div class="pie">${pie}</div>`)
}
