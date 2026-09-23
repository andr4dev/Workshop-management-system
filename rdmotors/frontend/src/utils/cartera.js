/**
 * La Cartera (spec 0008), sin JSX, para probarla con `node --test`.
 *
 * Las cifras las calcula el servidor: lo que debe cada cliente, desde cuándo y el total por cobrar. Aquí solo se dicen
 * en palabras y se comprueba que las partes cuadren; si no cuadran, se dice, nunca se "arregla" una cifra.
 */
import { formatoCOP } from './formato.js'
import { diasEntre } from './periodo.js'

export const ESTADOS = { PENDIENTE: 'Pendiente', ABONADA: 'Abonada', PAGADA: 'Pagada', ANULADA: 'Anulada' }

export const FORMAS = { EFECTIVO: 'Efectivo', TRANSFERENCIA: 'Transferencia' }

export const VISTAS = [
  { valor: 'DEBEN', texto: 'Deben' },
  { valor: 'HISTORIAL', texto: 'Historial completo' },
]

/** Por qué fecha se filtra (RF-022): cuándo se fió, o cuándo abonó. */
export const MODOS_FECHA = [
  { valor: 'VENTA', texto: 'Fecha de la venta' },
  { valor: 'ABONO', texto: 'Fecha del abono' },
]

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sept', 'oct', 'nov', 'dic']

/** "12 sept 2026" desde "2026-09-12", sin pasar por la zona del equipo: es un día, no un instante. */
export function fechaCorta(texto) {
  if (!texto) return ''
  const [a, m, d] = texto.split('-').map(Number)
  return `${d} ${MESES[m - 1]} ${a}`
}

/** Cuántos días lleva debiendo: 0 si es de hoy. */
export const diasDebiendo = (desde, hoy) => (desde ? diasEntre(desde, hoy) - 1 : null)

/** "desde hoy", "desde ayer" o "desde el 12 sept 2026 · hace 9 días". `null` si está al día. */
export function desdeCuandoEnPalabras(desde, hoy) {
  const dias = diasDebiendo(desde, hoy)
  if (dias == null) return null
  if (dias <= 0) return 'desde hoy'
  if (dias === 1) return 'desde ayer'
  return `desde el ${fechaCorta(desde)} · hace ${dias} días`
}

/** La marca de deuda vieja (spec 0008, H13): lo que debe hace más de 30 días. */
export const DIAS_DEUDA_VIEJA = 30
export const esDeudaVieja = (desde, hoy) => (diasDebiendo(desde, hoy) ?? 0) > DIAS_DEUDA_VIEJA

export const textoPendientes = (n) => (n === 1 ? '1 venta pendiente' : `${n} ventas pendientes`)

/** Lo de arriba de la lista: "3 clientes deben · $180.000 por cobrar". */
export function resumenDeLaCartera({ deben, porCobrar, clientes }, vista) {
  if (vista === 'HISTORIAL') {
    const n = clientes.length
    return `${n} ${n === 1 ? 'cliente ha tenido' : 'clientes han tenido'} fiado · ${formatoCOP(porCobrar)} por cobrar`
  }
  if (deben === 0) return 'Nadie debe: todo al día'
  return `${deben} ${deben === 1 ? 'cliente debe' : 'clientes deben'} · ${formatoCOP(porCobrar)} por cobrar`
}

/** "Venta N.º 41" o "Saldo del cuaderno". */
export const nombreDeLaDeuda = (d) => (d.origen === 'CUADERNO' ? 'Saldo del cuaderno' : `Venta N.º ${d.numeroVenta}`)

/**
 * Si las cifras de la ficha no cuadran, por qué: cada deuda `abonado + pendiente = monto`, y lo que debe es la suma de
 * lo pendiente. El servidor ya lo cumple; una lista con algo es un error de programa y se muestra en rojo.
 */
export function problemasDeLaFicha(ficha) {
  const problemas = []
  let pendiente = 0
  for (const d of ficha.deudas) {
    if (d.estado !== 'ANULADA' && d.abonado + d.pendiente !== d.monto) {
      problemas.push(`${nombreDeLaDeuda(d)}: ${formatoCOP(d.abonado)} abonados y ${formatoCOP(d.pendiente)} pendientes no dan ${formatoCOP(d.monto)}`)
    }
    pendiente += d.pendiente
  }
  if (pendiente !== ficha.debe) {
    problemas.push(`Lo pendiente de sus ventas suma ${formatoCOP(pendiente)} y debe ${formatoCOP(ficha.debe)}`)
  }
  return problemas
}
