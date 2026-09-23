import { useState } from 'react'
import { Link } from 'react-router-dom'
import Segmento from '../caja/Segmento'
import { formatoCOP, GUION } from '../../utils/formato'
import {
  conComa, ORDENES_DE_REPUESTOS, rankingDeRepuestos, sumanLasVentasNetas, vendidosConPerdida,
} from '../../utils/resultados'
import comun from '../../paginas/Listado.module.css'
import estilos from './Resultados.module.css'

const LIMITE = 20

/**
 * Qué repuestos dejan plata (spec 0007, RF-019 y RF-020): los 20 primeros por utilidad, por unidades o por ventas
 * netas, y aparte los vendidos por debajo del costo.
 *
 * Las ventas netas de cada repuesto llevan repartido el descuento de su venta: la lista completa suma las ventas
 * netas del período. Uno vendido sin costo no tiene utilidad ni margen: dice "—".
 */
export default function RepuestosDelPeriodo({ r, cargando }) {
  const [orden, setOrden] = useState('UTILIDAD')
  const primeros = rankingDeRepuestos(r.repuestos, orden, LIMITE)
  const conPerdida = vendidosConPerdida(r.repuestos)
  const cuadra = sumanLasVentasNetas(r.repuestos, r)

  return (
    <>
      <div className={estilos.encabezadoSeccion}>
        <h2 className={estilos.seccion}>Repuestos</h2>
        <Segmento etiqueta="Ordenar los repuestos por" valor={orden} opciones={ORDENES_DE_REPUESTOS} onCambio={setOrden} />
      </div>
      {!cuadra && (
        <p className={estilos.descuadre} role="alert">
          <span aria-hidden>⚠</span> Los repuestos no suman las ventas netas del período.
        </p>
      )}
      <div className={`scroll-x ${comun.marco} ${cargando ? estilos.atenuado : ''}`}>
        <table className={estilos.tabla}>
          <thead>
            <tr>
              <th className={estilos.cifraCol}>#</th>
              <th>Repuesto</th>
              <th className={estilos.cifraCol}>Unidades</th>
              <th className={estilos.cifraCol}>Ventas netas</th>
              <th className={estilos.cifraCol}>Costo</th>
              <th className={estilos.cifraCol}>Utilidad</th>
              <th className={estilos.cifraCol}>Margen</th>
            </tr>
          </thead>
          <tbody>
            {primeros.map((v, i) => <FilaRepuesto key={v.id} v={v} puesto={i + 1} />)}
          </tbody>
        </table>
      </div>
      <p className={estilos.pie}>
        {r.repuestos.length > LIMITE
          ? `Los ${LIMITE} primeros de ${r.repuestos.length} repuestos vendidos.`
          : `${r.repuestos.length} ${r.repuestos.length === 1 ? 'repuesto vendido' : 'repuestos vendidos'}.`}
        {' '}Las ventas netas llevan repartido el descuento de cada venta.
      </p>

      {conPerdida.length > 0 && (
        <>
          <h3 className={estilos.subseccion}>Vendidos con pérdida</h3>
          <div className={`scroll-x ${comun.marco} ${cargando ? estilos.atenuado : ''}`}>
            <table className={estilos.tabla}>
              <thead>
                <tr>
                  <th>Repuesto</th>
                  <th className={estilos.cifraCol}>Unidades</th>
                  <th className={estilos.cifraCol}>Ventas netas</th>
                  <th className={estilos.cifraCol}>Costo</th>
                  <th className={estilos.cifraCol}>Se perdió</th>
                </tr>
              </thead>
              <tbody>
                {conPerdida.map((v) => (
                  <tr key={v.id}>
                    <td><NombreRepuesto v={v} /></td>
                    <td className={estilos.cifraCol}>{v.unidades}</td>
                    <td className={estilos.cifraCol}>{formatoCOP(v.ventasNetas)}</td>
                    <td className={estilos.cifraCol}>{formatoCOP(v.costo)}</td>
                    <td className={`${estilos.cifraCol} ${estilos.negativo}`}><strong>{formatoCOP(-v.utilidad)}</strong></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className={estilos.pie}>Se vendieron por debajo de lo que costaron: revisa su precio o la compra que les dio el costo.</p>
        </>
      )}
    </>
  )
}

function NombreRepuesto({ v }) {
  return (
    <>
      <Link to={`/inventario/${v.id}`} className={comun.enlace}>{v.nombre}</Link>
      <span className={estilos.sub}>{v.codigo} · {v.marca}</span>
    </>
  )
}

function FilaRepuesto({ v, puesto }) {
  const sinCosto = v.renglonesSinCosto > 0
  return (
    <tr className={v.conPerdida ? estilos.filaPerdida : undefined}>
      <td className={estilos.cifraCol}>{puesto}</td>
      <td><NombreRepuesto v={v} /></td>
      <td className={estilos.cifraCol}>{v.unidades}</td>
      <td className={estilos.cifraCol}>{formatoCOP(v.ventasNetas)}</td>
      <td className={estilos.cifraCol}>
        {formatoCOP(v.costo)}
        {sinCosto && <span className={estilos.asterisco} title="Algún renglón se vendió sin costo">*</span>}
      </td>
      <td className={`${estilos.cifraCol} ${v.conPerdida ? estilos.negativo : ''}`}>
        {v.utilidad == null ? GUION : formatoCOP(v.utilidad)}
      </td>
      <td className={estilos.cifraCol}>{v.margen == null ? GUION : `${conComa(v.margen)} %`}</td>
    </tr>
  )
}
