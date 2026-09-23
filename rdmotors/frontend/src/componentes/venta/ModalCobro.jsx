import { useEffect, useRef, useState } from 'react'
import Modal from '../Modal'
import Boton from '../Boton'
import SelectorCliente from '../clientes/SelectorCliente'
import { formatoCOP, soloDigitos } from '../../utils/formato'
import {
  billetesSugeridos, cambioDelCobro, fiadoDelCobro, pagosDelCobro, problemaDelCobro,
} from '../../utils/venta'
import estilos from './ModalCobro.module.css'

const FORMAS = [
  ['EFECTIVO', 'Efectivo'],
  ['TRANSFERENCIA', 'Transferencia'],
  ['MIXTO', 'Mixto'],
  ['FIADO', 'Fiado'],
]

/**
 * Cobrar (spec 0003, H3, RF-010, RF-011 y RF-031).
 *
 * Arranca en <b>efectivo</b>, con el cursor en "Recibido" y botones con los billetes que tiene sentido
 * ofrecer: el cambio sale solo, sin teclear. Enter cobra.
 *
 * Si la red se cae en el medio, no se sabe si se cobró: el modal lo dice y solo ofrece reintentar lo
 * mismo. El padre bloquea la venta hasta tener una respuesta.
 *
 * <b>Fiado</b> (spec 0008): a quién —con su cédula y su celular, o no se cierra—, cuánto paga ahora si paga algo, y
 * cuánto queda debiendo. El cliente se escoge o se crea aquí mismo, sin salir de la venta.
 *
 * @param cobroAnterior  el cobro de un intento que quedó sin respuesta: se reintenta exactamente ese
 */
