import { forwardRef } from 'react'
import estilos from './Boton.module.css'

/**
 * @param variante  'primario' | 'secundario' | 'fantasma' | 'peligro'
 *
 * `forwardRef` para poder llevarle el foco: en el cobro por transferencia no hay nada que escribir, y
 * Enter tiene que caer en "Cobrar".
 */
const Boton = forwardRef(function Boton({
  variante = 'secundario',
  tamano = 'normal',
  children,
  className = '',
  ...props
}, ref) {
  return (
    <button
      ref={ref}
      type="button"
      className={`${estilos.boton} ${estilos[variante]} ${estilos[tamano]} ${className}`}
      {...props}
    >
      {children}
    </button>
  )
})

export default Boton
