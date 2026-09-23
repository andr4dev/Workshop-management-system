/**
 * Corregir una compra desde la misma pantalla de registrar (spec 0002, RF-014).
 *
 * La pantalla trabaja con renglones "de formulario" (textos de los campos). Aquí se traducen en los
 * dos sentidos: del detalle guardado a renglones editables, y de los renglones a lo que se manda.
 *
 * La regla que más cuida este archivo: **un renglón que nadie tocó se manda exactamente como estaba**.
 * Si se mandara redondeado o con el precio de hoy en vez del que fijó, el backend lo vería cambiado:
 * revertiría su entrada sin razón, o devolvería un precio que nadie quiso cambiar.
 */
import { codigoDelRenglon, precioParaEnviar } from './compra.js'
import { soloDigitos } from './formato.js'

/** Los renglones vigentes del detalle, listos para editar, cada uno con su `original`. */
export function renglonesDesdeDetalle(detalle, renglonVacio) {
  return detalle.renglones.map((r) => ({
    ...renglonVacio(),
    lineaId: r.lineaId,
    codigo: r.codigo,
    // Ya consultado: salir del campo no vuelve a buscarlo.
    codigoBuscado: r.codigo,
    repuesto: {
      id: r.varianteId,
      codigo: r.codigo,
      nombre: r.nombre,
      marca: r.marca,
      precio: r.precioActual,
      stock: r.stockActual,
      costoPromedio: r.costoPromedioActual,
    },
    cantidad: String(r.cantidad),
    modo: r.modoCaptura,
    costoTotal: r.modoCaptura === 'TOTAL' ? String(r.costoTotal) : '',
    // El campo solo acepta dígitos; el valor exacto queda en `original`.
    costoUnitario: r.modoCaptura === 'UNITARIO' ? String(Math.round(Number(r.costoUnitario))) : '',
    precioVenta: String(r.precioVenta ?? r.precioActual),
    original: {
      varianteId: r.varianteId,
      cantidad: r.cantidad,
      modo: r.modoCaptura,
      costoTotal: r.costoTotal,
      costoUnitario: Number(r.costoUnitario),
      precioVenta: r.precioVenta,
    },
  }))
}

/** ¿El renglón mete al inventario lo mismo que el original? El precio no cuenta. */
export function mismaEntrada(r) {
  const o = r.original
  if (!o || r.repuestoNuevo || r.repuesto?.id !== o.varianteId) return false
  if (Number(r.cantidad) !== Number(o.cantidad) || r.modo !== o.modo) return false
  return r.modo === 'TOTAL'
    ? Number(soloDigitos(r.costoTotal)) === Number(o.costoTotal)
    : Number(soloDigitos(r.costoUnitario)) === Math.round(Number(o.costoUnitario))
}

/**
 * El precio que se manda en una corrección.
 *
 * Contra el precio **del renglón**, no contra el de hoy. Un renglón que fijó $25.000 llega con
 * "$25.000" escrito; si se comparara contra el precio de hoy y coincidiera, se mandaría "no tocar",
 * y el backend entendería que se le quitó el precio al renglón — y lo devolvería al de antes.
 */
export function precioParaCorreccion(r) {
  if (r.repuestoNuevo) return null   // el precio de un repuesto nuevo va en sus datos de creación
  const o = r.original
  if (!o || r.repuesto?.id !== o.varianteId) {
    return precioParaEnviar(r.precioVenta, r.repuesto?.precio)
  }
  const escrito = Number(soloDigitos(r.precioVenta)) || null
  const base = o.precioVenta ?? r.repuesto?.precio
  if (escrito == null || escrito === Number(base)) return o.precioVenta ?? null
  return escrito
}

/** Un renglón tal como lo espera `POST /api/compras/{id}/correcciones`. */
export function lineaParaCorregir(r) {
  const o = r.original
  const esNuevo = r.repuestoNuevo != null
  const intacto = mismaEntrada(r)
  // `nombreConcepto` es solo para pintar el renglón; el backend no lo espera.
  const { nombreConcepto, ...nuevo } = r.repuestoNuevo ?? {}

  return {
    lineaId: r.lineaId ?? null,
    varianteId: esNuevo ? null : r.repuesto.id,
    repuestoNuevo: esNuevo ? { ...nuevo, precio: Number(soloDigitos(r.precioVenta)) } : null,
    cantidad: Number(r.cantidad),
    modo: r.modo,
    costoTotal: r.modo !== 'TOTAL' ? null : intacto ? o.costoTotal : Number(soloDigitos(r.costoTotal)),
    // Intacto: el unitario exacto, con sus decimales. Si no, lo que se escribió.
    costoUnitario: r.modo !== 'UNITARIO' ? null
      : intacto ? o.costoUnitario : Number(soloDigitos(r.costoUnitario)),
    precioVenta: esNuevo ? null : precioParaCorreccion(r),
  }
}

/** Lo que se pagó por un renglón que se va a mandar, en pesos enteros, como lo calcula el backend. */
export function totalDeLinea(linea) {
  return linea.modo === 'TOTAL'
    ? Number(linea.costoTotal)
    : Math.round(Number(linea.costoUnitario) * Number(linea.cantidad))
}

/** El detalle guardado con la forma de la foto de auditoría (ver `utils/auditoria.js`). */
export function fotografiaDelDetalle(detalle) {
  return {
    estado: detalle.estado,
    proveedor: detalle.proveedor,
    fechaDocumento: detalle.fechaDocumento,
    numeroFactura: detalle.numeroFactura ?? null,
    formaPago: detalle.formaPago,
    cuenta: detalle.cuenta ?? null,
    pagadaDeCaja: Boolean(detalle.pagadaDeCaja),
    total: detalle.total,
    renglones: detalle.renglones.map((r) => ({
      codigo: r.codigo,
      repuesto: `${r.nombre} ${r.marca}`,
      cantidad: r.cantidad,
      modo: r.modoCaptura,
      costoUnitario: r.costoUnitario,
      costoTotal: r.costoTotal,
      precioVenta: r.precioVenta ?? null,
    })),
  }
}

/** Cómo va a quedar la compra si se guarda lo que hay en pantalla, con la misma forma. */
export function fotografiaDelFormulario({ estado, proveedor, fechaDocumento, numeroFactura, formaPago,
  cuenta, pagadaDeCaja = false, renglones }) {
  const lineas = renglones.map((r) => ({ renglon: r, linea: lineaParaCorregir(r) }))
  const fotos = lineas.map(({ renglon, linea }) => ({
    codigo: codigoDelRenglon(renglon),
    repuesto: renglon.repuesto ? `${renglon.repuesto.nombre} ${renglon.repuesto.marca}` : null,
    cantidad: linea.cantidad,
    modo: linea.modo,
    costoUnitario: linea.costoUnitario,
    costoTotal: totalDeLinea(linea),
    precioVenta: linea.precioVenta ?? null,
  }))
  return {
    estado,
    proveedor,
    fechaDocumento,
    numeroFactura: numeroFactura?.trim() || null,
    formaPago,
    cuenta: formaPago === 'TRANSFERENCIA' ? (cuenta ?? null) : null,
    pagadaDeCaja: formaPago === 'EFECTIVO' && Boolean(pagadaDeCaja),
    total: fotos.reduce((suma, f) => suma + f.costoTotal, 0),
    renglones: fotos,
  }
}
