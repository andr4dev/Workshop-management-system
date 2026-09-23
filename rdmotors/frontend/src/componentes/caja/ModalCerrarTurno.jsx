import { useState } from 'react'
import Modal from '../Modal'
import Boton from '../Boton'
import Campo from '../Campo'
import DesgloseArqueo from './DesgloseArqueo'
import { imprimirCierre } from './imprimirCierre'
import { turnosApi } from '../../api/cliente'
import {
  BILLETES, contadoDesdeTexto, diferenciaEnPalabras, MONEDAS, problemaDelContado, totalPorDenominaciones,
} from '../../utils/arqueo'
import { formatoCOP } from '../../utils/formato'
import { esteEquipoEsElMostrador } from '../../utils/imprimir'
import estilos from './Caja.module.css'

const TITULOS = { CONTAR: 'Cerrar turno', CONFIRMAR: '¿Cerrar el turno?', RESULTADO: 'Turno cerrado' }

/**
 * Cerrar el turno con lo contado (spec 0006, H3, RF-012 a RF-019), en tres pasos:
 *
 *   1. CONTAR: se ve lo que debería haber con su desglose (en vivo, del turno que se cargó), se escribe cuánto
 *      efectivo se contó —o se cuenta por billetes— y la diferencia sale mientras se escribe.
 *   2. CONFIRMAR: al confirmar el turno se cierra, y ya no se puede recontar.
 *   3. RESULTADO: las cifras que QUEDARON GUARDADAS. Pueden diferir de las del paso 1 si entró un cobro en el
 *      medio: el servidor calcula de nuevo con el turno bloqueado. Si no cuadró, se piden las observaciones (se
 *      pueden dejar para después). En el mostrador, el comprobante sale solo.
 *
 * Si otra pestaña o un doble clic ya lo había cerrado, se muestra ese cierre en vez de un error.
 *
 * @param turno     el detalle del turno abierto, con su `arqueo` en vivo
 * @param onCerrado se llama con el turno cerrado al salir del resultado
 */
