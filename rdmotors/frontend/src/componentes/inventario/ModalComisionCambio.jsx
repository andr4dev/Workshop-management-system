import { useState } from 'react'
import Modal from '../Modal'
import Boton from '../Boton'
import Campo from '../Campo'
import { repuestosApi } from '../../api/cliente'
import { formatoCOP } from '../../utils/formato'
import { COMISION_DE_ENTRADA, problemaDeComision } from '../../utils/inventario'

/**
 * Si el repuesto paga comisión por cambio de aceite, y cuánto (spec 0015, RF-001). Es del administrador.
 *
 * Cambiarlo no toca las comisiones que ya se registraron: cada una guarda el monto de su cobro.
 *
 * @param repuesto la ficha, con `comisionCambio` (o `null` si no paga)
 */
export default function ModalComisionCambio({ repuesto, onGuardado, onCerrar }) {
  const [paga, setPaga] = useState(repuesto.comisionCambio != null)
  const [monto, setMonto] = useState(String(repuesto.comisionCambio ?? COMISION_DE_ENTRADA))
  const [intentado, setIntentado] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)
  const problema = paga ? problemaDeComision(monto) : null

  async function guardar() {
    setIntentado(true)
    if (problema) return
    setEnviando(true)
    setError(null)
    try {
      onGuardado(await repuestosApi.cambiarComision(repuesto.id, paga ? Number(monto.replace(/\D/g, '')) : null))
    } catch (e) {
      setError(e)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Comisión por cambio de aceite" ancho={460}
      pie={
        <>
          <Boton variante="fantasma" onClick={onCerrar} disabled={enviando}>Cancelar</Boton>
          <Boton variante="primario" onClick={guardar} disabled={enviando}>{enviando ? 'Guardando…' : 'Guardar'}</Boton>
        </>
      }>
      <p style={{ marginTop: 0 }}>
        Cuando se vende <strong>{repuesto.nombre}</strong> y se cambia en la tienda, esto es para quien hizo el cambio,
        por cada unidad.
      </p>
      <label style={{ display: 'flex', gap: 'var(--esp-2)', alignItems: 'center', marginBottom: 'var(--esp-3)' }}>
        <input type="checkbox" checked={paga} disabled={enviando} onChange={(e) => setPaga(e.target.checked)} />
        Paga comisión por cambio
      </label>
      {paga && (
        <Campo etiqueta="Por cada cambio" requerido value={monto} inputMode="numeric" autoComplete="off"
          disabled={enviando} onChange={(e) => setMonto(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); guardar() } }}
          error={intentado ? problema : null}
          ayuda={problema ? null : formatoCOP(Number(monto.replace(/\D/g, '')))} />
      )}
      {error && <p role="alert" style={{ color: 'var(--danger)' }}><span aria-hidden>⚠</span> {error.message}</p>}
    </Modal>
  )
}
