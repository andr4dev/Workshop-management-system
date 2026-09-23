import AyudaDe from './AyudaDe'
import { formatoCOP, GUION } from '../../utils/formato'
import { textoDeMargen } from '../../utils/resultados'
import estilos from './Resultados.module.css'

/**
 * Una de las cuatro cifras grandes (spec 0007, H1 y RF-012): el monto, su margen, su ayuda y *Ver cálculo*.
 *
 * @param margen `undefined` si la cifra no lleva margen; `null` si lo lleva pero no hay ventas ("—", no 0 %)
 * @param asterisco hay renglones vendidos sin costo: la utilidad está sobrestimada (RF-007)
 * @param cuadra si su desglose suma la cifra; si no, lo dice en rojo, junto a la cifra
 * @param variacion frente al período anterior (RF-022): `{ texto, tono: 'BUENA' | 'MALA' | 'NEUTRA' }`. Que suban
 *                  los gastos es malo; que suba la ganancia, bueno
 */
export default function CifraGrande({ ayuda, etiqueta, valor, margen, asterisco = false, nota, destacada = false,
  cuadra = true, variacion, onVerCalculo }) {
  const claseVariacion = { BUENA: estilos.variacionBuena, MALA: estilos.variacionMala }[variacion?.tono]
    ?? estilos.variacion
  return (
    <div className={destacada ? estilos.cifraDestacada : estilos.cifra}>
      <span className={estilos.etiqueta}>{etiqueta} <AyudaDe clave={ayuda} /></span>
      <span className={`${estilos.valor} ${valor < 0 ? estilos.negativo : ''}`}>
        {formatoCOP(valor)}
        {asterisco && <span className={estilos.asterisco} title="Hay renglones sin costo: la utilidad está sobrestimada">*</span>}
      </span>
      {margen !== undefined && (
        <span className={estilos.margen}>{textoDeMargen(margen) ?? `${GUION} sin ventas`}</span>
      )}
      {nota && <p className={estilos.nota}>{nota}</p>}
      {variacion && <span className={claseVariacion}>{variacion.texto}</span>}
      {!cuadra && <p className={estilos.noCuadra} role="alert"><span aria-hidden>⚠</span> Su desglose no cuadra</p>}
      <button type="button" className={estilos.verCalculo} onClick={onVerCalculo}>Ver cálculo</button>
    </div>
  )
}
