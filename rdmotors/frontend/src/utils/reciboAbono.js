/**
 * El recibo del abono, en papel de 80 mm (spec 0008, RF-016): cuánto abonó, cómo, a qué ventas se aplicó y cuánto
 * sigue debiendo.
 *
 * Como el comprobante de la venta: **no recalcula nada**. Las cifras son las que devolvió el servidor, y si las partes
 * no cuadran se dice; nunca se "arregla" una cifra en el papel.
 */
import { formatoCOP } from './formato.js'
import { documento80mm, esc, fila } from './papel80mm.js'
import { fechaDelTicket, PIE_LEGAL } from './ticket.js'

const FORMAS = { EFECTIVO: 'Efectivo', TRANSFERENCIA: 'Transferencia' }

/**
 * @param abono  el que devuelve el servidor (`POST /api/abonos`, `GET /api/abonos/{id}`)
 * @param tienda los datos de la tienda (`GET /api/tienda`)
 */
export function armarRecibo(abono, tienda) {
  return {
    tienda: {
      nombre: tienda.nombreComercial,
      lineas: [tienda.nit && `NIT ${tienda.nit}`, tienda.direccion, tienda.telefono && `Tel. ${tienda.telefono}`]
        .filter(Boolean),
    },
    titulo: 'RECIBO DE ABONO',
    numero: abono.numero,
    fecha: fechaDelTicket(abono.recibidoEn),
    recibio: abono.recibidoPor?.nombre ?? null,
    cliente: [abono.cliente?.nombre, abono.cliente?.documento].filter(Boolean).join(' · '),
    monto: abono.monto,
    forma: FORMAS[abono.forma] ?? abono.forma,
    referencia: abono.referencia ?? null,
    nota: abono.nota ?? null,
    aplicaciones: abono.aplicaciones.map((a) => ({ deuda: a.deuda, monto: a.monto })),
    sinAplicar: abono.sinAplicar ?? 0,
    debeDespues: abono.debeDespues,
    anulado: abono.anuladoEn ? { fecha: fechaDelTicket(abono.anuladoEn), motivo: abono.motivoAnulacion } : null,
    pie: [tienda.mensajePie, PIE_LEGAL].filter(Boolean),
  }
}

/** Lo aplicado, más lo que quedó a favor, tiene que dar el abono. Vacía si cuadra. */
export function problemasDelRecibo(recibo) {
  const aplicado = recibo.aplicaciones.reduce((suma, a) => suma + Number(a.monto), 0)
  if (aplicado + recibo.sinAplicar !== recibo.monto) {
    return [`Lo aplicado (${formatoCOP(aplicado)}) y lo que queda a favor (${formatoCOP(recibo.sinAplicar)}) no dan `
      + `el abono de ${formatoCOP(recibo.monto)}`]
  }
  return []
}

export function htmlDelRecibo(recibo) {
  const cabecera = [
    `<div class="centro tienda">${esc(recibo.tienda.nombre)}</div>`,
    ...recibo.tienda.lineas.map((linea) => `<div class="centro dato">${esc(linea)}</div>`),
    `<div class="centro documento">${esc(recibo.titulo)}</div>`,
    recibo.anulado
      ? `<div class="anulada">ANULADO</div><div class="centro dato">el ${esc(recibo.anulado.fecha)}</div>`
      : '',
  ].join('')

  const datos = fila(`N.º ${recibo.numero}`, recibo.fecha)
    + `<div class="texto">Cliente: ${esc(recibo.cliente)}</div>`
    + (recibo.recibio ? `<div class="texto">Recibió: ${esc(recibo.recibio)}</div>` : '')

  const aplicaciones = recibo.aplicaciones.map((a) => fila(a.deuda, formatoCOP(a.monto), 'detalle')).join('')
    + (recibo.sinAplicar > 0 ? fila('Queda a favor', formatoCOP(recibo.sinAplicar), 'detalle') : '')

  return documento80mm(`Recibo de abono N.º ${recibo.numero}`, `${cabecera}
<hr>
${datos}
<hr>
${fila(`Abono en ${recibo.forma}`, formatoCOP(recibo.monto), 'total')}
${recibo.referencia ? `<div class="texto">Ref. ${esc(recibo.referencia)}</div>` : ''}
${recibo.nota ? `<div class="texto">${esc(recibo.nota)}</div>` : ''}
<hr>
<div class="etiqueta">SE APLICÓ A</div>
${aplicaciones}
<hr>
${fila('Debe en total', formatoCOP(recibo.debeDespues), 'total')}
<div class="pie">${recibo.pie.map((linea) => `<div>${esc(linea)}</div>`).join('')}</div>`)
}
