import { useEffect, useState } from 'react'
import Modal from '../Modal'
import Boton from '../Boton'
import Campo from '../Campo'
import { turnosApi } from '../../api/cliente'
import { fondoDesdeTexto, problemaDelFondo } from '../../utils/caja'
import { formatoCOP } from '../../utils/formato'

/**
 * Abrir el turno de caja con el fondo del cajón (spec 0003, H1).
 *
 * El padre lo monta solo al abrir: cada apertura arranca limpia.
 *
 * Si otro dispositivo abrió un turno mientras este modal estaba abierto, no se muestra un error: se
 * avisa al padre para que cargue ese turno, que es en el que se va a vender.
 *
 * Sugiere como fondo el del último turno cerrado (spec 0006, RF-022), mientras nadie haya escrito otro: casi
 * siempre se arranca con la misma base. Si no se puede leer, el campo queda vacío, como antes.
 */
export default function ModalAbrirTurno({ abierto, onCerrar, onAbierto, onYaHabiaUno }) {
  const [fondo, setFondo] = useState('')
  const [error, setError] = useState(null)
  const [intentoAbrir, setIntentoAbrir] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [sugerido, setSugerido] = useState(null)

  useEffect(() => {
    let vigente = true
    turnosApi.cerrados({ pagina: 0, tamano: 1 })
      .then(({ elementos }) => {
        if (!vigente || elementos.length === 0) return
        const fondo = String(elementos[0].fondo)
        setSugerido(fondo)
        // Solo si el campo sigue vacío: no se pisa lo que el cajero ya escribió.
        setFondo((actual) => (actual === '' ? fondo : actual))
      })
      .catch(() => {})
    return () => { vigente = false }
  }, [])

  const problema = problemaDelFondo(fondo)
  const monto = fondoDesdeTexto(fondo)

  async function abrir() {
    setIntentoAbrir(true)
    if (problema) return
    setEnviando(true)
    setError(null)
    try {
      onAbierto(await turnosApi.abrir({ fondo: monto }))
    } catch (e) {
      if (e.estado === 409) {
        onYaHabiaUno()
        return
      }
      setError(e.message)
      setEnviando(false)
    }
  }

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      titulo="Abrir turno"
      ancho={420}
      pie={
        <>
          <Boton variante="fantasma" onClick={onCerrar} disabled={enviando}>Cancelar</Boton>
          <Boton variante="primario" onClick={abrir} disabled={enviando}>
            {enviando ? 'Abriendo…' : 'Abrir turno'}
          </Boton>
        </>
      }
    >
      <Campo
        etiqueta="Fondo del cajón"
        requerido
        value={fondo}
        onChange={(e) => { setFondo(e.target.value); setError(null) }}
        onKeyDown={(e) => e.key === 'Enter' && abrir()}
        inputMode="numeric"
        placeholder="100000"
        autoComplete="off"
        error={(intentoAbrir && problema) || error}
        ayuda={monto != null
          ? `${formatoCOP(monto)} en el cajón al empezar${sugerido === fondo ? ' · el mismo fondo del turno anterior' : ''}`
          : 'Con cuánta plata arranca el cajón. Puede ser $0.'}
      />
    </Modal>
  )
}
