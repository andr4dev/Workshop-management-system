import { Fragment } from 'react'
import { Link } from 'react-router-dom'
import Modal from '../Modal'
import Boton from '../Boton'
import { fechaDia, formatoCOP } from '../../utils/formato'
import { fechaLocal } from '../../utils/inventario'
import { compraDelGasto, enlaceAlGasto, textoDelReparto } from '../../utils/resultados'
import estilos from './Resultados.module.css'

/**
 * *Ver cálculo* (spec 0007, RF-012): de dónde sale una cifra, parte por parte, con cada categoría de costo y de gasto.
 *
 * Cada categoría de costo y de gasto se abre en sus gastos, con un enlace a cada uno (spec 0014, RF-008 a RF-010).
 *
 * Dice si cuadra. Si un subtotal no es lo que suman sus partes, lo dice en rojo con lo que dan las partes, y **no lo
 * corrige**: una memoria de cálculo que miente es peor que no tenerla.
 *
 * @param calculo el de `calculoDe` (utils/resultados.js)
 */
export default function PanelCalculo({ calculo, onCerrar }) {
  return (
    <Modal abierto onCerrar={onCerrar} titulo={calculo.titulo} ancho={540}
      pie={<Boton variante="secundario" onClick={onCerrar}>Cerrar</Boton>}>
      <p className={estilos.pregunta}>{calculo.pregunta}</p>
      <table className={estilos.calculo}>
        <tbody>
          {calculo.filas.map((fila, i) => (fila.tipo === 'SUBTOTAL' ? (
            <tr key={fila.etiqueta} className={estilos.subtotalCalculo}>
              <td className={estilos.signo}>=</td>
              <td>
                {fila.etiqueta}
                {fila.cuadra === false && (
                  <span className={estilos.noCuadra} role="alert"> ⚠ las partes dan {formatoCOP(fila.calculado)}</span>
                )}
              </td>
              <td className={`${estilos.monto} ${fila.monto < 0 ? estilos.negativo : ''}`}>{formatoCOP(fila.monto)}</td>
            </tr>
          ) : (
            <Fragment key={fila.etiqueta}>
              <tr>
                <td className={estilos.signo}>{i === 0 && fila.signo === '+' ? '' : fila.signo}</td>
                <td>
                  {fila.etiqueta}
                  {fila.sinCosto > 0 && (
                    <span className={estilos.notaCalculo}>
                      {' '}* sin {fila.sinCosto} {fila.sinCosto === 1 ? 'renglón' : 'renglones'} sin costo
                    </span>
                  )}
                  {fila.cuadra === false && (
                    <span className={estilos.noCuadra} role="alert"> ⚠ las categorías suman {formatoCOP(fila.sumaHijos)}</span>
                  )}
                </td>
                <td className={estilos.monto}>{formatoCOP(fila.monto)}</td>
              </tr>
              {fila.hijos?.map((hijo) => (
                <Fragment key={hijo.categoriaId ?? hijo.etiqueta}>
                  <tr className={estilos.hijo}>
                    <td />
                    <td>{hijo.etiqueta}</td>
                    <td className={estilos.monto}>{formatoCOP(hijo.monto)}</td>
                  </tr>
                  {hijo.gastos?.map((gasto) => {
                    const reparto = textoDelReparto(gasto, formatoCOP)
                    const compra = compraDelGasto(gasto)
                    return (
                      <tr key={gasto.id} className={estilos.gastoDelCalculo}>
                        <td />
                        <td>
                          <Link to={enlaceAlGasto(gasto, hijo.categoriaId)} className={estilos.enlaceGasto}>
                            {fechaDia(fechaLocal(gasto.fecha))} · {gasto.descripcion.replace(/\s*\[compra [^\]]+\]/i, '')}
                          </Link>
                          {reparto && <span className={estilos.notaCalculo}> ({reparto})</span>}
                          {compra && (
                            <> · <Link to={`/compras/historial/${compra}`} className={estilos.enlaceGasto}>ver la compra</Link></>
                          )}
                        </td>
                        <td className={estilos.monto}>{formatoCOP(gasto.cargado)}</td>
                      </tr>
                    )
                  })}
                </Fragment>
              ))}
            </Fragment>
          )))}
        </tbody>
      </table>
      {calculo.cuadra
        ? <p className={estilos.cuadra}><span aria-hidden>✓</span> Cuadra al peso</p>
        : (
          <p className={estilos.descuadre} role="alert">
            <span aria-hidden>⚠</span> Este desglose no cuadra con su cifra. No se corrige en pantalla: hay que revisarlo.
          </p>
        )}
    </Modal>
  )
}
