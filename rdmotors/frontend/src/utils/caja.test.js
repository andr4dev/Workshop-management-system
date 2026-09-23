import { test } from 'node:test'
import assert from 'node:assert/strict'
import { fondoDesdeTexto, problemaDelFondo } from './caja.js'

test('el fondo se lee con o sin puntos y signo de peso', () => {
  assert.equal(fondoDesdeTexto('100000'), 100000)
  assert.equal(fondoDesdeTexto('100.000'), 100000)
  assert.equal(fondoDesdeTexto('$ 100.000'), 100000)
})

test('vacío no es cero: la pantalla pide escribirlo', () => {
  assert.equal(fondoDesdeTexto(''), null)
  assert.equal(fondoDesdeTexto('   '), null)
  assert.match(problemaDelFondo(''), /aunque sea \$0/)
})

test('$0 es un fondo válido', () => {
  assert.equal(fondoDesdeTexto('0'), 0)
  assert.equal(problemaDelFondo('0'), null)
})

test('un monto absurdo de largo no pasa', () => {
  assert.equal(problemaDelFondo('9'.repeat(20)), 'Ese monto no es válido')
})
