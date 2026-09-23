import { forwardRef, useEffect, useState } from 'react'
import { inventarioApi, repuestosApi } from '../../api/cliente'
import { formatoCOP } from '../../utils/formato'
import estilos from './BuscadorVenta.module.css'

const MINIMO_PARA_BUSCAR = 2

/**
 * El buscador del mostrador (spec 0003, RF-004): un solo campo, primero teclado.
 *
 *   - Mientras se escribe, tras una pausa, lista lo que coincide por código, nombre, marca o moto
 *     (sin tildes). Flechas para moverse.
 *   - Enter con uno resaltado lo agrega. Si no hay resaltado, busca el código EXACTO y lo agrega; si
 *     solo hay uno en la lista, ese. El foco vuelve solo al campo.
 *   - Esc borra lo escrito.
 *
 * `onAgregar(repuesto)` devuelve `null` si lo agregó, o por qué no (sin precio, sin stock): el texto
 * se queda para que el cajero vea qué pasó.
 */
const BuscadorVenta = forwardRef(function BuscadorVenta({ onAgregar, deshabilitado }, ref) {
  const [texto, setTexto] = useState('')
  const [resultados, setResultados] = useState({ clave: null, elementos: [], error: null })
  const [resaltado, setResaltado] = useState(-1)
  const [mensaje, setMensaje] = useState(null)

  // La clave es lo que se busca: cada respuesta recuerda para qué texto llegó, así una respuesta vieja
  // nunca pinta resultados de algo que ya no está escrito.
  const clave = texto.trim().length >= MINIMO_PARA_BUSCAR ? texto.trim() : null
  const visibles = resultados.clave === clave ? resultados.elementos : []
  const indice = resaltado < visibles.length ? resaltado : -1

  useEffect(() => {
    if (!clave) return
    let vigente = true
    const pausa = setTimeout(() => {
      inventarioApi.listar({ texto: clave, tamano: 8 })
        .then((pagina) => { if (vigente) setResultados({ clave, elementos: pagina.elementos, error: null }) })
        .catch((error) => { if (vigente) setResultados({ clave, elementos: [], error }) })
    }, 250)
    return () => { vigente = false; clearTimeout(pausa) }
  }, [clave])

  function agregar(repuesto) {
    const problema = onAgregar(repuesto)
    if (problema) {
      setMensaje(problema)
      return
    }
    setTexto('')
    setResaltado(-1)
    setMensaje(null)
  }

  async function alEnter() {
    const buscado = texto.trim()
    if (!buscado) return
    if (indice >= 0) {
      agregar(visibles[indice])
      return
    }
    try {
      const [exacto] = await repuestosApi.porCodigo(buscado)
      if (exacto) {
        agregar(exacto)
        return
      }
    } catch (error) {
      setMensaje(error.estado === 0 ? 'No hay conexión con el servidor' : error.message)
      return
    }
    if (visibles.length === 1) {
      agregar(visibles[0])
    } else if (visibles.length > 1) {
      setResaltado(0)
      setMensaje('Hay varios: elige con las flechas y Enter')
    } else {
      setMensaje(`No hay un repuesto con «${buscado}»`)
    }
  }

  function alTeclear(e) {
    if (e.key === 'Enter') {
      e.preventDefault()
      alEnter()
    } else if (e.key === 'ArrowDown' && visibles.length) {
      e.preventDefault()
      setResaltado((indice + 1) % visibles.length)
    } else if (e.key === 'ArrowUp' && visibles.length) {
      e.preventDefault()
      setResaltado(indice <= 0 ? visibles.length - 1 : indice - 1)
    } else if (e.key === 'Escape' && texto) {
      e.preventDefault()
      setTexto('')
      setResaltado(-1)
      setMensaje(null)
    }
  }

  return (
    <div className={estilos.buscador}>
      <input
        ref={ref}
        className={estilos.campo}
        value={texto}
        onChange={(e) => { setTexto(e.target.value); setResaltado(-1); setMensaje(null) }}
        onKeyDown={alTeclear}
        placeholder="Código, nombre, marca o moto — Enter agrega"
        aria-label="Buscar repuesto para vender"
        autoComplete="off"
        spellCheck="false"
        // El mostrador arranca listo para escribir: el cursor en el buscador sin tocar el mouse.
        autoFocus
        disabled={deshabilitado}
      />

      {mensaje && <p className={estilos.mensaje} role="status">{mensaje}</p>}
      {resultados.error && resultados.clave === clave && (
        <p className={estilos.mensaje} role="alert">
          {resultados.error.estado === 0 ? 'No hay conexión con el servidor' : resultados.error.message}
        </p>
      )}

      {visibles.length > 0 && (
        <ul className={estilos.lista} role="listbox" aria-label="Repuestos que coinciden">
          {visibles.map((r, i) => (
            <li key={r.id}>
              <button
                type="button"
                role="option"
                aria-selected={i === indice}
                className={`${estilos.opcion} ${i === indice ? estilos.opcionResaltada : ''}`}
                // onMouseDown y no onClick: el clic no le quita el foco al campo, y el teclado sigue.
                onMouseDown={(e) => { e.preventDefault(); agregar(r) }}
                tabIndex={-1}
              >
                <span className={estilos.codigo}>{r.codigo}</span>
                <span className={estilos.nombre}>
                  {r.nombre} <span className={estilos.marca}>{r.marca}</span>
                  {r.aplicacion && <span className={estilos.aplicacion}>{r.aplicacion}</span>}
                </span>
                <span className={r.stock > 0 ? estilos.stock : estilos.sinStock}>
                  {r.stock > 0 ? `hay ${r.stock}` : 'sin stock'}
                </span>
                <span className={estilos.precio}>{formatoCOP(r.precio)}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
})

export default BuscadorVenta
