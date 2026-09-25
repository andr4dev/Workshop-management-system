/**
 * La pre-carga de una factura (spec 0012): los textos de la ganancia, los filtros, el cuadre con la factura y el
 * resumen antes de confirmar. Lo que decide si algo es un problema lo dice el servidor; aquí solo se muestra.
 */
import { formatoCOP } from './formato.js'

/** Lo mismo que el servidor: más de esto no es una factura, es un archivo equivocado. */
export const TAMANO_MAXIMO = 5 * 1024 * 1024

export const POR_PAGINA = 50

/**
 * Lo que se gana en una unidad, con los DOS porcentajes y un nombre distinto para cada uno (decisión 2).
 *
 * El 45% que pone el dueño es sobre lo que pagó. El "margen" que el sistema ya muestra en compras y en la ficha del
 * repuesto es sobre el precio de venta, y con la bujía da 31%. Si los dos se llamaran "ganancia", alguien vería 31%
 * donde puso 45% y creería que el sistema calcula mal.
 *
 * @return `null` si falta el costo o el precio
 */
export function gananciaDe(costoPorUnidad, precio) {
  const costo = Number(costoPorUnidad)
  const venta = Number(precio)
  if (costoPorUnidad == null || precio == null || !(costo > 0) || !(venta > 0)) return null
  const pesos = venta - costo
  return {
    pesos,
    sobreLoPagado: Math.round((pesos / costo) * 100),
    delPrecio: Math.round((pesos / venta) * 100),
    aPerdida: pesos < 0,
  }
}

/** "Le ganas 45% a lo que pagaste · 31% del precio es ganancia", o cuánto se pierde. */
export function textoDeGanancia(costoPorUnidad, precio) {
  const g = gananciaDe(costoPorUnidad, precio)
  if (!g) return ''
  if (g.aPerdida) return `Vendes a pérdida: ${formatoCOP(Math.ceil(-g.pesos))} por unidad`
  return `Le ganas ${g.sobreLoPagado}% a lo que pagaste · ${g.delPrecio}% del precio es ganancia`
}

