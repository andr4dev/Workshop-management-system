import { useState } from 'react'
import Modal from '../Modal'
import Boton from '../Boton'
import Campo from '../Campo'
import { turnosApi } from '../../api/cliente'
import { fondoDesdeTexto, problemaDelFondo } from '../../utils/caja'
import { formatoCOP } from '../../utils/formato'

/**
 * Anotar con cuánto arrancó la cuenta de transferencias (Daviplata) en un turno que se abrió sin eso (2026-10-09).
 * Se anota una sola vez: desde ahí la caja dice cuánto debería haber en la cuenta.
 */
export default function ModalSaldoTransferencias({ turno, onAnotado, onCerrar }) {
  const [saldo, setSaldo] = useState('')
  const [intento, setIntento] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)

  const problema = problemaDelFondo(saldo)
  const monto = fondoDesdeTexto(saldo)

  async function anotar() {
    setIntento(true)
    if (problema) return
    setEnviando(true)
    setError(null)
    try {
      onAnotado(await turnosApi.anotarSaldoTransferencias(turno.id, monto))
    } catch (e) {
      setError(e.message)
      setEnviando(false)
    }
  }

  return (
    <Modal
      abierto
      onCerrar={onCerrar}
      titulo="Con cuánto arrancó la cuenta"
      ancho={440}
      pie={
        <>
          <Boton variante="fantasma" onClick={onCerrar} disabled={enviando}>Cancelar</Boton>
          <Boton variante="primario" onClick={anotar} disabled={enviando}>{enviando ? 'Anotando…' : 'Anotar'}</Boton>
        </>
      }
    >
      <Campo
        etiqueta="Saldo de transferencias al abrir el turno"
        requerido
        value={saldo}
        onChange={(e) => { setSaldo(e.target.value); setError(null) }}
        onKeyDown={(e) => e.key === 'Enter' && anotar()}
        inputMode="numeric"
        placeholder="599000"
        autoComplete="off"
        error={(intento && problema) || error}
        ayuda={monto != null
          ? `${formatoCOP(monto)} en Daviplata cuando se abrió el turno. Se anota una sola vez.`
          : 'Lo que había en Daviplata cuando se abrió el turno, antes de las ventas de hoy.'}
      />
    </Modal>
  )
}
