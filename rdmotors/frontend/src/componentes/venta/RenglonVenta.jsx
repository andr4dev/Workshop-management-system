import { formatoCOP } from '../../utils/formato'
import { pagaComision, precioDelRenglon } from '../../utils/venta'
import estilos from './RenglonVenta.module.css'

/**
 * Un renglón de la venta: qué se lleva, cuántos, a cuánto, y cuántos hay (spec 0003, RF-005).
 *
 * El precio no se edita (RF-007): negociar es un descuento sobre el total. Si la cantidad pasa de lo
 * que hay, se marca en rojo en vez de corregirse sola: el cajero decide si baja la cantidad o quita el
 * renglón.
 *
 * Un aceite que paga comisión por cambio (spec 0015) pregunta *¿se cambia aquí?* sin nada escogido: *no* baja la
 * comisión del precio; *sí* pide quién lo cambió (de entrada, el que registra) y al cobrar la comisión sale del cajón.
 *
 * @param personas quién puede haber hecho el cambio: id y nombre
 */
export default function RenglonVenta({ renglon, onCantidad, onQuitar, onCambio, onQuien, personas = [], bloqueado }) {
  const { codigo, nombre, marca, cantidad, stock, problema, aviso } = renglon
  const precio = precioDelRenglon(renglon)
  const sePasa = cantidad > stock
  const conCambio = pagaComision(renglon)

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
        {conCambio && (
          <span className={estilos.cambio} role="group" aria-label={`¿Se le cambia el aceite aquí? ${nombre}`}>
            <span className={estilos.cambioPregunta}>¿Se cambia aquí?</span>
            <button type="button" aria-pressed={renglon.cambio === 'SE_CAMBIA'} disabled={bloqueado}
              className={renglon.cambio === 'SE_CAMBIA' ? estilos.opcionActiva : estilos.opcion}
              onClick={() => onCambio('SE_CAMBIA')}>Sí</button>
            <button type="button" aria-pressed={renglon.cambio === 'NO_SE_CAMBIA'} disabled={bloqueado}
              className={renglon.cambio === 'NO_SE_CAMBIA' ? estilos.opcionActiva : estilos.opcion}
              onClick={() => onCambio('NO_SE_CAMBIA')}>No (−{formatoCOP(renglon.comisionCambio)})</button>
            {renglon.cambio === 'SE_CAMBIA' && (
              <select className={estilos.quien} value={renglon.cambioPorId ?? ''} disabled={bloqueado}
                aria-label={`Quién le cambió el aceite: ${nombre}`} onChange={(e) => onQuien(e.target.value)}>
                <option value="">¿Quién lo cambió?</option>
                {personas.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
              </select>
            )}
            {!renglon.cambio && <span className={estilos.problema}>Escoge uno para cobrar</span>}
          </span>
        )}
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
