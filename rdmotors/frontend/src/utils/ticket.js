import { formatoCOP } from './formato.js'
import { documento80mm, esc, fila } from './papel80mm.js'

/**
 * El comprobante de 80 mm (spec 0003, RF-018 y RF-019). Todo lo que decide qué dice el ticket vive
 * aquí, puro y con pruebas; la pantalla y la impresora solo pintan lo que sale de aquí.
 *
 *   armarTicket(venta, tienda)  → qué dice, con las cifras TAL COMO LAS GUARDÓ el servidor
 *   problemasDelTicket(ticket)  → si sus partes no suman, por qué
 *   htmlDelTicket(ticket)       → el documento de 80 mm, el MISMO para imprimir y para ver en pantalla
 *
 * El ticket no recalcula nada: el total impreso y el del reporte salen del mismo sitio. Si las partes
 * no cuadran, se avisa; nunca se "arregla" una cifra en el papel.
 */

export const PIE_LEGAL = 'Comprobante interno · no es factura electrónica'

/** El comprobante va en hora de Colombia aunque el equipo tenga otra zona (spec 0003, §7). */
export const ZONA_HORARIA = 'America/Bogota'

const FORMAS = { EFECTIVO: 'Efectivo', TRANSFERENCIA: 'Transferencia' }

const FECHA = new Intl.DateTimeFormat('es-CO', {
  timeZone: ZONA_HORARIA,
  day: '2-digit', month: '2-digit', year: 'numeric', hour: 'numeric', minute: '2-digit',
})

/** "14/09/2026, 5:03 p. m.", en hora de Colombia. */
export const fechaDelTicket = (iso) => (iso ? FECHA.format(new Date(iso)) : '')

/**
 * @param venta   la venta como la devuelve el servidor (`POST /api/ventas`, `GET /api/ventas/{id}`)
 * @param tienda  los datos de la tienda (`GET /api/tienda`)
 * @param atendio quién atendió: por defecto, quien cobró la venta (`vendidoPor`, spec 0004, RF-023). Sin nombre,
 *                la línea no sale
 */
export function armarTicket(venta, tienda, { atendio = venta.vendidoPor?.nombre ?? null } = {}) {
  return {
    tienda: {
      nombre: tienda.nombreComercial,
      lineas: [
        tienda.nit && `NIT ${tienda.nit}`,
        tienda.direccion,
        tienda.telefono && `Tel. ${tienda.telefono}`,
      ].filter(Boolean),
    },
    titulo: 'COMPROBANTE DE PAGO',
    numero: venta.numero,
    fecha: fechaDelTicket(venta.cobradaEn),
    atendio,
    anulada: venta.estado === 'ANULADA' ? { fecha: fechaDelTicket(venta.anuladaEn) } : null,
    renglones: venta.renglones.map((r) => ({
      descripcion: [r.nombre, r.marca].filter(Boolean).join(' '),
      codigo: r.codigo,
      cantidad: r.cantidad,
      precioUnitario: r.precioUnitario,
      total: r.total,
    })),
    subtotal: venta.subtotal,
    descuento: venta.descuento > 0
      ? { monto: venta.descuento, porcentaje: venta.descuentoModo === 'PORCENTAJE' ? venta.descuentoPorcentaje : null }
      : null,
    total: venta.total,
    // Lo fiado va aparte de los pagos: no se pagó (spec 0008, decisión 4). Dice a nombre de quién y cuánto debía
    // después, tal como quedó guardado: al reimprimirlo dice lo mismo.
    fiado: venta.fiado > 0
      ? {
          monto: venta.fiado,
          cliente: [venta.cliente?.nombre, venta.cliente?.documento].filter(Boolean).join(' · '),
          debeDespues: venta.debeDespues ?? null,
        }
      : null,
    // En efectivo, lo recibido y el cambio solo si se escribió con cuánto pagó. Por transferencia,
    // solo el monto: no se dice a qué cuenta (spec 0003, decisión 2).
    pagos: venta.pagos.map((p) => {
      const conRecibido = p.forma === 'EFECTIVO' && p.recibido != null
      return {
        forma: FORMAS[p.forma] ?? p.forma,
        monto: p.monto,
        recibido: conRecibido ? p.recibido : null,
        cambio: conRecibido ? p.cambio : null,
      }
    }),
    pie: [tienda.mensajePie, PIE_LEGAL].filter(Boolean),
  }
}

const sumar = (lista, campo) => lista.reduce((suma, x) => suma + Number(x[campo] ?? 0), 0)

/**
 * RF-019: los renglones suman el total impreso. La base ya lo exige, así que una lista con algo es un
 * error de programa; se muestra en rojo y el ticket no sale solo por la impresora. Vacía si cuadra.
 */
