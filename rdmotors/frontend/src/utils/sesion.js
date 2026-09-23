/**
 * La sesión en la pantalla (spec 0004, fase 1): qué pantalla toca, qué le falta a un formulario de entrar o de
 * contraseña, y la cookie del token anti-CSRF.
 *
 * El token de la sesión **no pasa por aquí**: viaja en una cookie que la página no puede leer. Lo único que la página
 * lee es el token anti-CSRF, que tiene que mandar de vuelta en cada escritura.
 */

/** Lo que dispara el cliente de la API cuando el servidor dice que no hay sesión. */
export const EVENTO_SIN_SESION = 'rdmotors:sin-sesion'

export const LARGO_MINIMO_CONTRASENA = 6
export const LARGO_MAXIMO_CONTRASENA = 64

export const NOMBRES_DE_ROL = { ADMINISTRADOR: 'Administrador', CAJERO: 'Cajero' }

/** El valor de una cookie, o `null`. `cookies` es `document.cookie`. */
export function leerCookie(nombre, cookies) {
  for (const par of String(cookies ?? '').split(';')) {
    const [clave, ...valor] = par.trim().split('=')
    if (clave === nombre) return decodeURIComponent(valor.join('='))
  }
  return null
}

/**
 * Qué pantalla toca:
 *   CARGANDO            todavía no se sabe
 *   INSTALAR            no hay ningún usuario: se crea el primer administrador
 *   ENTRAR              hay usuarios y nadie adentro
 *   CAMBIAR_CONTRASENA  entró con una contraseña inicial o restablecida (RF-014)
 *   ADENTRO             el sistema
 */
export function pantallaDeSesion({ cargando, usuario, faltaAdministrador }) {
  if (cargando) return 'CARGANDO'
  if (usuario) return usuario.debeCambiarContrasena ? 'CAMBIAR_CONTRASENA' : 'ADENTRO'
  return faltaAdministrador ? 'INSTALAR' : 'ENTRAR'
}

function problemaDeContrasena(contrasena) {
  if (!contrasena) return 'Escribe la contraseña'
  if (contrasena.length < LARGO_MINIMO_CONTRASENA) {
    return `La contraseña tiene que tener al menos ${LARGO_MINIMO_CONTRASENA} caracteres`
  }
  if (contrasena.length > LARGO_MAXIMO_CONTRASENA) {
    return `La contraseña puede tener hasta ${LARGO_MAXIMO_CONTRASENA} caracteres`
  }
  return null
}

const sinVacios = (problemas) => Object.fromEntries(Object.entries(problemas).filter(([, v]) => v))

/** Entrar: los dos campos escritos. El largo lo revisa el servidor, que responde igual para todo. */
export function problemasParaEntrar({ usuario, contrasena }) {
  return sinVacios({
    usuario: String(usuario ?? '').trim() ? null : 'Escribe tu usuario',
    contrasena: contrasena ? null : 'Escribe tu contraseña',
  })
}

const USUARIO_VALIDO = /^[\p{L}\p{N}._-]{3,40}$/u

/** El primer administrador (RF-018): las mismas reglas que el servidor, para decirlo antes de mandar. */
export function problemasDelAdministrador({ nombre, usuario, contrasena, confirmacion }) {
  const u = String(usuario ?? '').trim()
  return sinVacios({
    nombre: String(nombre ?? '').trim() ? null : 'Escribe tu nombre: es el que sale en el comprobante',
    usuario: !u ? 'Escribe el usuario con que vas a entrar'
      : USUARIO_VALIDO.test(u) ? null
        : 'De 3 a 40 letras o números, sin espacios (puede llevar punto, guion o guion bajo)',
    contrasena: problemaDeContrasena(contrasena),
    confirmacion: contrasena && confirmacion !== contrasena ? 'No coincide con la contraseña' : null,
  })
}

/** Cambiar la contraseña (RF-014, RF-017). */
export function problemasDeCambio({ actual, nueva, confirmacion }) {
  return sinVacios({
    actual: actual ? null : 'Escribe tu contraseña actual',
    nueva: problemaDeContrasena(nueva) ?? (nueva === actual ? 'Tiene que ser distinta de la actual' : null),
    confirmacion: nueva && confirmacion !== nueva ? 'No coincide con la contraseña nueva' : null,
  })
}

/** "Rubén Díaz" → "RD"; "Carolina" → "C". Para el botón de la persona en la barra. */
export function iniciales(nombre) {
  return String(nombre ?? '').trim().split(/\s+/).filter(Boolean).slice(0, 2)
    .map((palabra) => palabra[0].toUpperCase()).join('')
}
