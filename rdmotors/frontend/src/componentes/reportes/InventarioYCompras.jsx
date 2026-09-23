import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import AvisoCarga from '../AvisoCarga'
import { comprasApi, inventarioApi } from '../../api/cliente'
import { formatoCOP } from '../../utils/formato'
import comun from '../../paginas/Listado.module.css'
import estilos from './Resultados.module.css'

/**
 * P3 del reporte (spec 0007): el inventario de hoy (RF-025) y la mercancía comprada en el período (RF-026).
 *
 * Son datos aparte, con sus propias consultas: si una falla, dice por qué en su tarjeta y se reintenta sola; el resto
 * del reporte no se entera. Ninguna de las dos mueve la utilidad.
 *
 * @param periodo `{ desde, hasta }`; `null` si el período tiene un problema (entonces no se piden compras)
 */
export default function InventarioYCompras({ periodo }) {
  return (
    <div className={estilos.dosColumnas}>
      <InventarioHoy />
      <CompradoEnElPeriodo periodo={periodo} />
    </div>
  )
}

function InventarioHoy() {
  const [intento, setIntento] = useState(0)
  const [carga, setCarga] = useState({ intento: -1, datos: null, error: null })

  useEffect(() => {
    let vigente = true
    inventarioApi.resumen()
      .then((datos) => { if (vigente) setCarga({ intento, datos, error: null }) })
      .catch((error) => { if (vigente) setCarga({ intento, datos: null, error }) })
    return () => { vigente = false }
  }, [intento])

  const d = carga.datos
  return (
    <section aria-labelledby="inventario-hoy">
      <h2 className={estilos.seccion} id="inventario-hoy">Inventario hoy</h2>
      <div className={estilos.panel}>
        <AvisoCarga error={carga.error} onReintentar={() => setIntento((n) => n + 1)} />
        {!d && !carga.error && <p className={estilos.nota}>Cargando el inventario…</p>}
        {d && (
          <>
            <ul className={estilos.lista}>
              <li><span>Vale, al costo</span><span className={estilos.monto}>{formatoCOP(d.valor)}</span></li>
              <li><span>Repuestos con stock bajo</span><span className={estilos.monto}>{d.conStockBajo}</span></li>
              <li><span>Repuestos sin costo</span><span className={estilos.monto}>{d.sinCosto}</span></li>
            </ul>
            <p className={estilos.nota}>
              Es una foto de <strong>hoy</strong>: no cambia con el período.{' '}
              <Link to="/inventario" className={comun.enlace}>Ver inventario</Link>
            </p>
          </>
        )}
      </div>
    </section>
  )
}

function CompradoEnElPeriodo({ periodo }) {
  const [intento, setIntento] = useState(0)
  const clave = periodo ? JSON.stringify({ desde: periodo.desde, hasta: periodo.hasta, intento }) : null
  const [carga, setCarga] = useState({ clave: null, datos: null, error: null })

  useEffect(() => {
    if (!clave) return
    const { desde, hasta } = JSON.parse(clave)
    let vigente = true
    comprasApi.totales({ desde, hasta, estado: 'VIGENTE' })
      .then((datos) => { if (vigente) setCarga({ clave, datos, error: null }) })
      // Un total de otro período no se deja a la vista como si fuera de este.
      .catch((error) => { if (vigente) setCarga({ clave, datos: null, error }) })
    return () => { vigente = false }
  }, [clave])

  if (!periodo) return null
  const cargando = carga.clave !== clave
  const d = carga.datos
  const historial = `/compras/historial?${new URLSearchParams({ desde: periodo.desde, hasta: periodo.hasta })}`

  return (
    <section aria-labelledby="comprado-en-el-periodo">
      <h2 className={estilos.seccion} id="comprado-en-el-periodo">Mercancía comprada en el período</h2>
      <div className={`${estilos.panel} ${cargando && d ? estilos.atenuado : ''}`}>
        <AvisoCarga error={cargando ? null : carga.error} onReintentar={() => setIntento((n) => n + 1)} />
        {!d && cargando && <p className={estilos.nota}>Cargando las compras…</p>}
        {d && (
          <>
            <ul className={estilos.lista}>
              <li>
                <span>Se compraron</span>
                <span className={estilos.monto}>{formatoCOP(d.total)}</span>
              </li>
              <li>
                <span>Facturas</span>
                <span className={estilos.monto}>{d.compras}</span>
              </li>
            </ul>
            <p className={estilos.nota}>
              Por la fecha de la factura, sin las anuladas. <strong>Comprar no resta de la utilidad</strong>: el costo
              entra cuando se vende.{' '}
              <Link to={historial} className={comun.enlace}>Ver compras</Link>
            </p>
          </>
        )}
      </div>
    </section>
  )
}
