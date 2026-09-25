import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  fechaCorta, hayCostosEscritos, opcionDeCajon, precioParaEnviar, problemaDelRenglon, problemasDelPago,
  renglonesRepetidos, sumarIva, textoDelPago, ultimaCategoriaElegida, ultimaCompraDe,
} from './compra.js'

// El kardex llega del más reciente al más antiguo (el controlador lo invierte).
const kardex = [
  { tipo: 'VENTA', cuando: '2026-09-10T15:00:00Z', costoUnitario: 9 },
  { tipo: 'COMPRA', cuando: '2026-09-01T15:00:00Z', costoUnitario: 8 },
  { tipo: 'COMPRA', cuando: '2026-08-01T15:00:00Z', costoUnitario: 10 },
]

test('spec 0005, RF-010: la categoría sugerida es la del último repuesto nuevo que escogió una', () => {
  const renglones = [
    { id: 1, repuestoNuevo: { categoriaId: 'filtros' } },
    { id: 2, repuesto: { codigo: 'YA-EXISTE' } },
    { id: 3, repuestoNuevo: { categoriaId: 'frenos' } },
    // Reutiliza un concepto: no escoge categoría y no cambia la sugerencia.
    { id: 4, repuestoNuevo: { productoId: 'p1', categoriaId: null } },
  ]
  assert.equal(ultimaCategoriaElegida(renglones), 'frenos')
  assert.equal(ultimaCategoriaElegida([{ id: 1, repuesto: {} }]), null)
  assert.equal(ultimaCategoriaElegida(undefined), null)
})

test('toma la compra más reciente', () => {
  assert.equal(ultimaCompraDe(kardex).costoUnitario, 8)
})

test('ignora la venta aunque sea el último movimiento', () => {
  // El costo de una venta es el promedio, no lo que se le pagó a un proveedor.
  assert.notEqual(ultimaCompraDe(kardex).costoUnitario, 9)
})

test('sin compras no sugiere nada', () => {
  assert.equal(ultimaCompraDe([]), null)
  assert.equal(ultimaCompraDe(null), null)
  assert.equal(ultimaCompraDe([{ tipo: 'VENTA', costoUnitario: 9 }]), null)
})

test('un costo con decimales se redondea para el campo y se conserva exacto para mostrar', () => {
  const r = ultimaCompraDe([{ tipo: 'COMPRA', cuando: null, costoUnitario: 13333.3333 }])
  assert.equal(r.costoUnitario, 13333)
  assert.equal(r.costoExacto, 13333.3333)
})

test('el precio sin cambios no se manda', () => {
  assert.equal(precioParaEnviar('25000', 25000), null)
  assert.equal(precioParaEnviar('25.000', 25000), null)
})

test('el precio vacío no se manda', () => {
  assert.equal(precioParaEnviar('', 25000), null)
})

test('el precio cambiado se manda', () => {
  assert.equal(precioParaEnviar('27000', 25000), 27000)
})

test('fecha corta', () => {
  assert.equal(fechaCorta(null), '')
  assert.ok(fechaCorta('2026-08-13T15:00:00Z').length > 0)
})

const existente = (id, codigo) => ({ id, repuesto: { codigo }, repuestoNuevo: null })
const nuevo = (id, codigo) => ({ id, repuesto: null, repuestoNuevo: { codigo } })
const vacio = (id) => ({ id, repuesto: null, repuestoNuevo: null })

test('marca el segundo renglón con el mismo repuesto, no el primero', () => {
  const r = renglonesRepetidos([existente('a', 'ABC123'), existente('b', 'X1'), existente('c', 'ABC123')])
  assert.equal(r.get('c'), 'a')
  assert.equal(r.has('a'), false)
  assert.equal(r.size, 1)
})

test('un repuesto nuevo con el código de uno existente también cuenta como repetido', () => {
  assert.equal(renglonesRepetidos([existente('a', 'ABC123'), nuevo('b', 'ABC123')]).get('b'), 'a')
})

test('los renglones sin repuesto no se comparan', () => {
  assert.equal(renglonesRepetidos([vacio('a'), vacio('b')]).size, 0)
})

const base = { id: 'x', codigo: '', repuesto: null, repuestoNuevo: null, cantidad: '',
  modo: 'UNITARIO', costoTotal: '', costoUnitario: '', precioVenta: '' }

test('un renglón vacío no tiene problema: se ignora', () => {
  assert.equal(problemaDelRenglon(base), null)
})

test('un código tecleado sin repuesto enganchado no pasa en silencio', () => {
  assert.match(problemaDelRenglon({ ...base, codigo: 'ABC123', cantidad: '10' }), /Falta el repuesto/)
})

