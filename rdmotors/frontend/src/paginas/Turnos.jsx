import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import AvisoCarga from '../componentes/AvisoCarga'
import Boton from '../componentes/Boton'
import PestanasVenta from '../componentes/venta/PestanasVenta'
import { turnosApi } from '../api/cliente'
import { diferenciaEnPalabras, faltaExplicacion } from '../utils/arqueo'
import { fechaHora, formatoCOP } from '../utils/formato'
import { paginaDeLaUrl, rangoDePagina } from '../utils/inventario'
import comun from './Listado.module.css'
import estilos from './Caja.module.css'

const CLASE_DIFERENCIA = { FALTANTE: 'faltante', SOBRANTE: 'sobrante', CUADRA: 'cuadra' }

/**
 * Los turnos cerrados (spec 0006, H5 y RF-020), del cierre más reciente al más antiguo: lo que debería haber, lo
 * contado y la diferencia. Los faltantes resaltan, y un cierre con diferencia sin explicación se marca.
 */
export default function Turnos() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const pagina = paginaDeLaUrl(params.get('p'))
  const [intento, setIntento] = useState(0)
  const clave = JSON.stringify({ pagina, intento })
  const [listado, setListado] = useState({ clave: null, datos: null, error: null })

  useEffect(() => {
    let vigente = true
    turnosApi.cerrados({ pagina, tamano: 25 })
      .then((datos) => { if (vigente) setListado({ clave, datos, error: null }) })
      .catch((error) => { if (vigente) setListado((l) => ({ clave, datos: l.datos, error })) })
    return () => { vigente = false }
  }, [clave, pagina])

  const cargando = listado.clave !== clave
  const datos = listado.datos

  return (
    <div className={comun.pagina}>
      <PestanasVenta />
      <Link to="/vender/caja" className={comun.volver}>← Caja</Link>

      <header className={comun.encabezado}>
        <div>
          <h1 className={comun.titulo}>Turnos anteriores</h1>
          <p className={comun.subtitulo}>Del cierre más reciente al más antiguo. Abre uno para ver su detalle y reimprimir.</p>
        </div>
      </header>

      <AvisoCarga error={cargando ? null : listado.error} onReintentar={() => setIntento((n) => n + 1)}
        desactualizado={datos != null} />
      {!datos && cargando && <p className={comun.vacio}>Cargando turnos…</p>}
      {datos && datos.elementos.length === 0 && (
        <div className={comun.vacio}><p>Todavía no se ha cerrado ningún turno.</p></div>
      )}

      {datos && datos.elementos.length > 0 && (
        <>
          <div className={`scroll-x ${comun.marco} ${cargando ? comun.atenuado : ''}`} aria-busy={cargando}>
            <table className={comun.tabla}>
              <thead>
                <tr>
                  <th>Cerró</th>
                  <th>Abrió</th>
                  <th className="cifra">Debería haber</th>
                  <th className="cifra">Contado</th>
                  <th className="cifra">Diferencia</th>
                  <th>Observaciones</th>
                </tr>
              </thead>
              <tbody>
                {datos.elementos.map((t) => {
                  const diferencia = diferenciaEnPalabras(t.diferencia)
                  return (
                    <tr key={t.id} className={comun.fila}
                      onClick={(e) => { if (!e.target.closest('a')) navigate(`/vender/caja/turnos/${t.id}`) }}>
                      <td>
                        <Link to={`/vender/caja/turnos/${t.id}`} className={comun.enlace}>{fechaHora(t.cerradoEn)}</Link>
                      </td>
                      <td>{fechaHora(t.abiertoEn)}</td>
                      <td className="cifra">{formatoCOP(t.esperado)}</td>
                      <td className="cifra">{formatoCOP(t.contado)}</td>
                      <td className="cifra">
                        <span className={estilos[CLASE_DIFERENCIA[diferencia.tipo]]}>
                          {diferencia.tipo === 'FALTANTE' && <span aria-hidden>⚠ </span>}{diferencia.texto}
                        </span>
                      </td>
                      <td>
                        {faltaExplicacion(t)
                          ? <span className={estilos.sinExplicacion}>Sin explicación</span>
                          : <span className={estilos.observacion} title={t.observaciones ?? ''}>{t.observaciones ?? ''}</span>}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <nav className={comun.paginacion} aria-label="Páginas">
            <span className={comun.rango}>{rangoDePagina(datos.numero, datos.tamano, datos.total)}</span>
            <Boton variante="secundario" tamano="chico" disabled={datos.numero === 0}
              onClick={() => setParams({ p: String(datos.numero - 1) }, { replace: true })}>← Anterior</Boton>
            <Boton variante="secundario" tamano="chico" disabled={datos.numero + 1 >= datos.totalPaginas}
              onClick={() => setParams({ p: String(datos.numero + 1) }, { replace: true })}>Siguiente →</Boton>
          </nav>
        </>
      )}
    </div>
  )
}
