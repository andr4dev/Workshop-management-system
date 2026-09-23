import { describe, test } from 'node:test'
import assert from 'node:assert/strict'
import { armarTicket, fechaDelTicket, htmlDelTicket, PIE_LEGAL, problemasDelTicket, VENTA_DE_EJEMPLO } from './ticket.js'
import { esElMostrador } from './imprimir.js'

const tienda = {
  nombreComercial: 'RD MOTORS',
  nit: '900.123.456-7',
  direccion: 'Calle 10 # 5-20',
  telefono: '300 123 4567',
  mensajePie: 'Gracias por su compra',
}

/** La venta N.º 10 de la prueba en el navegador: tres repuestos, cobro mixto con cambio. */
const ventaMixta = {
  numero: 10,
  estado: 'COBRADA',
  cobradaEn: '2026-09-14T22:03:00Z',
  subtotal: 68_000,
  descuento: 0,
  descuentoModo: null,
  descuentoPorcentaje: null,
  total: 68_000,
  renglones: [
    { codigo: 'ABC123', nombre: 'FILTRO DE ACEITE', marca: 'INOKI', cantidad: 1, precioUnitario: 24_000, total: 24_000 },
    { codigo: '352B59K', nombre: 'FILTRO ACEITE', marca: 'INOKI', cantidad: 1, precioUnitario: 9_000, total: 9_000 },
    { codigo: 'ABC23', nombre: 'FILTRO DE AIRE', marca: 'IRIDIUM', cantidad: 1, precioUnitario: 35_000, total: 35_000 },
  ],
  pagos: [
    { forma: 'EFECTIVO', monto: 20_000, recibido: 50_000, cambio: 30_000 },
    { forma: 'TRANSFERENCIA', monto: 48_000, recibido: null, cambio: 0 },
  ],
}

/** Venta N.º 41: $80.000, $30.000 en efectivo y $50.000 fiados a Juan (spec 0008). */
const ventaFiada = {
  ...ventaMixta,
  numero: 41,
  subtotal: 80_000,
  total: 80_000,
  renglones: [{ codigo: 'KIT1', nombre: 'KIT ARRASTRE', marca: 'CHOHO', cantidad: 1, precioUnitario: 80_000, total: 80_000 }],
  pagos: [{ forma: 'EFECTIVO', monto: 30_000, recibido: null, cambio: 0 }],
  fiado: 50_000,
  cliente: { id: 'c-juan', nombre: 'Juan Pérez', documento: '1.234.567' },
  debeDespues: 70_000,
}

describe('venta fiada (spec 0008)', () => {
  test('dice lo fiado, a nombre de quién y cuánto debe en total, y cuadra con los pagos', () => {
    const t = armarTicket(ventaFiada, tienda)
    assert.deepEqual(t.fiado, { monto: 50_000, cliente: 'Juan Pérez · 1.234.567', debeDespues: 70_000 })
    assert.deepEqual(problemasDelTicket(t), [])
    const html = htmlDelTicket(t)
    assert.match(html, /FIADO A/)
    assert.match(html, /Juan Pérez · 1\.234\.567/)
    assert.match(html, /Debe en total/)
  })

  test('si los pagos y lo fiado no dan el total, se dice', () => {
    const t = armarTicket({ ...ventaFiada, fiado: 40_000 }, tienda)
    assert.equal(problemasDelTicket(t).length, 1)
    assert.match(problemasDelTicket(t)[0], /lo fiado/)
  })

  test('una venta de contado no trae nada de fiado', () => {
    assert.equal(armarTicket(ventaMixta, tienda).fiado, null)
    assert.doesNotMatch(htmlDelTicket(armarTicket(ventaMixta, tienda)), /FIADO/)
  })
})

