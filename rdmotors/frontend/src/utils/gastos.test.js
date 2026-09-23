import { describe, test } from 'node:test'
import assert from 'node:assert/strict'
import {
  categoriasParaElegir, comandoDelGasto, comandoDelRetiro, conCategoria, consultaDeGastos, consultaDeTotalesDeGastos,
  filtrosDeGastos, gastoNuevo, movimientosDelCajon, problemaDelRango, problemasDelGasto, problemasDelRetiro,
  sePuedeAnular, sinProblemas, TAMANO_GASTOS, textoDelOrigen,
} from './gastos.js'

const HOY = '2026-09-16'
const LLAVE = '0f0f0f0f-0000-4000-8000-000000000001'

describe('la lista de gastos', () => {
  test('los filtros salen de la URL, y una página que no sirve es la primera', () => {
    const f = filtrosDeGastos(new URLSearchParams('desde=2026-09-01&categoriaId=abc&p=-3'))
    assert.deepEqual(f, { desde: '2026-09-01', hasta: '', categoriaId: 'abc', pagina: 0 })
  })

  test('la lista y los totales piden los mismos filtros; solo la lista lleva página', () => {
    const f = { desde: '2026-09-01', hasta: '', categoriaId: 'abc', pagina: 2 }
    assert.deepEqual(consultaDeTotalesDeGastos(f), { desde: '2026-09-01', categoriaId: 'abc' })
    assert.deepEqual(consultaDeGastos(f), { desde: '2026-09-01', categoriaId: 'abc', pagina: 2, tamano: TAMANO_GASTOS })
  })

  test('un rango al revés se dice', () => {
    assert.equal(problemaDelRango({ desde: '2026-09-30', hasta: '2026-09-01' }), 'La fecha inicial es posterior a la final')
    assert.equal(problemaDelRango({ desde: '2026-09-01', hasta: '' }), null)
  })

  test('de dónde salió la plata', () => {
    assert.equal(textoDelOrigen({ delCajon: true, formaPago: 'EFECTIVO' }), 'Del cajón')
    assert.equal(textoDelOrigen({ delCajon: false, formaPago: 'EFECTIVO' }), 'Efectivo por fuera')
    assert.equal(textoDelOrigen({ delCajon: false, formaPago: 'TRANSFERENCIA', cuenta: 'Nequi del dueño' }),
      'Transferencia · Nequi del dueño')
  })

  test('se anula uno por fuera siempre, uno del cajón solo con su turno abierto, y un anulado nunca', () => {
    assert.equal(sePuedeAnular({ delCajon: false, anuladoEn: null }, null), true)
    assert.equal(sePuedeAnular({ delCajon: true, turnoId: 't1', anuladoEn: null }, 't1'), true)
    assert.equal(sePuedeAnular({ delCajon: true, turnoId: 't1', anuladoEn: null }, 't2'), false)
    assert.equal(sePuedeAnular({ delCajon: false, anuladoEn: '2026-09-16T10:00:00Z' }, null), false)
  })

  test('para un gasto nuevo se ofrecen las categorías activas, por nombre', () => {
    const categorias = [
      { id: '1', nombre: 'Papelería', activa: true },
      { id: '2', nombre: 'Arriendo', activa: true },
      { id: '3', nombre: 'Publicidad', activa: false },
    ]
    assert.deepEqual(categoriasParaElegir(categorias).map((c) => c.nombre), ['Arriendo', 'Papelería'])
  })
})

