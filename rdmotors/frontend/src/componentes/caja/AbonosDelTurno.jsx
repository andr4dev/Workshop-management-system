import { Link } from 'react-router-dom'
import { formatoCOP } from '../../utils/formato'
import { FORMAS } from '../../utils/cartera'
import comun from '../../paginas/Listado.module.css'

const HORA = new Intl.DateTimeFormat('es-CO', { hour: 'numeric', minute: '2-digit', timeZone: 'America/Bogota' })

/**
 * Los abonos de clientes recibidos en el turno (spec 0008, RF-013): el efectivo entró al cajón y suma en el arqueo;
 * la transferencia no. Un abono anulado queda tachado y deja de contar.
 */
export default function AbonosDelTurno({ abonos = [] }) {
  if (abonos.length === 0) {
    return <div className={comun.vacio}><p>Ningún cliente abonó en este turno.</p></div>
  }

  return (
    <div className={`scroll-x ${comun.marco}`}>
      <table className={comun.tabla} style={{ minWidth: 560 }}>
        <thead>
          <tr>
            <th className="cifra">N.º</th>
            <th>Hora</th>
            <th>Cliente</th>
            <th>Cómo</th>
            <th className="cifra">Entró al cajón</th>
          </tr>
        </thead>
        <tbody>
          {abonos.map((a) => (
            <tr key={a.id}>
              <td className="cifra">{a.numero}</td>
              <td>
                {HORA.format(new Date(a.recibidoEn))}
                {a.anuladoEn && <span className={comun.anulada}>Anulado</span>}
              </td>
              <td>
                <Link to={`/cartera/${a.clienteId}`} className={comun.enlace}>{a.cliente}</Link>
                {a.recibidoPor && <span className={comun.tenue}> · lo recibió {a.recibidoPor.nombre}</span>}
              </td>
              <td>{FORMAS[a.forma] ?? a.forma}</td>
              <td className={`cifra ${a.anuladoEn ? comun.tachado : ''}`}>
                {a.forma === 'EFECTIVO' && !a.anuladoEn ? formatoCOP(a.monto) : '—'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
