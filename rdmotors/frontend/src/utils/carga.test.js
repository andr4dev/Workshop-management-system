import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  conteos, cuadre, filtrar, gananciaDe, pagina, porcentajeEscrito, precioEscrito, problemaDelArchivo,
  resumenParaConfirmar, textoDeGanancia,
} from './carga.js'

// La bujía de la MAG477: $45.855,7575 con IVA, sugerida a $66.500.
const COSTO_BUJIA = 45855.7575

test('decisión 2: LOS DOS PORCENTAJES de la bujía, con su nombre cada uno: 45% sobre lo pagado, 31% del precio', () => {
  const g = gananciaDe(COSTO_BUJIA, 66500)
  assert.equal(g.sobreLoPagado, 45)
  assert.equal(g.delPrecio, 31)
  assert.equal(Math.round(g.pesos), 20644)
  assert.equal(textoDeGanancia(COSTO_BUJIA, 66500),
    'Le ganas 45% a lo que pagaste · 31% del precio es ganancia')
})

test('decisión 2: el porcentaje sobre el precio NUNCA se presenta como "le ganas"', () => {
  // Si alguien cambia el texto para mostrar solo el margen, la bujía diría "le ganas 31%" y el dueño puso 45%.
  assert.doesNotMatch(textoDeGanancia(COSTO_BUJIA, 66500), /le ganas 31%/i)
})

test('§6: un precio bajo el costo dice cuánto se pierde por unidad, y no lo impide', () => {
  assert.match(textoDeGanancia(COSTO_BUJIA, 40000), /^Vendes a pérdida: \$\s5\.856 por unidad$/)
  assert.equal(gananciaDe(COSTO_BUJIA, 40000).aPerdida, true)
})

test('sin costo o sin precio no hay ganancia que mostrar, ni un 0% inventado', () => {
  assert.equal(gananciaDe(null, 66500), null)
  assert.equal(gananciaDe(COSTO_BUJIA, null), null)
  assert.equal(gananciaDe(0, 66500), null)
  assert.equal(textoDeGanancia(null, 66500), '')
})

const renglones = [
  { posicion: 0, codigo: '524XRE3IJ', descripcion: 'BUJIA IRIDIUM CR7HIX NGK', marca: 'NGK', marcaPropuesta: true, problemas: [] },
  { posicion: 1, codigo: '093AKTCLKI', descripcion: 'KIT EMPAQUES MEDIO AK150', marca: null, problemas: [{ tipo: 'SIN_MARCA' }] },
  { posicion: 2, codigo: '082T3S', descripcion: 'TENSOR CADENILLA CB110 INOKI', marca: 'INOKI', existente: { stock: 4 }, marcaPropuesta: true, problemas: [] },
  { posicion: 3, codigo: '111X', descripcion: 'PIÑON SALIDA', marca: 'KOYO', ajustadoAMano: true, problemas: [] },
  { posicion: 4, codigo: '222Y', descripcion: 'RETEN', marca: 'ARX', quitado: true, problemas: [{ tipo: 'NO_CUADRA' }] },
]

test('RF-013: los filtros, y un quitado solo aparece en "Quitados"', () => {
  assert.deepEqual(filtrar(renglones, 'PROBLEMAS').map((r) => r.posicion), [1])
  // La reposición no cuenta como propuesta: su marca no se usa, ya la tiene el repuesto.
  assert.deepEqual(filtrar(renglones, 'PROPUESTOS').map((r) => r.posicion), [0])
  assert.deepEqual(filtrar(renglones, 'REPOSICIONES').map((r) => r.posicion), [2])
  assert.deepEqual(filtrar(renglones, 'AJUSTADOS').map((r) => r.posicion), [3])
  assert.deepEqual(filtrar(renglones, 'QUITADOS').map((r) => r.posicion), [4])
  assert.deepEqual(filtrar(renglones, 'TODOS').map((r) => r.posicion), [0, 1, 2, 3])
  assert.deepEqual(conteos(renglones), { TODOS: 4, PROBLEMAS: 1, PROPUESTOS: 1, REPOSICIONES: 1, AJUSTADOS: 1, QUITADOS: 1 })
})

