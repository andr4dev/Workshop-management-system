import { describe, test } from 'node:test'
import assert from 'node:assert/strict'
import {
  avisoDeGastosDelMes, calculoDe, CIFRAS, columnasQueNoCuadran, enlaceAGastos, hayMovimientos, pagosCuadran,
  participacion, porcentajeDeDescuentos, rankingDeRepuestos, sumanLasVentasNetas, textoDeDiferencia, textoDeMargen,
  textoDeVariacion, textoSinCosto, variacion, vendidosConPerdida,
} from './resultados.js'

const pesos = (n) => `$${n.toLocaleString('es-CO')}`

/** La semana del ejemplo del spec (§2), como la devuelve el servidor. */
function semanaDelEjemplo() {
  const fila = (dia, ventasNetas, costoVendido, costosAdicionales, gastos) => ({
    desde: dia, hasta: dia, ventas: 0, ventasNetas, costoVendido, costosAdicionales,
    utilidadBruta: ventasNetas - costoVendido - costosAdicionales, gastos,
    utilidadOperativa: ventasNetas - costoVendido - costosAdicionales - gastos, renglonesSinCosto: 0,
  })
  const filas = [
    { ...fila('2026-09-14', 700_000, 450_000, 0, 60_000), ventas: 28 },
    { ...fila('2026-09-15', 520_000, 330_000, 20_000, 40_000), ventas: 20 },
  ]
  return {
    desde: '2026-09-14', hasta: '2026-09-20', dias: 7, agrupacion: 'DIA', modoGastosDelMes: 'REPARTIDOS',
    cifras: {
      ventas: 48, unidades: 132, renglones: 1_250_000, descuentos: 30_000, ventasConDescuento: 6,
      ventasNetas: 1_220_000, ticketPromedio: 25_417, efectivo: 820_000, transferencia: 400_000,
      costoVendido: 780_000, costosAdicionales: 20_000, utilidadBruta: 420_000, margenBruto: 34.4,
      gastos: 100_000, utilidadOperativa: 320_000, margenOperativo: 26.2,
    },
    costosPorCategoria: [{ categoriaId: 'f', categoria: 'Fletes de mercancía', monto: 20_000 }],
    gastosPorCategoria: [
      { categoriaId: 'a', categoria: 'Alimentación', monto: 60_000 },
      { categoriaId: 'b', categoria: 'Aseo y cafetería', monto: 25_000 },
      { categoriaId: 'c', categoria: 'Papelería', monto: 15_000 },
    ],
    gastosDelMes: { incluidos: 0, fuera: 0 },
    sinCosto: { renglones: 0, unidades: 0, vendido: 0, repuestos: [] },
    filas,
    filaGastosDelMes: null,
  }
}

describe('Ver cálculo (RF-012)', () => {
  test('la utilidad operativa reproduce el desglose del §2, con cada categoría, y cuadra', () => {
    const calculo = calculoDe(CIFRAS.UTILIDAD_OPERATIVA, semanaDelEjemplo())

    assert.equal(calculo.cuadra, true)
    assert.deepEqual(calculo.filas.map((f) => [f.signo ?? '=', f.etiqueta, f.monto]), [
      ['+', 'Renglones vendidos', 1_250_000],
      ['−', 'Descuentos dados', 30_000],
      ['=', 'Ventas netas', 1_220_000],
      ['−', 'Costo de los repuestos vendidos', 780_000],
      ['−', 'Costos adicionales', 20_000],
      ['=', 'Utilidad bruta', 420_000],
      ['−', 'Gastos del local', 100_000],
      ['=', 'Utilidad operativa', 320_000],
    ])
    assert.deepEqual(calculo.filas[6].hijos.map((h) => [h.etiqueta, h.monto]),
      [['Alimentación', 60_000], ['Aseo y cafetería', 25_000], ['Papelería', 15_000]])
  })

  test('las cuatro cifras cuadran con sus partes', () => {
    const r = semanaDelEjemplo()
    for (const cifra of Object.values(CIFRAS)) {
      assert.equal(calculoDe(cifra, r).cuadra, true, cifra)
    }
    assert.equal(calculoDe(CIFRAS.GASTOS, r).filas.at(-1).monto, 100_000)
  })

  test('si un subtotal no es lo que suman sus partes, lo dice, con lo que dan las partes; no lo corrige', () => {
    const r = semanaDelEjemplo()
    r.cifras.utilidadBruta = 430_000

    const calculo = calculoDe(CIFRAS.UTILIDAD_BRUTA, r)
    const bruta = calculo.filas.at(-1)

    assert.equal(calculo.cuadra, false)
    assert.equal(bruta.cuadra, false)
    assert.equal(bruta.monto, 430_000)
    assert.equal(bruta.calculado, 420_000)
  })

  test('si las categorías no suman su parte, también lo dice', () => {
    const r = semanaDelEjemplo()
    r.gastosPorCategoria.pop()

    const calculo = calculoDe(CIFRAS.GASTOS, r)

    assert.equal(calculo.cuadra, false)
    assert.equal(calculo.filas[0].sumaHijos, 85_000)
  })
})

