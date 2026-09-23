import { describe, test } from 'node:test'
import assert from 'node:assert/strict'
import {
  categoriaVigente, chipsDeCategorias, consultaDelCatalogo, juntarPaginas, moverResaltado, nombreDeCategoria,
  sePuedeAgregar, SIN_CATEGORIA, TAMANO_CATALOGO, TODAS,
} from './catalogo.js'

describe('chipsDeCategorias (RF-002)', () => {
  test('con "aceite": Todas con la suma, las categorías en su orden y Sin categoría al final', () => {
    const chips = chipsDeCategorias([
      { categoriaId: 'filtros', nombre: 'FILTROS', repuestos: 2 },
      { categoriaId: 'lub', nombre: 'LUBRICANTES Y QUIMICOS', repuestos: 5 },
      { categoriaId: null, nombre: null, repuestos: 1 },
    ])
    assert.deepEqual(chips.map((c) => `${c.nombre} ${c.repuestos}`),
      ['Todas 8', 'Filtros 2', 'Lubricantes y quimicos 5', 'Sin categoría 1'])
    assert.deepEqual(chips.map((c) => c.clave), [TODAS, 'filtros', 'lub', SIN_CATEGORIA])
  })

  test('una categoría en cero no sale, y sin conteos solo queda Todas en 0', () => {
    assert.deepEqual(chipsDeCategorias([{ categoriaId: 'frenos', nombre: 'FRENOS', repuestos: 0 }]).map((c) => c.clave), [TODAS])
    assert.deepEqual(chipsDeCategorias(undefined), [{ clave: TODAS, nombre: 'Todas', repuestos: 0 }])
  })

  test('nombreDeCategoria', () => {
    assert.equal(nombreDeCategoria('TRANSMISION Y ARRASTRE'), 'Transmision y arrastre')
    assert.equal(nombreDeCategoria(null), '')
  })
})

describe('categoriaVigente', () => {
  const chips = chipsDeCategorias([{ categoriaId: 'filtros', nombre: 'FILTROS', repuestos: 2 }])

  test('la elegida sigue si todavía tiene algo de lo buscado', () => {
    assert.equal(categoriaVigente(chips, 'filtros'), 'filtros')
  })

  test('si la elegida se quedó sin nada, vuelve a Todas; si los números no han llegado, se respeta', () => {
    assert.equal(categoriaVigente(chips, 'frenos'), TODAS)
    assert.equal(categoriaVigente(null, 'frenos'), 'frenos')
  })
})

describe('consultaDelCatalogo', () => {
  test('una categoría, sin categoría o todas, siempre con stock primero y de a 50', () => {
    assert.deepEqual(consultaDelCatalogo({ texto: 'aceite', categoria: 'filtros', pagina: 2 }),
      { texto: 'aceite', categoriaId: 'filtros', sinCategoria: false, conStockPrimero: true, pagina: 2, tamano: TAMANO_CATALOGO })
    assert.equal(consultaDelCatalogo({ categoria: SIN_CATEGORIA }).sinCategoria, true)
    assert.equal(consultaDelCatalogo({ categoria: SIN_CATEGORIA }).categoriaId, '')
    assert.equal(consultaDelCatalogo({}).categoriaId, '')
    assert.equal(TAMANO_CATALOGO, 50)
  })
})

describe('juntarPaginas (RF-007)', () => {
  const r = (id) => ({ id })

  test('Ver más agrega debajo sin repetir, y sabe cuándo ya no hay más', () => {
    const primera = juntarPaginas(null, { elementos: [r('a'), r('b')], total: 3, numero: 0 })
    assert.deepEqual(primera.elementos.map((x) => x.id), ['a', 'b'])
    assert.equal(primera.hayMas, true)
    assert.equal(primera.siguiente, 1)

    // Entre los dos pedidos "b" se movió a la página siguiente: no sale dos veces.
    const segunda = juntarPaginas(primera, { elementos: [r('b'), r('c')], total: 3, numero: 1 })
    assert.deepEqual(segunda.elementos.map((x) => x.id), ['a', 'b', 'c'])
    assert.equal(segunda.hayMas, false)
  })

  test('una página vacía no promete más', () => {
    assert.equal(juntarPaginas({ elementos: [r('a')] }, { elementos: [], total: 5, numero: 3 }).hayMas, false)
  })
})

describe('moverResaltado', () => {
  test('↓ desde nada va al primero, y no da la vuelta en los bordes', () => {
    assert.equal(moverResaltado(-1, 3, 'ArrowDown'), 0)
    assert.equal(moverResaltado(2, 3, 'ArrowDown'), 2)
    assert.equal(moverResaltado(0, 3, 'ArrowUp'), 0)
    assert.equal(moverResaltado(2, 3, 'ArrowUp'), 1)
    assert.equal(moverResaltado(1, 3, 'Enter'), 1)
    assert.equal(moverResaltado(0, 0, 'ArrowDown'), -1)
  })
})

describe('sePuedeAgregar (RF-005 y RF-008)', () => {
  test('sin stock, sin precio o con la venta bloqueada, no', () => {
    const filtro = { stock: 3, precio: 24000 }
    assert.equal(sePuedeAgregar(filtro, false), true)
    assert.equal(sePuedeAgregar({ ...filtro, stock: 0 }, false), false)
    assert.equal(sePuedeAgregar({ ...filtro, precio: 0 }, false), false)
    assert.equal(sePuedeAgregar(filtro, true), false)
  })
})
