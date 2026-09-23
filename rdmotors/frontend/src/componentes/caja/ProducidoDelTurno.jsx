import { formatoCOP } from '../../utils/formato'
import { DESTINO, notaDelProducido, producidoDelTurno } from '../../utils/producido'
import estilos from './ProducidoDelTurno.module.css'

/**
 * El producido del turno con todas sus líneas, y por qué de todo eso solo se responde por el efectivo.
 *
 * Tres bloques que no se mezclan (portado del corte de caja del car-wash): lo que se vendió, cómo lo pagaron, y la
 * cartera del turno. El cuarto —cuánto efectivo debe haber— es `DesgloseArqueo`, justo debajo: es lo único que se
 * cuenta y se firma al cerrar.
 */
export default function ProducidoDelTurno({ turno }) {
  const { producido, formas, cartera, descuadre, efectivoQueSeResponde } = producidoDelTurno(turno)

  return (
    <section className={estilos.producido} aria-label="Producido del turno">
      <div className={estilos.encabezado}>
        <div>
          <h2 className={estilos.titulo}>Producido del turno</h2>
          <p className={estilos.nota}>{notaDelProducido(producido, formatoCOP)}</p>
        </div>
        <span className={estilos.total}>{formatoCOP(producido.total)}</span>
      </div>

      <div className={estilos.bloques}>
        <div className={estilos.bloque}>
          <h3 className={estilos.subtitulo}>Cómo lo pagaron</h3>
          <table className={estilos.tabla}>
            <tbody>
              {[['efectivo', 'Efectivo'], ['transferencia', 'Transferencia'], ['fiado', 'Fiado']].map(([clave, texto]) => (
                <tr key={clave} className={clave === 'efectivo' ? estilos.responde : undefined}>
                  <td>{texto}</td>
                  <td className={estilos.destino}>{DESTINO[clave]}</td>
                  <td className={estilos.cifra}>{formatoCOP(formas[clave])}</td>
                </tr>
              ))}
              <tr className={estilos.filaTotal}>
                <td colSpan={2}>Suman lo producido</td>
                <td className={estilos.cifra}>{formatoCOP(formas.total)}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div className={estilos.bloque}>
          <h3 className={estilos.subtitulo}>Cartera del turno</h3>
          <table className={estilos.tabla}>
            <tbody>
              <tr>
                <td>Se fió</td>
                <td className={estilos.destino}>a la cartera</td>
                <td className={estilos.cifra}>{formatoCOP(cartera.fiado)}</td>
              </tr>
              <tr className={estilos.responde}>
                <td>Abonos en efectivo</td>
                <td className={estilos.destino}>al cajón</td>
                <td className={estilos.cifra}>{formatoCOP(cartera.abonosEfectivo)}</td>
              </tr>
              <tr>
                <td>Abonos por transferencia</td>
                <td className={estilos.destino}>a la cuenta</td>
                <td className={estilos.cifra}>{formatoCOP(cartera.abonosTransferencia)}</td>
              </tr>
            </tbody>
          </table>
          <p className={estilos.nota}>Los abonos no suman al producido: pagan ventas de otros días.</p>
        </div>
      </div>

      {efectivoQueSeResponde != null && (
        <p className={estilos.seResponde}>
          <span>Respondes solo por el efectivo del cajón</span>
          <span className={estilos.seRespondeCifra}>{formatoCOP(efectivoQueSeResponde)}</span>
        </p>
      )}

      {descuadre !== 0 && (
        <p className={estilos.error} role="alert">
          <span aria-hidden>⚠</span> Las formas de pago suman {formatoCOP(formas.total)} y lo vendido es{' '}
          {formatoCOP(producido.total)}: estas cifras no cuadran.
        </p>
      )}
    </section>
  )
}
