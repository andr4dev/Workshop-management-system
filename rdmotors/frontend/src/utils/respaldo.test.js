import { test } from 'node:test'
import assert from 'node:assert/strict'

import {
  avisoDelRespaldo, cuandoEnPalabras, diaDe, duracionEnPalabras, haceCuanto, pesoEnPalabras, textoDeLaCopia,
} from './respaldo.js'

const HOY = '2026-09-21'

const copia = (extra = {}) => ({
  id: 'r-1',
  hechoEn: '2026-09-21T07:00:00Z',
  archivo: 'rdmotors-2026-09-21-090000.dump',
  bytes: 4_404_019,
  duracionMs: 1_400,
  estado: 'HECHO',
  error: null,
  origen: 'A_MANO',
  ...extra,
})

test('el peso se dice en la unidad que se entiende de un vistazo', () => {
  assert.equal(pesoEnPalabras(900), '900 bytes')
  assert.equal(pesoEnPalabras(4_404_019), '4,2 MB')
  assert.equal(pesoEnPalabras(120_000), '117,2 KB')
  assert.equal(pesoEnPalabras(null), '')
})

test('lo que tardó, en milisegundos o en segundos', () => {
  assert.equal(duracionEnPalabras(340), '340 ms')
  assert.equal(duracionEnPalabras(1_400), '1,4 s')
})

test('las 2 a. m. de Colombia son del día de Colombia, no del día UTC', () => {
  // Las 2 a. m. del 21 en Colombia son las 07:00 UTC del 21: el mismo día.
  assert.equal(diaDe('2026-09-21T07:00:00Z'), '2026-09-21')
  // Pero las 22:00 del 20 en Colombia son las 03:00 UTC del 21: el día de la tienda es el 20.
  assert.equal(diaDe('2026-09-21T03:00:00Z'), '2026-09-20')
})

test('cuándo fue, en palabras', () => {
  assert.equal(cuandoEnPalabras('2026-09-21T07:00:00Z', HOY), 'hoy')
  assert.equal(cuandoEnPalabras('2026-09-20T07:00:00Z', HOY), 'ayer')
  assert.equal(cuandoEnPalabras('2026-09-18T07:00:00Z', HOY), 'hace 3 días')
  assert.equal(cuandoEnPalabras(null, HOY), '')
})

test('hace cuánto, a partir del número de días del servidor', () => {
  assert.equal(haceCuanto(0), 'hoy')
  assert.equal(haceCuanto(1), 'ayer')
  assert.equal(haceCuanto(9), 'hace 9 días')
  assert.equal(haceCuanto(null), '')
})

test('sin nada que avisar, no se avisa', () => {
  assert.equal(avisoDelRespaldo({ hayQueAvisar: false, copias: [copia()], diasSinBajar: 0 }), null)
  assert.equal(avisoDelRespaldo(null), null)
})

test('NUNCA SE BAJÓ NINGUNA: se dice qué se perdería, no "error"', () => {
  const aviso = avisoDelRespaldo({ hayQueAvisar: true, copias: [], diasSinBajar: null })

  assert.equal(aviso.tono, 'GRAVE')
  assert.match(aviso.texto, /Nunca te has bajado una copia/)
  assert.match(aviso.texto, /el inventario, las ventas ni lo que te deben/)
})

test('el último intento falló: se dice por qué y de cuándo es la última buena', () => {
  const fallo = copia({ estado: 'FALLO', bytes: null, error: 'No encontré pg_dump' })

  const aviso = avisoDelRespaldo({ hayQueAvisar: true, copias: [fallo, copia()], diasSinBajar: 3 })

  assert.equal(aviso.tono, 'GRAVE')
  assert.match(aviso.texto, /No encontré pg_dump/)
  assert.match(aviso.texto, /hace 3 días/)
})

test('hace más de una semana que no se baja una: se avisa sin alarmar', () => {
  const aviso = avisoDelRespaldo({ hayQueAvisar: true, copias: [copia()], diasSinBajar: 9 })

  assert.equal(aviso.tono, 'AVISO')
  assert.match(aviso.texto, /hace 9 días/)
  assert.match(aviso.texto, /Baja una nueva/)
})

test('EL AVISO NO HABLA DE CARPETAS NI DE DISCOS: la copia ya no vive en ningún computador nuestro', () => {
  // Antes decía "la copia quedó en C:/…". Si eso se cuela otra vez, el dueño va a buscar en el servidor un archivo
  // que solo está en su propio equipo — o peor, va a creer que el sistema se lo guarda.
  const textos = [
    avisoDelRespaldo({ hayQueAvisar: true, copias: [], diasSinBajar: null }).texto,
    avisoDelRespaldo({ hayQueAvisar: true, copias: [copia()], diasSinBajar: 9 }).texto,
  ]

  for (const texto of textos) {
    assert.doesNotMatch(texto, /carpeta|disco|USB|C:\/|servidor/i)
  }
})

test('cada copia se lee con lo que importa: cuándo, cuánto pesó y cuánto tardó', () => {
  assert.equal(textoDeLaCopia(copia(), HOY), 'hoy · 4,2 MB · 1,4 s')
  assert.equal(textoDeLaCopia(copia({ estado: 'FALLO', error: 'El disco está lleno' }), HOY),
    'Falló · El disco está lleno')
})
