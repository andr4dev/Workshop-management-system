import { useState } from 'react'
import Modal from '../Modal'
import Boton from '../Boton'
import { abonosApi } from '../../api/cliente'
import { formatoCOP, idLocal, soloDigitos } from '../../utils/formato'
import { llaveNueva } from '../../utils/venta'
import { loMarcado, nombreDeLaDeuda, nombreDelProducto, paraAbonar } from '../../utils/cartera'
import estilos from './ModalAbono.module.css'

const FORMAS = [['EFECTIVO', 'Efectivo'], ['TRANSFERENCIA', 'Transferencia']]

/**
 * El cliente abona lo que trae (spec 0008, H4 y RF-011 a RF-013).
 *
 *   - Cualquier monto hasta lo que debe. El efectivo entra al cajón del turno; la transferencia, no.
 *   - Se aplica **a lo más viejo primero**, o primero a los productos que el cliente diga que paga (spec 0016): cada
 *     venta pendiente se ve con sus productos y lo que falta de cada uno; marcarlos llena el monto, que se puede
 *     cambiar. Si alcanza para menos, el último marcado queda a medias; si sobra, va a lo más viejo.
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
  const [marcados, setMarcados] = useState([])
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)
  const idSelector = idLocal()

  const grupos = paraAbonar(ficha.deudas)
  const marcado = loMarcado(grupos, marcados)

  // Marcar llena el monto con lo que falta de lo marcado; sin nada marcado, lo deja en blanco para escribirlo.
  function marcar(ids, si) {
    const nuevos = si ? [...new Set([...marcados, ...ids])] : marcados.filter((id) => !ids.includes(id))
    setMarcados(nuevos)
    const suma = loMarcado(grupos, nuevos).monto
    setMonto(suma > 0 ? String(suma) : '')
    setError(null)
  }
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
        primero: marcado.ids,
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

      {grupos.length > 0 && (
        <fieldset className={estilos.queSePaga} disabled={enviando}>
          <legend className={estilos.etiqueta}>Qué paga (opcional: sin marcar, a lo más viejo)</legend>
          {grupos.map((g) => {
            const ids = g.productos.map((d) => d.id)
            const todos = ids.every((id) => marcados.includes(id))
            return (
              <div key={g.clave} className={estilos.ventaPendiente}>
                <label className={estilos.ventaMarca}>
                  <input type="checkbox" checked={todos} onChange={(e) => marcar(ids, e.target.checked)} />
                  <span>{nombreDeLaDeuda(g)}</span>
                  <span className={estilos.faltan}>faltan {formatoCOP(g.pendiente)}</span>
                </label>
                {g.porProducto && g.productos.length > 0 && (
                  <ul className={estilos.productos}>
                    {g.productos.map((d) => (
                      <li key={d.id}>
                        <label className={estilos.productoMarca}>
                          <input type="checkbox" checked={marcados.includes(d.id)}
                            onChange={(e) => marcar([d.id], e.target.checked)} />
                          <span>{nombreDelProducto(d)}</span>
                          <span className={estilos.faltan}>{formatoCOP(d.pendiente)}</span>
                        </label>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )
          })}
          {marcado.ids.length > 0 && (
            <p className={estilos.marcado}>
              Lo marcado: <strong>{formatoCOP(marcado.monto)}</strong>
              {valor > 0 && valor < marcado.monto && ' · abona menos: el último queda a medias'}
              {valor > marcado.monto && ' · lo que sobra va a lo más viejo'}
            </p>
          )}
        </fieldset>
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
