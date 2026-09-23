import { estadoStock } from '../../utils/inventario'
import estilos from './InsigniaStock.module.css'

/**
 * "Agotado" o "Por pedir" junto al stock. Con suficiente no pinta nada: una etiqueta verde en cada
 * fila sería ruido, y lo que el ojo tiene que encontrar son las excepciones.
 *
 * Nunca solo color: la palabra va escrita.
 */
export default function InsigniaStock({ stock, stockMinimo }) {
  const estado = estadoStock(stock, stockMinimo)
  if (estado === 'ok') return null
  return (
    <span
      className={estado === 'agotado' ? estilos.agotado : estilos.bajo}
      title={`Mínimo: ${stockMinimo}`}
    >
      {estado === 'agotado' ? 'Agotado' : 'Por pedir'}
    </span>
  )
}
