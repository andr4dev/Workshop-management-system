import { test } from 'node:test'
import assert from 'node:assert/strict'
import { cambiosDeFicha, desgloseDeCambios } from './auditoria.js'

const base = {
  estado: 'VIGENTE', proveedor: 'Jotapartes', fechaDocumento: '2026-09-01', numeroFactura: 'FV-1',
  formaPago: 'EFECTIVO', cuenta: null, total: 30000,
  renglones: [{ codigo: 'ABC123', repuesto: 'FILTRO DE ACEITE INOKI', cantidad: 12, costoTotal: 30000, precioVenta: null }],
}

/** Quita el espacio que Intl pone después del signo de peso, que varía entre versiones de Node. */
const plano = (valor) => JSON.parse(JSON.stringify(valor).replace(/\$\s/g, '$'))

test('fotos iguales: desglose vacío', () => {
  const d = desgloseDeCambios(base, structuredClone(base))
  assert.equal(d.vacio, true)
  assert.deepEqual([d.factura, d.renglones, d.total], [[], [], null])
})

test('sin alguna de las dos fotos no inventa cambios', () => {
  assert.equal(desgloseDeCambios(null, base).vacio, true)
  assert.equal(desgloseDeCambios(base, undefined).vacio, true)
})

test('EL CASO DE LA CAPTURA: 12 → 17 por $30.000 → $42.500 dice que el costo por unidad no cambió', () => {
  const d = plano(desgloseDeCambios(base, {
    ...base, total: 42500, renglones: [{ ...base.renglones[0], cantidad: 17, costoTotal: 42500 }],
  }))

  assert.deepEqual(d.factura, [])
  assert.deepEqual(d.renglones, [{
    codigo: 'ABC123',
    repuesto: 'FILTRO DE ACEITE INOKI',
    tipo: 'cambiado',
    filas: [
      { etiqueta: 'Cantidad', antes: '12', despues: '17' },
      { etiqueta: 'Total pagado', antes: '$30.000', despues: '$42.500' },
      { etiqueta: 'Costo por unidad', antes: '$2.500', despues: '$2.500', igual: true },
    ],
    inventario: '+5 unidades',
  }])
  assert.deepEqual(d.total, { antes: '$30.000', despues: '$42.500', diferencia: '+$12.500' })
})

test('datos de la factura: anular, número vacío y pago, cada uno en su fila', () => {
  const d = desgloseDeCambios(base, {
    ...base, estado: 'ANULADA', numeroFactura: null, formaPago: 'TRANSFERENCIA', cuenta: 'Nequi',
  })
  assert.deepEqual(d.factura, [
    { etiqueta: 'Estado', antes: 'Vigente', despues: 'Anulada' },
    { etiqueta: 'N.º de factura', antes: 'FV-1', despues: 'sin número' },
    { etiqueta: 'Pago', antes: 'Efectivo', despues: 'Transferencia · Nequi' },
  ])
  assert.equal(d.total, null, 'el total no cambió: no se muestra')
})

test('renglones: solo precio no mueve inventario; agregado y quitado dicen cuánto entra y sale', () => {
  const d = plano(desgloseDeCambios(base, {
    ...base,
    total: 40000,
    renglones: [
      { codigo: 'XYZ9', repuesto: null, cantidad: 1, costoTotal: 10000, precioVenta: 15000 },
    ],
  }))
  assert.deepEqual(d.renglones.map((g) => [g.codigo, g.tipo, g.inventario]), [
    ['XYZ9', 'agregado', '+1 unidad'],
    ['ABC123', 'quitado', '−12 unidades'],
  ], 'los quitados van al final')
  assert.equal(d.renglones[0].filas.at(-1).despues, '$15.000')
  assert.equal(d.renglones[1].filas[0].despues, null)
  assert.equal(d.total.diferencia, '+$10.000')

  const soloPrecio = plano(desgloseDeCambios(base, {
    ...base, renglones: [{ ...base.renglones[0], precioVenta: 25000 }],
  }))
  assert.deepEqual(soloPrecio.renglones[0].filas, [
    { etiqueta: 'Precio de venta', antes: 'no lo cambia', despues: '$25.000' },
  ])
  assert.equal(soloPrecio.renglones[0].inventario, 'sin cambio')
})

test('menos unidades: el inventario baja con el menos tipográfico, y el unitario con centavos si los hay', () => {
  const d = plano(desgloseDeCambios(base, {
    ...base, total: 20000, renglones: [{ ...base.renglones[0], cantidad: 9, costoTotal: 20000 }],
  }))
  assert.equal(d.renglones[0].inventario, '−3 unidades')
  const unitario = d.renglones[0].filas.find((f) => f.etiqueta === 'Costo por unidad')
  assert.equal(unitario.igual, false)
  assert.match(unitario.despues, /^\$2\.222,22$/)
  assert.equal(d.total.diferencia, '−$10.000')
})

test('la fecha de la factura no se corre un día', () => {
  const [fila] = desgloseDeCambios(base, { ...base, fechaDocumento: '2026-09-02' }).factura
  assert.match(fila.antes, /^1 /)
  assert.match(fila.despues, /^2 /)
})

// ── Ficha del repuesto (spec 0002, H7) ─────────────────────────────────────

const ficha = {
  codigo: '352B59K', nombre: 'FILTRO ACEIT', categoria: 'FILTROS', aplicacion: 'PULSAR NS 200',
  marca: 'INOKI', precio: 6000, stockMinimo: 5,
}

test('ficha: fotos iguales no dicen nada, aunque el jsonb traiga 5.0 donde se guardó 5', () => {
  assert.deepEqual(cambiosDeFicha(ficha, { ...ficha, stockMinimo: 5.0, precio: 6000.0 }), [])
  assert.deepEqual(cambiosDeFicha(null, ficha), [])
})

test('ficha: cada campo cambiado es una frase, en el orden de la ficha', () => {
  const cambios = cambiosDeFicha(ficha, {
    ...ficha, nombre: 'FILTRO ACEITE', categoria: null, precio: 7500, stockMinimo: 8,
  })
  assert.equal(cambios.length, 4, cambios.join(' | '))
  assert.equal(cambios[0], 'Nombre: FILTRO ACEIT → FILTRO ACEITE')
  assert.equal(cambios[1], 'Categoría: FILTROS → Sin clasificar')
  assert.match(cambios[2], /^Precio de venta: \$\s?6\.000 → \$\s?7\.500$/)
  assert.equal(cambios[3], 'Avisar con: 5 o menos → 8 o menos')
})

test('ficha: quitar la aplicación se lee con guion, no con "null"', () => {
  assert.deepEqual(cambiosDeFicha(ficha, { ...ficha, aplicacion: null }), ['Aplicación: PULSAR NS 200 → —'])
})