describe('registrar un gasto', () => {
  test('con turno arranca del cajón; sin turno, por fuera', () => {
    assert.equal(gastoNuevo({ hayTurno: true, hoy: HOY }).delCajon, true)
    assert.equal(gastoNuevo({ hayTurno: false, hoy: HOY }).delCajon, false)
  })

  test('del cajón: categoría, monto y descripción; sin turno, se dice que se registre por fuera', () => {
    const vacio = gastoNuevo({ hayTurno: true, hoy: HOY })
    const p = problemasDelGasto(vacio, { hayTurno: true, hoy: HOY })
    assert.equal(p.categoria, 'Elige la categoría')
    assert.equal(p.monto, 'Escribe cuánto se gastó')
    assert.equal(p.descripcion, 'Escribe en qué se gastó')
    assert.equal(p.formaPago, null)

    const completo = { ...vacio, categoriaId: 'c', monto: '15.000', descripcion: 'Flete' }
    assert.equal(sinProblemas(problemasDelGasto(completo, { hayTurno: true, hoy: HOY })), true)
    assert.match(problemasDelGasto(completo, { hayTurno: false, hoy: HOY }).origen, /por fuera del cajón/)
  })

  test('por fuera: forma de pago, cuenta si es transferencia, y la fecha no es futura', () => {
    const base = { ...gastoNuevo({ hayTurno: false, hoy: HOY }), categoriaId: 'c', monto: '800000', descripcion: 'Arriendo' }
    assert.equal(problemasDelGasto(base, { hayTurno: false, hoy: HOY }).formaPago, 'Elige cómo se pagó')
    assert.equal(problemasDelGasto({ ...base, formaPago: 'TRANSFERENCIA' }, { hayTurno: false, hoy: HOY }).cuenta,
      'Elige desde qué cuenta salió')
    assert.equal(problemasDelGasto({ ...base, formaPago: 'EFECTIVO', fecha: '2026-09-17' }, { hayTurno: false, hoy: HOY }).fecha,
      'La fecha no puede ser después de hoy')
    assert.equal(sinProblemas(problemasDelGasto({ ...base, formaPago: 'EFECTIVO' }, { hayTurno: false, hoy: HOY })), true)
  })

  test('el comando del cajón no manda forma, cuenta ni fecha; el de por fuera sí', () => {
    const delCajon = { categoriaId: 'c', monto: '$ 15.000', descripcion: '  Flete  ', delCajon: true,
      formaPago: 'TRANSFERENCIA', cuentaId: 'x', fecha: HOY }
    assert.deepEqual(comandoDelGasto(delCajon, LLAVE), {
      llave: LLAVE, categoriaId: 'c', monto: 15_000, descripcion: 'Flete', delCajon: true,
      formaPago: null, cuentaId: null, fecha: null, delMes: false, confirmado: false,
    })

    const porFuera = { ...delCajon, delCajon: false, formaPago: 'EFECTIVO' }
    assert.deepEqual(comandoDelGasto(porFuera, LLAVE, true), {
      llave: LLAVE, categoriaId: 'c', monto: 15_000, descripcion: 'Flete', delCajon: false,
      formaPago: 'EFECTIVO', cuentaId: null, fecha: HOY, delMes: false, confirmado: true,
    })
  })

  test('SPEC 0007: la categoría sugiere si es del mes, hasta que el cajero toca la casilla', () => {
    const arriendo = { id: 'a', nombre: 'Arriendo', mensual: true }
    const flete = { id: 'f', nombre: 'Transporte y fletes', mensual: false }
    const nuevo = gastoNuevo({ hayTurno: true, hoy: HOY })

    assert.equal(conCategoria(nuevo, arriendo).delMes, true)
    assert.equal(conCategoria(conCategoria(nuevo, arriendo), flete).delMes, false)

    const tocado = { ...conCategoria(nuevo, flete), delMes: true, delMesTocado: true }
    assert.equal(conCategoria(tocado, flete).delMes, true)
    assert.equal(comandoDelGasto({ ...tocado, monto: '800000', descripcion: 'Arriendo' }, LLAVE).delMes, true)
  })
})

describe('registrar un retiro', () => {
  test('monto y motivo obligatorios', () => {
    assert.deepEqual(problemasDelRetiro({ monto: '', motivo: ' ' }),
      { monto: 'Escribe cuánto se sacó', motivo: 'Escribe quién se la llevó o para qué' })
    assert.equal(sinProblemas(problemasDelRetiro({ monto: '100.000', motivo: 'Don Rubén' })), true)
    assert.deepEqual(comandoDelRetiro({ monto: '100.000', motivo: ' Don Rubén ' }, LLAVE),
      { llave: LLAVE, monto: 100_000, motivo: 'Don Rubén', confirmado: false })
  })
})

describe('movimientosDelCajon', () => {
  test('gastos, retiros, compras de caja y devoluciones en efectivo, en el orden en que pasaron, con los anulados', () => {
    const turno = {
      id: 'hoy',
      ventas: [{ id: 'v1', numero: 7, efectivo: 10_000, anuladaEn: '2026-09-16T19:00:00Z', anuladaEnTurnoId: 'hoy' },
        { id: 'v2', numero: 8, efectivo: 0, anuladaEn: '2026-09-16T19:30:00Z', anuladaEnTurnoId: 'hoy' },
        { id: 'v3', numero: 9, efectivo: 5_000, anuladaEn: null, anuladaEnTurnoId: null }],
      anuladasDeOtrosTurnos: [{ id: 'v0', numero: 3, efectivo: 70_000, anuladaEn: '2026-09-16T14:00:00Z',
        anuladaEnTurnoId: 'hoy' }],
      gastos: [{ id: 'g', registradoEn: '2026-09-16T15:00:00Z', categoria: 'Transporte y fletes', descripcion: 'Flete',
        monto: 15_000, anuladoEn: null }],
      retiros: [{ id: 'r', registradoEn: '2026-09-16T17:00:00Z', motivo: 'Don Rubén', monto: 100_000,
        anuladoEn: '2026-09-16T18:00:00Z', motivoAnulacion: 'Lo devolvió' }],
      compras: [{ id: 'c', fechaRegistro: '2026-09-16T16:00:00Z', proveedor: 'Jotapartes', numeroFactura: 'FV-1',
        total: 50_000, estado: 'VIGENTE' }],
    }
    const movimientos = movimientosDelCajon(turno)

    assert.deepEqual(movimientos.map((m) => [m.tipo, m.detalle, m.anulado]), [
      ['DEVOLUCION', 'N.º 3', false],
      ['GASTO', 'Flete', false],
      ['COMPRA', 'Jotapartes · FV-1', false],
      ['RETIRO', 'Don Rubén', true],
      ['DEVOLUCION', 'N.º 7', false],
    ])
  })
})
