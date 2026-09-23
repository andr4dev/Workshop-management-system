import { Link } from 'react-router-dom'
import Boton from '../Boton'
import { movimientosDelCajon } from '../../utils/gastos'
import { formatoCOP } from '../../utils/formato'
import comun from '../../paginas/Listado.module.css'
import estilos from '../../paginas/Caja.module.css'

const HORA = new Intl.DateTimeFormat('es-CO', { hour: 'numeric', minute: '2-digit', timeZone: 'America/Bogota' })
const TIPOS = { GASTO: 'Gasto', RETIRO: 'Retiro', COMPRA: 'Compra', DEVOLUCION: 'Devolución' }

/**
 * Lo que sacó billetes del cajón en un turno (spec 0006, RF-023): gastos, retiros, compras de caja y lo devuelto
 * por ventas anuladas, cada uno con su monto. Lo que suman está en el desglose del arqueo.
 *
 * @param onAnular si viene, los gastos y retiros sin anular ofrecen Anular (solo con el turno abierto)
 */
export default function TablaMovimientos({ turno, onAnular }) {
  const movimientos = movimientosDelCajon(turno)

  if (movimientos.length === 0) {
    return <div className={comun.vacio}><p>No ha salido plata del cajón en este turno.</p></div>
  }

  return (
    <div className={`scroll-x ${comun.marco}`}>
      <table className={comun.tabla} style={{ minWidth: 640 }}>
        <thead>
          <tr>
            <th>Hora</th>
            <th>Qué</th>
            <th>Detalle</th>
            <th className="cifra">Salió</th>
            {onAnular && <th aria-label="Acciones" />}
          </tr>
        </thead>
        <tbody>
          {movimientos.map((m) => (
            <tr key={`${m.tipo}-${m.id}`}>
              <td>{m.cuando ? HORA.format(new Date(m.cuando)) : ''}</td>
              <td>
                <span className={estilos.tipo}>{TIPOS[m.tipo]}</span>
                {m.tipo === 'GASTO' && m.titulo}
                {m.anulado && <span className={comun.anulada}>Anulado</span>}
              </td>
              <td>
                {m.tipo === 'COMPRA' ? <Link className={comun.enlace} to={`/compras/historial/${m.id}`}>{m.detalle}</Link>
                  : m.tipo === 'DEVOLUCION' ? <Link className={comun.enlace} to={`/vender/ventas/${m.id}`}>{m.detalle}</Link>
                    : m.detalle}
                {m.registradoPor && <span className={estilos.motivo}>Registró {m.registradoPor}</span>}
                {m.anulado && (m.motivoAnulacion || m.anuladoPor) && (
                  <span className={estilos.motivo}>
                    Anulado{m.anuladoPor && ` por ${m.anuladoPor}`}{m.motivoAnulacion && `: ${m.motivoAnulacion}`}
                  </span>
                )}
              </td>
              <td className="cifra">
                <strong className={m.anulado ? comun.tachado : undefined}>{formatoCOP(m.monto)}</strong>
              </td>
              {onAnular && (
                <td className="cifra">
                  {!m.anulado && (m.tipo === 'GASTO' || m.tipo === 'RETIRO') && (
                    <Boton variante="fantasma" tamano="chico" onClick={() => onAnular(m)}>Anular</Boton>
                  )}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
