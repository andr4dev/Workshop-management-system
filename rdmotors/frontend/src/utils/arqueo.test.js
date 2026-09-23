import { describe, test } from 'node:test'
import assert from 'node:assert/strict'
import {
  contadoDesdeTexto, diferenciaEnPalabras, faltaExplicacion, filasDelDesglose, problemaDelContado,
  problemasDelArqueo, sumaDelDesglose, totalPorDenominaciones,
} from './arqueo.js'
import { formatoCOP } from './formato.js'

/** El cierre del ejemplo del spec 0006 (§2), como lo devuelve el servidor. */
const TURNO_DEL_EJEMPLO = {
  id: 'hoy',
  estado: 'CERRADO',
  fondo: 100_000,
  cierre: {
    ventasEfectivo: 223_400, ventasTransferencia: 111_000, descuentos: 0, devolucionesEfectivo: 70_000,
    gastosCajon: 15_000, retiros: 100_000, comprasCajon: 50_000, esperado: 88_400, contado: 87_000, diferencia: -1_400,
  },
  observaciones: null,
}

/** El mismo turno, con un abono de cliente en efectivo (spec 0008). */
const CON_ABONOS = {
  ...TURNO_DEL_EJEMPLO,
  cierre: { ...TURNO_DEL_EJEMPLO.cierre, abonosEfectivo: 30_000, abonosTransferencia: 5_000, ventasFiado: 50_000,
    esperado: 118_400, contado: 118_400, diferencia: 0 },
}

describe('los abonos de clientes en el cajón (spec 0008)', () => {
  test('el abono en efectivo es una fila más del desglose y suma a lo que debería haber', () => {
    const filas = filasDelDesglose(CON_ABONOS)
    assert.ok(filas.some((f) => f.clave === 'abonosEfectivo' && f.signo === 1 && f.monto === 30_000))
    assert.equal(sumaDelDesglose(filas), 118_400)
    assert.deepEqual(problemasDelArqueo(CON_ABONOS), [])
  })

  test('sin abonos, la fila no sale; un cierre de antes de los abonos tampoco se rompe', () => {
    assert.ok(!filasDelDesglose(TURNO_DEL_EJEMPLO).some((f) => f.clave === 'abonosEfectivo'))
    assert.equal(sumaDelDesglose(filasDelDesglose(TURNO_DEL_EJEMPLO)), 88_400)
  })
})

describe('lo contado', () => {
  test('vacío no es $0: se pide escribirlo', () => {
    assert.equal(contadoDesdeTexto(''), null)
    assert.equal(problemaDelContado(''), 'Escribe cuánto contaste, aunque sea $0')
  })

  test('$0 vale, y se lee con o sin puntos', () => {
    assert.equal(problemaDelContado('0'), null)
    assert.equal(contadoDesdeTexto('$ 87.000'), 87_000)
  })

  test('un monto absurdo de largo no pasa', () => {
    assert.equal(problemaDelContado('9'.repeat(20)), 'Ese monto no es válido')
  })
})

