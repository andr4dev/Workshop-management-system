/**
 * La lógica de *Usuarios* (spec 0004, fase 4), sin JSX, para probarla con `node --test`. El servidor vuelve a exigir
 * todas estas reglas: esto es para no hacerle perder el viaje a quien escribe, y para no ofrecer lo que no se puede.
 */

export const ROLES = [
  { valor: 'ADMINISTRADOR', nombre: 'Administrador', explica: 'Ve costos, compras y reportes; administra usuarios' },
  { valor: 'CAJERO', nombre: 'Cajero', explica: 'Vende, maneja su turno y ve el inventario sin costos' },
]

export const NOMBRE_DE_ROL = Object.fromEntries(ROLES.map((r) => [r.valor, r.nombre]))

export const LARGO_MINIMO_CONTRASENA = 6
/** Lo mismo que exige el dominio: de 3 a 40, sin espacios. */
const USUARIO_VALIDO = /^[\p{L}\p{N}._-]{3,40}$/u

export const usuarioNuevo = () => ({ nombre: '', usuario: '', rol: 'CAJERO', contrasena: '' })

/** Qué le falta al formulario, por campo. Vacío si se puede crear. */
export function problemasDelUsuario(datos) {
  const problemas = {}
  if (!datos.nombre?.trim()) problemas.nombre = 'Escribe el nombre: es el que sale en el comprobante'
  if (!datos.usuario?.trim()) problemas.usuario = 'Escribe el usuario con el que va a entrar'
  else if (!USUARIO_VALIDO.test(datos.usuario.trim())) {
    problemas.usuario = 'De 3 a 40 letras o números, sin espacios (puede llevar punto, guion o guion bajo)'
  }
  if (!datos.rol) problemas.rol = 'Elige el rol'
  if ((datos.contrasena ?? '').length < LARGO_MINIMO_CONTRASENA) {
    problemas.contrasena = `La contraseña tiene que tener al menos ${LARGO_MINIMO_CONTRASENA} caracteres`
  }
  return problemas
}

export const sinProblemas = (problemas) => Object.keys(problemas).length === 0

/** Lo que se manda al servidor: el usuario sin espacios alrededor. */
export const comandoDelUsuario = (datos) => ({
  nombre: datos.nombre.trim(),
  usuario: datos.usuario.trim(),
  rol: datos.rol,
  contrasena: datos.contrasena,
})

const administradoresActivos = (usuarios) => usuarios.filter((u) => u.activo && u.rol === 'ADMINISTRADOR').length

/**
 * Por qué no se puede desactivar a alguien, o `null` si sí se puede (RF-016). El servidor lo vuelve a revisar: esto
 * es para decirlo antes, junto al botón.
 */
export function porQueNoSeDesactiva(usuario, yo, usuarios) {
  if (usuario.id === yo?.id) return 'No puedes desactivarte a ti mismo'
  if (usuario.activo && usuario.rol === 'ADMINISTRADOR' && administradoresActivos(usuarios) <= 1) {
    return 'Tiene que quedar al menos un administrador activo'
  }
  return null
}

/** Por qué no se le puede quitar el rol de administrador, o `null` si sí. */
export function porQueNoCambiaAcajero(usuario, usuarios) {
  if (usuario.rol !== 'ADMINISTRADOR' || !usuario.activo) return null
  return administradoresActivos(usuarios) <= 1 ? 'Tiene que quedar al menos un administrador activo' : null
}

/** El orden de la lista: los activos primero y, dentro, por nombre. Los desactivados quedan abajo, en gris. */
export function ordenarUsuarios(usuarios) {
  return [...usuarios].sort((a, b) => (a.activo === b.activo
    ? a.nombre.localeCompare(b.nombre, 'es')
    : (a.activo ? -1 : 1)))
}
