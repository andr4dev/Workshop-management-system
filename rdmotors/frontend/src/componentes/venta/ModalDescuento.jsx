import { useEffect, useRef, useState } from 'react'
import Modal from '../Modal'
import Boton from '../Boton'
import { formatoCOP, soloDigitos } from '../../utils/formato'
import { MOTIVOS_FRECUENTES, montoDescuento, porcentajeDesdeTexto } from '../../utils/venta'
import estilos from './ModalDescuento.module.css'

/**
 * Descuento sobre el total (spec 0003, H6, RF-015, RF-016 y RF-032).
 *
 * En pesos ("te lo dejo en $3.000 menos") o en porcentaje. Se ve cuánto queda antes de aplicarlo. El
 * motivo es obligatorio: queda en la auditoría con quién lo dio. Los motivos frecuentes se dan de un
 * toque.
 */
export default function ModalDescuento({ subtotal, actual, onAplicar, onCerrar }) {
  const [modo, setModo] = useState(actual?.modo ?? 'MONTO')
  const [valor, setValor] = useState(actual ? String(actual.valor) : '')
  const [motivo, setMotivo] = useState(actual?.motivo ?? '')
  const [intento, setIntento] = useState(false)
  const valorRef = useRef(null)

  useEffect(() => { valorRef.current?.focus() }, [modo])

  const numero = modo === 'MONTO' ? (soloDigitos(valor) === '' ? null : Number(soloDigitos(valor))) : porcentajeDesdeTexto(valor)
  const descuento = numero == null ? null : { modo, valor: numero, motivo: motivo.trim() }
  const monto = descuento ? montoDescuento(descuento, subtotal) : 0

  let problema = null
  if (numero == null || numero <= 0) problema = modo === 'MONTO' ? 'Escribe cuántos pesos' : 'Escribe el porcentaje'
  else if (modo === 'PORCENTAJE' && numero > 100) problema = 'El porcentaje va hasta 100'
  else if (modo === 'PORCENTAJE' && !/^\d+([.,]\d{1,2})?$/.test(valor.trim())) problema = 'El porcentaje admite hasta 2 decimales'
  else if (monto <= 0) problema = 'Ese descuento redondea a $0'
  else if (monto > subtotal) problema = 'El descuento no puede ser mayor que el total'
  else if (!motivo.trim()) problema = 'Escribe o elige el motivo: queda registrado con quién lo dio'

  function aplicar() {
    setIntento(true)
    if (problema) return
    onAplicar(descuento)
  }

  const alTeclear = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      aplicar()
    }
  }

  return (
    <Modal
      abierto
      onCerrar={onCerrar}
      titulo="Descuento"
      ancho={460}
      pie={
        <>
          {actual && <Boton variante="peligro" onClick={() => onAplicar(null)}>Quitar descuento</Boton>}
          <Boton variante="fantasma" onClick={onCerrar}>Cancelar</Boton>
          <Boton variante="primario" onClick={aplicar}>Aplicar</Boton>
        </>
      }
    >
      <div className={estilos.segmento} role="group" aria-label="Cómo se da el descuento">
        {[['MONTO', 'En pesos'], ['PORCENTAJE', 'En %']].map(([valorModo, texto]) => (
          <button key={valorModo} type="button" aria-pressed={modo === valorModo}
            className={modo === valorModo ? estilos.segmentoActivo : estilos.segmentoOpcion}
            onClick={() => { setModo(valorModo); setValor('') }}>
            {texto}
          </button>
        ))}
      </div>

      <label className={estilos.campo}>
        <span className={estilos.etiqueta}>{modo === 'MONTO' ? 'Cuántos pesos' : 'Porcentaje'}</span>
        <input ref={valorRef} className={estilos.entrada} value={valor} onChange={(e) => setValor(e.target.value)}
          onKeyDown={alTeclear} inputMode="decimal" placeholder={modo === 'MONTO' ? '3000' : '10'} autoComplete="off" />
      </label>

      <p className={estilos.resultado}>
        <span>Total {formatoCOP(subtotal)}</span>
        <span>− {formatoCOP(Math.max(0, monto))}</span>
        <strong>Queda {formatoCOP(Math.max(0, subtotal - monto))}</strong>
      </p>

      <div className={estilos.campo}>
        <span className={estilos.etiqueta}>Motivo</span>
        <div className={estilos.motivos}>
          {MOTIVOS_FRECUENTES.map((m) => (
            <button key={m} type="button" aria-pressed={motivo === m}
              className={motivo === m ? estilos.motivoElegido : estilos.motivo} onClick={() => setMotivo(m)}>
              {m}
            </button>
          ))}
        </div>
        <input className={estilos.entradaMotivo} value={motivo} onChange={(e) => setMotivo(e.target.value)}
          onKeyDown={alTeclear} maxLength={300} placeholder="U otro motivo" aria-label="Motivo del descuento" />
      </div>

      {intento && problema && <p className={estilos.problema} role="alert">{problema}</p>}
    </Modal>
  )
}
