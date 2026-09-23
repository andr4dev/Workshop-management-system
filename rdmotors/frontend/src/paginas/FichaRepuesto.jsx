import { useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import Ayuda from '../componentes/Ayuda'
import Boton from '../componentes/Boton'
import InsigniaStock from '../componentes/inventario/InsigniaStock'
import ModalRepuestoNuevo from '../componentes/compra/ModalRepuestoNuevo'
import { repuestosApi } from '../api/cliente'
import { useSesion } from '../componentes/sesion/contexto'
import { cambiosDeFicha } from '../utils/auditoria'
import { fechaDia, fechaHora, formatoCOP, formatoCosto, GUION, margen } from '../utils/formato'
import {
  direccionesDelPromedio, documentoDe, entradaSalida, fechaLocal, nombreMovimiento,
} from '../utils/inventario'
import { esAdministrador, veCostos } from '../utils/permisos'
import estilos from './FichaRepuesto.module.css'

/**
 * Un repuesto y su kardex: qué es, cuánto hay, cuánto costó, y cada movimiento que lo trajo hasta
 * aquí.
 *
 * <p>El kardex se lee de arriba (lo último) hacia abajo (lo primero). Cada fila dice el saldo y el
 * costo promedio que quedaron DESPUÉS del movimiento, así que cualquier fila se entiende sola y un
 * error de hace meses se ve en el punto exacto donde entró.
 *
 * <p>La factura de cada movimiento abre la compra (spec 0002, H8), y debajo del kardex quedan las
 * correcciones de la ficha: quién cambió el nombre, el precio o el mínimo, y cuándo (H7).
 */
export default function FichaRepuesto() {
  // El cajero ve existencias y precio, no costos ni compras (spec 0004, decisión 1); corregir es del administrador.
  const { usuario } = useSesion()
  const conCostos = veCostos(usuario)
  const { id } = useParams()
  const location = useLocation()
  // Si se llegó desde el inventario, volver lleva a la misma lista con los mismos filtros.
  const volverA = `/inventario${location.state?.desde ?? ''}`

  const [intento, setIntento] = useState(0)
  const clave = `${id}|${intento}`
  const [estado, setEstado] = useState({
    clave: null, ficha: null, kardex: [], correcciones: [], error: null,
  })
  const [corrigiendo, setCorrigiendo] = useState(false)

  useEffect(() => {
    let vigente = true
    Promise.all([
      repuestosApi.ficha(id),
      repuestosApi.kardex(id),
      // Las correcciones son el rastro, no la ficha: si fallan, la ficha se ve igual y la sección
      // dice que no se pudieron leer (null) en vez de decir que no hay (lista vacía).
      repuestosApi.correcciones(id).catch(() => null),
    ])
      .then(([ficha, kardex, correcciones]) => {
        if (vigente) setEstado({ clave, ficha, kardex, correcciones, error: null })
      })
      .catch((error) => {
        if (vigente) setEstado({ clave, ficha: null, kardex: [], correcciones: [], error })
      })
    return () => { vigente = false }
  }, [id, clave])

  const cargando = estado.clave !== clave
  const { ficha, kardex, correcciones, error } = estado

  function fichaCorregida(actualizada) {
    setEstado((e) => ({ ...e, ficha: actualizada }))
    setCorrigiendo(false)
    // La corrección recién guardada tiene que aparecer en el rastro sin recargar la página.
    repuestosApi.correcciones(id)
      .then((lista) => setEstado((e) => ({ ...e, correcciones: lista })))
      .catch(() => setEstado((e) => ({ ...e, correcciones: null })))
  }

  const volver = <Link to={volverA} className={estilos.volver}>← Inventario</Link>

  if (cargando) {
    return <div className={estilos.pagina}>{volver}<p className={estilos.estado}>Cargando ficha…</p></div>
  }

  if (error) {
    const noExiste = error.estado === 404
    const sinRed = error.estado === 0
    return (
      <div className={estilos.pagina}>
        {volver}
        <div className={noExiste ? estilos.estado : sinRed ? estilos.avisoRed : estilos.avisoError}
          role={noExiste ? undefined : 'alert'}>
          <p>
            {!noExiste && <span aria-hidden>⚠ </span>}
            {noExiste
              ? 'Ese repuesto no existe. Puede que el enlace sea viejo.'
              : sinRed ? 'No hay conexión con el servidor.' : error.message}
          </p>
          {!noExiste && (
            <button type="button" className={estilos.reintentar}
              onClick={() => setIntento((n) => n + 1)}>
              Reintentar
            </button>
          )}
        </div>
      </div>
    )
  }

  const m = margen(ficha.costoPromedio, ficha.precio)
  const direcciones = direccionesDelPromedio(kardex)

  return (
    <div className={estilos.pagina}>
      {volver}

      {/* ── Identidad ─────────────────────────────────────────────────────── */}
      <header className={estilos.encabezado}>
        <div className={estilos.identidad}>
          <div className={estilos.etiquetas}>
            <span className={estilos.codigo}>{ficha.codigo}</span>
            <span className={estilos.categoria}>{ficha.categoria ?? 'Sin clasificar'}</span>
          </div>
          <h1 className={estilos.titulo}>{ficha.nombre}</h1>
          <p className={estilos.subtitulo}>
            <strong>{ficha.marca}</strong>
            {ficha.aplicacion && <> · {ficha.aplicacion}</>}
          </p>
        </div>
        {esAdministrador(usuario) && (
          <Boton variante="secundario" onClick={() => setCorrigiendo(true)}>Corregir ficha</Boton>
        )}
      </header>

      {/* ── Cifras ────────────────────────────────────────────────────────── */}
      <section className={estilos.cifras} aria-label="Cifras del repuesto">
        <div className={estilos.tarjeta}>
          <span className={estilos.tarjetaEtiqueta}>En inventario</span>
          <span className={estilos.tarjetaValor}>
            {ficha.stock}
            <InsigniaStock stock={ficha.stock} stockMinimo={ficha.stockMinimo} />
          </span>
          <span className={estilos.tarjetaNota}>Avisa con {ficha.stockMinimo} o menos</span>
        </div>
        {conCostos && <div className={estilos.tarjeta}>
          <span className={estilos.tarjetaEtiqueta}>Costo promedio</span>
          <span className={estilos.tarjetaValor}>{formatoCosto(ficha.costoPromedio)}</span>
          <span className={estilos.tarjetaNota}>
            {ficha.costoDesconocido ? 'Nunca se ha comprado' : 'Ponderado de todas las compras'}
          </span>
        </div>}
        <div className={estilos.tarjeta}>
          <span className={estilos.tarjetaEtiqueta}>Precio de venta</span>
          <span className={estilos.tarjetaValor}>{formatoCOP(ficha.precio)}</span>
          <span className={estilos.tarjetaNota}>Se cambia al registrar una compra</span>
        </div>
        {conCostos && <div className={estilos.tarjeta}>
          <span className={estilos.tarjetaEtiqueta}>Margen</span>
          <span className={`${estilos.tarjetaValor} ${m && m.utilidad < 0 ? estilos.enPerdida : ''}`}>
            {m ? `${m.utilidad < 0 ? '⚠ ' : ''}${m.porcentaje.toFixed(0)}%` : GUION}
          </span>
          <span className={estilos.tarjetaNota}>
            {m ? `${formatoCOP(m.utilidad)} por unidad` : 'Sin costo no hay margen'}
          </span>
        </div>}
        {conCostos && <div className={estilos.tarjeta}>
          <span className={estilos.tarjetaEtiqueta}>Valor al costo</span>
          <span className={estilos.tarjetaValor}>
            {ficha.valor == null ? GUION : formatoCOP(ficha.valor)}
          </span>
          <span className={estilos.tarjetaNota}>Unidades × costo promedio</span>
        </div>}
      </section>

      {/* ── Kardex ────────────────────────────────────────────────────────── */}
      <section className={estilos.kardex}>
        <h2 className={estilos.seccion}>Kardex</h2>

        {kardex.length === 0 ? (
          <p className={estilos.estado}>
            Sin movimientos todavía. Entra al inventario con su primera compra.
          </p>
        ) : (
          <div className={`scroll-x ${estilos.marco}`}>
            <table className={estilos.tabla}>
              <thead>
                <tr>
                  <th>Registrado</th>
                  <th>Movimiento</th>
                  <th className="cifra">Entra</th>
                  <th className="cifra">Sale</th>
                  {conCostos && <th className="cifra">Costo c/u</th>}
                  {conCostos && <th className="cifra">Costo total</th>}
                  <th className="cifra">Saldo</th>
                  {conCostos && <th className="cifra">Costo promedio</th>}
                  {conCostos && <th className="cifra">
                    Precio fijado
                    <Ayuda sobre="precio fijado" titulo="Precio fijado">
                      <span>El precio de venta que le puso al repuesto la compra de esa fila.</span>
                      <span>Solo aparece en las compras que lo cambiaron: vacío es que lo dejó igual.</span>
                    </Ayuda>
                  </th>}
                </tr>
              </thead>
              <tbody>
                {kardex.map((mov, i) => {
                  const { entrada, salida } = entradaSalida(mov.cantidad)
                  const documento = documentoDe(mov)
                  const direccion = direcciones[i]
                  return (
                    // El kardex no tiene id en la respuesta; la posición es estable porque es de
                    // solo agregar y se pinta completo.
                    <tr key={`${mov.cuando}-${i}`}>
                      <td className={estilos.fecha}>{fechaHora(mov.cuando)}</td>
                      <td>
                        <span className={estilos[`tipo${mov.tipo}`] ?? estilos.tipo}>
                          {nombreMovimiento(mov.tipo)}
                        </span>
                        {documento && (
                          <span className={estilos.documento}>
                            {mov.compraId ? (
                              <Link to={`/compras/historial/${mov.compraId}`} className={estilos.enlaceCompra}
                                state={{ desdeFicha: { id, codigo: ficha.codigo } }}
                                title="Abrir la compra">
                                {documento}
                              </Link>
                            ) : documento}
                            {mov.compraAnulada && <span className={estilos.anulada}> · anulada</span>}
                          </span>
                        )}
                        {/* Por qué la misma factura aparece dos veces: el motivo de la corrección. */}
                        {mov.motivo && <span className={estilos.motivo}>“{mov.motivo}”</span>}
                        {mov.fechaDocumento && (
                          <span className={estilos.documentoFecha}>
                            Factura del {fechaDia(fechaLocal(mov.fechaDocumento))}
                          </span>
                        )}
                      </td>
                      <td className={`cifra ${estilos.entra}`}>{entrada != null && `+${entrada}`}</td>
                      <td className={`cifra ${estilos.sale}`}>{salida != null && `−${salida}`}</td>
                      {conCostos && <td className="cifra">{formatoCosto(mov.costoUnitario)}</td>}
                      {conCostos && (
                        <td className="cifra">
                          {mov.costoTotal == null ? GUION : formatoCOP(mov.costoTotal)}
                        </td>
                      )}
                      <td className="cifra"><strong>{mov.saldoDespues}</strong></td>
                      {conCostos && <td className="cifra">
                        <span className={estilos.promedio}>
                          {direccion && (
                            <span className={estilos.flecha}
                              aria-label={direccion === 'sube' ? 'subió' : 'bajó'}
                              title={direccion === 'sube' ? 'Subió' : 'Bajó'}>
                              {direccion === 'sube' ? '▲' : '▼'}
                            </span>
                          )}
                          {formatoCosto(mov.costoPromedioDespues)}
                        </span>
                      </td>}
                      {conCostos && (
                        <td className="cifra">
                          {mov.precioVenta != null && formatoCOP(mov.precioVenta)}
                        </td>
                      )}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}

        <p className={estilos.nota}>
          El kardex no se edita ni se borra. <strong>Saldo</strong>{conCostos && <> y <strong>costo promedio</strong></>}{' '}
          {conCostos ? 'son los que quedaron' : 'es el que quedó'} después de cada movimiento.
        </p>
      </section>

      {/* ── Correcciones de la ficha ──────────────────────────────────────── */}
      <section className={estilos.kardex}>
        <h2 className={estilos.seccion}>Correcciones de la ficha</h2>
        {correcciones == null ? (
          <p className={estilos.avisoRed} role="alert">
            <span aria-hidden>⚠</span> No se pudieron leer las correcciones. Recarga la página para intentarlo de nuevo.
          </p>
        ) : correcciones.length === 0 ? (
          <p className={estilos.nota}>La ficha no se ha corregido desde que se creó.</p>
        ) : (
          <ol className={estilos.rastro}>
            {[...correcciones].reverse().map((evento) => (
              <li key={evento.ocurridoEn} className={estilos.evento}>
                <div className={estilos.eventoCabecera}>
                  <span className={estilos.accion}>Corrección</span>
                  <span className={estilos.eventoQuien}>{fechaHora(evento.ocurridoEn)}</span>
                  <span className={estilos.eventoQuien}>· {evento.quien?.nombre ?? 'quién no quedó registrado'}</span>
                </div>
                <ul className={estilos.eventoCambios}>
                  {cambiosDeFicha(evento.antes, evento.despues).map((c) => <li key={c}>{c}</li>)}
                </ul>
              </li>
            ))}
          </ol>
        )}
      </section>

      {corrigiendo && (
        <ModalRepuestoNuevo
          abierto
          codigo={ficha.codigo}
          existente={ficha}
          onCerrar={() => setCorrigiendo(false)}
          onActualizado={fichaCorregida}
        />
      )}
    </div>
  )
}
