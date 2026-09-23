import { test } from 'node:test'
import assert from 'node:assert/strict'

import { DESTINO, notaDelProducido, producidoDelTurno } from './producido.js'

const venta = (extra) => ({
  estado: 'COBRADA', total: 0, descuento: 0, efectivo: 0, transferencia: 0, fiado: 0, ...extra,
})

/** El turno de la tienda del 21 de septiembre, tal como lo devolvió el servidor. */
const turnoReal = {
  fondo: 100_000,
  ventas: [
    venta({ numero: 1, total: 22_000, efectivo: 22_000 }),
    venta({ numero: 2, total: 22_000, efectivo: 15_000, transferencia: 7_000 }),
    venta({ numero: 3, total: 22_000, fiado: 22_000 }),
  ],
  arqueo: {
    ventasEfectivo: 37_000, ventasTransferencia: 7_000, ventasFiado: 22_000, descuentos: 0,
    devolucionesEfectivo: 0, abonosEfectivo: 22_000, abonosTransferencia: 0, gastosCajon: 0, retiros: 0,
    comprasCajon: 0, esperado: 159_000,
  },
}

test('el producido es lo vendido, y las formas de pago son los MISMOS pesos', () => {
  const p = producidoDelTurno(turnoReal)

  assert.equal(p.producido.total, 66_000)
  assert.equal(p.producido.ventas, 3)
  assert.deepEqual(p.formas, { efectivo: 37_000, transferencia: 7_000, fiado: 22_000, total: 66_000 })
  assert.equal(p.descuadre, 0)
})

test('UN ABONO NO ES PRODUCIDO: paga una venta de otro día, que ya se contó', () => {
  const p = producidoDelTurno(turnoReal)

  // Los $22.000 que abonó Julio no se suman a lo vendido: se sumarían dos veces.
  assert.equal(p.producido.total, 66_000)
  assert.equal(p.cartera.abonosEfectivo, 22_000)
  assert.equal(p.cartera.abonos, 22_000)
})

test('se responde solo por el efectivo: lo que debería haber en el cajón', () => {
  const p = producidoDelTurno(turnoReal)

  // fondo 100.000 + ventas en efectivo 37.000 + abono en efectivo 22.000
  assert.equal(p.efectivoQueSeResponde, 159_000)
})

test('una venta anulada no suma al producido ni a las formas de pago', () => {
  const p = producidoDelTurno({
    ventas: [
      venta({ total: 36_000, efectivo: 36_000 }),
      venta({ total: 18_000, efectivo: 18_000, estado: 'ANULADA' }),
    ],
    arqueo: { esperado: 136_000 },
  })

  assert.equal(p.producido.total, 36_000)
  assert.equal(p.producido.anuladas, 1)
  assert.equal(p.formas.efectivo, 36_000)
  assert.equal(p.descuadre, 0)
})

test('el descuento ya viene restado del total, y se cuenta aparte para decirlo', () => {
  const p = producidoDelTurno({
    ventas: [venta({ total: 35_000, descuento: 3_000, transferencia: 35_000 }), venta({ total: 18_000, efectivo: 18_000 })],
    arqueo: { esperado: 118_000 },
  })

  assert.equal(p.producido.total, 53_000)
  assert.equal(p.producido.descuentos, 3_000)
  assert.equal(p.producido.conDescuento, 1)
})

test('si las formas de pago no suman lo vendido, se dice: nunca se arregla la cifra', () => {
  const p = producidoDelTurno({ ventas: [venta({ total: 20_000, efectivo: 15_000 })], arqueo: { esperado: 115_000 } })

  assert.equal(p.descuadre, 5_000)
})

test('un turno cerrado lee sus abonos y su efectivo de lo que se firmó al cerrar', () => {
  const p = producidoDelTurno({
    ventas: [venta({ total: 10_000, efectivo: 10_000 })],
    arqueo: null,
    cierre: { abonosEfectivo: 5_000, abonosTransferencia: 3_000, esperado: 115_000, contado: 115_000, diferencia: 0 },
  })

  assert.equal(p.cartera.abonos, 8_000)
  assert.equal(p.efectivoQueSeResponde, 115_000)
})

test('un turno sin ventas produce $0, no falla', () => {
  const p = producidoDelTurno({ ventas: [], arqueo: { esperado: 100_000 } })

  assert.equal(p.producido.total, 0)
  assert.equal(p.formas.total, 0)
  assert.equal(p.cartera.abonos, 0)
})

test('la nota dice cuántas ventas, los descuentos y las anuladas', () => {
  const pesos = (n) => `$ ${n}`
  assert.equal(notaDelProducido({ ventas: 1, conDescuento: 0, descuentos: 0, anuladas: 0 }, pesos), '1 venta')
  assert.equal(notaDelProducido({ ventas: 3, conDescuento: 1, descuentos: 2000, anuladas: 1 }, pesos),
    '3 ventas · 1 con descuento ($ 2000) · 1 anulada no suma')
})

test('lo fiado va "a la cartera", nunca "lo deben": si abonó en el mismo turno, ya no lo debe', () => {
  // El turno real: se le fió a Julio y pagó en el mismo turno. Decir que "lo deben" sería falso.
  const p = producidoDelTurno(turnoReal)
  assert.equal(p.cartera.fiado, 22_000)
  assert.equal(p.cartera.abonosEfectivo, 22_000)
  assert.equal(DESTINO.fiado, 'a la cartera')
  assert.doesNotMatch(Object.values(DESTINO).join(' '), /deben/)
})
