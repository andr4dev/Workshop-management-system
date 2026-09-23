import { test } from 'node:test'
import assert from 'node:assert/strict'
import { armarRecibo, htmlDelRecibo, problemasDelRecibo } from './reciboAbono.js'

const tienda = {
  nombreComercial: 'RD MOTORS',
  nit: '900.123.456-7',
  direccion: 'Calle 10 # 5-20',
  telefono: '300 123 4567',
  mensajePie: 'Gracias por su compra',
}

/** El abono del ejemplo del spec: $60.000 que pagan la 41 y abonan la 57. */
const abono = {
  id: 'a-7',
  numero: 7,
  cliente: { id: 'c-juan', nombre: 'Juan Pérez', documento: '1.234.567', celular: '300 123 4567', debe: 20_000 },
  monto: 60_000,
  forma: 'EFECTIVO',
  referencia: null,
  nota: null,
  recibidoEn: '2026-09-20T15:00:00Z',
  recibidoPor: { id: 'u-carolina', nombre: 'Carolina Ruiz' },
  debeDespues: 20_000,
  debeAhora: 20_000,
  sinAplicar: 0,
  aplicaciones: [
    { deudaId: 'd-41', deuda: 'la venta N.º 41', monto: 50_000 },
    { deudaId: 'd-57', deuda: 'la venta N.º 57', monto: 10_000 },
  ],
  anuladoEn: null,
  anuladoPor: null,
  motivoAnulacion: null,
}

test('el recibo dice cuánto abonó, cómo, a qué ventas fue y cuánto sigue debiendo', () => {
  const r = armarRecibo(abono, tienda)

  assert.equal(r.titulo, 'RECIBO DE ABONO')
  assert.equal(r.numero, 7)
  assert.equal(r.cliente, 'Juan Pérez · 1.234.567')
  assert.equal(r.forma, 'Efectivo')
  assert.equal(r.recibio, 'Carolina Ruiz')
  assert.deepEqual(r.aplicaciones.map((a) => a.deuda), ['la venta N.º 41', 'la venta N.º 57'])
  assert.deepEqual(problemasDelRecibo(r), [])

  const html = htmlDelRecibo(r)
  assert.match(html, /RECIBO DE ABONO/)
  assert.match(html, /la venta N\.º 41/)
  assert.match(html, /Debe en total/)
  assert.doesNotMatch(html, /ANULADO/)
})

test('si lo aplicado y lo que queda a favor no dan el abono, se dice', () => {
  const r = armarRecibo({ ...abono, monto: 70_000 }, tienda)
  assert.equal(problemasDelRecibo(r).length, 1)
  assert.match(problemasDelRecibo(r)[0], /no dan el abono/)
})

test('lo que queda a favor sale en el recibo, y un abono anulado se marca', () => {
  const aFavor = armarRecibo({ ...abono, monto: 80_000, sinAplicar: 20_000 }, tienda)
  assert.deepEqual(problemasDelRecibo(aFavor), [])
  assert.match(htmlDelRecibo(aFavor), /Queda a favor/)

  const anulado = armarRecibo({ ...abono, anuladoEn: '2026-09-20T16:00:00Z', motivoAnulacion: 'Se registró dos veces' },
    tienda)
  assert.match(htmlDelRecibo(anulado), /ANULADO/)
})
