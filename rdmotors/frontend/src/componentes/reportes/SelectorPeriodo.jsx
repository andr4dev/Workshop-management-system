import AyudaDe from './AyudaDe'
import Segmento from '../caja/Segmento'
import { ATAJOS, anterior, atajoDe, periodoDelAtajo, siguiente, tituloDelPeriodo } from '../../utils/periodo'
import comun from '../../paginas/Listado.module.css'
import estilos from './Resultados.module.css'

const NOMBRE = { DIA: 'Día', SEMANA: 'Semana', MES: 'Mes' }

/**
 * El período del reporte (spec 0007, RF-001): los atajos, las flechas al anterior y al siguiente (no más allá de
 * hoy), el rango entre dos fechas, y cómo se leen los gastos del mes (RF-010a).
 *
 * @param onCambio recibe el período nuevo; `{ reemplazar: true }` mientras se escribe una fecha del rango, para no
 *                 llenar el historial del navegador con cada tecla
 * @param problema por qué el período no se puede consultar, o `null`
 */
export default function SelectorPeriodo({ periodo, hoy, problema, onCambio }) {
  const activo = atajoDe(periodo, hoy)
  const esRango = periodo.tipo === 'RANGO'
  const antes = anterior(periodo, hoy)
  const despues = siguiente(periodo, hoy)
  const elegir = (nuevo, opciones) => onCambio({ ...nuevo, gastosDelMes: periodo.gastosDelMes }, opciones)

  const claseAtajo = (marcado) => (marcado ? estilos.atajoActivo : estilos.atajo)

  return (
    <section className={estilos.selector} aria-label="Período del reporte">
      <div className={estilos.atajos} role="group" aria-label="Períodos">
        {ATAJOS.map(([atajo, texto]) => (
          <button key={atajo} type="button" aria-pressed={activo === atajo} className={claseAtajo(activo === atajo)}
            onClick={() => elegir(periodoDelAtajo(atajo, hoy))}>
            {texto}
          </button>
        ))}
        <button type="button" aria-pressed={esRango} className={claseAtajo(esRango)}
          onClick={() => elegir({ tipo: 'RANGO', desde: periodo.desde, hasta: periodo.hasta })}>
          Rango
        </button>
      </div>

      <div className={estilos.navegacion}>
        {esRango ? (
          <div className={estilos.rango}>
            <div className={comun.filtro}>
              <label className={comun.etiquetaFiltro} htmlFor="resultados-desde">Desde</label>
              <input id="resultados-desde" type="date" max={hoy} value={periodo.desde}
                className={`${comun.control} ${problema ? comun.controlError : ''}`}
                onChange={(e) => elegir({ ...periodo, desde: e.target.value }, { reemplazar: true })} />
            </div>
            <div className={comun.filtro}>
              <label className={comun.etiquetaFiltro} htmlFor="resultados-hasta">Hasta</label>
              <input id="resultados-hasta" type="date" max={hoy} value={periodo.hasta}
                className={`${comun.control} ${problema ? comun.controlError : ''}`}
                onChange={(e) => elegir({ ...periodo, hasta: e.target.value }, { reemplazar: true })} />
            </div>
            {!problema && <h2 className={estilos.tituloPeriodo}>{tituloDelPeriodo(periodo)}</h2>}
          </div>
        ) : (
          <div className={estilos.flechas}>
            <button type="button" className={estilos.flecha} disabled={!antes} onClick={() => elegir(antes)}
              aria-label={`${NOMBRE[periodo.tipo]} anterior`} title={`${NOMBRE[periodo.tipo]} anterior`}>‹</button>
            <h2 className={estilos.tituloPeriodo} aria-live="polite">{tituloDelPeriodo(periodo)}</h2>
            <button type="button" className={estilos.flecha} disabled={!despues} onClick={() => elegir(despues)}
              aria-label={`${NOMBRE[periodo.tipo]} siguiente`} title={`${NOMBRE[periodo.tipo]} siguiente`}>›</button>
          </div>
        )}

        <div className={estilos.modo}>
          <span>Gastos del mes <AyudaDe clave="gastosDelMes" /></span>
          <Segmento
            etiqueta="Cómo se leen los gastos del mes"
            valor={periodo.gastosDelMes}
            opciones={[['REPARTIDOS', 'Repartidos día por día'], ['SOLO_EN_EL_MES', 'Solo en el mes']]}
            onCambio={(gastosDelMes) => onCambio({ ...periodo, gastosDelMes })}
          />
        </div>
      </div>

      {problema && <p className={comun.errorFiltro} role="alert"><span aria-hidden>⚠</span> {problema}</p>}
    </section>
  )
}