test('RF-013: la búsqueda encuentra por código, descripción o marca, sin tildes ni mayúsculas', () => {
  assert.deepEqual(filtrar(renglones, 'TODOS', 'pinon').map((r) => r.posicion), [3])
  assert.deepEqual(filtrar(renglones, 'TODOS', '524x').map((r) => r.posicion), [0])
  assert.deepEqual(filtrar(renglones, 'TODOS', 'inoki').map((r) => r.posicion), [2])
  assert.deepEqual(filtrar(renglones, 'PROBLEMAS', 'bujia'), [])
})

test('de a 50: 592 renglones son 12 páginas, y una página que no existe cae en la última', () => {
  const muchos = Array.from({ length: 592 }, (_, i) => ({ posicion: i }))
  assert.equal(pagina(muchos, 0).paginas, 12)
  assert.equal(pagina(muchos, 11).renglones.length, 42)
  assert.equal(pagina(muchos, 99).actual, 11)
  assert.equal(pagina([], 0).paginas, 1)
})

test('RF-004: el cuadre con la factura dice cuánto falta o cuánto sobra', () => {
  assert.equal(cuadre({ subtotalFactura: 14729523, sumaLeida: 14729523, diferencia: 0 }).estado, 'CUADRA')
  const faltan = cuadre({ subtotalFactura: 14729523, sumaLeida: 14716580, diferencia: 12943 })
  assert.equal(faltan.estado, 'NO_CUADRA')
  assert.match(faltan.texto, /^Faltan \$\s12\.943: lo leído suma \$\s14\.716\.580 y la factura dice \$\s14\.729\.523$/)
  assert.match(cuadre({ subtotalFactura: 100, sumaLeida: 150, diferencia: -50 }).texto, /^Sobran/)
  assert.equal(cuadre({ subtotalFactura: null, sumaLeida: 10 }).estado, 'FALTA_SUBTOTAL')
})

test('el resumen antes del último clic: repuestos, unidades y el total que se pagó', () => {
  const carga = {
    totales: { incluidos: 592, unidades: 2269, totalConIva: 17528132, reposiciones: 3, propuestasSinRevisar: 416, quitados: 0 },
    renglones: [{ quitado: false, avisos: [{ tipo: 'PRECIO_BAJO_COSTO' }] }],
  }
  const [primera, segunda, ...resto] = resumenParaConfirmar(carga)
  assert.match(primera, /^592 repuestos, 2\.269 unidades, compra por \$\s17\.528\.132$/)
  assert.equal(segunda, '589 nuevos y 3 que ya existían y suman stock')
  assert.deepEqual(resto, [
    '416 renglones tienen la marca o la categoría que propuso el sistema, sin revisar',
    '1 queda a pérdida',
  ])
})

test('el archivo se revisa antes de subirlo: tipo, tamaño, y el .xls viejo con su salida', () => {
  assert.equal(problemaDelArchivo({ name: 'MAG477.pdf', size: 714473 }), null)
  assert.equal(problemaDelArchivo({ name: 'carga.XLSX', size: 10 }), null)
  assert.match(problemaDelArchivo({ name: 'viejo.xls', size: 10 }), /guárdalo como \.xlsx/)
  assert.match(problemaDelArchivo({ name: 'foto.jpg', size: 10 }), /PDF, un Excel/)
  assert.match(problemaDelArchivo({ name: 'grande.pdf', size: 6 * 1024 * 1024 }), /5 MB/)
  assert.match(problemaDelArchivo(null), /Elige/)
})

test('lo que se escribe: precios con puntos y signo, porcentajes con coma', () => {
  assert.equal(precioEscrito('$68.500'), 68500)
  assert.equal(precioEscrito(''), null)
  assert.equal(porcentajeEscrito('19'), 19)
  assert.equal(porcentajeEscrito('19,5'), 19.5)
  assert.equal(porcentajeEscrito('45%'), 45)
  assert.equal(porcentajeEscrito('mucho'), null)
})
