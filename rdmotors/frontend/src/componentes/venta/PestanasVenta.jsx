import { NavLink } from 'react-router-dom'
import estilos from '../Pestanas.module.css'

/**
 * Las secciones del mostrador: vender y lo que se vendió en el turno (spec 0003, H7 y H10), y la caja del turno
 * con sus gastos, retiros y cierre (spec 0006). La lista de gastos es de Reportes.
 */
export default function PestanasVenta() {
  const clase = ({ isActive }) => (isActive ? estilos.activa : estilos.pestana)
  return (
    <nav className={estilos.pestanas} aria-label="Secciones de venta">
      {/* `end`: sin él, "Vender" quedaría marcada también dentro de las ventas del turno. */}
      <NavLink to="/vender" end className={clase}>Vender</NavLink>
      <NavLink to="/vender/ventas" className={clase}>Ventas del turno</NavLink>
      <NavLink to="/vender/caja" className={clase}>Caja</NavLink>
    </nav>
  )
}
