import { describe, test } from 'node:test'
import assert from 'node:assert/strict'
import { esTurnoAjeno, modulosPara, puedeAbrir, textoDeTurnoAjeno, veCostos } from './permisos.js'

const ruben = { id: 'u-ruben', nombre: 'Rubén', rol: 'ADMINISTRADOR', veCostos: true }
const carolina = { id: 'u-carolina', nombre: 'Carolina', rol: 'CAJERO', veCostos: false }
const andres = { id: 'u-andres', nombre: 'Andrés', rol: 'CAJERO', veCostos: false }
const turnoDeCarolina = { id: 't-1', abiertoPor: { id: 'u-carolina', nombre: 'Carolina' } }

describe('qué ve cada rol (spec 0004, §5)', () => {
  test('el cajero ve Vender, Cartera e Inventario; el administrador, además Compras y Reportes', () => {
    assert.deepEqual(modulosPara(carolina).map((m) => m.nombre), ['Vender', 'Cartera', 'Inventario'])
    assert.deepEqual(modulosPara(ruben).map((m) => m.nombre), ['Vender', 'Cartera', 'Compras', 'Inventario', 'Reportes'])
  })

  test('el cajero no abre compras, reportes ni los datos de la tienda, aunque escriba la dirección', () => {
    for (const ruta of ['/compras', '/compras/historial/abc', '/reportes/resultados', '/reportes/gastos',
      '/tienda', '/usuarios']) {
      assert.equal(puedeAbrir(carolina, ruta), false, ruta)
      assert.equal(puedeAbrir(ruben, ruta), true, ruta)
    }
    for (const ruta of ['/vender', '/vender/caja', '/vender/caja/turnos/t-1', '/inventario', '/inventario/r-1',
      '/cartera', '/cartera/c-juan']) {
      assert.equal(puedeAbrir(carolina, ruta), true, ruta)
    }
    // Una dirección que solo empieza igual no es la misma.
    assert.equal(puedeAbrir(carolina, '/tiendas-aliadas'), true)
  })

  test('los costos los ve quien el servidor diga, no quien la pantalla crea', () => {
    assert.equal(veCostos(ruben), true)
    assert.equal(veCostos(carolina), false)
    assert.equal(veCostos({ ...carolina, rol: 'ADMINISTRADOR' }), false)
    assert.equal(veCostos(null), false)
  })
})

describe('el turno con dueño (decisión 2)', () => {
  test('es ajeno para otro cajero; no para quien lo abrió ni para el administrador', () => {
    assert.equal(esTurnoAjeno(andres, turnoDeCarolina), true)
    assert.equal(esTurnoAjeno(carolina, turnoDeCarolina), false)
    assert.equal(esTurnoAjeno(ruben, turnoDeCarolina), false)
    assert.equal(esTurnoAjeno(andres, null), false)
  })

  test('el aviso dice de quién es, sin suponer si es él o ella', () => {
    assert.equal(textoDeTurnoAjeno(turnoDeCarolina), 'El turno abierto es de Carolina: lo cierra Carolina o un administrador')
    assert.match(textoDeTurnoAjeno({ id: 't-2' }), /es de otra persona/)
  })
})
