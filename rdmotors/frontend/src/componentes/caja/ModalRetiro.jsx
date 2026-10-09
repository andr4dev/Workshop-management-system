import { useState } from 'react'
import Modal from '../Modal'
import Boton from '../Boton'
import Campo from '../Campo'
import AvisoConfirmarMonto from './AvisoConfirmarMonto'
import Segmento from './Segmento'
import { retirosApi } from '../../api/cliente'
import {
  comandoDelRetiro, montoDesdeTexto, problemasDelRetiro, sinProblemas, TIPOS_DE_RETIRO,
} from '../../utils/gastos'
import { formatoCOP } from '../../utils/formato'
import { llaveNueva } from '../../utils/venta'
import estilos from './Caja.module.css'

/**
 * Registrar un retiro (spec 0006, H2): el dueño se lleva plata del cajón. No es un gasto: no cuenta como gasto
 * en ningún reporte. Siempre con motivo: un retiro sin razón tapa cualquier faltante.
 *
 * El padre lo monta solo al abrir: cada apertura arranca limpia, con una llave nueva.
 */
export default function ModalRetiro({ onRegistrado, onCerrar }) {
  const [llave] = useState(() => llaveNueva())
  const [retiro, setRetiro] = useState({ monto: '', motivo: '', tipo: 'DEL_CAJON' })
  const [intentoRegistrar, setIntentoRegistrar] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)
  const [pideConfirmar, setPideConfirmar] = useState(false)

  const problemas = problemasDelRetiro(retiro)
  const monto = montoDesdeTexto(retiro.monto)

  function cambiar(cambios) {
    setRetiro((r) => ({ ...r, ...cambios }))
    setError(null)
    setPideConfirmar(false)
  }

  async function registrar(confirmado = false) {
    setIntentoRegistrar(true)
    if (!sinProblemas(problemas)) return
    setEnviando(true)
    setError(null)
    try {
      onRegistrado(await retirosApi.registrar(comandoDelRetiro(retiro, llave, confirmado)))
    } catch (e) {
      if (e.esConfirmarMonto) setPideConfirmar(true)
      else setError(e.estado === 0 ? 'No hay conexión con el servidor. Intenta de nuevo: el retiro no queda dos veces.' : e.message)
      setEnviando(false)
    }
  }

  return (
    <Modal
      abierto
      onCerrar={onCerrar}
      titulo="Registrar retiro"
      ancho={460}
      pie={
        <>
          <Boton variante="fantasma" onClick={onCerrar} disabled={enviando}>Cancelar</Boton>
          <Boton variante="primario" onClick={() => registrar(false)} disabled={enviando || pideConfirmar}>
            {enviando && !pideConfirmar ? 'Registrando…' : 'Registrar retiro'}
          </Boton>
        </>
      }
    >
      <div className={estilos.formulario}>
        <Campo etiqueta="De dónde sale" requerido>
          <Segmento
            etiqueta="De dónde sale"
            valor={retiro.tipo}
            opciones={TIPOS_DE_RETIRO.map(([valor, texto]) => [valor, texto])}
            onCambio={(tipo) => cambiar({ tipo })}
          />
        </Campo>
        <p className={estilos.nota}>{TIPOS_DE_RETIRO.find(([valor]) => valor === retiro.tipo)?.[2]}</p>
        <Campo
          etiqueta="Monto"
          requerido
          value={retiro.monto}
          onChange={(e) => cambiar({ monto: e.target.value })}
          inputMode="numeric"
          placeholder="100000"
          autoComplete="off"
          error={intentoRegistrar ? problemas.monto : null}
          ayuda={monto ? formatoCOP(monto) : null}
        />
        <Campo
          etiqueta="Motivo"
          requerido
          value={retiro.motivo}
          maxLength={300}
          onChange={(e) => cambiar({ motivo: e.target.value })}
          onKeyDown={(e) => e.key === 'Enter' && !pideConfirmar && registrar(false)}
          placeholder="Se lo llevó don Rubén"
          autoComplete="off"
          error={intentoRegistrar ? problemas.motivo : null}
          ayuda="Quién se la llevó o para qué"
        />
        {pideConfirmar && (
          <AvisoConfirmarMonto enviando={enviando} onRevisar={() => setPideConfirmar(false)}
            onConfirmar={() => registrar(true)} />
        )}
        {error && <p className={estilos.error} role="alert"><span aria-hidden>⚠</span> {error}</p>}
      </div>
    </Modal>
  )
}
