/**
 * El respaldo dicho en palabras (spec 0011), sin JSX, para probarlo con `node --test`.
 *
 * Las cifras las calcula el servidor: cuándo fue la última copia que se bajó, cuánto pesó y si hay que avisar.
 * Aquí solo se traducen a algo que el dueño del almacén entienda de un vistazo.
 */
import { diasEntre } from './periodo.js'

/**
 * Se acaba de bajar una copia. Lo grita la pantalla de Respaldo y lo escucha el aviso de arriba, que si no se
 * quedaría diciendo *"hace 8 días"* con la copia recién bajada en la misma pantalla.
 */
export const EVENTO_RESPALDO_HECHO = 'rdmotors:respaldo-hecho'

/** "4,2 MB". En kilobytes por debajo de un mega: una base chica no tiene que decir "0,0 MB". */
export function pesoEnPalabras(bytes) {
  if (bytes == null) return ''
  if (bytes < 1024) return `${bytes} bytes`
  const kb = bytes / 1024
  if (kb < 1024) return `${redondear(kb)} KB`
  const mb = kb / 1024
  if (mb < 1024) return `${redondear(mb)} MB`
  return `${redondear(mb / 1024)} GB`
}

const redondear = (n) => n.toFixed(1).replace('.', ',')

/** "1,4 s" o "340 ms": cuánto tardó la copia. */
export function duracionEnPalabras(ms) {
  if (ms == null) return ''
  return ms < 1000 ? `${ms} ms` : `${redondear(ms / 1000)} s`
}

/** Un instante ISO a "hoy", "ayer" o "hace 3 días", según el día de Colombia. */
export function cuandoEnPalabras(instante, hoy) {
  if (!instante) return ''
  const dia = diaDe(instante)
  const dias = diasEntre(dia, hoy) - 1
  if (dias <= 0) return 'hoy'
  if (dias === 1) return 'ayer'
  return `hace ${dias} días`
}

/** El día de Colombia de un instante, como "2026-09-21". */
export function diaDe(instante) {
  const fecha = new Date(instante)
  if (Number.isNaN(fecha.getTime())) return ''
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Bogota', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(fecha)
}

/**
 * El aviso de arriba (spec 0011, RF-011), o `null` si no hay nada que decir.
 *
 * Dice **qué pasó** y **qué hacer**, nunca solo "error": el dueño tiene que saber si su negocio está respaldado o
 * no. Y nunca habla de carpetas ni de discos, porque la copia ya no vive en ningún computador nuestro: vive en el
 * suyo, si se la llevó.
 */
export function avisoDelRespaldo(estado) {
  if (!estado || !estado.hayQueAvisar) return null
  const ultimoIntento = estado.copias?.[0]
  if (ultimoIntento && ultimoIntento.estado === 'FALLO') {
    return {
      tono: 'GRAVE',
      texto: `La última vez que intentaste bajar una copia falló: ${ultimoIntento.error ?? 'no se pudo sacar'}. `
        + (estado.diasSinBajar == null
          ? 'Nunca se ha bajado ninguna.'
          : `La última buena la bajaste ${haceCuanto(estado.diasSinBajar)}.`),
    }
  }
  if (estado.diasSinBajar == null) {
    return {
      tono: 'GRAVE',
      texto: 'Nunca te has bajado una copia del negocio. Si un día pierdes el acceso al sistema, no tendrías cómo '
        + 'recuperar el inventario, las ventas ni lo que te deben.',
    }
  }
  return {
    tono: 'AVISO',
    texto: `La última copia que bajaste es de ${haceCuanto(estado.diasSinBajar)}. Baja una nueva y guárdala.`,
  }
}

/** "hoy", "ayer" o "hace 9 días", a partir del número que manda el servidor. */
export function haceCuanto(dias) {
  if (dias == null) return ''
  if (dias <= 0) return 'hoy'
  if (dias === 1) return 'ayer'
  return `hace ${dias} días`
}

/** Cómo se lee una copia en la lista. */
export function textoDeLaCopia(copia, hoy) {
  if (copia.estado === 'FALLO') return `Falló · ${copia.error ?? 'sin detalle'}`
  return `${cuandoEnPalabras(copia.hechoEn, hoy)} · ${pesoEnPalabras(copia.bytes)} · `
    + `${duracionEnPalabras(copia.duracionMs)}`
}
