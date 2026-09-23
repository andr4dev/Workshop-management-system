import { NavLink } from 'react-router-dom'
import estilos from '../Pestanas.module.css'

/**
 * Las secciones del módulo de compras. Registrar y ver lo registrado son del mismo módulo; el
 * inventario no, porque su kardex también tendrá ventas y ajustes.
 */
export default function PestanasCompras() {
  const clase = ({ isActive }) => (isActive ? estilos.activa : estilos.pestana)
  return (
    <nav className={estilos.pestanas} aria-label="Secciones de compras">
      {/* `end`: sin él, "Registrar" quedaría marcada también dentro del historial. */}
      <NavLink to="/compras" end className={clase}>Registrar</NavLink>
      <NavLink to="/compras/historial" className={clase}>Historial</NavLink>
    </nav>
  )
}
