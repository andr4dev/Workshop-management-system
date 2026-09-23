import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  agregarRenglon, aplicarProblemas, billetesSugeridos, cambiarCantidad, cambioDelCobro, claveDelBorrador,
  comandoDeCobro, consultaDePerdida, llaveNueva, montoDescuento, pagosDelCobro, porcentajeDesdeTexto, problemaDelCobro,
  problemaParaAgregar, problemasDeLaVenta, quitarRenglon, refrescarRenglon, refrescarRenglones, restaurarVenta,
  serializarVenta, ventaAlVolver, AVISO_RETOMADA,
  textoDePerdida, totalesDe, ventaNueva, fiadoDelCobro,
} from './venta.js'

const juan = { id: 'c-juan', nombre: 'Juan Pérez', documento: '1234567', celular: '3001234567', debe: 20_000,
  datosQueFaltan: [], fiadoCerrado: false }

const filtro = { id: 'v-filtro', codigo: '352B59K', nombre: 'FILTRO ACEITE', marca: 'INOKI', precio: 13000, stock: 10, costoPromedio: 8000 }
const pastillas = { id: 'v-pastillas', codigo: '152RTX2B', nombre: 'PASTILLAS', marca: 'CBI', precio: 12000, stock: 5, costoPromedio: '7500.0000' }

/** 2 filtros + 1 pastilla = $38.000 */
function ventaDe38() {
  let renglones = agregarRenglon([], filtro)
  renglones = agregarRenglon(renglones, filtro)
  renglones = agregarRenglon(renglones, pastillas)
  return { ...ventaNueva('llave-fija'), renglones }
}

