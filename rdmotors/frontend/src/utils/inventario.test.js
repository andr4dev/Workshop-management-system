import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  direccionesDelPromedio, documentoDe, entradaSalida, estadoStock, fechaLocal, nombreMovimiento,
  paginaDeLaUrl, rangoDePagina,
} from './inventario.js'

test('estadoStock separa agotado de bajo', () => {
  assert.equal(estadoStock(0, 5), 'agotado')
  assert.equal(estadoStock(3, 5), 'bajo')
  assert.equal(estadoStock(5, 5), 'bajo', 'en el mínimo ya hay que pedir: igual que el backend')
  assert.equal(estadoStock(6, 5), 'ok')
})

test('estadoStock: con mínimo 0, cero unidades sigue siendo agotado', () => {
  assert.equal(estadoStock(0, 0), 'agotado')
  assert.equal(estadoStock(1, 0), 'ok')
})

test('entradaSalida pone la cantidad en su columna y deja la otra vacía', () => {
  assert.deepEqual(entradaSalida(15), { entrada: 15, salida: null })
  assert.deepEqual(entradaSalida(-2), { entrada: null, salida: 2 })
  assert.deepEqual(entradaSalida(0), { entrada: null, salida: null })
})

test('documentoDe arma proveedor y factura, y no inventa nada si falta', () => {
  assert.equal(documentoDe({ proveedor: 'Jotapartes', factura: 'FV-1' }), 'Jotapartes · FV-1')
  assert.equal(documentoDe({ proveedor: 'Jotapartes', factura: null }), 'Jotapartes')
  assert.equal(documentoDe({ tipo: 'VENTA' }), null)
  assert.equal(documentoDe(null), null)
})

test('nombreMovimiento traduce, y un tipo nuevo no desaparece', () => {
  assert.equal(nombreMovimiento('DEVOLUCION'), 'Devolución')
  assert.equal(nombreMovimiento('CORRECCION_COMPRA'), 'Corrección')
  assert.equal(nombreMovimiento('ANULACION_COMPRA'), 'Anulación')
  assert.equal(nombreMovimiento('TRASLADO'), 'TRASLADO')
})

test('direccionesDelPromedio compara cada fila con la ANTERIOR en el tiempo', () => {
  // Del más reciente al más antiguo, como llega del backend
  const kardex = [
    { tipo: 'VENTA', costoPromedioDespues: '9.0000' },    // vender no mueve el promedio
    { tipo: 'COMPRA', costoPromedioDespues: '9.0000' },   // 50 a 10 + 50 a 8 → 9
    { tipo: 'COMPRA', costoPromedioDespues: '10.0000' },  // la primera
  ]
  assert.deepEqual(direccionesDelPromedio(kardex), [null, 'baja', null])
})

test('direccionesDelPromedio: sin promedio no hay dirección, ni cuenta como cero', () => {
  const kardex = [
    { costoPromedioDespues: '12.0000' },
    { costoPromedioDespues: null },
  ]
  assert.deepEqual(direccionesDelPromedio(kardex), [null, null])
  assert.deepEqual(direccionesDelPromedio(undefined), [])
})

test('direccionesDelPromedio compara números, no textos', () => {
  // Como texto, "9.0000" > "10.0000". Como número, no.
  const kardex = [{ costoPromedioDespues: '10.0000' }, { costoPromedioDespues: '9.0000' }]
  assert.deepEqual(direccionesDelPromedio(kardex), ['sube', null])
})

test('rangoDePagina', () => {
  assert.equal(rangoDePagina(0, 25, 132), '1–25 de 132')
  assert.equal(rangoDePagina(5, 25, 132), '126–132 de 132')
  assert.equal(rangoDePagina(0, 25, 0), '0 de 0')
})

test('paginaDeLaUrl nunca revienta con lo que se escriba en la barra', () => {
  assert.equal(paginaDeLaUrl('3'), 3)
  assert.equal(paginaDeLaUrl(null), 0)
  assert.equal(paginaDeLaUrl('-2'), 0)
  assert.equal(paginaDeLaUrl('abc'), 0)
  assert.equal(paginaDeLaUrl('1.5'), 0)
})

test('fechaLocal no le quita un día a la factura en Colombia', () => {
  const fecha = fechaLocal('2026-09-01')
  assert.equal(fecha.getFullYear(), 2026)
  assert.equal(fecha.getMonth(), 8)
  assert.equal(fecha.getDate(), 1)
  assert.equal(fechaLocal(null), null)
  assert.equal(fechaLocal('basura'), null)
})
