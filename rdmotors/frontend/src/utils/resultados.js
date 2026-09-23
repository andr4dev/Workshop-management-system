/**
 * Lo que la pantalla de resultados hace con la respuesta del servidor (spec 0007).
 *
 * **La pantalla no calcula plata.** Las cifras, el día por día y las categorías vienen hechos del servidor, de un
 * solo cálculo. Aquí solo se ordenan para mostrarlos, y se comprueba que cuadren: si un desglose no suma su cifra,
 * la pantalla lo dice en rojo en vez de corregirlo (RF-012). Una memoria de cálculo que miente es peor que no
 * tenerla, porque se le cree.
 */

/** La consulta al servidor. */
export function consultaDeResultados({ desde, hasta, gastosDelMes }) {
  return { desde, hasta, gastosDelMes: gastosDelMes ?? 'REPARTIDOS' }
}

const parte = (etiqueta, monto, signo, extra = {}) => ({ tipo: 'PARTE', etiqueta, monto, signo, ...extra })
const subtotal = (etiqueta, monto) => ({ tipo: 'SUBTOTAL', etiqueta, monto })

const hijosDe = (categorias) => categorias.map((c) => ({ etiqueta: c.categoria, monto: c.monto, categoriaId: c.categoriaId }))

/**
 * Recorre las filas y comprueba cada igualdad: cada subtotal es lo que suman las partes de arriba, y los hijos de
 * una parte suman la parte. Devuelve las filas con `cuadra` en cada subtotal y en cada parte con hijos.
 */
function cerrar(titulo, pregunta, filas) {
  let acumulado = 0
  const revisadas = filas.map((fila) => {
    if (fila.tipo === 'SUBTOTAL') {
      const cuadra = acumulado === fila.monto
      const revisada = { ...fila, calculado: acumulado, cuadra }
      acumulado = fila.monto
      return revisada
    }
    acumulado += fila.signo === '−' ? -fila.monto : fila.monto
    if (!fila.hijos) return fila
    const sumaHijos = fila.hijos.reduce((s, h) => s + h.monto, 0)
    return { ...fila, sumaHijos, cuadra: sumaHijos === fila.monto }
  })
  const cuadra = revisadas.every((f) => f.cuadra !== false)
  return { titulo, pregunta, filas: revisadas, cuadra }
}

export const CIFRAS = {
  VENTAS_NETAS: 'VENTAS_NETAS',
  UTILIDAD_BRUTA: 'UTILIDAD_BRUTA',
  GASTOS: 'GASTOS',
  UTILIDAD_OPERATIVA: 'UTILIDAD_OPERATIVA',
}

/**
 * La memoria de cálculo de una de las cuatro cifras grandes (RF-012): el desglose del §2 del spec, con cada categoría
 * de costo y de gasto.
 */
export function calculoDe(cifra, r) {
  const c = r.cifras
  const ventasNetas = [
    parte('Renglones vendidos', c.renglones, '+'),
    parte('Descuentos dados', c.descuentos, '−'),
    subtotal('Ventas netas', c.ventasNetas),
  ]
  const costos = [
    parte('Costo de los repuestos vendidos', c.costoVendido, '−', { sinCosto: r.sinCosto.renglones }),
    parte('Costos adicionales', c.costosAdicionales, '−', { hijos: hijosDe(r.costosPorCategoria) }),
    subtotal('Utilidad bruta', c.utilidadBruta),
  ]
  const gastos = parte('Gastos del local', c.gastos, '−', { hijos: hijosDe(r.gastosPorCategoria) })

  switch (cifra) {
    case CIFRAS.VENTAS_NETAS:
      return cerrar('Ventas netas', '¿Qué se vendió, sin lo que se regaló?', ventasNetas)
    case CIFRAS.UTILIDAD_BRUTA:
      return cerrar('Utilidad bruta', '¿Cuánto dejó la mercancía?', [
        parte('Ventas netas', c.ventasNetas, '+'), ...costos])
    case CIFRAS.GASTOS:
      return cerrar('Gastos del local', '¿Qué costó tener la tienda abierta?', [
        { ...gastos, signo: '+' }, subtotal('Gastos del local', c.gastos)])
    case CIFRAS.UTILIDAD_OPERATIVA:
    default:
      return cerrar('Utilidad operativa', '¿Cuánto se ganó?', [
        ...ventasNetas, ...costos, gastos, subtotal('Utilidad operativa', c.utilidadOperativa)])
  }
}

/** Efectivo + transferencia + fiado = ventas netas (RF-014; spec 0008): las mismas ventas. */
export function pagosCuadran(c) {
  return c.efectivo + c.transferencia + (c.fiado ?? 0) === c.ventasNetas
}

/**
 * Las filas del día por día, más la de gastos del mes si viene, suman las cifras (RF-017). Devuelve los nombres de
 * las columnas que no cuadran; vacío si todo cuadra.
 */
export function columnasQueNoCuadran(r) {
  const filas = r.filaGastosDelMes ? [...r.filas, r.filaGastosDelMes] : r.filas
  const columnas = ['ventas', 'ventasNetas', 'costoVendido', 'costosAdicionales', 'utilidadBruta', 'gastos',
    'utilidadOperativa']
  return columnas.filter((col) => filas.reduce((s, f) => s + f[col], 0) !== r.cifras[col])
}

/** "34,4 % de las ventas", o `null` sin ventas: un margen sin ventas no es 0 % (RF-011). */
export function textoDeMargen(margen) {
  if (margen == null) return null
  return `${conComa(margen)} % de las ventas`
}

