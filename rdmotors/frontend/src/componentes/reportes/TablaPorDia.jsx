import { formatoCOP } from '../../utils/formato'
import { etiquetaDeFila } from '../../utils/periodo'
import comun from '../../paginas/Listado.module.css'
import estilos from './Resultados.module.css'

/**
 * El día por día (spec 0007, RF-017): cada día o semana, los días sin movimiento en $0, y la fila de totales, que son
 * las cifras de arriba tal como vinieron. La columna de costos adicionales solo sale si hubo.
 */
export default function TablaPorDia({ r, cargando }) {
  const c = r.cifras
  const conCostos = c.costosAdicionales !== 0
  const celda = (valor) => (
    <td className={`${estilos.cifraCol} ${valor < 0 ? estilos.negativo : ''}`}>{formatoCOP(valor)}</td>
  )
  const columnas = (f) => (
    <>
      <td className={estilos.cifraCol}>{f.ventas}</td>
      {celda(f.ventasNetas)}
      {celda(f.costoVendido)}
      {conCostos && celda(f.costosAdicionales)}
      {celda(f.utilidadBruta)}
      {celda(f.gastos)}
      {celda(f.utilidadOperativa)}
    </>
  )
  const sinMovimiento = (f) => f.ventas === 0 && f.gastos === 0 && f.costosAdicionales === 0

  return (
    <div className={`scroll-x ${comun.marco} ${cargando ? estilos.atenuado : ''}`}>
      <table className={estilos.tabla}>
        <thead>
          <tr>
            <th>{r.agrupacion === 'SEMANA' ? 'Semana' : 'Día'}</th>
            <th className={estilos.cifraCol}>Ventas</th>
            <th className={estilos.cifraCol}>Ventas netas</th>
            <th className={estilos.cifraCol}>Costo vendido</th>
            {conCostos && <th className={estilos.cifraCol}>Costos adic.</th>}
            <th className={estilos.cifraCol}>Utilidad bruta</th>
            <th className={estilos.cifraCol}>Gastos</th>
            <th className={estilos.cifraCol}>Utilidad operativa</th>
          </tr>
        </thead>
        <tbody>
          {r.filas.map((f) => (
            <tr key={f.desde} className={sinMovimiento(f) ? estilos.sinMovimiento : undefined}>
              <td>
                {etiquetaDeFila(f)}
                {f.renglonesSinCosto > 0 && (
                  <span className={estilos.asterisco} title="Renglones sin costo: la utilidad está sobrestimada">*</span>
                )}
              </td>
              {columnas(f)}
            </tr>
          ))}
          {r.filaGastosDelMes && (
            <tr className={estilos.filaDelMes}>
              <td>Gastos del mes</td>
              {columnas(r.filaGastosDelMes)}
            </tr>
          )}
        </tbody>
        <tfoot>
          <tr className={estilos.totales}>
            <td>Total</td>
            {columnas(c)}
          </tr>
        </tfoot>
      </table>
    </div>
  )
}
