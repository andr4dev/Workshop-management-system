import Boton from '../Boton'
import { diferenciaEnPalabras } from '../../utils/arqueo'
import { fechaHora, formatoCOP } from '../../utils/formato'
import { filasDeTransferencias, problemasDeTransferencias, salidasDeTransferencias } from '../../utils/transferencias'
import estilos from './Caja.module.css'

const CLASE = { FALTANTE: 'cifraFaltante', SOBRANTE: 'cifraSobrante', CUADRA: 'cifraCuadra' }
const ICONO = { FALTANTE: '⚠', SOBRANTE: '⚠', CUADRA: '✓' }

/**
 * Lo que debería haber en la cuenta de transferencias (Daviplata) y de dónde sale cada peso (2026-10-09). Es el
 * gemelo de `DesgloseArqueo` para la plata que no está en el cajón: suma lo que entró por transferencia y resta lo
 * que se pagó desde la cuenta, con la lista de esas salidas.
 *
 *   - Turno ABIERTO: en vivo. Con `contado`, la diferencia sale mientras se escribe, antes de cerrar.
 *   - Turno CERRADO: lo que quedó guardado al cerrar, con lo que dijo la cuenta.
 *
 * @param contado       lo que dice la cuenta, mientras se escribe en el cierre; o null
 * @param onAnotarSaldo si llega, y el turno no anotó con cuánto arrancó la cuenta, se ofrece anotarlo
 */
export default function CuadreTransferencias({ turno, contado = null, onAnotarSaldo = null, conSalidas = true }) {
  const t = turno.transferencias
  if (!t) return null
  const cerrado = turno.cierre != null
  const contadoMostrado = cerrado ? t.contado : contado
  const diferencia = t.esperado == null || contadoMostrado == null ? null
    : diferenciaEnPalabras(cerrado ? t.diferencia : contadoMostrado - t.esperado)
  const filas = filasDeTransferencias(t)
  const problemas = problemasDeTransferencias(t)
  const salidas = conSalidas ? salidasDeTransferencias(turno) : []

  return (
    <>
      {t.saldoInicial == null && (
        <div className={estilos.nota}>
          <p>
            No se anotó con cuánto arrancó la cuenta en este turno: se ve lo que entró y salió, pero no cuánto debería
            haber.
          </p>
          {onAnotarSaldo && !cerrado && (
            <Boton variante="secundario" tamano="chico" onClick={onAnotarSaldo}>Anotar con cuánto arrancó</Boton>
          )}
        </div>
      )}

      {t.esperado != null && (
        <div className={estilos.cifras}>
          <div className={estilos.cifra}>
            <span className={estilos.cifraEtiqueta}>{cerrado ? 'Debería haber' : 'Debería haber ahora'}</span>
            <span className={estilos.cifraValor}>{formatoCOP(t.esperado)}</span>
          </div>
          {diferencia && (
            <>
              <div className={estilos.cifra}>
                <span className={estilos.cifraEtiqueta}>La cuenta dice</span>
                <span className={estilos.cifraValor}>{formatoCOP(contadoMostrado)}</span>
              </div>
              <div className={estilos[CLASE[diferencia.tipo]]}>
                <span className={estilos.cifraEtiqueta}>Diferencia</span>
                <span className={estilos.cifraValor}><span aria-hidden>{ICONO[diferencia.tipo]}</span> {diferencia.texto}</span>
              </div>
            </>
          )}
        </div>
      )}

      <table className={estilos.desglose} aria-label="De dónde sale lo que debería haber en transferencias">
        <tbody>
          {filas.map((f) => (
            <tr key={f.clave} className={f.signo < 0 ? estilos.resta : undefined}>
              <td>{f.etiqueta}</td>
              <td>{f.clave === 'saldoInicial' ? '' : f.signo < 0 ? '− ' : '+ '}{formatoCOP(f.monto)}</td>
            </tr>
          ))}
          {t.esperado != null && (
            <tr className={estilos.desgloseTotal}>
              <td>Debería haber</td>
              <td>{formatoCOP(t.esperado)}</td>
            </tr>
          )}
        </tbody>
      </table>

      {salidas.length > 0 && (
        <table className={estilos.desglose} aria-label="Lo que movió la cuenta de transferencias">
          <tbody>
            {salidas.map((s) => (
              <tr key={s.id} className={s.anulado ? estilos.anulado : s.signo < 0 ? estilos.resta : undefined}>
                <td>
                  <strong>{s.titulo}</strong>{s.detalle ? ` · ${s.detalle}` : ''}
                  <br /><span className={estilos.nota}>{fechaHora(s.cuando)}{s.anulado ? ' · anulado, no cuenta' : ''}</span>
                </td>
                <td>{s.signo < 0 ? '− ' : '+ '}{formatoCOP(s.monto)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {problemas.length > 0 && (
        <div className={estilos.error} role="alert">
          <span aria-hidden>⚠</span> Estas cifras no cuadran:
          <ul>{problemas.map((p) => <li key={p}>{p}</li>)}</ul>
        </div>
      )}
    </>
  )
}
