import { useEffect, useRef, useState } from 'react'
import { iniciales, NOMBRES_DE_ROL } from '../../utils/sesion'
import CambiarContrasena from './CambiarContrasena'
import { useSesion } from './contexto'
import estilos from './Sesion.module.css'

/**
 * Quién está adentro, en la barra (spec 0004, RF-006): su nombre y su rol, *Cambiar mi contraseña*, *Cambiar de
 * usuario* y *Salir*. Los dos últimos hacen lo mismo hoy (salir y volver a *Entrar*); se ven distinto porque el cajero
 * que le pasa el mostrador a otro no está "saliendo" del sistema.
 */
export default function MenuDeUsuario() {
  const { usuario, salir } = useSesion()
  const [abierto, setAbierto] = useState(false)
  const [cambiando, setCambiando] = useState(false)
  const contenedor = useRef(null)

  useEffect(() => {
    if (!abierto) return
    const fuera = (e) => { if (!contenedor.current?.contains(e.target)) setAbierto(false) }
    const escape = (e) => { if (e.key === 'Escape') setAbierto(false) }
    document.addEventListener('pointerdown', fuera)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('pointerdown', fuera)
      document.removeEventListener('keydown', escape)
    }
  }, [abierto])

  if (!usuario) return null
  const elegir = (accion) => () => { setAbierto(false); accion() }

  return (
    <div className={estilos.persona} ref={contenedor}>
      <button type="button" className={estilos.botonPersona} aria-haspopup="menu" aria-expanded={abierto}
        title={`${usuario.nombre} · ${NOMBRES_DE_ROL[usuario.rol]}`} onClick={() => setAbierto((a) => !a)}>
        <span className={estilos.inicial} aria-hidden>{iniciales(usuario.nombre)}</span>
        <span className={estilos.nombreCorto}>{usuario.nombre}</span>
      </button>
      {abierto && (
        <div className={estilos.menu} role="menu">
          <div className={estilos.quien}>
            <strong>{usuario.nombre}</strong>
            <span>{NOMBRES_DE_ROL[usuario.rol]} · {usuario.usuario}</span>
          </div>
          <button type="button" role="menuitem" className={estilos.opcion} onClick={elegir(() => setCambiando(true))}>
            Cambiar mi contraseña
          </button>
          <button type="button" role="menuitem" className={estilos.opcion} onClick={elegir(salir)}>
            Cambiar de usuario
          </button>
          <button type="button" role="menuitem" className={estilos.opcion} onClick={elegir(salir)}>
            Salir
          </button>
        </div>
      )}
      {cambiando && <CambiarContrasena onCerrar={() => setCambiando(false)} />}
    </div>
  )
}