test('pide cantidad, costo y, en uno nuevo, precio', () => {
  const conRepuesto = { ...base, codigo: 'A', repuesto: { codigo: 'A', precio: 100 } }
  assert.match(problemaDelRenglon(conRepuesto), /cantidad/)
  assert.match(problemaDelRenglon({ ...conRepuesto, cantidad: '5' }), /costo/)
  assert.equal(problemaDelRenglon({ ...conRepuesto, cantidad: '5', costoUnitario: '50' }), null)

  const nuevo = { ...base, codigo: 'B', repuestoNuevo: { codigo: 'B' }, cantidad: '5', costoUnitario: '50' }
  assert.match(problemaDelRenglon(nuevo), /precio/)
  assert.equal(problemaDelRenglon({ ...nuevo, precioVenta: '90' }), null)
})

test('en modo Total el costo que cuenta es el total', () => {
  const r = { ...base, codigo: 'A', repuesto: { codigo: 'A' }, cantidad: '5', modo: 'TOTAL', costoUnitario: '50' }
  assert.match(problemaDelRenglon(r), /costo/)
  assert.equal(problemaDelRenglon({ ...r, costoTotal: '250' }), null)
})

test('sin forma de pago elegida hay problema: no hay efectivo por defecto', () => {
  assert.deepEqual(problemasDelPago({ formaPago: '', cuentaId: '' }),
    { formaPago: 'Elige cómo se pagó', cuenta: null })
})

test('una transferencia pide la cuenta; el efectivo no', () => {
  assert.match(problemasDelPago({ formaPago: 'TRANSFERENCIA', cuentaId: '' }).cuenta, /cuenta/)
  assert.deepEqual(problemasDelPago({ formaPago: 'TRANSFERENCIA', cuentaId: 'c1' }),
    { formaPago: null, cuenta: null })
  assert.deepEqual(problemasDelPago({ formaPago: 'EFECTIVO', cuentaId: '' }),
    { formaPago: null, cuenta: null })
})

test('textoDelPago dice la cuenta solo en transferencias, y si el efectivo salió del cajón', () => {
  assert.equal(textoDelPago('EFECTIVO', null), 'Efectivo')
  assert.equal(textoDelPago('EFECTIVO', null, true), 'Efectivo · del cajón')
  assert.equal(textoDelPago('TRANSFERENCIA', 'Nequi', true), 'Transferencia · Nequi')
  assert.equal(textoDelPago('TRANSFERENCIA', 'Nequi del dueño'), 'Transferencia · Nequi del dueño')
  assert.equal(textoDelPago(null, null), '')
})


test('«con plata del cajón»: solo en efectivo, y sin turno abierto no se marca', () => {
  const turno = { id: 't1' }
  assert.equal(opcionDeCajon({ formaPago: 'TRANSFERENCIA', turnoAbierto: turno }).visible, false)
  assert.equal(opcionDeCajon({ formaPago: '', turnoAbierto: turno }).visible, false)
  assert.deepEqual(opcionDeCajon({ formaPago: 'EFECTIVO', turnoAbierto: turno }),
    { visible: true, bloqueada: false, bloqueaFormaDePago: false, porQue: null })
  const sinTurno = opcionDeCajon({ formaPago: 'EFECTIVO', turnoAbierto: null })
  assert.equal(sinTurno.bloqueada, true)
  assert.match(sinTurno.porQue, /No hay un turno abierto/)
})

test('al corregir una compra del cajón: con su turno abierto se cambia; con su turno cerrado no, ni la forma de pago', () => {
  const correccion = { pagadaDeCaja: true, turnoId: 't1' }
  assert.equal(opcionDeCajon({ formaPago: 'EFECTIVO', correccion, turnoAbierto: { id: 't1' } }).bloqueada, false)

  const cerrado = opcionDeCajon({ formaPago: 'EFECTIVO', correccion, turnoAbierto: { id: 't2' } })
  assert.equal(cerrado.bloqueada, true)
  assert.equal(cerrado.bloqueaFormaDePago, true)
  assert.match(cerrado.porQue, /ya se cerró/)
  assert.equal(opcionDeCajon({ formaPago: 'EFECTIVO', correccion, turnoAbierto: null }).bloqueaFormaDePago, true)
})

test('spec 0012, RF-020: sumar el 19% a los costos escritos, al peso, en el campo que se esté usando', () => {
  const renglones = [
    { id: 1, modo: 'TOTAL', costoTotal: '308.274', costoUnitario: '' },
    { id: 2, modo: 'UNITARIO', costoTotal: '', costoUnitario: '7280' },
    { id: 3, modo: 'TOTAL', costoTotal: '', costoUnitario: '' },
  ]
  const conIva = sumarIva(renglones, 19)
  assert.equal(conIva[0].costoTotal, '366846')
  assert.equal(conIva[1].costoUnitario, '8663')
  assert.equal(conIva[2].costoTotal, '')
  // No toca los de antes: con ellos se deshace.
  assert.equal(renglones[0].costoTotal, '308.274')
  assert.equal(hayCostosEscritos(renglones), true)
  assert.equal(hayCostosEscritos([renglones[2]]), false)
})