/** Sin mayúsculas ni tildes, como busca el resto del sistema. */
const normalizar = (texto) => String(texto ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

export const FILTROS = [
  { id: 'TODOS', nombre: 'Todos' },
  { id: 'PROBLEMAS', nombre: 'Con problema' },
  { id: 'PROPUESTOS', nombre: 'Propuestos' },
  { id: 'REPOSICIONES', nombre: 'Ya existían' },
  { id: 'AJUSTADOS', nombre: 'Ajustados a mano' },
  { id: 'QUITADOS', nombre: 'Quitados' },
]

const tieneProblema = (r) => !r.quitado && (r.problemas?.length ?? 0) > 0
/** La marca o la categoría las puso el sistema y nadie las ha mirado. En una reposición no cuentan: no se usan. */
export const esPropuesto = (r) => !r.quitado && !r.existente && (r.marcaPropuesta || r.categoriaPropuesta)

const CUMPLE = {
  TODOS: (r) => !r.quitado,
  PROBLEMAS: tieneProblema,
  PROPUESTOS: esPropuesto,
  REPOSICIONES: (r) => !r.quitado && r.existente != null,
  AJUSTADOS: (r) => !r.quitado && r.ajustadoAMano,
  QUITADOS: (r) => r.quitado,
}

/** Los renglones del filtro que contienen el texto en su código, descripción o marca. */
export function filtrar(renglones, filtro = 'TODOS', texto = '') {
  const cumple = CUMPLE[filtro] ?? CUMPLE.TODOS
  const aguja = normalizar(texto.trim())
  return (renglones ?? []).filter((r) => cumple(r) && (!aguja
    || normalizar(r.codigo).includes(aguja)
    || normalizar(r.descripcion).includes(aguja)
    || normalizar(r.marca).includes(aguja)))
}

/** Cuántos hay en cada filtro, para ponerlo en su botón. */
export function conteos(renglones) {
  return Object.fromEntries(FILTROS.map((f) => [f.id, filtrar(renglones, f.id).length]))
}

/** Una página de la lista, empezando en 0. En el celular, 600 tarjetas de una vez no se pueden recorrer. */
export function pagina(lista, numero, tamano = POR_PAGINA) {
  const paginas = Math.max(1, Math.ceil(lista.length / tamano))
  const actual = Math.min(Math.max(0, numero), paginas - 1)
  return { renglones: lista.slice(actual * tamano, (actual + 1) * tamano), actual, paginas }
}

/**
 * Si lo leído cuadra con la factura (RF-004): la suma de los valores totales contra el sub-total impreso.
 *
 * @return `{ estado: 'CUADRA' | 'NO_CUADRA' | 'FALTA_SUBTOTAL', texto }`
 */
export function cuadre(totales) {
  if (!totales || totales.subtotalFactura == null) {
    return { estado: 'FALTA_SUBTOTAL', texto: 'Escribe el sub-total de la factura (el de abajo, antes del IVA) para comprobar que no falte nada' }
  }
  if (totales.diferencia === 0) {
    return { estado: 'CUADRA', texto: `Cuadra: lo leído suma ${formatoCOP(totales.sumaLeida)}, igual que la factura` }
  }
  const faltan = totales.diferencia > 0
  return {
    estado: 'NO_CUADRA',
    texto: `${faltan ? 'Faltan' : 'Sobran'} ${formatoCOP(Math.abs(totales.diferencia))}: lo leído suma `
      + `${formatoCOP(totales.sumaLeida)} y la factura dice ${formatoCOP(totales.subtotalFactura)}`,
  }
}

const plural = (n, uno, varios) => `${n.toLocaleString('es-CO')} ${n === 1 ? uno : varios}`

/**
 * Lo que se lee antes del último clic: "592 repuestos, 2.269 unidades, compra por $17.528.132", y lo que conviene
 * saber antes de confirmar.
 */
export function resumenParaConfirmar(carga) {
  const t = carga.totales
  const nuevos = t.incluidos - t.reposiciones
  const lineas = [
    `${plural(t.incluidos, 'repuesto', 'repuestos')}, ${plural(t.unidades, 'unidad', 'unidades')}, compra por ${formatoCOP(t.totalConIva)}`,
    `${plural(nuevos, 'nuevo', 'nuevos')} y ${plural(t.reposiciones, 'que ya existía y suma stock', 'que ya existían y suman stock')}`,
  ]
  if (t.propuestasSinRevisar > 0) {
    lineas.push(`${plural(t.propuestasSinRevisar, 'renglón tiene', 'renglones tienen')} la marca o la categoría que propuso el sistema, sin revisar`)
  }
  if (t.quitados > 0) {
    lineas.push(`${plural(t.quitados, 'renglón quitado no entra', 'renglones quitados no entran')}`)
  }
  const aPerdida = carga.renglones.filter((r) => !r.quitado && r.avisos?.length).length
  if (aPerdida > 0) lineas.push(`${plural(aPerdida, 'queda', 'quedan')} a pérdida`)
  return lineas
}

/** Antes de subirlo: lo que se sabe sin preguntarle al servidor. `null` si se puede subir. */
export function problemaDelArchivo(archivo) {
  if (!archivo) return 'Elige el archivo de la factura'
  const nombre = archivo.name?.toLowerCase() ?? ''
  if (nombre.endsWith('.xls')) {
    return 'Ese es un Excel de los viejos (.xls). Ábrelo en Excel y guárdalo como .xlsx o como .csv, y súbelo otra vez.'
  }
  if (!/\.(pdf|xlsx|csv|txt)$/.test(nombre)) return 'Sube la factura en PDF, un Excel (.xlsx) o un .csv'
  if (archivo.size > TAMANO_MAXIMO) return 'El archivo pesa más de 5 MB: una factura no pesa tanto. Revisa que sea el archivo correcto.'
  if (archivo.size === 0) return 'El archivo está vacío'
  return null
}

/** Un precio escrito ("$68.500", "68500") como número entero, o `null` si no hay. */
export function precioEscrito(texto) {
  const digitos = String(texto ?? '').replace(/\D/g, '')
  return digitos ? Number(digitos) : null
}

/** Un porcentaje escrito ("19", "19,5") como número, o `null` si no se entiende. */
export function porcentajeEscrito(texto) {
  const limpio = String(texto ?? '').trim().replace('%', '').replace(',', '.')
  if (limpio === '' || !/^\d+(\.\d+)?$/.test(limpio)) return null
  return Number(limpio)
}