export default function ModalCerrarTurno({ turno, onCerrado, onCerrar }) {
  const [paso, setPaso] = useState('CONTAR')
  const [contado, setContado] = useState('')
  const [porBilletes, setPorBilletes] = useState(false)
  const [conteo, setConteo] = useState({})
  const [intentoContar, setIntentoContar] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)
  const [cerrado, setCerrado] = useState(null)
  const [yaEstabaCerrado, setYaEstabaCerrado] = useState(false)
  const [impresion, setImpresion] = useState(null)
  const [observaciones, setObservaciones] = useState('')
  const [guardandoObservaciones, setGuardandoObservaciones] = useState(false)
  const [errorObservaciones, setErrorObservaciones] = useState(null)

  const billetes = totalPorDenominaciones(conteo)
  const textoContado = porBilletes ? String(billetes.total) : contado
  const problema = porBilletes && billetes.problemas.length > 0 ? billetes.problemas[0] : problemaDelContado(textoContado)
  const monto = contadoDesdeTexto(textoContado)

  function revisar() {
    setIntentoContar(true)
    if (problema) return
    setError(null)
    setPaso('CONFIRMAR')
  }

  async function mostrarResultado(detalle, yaEstaba) {
    setCerrado(detalle)
    setYaEstabaCerrado(yaEstaba)
    setPaso('RESULTADO')
    // El comprobante sale solo en el mostrador; en otro equipo se reimprime desde Turnos anteriores.
    if (yaEstaba) return
    if (!esteEquipoEsElMostrador()) {
      setImpresion('OTRO_EQUIPO')
      return
    }
    setImpresion('ENVIANDO')
    setImpresion(await imprimirCierre(detalle) ? 'ENVIADO' : 'FALLO')
  }

  async function cerrar() {
    setEnviando(true)
    setError(null)
    try {
      await mostrarResultado(await turnosApi.cerrar(turno.id, monto), false)
    } catch (e) {
      if (e.esTurnoCerrado) {
        try {
          await mostrarResultado(await turnosApi.detalle(turno.id), true)
        } catch (otro) {
          setError(otro.message)
        }
      } else {
        setError(e.estado === 0
          ? 'No hay conexión con el servidor. Intenta de nuevo: si el turno alcanzó a cerrarse, se muestra ese cierre.'
          : e.message)
      }
    } finally {
      setEnviando(false)
    }
  }

  async function reimprimir() {
    setImpresion('ENVIANDO')
    setImpresion(await imprimirCierre(cerrado) ? 'ENVIADO' : 'FALLO')
  }

  async function guardarObservaciones() {
    if (!observaciones.trim()) {
      setErrorObservaciones('Escribe qué pudo pasar')
      return
    }
    setGuardandoObservaciones(true)
    setErrorObservaciones(null)
    try {
      setCerrado(await turnosApi.observaciones(cerrado.id, observaciones.trim()))
    } catch (e) {
      setErrorObservaciones(e.message)
    } finally {
      setGuardandoObservaciones(false)
    }
  }

  const salir = () => {
    if (enviando || guardandoObservaciones) return
    if (paso === 'RESULTADO') onCerrado(cerrado)
    else onCerrar()
  }

  const pie = {
    CONTAR: (
      <>
        <Boton variante="fantasma" onClick={salir}>Cancelar</Boton>
        <Boton variante="primario" onClick={revisar}>Cerrar turno</Boton>
      </>
    ),
    CONFIRMAR: (
      <>
        <Boton variante="fantasma" onClick={() => setPaso('CONTAR')} disabled={enviando}>Volver a contar</Boton>
        <Boton variante="peligro" onClick={cerrar} disabled={enviando}>{enviando ? 'Cerrando…' : 'Sí, cerrar el turno'}</Boton>
      </>
    ),
    RESULTADO: (
      <>
        <Boton variante="secundario" onClick={reimprimir} disabled={impresion === 'ENVIANDO'}>
          {impresion === 'ENVIADO' || impresion === 'FALLO' ? 'Reimprimir' : 'Imprimir'}
        </Boton>
        <Boton variante="primario" onClick={salir} disabled={guardandoObservaciones}>Listo</Boton>
      </>
    ),
  }[paso]

  return (
    <Modal abierto onCerrar={salir} titulo={TITULOS[paso]} ancho={paso === 'RESULTADO' ? 620 : 520} pie={pie}>
      {paso === 'CONTAR' && (
        <div className={estilos.formulario}>
          <DesgloseArqueo turno={turno} contado={problema ? null : monto} />
          <p className={estilos.nota}>Cuenta los billetes y monedas del cajón y escribe cuánto hay, fondo incluido.</p>
          {porBilletes ? (
            <>
              <div className={estilos.billetes}>
                {[...BILLETES, ...MONEDAS].map((valor) => (
                  <Campo key={valor} etiqueta={formatoCOP(valor)} value={conteo[valor] ?? ''} inputMode="numeric"
                    autoComplete="off" placeholder="0"
                    onChange={(e) => setConteo((c) => ({ ...c, [valor]: e.target.value }))} />
                ))}
              </div>
              <p className={estilos.nota}>
                Total contado: <strong>{formatoCOP(billetes.total)}</strong>
                {' · '}
                <button type="button" className={estilos.enlace} onClick={() => { setPorBilletes(false); setContado(String(billetes.total)) }}>
                  Escribir el total
                </button>
              </p>
              {intentoContar && problema && <p className={estilos.error} role="alert"><span aria-hidden>⚠</span> {problema}</p>}
            </>
          ) : (
            <>
              <Campo
                etiqueta="¿Cuánto efectivo contaste?"
                requerido
                value={contado}
                onChange={(e) => setContado(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && revisar()}
                inputMode="numeric"
                placeholder="87000"
                autoComplete="off"
                error={intentoContar ? problema : null}
                ayuda={monto != null ? formatoCOP(monto) : 'Todo el efectivo del cajón, fondo incluido. Puede ser $0.'}
              />
              <p className={estilos.nota}>
                <button type="button" className={estilos.enlace} onClick={() => setPorBilletes(true)}>
                  Contar por billetes
                </button>
              </p>
            </>
          )}
        </div>
      )}

      {paso === 'CONFIRMAR' && (
        <div className={estilos.formulario}>
          <p>
            Vas a cerrar el turno con <strong>{formatoCOP(monto)}</strong> contados
            {turno.arqueo ? <>: <strong>{diferenciaEnPalabras(monto - turno.arqueo.esperado).texto.toLowerCase()}</strong></> : ''}.
            Después no se puede recontar, ni vender, anular o registrar gastos en este turno.
          </p>
          {error && <p className={estilos.error} role="alert"><span aria-hidden>⚠</span> {error}</p>}
        </div>
      )}

      {paso === 'RESULTADO' && cerrado?.cierre && (
        <div className={estilos.formulario}>
          {yaEstabaCerrado && (
            <p className={estilos.nota}>Este turno ya se había cerrado: este es ese cierre.</p>
          )}
          <DesgloseArqueo turno={cerrado} />

          {cerrado.observaciones ? (
            <Campo etiqueta="Observaciones">
              <p className={estilos.observaciones}>{cerrado.observaciones}</p>
            </Campo>
          ) : cerrado.cierre.diferencia !== 0 && (
            <>
              <Campo etiqueta="¿Qué pudo pasar?" error={errorObservaciones}
                ayuda="Queda con el cierre y se escribe una sola vez. Si la dejas para después, el cierre queda marcado «sin explicación».">
                <textarea className={estilos.textarea} rows={3} maxLength={500} value={observaciones}
                  placeholder="Se dio mal un cambio de $2.000"
                  onChange={(e) => { setObservaciones(e.target.value); setErrorObservaciones(null) }} />
              </Campo>
              <div className={estilos.acciones}>
                <Boton variante="secundario" onClick={guardarObservaciones} disabled={guardandoObservaciones}>
                  {guardandoObservaciones ? 'Guardando…' : 'Guardar observaciones'}
                </Boton>
              </div>
            </>
          )}

          {impresion === 'ENVIANDO' && <p className={estilos.nota} role="status">Imprimiendo el comprobante…</p>}
          {impresion === 'ENVIADO' && <p className={estilos.enviado} role="status">Comprobante enviado a la impresora</p>}
          {impresion === 'FALLO' && (
            <p className={estilos.fallo} role="alert">
              <span aria-hidden>⚠</span> No se pudo imprimir el comprobante. El turno quedó cerrado: usa Reimprimir.
            </p>
          )}
          {impresion === 'OTRO_EQUIPO' && (
            <p className={estilos.nota}>El comprobante se imprime desde el computador del mostrador, en Turnos anteriores.</p>
          )}
        </div>
      )}
    </Modal>
  )
}