test('la llave es un UUID v4 de verdad, sin crypto.randomUUID', () => {
  const llave = llaveNueva()
  assert.match(llave, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
  assert.notEqual(llaveNueva(), llave)
  assert.equal(llaveNueva(() => new Uint8Array(16)), '00000000-0000-4000-8000-000000000000')
})

test('agregar el mismo repuesto suma cantidad en su renglón, no crea otro', () => {
  const v = ventaDe38()
  assert.deepEqual(v.renglones.map((r) => [r.codigo, r.cantidad]), [['352B59K', 2], ['152RTX2B', 1]])
  assert.equal(v.renglones[1].costoPromedio, 7500)
})

test('no se agrega sin precio ni por encima del stock', () => {
  assert.match(problemaParaAgregar({ ...filtro, precio: 0 }, []), /no tiene precio de venta/)
  assert.match(problemaParaAgregar({ ...filtro, stock: 0 }, []), /No hay unidades/)
  const conDos = agregarRenglon(agregarRenglon([], { ...filtro, stock: 2 }), { ...filtro, stock: 2 })
  assert.match(problemaParaAgregar({ ...filtro, stock: 2 }, conDos), /Ya están en la venta las 2 unidades/)
  assert.equal(problemaParaAgregar(filtro, conDos), null)
})

test('la cantidad escrita: solo dígitos y mínimo 1; pasar del stock se marca, no se corrige', () => {
  const v = ventaDe38()
  assert.equal(cambiarCantidad(v.renglones, 'v-filtro', '7a')[0].cantidad, 7)
  assert.equal(cambiarCantidad(v.renglones, 'v-filtro', '')[0].cantidad, 1)
  const demasiado = { ...v, renglones: cambiarCantidad(v.renglones, 'v-pastillas', '9') }
  assert.deepEqual(problemasDeLaVenta(demasiado), ['De 152RTX2B solo hay 5'])
  assert.deepEqual(quitarRenglon(v.renglones, 'v-filtro').map((r) => r.codigo), ['152RTX2B'])
})

// La MISMA tabla que DescuentoTest del backend: si calcularan distinto, se vería un total y se cobraría otro.
for (const [porcentaje, subtotal, esperado] of [
  [10, 38500, 3850], [7.5, 13333, 1000], [15, 20000, 3000], [33.33, 10000, 3333], [0.5, 1999, 10], [100, 45000, 45000],
]) {
  test(`descuento: ${porcentaje}% de $${subtotal} = $${esperado}, igual que el backend`, () => {
    assert.equal(montoDescuento({ modo: 'PORCENTAJE', valor: porcentaje }, subtotal), esperado)
  })
}

test('totales: subtotal, descuento en pesos y total', () => {
  const v = { ...ventaDe38(), descuento: { modo: 'MONTO', valor: 3000, motivo: 'negociación' } }
  assert.deepEqual(totalesDe(v), { subtotal: 38000, descuento: 3000, total: 35000 })
  assert.deepEqual(problemasDeLaVenta({ ...v, descuento: { modo: 'MONTO', valor: 40000 } }), ['El descuento es mayor que el total'])
  assert.equal(porcentajeDesdeTexto('7,5'), 7.5)
  assert.equal(porcentajeDesdeTexto('abc'), null)
})

test('billetes sugeridos: exacto y los que pasan el total, sin repetir', () => {
  assert.deepEqual(billetesSugeridos(38000), [38000, 40000, 50000, 100000])
  assert.deepEqual(billetesSugeridos(50000), [50000, 100000])
  assert.deepEqual(billetesSugeridos(13000), [13000, 20000, 50000, 100000])
  assert.deepEqual(billetesSugeridos(125000), [125000, 130000, 150000, 200000])
  assert.deepEqual(billetesSugeridos(0), [])
})

test('efectivo con billete de $50.000: un pago y $12.000 de cambio', () => {
  const cobro = { forma: 'EFECTIVO', recibido: '50.000', efectivo: '' }
  assert.deepEqual(pagosDelCobro(cobro, 38000), [{ forma: 'EFECTIVO', monto: 38000, recibido: 50000 }])
  assert.equal(cambioDelCobro(cobro, 38000), 12000)
  assert.equal(problemaDelCobro(cobro, 38000), null)
  assert.match(problemaDelCobro({ ...cobro, recibido: '20000' }, 38000), /no alcanza/)
  assert.equal(cambioDelCobro({ ...cobro, recibido: '' }, 38000), 0)
})

test('mixto: la parte en efectivo y el resto por transferencia, que suman el total', () => {
  const cobro = { forma: 'MIXTO', efectivo: '20000', recibido: '' }
  const pagos = pagosDelCobro(cobro, 38000)
  assert.deepEqual(pagos, [
    { forma: 'EFECTIVO', monto: 20000, recibido: null },
    { forma: 'TRANSFERENCIA', monto: 18000, recibido: null },
  ])
  assert.equal(pagos.reduce((s, p) => s + p.monto, 0), 38000)
  assert.match(problemaDelCobro({ ...cobro, efectivo: '' }, 38000), /cuánto paga en efectivo/)
  assert.match(problemaDelCobro({ ...cobro, efectivo: '38000' }, 38000), /menor que el total/)
})

test('una venta en $0 (descuento del 100%) se registra sin pagos', () => {
  assert.deepEqual(pagosDelCobro({ forma: 'EFECTIVO', recibido: '' }, 0), [])
  assert.equal(problemaDelCobro({ forma: 'MIXTO', efectivo: '' }, 0), null)
})

test('el comando de cobro lleva la llave, el precio que se vio y los pagos', () => {
  const v = { ...ventaDe38(), descuento: { modo: 'PORCENTAJE', valor: '10', motivo: 'Cliente frecuente' } }
  const comando = comandoDeCobro(v, { forma: 'TRANSFERENCIA' })
  assert.equal(comando.llave, 'llave-fija')
  assert.deepEqual(comando.renglones[0], { varianteId: 'v-filtro', cantidad: 2, precioVisto: 13000 })
  assert.deepEqual(comando.descuento, { modo: 'PORCENTAJE', valor: 10, motivo: 'Cliente frecuente' })
  assert.deepEqual(comando.pagos, [{ forma: 'TRANSFERENCIA', monto: 34200, recibido: null }])
})

test('fiar todo: sin pagos, todo queda debiendo, y el comando lleva a quién (spec 0008)', () => {
  const cobro = { forma: 'FIADO', cliente: juan, pagaAhora: '' }
  assert.deepEqual(pagosDelCobro(cobro, 38_000), [])
  assert.equal(fiadoDelCobro(cobro, 38_000), 38_000)
  assert.equal(problemaDelCobro(cobro, 38_000), null)
  const comando = comandoDeCobro(ventaDe38(), cobro)
  assert.equal(comando.clienteId, 'c-juan')
  assert.equal(comando.fiado, 38_000)
  assert.deepEqual(comando.pagos, [])
})

test('fiar una parte: paga $20.000 por transferencia y queda debiendo $18.000; los dos suman el total', () => {
  const cobro = { forma: 'FIADO', cliente: juan, pagaAhora: '20.000', formaPagaAhora: 'TRANSFERENCIA' }
  const pagos = pagosDelCobro(cobro, 38_000)
  assert.deepEqual(pagos, [{ forma: 'TRANSFERENCIA', monto: 20_000, recibido: null }])
  assert.equal(pagos[0].monto + fiadoDelCobro(cobro, 38_000), 38_000)
  assert.equal(cambioDelCobro(cobro, 38_000), 0)
})

test('no se fía sin escoger a quién, a quien tiene el fiado cerrado, ni si paga todo', () => {
  assert.equal(problemaDelCobro({ forma: 'FIADO', cliente: null }, 38_000), 'Escoge a quién se le fía')
  assert.match(problemaDelCobro({ forma: 'FIADO', cliente: { ...juan, fiadoCerrado: true } }, 38_000),
    /no se le fía/)
  assert.match(problemaDelCobro({ forma: 'FIADO', cliente: juan, pagaAhora: '38000' }, 38_000), /no es fiado/)
  // Que le falten la cédula y el celular ya no frena la venta (decisión 2, cambiada el 2026-09-21).
  assert.equal(problemaDelCobro({ forma: 'FIADO', cliente: { ...juan, documento: null, celular: null } }, 38_000), null)
})

test('de contado no hay fiado ni cliente, como siempre', () => {
  const comando = comandoDeCobro(ventaDe38(), { forma: 'EFECTIVO', recibido: '' })
  assert.equal(comando.fiado, 0)
  assert.equal(comando.clienteId, null)
})

test('al servidor se le pregunta por la pérdida con los renglones y el descuento, sin costos (spec 0004)', () => {
  const v = { ...ventaDe38(), descuento: { modo: 'MONTO', valor: 20000, motivo: 'Negociación' } }
  assert.deepEqual(consultaDePerdida(v), {
    renglones: [
      { varianteId: 'v-filtro', cantidad: 2, precioVisto: 13000 },
      { varianteId: 'v-pastillas', cantidad: 1, precioVisto: 12000 },
    ],
    descuento: { modo: 'MONTO', valor: 20000, motivo: 'Negociación' },
  })
  assert.equal(consultaDePerdida(ventaDe38()).descuento, null)
  assert.equal(JSON.stringify(consultaDePerdida(v)).includes('costo'), false)
})

test('al administrador el aviso le dice lo que costó y lo que cobra, y cuántos no tienen costo', () => {
  assert.match(textoDePerdida({ bajoCosto: true, sinCosto: 0, costo: 23500, cobrado: 18000, diferencia: 5500 }),
    /^Venta a pérdida: estos repuestos te costaron \$\s?23\.500 y cobras \$\s?18\.000 \(pierdes \$\s?5\.500\)$/)
  assert.match(textoDePerdida({ bajoCosto: true, sinCosto: 1, costo: 8000, cobrado: 2600, diferencia: 5400 }),
    /los repuestos con costo conocido te costaron .* 1 repuesto no tiene costo y no cuenta\.$/)
})

test('al cajero el aviso le dice que queda a pérdida, sin cifras (spec 0004, decisión 1)', () => {
  const texto = textoDePerdida({ bajoCosto: true, sinCosto: 0 })
  assert.equal(texto, 'Venta a pérdida: el total queda por debajo de lo que costaron estos repuestos')
  assert.doesNotMatch(texto, /\$/)
  assert.equal(textoDePerdida({ bajoCosto: true, sinCosto: 2 }),
    'Venta a pérdida: el total queda por debajo de lo que costaron los repuestos con costo conocido. '
    + '2 repuestos no tienen costo y no cuentan.')
  assert.equal(textoDePerdida({ bajoCosto: false, sinCosto: 0 }), null)
  assert.equal(textoDePerdida(null), null)
})

test('lo que responde el servidor: precio cambiado se actualiza y avisa; sin stock bloquea', () => {
  const v = ventaDe38()
  const renglones = aplicarProblemas(v.renglones, [
    { varianteId: 'v-filtro', tipo: 'PRECIO_CAMBIADO', precioVisto: 13000, precioActual: 14000 },
    { varianteId: 'v-pastillas', tipo: 'SIN_STOCK', disponible: 0, pedido: 1 },
  ])
  assert.equal(renglones[0].precio, 14000)
  assert.equal(renglones[0].problema, null)
  assert.match(renglones[0].aviso, /cambió de \$\s?13\.000 a \$\s?14\.000/)
  assert.equal(renglones[1].stock, 0)
  assert.deepEqual(problemasDeLaVenta({ ...v, renglones }), ['152RTX2B: Ya no quedan unidades'])
})

test('retomar: se guarda y se restaura igual, y lo que no es una venta con repuestos no se ofrece', () => {
  const v = { ...ventaDe38(), enviada: true, cobro: { forma: 'EFECTIVO', recibido: '50000' } }
  assert.deepEqual(restaurarVenta(serializarVenta(v)), v)
  assert.equal(restaurarVenta(serializarVenta(ventaNueva('x'))), null)
  assert.equal(restaurarVenta('{roto'), null)
  assert.equal(restaurarVenta(null), null)
})

test('RF-028: al volver, la venta guardada se retoma sola, con un aviso que no bloquea, y se revisan sus precios', () => {
  const guardada = ventaDe38()
  const alVolver = ventaAlVolver(guardada)
  assert.equal(alVolver.venta, guardada)
  assert.equal(alVolver.aviso, AVISO_RETOMADA)
  assert.equal(alVolver.revisarPrecios, true)
})

test('RF-028: una que se mandó a cobrar sin respuesta vuelve bloqueada, sin aviso ni revisión: solo se reintenta lo mismo', () => {
  const enviada = { ...ventaDe38(), enviada: true, cobro: { forma: 'EFECTIVO', recibido: '50000' } }
  const alVolver = ventaAlVolver(enviada)
  assert.equal(alVolver.venta, enviada)
  assert.equal(alVolver.venta.enviada, true)
  assert.equal(alVolver.aviso, null)
  assert.equal(alVolver.revisarPrecios, false)
})

test('RF-028: sin nada guardado arranca una venta nueva, cada vez con su propia llave', () => {
  const a = ventaAlVolver(null)
  assert.deepEqual(a.venta.renglones, [])
  assert.equal(a.aviso, null)
  assert.equal(a.revisarPrecios, false)
  assert.notEqual(a.venta.llave, ventaAlVolver(null).venta.llave)
})

test('RF-029: los precios se ponen al día por repuesto, no por posición; una ficha que no llegó deja el renglón igual', () => {
  const [filtroGuardado, pastillasGuardadas] = ventaDe38().renglones
  const fichaDePastillas = { id: pastillasGuardadas.varianteId, nombre: pastillasGuardadas.nombre, marca: pastillasGuardadas.marca,
    precio: pastillasGuardadas.precio + 1000, stock: 2, costoPromedio: null }

  // Las fichas llegan en otro orden, y la del filtro no llegó.
  const renglones = refrescarRenglones([filtroGuardado, pastillasGuardadas], [fichaDePastillas, null])

  assert.equal(renglones[0], filtroGuardado)
  assert.equal(renglones[1].precio, pastillasGuardadas.precio + 1000)
  assert.equal(renglones[1].stock, 2)
  assert.match(renglones[1].aviso, /mientras la venta estaba guardada/)
})

test('al retomar, el precio y el stock se ponen al día y se avisa si el precio cambió', () => {
  const [renglon] = ventaDe38().renglones
  const refrescado = refrescarRenglon(renglon, { ...filtro, precio: 15000, stock: 3 })
  assert.equal(refrescado.precio, 15000)
  assert.equal(refrescado.stock, 3)
  assert.match(refrescado.aviso, /mientras la venta estaba guardada/)
  assert.equal(refrescarRenglon(renglon, filtro).aviso, null)
})

test('la venta a medias se guarda por persona: otra persona en el mismo equipo no la hereda (spec 0004, RF-021)', () => {
  assert.equal(claveDelBorrador('id-de-carolina'), 'rdmotors:venta-en-curso:id-de-carolina')
  assert.notEqual(claveDelBorrador('id-de-carolina'), claveDelBorrador('id-de-andres'))
  assert.equal(claveDelBorrador('id-de-carolina'), claveDelBorrador('id-de-carolina'))
})