export function problemasDelTicket(ticket) {
  const problemas = []
  for (const r of ticket.renglones) {
    if (r.cantidad * r.precioUnitario !== r.total) {
      problemas.push(`El renglón ${r.codigo} dice ${r.cantidad} × ${formatoCOP(r.precioUnitario)} = ${formatoCOP(r.total)}`)
    }
  }
  const renglones = sumar(ticket.renglones, 'total')
  if (renglones !== ticket.subtotal) {
    problemas.push(`Los renglones suman ${formatoCOP(renglones)} y el subtotal es ${formatoCOP(ticket.subtotal)}`)
  }
  const descuento = ticket.descuento?.monto ?? 0
  if (ticket.subtotal - descuento !== ticket.total) {
    problemas.push(`${formatoCOP(ticket.subtotal)} menos ${formatoCOP(descuento)} de descuento no da ${formatoCOP(ticket.total)}`)
  }
  const pagos = sumar(ticket.pagos, 'monto')
  const fiado = ticket.fiado?.monto ?? 0
  if (pagos + fiado !== ticket.total) {
    problemas.push(fiado > 0
      ? `Los pagos (${formatoCOP(pagos)}) y lo fiado (${formatoCOP(fiado)}) no dan el total de ${formatoCOP(ticket.total)}`
      : `Los pagos suman ${formatoCOP(pagos)} y el total es ${formatoCOP(ticket.total)}`)
  }
  return problemas
}

const porcentaje = (valor) => `${Number(valor).toLocaleString('es-CO', { maximumFractionDigits: 2 })} %`

/**
 * El documento completo, para papel de 80 mm. Es el mismo HTML el que se imprime y el que se ve en
 * pantalla (en un iframe): así "ver el comprobante" muestra exactamente lo que sale en papel.
 *
 * Nada se corta con puntos suspensivos: un nombre largo baja de línea. En un comprobante, un texto
 * cortado es un dato perdido.
 */
export function htmlDelTicket(ticket) {
  const cabecera = [
    `<div class="centro tienda">${esc(ticket.tienda.nombre)}</div>`,
    ...ticket.tienda.lineas.map((linea) => `<div class="centro dato">${esc(linea)}</div>`),
    `<div class="centro documento">${esc(ticket.titulo)}</div>`,
    ticket.anulada
      ? `<div class="anulada">ANULADA</div>${ticket.anulada.fecha ? `<div class="centro dato">el ${esc(ticket.anulada.fecha)}</div>` : ''}`
      : '',
  ].join('')

  const datos = fila(`N.º ${ticket.numero}`, ticket.fecha)
    + (ticket.atendio ? `<div class="texto">Atendió: ${esc(ticket.atendio)}</div>` : '')

  const renglones = ticket.renglones.map((r) =>
    `<div class="renglon"><div class="texto">${esc(r.descripcion)}</div>`
    + fila(`${r.codigo} · ${r.cantidad} × ${formatoCOP(r.precioUnitario)}`, formatoCOP(r.total), 'detalle')
    + '</div>').join('')

  // Sin descuento, subtotal y total son la misma cifra: se imprime una sola vez.
  const totales = (ticket.descuento
    ? fila('Subtotal', formatoCOP(ticket.subtotal))
      + fila(`Descuento${ticket.descuento.porcentaje != null ? ` (${porcentaje(ticket.descuento.porcentaje)})` : ''}`,
        `-${formatoCOP(ticket.descuento.monto)}`)
    : '')
    + fila('TOTAL', formatoCOP(ticket.total), 'total')

  const pagos = ticket.pagos.map((p) =>
    fila(p.forma, formatoCOP(p.monto))
    + (p.recibido != null ? fila('Recibido', formatoCOP(p.recibido), 'sangria') + fila('Cambio', formatoCOP(p.cambio), 'sangria') : ''))
    .join('')

  const fiado = ticket.fiado
    ? fila('Fiado', formatoCOP(ticket.fiado.monto))
      + `<div class="etiqueta">FIADO A</div><div class="texto">${esc(ticket.fiado.cliente)}</div>`
      + (ticket.fiado.debeDespues != null ? fila('Debe en total', formatoCOP(ticket.fiado.debeDespues), 'total') : '')
    : ''

  const pie = ticket.pie.map((linea) => `<div>${esc(linea)}</div>`).join('')

  return documento80mm(`Comprobante N.º ${ticket.numero}`, `${cabecera}
<hr>
${datos}
<hr>
${renglones}
<hr>
${totales}
<hr>
<div class="etiqueta">PAGO</div>
${pagos}
${fiado}
<div class="pie">${pie}</div>`)
}

/** Una venta de muestra para ver cómo quedan los datos de la tienda en el comprobante, sin vender nada. */
export const VENTA_DE_EJEMPLO = Object.freeze({
  numero: 1,
  estado: 'COBRADA',
  cobradaEn: '2026-09-14T22:03:00Z',
  subtotal: 33_000,
  descuento: 0,
  total: 33_000,
  vendidoPor: { id: 'ejemplo', nombre: 'Carolina' },
  renglones: [
    { codigo: 'ABC123', nombre: 'FILTRO DE ACEITE', marca: 'INOKI', cantidad: 1, precioUnitario: 24_000, total: 24_000 },
    { codigo: '352B59K', nombre: 'FILTRO ACEITE', marca: 'INOKI', cantidad: 1, precioUnitario: 9_000, total: 9_000 },
  ],
  pagos: [{ forma: 'EFECTIVO', monto: 33_000, recibido: 50_000, cambio: 17_000 }],
})
