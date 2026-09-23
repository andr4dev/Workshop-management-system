import { test } from 'node:test'
import assert from 'node:assert/strict'

import { htmlDeLaCartera, tituloDelFiltro } from './carteraImpresa.js'

const TIENDA = { nombreComercial: 'RD MOTORS' }

const LISTA = {
  deben: 2,
  porCobrar: 180000,
  clientes: [
    { id: '1', nombre: 'Juan Pérez', documento: '1094123456', celular: '3001112233', debe: 120000, pendientes: 2, desde: '2026-09-12', fiadoCerrado: false },
    { id: '2', nombre: 'María Gómez', documento: '1094999888', celular: '', debe: 60000, pendientes: 1, desde: '2026-09-21', fiadoCerrado: true },
  ],
}

test('el título dice a quiénes se está viendo', () => {
  assert.equal(tituloDelFiltro({ vista: 'DEBEN', modoFecha: 'VENTA' }), 'Los que deben')
  assert.equal(tituloDelFiltro({ vista: 'HISTORIAL', modoFecha: 'VENTA' }), 'Historial completo')
})

test('el título dice el período y por qué fecha se filtró', () => {
  assert.equal(
    tituloDelFiltro({ vista: 'DEBEN', modoFecha: 'VENTA', desde: '2026-09-01', hasta: '2026-09-21' }),
    'Los que deben · se les fió entre el 1 sept 2026 y el 21 sept 2026',
  )
  assert.equal(
    tituloDelFiltro({ vista: 'HISTORIAL', modoFecha: 'ABONO', desde: '2026-09-01', hasta: '2026-09-21' }),
    'Historial completo · abonaron entre el 1 sept 2026 y el 21 sept 2026',
  )
})

test('media fecha no es un período: se dice a quiénes y nada más', () => {
  assert.equal(tituloDelFiltro({ vista: 'DEBEN', modoFecha: 'ABONO', desde: '2026-09-01' }), 'Los que deben')
})

test('el papel lleva cada cliente, lo que debe y el total por cobrar', () => {
  const html = htmlDeLaCartera(LISTA, { vista: 'DEBEN', modoFecha: 'VENTA' }, TIENDA, '2026-09-21')

  assert.match(html, /Juan Pérez/)
  assert.match(html, /1094123456 · 3001112233/)
  assert.match(html, /2 ventas pendientes/)
  assert.match(html, /desde el 12 sept 2026 · hace 9 días/)
  assert.match(html, /\$\s180\.000/)
  assert.match(html, /2 clientes deben/)
  assert.match(html, /Impreso el 21 sept 2026/)
})

test('se marca a quien tiene el fiado cerrado y a quien debe desde hoy', () => {
  const html = htmlDeLaCartera(LISTA, { vista: 'DEBEN', modoFecha: 'VENTA' }, TIENDA, '2026-09-21')

  assert.match(html, /María Gómez<span class="marca">|María Gómez <span class="marca">fiado cerrado<\/span>/)
  assert.match(html, /desde hoy/)
})

test('quien está al día se dice al día, no "0 ventas pendientes"', () => {
  const alDia = { deben: 0, porCobrar: 0, clientes: [{ id: '3', nombre: 'Ana', documento: '123', celular: '', debe: 0, pendientes: 0, desde: null, fiadoCerrado: false }] }
  const html = htmlDeLaCartera(alDia, { vista: 'HISTORIAL', modoFecha: 'VENTA' }, TIENDA, '2026-09-21')

  assert.match(html, /Al día/)
  assert.match(html, /0 clientes deben/)
})

test('el nombre del cliente no puede meter etiquetas en el papel', () => {
  const conTrampa = { ...LISTA, clientes: [{ ...LISTA.clientes[0], nombre: '<script>alert(1)</script>' }] }
  const html = htmlDeLaCartera(conTrampa, { vista: 'DEBEN', modoFecha: 'VENTA' }, TIENDA, '2026-09-21')

  assert.ok(!html.includes('<script>alert(1)</script>'))
  assert.match(html, /&lt;script&gt;/)
})
