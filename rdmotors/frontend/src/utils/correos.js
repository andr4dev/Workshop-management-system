/**
 * El correo del cierre de caja (spec 0010), sin JSX, para probarlo con `node --test`.
 *
 * La misma revisión de forma que el servidor (`Destinatarios.java`), para avisar mientras se escribe; si no
 * coincidieran, manda el servidor.
 */
import { cuandoEnPalabras } from './respaldo.js'

export const MAXIMO_DESTINATARIOS = 5
const FORMA = /^[^\s@,;]+@[^\s@,;]+\.[^\s@,;]{2,}$/

/** Lo escrito en la caja de texto, un correo por línea o separados por comas, limpio y sin repetir. */
export function destinatariosDesdeTexto(texto) {
  const vistos = new Set()
  for (const trozo of String(texto ?? '').split(/[\n,;]+/)) {
    const correo = trozo.trim().toLowerCase()
    if (correo) vistos.add(correo)
  }
  return [...vistos]
}

/** Qué está mal en lo escrito, o `null` si se puede guardar. */
export function problemaDeLosDestinatarios(correos) {
  const malos = correos.filter((c) => !FORMA.test(c))
  if (malos.length === 1) return `«${malos[0]}» no parece un correo: revísalo`
  if (malos.length > 1) return `Estos no parecen correos: ${malos.join(', ')}`
  if (correos.length > MAXIMO_DESTINATARIOS) return `El resumen llega a ${MAXIMO_DESTINATARIOS} correos como máximo`
  return null
}

export const ESTADOS = {
  POR_MANDAR: 'Por mandar',
  ENVIADO: 'Enviado',
  FALLO: 'Falló',
}

export const TIPOS = {
  CIERRE_DE_TURNO: 'Cierre de turno',
  PRUEBA: 'Prueba',
}

/**
 * Qué pasó con un correo, en una línea. Un "por mandar" con intentos no es lo mismo que uno recién encolado: el
 * primero está esperando a que vuelva internet, y eso hay que decirlo.
 */
export function textoDelCorreo(correo, hoy) {
  if (correo.estado === 'ENVIADO') return `Enviado ${cuandoEnPalabras(correo.enviadoEn, hoy)}`
  if (correo.estado === 'FALLO') return `Falló · ${correo.ultimoError ?? 'sin detalle'}`
  if (correo.intentos > 0) {
    return `Esperando para reintentar (${correo.intentos} ${correo.intentos === 1 ? 'intento' : 'intentos'})`
      + ` · ${correo.ultimoError ?? ''}`.trimEnd()
  }
  return correo.ultimoError ? `Por mandar · ${correo.ultimoError}` : 'Por mandar'
}

/** El aviso de arriba: qué falta para que los correos salgan, o `null` si no falta nada. */
export function avisoDeLosCorreos(estado) {
  if (!estado) return null
  if (estado.destinatarios.length === 0) {
    return { tono: 'AVISO', texto: 'Nadie recibe el resumen del cierre. Escribe abajo a qué correos debe llegar.' }
  }
  if (!estado.listoParaMandar) {
    return { tono: 'GRAVE', texto: `Los resúmenes se están acumulando sin salir: ${estado.loQueFalta}.` }
  }
  const fallidos = (estado.ultimos ?? []).filter((c) => c.estado === 'FALLO').length
  if (fallidos > 0) {
    return { tono: 'GRAVE', texto: `${fallidos} ${fallidos === 1 ? 'correo falló' : 'correos fallaron'}: revisa abajo por qué.` }
  }
  return null
}
