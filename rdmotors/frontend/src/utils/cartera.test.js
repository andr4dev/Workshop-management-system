import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  desdeCuandoEnPalabras, esDeudaVieja, fechaCorta, nombreDeLaDeuda, problemasDeLaFicha, resumenDeLaCartera,
  textoPendientes,
} from './cartera.js'

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
