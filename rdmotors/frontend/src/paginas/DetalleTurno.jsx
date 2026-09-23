import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import AvisoCarga from '../componentes/AvisoCarga'
import Boton from '../componentes/Boton'
import Campo from '../componentes/Campo'
import PestanasVenta from '../componentes/venta/PestanasVenta'
import ComprobanteCierre from '../componentes/caja/ComprobanteCierre'
import DesgloseArqueo from '../componentes/caja/DesgloseArqueo'
import ProducidoDelTurno from '../componentes/caja/ProducidoDelTurno'
import TablaMovimientos from '../componentes/caja/TablaMovimientos'
import AbonosDelTurno from '../componentes/caja/AbonosDelTurno'
import { turnosApi } from '../api/cliente'
import { fechaHora, formatoCOP } from '../utils/formato'
import cajaEstilos from '../componentes/caja/Caja.module.css'
import comun from './Listado.module.css'
import estilos from './Caja.module.css'

const HORA = new Intl.DateTimeFormat('es-CO', { hour: 'numeric', minute: '2-digit', timeZone: 'America/Bogota' })

/**
 * Un turno cerrado (spec 0006, H5, H6 y RF-020): su desglose, sus observaciones (se escriben aquí si quedaron
 * pendientes), lo que salió del cajón, sus ventas y el comprobante para reimprimir.
 *
 * Un turno abierto se ve en la sección Caja, con lo que debería haber en vivo.
 */
export default function DetalleTurno() {
  const { id } = useParams()
  const [intento, setIntento] = useState(0)
  const [carga, setCarga] = useState({ clave: null, turno: null, error: null })
  const clave = `${id}:${intento}`
  const [observaciones, setObservaciones] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [errorObservaciones, setErrorObservaciones] = useState(null)

  useEffect(() => {
    let vigente = true
    turnosApi.detalle(id)
      .then((turno) => { if (vigente) setCarga({ clave, turno, error: null }) })
      .catch((error) => { if (vigente) setCarga({ clave, turno: null, error }) })
    return () => { vigente = false }
  }, [id, clave])

  const cargando = carga.clave !== clave
  const { turno } = carga

  async function guardarObservaciones() {
    if (!observaciones.trim()) {
      setErrorObservaciones('Escribe qué pudo pasar')
      return
    }
    setGuardando(true)
    setErrorObservaciones(null)
    try {
      const actualizado = await turnosApi.observaciones(id, observaciones.trim())
      setCarga({ clave, turno: actualizado, error: null })
    } catch (e) {
      setErrorObservaciones(e.message)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <div className={comun.pagina}>
      <PestanasVenta />
      <Link to="/vender/caja/turnos" className={comun.volver}>← Turnos anteriores</Link>

      {cargando && !turno && <p className={comun.vacio}>Cargando turno…</p>}
      {!cargando && carga.error && (
        <AvisoCarga error={carga.error.estado === 404 ? { ...carga.error, message: 'Ese turno no existe' } : carga.error}
          onReintentar={carga.error.estado === 404 ? null : () => setIntento((n) => n + 1)} />
      )}

      {turno && turno.estado === 'ABIERTO' && (
        <div className={comun.vacio}>
          <p>Este turno sigue abierto, desde {fechaHora(turno.abiertoEn)}: se ve y se cierra en la caja.</p>
          <Link to="/vender/caja" className={comun.accion}>Ir a la caja</Link>
        </div>
      )}

      {turno && turno.cierre && (
        <>
          <header className={comun.encabezado}>
            <div>
              <h1 className={comun.titulo}>
                Turno cerrado el {fechaHora(turno.cerradoEn)}{turno.cerradoPor && ` por ${turno.cerradoPor.nombre}`}
              </h1>
              <p className={comun.subtitulo}>
                {turno.abiertoPor ? `Lo abrió ${turno.abiertoPor.nombre} el` : 'Abrió el'}{' '}
                {fechaHora(turno.abiertoEn)} con {formatoCOP(turno.fondo)} de fondo · {turno.ventas.length}{' '}
                {turno.ventas.length === 1 ? 'venta' : 'ventas'}
              </p>
            </div>
          </header>

          <div className={estilos.columnas}>
            <div className={estilos.datos}>
              <ProducidoDelTurno turno={turno} />

              <section className={estilos.panel} aria-label="El arqueo">
                <h2 className={estilos.panelTitulo}>El arqueo · por esto se responde</h2>
                <DesgloseArqueo turno={turno} />
              </section>

              <section className={estilos.panel} aria-label="Observaciones">
                <h2 className={estilos.panelTitulo}>Observaciones</h2>
                {turno.observaciones ? (
                  <p className={cajaEstilos.observaciones}>{turno.observaciones}</p>
                ) : (
                  <>
                    {turno.cierre.diferencia !== 0 && (
                      <p className={estilos.sinExplicacion}>Sin explicación</p>
                    )}
                    <Campo etiqueta="¿Qué pudo pasar?" error={errorObservaciones}
                      ayuda="Queda con el cierre y se escribe una sola vez.">
                      <textarea className={cajaEstilos.textarea} rows={3} maxLength={500} value={observaciones}
                        onChange={(e) => { setObservaciones(e.target.value); setErrorObservaciones(null) }} />
                    </Campo>
                    <div className={cajaEstilos.acciones}>
                      <Boton variante="secundario" onClick={guardarObservaciones} disabled={guardando}>
                        {guardando ? 'Guardando…' : 'Guardar observaciones'}
                      </Boton>
                    </div>
                  </>
                )}
              </section>

              <section aria-label="Lo que salió del cajón">
                <h2 className={estilos.seccionTitulo}>Lo que salió del cajón</h2>
                <TablaMovimientos turno={turno} />
              </section>

              <section aria-label="Abonos de clientes">
                <h2 className={estilos.seccionTitulo}>Abonos de clientes</h2>
                <AbonosDelTurno abonos={turno.abonos} />
              </section>

              <section aria-label="Ventas del turno">
                <h2 className={estilos.seccionTitulo}>Ventas del turno</h2>
                {turno.ventas.length === 0 ? (
                  <div className={comun.vacio}><p>No hubo ventas en este turno.</p></div>
                ) : (
                  <div className={`scroll-x ${comun.marco}`}>
                    <table className={comun.tabla} style={{ minWidth: 560 }}>
                      <thead>
                        <tr>
                          <th className="cifra">N.º</th>
                          <th>Hora</th>
                          <th className="cifra">Efectivo</th>
                          <th className="cifra">Transferencia</th>
                          <th className="cifra">Fiado</th>
                          <th className="cifra">Total</th>
                        </tr>
                      </thead>
                      <tbody>
                        {turno.ventas.map((v) => (
                          <tr key={v.id}>
                            <td className="cifra">
                              <Link to={`/vender/ventas/${v.id}`} className={comun.enlace}>{v.numero}</Link>
                            </td>
                            <td>
                              {HORA.format(new Date(v.cobradaEn))}
                              {v.estado === 'ANULADA' && <span className={comun.anulada}>Anulada</span>}
                            </td>
                            <td className="cifra">{formatoCOP(v.efectivo)}</td>
                            <td className="cifra">{formatoCOP(v.transferencia)}</td>
                            <td className="cifra">{v.fiado > 0 ? formatoCOP(v.fiado) : ''}</td>
                            <td className="cifra"><strong>{formatoCOP(v.total)}</strong></td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
            </div>

            <aside className={estilos.lateral}>
              <ComprobanteCierre turno={turno} />
            </aside>
          </div>
        </>
      )}
    </div>
  )
}
