import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { respaldosApi } from '../api/cliente'
import { useSesion } from './sesion/contexto'
import { esAdministrador } from '../utils/permisos'
import { avisoDelRespaldo, EVENTO_RESPALDO_HECHO } from '../utils/respaldo'
import estilos from './AvisoDeRespaldo.module.css'

const CERRADO = 'rdmotors:aviso-de-respaldo-cerrado'

/**
 * *"Hace nueve días que no bajas una copia"* (spec 0011, RF-011), arriba de todo y solo para el administrador.
 *
 * <p><b>Por qué va aquí y no en la pantalla de Respaldo.</b> Nadie entra a mirar el respaldo; se entra a vender. Si
 * el aviso viviera dentro de su propia pantalla, un negocio sin respaldo podría quedarse así meses.
 *
 * <p>Se puede cerrar y no vuelve a salir en esta sesión del navegador: repetirlo cada recarga lo volvería ruido, y
 * al ruido se le deja de hacer caso. Mañana vuelve a salir si el problema sigue.
 */
export default function AvisoDeRespaldo() {
  const { usuario } = useSesion()
  const [aviso, setAviso] = useState(null)
  const [cerrado, setCerrado] = useState(() => leerCerrado())

  const soyAdministrador = esAdministrador(usuario)

  useEffect(() => {
    if (!soyAdministrador) return undefined
    let vigente = true
    const mirar = () => respaldosApi.estado()
      .then((estado) => { if (vigente) setAviso(avisoDelRespaldo(estado)) })
      // Que no se pueda consultar el respaldo no es algo que deba interrumpir a nadie: la pantalla de Respaldo lo dirá.
      .catch(() => { if (vigente) setAviso(null) })
    mirar()
    // Si acaban de bajar una copia, este aviso tiene que enterarse: si no, seguiría diciendo "hace 9 días" con la
    // copia recién bajada a la vista en la misma pantalla.
    window.addEventListener(EVENTO_RESPALDO_HECHO, mirar)
    return () => {
      vigente = false
      window.removeEventListener(EVENTO_RESPALDO_HECHO, mirar)
    }
  }, [soyAdministrador])

  // Al cajero no se le pregunta nada y no se le muestra nada: el respaldo no es suyo.
  if (!soyAdministrador || !aviso || cerrado) return null

  return (
    <div className={aviso.tono === 'GRAVE' ? estilos.grave : estilos.aviso} role="alert">
      <span className={estilos.texto}>
        <span aria-hidden>⚠</span> {aviso.texto}
      </span>
      <Link to="/respaldo" className={estilos.enlace}>Ver el respaldo</Link>
      <button type="button" className={estilos.cerrar} aria-label="Cerrar el aviso" onClick={cerrar}>×</button>
    </div>
  )

  function cerrar() {
    setCerrado(true)
    try {
      sessionStorage.setItem(CERRADO, '1')
    } catch {
      // Sin sessionStorage (modo privado, permisos), el aviso vuelve en la próxima carga. No es grave.
    }
  }
}

function leerCerrado() {
  try {
    return sessionStorage.getItem(CERRADO) === '1'
  } catch {
    return false
  }
}
