import { formatoCOP } from '../../utils/formato'
import { pagaComision, precioDelRenglon } from '../../utils/venta'
import estilos from './CorregirPedido.module.css'

/**
 * Corregir el pedido sin salir de *Cobrar*: cuántos, quitar un repuesto, y en un aceite si se cambia aquí y quién lo
 * cambió. Son las mismas correcciones que cada renglón tiene en la venta (las mismas funciones de `Vender`): aquí solo
 * están a la mano, para no cancelar el cobro por un error que se ve al repasar el pedido con el cliente.
 *
 * @param correcciones `{ cantidad(varianteId, n), quitar(varianteId), cambio(varianteId, cambio), quien(varianteId, id) }`
 */
export default function CorregirPedido({ renglones, personas = [], correcciones, bloqueado }) {
  return (
    <ul className={estilos.lista} aria-label="Corregir el pedido">
      {renglones.map((r) => {
        const sePasa = r.cantidad > r.stock
        return (
          <li key={r.varianteId} className={estilos.renglon}>
            <div className={estilos.fila}>
              <span className={estilos.nombre}>{r.nombre}</span>
              <span className={estilos.valor}>{formatoCOP(precioDelRenglon(r) * r.cantidad)}</span>
            </div>
            <div className={estilos.controles}>
              <span className={estilos.cantidad} role="group" aria-label={`Cuántos ${r.nombre}`}>
                <button type="button" className={estilos.paso} disabled={bloqueado || r.cantidad <= 1}
                  aria-label={`Uno menos de ${r.nombre}`} onClick={() => correcciones.cantidad(r.varianteId, r.cantidad - 1)}>−</button>
                <span className={estilos.numero}>{r.cantidad}</span>
                <button type="button" className={estilos.paso} disabled={bloqueado}
                  aria-label={`Uno más de ${r.nombre}`} onClick={() => correcciones.cantidad(r.varianteId, r.cantidad + 1)}>+</button>
              </span>
              <button type="button" className={estilos.quitar} disabled={bloqueado}
                onClick={() => correcciones.quitar(r.varianteId)}>Quitar</button>
            </div>
            {sePasa && (
              <p className={estilos.problema}>{r.stock === 0 ? 'Ya no hay unidades' : `Solo hay ${r.stock}`}</p>
            )}
            {pagaComision(r) && (
              <div className={estilos.cambio} role="group" aria-label={`¿Se le cambia el aceite aquí? ${r.nombre}`}>
                <span className={estilos.pregunta}>¿Se cambia aquí?</span>
                <button type="button" aria-pressed={r.cambio === 'SE_CAMBIA'} disabled={bloqueado}
                  className={r.cambio === 'SE_CAMBIA' ? estilos.opcionActiva : estilos.opcion}
                  onClick={() => correcciones.cambio(r.varianteId, 'SE_CAMBIA')}>Sí</button>
                <button type="button" aria-pressed={r.cambio === 'NO_SE_CAMBIA'} disabled={bloqueado}
                  className={r.cambio === 'NO_SE_CAMBIA' ? estilos.opcionActiva : estilos.opcion}
                  onClick={() => correcciones.cambio(r.varianteId, 'NO_SE_CAMBIA')}>No (−{formatoCOP(r.comisionCambio)})</button>
                {r.cambio === 'SE_CAMBIA' && (
                  <label className={estilos.quien}>
                    <span>Lo cambia</span>
                    <select value={r.cambioPorId ?? ''} disabled={bloqueado}
                      onChange={(e) => correcciones.quien(r.varianteId, e.target.value)}>
                      <option value="">Escoge quién</option>
                      {personas.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
                    </select>
                  </label>
                )}
              </div>
            )}
          </li>
        )
      })}
    </ul>
  )
}
