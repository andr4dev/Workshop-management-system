import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  camposQueFaltan, clienteNuevoDesde, datosDelCliente, identificacion, loQueFaltaEnPalabras, normalizarCelular,
  normalizarDocumento, porQueNoSeLeFia, problemasDeDatos, textoDeuda,
} from './clientes.js'

test('la cédula y el celular se comparan sin puntos, guiones ni espacios, como en el servidor', () => {
  assert.equal(normalizarDocumento('1.234.567-8'), '12345678')
  assert.equal(normalizarDocumento(' 900.123.456-7 '), '9001234567')
  assert.equal(normalizarDocumento('pa 1234x'), 'PA1234X')
  assert.equal(normalizarCelular('300 123 4567'), '3001234567')
  assert.equal(normalizarDocumento(null), '')
})

test('basta el nombre, también para fiar: la cédula y el celular se piden, no se exigen', () => {
  const soloNombre = { nombre: 'Juan', documento: '', celular: '' }
  assert.deepEqual(problemasDeDatos(soloNombre), {})
  assert.deepEqual(problemasDeDatos({ nombre: 'Juan', documento: '1.234.567', celular: '3001234567' }), {})
})

test('sin nombre no hay cliente; una cédula corta o un celular con letras se dicen antes de mandar', () => {
  assert.equal(problemasDeDatos({ nombre: '  ' }).nombre, 'Escribe el nombre del cliente')
  assert.match(problemasDeDatos({ nombre: 'Juan', documento: '12-3' }).documento, /5 a 15/)
  assert.match(problemasDeDatos({ nombre: 'Juan', celular: '12 34' }).celular, /7 a 15/)
})

test('lo único que impide fiar es el fiado cerrado; que falten datos, no', () => {
  assert.equal(porQueNoSeLeFia(null), 'Escoge a quién se le fía')
  assert.equal(porQueNoSeLeFia({ nombre: 'Pedro', documento: null, celular: null, fiadoCerrado: false }), null)
  assert.match(porQueNoSeLeFia({ nombre: 'Juan', fiadoCerrado: true }), /no se le fía/)
  assert.equal(porQueNoSeLeFia({ nombre: 'Juan', fiadoCerrado: false }), null)
})

test('lo que le falta se dice en palabras, para recordarlo sin frenar la venta', () => {
  assert.equal(loQueFaltaEnPalabras({ documento: null, celular: null }), 'la cédula o NIT y el celular')
  assert.equal(loQueFaltaEnPalabras({ documento: '123456', celular: null }), 'el celular')
  assert.equal(loQueFaltaEnPalabras({ documento: '123456', celular: '3001234567' }), null)
})

test('lo que falta, cómo se reconoce y cómo se dice lo que debe', () => {
  assert.deepEqual(camposQueFaltan({ documento: '123456', celular: null }), ['celular'])
  assert.equal(identificacion({ documento: '1.234.567', celular: '300 123 4567' }), '1.234.567 · 300 123 4567')
  assert.equal(identificacion({ documento: null, celular: '300' }), '300')
  assert.match(textoDeuda(50_000), /^Debe \$\s?50\.000$/)
  assert.equal(textoDeuda(0), 'Al día')
  assert.deepEqual(datosDelCliente({ nombre: 'Ana', documento: null }),
    { nombre: 'Ana', documento: '', celular: '', direccion: '', nota: '' })
})

test('lo buscado arranca el cliente nuevo: números son la cédula, lo demás el nombre', () => {
  assert.equal(clienteNuevoDesde('1.234.567').documento, '1.234.567')
  assert.equal(clienteNuevoDesde('1.234.567').nombre, '')
  assert.equal(clienteNuevoDesde(' Juan Pérez ').nombre, 'Juan Pérez')
  assert.equal(clienteNuevoDesde('').nombre, '')
})
