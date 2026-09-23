import { Link } from 'react-router-dom'
import Modal from '../Modal'
import Boton from '../Boton'
import { formatoCOP } from '../../utils/formato'
import { AYUDA } from '../../utils/ayudaReportes'
import { textoSinCosto } from '../../utils/resultados'
import comun from '../../paginas/Listado.module.css'
import estilos from './Resultados.module.css'

/**
 * *¿Cuáles?* (spec 0007, RF-007): los repuestos vendidos sin costo conocido, con las unidades, lo cobrado y el enlace
 * a su ficha para registrarles la compra.
 */
export default function SinCosto({ sinCosto, onCerrar }) {
  return (
    <Modal abierto onCerrar={onCerrar} titulo="Vendidos sin costo" ancho={600}
      pie={<Boton variante="secundario" onClick={onCerrar}>Cerrar</Boton>}>
      <p className={estilos.pregunta}>
        {textoSinCosto(sinCosto.renglones)}. {AYUDA.sinCosto.formula}
      </p>
      <ul className={estilos.listaSinCosto}>
        {sinCosto.repuestos.map((r) => (
          <li key={r.varianteId}>
            <span>
              <Link to={`/inventario/${r.varianteId}`} className={comun.enlace}>{r.nombre}</Link>{' '}
              <span className={estilos.codigo}>{r.codigo} · {r.marca}</span>
            </span>
            <span className="cifra">
              {r.unidades} {r.unidades === 1 ? 'unidad' : 'unidades'} · {formatoCOP(r.vendido)}
            </span>
          </li>
        ))}
      </ul>
    </Modal>
  )
}