describe('las partes suman', () => {
  test('efectivo + transferencia = ventas netas', () => {
    const r = semanaDelEjemplo()
    assert.equal(pagosCuadran(r.cifras), true)
    assert.equal(pagosCuadran({ ...r.cifras, efectivo: 1 }), false)
  })

  test('las filas del día por día suman las cifras; la de gastos del mes cuenta', () => {
    const r = semanaDelEjemplo()
    assert.deepEqual(columnasQueNoCuadran(r), [])

    r.filaGastosDelMes = { ...r.filas[0], ventas: 0, ventasNetas: 0, costoVendido: 0, costosAdicionales: 0,
      utilidadBruta: 0, gastos: 800_000, utilidadOperativa: -800_000 }
    assert.deepEqual(columnasQueNoCuadran(r), ['gastos', 'utilidadOperativa'])
  })
})

describe('los textos', () => {
  test('margen con un decimal y coma; sin ventas, nada (la pantalla pone "—")', () => {
    assert.equal(textoDeMargen(34.4), '34,4 % de las ventas')
    assert.equal(textoDeMargen(-5), '-5,0 % de las ventas')
    assert.equal(textoDeMargen(null), null)
  })

  test('renglones sin costo, en singular y plural', () => {
    assert.equal(textoSinCosto(0), null)
    assert.equal(textoSinCosto(1), '1 renglón sin costo: la utilidad está sobrestimada')
    assert.equal(textoSinCosto(3), '3 renglones sin costo: la utilidad está sobrestimada')
  })

  test('RF-015: el aviso dice cómo se leyeron los gastos del mes', () => {
    const r = semanaDelEjemplo()
    assert.equal(avisoDeGastosDelMes(r, pesos), null)

    r.gastosDelMes = { incluidos: 186_669, fuera: 0 }
    assert.match(avisoDeGastosDelMes(r, pesos), /repartidos día por día: este período carga \$186\.669/)

    r.modoGastosDelMes = 'SOLO_EN_EL_MES'
    r.gastosDelMes = { incluidos: 0, fuera: 800_000 }
    assert.equal(avisoDeGastosDelMes(r, pesos), 'No incluye $800.000 de gastos del mes: se ven en el reporte del mes.')

    r.gastosDelMes = { incluidos: 800_000, fuera: 0 }
    assert.match(avisoDeGastosDelMes(r, pesos), /^Incluye \$800\.000 de gastos del mes enteros/)
  })

  test('§6: con gastos y sin ventas hay movimientos; sin nada, no', () => {
    const r = semanaDelEjemplo()
    const vacio = { ...r, cifras: { ...r.cifras, ventas: 0, gastos: 0, costosAdicionales: 0 } }
    assert.equal(hayMovimientos(vacio), false)
    assert.equal(hayMovimientos({ ...vacio, cifras: { ...vacio.cifras, gastos: 15_000 } }), true)
    assert.equal(hayMovimientos({ ...vacio, gastosDelMes: { incluidos: 0, fuera: 800_000 } }), true)
  })

  test('los descuentos como porcentaje de los renglones', () => {
    assert.equal(porcentajeDeDescuentos(semanaDelEjemplo().cifras), 2.4)
    assert.equal(porcentajeDeDescuentos({ renglones: 0, descuentos: 0 }), null)
  })
})

