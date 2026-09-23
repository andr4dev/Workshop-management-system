/**
 * Formato de cifras. Un solo sitio, porque que dos pantallas muestren el mismo monto distinto
 * es de las cosas que más rápido destruyen la confianza en un sistema de plata.
 */

const COP = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  minimumFractionDigits: 0,
  maximumFractionDigits: 0,
})

/** El peso no tiene centavos. Nunca se muestran decimales en un monto. */
export const formatoCOP = (pesos) => COP.format(pesos ?? 0)

/**
 * Costo unitario, que SÍ tiene decimales — es lo que el usuario quiere ver cuando compra por
 * lote: "me sale a $13.333,33 c/u". Ocultarlos escondería de dónde salió la cifra.
 */
export function formatoCosto(valor) {
  if (valor == null) return GUION
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(valor)
}

/**
 * Lo que se pinta cuando un dato NO SE CONOCE.
 *
 * Nunca `$0`. Un cero inventado se lee como un hecho —"este repuesto no cuesta nada"— y haría
 * que el reporte muestre 100% de margen. Un guion dice la verdad: no lo sabemos todavía.
 */
export const GUION = '—'

/** Margen por unidad. `null` si falta alguno de los dos lados. */
export function margen(costo, precio) {
  if (costo == null || precio == null || Number(costo) <= 0) return null
  const utilidad = Number(precio) - Number(costo)
  return { utilidad, porcentaje: (utilidad / Number(precio)) * 100 }
}

/** "12 sept 2026, 4:28 p. m." — un instante (con hora), en la hora de este equipo. */
export function fechaHora(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString('es-CO', {
    day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit',
  })
}

/** "1 sept 2026" — una fecha que ya viene como `Date` local (ver `fechaLocal`). */
export function fechaDia(fecha) {
  if (!fecha) return ''
  return fecha.toLocaleDateString('es-CO', { day: 'numeric', month: 'short', year: 'numeric' })
}

/** Quita todo lo que no sea dígito. Los campos de dinero aceptan "18.000" o "$18.000". */
export const soloDigitos = (texto) => String(texto ?? '').replace(/\D/g, '')

/**
 * Id local, único dentro de esta sesión. Solo para claves de React y estado en memoria —
 * los ids de verdad los genera el backend.
 *
 * NO usa `crypto.randomUUID()` a secas: esa función **solo existe en contexto seguro**
 * (https o localhost). La tablet del pasillo entra por `http://192.168.x.x:5174`, que NO lo es,
 * y ahí `crypto.randomUUID` es `undefined` — la pantalla reventaría al abrir, justo en el
 * dispositivo para el que se diseñó. Se probó en el portátil, donde localhost sí es seguro,
 * y por eso no se vio.
 */
let contador = 0
export function idLocal() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  contador += 1
  return `local-${Date.now().toString(36)}-${contador}`
}
