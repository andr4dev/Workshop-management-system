import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  agruparPorVenta, desdeCuandoEnPalabras, esDeudaVieja, fechaCorta, loMarcado, loQueSeLlevo, paraAbonar,
  renglonesDelGrupo, nombreDeLaDeuda, problemasDeLaFicha,
  resumenDeLaCartera, textoPendientes,
} from './cartera.js'
import { formatoCOP } from './formato.js'

const HOY = '2026-09-21'

test('desde cuándo debe, en palabras: hoy, ayer, o la fecha y cuántos días', () => {
  assert.equal(desdeCuandoEnPalabras(null, HOY), null)
  assert.equal(desdeCuandoEnPalabras('2026-09-21', HOY), 'desde hoy')
  assert.equal(desdeCuandoEnPalabras('2026-09-20', HOY), 'desde ayer')
  assert.equal(desdeCuandoEnPalabras('2026-09-12', HOY), 'desde el 12 sept 2026 · hace 9 días')
  assert.equal(fechaCorta('2026-01-05'), '5 ene 2026')
})

test('deuda vieja: más de 30 días', () => {
  assert.equal(esDeudaVieja('2026-08-22', HOY), false)
  assert.equal(esDeudaVieja('2026-08-21', HOY), true)
  assert.equal(esDeudaVieja(null, HOY), false)
})

test('lo de arriba de la lista dice cuántos deben y cuánto hay por cobrar', () => {
  assert.match(resumenDeLaCartera({ deben: 3, porCobrar: 180_000, clientes: [] }, 'DEBEN'),
    /^3 clientes deben · \$\s?180\.000 por cobrar$/)
  assert.match(resumenDeLaCartera({ deben: 1, porCobrar: 20_000, clientes: [] }, 'DEBEN'), /^1 cliente debe/)
  assert.equal(resumenDeLaCartera({ deben: 0, porCobrar: 0, clientes: [] }, 'DEBEN'), 'Nadie debe: todo al día')
  assert.match(resumenDeLaCartera({ deben: 1, porCobrar: 20_000, clientes: [{}, {}] }, 'HISTORIAL'),
    /^2 clientes han tenido fiado/)
})

test('cómo se nombra cada deuda y cuántas quedan', () => {
  assert.equal(nombreDeLaDeuda({ origen: 'VENTA', numeroVenta: 41 }), 'Venta N.º 41')
  assert.equal(nombreDeLaDeuda({ origen: 'CUADERNO' }), 'Saldo del cuaderno')
  assert.equal(textoPendientes(1), '1 venta pendiente')
  assert.equal(textoPendientes(2), '2 ventas pendientes')
})

test('la ficha cuadra: cada venta abonado + pendiente = fiado, y lo que debe es la suma de lo pendiente', () => {
  const ficha = {
    debe: 20_000,
    deudas: [
      { origen: 'VENTA', numeroVenta: 57, monto: 30_000, abonado: 10_000, pendiente: 20_000, estado: 'ABONADA' },
      { origen: 'VENTA', numeroVenta: 41, monto: 50_000, abonado: 50_000, pendiente: 0, estado: 'PAGADA' },
      { origen: 'VENTA', numeroVenta: 40, monto: 9_000, abonado: 0, pendiente: 0, estado: 'ANULADA' },
    ],
  }
  assert.deepEqual(problemasDeLaFicha(ficha), [])
  assert.equal(problemasDeLaFicha({ ...ficha, debe: 25_000 }).length, 1)
  const rota = { ...ficha, deudas: [{ ...ficha.deudas[0], abonado: 5_000 }] }
  assert.match(problemasDeLaFicha(rota)[0], /Venta N\.º 57/)
})

test('qué se llevó en una venta fiada: cada repuesto con su valor, y lo que la baja hasta lo fiado', () => {
  const deuda = {
    origen: 'VENTA', numeroVenta: 9, monto: 55_000,
    venta: {
      renglones: [
        { codigo: '104089', nombre: 'MOTUL 7100 10W30', marca: 'MOTUL', cantidad: 1, precioUnitario: 62_000,
          total: 62_000, cambio: 'NO_SE_CAMBIA' },
        { codigo: 'ABC123', nombre: 'FILTRO DE ACEITE', marca: 'INOKI', cantidad: 2, precioUnitario: 11_000,
          total: 22_000, cambio: null },
      ],
      subtotal: 84_000, descuento: 4_000, motivoDescuento: 'Cliente frecuente', total: 80_000,
    },
  }
  const r = loQueSeLlevo(deuda)
  assert.deepEqual(r.renglones.map((x) => [x.texto, x.detalle, x.total]), [
    ['1 × MOTUL 7100 10W30', 'sin cambio', 62_000],
    ['2 × FILTRO DE ACEITE', `INOKI · ${formatoCOP(11_000)} c/u`, 22_000],
  ])
  assert.deepEqual(r.descuento, { monto: 4_000, motivo: 'Cliente frecuente' })
  assert.equal(r.pagoAlLlevarselo, 25_000, 'la venta fue de 80.000 y quedó fiado 55.000')
  assert.equal(r.renglones.reduce((s, x) => s + x.total, 0) - r.descuento.monto - r.pagoAlLlevarselo, deuda.monto,
    'las partes suman lo fiado')

  const todoFiado = loQueSeLlevo({ ...deuda, monto: 80_000, venta: { ...deuda.venta, descuento: 0 } })
  assert.equal(todoFiado.descuento, null)
  assert.equal(todoFiado.pagoAlLlevarselo, 0)
  assert.equal(loQueSeLlevo({ origen: 'CUADERNO', monto: 20_000, venta: null }), null, 'el cuaderno no tiene venta')
})

