import { useState } from 'react'
import Modal from '../Modal'
import Boton from '../Boton'
import { abonosApi } from '../../api/cliente'
import { formatoCOP, idLocal, soloDigitos } from '../../utils/formato'
import { llaveNueva } from '../../utils/venta'
import { nombreDeLaDeuda } from '../../utils/cartera'
import estilos from './ModalAbono.module.css'

const FORMAS = [['EFECTIVO', 'Efectivo'], ['TRANSFERENCIA', 'Transferencia']]

/**
 * El cliente abona lo que trae (spec 0008, H4 y RF-011 a RF-013).
 *
 *   - Cualquier monto hasta lo que debe. El efectivo entra al cajón del turno; la transferencia, no.
 *   - Se aplica **a lo más viejo primero**, o a la venta que el cliente diga.
 *   - Lleva llave: un doble clic no abona dos veces, y si la red se cae se reintenta el mismo abono.
 *
 * @param ficha la del cliente, como la devuelve el servidor
 */
export default function ModalAbono({ ficha, onAbonado, onCerrar }) {
  const [llave] = useState(() => llaveNueva())
  const [monto, setMonto] = useState('')
  const [forma, setForma] = useState('EFECTIVO')
  const [referencia, setReferencia] = useState('')
  const [nota, setNota] = useState('')
  const [primeroA, setPrimeroA] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)
  const idSelector = idLocal()

  const pendientes = ficha.deudas.filter((d) => d.pendiente > 0)
  const valor = Number(soloDigitos(monto)) || 0
  const problema = valor <= 0 ? 'Escribe cuánto abona'
    : valor > ficha.debe ? `${ficha.cliente.nombre} debe ${formatoCOP(ficha.debe)}: no se le puede recibir más`
      : null

  async function abonar() {
    if (problema || enviando) return
    setEnviando(true)
    setError(null)
    try {
      onAbonado(await abonosApi.registrar({
        llave,
        clienteId: ficha.cliente.id,
        monto: valor,
        forma,
        referencia: forma === 'TRANSFERENCIA' && referencia.trim() ? referencia.trim() : null,
        nota: nota.trim() || null,
        primeroA: primeroA || null,
      }))
    } catch (e) {
      setError(e)
    } finally {
      setEnviando(false)
    }
  }

  const alTeclear = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      abonar()
    }
  }

  const queda = Math.max(0, ficha.debe - valor)

  return (
    <Modal
      abierto
      onCerrar={onCerrar}
      titulo={`Abono de ${ficha.cliente.nombre}`}
      ancho={520}
      pie={
        <>
          <Boton variante="fantasma" onClick={onCerrar} disabled={enviando}>Cancelar</Boton>
          <Boton variante="primario" tamano="grande" onClick={abonar} disabled={Boolean(problema) || enviando}>
            {enviando ? 'Registrando…' : error?.estado === 0 ? 'Reintentar abono' : `Recibir ${formatoCOP(valor)}`}
          </Boton>
        </>
      }
    >
      <p className={estilos.debe}>
        <span>Debe</span>
        <strong>{formatoCOP(ficha.debe)}</strong>
      </p>

      <div className={estilos.campo}>
        <label className={estilos.etiqueta} htmlFor={`abono-monto-${idSelector}`}>Cuánto abona</label>
        <input id={`abono-monto-${idSelector}`} className={estilos.entrada} inputMode="numeric" value={monto}
          onChange={(e) => { setMonto(soloDigitos(e.target.value)); setError(null) }} onKeyDown={alTeclear}
          placeholder="0" autoComplete="off" autoFocus disabled={enviando} />
        <button type="button" className={estilos.todo} onClick={() => setMonto(String(ficha.debe))} disabled={enviando}>
          Todo lo que debe ({formatoCOP(ficha.debe)})
        </button>
      </div>

      <div className={estilos.campo}>
        <span className={estilos.etiqueta}>Cómo paga</span>
        <div className={estilos.segmento} role="group" aria-label="Cómo paga el abono">
          {FORMAS.map(([valorForma, texto]) => (
            <button key={valorForma} type="button" aria-pressed={forma === valorForma}
              className={forma === valorForma ? estilos.segmentoActivo : estilos.segmentoOpcion}
              onClick={() => setForma(valorForma)} disabled={enviando}>
              {texto}
            </button>
          ))}
        </div>
      </div>

      {forma === 'TRANSFERENCIA' && (
        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor={`abono-ref-${idSelector}`}>Referencia (opcional)</label>
          <input id={`abono-ref-${idSelector}`} className={estilos.entrada} value={referencia}
            onChange={(e) => setReferencia(e.target.value)} onKeyDown={alTeclear}
            placeholder="N.º de transacción" autoComplete="off" disabled={enviando} />
        </div>
      )}

      {pendientes.length > 1 && (
        <div className={estilos.campo}>
          <label className={estilos.etiqueta} htmlFor={`abono-deuda-${idSelector}`}>A cuál se aplica</label>
          <select id={`abono-deuda-${idSelector}`} className={estilos.entrada} value={primeroA}
            onChange={(e) => setPrimeroA(e.target.value)} disabled={enviando}>
            <option value="">A lo más viejo primero</option>
            {pendientes.map((d) => (
              <option key={d.id} value={d.id}>
                {nombreDeLaDeuda(d)} · faltan {formatoCOP(d.pendiente)}
              </option>
            ))}
          </select>
        </div>
      )}

      <div className={estilos.campo}>
        <label className={estilos.etiqueta} htmlFor={`abono-nota-${idSelector}`}>Nota (opcional)</label>
        <input id={`abono-nota-${idSelector}`} className={estilos.entrada} value={nota}
          onChange={(e) => setNota(e.target.value)} onKeyDown={alTeclear}
          placeholder="Ej.: abona el viernes lo que falta" autoComplete="off" disabled={enviando} />
      </div>

      <p className={estilos.queda} aria-live="polite">
        <span>Queda debiendo</span>
        <strong>{formatoCOP(queda)}</strong>
      </p>

      {problema && monto !== '' && <p className={estilos.problema}>{problema}</p>}
      {error && (
        <p className={error.estado === 0 ? estilos.sinConexion : estilos.error} role="alert">
          <span aria-hidden>⚠</span> {error.estado === 0
            ? 'No hubo respuesta del servidor y no se sabe si el abono quedó. Reintentar no abona dos veces.'
            : error.message}
        </p>
      )}
    </Modal>
  )
}
