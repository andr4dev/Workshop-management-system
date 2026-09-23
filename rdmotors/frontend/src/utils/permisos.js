/**
 * Qué ve cada rol en la pantalla (spec 0004, §5). Esconder no protege nada —el servidor responde "no permitido" y no
 * cambia nada—, pero al cajero no se le ofrece lo que no puede usar.
 */

export const MODULOS = [
  { ruta: '/vender', nombre: 'Vender', soloAdministrador: false },
  // La ven los dos (spec 0008, decisión 5): el cajero recibe abonos y tiene que saber qué debe cada uno.
  { ruta: '/cartera', nombre: 'Cartera', soloAdministrador: false },
  { ruta: '/compras', nombre: 'Compras', soloAdministrador: true },
  { ruta: '/inventario', nombre: 'Inventario', soloAdministrador: false },
  { ruta: '/reportes', nombre: 'Reportes', soloAdministrador: true },
]

/** Las direcciones que son del administrador, con todo lo que cuelga de ellas. */
const RUTAS_DEL_ADMINISTRADOR = ['/compras', '/reportes', '/tienda', '/usuarios', '/respaldo', '/correos']

export const MENSAJE_ES_DEL_ADMINISTRADOR = 'Es del administrador'

export const esAdministrador = (usuario) => usuario?.rol === 'ADMINISTRADOR'

/** El servidor lo dice en la sesión; la pantalla no lo deduce del rol por su cuenta (decisión 10 del plan). */
export const veCostos = (usuario) => usuario?.veCostos === true

export const modulosPara = (usuario) => MODULOS.filter((m) => !m.soloAdministrador || esAdministrador(usuario))

/** Si esa persona puede abrir esa dirección. Una que no, muestra "Es del administrador" en vez de la pantalla. */
export function puedeAbrir(usuario, ruta) {
  if (esAdministrador(usuario)) return true
  return !RUTAS_DEL_ADMINISTRADOR.some((r) => ruta === r || ruta.startsWith(`${r}/`))
}

/**
 * El turno abierto es de otra persona y quien está adentro no es administrador (decisión 2): no vende, no registra
 * gastos ni retiros y no cierra en él. `turno.abiertoPor` lo manda el servidor con el turno abierto.
 */
export function esTurnoAjeno(usuario, turno) {
  if (!turno || !usuario || esAdministrador(usuario)) return false
  return turno.abiertoPor?.id !== usuario.id
}

/** Sin suponer si es "él" o "ella": el sistema no lo sabe. */
export function textoDeTurnoAjeno(turno) {
  const nombre = turno?.abiertoPor?.nombre
  return nombre
    ? `El turno abierto es de ${nombre}: lo cierra ${nombre} o un administrador`
    : 'El turno abierto es de otra persona: lo cierra quien lo abrió o un administrador'
}