// ── Por producto (spec 0016) ──────────────────────────────────────────────────

const producto = (id, ventaId, numeroVenta, posicion, descripcion, monto, abonado, estado = null, lineaVentaId = `l-${id}`) => ({
  id, origen: 'VENTA', ventaId, numeroVenta, lineaVentaId, posicion, descripcion, fecha: '2026-09-28', monto, abonado,
  pendiente: estado === 'ANULADA' ? 0 : monto - abonado,
  estado: estado ?? (abonado === 0 ? 'PENDIENTE' : abonado === monto ? 'PAGADA' : 'ABONADA'), abonos: [], venta: null,
})

// Como las manda el servidor: la más reciente primero, y dentro de una venta, el último producto primero.
const DEUDAS = [
  producto('d-filtro18', 'v18', 18, 1, 'FILTRO DE ACEITE', 11000, 11000),
  producto('d-motul18', 'v18', 18, 0, 'MOTUL 7100 10W30', 65000, 0),
  { ...producto('d-10', 'v10', 10, null, null, 90000, 20000, null, null) },
  { id: 'd-cuaderno', origen: 'CUADERNO', ventaId: null, numeroVenta: null, lineaVentaId: null, posicion: null,
    descripcion: null, fecha: '2026-07-01', monto: 20000, abonado: 0, pendiente: 20000, estado: 'PENDIENTE',
    motivo: 'Lo del cuaderno', abonos: [], venta: null },
]

test('la ficha junta los productos de una venta, en su orden, con las cifras y el estado de la venta', () => {
  const grupos = agruparPorVenta(DEUDAS)
  assert.deepEqual(grupos.map((g) => g.clave), ['v18', 'v10', 'd-cuaderno'])
  const v18 = grupos[0]
  assert.deepEqual(v18.productos.map((d) => d.descripcion), ['MOTUL 7100 10W30', 'FILTRO DE ACEITE'])
  assert.equal(v18.porProducto, true)
  assert.deepEqual([v18.monto, v18.abonado, v18.pendiente, v18.estado], [76000, 11000, 65000, 'ABONADA'])
  assert.equal(grupos[1].porProducto, false, 'la de antes que no se partió')
  assert.equal(grupos[2].estado, 'PENDIENTE')

  const anulada = agruparPorVenta([producto('a1', 'v6', 6, 0, 'X', 1000, 0, 'ANULADA'),
    producto('a2', 'v6', 6, 1, 'Y', 2000, 0, 'ANULADA')])[0]
  assert.deepEqual([anulada.estado, anulada.monto], ['ANULADA', 3000], 'anulada: lo que era, para verlo tachado')
})

test('cada renglón con su deuda; el que no tiene deuda se pagó al llevárselo', () => {
  const venta = { renglones: [{ lineaId: 'l-d-motul18', nombre: 'MOTUL 7100 10W30', cantidad: 1, total: 65000 },
    { lineaId: 'l-pagado', nombre: 'FILTRO DE ACEITE', cantidad: 1, total: 11000 }] }
  const grupo = agruparPorVenta([{ ...producto('d-motul18', 'v18', 18, 0, 'MOTUL 7100 10W30', 65000, 0), venta }])[0]
  assert.deepEqual(renglonesDelGrupo(grupo).map((r) => [r.renglon.nombre, r.deuda?.id ?? null]),
    [['MOTUL 7100 10W30', 'd-motul18'], ['FILTRO DE ACEITE', null]])
})

test('para abonar: lo pendiente, de lo más viejo a lo más nuevo, y lo marcado en el orden de la lista con lo que falta', () => {
  const grupos = paraAbonar(DEUDAS)
  assert.deepEqual(grupos.map((g) => g.clave), ['d-cuaderno', 'v10', 'v18'])
  assert.deepEqual(grupos[2].productos.map((d) => d.id), ['d-motul18'], 'el filtro ya está pagado')
  assert.deepEqual(loMarcado(grupos, ['d-motul18', 'd-cuaderno']), { ids: ['d-cuaderno', 'd-motul18'], monto: 85000 })
  assert.deepEqual(loMarcado(grupos, []), { ids: [], monto: 0 })
})

