import { NavLink } from 'react-router-dom'
import estilos from '../Pestanas.module.css'

/**
 * Las secciones de reportes: los resultados del período (spec 0007), primero, y los gastos del negocio (spec 0006,
 * RF-007a).
 */
export default function PestanasReportes() {
  const clase = ({ isActive }) => (isActive ? estilos.activa : estilos.pestana)
  return (
    <nav className={estilos.pestanas} aria-label="Secciones de reportes">
      <NavLink to="/reportes/resultados" className={clase}>Resultados</NavLink>
      <NavLink to="/reportes/gastos" className={clase}>Gastos</NavLink>
    </nav>
  )
}
