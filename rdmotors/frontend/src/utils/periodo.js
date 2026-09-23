/**
 * El período de un reporte (spec 0007, RF-001 a RF-003): Hoy, Ayer, Esta semana, Semana pasada, Este mes, Mes
 * pasado o un rango, con flechas para el anterior y el siguiente.
 *
 * Las fechas van como texto `AAAA-MM-DD`, días de Colombia: así viajan al servidor y a la dirección. Se calcula en
 * UTC para que un cambio de horario del equipo no corra un día.
 *
 * Un período es `{ tipo, desde, hasta }`:
 *   DIA     un día
 *   SEMANA  de lunes a domingo; la semana en curso, de lunes a hoy
 *   MES     del 1 al último día; el mes en curso, del 1 a hoy
 *   RANGO   dos fechas cualesquiera, sin flechas
 *
 * En la dirección van las fechas, no "este mes": un enlace o volver atrás deja el mismo reporte.
 */

export const MAXIMO_DE_DIAS = 366

export const TIPOS = ['DIA', 'SEMANA', 'MES', 'RANGO']

export const MODOS_GASTOS_DEL_MES = ['REPARTIDOS', 'SOLO_EN_EL_MES']

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre',
  'noviembre', 'diciembre']
const MESES_CORTOS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sept', 'oct', 'nov', 'dic']
const DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado']
const DIAS_CORTOS = ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb']

const FECHA = /^\d{4}-\d{2}-\d{2}$/

/** `AAAA-MM-DD` → Date en UTC; `null` si no es una fecha de verdad (un 31 de febrero no lo es). */
function aDate(texto) {
  if (!FECHA.test(texto ?? '')) return null
  const [a, m, d] = texto.split('-').map(Number)
  const fecha = new Date(Date.UTC(a, m - 1, d))
  return fecha.getUTCMonth() === m - 1 && fecha.getUTCDate() === d ? fecha : null
}

const aTexto = (fecha) => fecha.toISOString().slice(0, 10)

export function esFecha(texto) {
  return aDate(texto) != null
}

export function sumarDias(texto, dias) {
  const fecha = aDate(texto)
  fecha.setUTCDate(fecha.getUTCDate() + dias)
  return aTexto(fecha)
}

/** Días entre dos fechas, los dos incluidos. */
export function diasEntre(desde, hasta) {
  return Math.round((aDate(hasta) - aDate(desde)) / 86_400_000) + 1
}

const menor = (a, b) => (a < b ? a : b)

function lunesDe(texto) {
  const dia = aDate(texto).getUTCDay()           // 0 domingo … 6 sábado
  return sumarDias(texto, dia === 0 ? -6 : 1 - dia)
}

const primeroDelMes = (texto) => `${texto.slice(0, 8)}01`

function ultimoDelMes(texto) {
  const [a, m] = texto.split('-').map(Number)
  return aTexto(new Date(Date.UTC(a, m, 0)))
}

/** El día, la semana o el mes que contiene la fecha, recortado a hoy. */
export function periodoQueContiene(tipo, fecha, hoy) {
  if (tipo === 'SEMANA') {
    const lunes = lunesDe(fecha)
    return { tipo, desde: lunes, hasta: menor(sumarDias(lunes, 6), hoy) }
  }
  if (tipo === 'MES') {
    return { tipo, desde: primeroDelMes(fecha), hasta: menor(ultimoDelMes(fecha), hoy) }
  }
  return { tipo: 'DIA', desde: fecha, hasta: fecha }
}

/** Los atajos, en el orden en que se muestran. */
export const ATAJOS = [
  ['HOY', 'Hoy'],
  ['AYER', 'Ayer'],
  ['ESTA_SEMANA', 'Esta semana'],
  ['SEMANA_PASADA', 'Semana pasada'],
  ['ESTE_MES', 'Este mes'],
  ['MES_PASADO', 'Mes pasado'],
]

export function periodoDelAtajo(atajo, hoy) {
  switch (atajo) {
    case 'HOY': return periodoQueContiene('DIA', hoy, hoy)
    case 'AYER': return periodoQueContiene('DIA', sumarDias(hoy, -1), hoy)
    case 'ESTA_SEMANA': return periodoQueContiene('SEMANA', hoy, hoy)
    case 'SEMANA_PASADA': return periodoQueContiene('SEMANA', sumarDias(hoy, -7), hoy)
    case 'ESTE_MES': return periodoQueContiene('MES', hoy, hoy)
    case 'MES_PASADO': return periodoQueContiene('MES', sumarDias(primeroDelMes(hoy), -1), hoy)
    default: return null
  }
}

/** Qué atajo es el período, si es alguno; así se marca aunque se haya llegado con las flechas. */
export function atajoDe(periodo, hoy) {
  return ATAJOS.map(([atajo]) => atajo).find((atajo) => {
    const p = periodoDelAtajo(atajo, hoy)
    return p.tipo === periodo.tipo && p.desde === periodo.desde && p.hasta === periodo.hasta
  }) ?? null
}

/** Sin nada en la dirección, el reporte abre en el mes en curso. */
export const periodoPorDefecto = (hoy) => periodoDelAtajo('ESTE_MES', hoy)

/**
 * Lo que dice la dirección: `?tipo=SEMANA&desde=2026-09-14` (el tipo y cualquier fecha adentro), o
 * `?tipo=RANGO&desde=…&hasta=…`, y `gastosDelMes`. Lo que no se entiende, se ignora.
 */
