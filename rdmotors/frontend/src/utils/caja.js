/**
 * Lógica del turno de caja, sin JSX, para probarla con `node --test`.
 */
import { soloDigitos } from './formato.js'

/**
 * El fondo escrito en pantalla, en pesos. Acepta "100000", "100.000" o "$ 100.000".
 * `null` si no se escribió nada: vacío no es $0, y la pantalla lo pide explícito.
 */
export function fondoDesdeTexto(texto) {
  const digitos = soloDigitos(texto)
  return digitos === '' ? null : Number(digitos)
}

/** `null` si el fondo sirve. $0 sirve: hay tiendas que arrancan con el cajón vacío. */
export function problemaDelFondo(texto) {
  const fondo = fondoDesdeTexto(texto)
  if (fondo == null) return 'Escribe con cuánto arranca el cajón, aunque sea $0'
  if (!Number.isSafeInteger(fondo)) return 'Ese monto no es válido'
  return null
}
