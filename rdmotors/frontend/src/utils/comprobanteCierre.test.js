import { describe, test } from 'node:test'
import assert from 'node:assert/strict'
import {
  armarComprobanteCierre, htmlDelComprobanteCierre, PIE_CIERRE, problemasDelComprobanteCierre,
} from './comprobanteCierre.js'
import { formatoCOP } from './formato.js'

const tienda = { nombreComercial: 'RD MOTORS', nit: '900.123.456-7', direccion: null, telefono: null, mensajePie: null }

/** El día del ejemplo del spec 0006 (§2), como lo devuelve `GET /api/turnos/{id}`. */
const turno = {
  id: 'hoy',
  estado: 'CERRADO',
  fondo: 100_000,
  abiertoEn: '2026-09-16T13:00:00Z',
  cerradoEn: '2026-09-16T23:30:00Z',
  cierre: {
    ventasEfectivo: 223_400, ventasTransferencia: 111_000, descuentos: 0, devolucionesEfectivo: 70_000,
    gastosCajon: 15_000, retiros: 100_000, comprasCajon: 50_000, esperado: 88_400, contado: 87_000, diferencia: -1_400,
  },
  observaciones: 'Se dio mal un cambio',
  gastos: [
    { id: 'g1', categoria: 'Transporte y fletes', descripcion: 'Flete', monto: 15_000, anuladoEn: null },
    { id: 'g2', categoria: 'Transporte y fletes', descripcion: 'Cero de más', monto: 150_000, anuladoEn: '2026-09-16T14:00:00Z' },
  ],
  retiros: [{ id: 'r1', motivo: 'Se lo llevó don <Rubén>', monto: 100_000, anuladoEn: null }],
  compras: [{ id: 'c1', proveedor: 'Importadora Jotapartes', numeroFactura: 'FV-9912', total: 50_000, estado: 'VIGENTE' }],
  ventas: [
    { id: 'v1', numero: 20, turnoId: 'hoy', efectivo: 223_400, transferencia: 0, anuladaEnTurnoId: null },
    { id: 'v2', numero: 21, turnoId: 'hoy', efectivo: 0, transferencia: 111_000, anuladaEnTurnoId: null },
  ],
  anuladasDeOtrosTurnos: [{ id: 'v0', numero: 19, turnoId: 'ayer', efectivo: 70_000, transferencia: 0, anuladaEnTurnoId: 'hoy' }],
}

/** El mismo cierre con un abono de cliente en efectivo y otro por transferencia (spec 0008). */
const conAbonos = {
  ...turno,
  cierre: { ...turno.cierre, abonosEfectivo: 30_000, abonosTransferencia: 5_000, ventasFiado: 50_000,
    esperado: 118_400, contado: 118_400, diferencia: 0 },
  abonos: [
    { id: 'a1', numero: 7, clienteId: 'c-juan', cliente: 'Juan Pérez', monto: 30_000, forma: 'EFECTIVO', anuladoEn: null },
    { id: 'a2', numero: 8, clienteId: 'c-ana', cliente: 'Ana Gómez', monto: 5_000, forma: 'TRANSFERENCIA', anuladoEn: null },
    { id: 'a3', numero: 9, clienteId: 'c-ana', cliente: 'Ana Gómez', monto: 99_000, forma: 'EFECTIVO', anuladoEn: '2026-09-16T20:00:00Z' },
  ],
}

describe('los abonos en el comprobante del cierre (spec 0008)', () => {
  test('salen con el nombre del cliente, el anulado no cuenta, y lo fiado se dice aparte', () => {
    const c = armarComprobanteCierre(conAbonos, tienda)

    assert.equal(c.abonosEfectivo, 30_000)
    assert.deepEqual(c.abonos.map((a) => a.cliente), ['Juan Pérez', 'Ana Gómez'])
    assert.deepEqual(problemasDelComprobanteCierre(c), [])
    const html = htmlDelComprobanteCierre(c)
    assert.match(html, /ABONOS DE CLIENTES/)
    assert.match(html, /Juan Pérez/)
    assert.match(html, /Fiado \(lo deben los clientes\)/)
  })

  test('si los abonos en efectivo no dan la parte firmada, se dice', () => {
    const c = armarComprobanteCierre({ ...conAbonos, cierre: { ...conAbonos.cierre, abonosEfectivo: 10_000 } }, tienda)
    assert.match(problemasDelComprobanteCierre(c).find((p) => p.includes('abonos')), /Los abonos en efectivo suman/)
  })
})

describe('armarComprobanteCierre', () => {
  test('el ejemplo del spec: desglose, contado, diferencia y el detalle de cada salida, sin los anulados', () => {
    const c = armarComprobanteCierre(turno, tienda)

    assert.equal(c.titulo, 'CIERRE DE CAJA')
    assert.equal(c.esperado, 88_400)
    assert.equal(c.diferencia, -1_400)
    assert.deepEqual(c.gastosPorCategoria, [{ categoria: 'Transporte y fletes', gastos: 1, monto: 15_000 }])
    assert.deepEqual(c.anulaciones, [{ numero: 19, monto: 70_000 }])
    assert.deepEqual(c.retiros, [{ motivo: 'Se lo llevó don <Rubén>', monto: 100_000 }])
    assert.equal(c.ventas, 2)
    assert.deepEqual(c.pie, [PIE_CIERRE])
    assert.deepEqual(problemasDelComprobanteCierre(c), [])
  })

  test('una venta de este turno anulada aquí mismo sale entre las anulaciones; una transferencia anulada no devuelve efectivo', () => {
    const conAnulada = {
      ...turno,
      ventas: [...turno.ventas,
        { id: 'v3', numero: 22, turnoId: 'hoy', efectivo: 10_000, transferencia: 0, anuladaEnTurnoId: 'hoy' },
        { id: 'v4', numero: 23, turnoId: 'hoy', efectivo: 0, transferencia: 5_000, anuladaEnTurnoId: 'hoy' }],
    }
    assert.deepEqual(armarComprobanteCierre(conAnulada, tienda).anulaciones.map((a) => a.numero), [22, 19])
  })

  test('si una parte no cuadra con el cierre, lo dice', () => {
    const c = { ...armarComprobanteCierre(turno, tienda), retirosTotal: 90_000 }
    assert.deepEqual(problemasDelComprobanteCierre(c),
      [`Los retiros suman ${formatoCOP(100_000)} y el cierre dice ${formatoCOP(90_000)}`])
  })
})

describe('htmlDelComprobanteCierre', () => {
  test('lleva lo que debería haber, lo contado, el faltante y las observaciones, y escapa los textos', () => {
    const html = htmlDelComprobanteCierre(armarComprobanteCierre(turno, tienda))

    assert.match(html, /CIERRE DE CAJA/)
    assert.ok(html.includes(formatoCOP(88_400)))
    assert.ok(html.includes(formatoCOP(87_000)))
    assert.ok(html.includes(`FALTAN ${formatoCOP(1_400)}`.toUpperCase()))
    assert.match(html, /Observaciones: Se dio mal un cambio/)
    assert.ok(html.includes('don &lt;Rubén&gt;'))
    assert.ok(!html.includes('don <Rubén>'))
    assert.match(html, /Venta N\.º 19/)
    assert.match(html, /size: 80mm auto/)
  })
})
