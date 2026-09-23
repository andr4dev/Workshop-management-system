import { Fragment } from 'react'
import Modal from '../Modal'
import Boton from '../Boton'
import { formatoCOP } from '../../utils/formato'
import estilos from './Resultados.module.css'

/**
 * *Ver cálculo* (spec 0007, RF-012): de dónde sale una cifra, parte por parte, con cada categoría de costo y de gasto.
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
                <tr key={hijo.categoriaId ?? hijo.etiqueta} className={estilos.hijo}>
                  <td />
                  <td>{hijo.etiqueta}</td>
                  <td className={estilos.monto}>{formatoCOP(hijo.monto)}</td>
                </tr>
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