/** "3 renglones sin costo: la utilidad está sobrestimada", o `null` (RF-007). */
export function textoSinCosto(renglones) {
  if (!renglones) return null
  return `${renglones} ${renglones === 1 ? 'renglón' : 'renglones'} sin costo: la utilidad está sobrestimada`
}

/**
 * El aviso de cómo se leyeron los gastos del mes (RF-015), o `null` si no hay gastos del mes en el período.
 *
 * @param formato cómo se escribe un monto (el de la pantalla, para no traer aquí el formato de pesos)
 */
export function avisoDeGastosDelMes(r, formato) {
  const { incluidos, fuera } = r.gastosDelMes
  if (r.modoGastosDelMes === 'SOLO_EN_EL_MES') {
    if (fuera > 0) return `No incluye ${formato(fuera)} de gastos del mes: se ven en el reporte del mes.`
    if (incluidos > 0) return `Incluye ${formato(incluidos)} de gastos del mes enteros: el período cubre su mes.`
    return null
  }
  if (incluidos > 0) {
    return `El arriendo y los demás gastos del mes van repartidos día por día: este período carga ${formato(incluidos)}.`
  }
  return null
}

/** Si hay algo que mostrar. Con gastos y sin ventas SÍ lo hay: la ganancia negativa es el dato (§6). */
export function hayMovimientos(r) {
  const c = r.cifras
  return c.ventas > 0 || c.gastos !== 0 || c.costosAdicionales !== 0 || r.gastosDelMes.fuera > 0
}

/** Cuánto se regaló: los descuentos sobre los renglones, con un decimal. `null` sin renglones. */
export function porcentajeDeDescuentos(c) {
  if (!c.renglones) return null
  return Math.round((c.descuentos / c.renglones) * 1000) / 10
}

/** Un porcentaje con un decimal y coma: "12,5". */
export const conComa = (numero) =>
  Number(numero).toLocaleString('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

// ── P2 ───────────────────────────────────────────────────────────────────────

export const ORDENES_DE_REPUESTOS = [['UTILIDAD', 'Utilidad'], ['UNIDADES', 'Unidades'], ['VENTAS', 'Ventas netas']]

/**
 * Los primeros repuestos en el orden elegido (RF-019). Las cifras vienen hechas: aquí solo se ordena. Por utilidad,
 * los que no tienen (vendidos sin costo) van al final.
 */
export function rankingDeRepuestos(repuestos, orden, limite = 20) {
  const porVentas = (a, b) => b.ventasNetas - a.ventasNetas || a.nombre.localeCompare(b.nombre, 'es')
  const comparar = {
    UTILIDAD: (a, b) => (a.utilidad == null) - (b.utilidad == null) || (b.utilidad ?? 0) - (a.utilidad ?? 0) || porVentas(a, b),
    UNIDADES: (a, b) => b.unidades - a.unidades || porVentas(a, b),
    VENTAS: porVentas,
  }[orden] ?? porVentas
  return [...repuestos].sort(comparar).slice(0, limite)
}

/** Los vendidos por debajo del costo (RF-020), de la mayor pérdida a la menor. */
export function vendidosConPerdida(repuestos) {
  return repuestos.filter((r) => r.conPerdida).sort((a, b) => a.utilidad - b.utilidad)
}

/** Si los repuestos, o las categorías, suman las ventas netas del período (el descuento va repartido). */
export function sumanLasVentasNetas(vendidos, r) {
  return vendidos.reduce((s, v) => s + v.ventasNetas, 0) === r.cifras.ventasNetas
}

/** Qué parte de las ventas netas es un monto, con un decimal. `null` sin ventas netas. */
export function participacion(monto, total) {
  if (!total) return null
  return Math.round((monto / total) * 1000) / 10
}

/**
 * Cuánto subió o bajó una cifra frente al período anterior (RF-022).
 *
 * @return { diferencia, porcentaje, direccion: 'SUBE'|'BAJA'|'IGUAL' } — `porcentaje` es `null` si el anterior fue $0
 */
export function variacion(actual, anterior) {
  const diferencia = actual - anterior
  return {
    diferencia,
    porcentaje: anterior === 0 ? null : Math.round((diferencia / Math.abs(anterior)) * 1000) / 10,
    direccion: diferencia > 0 ? 'SUBE' : diferencia < 0 ? 'BAJA' : 'IGUAL',
  }
}

/** "▲ $120.000 (+12,5 %)", "▼ $80.000 (−6,1 %)", "Igual". */
export function textoDeVariacion({ diferencia, porcentaje, direccion }, formato) {
  if (direccion === 'IGUAL') return 'Igual'
  const flecha = direccion === 'SUBE' ? '▲' : '▼'
  const signo = direccion === 'SUBE' ? '+' : '−'
  const pct = porcentaje == null ? '' : ` (${signo}${conComa(Math.abs(porcentaje))} %)`
  return `${flecha} ${formato(Math.abs(diferencia))}${pct}`
}

/** "faltaron $1.400", "sobraron $500", "cuadró". */
export function textoDeDiferencia(diferencia, formato) {
  if (diferencia < 0) return `faltaron ${formato(-diferencia)}`
  if (diferencia > 0) return `sobraron ${formato(diferencia)}`
  return 'cuadró'
}

/** La dirección de Reportes › Gastos con las fechas del período y una categoría (RF-024). */
export function enlaceAGastos({ desde, hasta }, categoriaId) {
  return `/reportes/gastos?${new URLSearchParams({ desde, hasta, categoriaId })}`
}