describe('filasDelDesglose', () => {
  test('el ejemplo del spec: fondo y cada parte con su signo, y leídas de arriba abajo suman $88.400', () => {
    const filas = filasDelDesglose(TURNO_DEL_EJEMPLO)

    assert.deepEqual(filas.map((f) => [f.etiqueta, f.signo * f.monto]), [
      ['Fondo del turno', 100_000],
      ['Ventas en efectivo', 223_400],
      ['Devuelto por ventas anuladas', -70_000],
      ['Gastos pagados del cajón', -15_000],
      ['Retiros', -100_000],
      ['Compras pagadas del cajón', -50_000],
    ])
    assert.equal(sumaDelDesglose(filas), 88_400)
  })

  test('las partes en $0 no salen, pero el fondo sí, y siguen sumando', () => {
    const turno = {
      fondo: 0,
      cierre: { ...TURNO_DEL_EJEMPLO.cierre, ventasEfectivo: 5_000, devolucionesEfectivo: 0, gastosCajon: 0, retiros: 0,
        comprasCajon: 0, esperado: 5_000 },
    }
    assert.deepEqual(filasDelDesglose(turno).map((f) => f.clave), ['fondo', 'ventasEfectivo'])
    assert.equal(sumaDelDesglose(filasDelDesglose(turno)), 5_000)
  })

  test('un turno abierto se desglosa EN VIVO con su arqueo, y suma igual', () => {
    const abierto = { estado: 'ABIERTO', fondo: 100_000, cierre: null, arqueo: { ...TURNO_DEL_EJEMPLO.cierre } }
    delete abierto.arqueo.contado
    delete abierto.arqueo.diferencia

    assert.equal(sumaDelDesglose(filasDelDesglose(abierto)), 88_400)
    assert.deepEqual(problemasDelArqueo(abierto), [])
    assert.match(problemasDelArqueo({ ...abierto, arqueo: { ...abierto.arqueo, esperado: 90_000 } })[0], /El desglose suma/)
  })

  test('sin arqueo ni cierre no hay desglose', () => {
    assert.deepEqual(filasDelDesglose({ fondo: 100_000, cierre: null, arqueo: null }), [])
  })
})

describe('diferenciaEnPalabras', () => {
  test('faltante, sobrante y cuadra', () => {
    assert.deepEqual(diferenciaEnPalabras(-1_400), { tipo: 'FALTANTE', texto: `Faltan ${formatoCOP(1_400)}` })
    assert.deepEqual(diferenciaEnPalabras(500), { tipo: 'SOBRANTE', texto: `Sobran ${formatoCOP(500)}` })
    assert.deepEqual(diferenciaEnPalabras(0), { tipo: 'CUADRA', texto: 'Cuadra al peso' })
  })
})

describe('problemasDelArqueo', () => {
  test('el ejemplo cuadra', () => {
    assert.deepEqual(problemasDelArqueo(TURNO_DEL_EJEMPLO), [])
  })

  test('si el desglose no suma lo que debería haber, o la diferencia no es contado menos esperado, lo dice', () => {
    const roto = { ...TURNO_DEL_EJEMPLO, cierre: { ...TURNO_DEL_EJEMPLO.cierre, esperado: 90_000, diferencia: -1_400 } }
    const problemas = problemasDelArqueo(roto)

    assert.equal(problemas.length, 2)
    assert.match(problemas[0], /El desglose suma/)
    assert.match(problemas[1], /la diferencia no es/)
  })
})

describe('faltaExplicacion', () => {
  test('solo un cierre con diferencia y sin observaciones', () => {
    assert.equal(faltaExplicacion({ estado: 'CERRADO', diferencia: -1_400, observaciones: null }), true)
    assert.equal(faltaExplicacion({ estado: 'CERRADO', diferencia: -1_400, observaciones: 'Cambio mal dado' }), false)
    assert.equal(faltaExplicacion({ estado: 'CERRADO', diferencia: 0, observaciones: null }), false)
    assert.equal(faltaExplicacion({ estado: 'ABIERTO', diferencia: null, observaciones: null }), false)
  })
})

describe('totalPorDenominaciones', () => {
  test('suma billetes y monedas; lo vacío no cuenta', () => {
    const { total, problemas } = totalPorDenominaciones({ 50000: '1', 20000: '1', 10000: '1', 5000: '1', 2000: '1',
      500: '', 100: '0' })
    assert.equal(total, 87_000)
    assert.deepEqual(problemas, [])
  })

  test('una cantidad que no es un número entero no suma y se dice cuál', () => {
    const { total, problemas } = totalPorDenominaciones({ 100000: '1', 1000: '2,5' })
    assert.equal(total, 100_000)
    assert.equal(problemas.length, 1)
    assert.match(problemas[0], /no es válida/)
  })
})
