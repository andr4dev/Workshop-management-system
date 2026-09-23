/**
 * Los clientes (spec 0008), sin JSX, para probarlos con `node --test`.
 *
 * Qué es obligatorio y cómo se compara lo decide el servidor (`Cliente.java`); aquí se repiten sus mismos límites
 * para avisar mientras se escribe, antes de mandar. Si no coincidieran, manda el servidor.
 */
import { formatoCOP } from './formato.js'

/** Sin puntos, guiones ni espacios, en mayúsculas: "1.234.567-8" → "12345678". Como la base la compara. */
export const normalizarDocumento = (texto) => String(texto ?? '').replace(/[^\p{L}\p{N}]/gu, '').toUpperCase()

/** Solo los dígitos: "300 123 4567" → "3001234567". */
export const normalizarCelular = (texto) => String(texto ?? '').replace(/\D/g, '')

const DOCUMENTO_VALIDO = /^[A-Z0-9]{5,15}$/
const CELULAR_VALIDO = /^\d{7,15}$/

/** Los datos de un cliente para el formulario: nunca `null`, siempre texto. */
export function datosDelCliente(cliente) {
  return {
    nombre: cliente?.nombre ?? '',
    documento: cliente?.documento ?? '',
    celular: cliente?.celular ?? '',
    direccion: cliente?.direccion ?? '',
    nota: cliente?.nota ?? '',
  }
}

/**
 * Qué está mal en lo escrito, por campo. Vacío si se puede guardar.
 *
 * <p><b>Solo el nombre es obligatorio</b>, también para fiar (decisión 2, cambiada el 2026-09-21): la cédula y el
 * celular se piden, pero no frenan la venta. Lo que sí se revisa es que, si se escriben, tengan forma de cédula y
 * de celular: un dato mal escrito es peor que uno vacío, porque parece que está.
 */
export function problemasDeDatos(datos) {
  const problemas = {}
  if (!String(datos.nombre ?? '').trim()) problemas.nombre = 'Escribe el nombre del cliente'
  if (String(datos.documento ?? '').trim() && !DOCUMENTO_VALIDO.test(normalizarDocumento(datos.documento))) {
    problemas.documento = 'La cédula o NIT: de 5 a 15 números o letras'
  }
  if (String(datos.celular ?? '').trim() && !CELULAR_VALIDO.test(normalizarCelular(datos.celular))) {
    problemas.celular = 'El celular: de 7 a 15 dígitos'
  }
  return problemas
}

/**
 * Por qué a ese cliente no se le puede fiar, o `null` si sí. Lo único que lo impide es que el administrador le
 * haya cerrado el fiado; que le falten datos no (decisión 2, cambiada el 2026-09-21).
 */
export function porQueNoSeLeFia(cliente) {
  if (!cliente) return 'Escoge a quién se le fía'
  if (cliente.fiadoCerrado) return `A ${cliente.nombre} no se le fía: lo cerró el administrador. Puede pagar de contado`
  return null
}

/** Los campos vacíos que conviene completar: el cajero los llena sin ser administrador. No frenan la venta. */
export const camposQueFaltan = (cliente) => ['documento', 'celular'].filter((c) => !cliente?.[c])

/** El recordatorio, en palabras, de lo que le falta a ese cliente; `null` si no le falta nada. */
export function loQueFaltaEnPalabras(cliente) {
  const faltan = camposQueFaltan(cliente)
  if (faltan.length === 0) return null
  const nombres = { documento: 'la cédula o NIT', celular: 'el celular' }
  return faltan.map((c) => nombres[c]).join(' y ')
}

/** "1.234.567 · 300 123 4567": para reconocerlo en una lista. */
export const identificacion = (cliente) => [cliente?.documento, cliente?.celular].filter(Boolean).join(' · ')

/** "Debe $50.000" o "Al día". */
export const textoDeuda = (debe) => (debe > 0 ? `Debe ${formatoCOP(debe)}` : 'Al día')

/**
 * Lo que se escribió en el buscador sirve para arrancar un cliente nuevo: si son números, es la cédula; si no, el
 * nombre.
 */
export function clienteNuevoDesde(texto) {
  const limpio = String(texto ?? '').trim()
  const esNumero = /^[\d.\-\s]+$/.test(limpio) && /\d/.test(limpio)
  return { ...datosDelCliente(null), nombre: esNumero ? '' : limpio, documento: esNumero ? limpio : '' }
}