describe('P2: repuestos, categorías, anterior y control', () => {
  const repuesto = (nombre, unidades, ventasNetas, utilidad, extra = {}) =>
    ({ id: nombre, nombre, unidades, ventasNetas, utilidad, conPerdida: utilidad != null && utilidad < 0, ...extra })
  const repuestos = [
    repuesto('Filtro', 36, 420_000, 150_000),
    repuesto('Pastillas', 56, 700_000, 280_000),
    repuesto('Caja aceite', 40, 100_000, 10_000),
    repuesto('Bujía', 1, 25, -975),
    repuesto('Tornillo', 80, 8_000, null),
  ]

  test('RF-019: por utilidad (los sin costo al final), por unidades o por ventas netas; los primeros N', () => {
    assert.deepEqual(rankingDeRepuestos(repuestos, 'UTILIDAD').map((r) => r.nombre),
      ['Pastillas', 'Filtro', 'Caja aceite', 'Bujía', 'Tornillo'])
    assert.deepEqual(rankingDeRepuestos(repuestos, 'UNIDADES').map((r) => r.nombre),
      ['Tornillo', 'Pastillas', 'Caja aceite', 'Filtro', 'Bujía'])
    assert.deepEqual(rankingDeRepuestos(repuestos, 'VENTAS', 2).map((r) => r.nombre), ['Pastillas', 'Filtro'])
    assert.equal(repuestos[0].nombre, 'Filtro', 'no cambia la lista que recibe')
  })

  test('RF-020: con pérdida, de la mayor a la menor', () => {
    const lista = [...repuestos, repuesto('Espejo', 1, 5_000, -12_000)]
    assert.deepEqual(vendidosConPerdida(lista).map((r) => r.nombre), ['Espejo', 'Bujía'])
  })

  test('RF-021: los repuestos o las categorías suman las ventas netas; la participación con un decimal', () => {
    const r = { cifras: { ventasNetas: 1_228_025 } }
    assert.equal(sumanLasVentasNetas(repuestos, r), true)
    assert.equal(sumanLasVentasNetas(repuestos.slice(1), r), false)
    assert.equal(participacion(420_000, 1_220_000), 34.4)
    assert.equal(participacion(1, 0), null)
  })

  test('RF-022: cuánto subió o bajó, con su porcentaje; desde $0 no hay porcentaje; desde una pérdida, sobre su valor', () => {
    assert.deepEqual(variacion(320_000, 280_000), { diferencia: 40_000, porcentaje: 14.3, direccion: 'SUBE' })
    assert.equal(textoDeVariacion(variacion(320_000, 280_000), pesos), '▲ $40.000 (+14,3 %)')
    assert.equal(textoDeVariacion(variacion(250_000, 280_000), pesos), '▼ $30.000 (−10,7 %)')
    assert.equal(textoDeVariacion(variacion(50_000, 0), pesos), '▲ $50.000')
    assert.equal(textoDeVariacion(variacion(0, 0), pesos), 'Igual')
    assert.equal(variacion(50_000, -100_000).porcentaje, 150)
  })

  test('RF-023 y RF-024: la diferencia de caja en palabras, y el enlace a los gastos de esas fechas y esa categoría', () => {
    assert.equal(textoDeDiferencia(-1_400, pesos), 'faltaron $1.400')
    assert.equal(textoDeDiferencia(500, pesos), 'sobraron $500')
    assert.equal(textoDeDiferencia(0, pesos), 'cuadró')
    assert.equal(enlaceAGastos({ desde: '2026-09-14', hasta: '2026-09-20' }, 'abc'),
      '/reportes/gastos?desde=2026-09-14&hasta=2026-09-20&categoriaId=abc')
  })
})
