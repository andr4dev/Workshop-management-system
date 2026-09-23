import { formatoCOP } from '../../utils/formato'
import { cadaCuantasEtiquetas, geometria, montoCorto } from '../../utils/grafica'
import { etiquetaDeFila } from '../../utils/periodo'
import estilos from './Resultados.module.css'

const ANCHO = 900
const ALTO = 260
const IZQUIERDA = 62     // las cifras del eje
const ARRIBA = 8
const ABAJO = 24         // las etiquetas de los días

/**
 * La gráfica del día por día (spec 0007, RF-018): ventas netas y utilidad operativa de cada día o semana. Una
 * utilidad negativa baja del eje, en rojo. Sin librería: rectángulos en un SVG.
 */
export default function GraficaPorDia({ r, cargando }) {
  const areaAncho = ANCHO - IZQUIERDA
  const areaAlto = ALTO - ARRIBA - ABAJO
  const g = geometria(r.filas, { ancho: areaAncho, alto: areaAlto })
  const cada = cadaCuantasEtiquetas(r.filas.length)
  const hayPerdida = r.filas.some((f) => f.utilidadOperativa < 0)
  const porQue = r.agrupacion === 'SEMANA' ? 'semana' : 'día'

  return (
    <figure className={`${estilos.grafica} ${cargando ? estilos.atenuado : ''}`}>
      <ul className={estilos.leyenda}>
        <li><span className={`${estilos.muestra} ${estilos.muestraVentas}`} aria-hidden /> Ventas netas</li>
        <li><span className={`${estilos.muestra} ${estilos.muestraUtilidad}`} aria-hidden /> Utilidad operativa</li>
        {hayPerdida && <li><span className={`${estilos.muestra} ${estilos.muestraPerdida}`} aria-hidden /> Pérdida</li>}
      </ul>
      <div className="scroll-x">
        <svg viewBox={`0 0 ${ANCHO} ${ALTO}`} role="img"
          aria-label={`Ventas netas y utilidad operativa por ${porQue}. Los montos exactos están en la tabla.`}>
          <g transform={`translate(${IZQUIERDA}, ${ARRIBA})`}>
            {g.marcas.map((marca) => (
              <g key={marca}>
                <line x1={0} x2={areaAncho} y1={g.y(marca)} y2={g.y(marca)}
                  className={marca === 0 ? estilos.lineaCero : estilos.lineaEje} />
                <text x={-8} y={g.y(marca)} textAnchor="end" dominantBaseline="middle" className={estilos.textoEje}>
                  {montoCorto(marca)}
                </text>
              </g>
            ))}
            {g.barras.map((b, i) => (
              <g key={b.fila.desde}>
                <title>
                  {`${etiquetaDeFila(b.fila)}: ventas netas ${formatoCOP(b.fila.ventasNetas)} · utilidad operativa ${formatoCOP(b.fila.utilidadOperativa)}`}
                </title>
                <rect x={b.x} y={0} width={b.ancho} height={areaAlto} fill="transparent" />
                <rect x={b.ventas.x} y={b.ventas.y} width={b.anchoBarra} height={b.ventas.alto} rx={2}
                  className={estilos.barraVentas} />
                <rect x={b.utilidad.x} y={b.utilidad.y} width={b.anchoBarra} height={b.utilidad.alto} rx={2}
                  className={b.utilidad.negativa ? estilos.barraPerdida : estilos.barraUtilidad} />
                {i % cada === 0 && (
                  <text x={b.x + b.ancho / 2} y={areaAlto + 17} textAnchor="middle" className={estilos.textoEje}>
                    {etiquetaDeFila(b.fila)}
                  </text>
                )}
              </g>
            ))}
          </g>
        </svg>
      </div>
    </figure>
  )
}
