import { describe, test } from 'node:test'
import assert from 'node:assert/strict'
import { numeroDeVenta, resumenDelTurno, textoDelPago, textoDeRepuestos } from './ventasDelTurno.js'

const venta = (total, pagos, extra = {}) => ({ estado: 'COBRADA', total, descuento: 0, pagos, ...extra })
const efectivo = (monto, recibido = null) => ({ forma: 'EFECTIVO', monto, recibido })
const transferencia = (monto) => ({ forma: 'TRANSFERENCIA', monto, recibido: null })

describe('resumenDelTurno (H10)', () => {
  test('en efectivo suma lo que pagó, no con cuánto: de $50.000 por $38.000 entran $38.000', () => {
    const r = resumenDelTurno([venta(38_000, [efectivo(38_000, 50_000)])])
    assert.equal(r.efectivo, 38_000)
    assert.equal(r.total, 38_000)
  })

  test('una venta mixta suma su parte a cada lado, y las partes dan el total', () => {
    const r = resumenDelTurno([
      venta(38_000, [efectivo(20_000, 50_000), transferencia(18_000)]),
      venta(12_000, [transferencia(12_000)]),
      venta(9_000, [efectivo(9_000)]),
    ])
    assert.deepEqual(
      { ventas: r.ventas, total: r.total, efectivo: r.efectivo, transferencia: r.transferencia, descuadre: r.descuadre },
      { ventas: 3, total: 59_000, efectivo: 29_000, transferencia: 30_000, descuadre: 0 },
    )
  })

  test('las anuladas no suman, pero se cuentan aparte', () => {
    const r = resumenDelTurno([
      venta(20_000, [efectivo(20_000)]),
      venta(50_000, [efectivo(50_000)], { estado: 'ANULADA' }),
    ])
    assert.equal(r.ventas, 1)
    assert.equal(r.anuladas, 1)
    assert.equal(r.total, 20_000)
    assert.equal(r.efectivo, 20_000)
  })

  test('cuenta las ventas con descuento, y una de $0 no descuadra', () => {
    const r = resumenDelTurno([
      venta(0, [], { descuento: 12_000 }),
      venta(32_000, [efectivo(32_000)], { descuento: 1_000 }),
    ])
    assert.equal(r.conDescuento, 2)
    assert.equal(r.descuadre, 0)
  })

  test('sin ventas, todo en cero', () => {
    assert.deepEqual(resumenDelTurno([]), { ventas: 0, anuladas: 0, total: 0, efectivo: 0, transferencia: 0, fiado: 0, conDescuento: 0, descuadre: 0 })
  })

  test('si las partes no dan el total, lo dice', () => {
    assert.equal(resumenDelTurno([venta(10_000, [efectivo(8_000)])]).descuadre, 2_000)
  })

  test('lo fiado se cuenta aparte y cuadra con el total (spec 0008)', () => {
    const r = resumenDelTurno([venta(80_000, [efectivo(30_000)], { fiado: 50_000 })])
    assert.equal(r.efectivo, 30_000)
    assert.equal(r.fiado, 50_000)
    assert.equal(r.descuadre, 0)
  })
})

describe('textoDelPago', () => {
  test('una forma, las dos, o ninguna', () => {
    assert.equal(textoDelPago([efectivo(1)]), 'Efectivo')
    assert.equal(textoDelPago([transferencia(1)]), 'Transferencia')
    assert.equal(textoDelPago([], 50_000), 'Fiado')
    assert.equal(textoDelPago([efectivo(1)], 50_000), 'Fiado en parte')
    assert.equal(textoDelPago([efectivo(1), transferencia(1)]), 'Mixto')
    assert.equal(textoDelPago([]), 'Sin pago')
  })
})

describe('textoDeRepuestos', () => {
  test('el primero con su marca, y cuántos más', () => {
    const r = (nombre, marca) => ({ nombre, marca })
    assert.equal(textoDeRepuestos([r('FILTRO DE ACEITE', 'INOKI')]), 'FILTRO DE ACEITE INOKI')
    assert.equal(textoDeRepuestos([r('FILTRO DE ACEITE', 'INOKI'), r('BUJÍA', 'NGK'), r('CADENA', null)]), 'FILTRO DE ACEITE INOKI y 2 más')
  })
})

describe('numeroDeVenta', () => {
  test('acepta el número como lo escriba el cajero', () => {
    for (const texto of ['12', ' 12 ', 'N.º 12', 'n.º12', 'No. 12', '#12', 'N 12']) {
      assert.equal(numeroDeVenta(texto), 12, texto)
    }
  })

  test('sin un entero positivo no hay qué buscar', () => {
    for (const texto of ['', '   ', 'abc', '0', '-3', '12.5', '1e3', null]) {
      assert.equal(numeroDeVenta(texto), null, String(texto))
    }
  })
})