export function periodoDesdeUrl(params, hoy) {
  const tipo = params.get('tipo')
  const desde = params.get('desde') ?? ''
  const hasta = params.get('hasta') ?? ''
  const modo = params.get('gastosDelMes')
  const gastosDelMes = MODOS_GASTOS_DEL_MES.includes(modo) ? modo : 'REPARTIDOS'

  let periodo
  if (tipo === 'RANGO') {
    periodo = { tipo, desde, hasta }
  } else if (TIPOS.includes(tipo) && esFecha(desde) && desde <= hoy) {
    periodo = periodoQueContiene(tipo, desde, hoy)
  } else {
    periodo = periodoPorDefecto(hoy)
  }
  return { ...periodo, gastosDelMes }
}

/** Lo que va a la dirección. El rango lleva sus dos fechas aunque estén a medio escribir. */
export function urlDelPeriodo({ tipo, desde, hasta, gastosDelMes }) {
  const url = { tipo, desde }
  if (tipo === 'RANGO') url.hasta = hasta
  if (gastosDelMes && gastosDelMes !== 'REPARTIDOS') url.gastosDelMes = gastosDelMes
  return url
}

/** Por qué no se puede consultar, o `null`. Las mismas reglas que el servidor (RF-003). */
export function problemaDelPeriodo({ desde, hasta }, hoy) {
  if (!esFecha(desde) || !esFecha(hasta)) return 'Elige la fecha inicial y la final'
  if (desde > hasta) return 'La fecha inicial es posterior a la final'
  if (diasEntre(desde, hasta) > MAXIMO_DE_DIAS) return `Un período no puede pasar de ${MAXIMO_DE_DIAS} días`
  if (hasta > hoy) return 'El período no puede terminar después de hoy'
  return null
}

/** El período anterior del mismo tipo: el día, la semana o el mes de antes. Un rango no tiene. */
export function anterior(periodo, hoy) {
  if (periodo.tipo === 'RANGO') return null
  const antes = periodo.tipo === 'MES' ? sumarDias(periodo.desde, -1)
    : sumarDias(periodo.desde, periodo.tipo === 'SEMANA' ? -7 : -1)
  return periodoQueContiene(periodo.tipo, antes, hoy)
}

/** El siguiente del mismo tipo, o `null` si empezaría después de hoy. */
export function siguiente(periodo, hoy) {
  if (periodo.tipo === 'RANGO') return null
  const despues = periodo.tipo === 'MES' ? sumarDias(ultimoDelMes(periodo.desde), 1)
    : sumarDias(periodo.desde, periodo.tipo === 'SEMANA' ? 7 : 1)
  return despues > hoy ? null : periodoQueContiene(periodo.tipo, despues, hoy)
}

function diaYMes(texto, { corto = false, conAnio = false } = {}) {
  const f = aDate(texto)
  const mes = (corto ? MESES_CORTOS : MESES)[f.getUTCMonth()]
  return `${f.getUTCDate()} de ${mes}${conAnio ? ` de ${f.getUTCFullYear()}` : ''}`
}

/** "del 14 al 20 de septiembre", "del 28 de septiembre al 4 de octubre", con el año si cambia. */
function tramo(desde, hasta, corto) {
  const d = aDate(desde)
  const h = aDate(hasta)
  if (desde === hasta) return diaYMes(desde, { corto })
  if (d.getUTCFullYear() !== h.getUTCFullYear()) {
    return `del ${diaYMes(desde, { corto, conAnio: true })} al ${diaYMes(hasta, { corto, conAnio: true })}`
  }
  if (d.getUTCMonth() === h.getUTCMonth()) {
    return `del ${d.getUTCDate()} al ${diaYMes(hasta, { corto })}`
  }
  return `del ${diaYMes(desde, { corto })} al ${diaYMes(hasta, { corto })}`
}

/**
 * El título del período: "jueves 17 de septiembre", "semana del 14 al 20 de septiembre", "septiembre de 2026, del
 * 1 al 17", "del 3 de agosto al 12 de septiembre".
 */
export function tituloDelPeriodo({ tipo, desde, hasta }) {
  if (!esFecha(desde) || !esFecha(hasta)) return 'Elige el período'
  if (tipo === 'DIA') return `${DIAS[aDate(desde).getUTCDay()]} ${diaYMes(desde)}`
  if (tipo === 'SEMANA') return `semana ${tramo(desde, hasta)}`
  if (tipo === 'MES') {
    const f = aDate(desde)
    const mes = `${MESES[f.getUTCMonth()]} de ${f.getUTCFullYear()}`
    return hasta === ultimoDelMes(desde) ? mes : `${mes}, del 1 al ${aDate(hasta).getUTCDate()}`
  }
  return tramo(desde, hasta)
}

/** "Sin movimientos entre el 1 y el 7 de octubre" (spec 0007, §6). */
export function textoSinMovimientos({ desde, hasta }) {
  if (desde === hasta) return `Sin movimientos el ${diaYMes(desde)}`
  const t = tramo(desde, hasta).replace(/^del /, 'entre el ').replace(' al ', ' y el ')
  return `Sin movimientos ${t}`
}

/** La etiqueta de una fila del día por día: "lun 14" o "14–20 sept". */
export function etiquetaDeFila({ desde, hasta }) {
  const d = aDate(desde)
  if (desde === hasta) return `${DIAS_CORTOS[d.getUTCDay()]} ${d.getUTCDate()}`
  const h = aDate(hasta)
  return d.getUTCMonth() === h.getUTCMonth()
    ? `${d.getUTCDate()}–${h.getUTCDate()} ${MESES_CORTOS[h.getUTCMonth()]}`
    : `${d.getUTCDate()} ${MESES_CORTOS[d.getUTCMonth()]}–${h.getUTCDate()} ${MESES_CORTOS[h.getUTCMonth()]}`
}

/** Hoy en Colombia, `AAAA-MM-DD`: los reportes cortan los días allá, esté donde esté el equipo. */
export function hoyEnColombia(ahora = new Date()) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(ahora)
}