describe('armarTicket', () => {
  test('lleva número, fecha en hora de Colombia, renglones, total y pagos tal como vienen del servidor', () => {
    const t = armarTicket(ventaMixta, tienda)

    assert.equal(t.titulo, 'COMPROBANTE DE PAGO')
    assert.equal(t.numero, 10)
    // 22:03 UTC son las 5:03 p. m. en Colombia (UTC−5), sin importar la zona del equipo.
    assert.match(t.fecha, /^14\/09\/2026/)
    assert.match(t.fecha, /5:03/)
    assert.deepEqual(t.renglones.map((r) => r.descripcion), ['FILTRO DE ACEITE INOKI', 'FILTRO ACEITE INOKI', 'FILTRO DE AIRE IRIDIUM'])
    assert.equal(t.total, 68_000)
    assert.equal(t.descuento, null)
    assert.equal(t.anulada, null)
  })

  test('encabezado con los datos de la tienda, y sin líneas vacías para lo que no se ha dado', () => {
    assert.deepEqual(armarTicket(ventaMixta, tienda).tienda.lineas, ['NIT 900.123.456-7', 'Calle 10 # 5-20', 'Tel. 300 123 4567'])

    const soloNombre = armarTicket(ventaMixta, { nombreComercial: 'RD MOTORS', nit: null, direccion: null, telefono: null, mensajePie: null })
    assert.equal(soloNombre.tienda.nombre, 'RD MOTORS')
    assert.deepEqual(soloNombre.tienda.lineas, [])
    assert.deepEqual(soloNombre.pie, [PIE_LEGAL])
  })

  test('el pie siempre dice que no es factura electrónica, después del mensaje de la tienda', () => {
    assert.deepEqual(armarTicket(ventaMixta, tienda).pie, ['Gracias por su compra', PIE_LEGAL])
  })

  test('en efectivo con lo recibido lleva recibido y cambio; por transferencia, solo el monto', () => {
    const [efectivo, transferencia] = armarTicket(ventaMixta, tienda).pagos

    assert.deepEqual(efectivo, { forma: 'Efectivo', monto: 20_000, recibido: 50_000, cambio: 30_000 })
    assert.deepEqual(transferencia, { forma: 'Transferencia', monto: 48_000, recibido: null, cambio: null })
  })

  test('en efectivo sin escribir con cuánto pagó, no inventa recibido ni cambio', () => {
    const venta = { ...ventaMixta, pagos: [{ forma: 'EFECTIVO', monto: 68_000, recibido: null, cambio: 0 }] }
    assert.deepEqual(armarTicket(venta, tienda).pagos, [{ forma: 'Efectivo', monto: 68_000, recibido: null, cambio: null }])
  })

  test('el descuento sale solo si lo hubo, y con su porcentaje si se dio en %', () => {
    const conPorcentaje = { ...ventaMixta, subtotal: 38_500, descuento: 3_850, descuentoModo: 'PORCENTAJE', descuentoPorcentaje: 10, total: 34_650 }
    assert.deepEqual(armarTicket(conPorcentaje, tienda).descuento, { monto: 3_850, porcentaje: 10 })

    const enPesos = { ...ventaMixta, descuento: 1_000, descuentoModo: 'MONTO', descuentoPorcentaje: null }
    assert.deepEqual(armarTicket(enPesos, tienda).descuento, { monto: 1_000, porcentaje: null })
  })

  test('una venta anulada sale marcada, con la fecha de la anulación', () => {
    const anulada = { ...ventaMixta, estado: 'ANULADA', anuladaEn: '2026-09-15T01:30:00Z' }
    const t = armarTicket(anulada, tienda)

    assert.ok(t.anulada)
    assert.match(t.anulada.fecha, /^14\/09\/2026/)   // 01:30 UTC del 15 es el 14 a las 8:30 p. m. en Colombia
    assert.match(htmlDelTicket(t), />ANULADA</)
  })

  test('quién atendió solo sale si se sabe (spec 0004)', () => {
    assert.doesNotMatch(htmlDelTicket(armarTicket(ventaMixta, tienda)), /Atendió/)
    assert.match(htmlDelTicket(armarTicket(ventaMixta, tienda, { atendio: 'Carlos' })), /Atendió: Carlos/)
  })

  test('RF-023: dice quién cobró la venta, con el nombre que trae el servidor', () => {
    const cobrada = { ...ventaMixta, vendidoPor: { id: 'u-1', nombre: 'Carolina' } }
    assert.equal(armarTicket(cobrada, tienda).atendio, 'Carolina')
    assert.match(htmlDelTicket(armarTicket(cobrada, tienda)), /Atendió: Carolina/)
    assert.match(htmlDelTicket(armarTicket(VENTA_DE_EJEMPLO, tienda)), /Atendió: Carolina/)
  })
})

