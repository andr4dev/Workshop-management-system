import { formatoCOP } from '../../utils/formato'
import estilos from './RenglonVenta.module.css'

/**
 * Un renglón de la venta: qué se lleva, cuántos, a cuánto, y cuántos hay (spec 0003, RF-005).
 *
 * El precio no se edita (RF-007): negociar es un descuento sobre el total. Si la cantidad pasa de lo
 * que hay, se marca en rojo en vez de corregirse sola: el cajero decide si baja la cantidad o quita el
 * renglón.
 */
export default function RenglonVenta({ renglon, onCantidad, onQuitar, bloqueado }) {
  const { codigo, nombre, marca, precio, cantidad, stock, problema, aviso } = renglon
  const sePasa = cantidad > stock

  return (
    <li className={`${estilos.renglon} ${problema || sePasa ? estilos.conProblema : ''}`}>
      <span className={estilos.codigo}>{codigo}</span>

      <span className={estilos.repuesto}>
        <span className={estilos.nombre}>{nombre}</span>
        <span className={estilos.marca}>{marca}</span>
        {problema && <span className={estilos.problema}>{problema.texto}</span>}
        {!problema && sePasa && (
          <span className={estilos.problema}>{stock === 0 ? 'Ya no hay unidades' : `Solo hay ${stock}`}</span>
        )}
        {aviso && <span className={estilos.aviso}>{aviso}</span>}
      </span>

      <span className={estilos.cantidad}>
        <button type="button" className={estilos.paso} onClick={() => onCantidad(String(cantidad - 1))}
          disabled={bloqueado || cantidad <= 1} aria-label={`Una ${codigo} menos`}>−</button>
        <input
          className={estilos.numero}
          value={cantidad}
          onChange={(e) => onCantidad(e.target.value)}
          inputMode="numeric"
          aria-label={`Cantidad de ${codigo}`}
          disabled={bloqueado}
        />
        <button type="button" className={estilos.paso} onClick={() => onCantidad(String(cantidad + 1))}
          disabled={bloqueado || cantidad >= stock} aria-label={`Una ${codigo} más`}>+</button>
      </span>

      <span className={estilos.precio}>{formatoCOP(precio)}</span>
      <span className={estilos.total}>{formatoCOP(precio * cantidad)}</span>

      <button type="button" className={estilos.quitar} onClick={onQuitar} disabled={bloqueado}
        aria-label={`Quitar ${codigo}`} title="Quitar de la venta">×</button>
    </li>
  )
}
