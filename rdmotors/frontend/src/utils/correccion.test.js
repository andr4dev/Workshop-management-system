import { test } from 'node:test'
import assert from 'node:assert/strict'
import { desgloseDeCambios } from './auditoria.js'
import {
  fotografiaDelDetalle, fotografiaDelFormulario, lineaParaCorregir, mismaEntrada,
  precioParaCorreccion, renglonesDesdeDetalle, totalDeLinea,
} from './correccion.js'

const renglonVacio = () => ({
  id: 'x', codigo: '', repuesto: null, repuestoNuevo: null, cantidad: '', modo: 'TOTAL',
  costoTotal: '', costoUnitario: '', precioVenta: '', codigoBuscado: null,
})

const detalle = {
  id: 'c1', version: 3, estado: 'VIGENTE', proveedor: 'Jotapartes', proveedorId: 'p1',
  fechaDocumento: '2026-09-01', numeroFactura: 'FV-1', formaPago: 'EFECTIVO', cuenta: null,
  total: 285200,
  renglones: [
    // Por total, con unitario de decimales infinitos
    { lineaId: 'l1', varianteId: 'v1', codigo: '352B59K', nombre: 'FILTRO', marca: 'INOKI',
      cantidad: 15, modoCaptura: 'TOTAL', costoTotal: 200000, costoUnitario: 13333.3333,
      precioVenta: null, precioActual: 9000, stockActual: 15, costoPromedioActual: 13333.3333 },
    // Por unidad, y fijó un precio que hoy sigue igual
    { lineaId: 'l2', varianteId: 'v2', codigo: '152RTX2B', nombre: 'PASTILLAS', marca: 'CBI',
      cantidad: 6, modoCaptura: 'UNITARIO', costoTotal: 85200, costoUnitario: 14200,
      precioVenta: 25000, precioActual: 25000, stockActual: 6, costoPromedioActual: 14200 },
  ],
}

test('un renglón que nadie tocó se manda EXACTAMENTE como estaba', () => {
  const [filtro, pastillas] = renglonesDesdeDetalle(detalle, renglonVacio)

  assert.deepEqual(lineaParaCorregir(filtro), {
    lineaId: 'l1', varianteId: 'v1', repuestoNuevo: null, cantidad: 15, modo: 'TOTAL',
    costoTotal: 200000, costoUnitario: null, precioVenta: null,
  })
  // El precio que fijó ($25.000) coincide con el de hoy: igual se manda $25.000, no "no tocar".
  assert.equal(lineaParaCorregir(pastillas).precioVenta, 25000)
})

test('un unitario con decimales intacto viaja con sus decimales, no redondeado', () => {
  const conDecimales = { ...detalle, renglones: [{ ...detalle.renglones[1], costoUnitario: 14200.4567 }] }
  const [r] = renglonesDesdeDetalle(conDecimales, renglonVacio)
  assert.equal(r.costoUnitario, '14200', 'el campo muestra dígitos')
  assert.equal(lineaParaCorregir(r).costoUnitario, 14200.4567, 'lo que viaja es el exacto')
})

test('cambiar la cantidad deja de ser la misma entrada y manda lo escrito', () => {
  const [filtro] = renglonesDesdeDetalle(detalle, renglonVacio)
  const corregido = { ...filtro, cantidad: '10', costoTotal: '133333' }
  assert.equal(mismaEntrada(corregido), false)
  assert.equal(lineaParaCorregir(corregido).cantidad, 10)
  assert.equal(lineaParaCorregir(corregido).costoTotal, 133333)
})

test('precio: dejarlo como venía es no cambiarlo; escribir otro es cambiarlo', () => {
  const [filtro, pastillas] = renglonesDesdeDetalle(detalle, renglonVacio)
  assert.equal(precioParaCorreccion(filtro), null, 'no había fijado precio y sigue igual')
  assert.equal(precioParaCorreccion({ ...filtro, precioVenta: '11000' }), 11000)
  assert.equal(precioParaCorreccion({ ...pastillas, precioVenta: '' }), 25000, 'vacío no borra lo que fijó')
})

test('un renglón nuevo o con otro repuesto compara el precio contra el de hoy', () => {
  const [filtro] = renglonesDesdeDetalle(detalle, renglonVacio)
  const otroRepuesto = { ...filtro, repuesto: { id: 'v9', precio: 5000 }, precioVenta: '5000' }
  assert.equal(precioParaCorreccion(otroRepuesto), null)
  assert.equal(precioParaCorreccion({ ...otroRepuesto, precioVenta: '6000' }), 6000)
})

test('totalDeLinea redondea el unitario al peso, como el backend', () => {
  assert.equal(totalDeLinea({ modo: 'TOTAL', costoTotal: 200000, cantidad: 15 }), 200000)
  assert.equal(totalDeLinea({ modo: 'UNITARIO', costoUnitario: 13333.3333, cantidad: 15 }), 200000)
})

test('sin tocar nada, la foto del formulario es la del detalle: no hay cambios', () => {
  const renglones = renglonesDesdeDetalle(detalle, renglonVacio)
  const despues = fotografiaDelFormulario({
    estado: 'VIGENTE', proveedor: 'Jotapartes', fechaDocumento: '2026-09-01', numeroFactura: 'FV-1',
    formaPago: 'EFECTIVO', cuenta: null, renglones,
  })
  assert.equal(desgloseDeCambios(fotografiaDelDetalle(detalle), despues).vacio, true)
})

test('las fotos dicen en palabras qué cambió', () => {
  const [filtro, pastillas] = renglonesDesdeDetalle(detalle, renglonVacio)
  const despues = fotografiaDelFormulario({
    estado: 'VIGENTE', proveedor: 'Jotapartes', fechaDocumento: '2026-09-01', numeroFactura: 'FV-1',
    formaPago: 'TRANSFERENCIA', cuenta: 'Nequi del dueño',
    renglones: [{ ...filtro, cantidad: '10', costoTotal: '133333' }],   // pastillas se quitó
  })
  const d = desgloseDeCambios(fotografiaDelDetalle(detalle), despues)

  assert.deepEqual(d.factura, [{ etiqueta: 'Pago', antes: 'Efectivo', despues: 'Transferencia · Nequi del dueño' }])
  assert.deepEqual(d.renglones.map((g) => [g.codigo, g.tipo]), [['352B59K', 'cambiado'], ['152RTX2B', 'quitado']])
  assert.deepEqual(d.renglones[0].filas[0], { etiqueta: 'Cantidad', antes: '15', despues: '10' })
  assert.ok(d.total)
  assert.equal(pastillas.lineaId, 'l2')
})
