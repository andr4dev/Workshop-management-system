import { NavLink } from 'react-router-dom'
import estilos from './Pestanas.module.css'

/**
 * Los ajustes de la tienda, que solo ve el administrador (spec 0004, §5): lo que sale en el comprobante, las
 * personas que entran al sistema y el respaldo de la base (spec 0009) y el correo del cierre (spec 0010).
 */
export default function PestanasAjustes() {
  const clase = ({ isActive }) => (isActive ? estilos.activa : estilos.pestana)
  return (
    <nav className={estilos.pestanas} aria-label="Ajustes">
      <NavLink to="/tienda" className={clase}>Datos de la tienda</NavLink>
      <NavLink to="/usuarios" end className={clase}>Usuarios</NavLink>
      <NavLink to="/usuarios/entradas" className={clase}>Entradas</NavLink>
      <NavLink to="/respaldo" className={clase}>Respaldo</NavLink>
      <NavLink to="/correos" className={clase}>Correos</NavLink>
    </nav>
  )
}