export default function ModalCobro({ total, cobroAnterior, onCobrar, onCerrar, enviando, error, sinRespuesta }) {
  const [cobro, setCobro] = useState(cobroAnterior
    ?? { forma: 'EFECTIVO', recibido: '', efectivo: '', cliente: null, pagaAhora: '', formaPagaAhora: 'EFECTIVO' })
  const [mostrandoCliente, setMostrandoCliente] = useState(false)
  const recibidoRef = useRef(null)
  const efectivoRef = useRef(null)
  const cobrarRef = useRef(null)

  const problema = problemaDelCobro(cobro, total)
  const cambio = cambioDelCobro(cobro, total)
  const pagos = pagosDelCobro(cobro, total)
  const fiado = fiadoDelCobro(cobro, total)
  const fia = cobro.forma === 'FIADO'
  const bloqueado = enviando || sinRespuesta

  // El Modal enfoca el primer botón; aquí se lleva el cursor a donde se escribe. Corre después del
  // efecto del Modal porque el Modal es hijo de este componente.
  // En transferencia no hay nada que escribir: el foco va a "Cobrar" y Enter cobra. En fiado, el buscador de
  // clientes se lleva el cursor solo.
  // Mientras se manda, todo está deshabilitado y el cursor se pierde; cuando vuelve la respuesta se
  // devuelve: al campo si hay que corregir algo, o a "Reintentar cobro" si no hubo respuesta.
  useEffect(() => {
    if (enviando || (cobro.forma === 'FIADO' && !sinRespuesta)) return
    const destino = sinRespuesta
      ? cobrarRef
      : { MIXTO: efectivoRef, TRANSFERENCIA: cobrarRef }[cobro.forma] ?? recibidoRef
    ;(destino.current ?? cobrarRef.current)?.focus()
  }, [cobro.forma, enviando, sinRespuesta])

  const cambiar = (campos) => setCobro((c) => ({ ...c, ...campos }))

  function cobrar() {
    if (problema || enviando) return
    onCobrar(cobro)
  }

  const alTeclear = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      cobrar()
    }
  }

  const transferencia = pagos.find((p) => p.forma === 'TRANSFERENCIA')?.monto ?? 0
  const pagaAhora = pagos.reduce((s, p) => s + p.monto, 0)
  const textoDelBoton = enviando ? 'Cobrando…'
    : sinRespuesta ? 'Reintentar cobro'
      : total === 0 ? 'Registrar venta'
        : fia ? (pagaAhora > 0 ? `Cobrar ${formatoCOP(pagaAhora)} y fiar ${formatoCOP(fiado)}` : `Fiar ${formatoCOP(fiado)}`)
          : `Cobrar ${formatoCOP(total)}`

  return (
    <Modal
      abierto
      onCerrar={onCerrar}
      titulo="Cobrar"
      ancho={fia ? 560 : 480}
      pie={
        <>
          <Boton variante="fantasma" onClick={onCerrar} disabled={enviando}>Cancelar</Boton>
          <Boton ref={cobrarRef} variante="primario" tamano="grande" onClick={cobrar}
            disabled={Boolean(problema) || enviando}>
            {textoDelBoton}
          </Boton>
        </>
      }
    >
      <p className={estilos.total}>
        <span className={estilos.totalEtiqueta}>Total a pagar</span>
        <span className={estilos.totalValor}>{formatoCOP(total)}</span>
      </p>

      {total > 0 && (
        <>
          <div className={estilos.segmento} role="group" aria-label="Forma de pago">
            {FORMAS.map(([valor, texto]) => (
              <button
                key={valor}
                type="button"
                aria-pressed={cobro.forma === valor}
                className={cobro.forma === valor ? estilos.segmentoActivo : estilos.segmentoOpcion}
                onClick={() => cambiar({ forma: valor })}
                disabled={bloqueado}
              >
                {texto}
              </button>
            ))}
          </div>

          {cobro.forma === 'MIXTO' && (
            <label className={estilos.campo}>
              <span className={estilos.etiqueta}>Parte en efectivo</span>
              <input ref={efectivoRef} className={estilos.entrada} inputMode="numeric" value={cobro.efectivo}
                onChange={(e) => cambiar({ efectivo: soloDigitos(e.target.value) })} onKeyDown={alTeclear}
                placeholder="20000" autoComplete="off" disabled={bloqueado} />
              <span className={estilos.ayuda}>
                Por transferencia: <strong>{formatoCOP(transferencia)}</strong>
              </span>
            </label>
          )}

          {fia && (
            <>
              <SelectorCliente cliente={cobro.cliente} onCambiar={(cliente) => cambiar({ cliente })} paraFiar
                deshabilitado={bloqueado} />

              <div className={estilos.campo}>
                <label className={estilos.etiqueta} htmlFor="paga-ahora">Paga ahora (opcional)</label>
                <div className={estilos.pagaAhora}>
                  <input id="paga-ahora" className={estilos.entrada} inputMode="numeric" value={cobro.pagaAhora ?? ''}
                    onChange={(e) => cambiar({ pagaAhora: soloDigitos(e.target.value) })} onKeyDown={alTeclear}
                    placeholder="0" autoComplete="off" disabled={bloqueado} />
                  <div className={estilos.segmentoChico} role="group" aria-label="Cómo paga lo de ahora">
                    {[['EFECTIVO', 'Efectivo'], ['TRANSFERENCIA', 'Transferencia']].map(([valor, texto]) => (
                      <button key={valor} type="button" aria-pressed={(cobro.formaPagaAhora ?? 'EFECTIVO') === valor}
                        className={(cobro.formaPagaAhora ?? 'EFECTIVO') === valor ? estilos.segmentoActivo : estilos.segmentoOpcion}
                        onClick={() => cambiar({ formaPagaAhora: valor })} disabled={bloqueado}>
                        {texto}
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              <p className={estilos.fiado} aria-live="polite">
                <span>Queda fiado</span>
                <strong>{formatoCOP(fiado)}</strong>
              </p>
              {cobro.cliente && fiado > 0 && (
                <p className={estilos.nota}>
                  {cobro.cliente.nombre} quedará debiendo <strong>{formatoCOP(cobro.cliente.debe + fiado)}</strong> en
                  total.
                </p>
              )}
            </>
          )}

          {/* A nombre de quién, en una venta de contado (spec 0008, RF-023): opcional, para su historial. */}
          {!fia && (
            cobro.cliente
              ? (
                <p className={estilos.aNombreDe}>
                  A nombre de <strong>{cobro.cliente.nombre}</strong>
                  <button type="button" className={estilos.quitarCliente} onClick={() => cambiar({ cliente: null })}
                    disabled={bloqueado}>Quitar</button>
                </p>
              )
              : mostrandoCliente
                ? <SelectorCliente cliente={null} onCambiar={(cliente) => cambiar({ cliente })} deshabilitado={bloqueado} />
                : (
                  <button type="button" className={estilos.aNombreDeEnlace} onClick={() => setMostrandoCliente(true)}
                    disabled={bloqueado}>
                    A nombre de un cliente (opcional)
                  </button>
                )
          )}

          {!fia && cobro.forma !== 'TRANSFERENCIA' && (
            <label className={estilos.campo}>
              <span className={estilos.etiqueta}>Con cuánto paga en efectivo</span>
              <input ref={recibidoRef} className={estilos.entrada} inputMode="numeric" value={cobro.recibido}
                onChange={(e) => cambiar({ recibido: soloDigitos(e.target.value) })} onKeyDown={alTeclear}
                placeholder="Opcional: para el cambio" autoComplete="off" disabled={bloqueado} />
            </label>
          )}

          {cobro.forma === 'EFECTIVO' && (
            <div className={estilos.billetes} aria-label="Billetes">
              {billetesSugeridos(total).map((billete) => (
                <button key={billete} type="button" className={estilos.billete} disabled={bloqueado}
                  onClick={() => cambiar({ recibido: String(billete) })}>
                  {billete === total ? 'Exacto' : formatoCOP(billete)}
                </button>
              ))}
            </div>
          )}

          {cobro.forma === 'TRANSFERENCIA' && (
            <p className={estilos.nota}>
              Verifica que la transferencia o el QR llegó antes de cobrar: el sistema no lo comprueba.
            </p>
          )}

          {/* Por transferencia no hay vuelto que contar, y en fiado no se pide con cuánto paga. */}
          {cobro.forma !== 'TRANSFERENCIA' && !fia && (
            <p className={estilos.cambio} aria-live="polite">
              <span>Cambio</span>
              <strong>{formatoCOP(cambio)}</strong>
            </p>
          )}
        </>
      )}

      {total === 0 && <p className={estilos.nota}>El descuento deja la venta en $0: no hay nada que cobrar.</p>}

      {/* Solo cuando ya se escribió algo: antes de escribir, el botón deshabilitado basta. */}
      {/* En fiado, a quién y qué le falta lo dice el buscador; aquí solo lo de lo que paga ahora. */}
      {problema && (fia ? Boolean(cobro.pagaAhora) && Boolean(cobro.cliente) : cobro.recibido || cobro.efectivo)
        && <p className={estilos.problema}>{problema}</p>}

      {sinRespuesta && (
        <p className={estilos.sinRespuesta} role="alert">
          <span aria-hidden>⚠</span> No hubo respuesta del servidor y no se sabe si la venta quedó cobrada.
          Reintentar cobra esta misma venta sin duplicarla.
        </p>
      )}
      {error && !sinRespuesta && <p className={estilos.error} role="alert"><span aria-hidden>⚠</span> {error}</p>}
    </Modal>
  )
}
