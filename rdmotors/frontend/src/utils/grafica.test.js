import { describe, test } from 'node:test'
import assert from 'node:assert/strict'
import { cadaCuantasEtiquetas, geometria, marcasDelEje, montoCorto } from './grafica.js'

const fila = (ventasNetas, utilidadOperativa) => ({ ventasNetas, utilidadOperativa })
const MEDIDAS = { ancho: 700, alto: 200 }

describe('el eje', () => {
  test('cifras redondas que cubren el máximo e incluyen el cero', () => {
    const { marcas, desde, hasta } = marcasDelEje(0, 1_220_000)
    assert.equal(desde, 0)
    assert.ok(hasta >= 1_220_000)
    assert.ok(marcas.includes(0))
    assert.ok(marcas.every((m) => m % 100_000 === 0), marcas.join())
  })

  test('con negativos, el eje baja del cero', () => {
    const { marcas, desde } = marcasDelEje(-550_000, 250_000)
    assert.ok(desde <= -550_000)
    assert.ok(marcas.includes(0))
  })

  test('todo en cero no divide por cero', () => {
    const { desde, hasta } = marcasDelEje(0, 0)
    assert.ok(hasta > desde)
  })
})

describe('las barras', () => {
  test('dos barras por fila, dentro de su hueco; la más alta llega cerca del tope', () => {
    const g = geometria([fila(700_000, 190_000), fila(520_000, 130_000)], MEDIDAS)

    assert.equal(g.barras.length, 2)
    assert.equal(g.cero, MEDIDAS.alto)
    for (const b of g.barras) {
      assert.ok(b.ventas.x >= b.x && b.utilidad.x + b.anchoBarra <= b.x + b.ancho)
      assert.ok(b.ventas.y + b.ventas.alto <= g.cero + 1e-9)
    }
    assert.ok(g.barras[0].ventas.alto > g.barras[1].ventas.alto)
    assert.ok(g.barras[0].ventas.alto / MEDIDAS.alto > 0.85)
  })

  test('una utilidad negativa baja del cero: empieza en el eje y crece hacia abajo', () => {
    const g = geometria([fila(250_000, -550_000), fila(300_000, 80_000)], MEDIDAS)
    const negativa = g.barras[0].utilidad

    assert.equal(negativa.negativa, true)
    assert.equal(negativa.y, g.cero)
    assert.ok(negativa.alto > 0)
    assert.ok(g.cero < MEDIDAS.alto)
    assert.ok(Math.abs(g.y(-550_000) - (negativa.y + negativa.alto)) < 1e-9)
  })

  test('sin movimientos: barras de alto cero, nada es NaN', () => {
    const g = geometria([fila(0, 0), fila(0, 0)], MEDIDAS)
    for (const b of g.barras) {
      for (const v of [b.ventas.y, b.ventas.alto, b.utilidad.y, b.utilidad.alto]) assert.ok(Number.isFinite(v))
      assert.equal(b.ventas.alto, 0)
    }
  })
})

describe('las etiquetas', () => {
  test('una semana, todas; un mes, cada 3; 53 semanas, cada 5', () => {
    assert.equal(cadaCuantasEtiquetas(7), 1)
    assert.equal(cadaCuantasEtiquetas(31), 3)
    assert.equal(cadaCuantasEtiquetas(53), 5)
  })

  test('montos cortos para el eje', () => {
    assert.equal(montoCorto(1_220_000), '$1,2 M')
    assert.equal(montoCorto(850_000), '$850 mil')
    assert.equal(montoCorto(-550_000), '−$550 mil')
    assert.equal(montoCorto(900), '$900')
    assert.equal(montoCorto(0), '$0')
  })
})
