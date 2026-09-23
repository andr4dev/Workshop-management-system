import { diferenciaEnPalabras, filasDelDesglose, problemasDelArqueo } from '../../utils/arqueo'
import { formatoCOP } from '../../utils/formato'
import estilos from './Caja.module.css'

const CLASE = { FALTANTE: 'cifraFaltante', SOBRANTE: 'cifraSobrante', CUADRA: 'cifraCuadra' }
const ICONO = { FALTANTE: '⚠', SOBRANTE: '⚠', CUADRA: '✓' }

/**
 * Lo que debería haber en el cajón y de dónde sale cada peso (spec 0006, RF-011 y RF-014).
 *
 *   - Turno ABIERTO: en vivo (`turno.arqueo`), como se ve en la sección Caja. Con `contado`, la diferencia sale
 *     mientras se escribe el conteo, antes de cerrar.
 *   - Turno CERRADO: las cifras que se guardaron al cerrar (`turno.cierre`), con lo contado y la diferencia.
 *
 * Aquí no se calcula nada: se ordena lo que dio el servidor, y si las partes no suman se dice en rojo.
 *
 * @param contado lo que se lleva contado en el modal de cierre, o null
 */
export default function DesgloseArqueo({ turno, contado = null }) {
  const partes = turno.cierre ?? turno.arqueo
  if (!partes) return null
  const cerrado = turno.cierre != null
  const contadoMostrado = cerrado ? turno.cierre.contado : contado
  const diferencia = contadoMostrado == null ? null
    : diferenciaEnPalabras(cerrado ? turno.cierre.diferencia : contadoMostrado - partes.esperado)
  const filas = filasDelDesglose(turno)
  const problemas = problemasDelArqueo(turno)

  return (
    <>
      <div className={estilos.cifras}>
        <div className={estilos.cifra}>
          <span className={estilos.cifraEtiqueta}>{cerrado ? 'Debería haber' : 'Debería haber ahora'}</span>
          <span className={estilos.cifraValor}>{formatoCOP(partes.esperado)}</span>
        </div>
        {diferencia && (
          <>
            <div className={estilos.cifra}>
              <span className={estilos.cifraEtiqueta}>Contaste</span>
              <span className={estilos.cifraValor}>{formatoCOP(contadoMostrado)}</span>
            </div>
            <div className={estilos[CLASE[diferencia.tipo]]}>
              <span className={estilos.cifraEtiqueta}>Diferencia</span>
              <span className={estilos.cifraValor}><span aria-hidden>{ICONO[diferencia.tipo]}</span> {diferencia.texto}</span>
            </div>
          </>
        )}
      </div>

      <table className={estilos.desglose} aria-label="De dónde sale lo que debería haber">
        <tbody>
          {filas.map((f) => (
            <tr key={f.clave} className={f.signo < 0 ? estilos.resta : undefined}>
              <td>{f.etiqueta}</td>
              <td>{f.signo < 0 ? '−' : '+'} {formatoCOP(f.monto)}</td>
            </tr>
          ))}
          <tr className={estilos.desgloseTotal}>
            <td>Debería haber</td>
            <td>{formatoCOP(partes.esperado)}</td>
          </tr>
        </tbody>
      </table>
      <p className={estilos.nota}>
        No entran al cajón: {formatoCOP(partes.ventasTransferencia)} cobrados por transferencia
        {partes.ventasFiado > 0 ? `, ${formatoCOP(partes.ventasFiado)} que quedaron fiados` : ''}
        {partes.abonosTransferencia > 0 ? `, ${formatoCOP(partes.abonosTransferencia)} de abonos por transferencia` : ''}
        {partes.descuentos > 0 ? `, y se dieron ${formatoCOP(partes.descuentos)} en descuentos` : ''}.
      </p>

      {problemas.length > 0 && (
        <div className={estilos.error} role="alert">
          <span aria-hidden>⚠</span> Estas cifras no cuadran:
          <ul>{problemas.map((p) => <li key={p}>{p}</li>)}</ul>
        </div>
      )}
    </>
  )
}
