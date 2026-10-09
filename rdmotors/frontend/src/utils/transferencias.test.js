import { test } from 'node:test'
import assert from 'node:assert/strict'
import { filasDeTransferencias, problemasDeTransferencias, salidasDeTransferencias } from './transferencias.js'

// El ejemplo: arranca con $599.000, entran $200.000 y se pagan $500.000 a Global.
const cuadre = {
  saldoInicial: 599000, ventas: 200000, abonos: 0, desdeElCajon: 0, devoluciones: 0, compras: 500000, gastos: 0,
  retiros: 0, esperado: 299000, contado: null, diferencia: null,
  comprasPorTransferencia: [{ id: 'c1', fechaRegistro: '2026-10-09T15:00:00Z', proveedor: 'Global repuestos',
    numeroFactura: '60485', total: 500000, estado: 'VIGENTE' }],
  gastosPorTransferencia: [],
}

test('el cuadre: con cuánto arrancó, las ventas siempre, y solo las partes que no son cero', () => {
  const filas = filasDeTransferencias(cuadre)
  assert.deepEqual(filas.map((f) => [f.clave, f.signo * f.monto]),
    [['saldoInicial', 599000], ['ventas', 200000], ['compras', -500000]])
  assert.deepEqual(problemasDeTransferencias(cuadre), [])
})

test('sin saldo al abrir no hay fila de arranque ni con qué comparar', () => {
  const sinSaldo = { ...cuadre, saldoInicial: null, esperado: null }
  assert.equal(filasDeTransferencias(sinSaldo)[0].clave, 'ventas')
  assert.deepEqual(problemasDeTransferencias(sinSaldo), [])
})

test('si las partes no suman lo que debería haber, se dice', () => {
  assert.equal(problemasDeTransferencias({ ...cuadre, esperado: 300000 }).length, 1)
})

test('de dónde salió: la compra, el gasto y los retiros que movieron la cuenta, en orden; lo anulado no cuenta', () => {
  const turno = {
    transferencias: {
      ...cuadre,
      gastosPorTransferencia: [{ id: 'g1', registradoEn: '2026-10-09T14:00:00Z', categoria: 'Arriendo',
        descripcion: 'Octubre', monto: 800000, anuladoEn: '2026-10-09T14:05:00Z' }],
    },
    retiros: [
      { id: 'r1', registradoEn: '2026-10-09T13:00:00Z', motivo: 'Consignado', monto: 300000, tipo: 'A_TRANSFERENCIAS' },
      { id: 'r2', registradoEn: '2026-10-09T16:00:00Z', motivo: 'Para Rubén', monto: 50000, tipo: 'DE_TRANSFERENCIAS' },
      { id: 'r3', registradoEn: '2026-10-09T17:00:00Z', motivo: 'Del cajón', monto: 10000, tipo: 'DEL_CAJON' },
    ],
  }
  const salidas = salidasDeTransferencias(turno)
  assert.deepEqual(salidas.map((s) => [s.titulo, s.signo * s.monto, s.anulado]), [
    ['Pasado del cajón', 300000, false],
    ['Arriendo', -800000, true],
    ['Compra', -500000, false],
    ['Sacado de la cuenta', -50000, false],
  ])
  assert.equal(salidas[2].detalle, 'Global repuestos · 60485')
})
