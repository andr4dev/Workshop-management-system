/**
 * El producido del turno, sin JSX, para probarlo con `node --test`.
 *
 * Portado del corte de caja del car-wash (`cashSessionUtils.js`), que separa preguntas de naturaleza distinta y
 * no las mezcla en un solo total:
 *
 *   1. ¿Cuánto se VENDIÓ?          el producido: lo vendido en el turno, fiado incluido, sin las anuladas
 *   2. ¿Cómo lo PAGARON?           los MISMOS pesos, por forma de pago. Tienen que sumar el producido
 *   3. ¿Qué pasó con la CARTERA?   lo que quedó fiado y los abonos que se recibieron
 *   4. ¿Cuánto EFECTIVO debe haber? el arqueo. Es lo único por lo que responde quien cierra el turno
 *
 * <b>Un abono nunca suma al producido</b>: paga una venta de otro día, que ya se contó el día que se vendió.
 * Sumarlo contaría la misma mercancía dos veces. Sí entra al cajón si fue en efectivo, y por eso está en el 4.
 *
 * Las cifras de las ventas salen de `turno.ventas` (cada una con lo que puso en efectivo, transferencia y fiado);
 * las de los abonos y el cajón, del arqueo que calculó el servidor (`turno.arqueo` en vivo, `turno.cierre` si ya
 * se cerró). Aquí no se inventa ninguna cifra: se ordenan, y si no cuadran se dice.
 */

/**
 * @param turno como lo devuelve `GET /api/turnos/{id}`
 */
export function producidoDelTurno(turno) {
  const todas = turno?.ventas ?? []
  const vigentes = todas.filter((v) => v.estado !== 'ANULADA')
  const suma = (campo) => vigentes.reduce((total, v) => total + (v[campo] ?? 0), 0)
  const partes = turno?.cierre ?? turno?.arqueo ?? {}

  const producido = {
    total: suma('total'),
    ventas: vigentes.length,
    anuladas: todas.length - vigentes.length,
    descuentos: suma('descuento'),
    conDescuento: vigentes.filter((v) => (v.descuento ?? 0) > 0).length,
  }

  const formas = {
    efectivo: suma('efectivo'),
    transferencia: suma('transferencia'),
    fiado: suma('fiado'),
  }
  formas.total = formas.efectivo + formas.transferencia + formas.fiado

  const cartera = {
    fiado: formas.fiado,
    abonosEfectivo: partes.abonosEfectivo ?? 0,
    abonosTransferencia: partes.abonosTransferencia ?? 0,
  }
  cartera.abonos = cartera.abonosEfectivo + cartera.abonosTransferencia

  return {
    producido,
    formas,
    cartera,
    /** Si las formas de pago no suman lo vendido: un error de programa, se muestra en rojo. */
    descuadre: producido.total - formas.total,
    /** Lo que debería haber en el cajón: por esto, y solo por esto, responde quien cierra. */
    efectivoQueSeResponde: partes.esperado ?? null,
  }
}

/**
 * A dónde fue cada forma de pago, dicho corto.
 *
 * El fiado va "a la cartera", no "lo deben": si el cliente abonó en el mismo turno, ya no lo debe, y la etiqueta
 * mentiría con el abono a la vista en la misma tarjeta (el car-wash tuvo ese error: su etiqueta de crédito decía
 * siempre "queda a deber"). Adónde fue es un hecho del turno; cuánto se debe hoy es de la Cartera.
 */
export const DESTINO = {
  efectivo: 'al cajón',
  transferencia: 'a la cuenta',
  fiado: 'a la cartera',
}

/** "3 ventas · 1 con descuento ($ 2.000) · 1 anulada no suma". */
export function notaDelProducido({ ventas, conDescuento, descuentos, anuladas }, formato) {
  const partes = [`${ventas} ${ventas === 1 ? 'venta' : 'ventas'}`]
  if (conDescuento > 0) partes.push(`${conDescuento} con descuento (${formato(descuentos)})`)
  if (anuladas > 0) partes.push(`${anuladas} ${anuladas === 1 ? 'anulada no suma' : 'anuladas no suman'}`)
  return partes.join(' · ')
}
