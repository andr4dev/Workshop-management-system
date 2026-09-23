import { useEffect, useRef } from 'react'
import estilos from './Modal.module.css'

/**
 * Diálogo modal.
 *
 * Tres cosas que no son opcionales en un POS que se opera con teclado:
 *   · Escape cierra.
 *   · El foco entra al abrir y no puede salirse con Tab mientras esté abierto.
 *   · Al cerrar, el foco VUELVE a donde estaba. Sin eso el cajero queda perdido
 *     y tiene que agarrar el mouse — que es justo lo que estamos evitando.
 */
export default function Modal({ abierto, onCerrar, titulo, children, pie, ancho = 560 }) {
  const caja = useRef(null)
  const focoPrevio = useRef(null)

  // onCerrar se lee de un ref: si el efecto dependiera de él, cada vez que el padre crea otra función
  // (por ejemplo al pasar a "cobrando") el efecto se rehace, anota como foco previo lo que haya en ese
  // momento —a veces nada— y al cerrar devuelve el cursor ahí en vez de al campo de donde se abrió.
  const cerrar = useRef(onCerrar)
  useEffect(() => { cerrar.current = onCerrar })

  useEffect(() => {
    if (!abierto) return

    focoPrevio.current = document.activeElement

    const enfocables = () =>
      caja.current?.querySelectorAll(
        'button:not(:disabled), input:not(:disabled), select, textarea, [tabindex]:not([tabindex="-1"])',
      ) ?? []

    // Al primer campo, no al botón de cerrar: se abre para escribir.
    const primero = enfocables()[0]
    primero?.focus()

    function alTeclear(e) {
      if (e.key === 'Escape') {
        e.stopPropagation()
        cerrar.current()
        return
      }
      if (e.key !== 'Tab') return

      const lista = [...enfocables()]
      if (lista.length === 0) return
      const [inicio] = lista
      const fin = lista[lista.length - 1]

      // Ciclo cerrado: Tab en el último vuelve al primero, y al revés.
      if (e.shiftKey && document.activeElement === inicio) {
        e.preventDefault()
        fin.focus()
      } else if (!e.shiftKey && document.activeElement === fin) {
        e.preventDefault()
        inicio.focus()
      }
    }

    document.addEventListener('keydown', alTeclear, true)
    const overflowPrevio = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    return () => {
      document.removeEventListener('keydown', alTeclear, true)
      document.body.style.overflow = overflowPrevio
      focoPrevio.current?.focus?.()
    }
  }, [abierto])

  if (!abierto) return null

  return (
    <div className={estilos.fondo} onMouseDown={(e) => e.target === e.currentTarget && onCerrar()}>
      <div
        ref={caja}
        className={estilos.caja}
        style={{ maxWidth: ancho }}
        role="dialog"
        aria-modal="true"
        aria-label={titulo}
      >
        <header className={estilos.cabecera}>
          <h2 className={estilos.titulo}>{titulo}</h2>
          <button className={estilos.cerrar} onClick={onCerrar} aria-label="Cerrar" type="button">
            ×
          </button>
        </header>

        <div className={estilos.cuerpo}>{children}</div>

        {pie && <footer className={estilos.pie}>{pie}</footer>}
      </div>
    </div>
  )
}
