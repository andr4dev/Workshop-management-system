import { useCallback, useEffect, useMemo, useState } from 'react'
import { instalacionApi, sesionApi } from '../../api/cliente'
import { EVENTO_SIN_SESION } from '../../utils/sesion'
import { ContextoDeSesion } from './contexto'

/**
 * Sabe quién está adentro (spec 0004): al abrir pregunta al servidor, y si no hay nadie, si hay que crear el primer
 * administrador.
 *
 * Si en cualquier momento el servidor responde que no hay sesión (venció, o desactivaron a la persona), el cliente
 * de la API avisa con `EVENTO_SIN_SESION` y la app vuelve a *Entrar* **sin cambiar de pantalla**: al entrar de nuevo,
 * se sigue donde se estaba.
 */
/** Quién está adentro y, si nadie, si falta el primer administrador. Devuelve el estado; no lo guarda. */
async function consultar() {
  try {
    const usuario = await sesionApi.yo()
    return { cargando: false, usuario, faltaAdministrador: false, error: null }
  } catch (error) {
    if (error.estado !== 401) return { cargando: false, usuario: null, faltaAdministrador: false, error }
    try {
      const { faltaAdministrador } = await instalacionApi.estado()
      return { cargando: false, usuario: null, faltaAdministrador, error: null }
    } catch (e) {
      return { cargando: false, usuario: null, faltaAdministrador: false, error: e }
    }
  }
}

export default function ProveedorDeSesion({ children }) {
  const [estado, setEstado] = useState({ cargando: true, usuario: null, faltaAdministrador: false, error: null })
  const [intento, setIntento] = useState(0)

  useEffect(() => {
    let vigente = true
    consultar().then((nuevo) => { if (vigente) setEstado(nuevo) })
    return () => { vigente = false }
  }, [intento])

  const recargar = useCallback(() => {
    setEstado((e) => ({ ...e, cargando: true, error: null }))
    setIntento((n) => n + 1)
  }, [])

  useEffect(() => {
    const sinSesion = () => setEstado((e) => ({ ...e, usuario: null }))
    window.addEventListener(EVENTO_SIN_SESION, sinSesion)
    return () => window.removeEventListener(EVENTO_SIN_SESION, sinSesion)
  }, [])

  const valor = useMemo(() => ({
    ...estado,
    recargar,
    /** Entró, creó el primer administrador o cambió la contraseña: el servidor ya puso la cookie. */
    entro: (usuario) => setEstado({ cargando: false, usuario, faltaAdministrador: false, error: null }),
    /** Salir y *Cambiar de usuario*: el servidor borra la cookie. Si no hay red, igual se sale en la pantalla. */
    salir: async () => {
      try { await sesionApi.salir() } catch { /* la cookie vence sola */ }
      setEstado((e) => ({ ...e, usuario: null }))
    },
  }), [estado, recargar])

  return <ContextoDeSesion.Provider value={valor}>{children}</ContextoDeSesion.Provider>
}
