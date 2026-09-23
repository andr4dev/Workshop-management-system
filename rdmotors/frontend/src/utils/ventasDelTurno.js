/**
 * Las ventas del turno (spec 0003, H7, H10 y RF-024): los totales, cómo se lee cada venta en la lista, y
 * buscar una por su número.
 */

/**
 * Cuánto se vendió en el turno y cómo entró la plata. Es el anticipo del arqueo (rebanada 3).
 *
 *   - Las ANULADAS no suman: se deshicieron, esa plata no entró.
 *   - En efectivo cuenta el MONTO del pago, no lo recibido: si pagó $38.000 con $50.000, al cajón
 *     entraron $38.000 (los $12.000 salieron de vuelta).
 *   - Una venta mixta suma su parte a cada lado.
 *
 *   - Lo fiado (spec 0008) no entró: se cuenta aparte, y el cliente lo debe.
 *
 * `descuadre` es lo que les falta a efectivo + transferencia + fiado para dar el total. Con datos del servidor
 * siempre es 0; si no, se dice en rojo.
 */
export function resumenDelTurno(ventas) {
  const vigentes = ventas.filter((v) => v.estado !== 'ANULADA')
  const resumen = {
    ventas: vigentes.length,
    anuladas: ventas.length - vigentes.length,
    total: 0,
    efectivo: 0,
    transferencia: 0,
    fiado: 0,
    conDescuento: 0,
  }
  for (const venta of vigentes) {
    resumen.total += venta.total
    if (venta.descuento > 0) resumen.conDescuento += 1
    resumen.fiado += venta.fiado ?? 0
    for (const pago of venta.pagos) {
      if (pago.forma === 'EFECTIVO') resumen.efectivo += pago.monto
      else if (pago.forma === 'TRANSFERENCIA') resumen.transferencia += pago.monto
    }
  }
  resumen.descuadre = resumen.total - resumen.efectivo - resumen.transferencia - resumen.fiado
  return resumen
}

/**
 * "Efectivo", "Transferencia", "Mixto", "Fiado" o "Fiado en parte". Una venta de $0 (todo descuento) no tiene pagos.
 *
 * @param fiado lo que quedó debiendo el cliente (spec 0008)
 */
export function textoDelPago(pagos, fiado = 0) {
  const formas = new Set(pagos.map((p) => p.forma))
  if (fiado > 0) return formas.size === 0 ? 'Fiado' : 'Fiado en parte'
  if (formas.size === 0) return 'Sin pago'
  if (formas.size > 1) return 'Mixto'
  return formas.has('EFECTIVO') ? 'Efectivo' : 'Transferencia'
}

/** "FILTRO DE ACEITE INOKI" o "FILTRO DE ACEITE INOKI y 2 más": lo justo para reconocerla en la lista. */
export function textoDeRepuestos(renglones) {
  if (renglones.length === 0) return ''
  const [primero] = renglones
  const nombre = [primero.nombre, primero.marca].filter(Boolean).join(' ')
  return renglones.length === 1 ? nombre : `${nombre} y ${renglones.length - 1} más`
}

/**
 * El número que se busca, como lo escriba el cajero: "12", "N.º 12", "#12" o "no. 12". `null` si no
 * hay un número entero positivo que buscar.
 */
export function numeroDeVenta(texto) {
  // "no." antes que "n": si no, de "No. 12" se comería solo la N y quedaría "o. 12".
  const limpio = String(texto ?? '').trim().replace(/^(no\.?|n\.?\s*º?|#)\s*/i, '')
  if (!/^\d{1,15}$/.test(limpio)) return null
  const numero = Number(limpio)
  return numero > 0 ? numero : null
}
