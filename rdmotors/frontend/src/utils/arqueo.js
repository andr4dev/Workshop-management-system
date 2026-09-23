/**
 * El arqueo del turno (spec 0006, fases 3 y 5), sin JSX, para probarlo con `node --test`.
 *
 * Las cifras las calcula el servidor: en vivo para el turno abierto (`turno.arqueo`) y guardadas al cerrar
 * (`turno.cierre`). Aquí solo se ordenan para leerlas, y se comprueba que las partes sumen: si no suman, se dice
 * en rojo, nunca se "arregla" una cifra.
 */
import { formatoCOP, soloDigitos } from './formato.js'

/** Lo contado, escrito en pantalla. `null` si no se escribió nada: vacío no es $0. */
export function contadoDesdeTexto(texto) {
  const digitos = soloDigitos(texto)
  return digitos === '' ? null : Number(digitos)
}

export function problemaDelContado(texto) {
  const contado = contadoDesdeTexto(texto)
  if (contado == null) return 'Escribe cuánto contaste, aunque sea $0'
  if (!Number.isSafeInteger(contado)) return 'Ese monto no es válido'
  return null
}

const PARTES = [
  { clave: 'ventasEfectivo', etiqueta: 'Ventas en efectivo', signo: 1 },
  // Los abonos de clientes en efectivo entran al cajón como una venta más (spec 0008, RF-013).
  { clave: 'abonosEfectivo', etiqueta: 'Abonos de clientes', signo: 1 },
  { clave: 'devolucionesEfectivo', etiqueta: 'Devuelto por ventas anuladas', signo: -1 },
  { clave: 'gastosCajon', etiqueta: 'Gastos pagados del cajón', signo: -1 },
  { clave: 'retiros', etiqueta: 'Retiros', signo: -1 },
  { clave: 'comprasCajon', etiqueta: 'Compras pagadas del cajón', signo: -1 },
]

/**
 * De dónde sale lo que debería haber (RF-011), fila por fila: el fondo y cada parte con su signo. Las partes
 * en $0 no salen, para que se lea lo que pasó; el fondo sale siempre. Leídas de arriba abajo suman
 * `esperado`.
 *
 * @param turno el turno como lo devuelve `GET /api/turnos/{id}`: abierto, con `arqueo` en vivo; cerrado, con
 *              `cierre`
 */
export function filasDelDesglose(turno) {
  const partes = turno.cierre ?? turno.arqueo
  if (!partes) return []
  // `?? 0`: una parte que el servidor no mandó (un cierre de antes de los abonos) no es una fila con NaN.
  return [
    { clave: 'fondo', etiqueta: 'Fondo del turno', signo: 1, monto: turno.fondo },
    ...PARTES.filter((p) => (partes[p.clave] ?? 0) !== 0).map((p) => ({ ...p, monto: partes[p.clave] ?? 0 })),
  ]
}

export const sumaDelDesglose = (filas) => filas.reduce((suma, f) => suma + f.signo * f.monto, 0)

/** "Cuadra al peso", "Sobran $ 500" o "Faltan $ 1.400". */
export function diferenciaEnPalabras(diferencia) {
  if (diferencia === 0) return { tipo: 'CUADRA', texto: 'Cuadra al peso' }
  if (diferencia > 0) return { tipo: 'SOBRANTE', texto: `Sobran ${formatoCOP(diferencia)}` }
  return { tipo: 'FALTANTE', texto: `Faltan ${formatoCOP(-diferencia)}` }
}

/**
 * Si las cifras de un cierre no cuadran, por qué. La base ya lo exige con un CHECK, así que una lista con algo
 * es un error de programa: se muestra en rojo. Vacía si cuadra.
 */
export function problemasDelArqueo(turno) {
  const partes = turno.cierre ?? turno.arqueo
  if (!partes) return []
  const problemas = []
  const suma = sumaDelDesglose(filasDelDesglose(turno))
  if (suma !== partes.esperado) {
    problemas.push(`El desglose suma ${formatoCOP(suma)} y lo que debería haber es ${formatoCOP(partes.esperado)}`)
  }
  const cierre = turno.cierre
  if (cierre && cierre.contado - cierre.esperado !== cierre.diferencia) {
    problemas.push(`Se contaron ${formatoCOP(cierre.contado)} y debería haber ${formatoCOP(cierre.esperado)}: `
      + `la diferencia no es ${formatoCOP(cierre.diferencia)}`)
  }
  return problemas
}

/** Un cierre con diferencia y sin explicación: el historial lo marca (spec 0006, §6). */
export const faltaExplicacion = (turno) =>
  turno.estado === 'CERRADO' && turno.diferencia !== 0 && turno.diferencia != null && !turno.observaciones

// ── Contar por billetes (P3, RF-021) ────────────────────────────────────────

export const BILLETES = [100_000, 50_000, 20_000, 10_000, 5_000, 2_000]
export const MONEDAS = [1_000, 500, 200, 100, 50]

/**
 * El total de un conteo por denominaciones: `{ 50000: '3', 1000: '12' }` → $162.000. Una cantidad que no es un
 * número entero no suma y se dice cuál.
 */
export function totalPorDenominaciones(conteo) {
  let total = 0
  const problemas = []
  for (const valor of [...BILLETES, ...MONEDAS]) {
    const escrito = String(conteo?.[valor] ?? '').trim()
    if (escrito === '') continue
    if (!/^\d{1,6}$/.test(escrito)) {
      problemas.push(`La cantidad de ${formatoCOP(valor)} no es válida`)
      continue
    }
    total += Number(escrito) * valor
  }
  return { total, problemas }
}
