/**
 * Lógica de las pantallas de inventario y kardex. Sin JSX y sin formato de moneda: solo decide qué
 * mostrar, para poder probarlo con `node --test`.
 */

const NOMBRES_MOVIMIENTO = {
  COMPRA: 'Compra',
  VENTA: 'Venta',
  DEVOLUCION: 'Devolución',
  AJUSTE: 'Ajuste',
  REVERSION: 'Reversión',
  CORRECCION_COMPRA: 'Corrección',
  ANULACION_COMPRA: 'Anulación',
}

/** "Compra", "Venta"… Un tipo desconocido se muestra tal cual en vez de desaparecer. */
export const nombreMovimiento = (tipo) => NOMBRES_MOVIMIENTO[tipo] ?? tipo

/**
 * Cómo está el stock de un repuesto.
 *
 *   'agotado'  no queda ninguno: no se puede vender
 *   'bajo'     quedan, pero en o bajo su mínimo: hay que pedir
 *   'ok'       hay suficiente
 *
 * Agotado se separa de bajo porque piden cosas distintas: uno es "no le prometas al cliente", el
 * otro "anótalo en el próximo pedido".
 */
export function estadoStock(stock, stockMinimo) {
  if (stock <= 0) return 'agotado'
  if (stock <= stockMinimo) return 'bajo'
  return 'ok'
}

/**
 * Cantidad que entra y que sale de un movimiento. Una de las dos es siempre `null`: el kardex se
 * lee en dos columnas, como en papel, y una celda vacía se lee más rápido que un signo menos.
 */
export function entradaSalida(cantidad) {
  if (cantidad > 0) return { entrada: cantidad, salida: null }
  if (cantidad < 0) return { entrada: null, salida: -cantidad }
  return { entrada: null, salida: null }
}

/** "Importadora Jotapartes · FV-4521". `null` si el movimiento no viene de un documento conocido. */
export function documentoDe(movimiento) {
  if (!movimiento?.proveedor) return null
  return movimiento.factura
    ? `${movimiento.proveedor} · ${movimiento.factura}`
    : movimiento.proveedor
}

/**
 * Hacia dónde movió el costo promedio cada fila, comparando con la fila anterior en el tiempo.
 *
 * @param movimientos del MÁS RECIENTE al más antiguo, tal como los devuelve el backend.
 * @returns un arreglo paralelo con 'sube' | 'baja' | null. `null` en el primer movimiento, cuando
 *          no cambió, o cuando falta alguno de los dos promedios: sin dato no se inventa dirección.
 */
export function direccionesDelPromedio(movimientos) {
  const lista = movimientos ?? []
  return lista.map((m, i) => {
    const anterior = lista[i + 1]
    if (!anterior || m.costoPromedioDespues == null || anterior.costoPromedioDespues == null) {
      return null
    }
    const ahora = Number(m.costoPromedioDespues)
    const antes = Number(anterior.costoPromedioDespues)
    if (ahora > antes) return 'sube'
    if (ahora < antes) return 'baja'
    return null
  })
}

/**
 * Una fecha sin hora ("2026-09-01", la de la factura) como fecha LOCAL.
 *
 * `new Date('2026-09-01')` la toma como medianoche en UTC, que en Colombia (UTC−5) es el 31 de
 * agosto a las 7 p. m.: la factura aparecería con un día menos.
 */
export function fechaLocal(yyyyMmDd) {
  const [anio, mes, dia] = String(yyyyMmDd ?? '').split('-').map(Number)
  if (!anio || !mes || !dia) return null
  return new Date(anio, mes - 1, dia)
}

/** "1–25 de 132". Con cero resultados, "0 de 0" en vez de "1–0 de 0". */
export function rangoDePagina(numero, tamano, total) {
  if (!total) return '0 de 0'
  const desde = numero * tamano + 1
  const hasta = Math.min((numero + 1) * tamano, total)
  return `${desde}–${hasta} de ${total}`
}

/**
 * Lee la página de la URL. Lo que llega por la barra de direcciones puede ser cualquier cosa:
 * "-2", "abc", "1.5". Nunca revienta; lo que no sirve es la primera página.
 */
export function paginaDeLaUrl(valor) {
  const n = Number(valor)
  return Number.isInteger(n) && n > 0 ? n : 0
}
