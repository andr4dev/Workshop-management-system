import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import estilos from './Ayuda.module.css'

const MOVIL = 768
const MARGEN = 8   // separación mínima a los bordes de la pantalla

/**
 * Un ícono de pregunta junto a un título, que explica qué significa. Portado del `HelpTip` del
 * car-wash, con el mismo comportamiento:
 *
 *   · Con mouse abre al pasar el cursor; con teclado, al enfocar. En pantalla táctil, con un toque:
 *     no depende de un hover que el dedo no tiene.
 *   · Cierra con Escape, tocando fuera, o quitando el cursor o el foco.
 *   · Va en un portal con posición fija: una tabla con scroll horizontal no lo recorta, y se acomoda
 *     para no salirse de la pantalla (debajo del ícono; arriba si no cabe). En celular es una hoja
 *     abajo, a todo el ancho.
 *
 * Sirve para sacar de la pantalla los párrafos que explican una columna: la explicación queda a un
 * gesto de quien la necesita, y no le estorba a quien ya la sabe.
 *
 * @param sobre     de qué es la ayuda, para el lector de pantalla ("Ayuda sobre precio fijado")
 * @param titulo    opcional, en negrita arriba
 * @param children  la explicación
 */
export default function Ayuda({ sobre, titulo, children }) {
  const [abierta, setAbierta] = useState(false)
  const [posicion, setPosicion] = useState(null)
  const contenedor = useRef(null)
  const boton = useRef(null)
  const globo = useRef(null)
  const id = useId()

  useEffect(() => {
    if (!abierta) return
    const alPresionar = (e) => {
      // El globo va en un portal, fuera del contenedor: también cuenta como "dentro".
      const dentro = contenedor.current?.contains(e.target) || globo.current?.contains(e.target)
      if (!dentro) setAbierta(false)
    }
    const alTeclear = (e) => { if (e.key === 'Escape') setAbierta(false) }
    document.addEventListener('pointerdown', alPresionar)
    document.addEventListener('keydown', alTeclear)
    return () => {
      document.removeEventListener('pointerdown', alPresionar)
      document.removeEventListener('keydown', alTeclear)
    }
  }, [abierta])

  // Se mide antes de pintar para que el globo no aparezca en un sitio y salte a otro.
  useLayoutEffect(() => {
    if (!abierta) return

    const calcular = () => {
      const b = boton.current?.getBoundingClientRect()
      const g = globo.current
      if (!b || !g) return
      const ancho = window.innerWidth
      const alto = window.innerHeight

      if (ancho <= MOVIL) {
        setPosicion({ left: 12, right: 12, bottom: 12, maxWidth: 'none' })
        return
      }

      const left = Math.max(MARGEN, Math.min(b.left + b.width / 2 - g.offsetWidth / 2,
        ancho - g.offsetWidth - MARGEN))
      let top = b.bottom + 8
      if (top + g.offsetHeight > alto - MARGEN) {
        const arriba = b.top - g.offsetHeight - 8
        top = arriba >= MARGEN ? arriba : Math.max(MARGEN, alto - g.offsetHeight - MARGEN)
      }
      setPosicion({ top, left })
    }

    calcular()
    window.addEventListener('resize', calcular)
    window.addEventListener('scroll', calcular, true)   // el scroll de cualquier contenedor
    return () => {
      window.removeEventListener('resize', calcular)
      window.removeEventListener('scroll', calcular, true)
    }
  }, [abierta])

  // La posición se borra al abrir (en el manejador, no en el efecto): el globo queda oculto hasta
  // que se mide, en vez de asomarse donde estuvo la vez anterior.
  const abrir = () => { setPosicion(null); setAbierta(true) }
  const cerrar = () => setAbierta(false)
  const conMouse = (accion) => (e) => { if (e.pointerType === 'mouse') accion() }

  const estiloGlobo = posicion
    ? { position: 'fixed', visibility: 'visible', ...posicion }
    : { position: 'fixed', top: 0, left: 0, visibility: 'hidden' }

  return (
    <span className={estilos.contenedor} ref={contenedor}>
      <button
        ref={boton}
        type="button"
        className={estilos.boton}
        aria-label={`Ayuda sobre ${sobre}`}
        aria-expanded={abierta}
        aria-describedby={abierta ? id : undefined}
        onClick={() => (abierta ? cerrar() : abrir())}
        onPointerEnter={conMouse(abrir)}
        onPointerLeave={conMouse(cerrar)}
        onFocus={abrir}
        onBlur={cerrar}
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
          strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <circle cx="12" cy="12" r="10" />
          <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
          <path d="M12 17h.01" />
        </svg>
      </button>

      {abierta && createPortal(
        <span role="tooltip" id={id} ref={globo} className={estilos.globo} style={estiloGlobo}>
          {titulo && <span className={estilos.titulo}>{titulo}</span>}
          {children}
        </span>,
        document.body,
      )}
    </span>
  )
}