describe('problemasDelTicket (RF-019)', () => {
  test('una venta del servidor cuadra', () => {
    assert.deepEqual(problemasDelTicket(armarTicket(ventaMixta, tienda)), [])
    assert.deepEqual(problemasDelTicket(armarTicket(VENTA_DE_EJEMPLO, tienda)), [])
  })

  test('renglones que no suman el subtotal', () => {
    const venta = { ...ventaMixta, subtotal: 70_000, total: 70_000, pagos: [{ forma: 'TRANSFERENCIA', monto: 70_000 }] }
    const problemas = problemasDelTicket(armarTicket(venta, tienda))
    assert.equal(problemas.length, 1)
    assert.match(problemas[0], /renglones suman \$\s68\.000 y el subtotal es \$\s70\.000/)
  })

  test('un renglón cuya cantidad por precio no da su total', () => {
    const renglones = [{ ...ventaMixta.renglones[0], cantidad: 2 }, ...ventaMixta.renglones.slice(1)]
    assert.match(problemasDelTicket(armarTicket({ ...ventaMixta, renglones }, tienda))[0], /ABC123/)
  })

  test('descuento que no explica el total, y pagos que no lo suman', () => {
    const venta = { ...ventaMixta, descuento: 1_000, descuentoModo: 'MONTO' }
    const problemas = problemasDelTicket(armarTicket(venta, tienda))
    assert.equal(problemas.length, 1)
    assert.match(problemas[0], /de descuento no da/)

    const pagosCortos = { ...ventaMixta, pagos: [ventaMixta.pagos[0]] }
    assert.match(problemasDelTicket(armarTicket(pagosCortos, tienda))[0], /pagos suman \$\s20\.000/)
  })
})

describe('htmlDelTicket', () => {
  test('es para papel de 80 mm y lleva cada cifra', () => {
    const html = htmlDelTicket(armarTicket(ventaMixta, tienda))

    assert.match(html, /@page \{ size: 80mm auto; margin: 0; \}/)
    assert.match(html, /COMPROBANTE DE PAGO/)
    assert.match(html, /N\.º 10/)
    assert.match(html, /ABC23 · 1 × \$\s35\.000/)
    assert.match(html, /TOTAL<\/span><span class="der">\$\s68\.000/)
    assert.match(html, /Recibido<\/span><span class="der">\$\s50\.000/)
    assert.match(html, /Cambio<\/span><span class="der">\$\s30\.000/)
    assert.match(html, /no es factura electrónica/)
  })

  test('sin descuento no repite el subtotal; con descuento lleva subtotal y descuento en negativo', () => {
    assert.doesNotMatch(htmlDelTicket(armarTicket(ventaMixta, tienda)), /Subtotal/)

    const con = { ...ventaMixta, subtotal: 38_500, descuento: 3_850, descuentoModo: 'PORCENTAJE', descuentoPorcentaje: 7.5, total: 34_650 }
    const html = htmlDelTicket(armarTicket(con, tienda))
    assert.match(html, /Subtotal<\/span><span class="der">\$\s38\.500/)
    assert.match(html, /Descuento \(7,5 %\)<\/span><span class="der">-\$\s3\.850/)
  })

  test('escapa lo que viene de los datos: un nombre con < o & no rompe el documento', () => {
    const venta = { ...ventaMixta, renglones: [{ ...ventaMixta.renglones[0], nombre: 'KIT <ARRASTRE> & PIÑÓN "X"' }] }
    const html = htmlDelTicket(armarTicket(venta, { ...tienda, nombreComercial: 'R&D <Motors>' }))

    assert.match(html, /KIT &lt;ARRASTRE&gt; &amp; PIÑÓN &quot;X&quot;/)
    assert.match(html, /R&amp;D &lt;Motors&gt;/)
    assert.doesNotMatch(html, /<ARRASTRE>/)
  })

  test('un nombre largo sale completo, para que baje de línea en vez de cortarse', () => {
    const largo = 'KIT DE ARRASTRE COMPLETO CADENA 428H 118 ESLABONES PIÑÓN 14T CORONA 43T REFORZADO'
    const venta = { ...ventaMixta, renglones: [{ ...ventaMixta.renglones[0], nombre: largo }] }
    const html = htmlDelTicket(armarTicket(venta, tienda))

    assert.ok(html.includes(largo))
    assert.match(html, /overflow-wrap: anywhere/)
    assert.doesNotMatch(html, /text-overflow|…/)
  })
})

describe('fechaDelTicket', () => {
  test('en hora de Colombia, y vacía si no hay fecha', () => {
    assert.match(fechaDelTicket('2026-01-01T04:59:00Z'), /^31\/12\/2025/)
    assert.equal(fechaDelTicket(null), '')
  })
})

describe('esElMostrador: solo imprime al cobrar el computador del mostrador', () => {
  for (const [caso, equipo, esperado] of [
    ['computador con mouse', { punteroGrueso: false, anchoPantalla: 1366 }, true],
    ['tablet (táctil, aunque sea ancha)', { punteroGrueso: true, anchoPantalla: 1024 }, false],
    ['celular', { punteroGrueso: true, anchoPantalla: 390 }, false],
    ['ventana angosta en el computador', { punteroGrueso: false, anchoPantalla: 640 }, false],
  ]) {
    test(caso, () => assert.equal(esElMostrador(equipo), esperado))
  }
})
