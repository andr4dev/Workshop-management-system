import { Link } from 'react-router-dom'
import { fechaHora, formatoCOP } from '../../utils/formato'
import { enlaceAGastos, textoDeDiferencia } from '../../utils/resultados'
import comun from '../../paginas/Listado.module.css'
import estilos from './Resultados.module.css'

/**
 * Los gastos por categoría (spec 0007, RF-024) y las señales de control (RF-023): ventas anuladas y diferencias de
 * caja de los turnos cerrados en el período.
 *
 * Nada de esto mueve la ganancia por su cuenta: los gastos ya están en las cifras, una anulada no cuenta, y la
 * diferencia de caja es de la caja.
 */
export default function GastosYControl({ r, cargando }) {
  const categorias = [
    ...r.costosPorCategoria.map((c) => ({ ...c, costo: true })),
    ...r.gastosPorCategoria.map((c) => ({ ...c, costo: false })),
  ]
  const control = r.control

  return (
    <div className={`${estilos.dosColumnas} ${cargando ? estilos.atenuado : ''}`}>
      <section aria-labelledby="gastos-por-categoria">
        <h2 className={estilos.seccion} id="gastos-por-categoria">Gastos por categoría</h2>
        <div className={estilos.panel}>
          {categorias.length === 0
            ? <p className={estilos.nota}>No hubo gastos en el período.</p>
            : (
              <ul className={estilos.lista}>
                {categorias.map((c) => (
                  <li key={`${c.costo}-${c.categoriaId}`}>
                    <Link to={enlaceAGastos(r, c.categoriaId)} className={comun.enlace}>{c.categoria}</Link>
                    {c.costo && <span className={estilos.chip}>Costo</span>}
                    <span className={estilos.monto}>{formatoCOP(c.monto)}</span>
                  </li>
                ))}
              </ul>
            )}
          {r.gastosDelMes.incluidos > 0 && r.modoGastosDelMes === 'REPARTIDOS' && (
            <p className={estilos.nota}>
              Los gastos del mes van con la parte que le toca al período; en Gastos se ven enteros, en su fecha.
            </p>
          )}
        </div>
      </section>

      <section aria-labelledby="control">
        <h2 className={estilos.seccion} id="control">Control</h2>
        <div className={estilos.panel}>
          <ul className={estilos.lista}>
            <li>
              <span>Ventas anuladas</span>
              <span className={estilos.monto}>
                {control.ventasAnuladas === 0
                  ? 'Ninguna'
                  : `${control.ventasAnuladas} · ${formatoCOP(control.montoAnuladas)}`}
              </span>
            </li>
            <li>
              <span>Descuentos</span>
              <span className={estilos.monto}>
                {r.cifras.ventasConDescuento === 0
                  ? 'Ninguno'
                  : `${r.cifras.ventasConDescuento} ${r.cifras.ventasConDescuento === 1 ? 'venta' : 'ventas'} · ${formatoCOP(r.cifras.descuentos)}`}
              </span>
            </li>
            <li>
              <span>Faltantes de caja</span>
              <span className={`${estilos.monto} ${control.faltantes > 0 ? estilos.negativo : ''}`}>
                {formatoCOP(control.faltantes)}
              </span>
            </li>
            <li>
              <span>Sobrantes de caja</span>
              <span className={estilos.monto}>{formatoCOP(control.sobrantes)}</span>
            </li>
          </ul>
          <p className={estilos.nota}>Las anuladas no cuentan en ninguna cifra; se muestran para que se vean.</p>

          <h3 className={estilos.subseccion}>Turnos cerrados en el período</h3>
          {control.turnos.length === 0
            ? <p className={estilos.nota}>Ninguno.</p>
            : (
              <ul className={estilos.lista}>
                {control.turnos.map((t) => (
                  <li key={t.id}>
                    <Link to={`/vender/caja/turnos/${t.id}`} className={comun.enlace}>
                      Cerrado el {fechaHora(t.cerradoEn)}
                    </Link>
                    <span className={`${estilos.monto} ${t.diferencia < 0 ? estilos.negativo : ''}`}>
                      {textoDeDiferencia(t.diferencia, formatoCOP)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
        </div>
      </section>
    </div>
  )
}
