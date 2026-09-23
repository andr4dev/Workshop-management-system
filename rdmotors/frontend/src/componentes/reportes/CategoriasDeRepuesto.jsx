import { formatoCOP, GUION } from '../../utils/formato'
import { conComa, participacion, sumanLasVentasNetas } from '../../utils/resultados'
import comun from '../../paginas/Listado.module.css'
import estilos from './Resultados.module.css'

/**
 * Por categoría de repuesto (spec 0007, RF-021): cuánto vendió y cuánto dejó cada una, con *Sin categoría*. Las
 * categorías suman las ventas netas; si no, lo dice.
 */
export default function CategoriasDeRepuesto({ r, cargando }) {
  const total = r.cifras.ventasNetas
  const cuadra = sumanLasVentasNetas(r.categoriasDeRepuesto, r)

  return (
    <>
      <h2 className={estilos.seccion}>Por categoría de repuesto</h2>
      {!cuadra && (
        <p className={estilos.descuadre} role="alert">
          <span aria-hidden>⚠</span> Las categorías no suman las ventas netas del período.
        </p>
      )}
      <div className={`scroll-x ${comun.marco} ${cargando ? estilos.atenuado : ''}`}>
        <table className={estilos.tabla}>
          <thead>
            <tr>
              <th>Categoría</th>
              <th className={estilos.cifraCol}>Unidades</th>
              <th className={estilos.cifraCol}>Ventas netas</th>
              <th className={estilos.cifraCol}>Parte de las ventas</th>
              <th className={estilos.cifraCol}>Utilidad</th>
              <th className={estilos.cifraCol}>Margen</th>
            </tr>
          </thead>
          <tbody>
            {r.categoriasDeRepuesto.map((c) => {
              const parte = participacion(c.ventasNetas, total)
              return (
                <tr key={c.id ?? 'sin-categoria'}>
                  <td>{c.nombre}</td>
                  <td className={estilos.cifraCol}>{c.unidades}</td>
                  <td className={estilos.cifraCol}>{formatoCOP(c.ventasNetas)}</td>
                  <td className={estilos.cifraCol}>
                    <span className={estilos.barraParte} aria-hidden>
                      <span style={{ width: `${Math.max(0, parte ?? 0)}%` }} />
                    </span>
                    {parte == null ? GUION : `${conComa(parte)} %`}
                  </td>
                  <td className={`${estilos.cifraCol} ${c.utilidad < 0 ? estilos.negativo : ''}`}>
                    {c.utilidad == null
                      ? <span title={`${c.renglonesSinCosto} sin costo`}>{GUION}</span>
                      : formatoCOP(c.utilidad)}
                  </td>
                  <td className={estilos.cifraCol}>{c.margen == null ? GUION : `${conComa(c.margen)} %`}</td>
                </tr>
              )
            })}
          </tbody>
          <tfoot>
            <tr className={estilos.totales}>
              <td>Total</td>
              <td className={estilos.cifraCol}>{r.cifras.unidades}</td>
              <td className={estilos.cifraCol}>{formatoCOP(total)}</td>
              <td className={estilos.cifraCol}>{total ? '100,0 %' : GUION}</td>
              <td />
              <td />
            </tr>
          </tfoot>
        </table>
      </div>
      <p className={estilos.pie}>
        La utilidad de una categoría queda en "—" si algún repuesto suyo se vendió sin costo.
      </p>
    </>
  )
}
