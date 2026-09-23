import { createContext, useContext } from 'react'

/**
 * Quién está adentro (spec 0004). Lo llena `ProveedorDeSesion`; las pantallas lo leen con `useSesion()`.
 *
 * `{ cargando, usuario, faltaAdministrador, error, entro(usuario), salir(), recargar() }`. El usuario trae `id`,
 * `usuario`, `nombre`, `rol`, `veCostos` y `debeCambiarContrasena`. **Nunca el token**: ese vive en una cookie que la
 * página no puede leer.
 */
export const ContextoDeSesion = createContext(null)

export function useSesion() {
  return useContext(ContextoDeSesion)
}
