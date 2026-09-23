import { useEffect, useState } from 'react'
import { Link, useLocation, useParams, useSearchParams } from 'react-router-dom'
import AvisoCarga from '../componentes/AvisoCarga'
import Boton from '../componentes/Boton'
import Ayuda from '../componentes/Ayuda'
import DesgloseCambios from '../componentes/DesgloseCambios'
import ModalMotivo from '../componentes/ModalMotivo'
import Resaltado from '../componentes/Resaltado'
import PestanasCompras from '../componentes/compra/PestanasCompras'
import { comprasApi } from '../api/cliente'
import { desgloseDeCambios } from '../utils/auditoria'
import { textoDelPago } from '../utils/compra'
import { fechaDia, fechaHora, formatoCOP, formatoCosto } from '../utils/formato'
import { muestraLaBusqueda, queLePasoAlRenglon, sumaDeRenglones } from '../utils/historial'
import { fechaLocal } from '../utils/inventario'
import comun from './Listado.module.css'
import estilos from './DetalleCompra.module.css'

const MODO = { TOTAL: 'Por total', UNITARIO: 'Por unidad' }
const ACCION = { CORREGIR_COMPRA: 'Corrección', ANULAR_COMPRA: 'Anulación' }

/** El título de la columna con su explicación a un gesto, en vez de un párrafo debajo de la tabla. */
function PrecioFijado({ conHoy = false }) {
  return (
    <>
      Precio fijado
      <Ayuda sobre="precio fijado" titulo="Precio fijado">
        <span>El precio de venta que esta compra le puso al repuesto.</span>
        <span><em>sin cambio</em>: la compra dejó el precio como estaba.</span>
        {conHoy && <span><em>hoy $…</em>: el precio cambió después, y así vale hoy.</span>}
      </Ayuda>
    </>
  )
}

/**
 * Una factura completa, renglón por renglón y en el orden en que se capturó (spec 0002, RF-008).
 *
 * <p>Desde aquí se corrige o se anula (H3, H4, H5), y aquí queda el rastro: qué cambió, cuándo, por
 * qué, y cómo estaban los renglones antes de cada corrección.
 *
 * <p>El total se compara contra la suma de los renglones. Si no cuadran, la pantalla lo dice en
 * rojo en vez de mostrar un total bonito: una compra que no cuadra con su propio desglose es un
 * dato mal guardado, y esconderlo es peor que verlo.
 */
export default function DetalleCompra() {
  const { id } = useParams()
  const location = useLocation()
  // Si se llegó desde el historial, volver lleva a la misma lista con los mismos filtros; si se llegó
  // desde el kardex de un repuesto (H8), vuelve a esa ficha.
  const desdeFicha = location.state?.desdeFicha
  const volverA = desdeFicha
    ? `/inventario/${desdeFicha.id}`
    : `/compras/historial${location.state?.desde ?? ''}`

  // Lo que se buscaba en la lista, si se abrió desde una búsqueda de repuesto (RF-026).
  const [params, setParams] = useSearchParams()
  const busqueda = (params.get('repuesto') ?? '').trim()

  const [intento, setIntento] = useState(0)
  const clave = `${id}|${busqueda}|${intento}`
  const [estado, setEstado] = useState({ clave: null, compra: null, error: null })

  // Lo que hay que avisar tras corregir (llega de la pantalla de corregir) o tras anular.
  const [aviso, setAviso] = useState(() => location.state?.mensaje
    ? { mensaje: location.state.mensaje, avisos: location.state.avisos ?? [] }
    : null)
  const [anulando, setAnulando] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [errorAnular, setErrorAnular] = useState(null)

  useEffect(() => {
    let vigente = true
    comprasApi.detalle(id, busqueda)
      .then((compra) => { if (vigente) setEstado({ clave, compra, error: null }) })
      .catch((error) => { if (vigente) setEstado({ clave, compra: null, error }) })
    return () => { vigente = false }
  }, [id, busqueda, clave])

  // Conserva el estado de la navegación: sin él, "← Historial" perdería los filtros de la lista.
  const quitarResaltado = () => setParams({}, { replace: true, state: location.state })

  const cargando = estado.clave !== clave
  const { compra, error } = estado
  const volver = (
    <Link to={volverA} className={comun.volver}>
      {desdeFicha ? `← Kardex de ${desdeFicha.codigo}` : '← Historial'}
    </Link>
  )

  async function anular(motivo) {
    setEnviando(true)
    setErrorAnular(null)
    try {
      const respuesta = await comprasApi.anular(compra.id, { version: compra.version, motivo })
      // La respuesta de anular no trae la búsqueda. Anular no cambia los renglones, así que se
      // conserva qué estaba resaltado en vez de apagarlo.
      setEstado((e) => {
        const coincidian = new Set(e.compra.renglones.filter((r) => r.coincide).map((r) => r.lineaId))
        return {
          ...e,
          compra: {
            ...respuesta.compra,
            renglones: respuesta.compra.renglones.map((r) => ({ ...r, coincide: coincidian.has(r.lineaId) })),
          },
        }
      })
      setAviso({ mensaje: 'Compra anulada', avisos: respuesta.avisos })
      setAnulando(false)
    } catch (e) {
      // El modal queda abierto con el motivo: si hay renglones bloqueados, el mensaje dice cuáles.
      setErrorAnular(e.message)
    } finally {
      setEnviando(false)
    }
  }

  if (cargando) {
    return (
      <div className={comun.pagina}>
        <PestanasCompras />
        {volver}
        <p className={comun.vacio}>Cargando compra…</p>
      </div>
    )
  }

  if (error) {
    return (
      <div className={comun.pagina}>
        <PestanasCompras />
        {volver}
        {error.estado === 404 ? (
          <div className={comun.vacio}>
            <p>Esa compra no existe. Puede que el enlace sea viejo.</p>
            <Link to="/compras/historial" className={comun.accion}>Ver el historial</Link>
          </div>
        ) : (
          <AvisoCarga error={error} onReintentar={() => setIntento((n) => n + 1)} />
        )}
      </div>
    )
  }

  const anulada = compra.estado === 'ANULADA'
  const suma = sumaDeRenglones(compra.renglones)
  const coinciden = compra.renglones.filter((r) => r.coincide).length
  const unidades = compra.renglones.reduce((total, r) => total + r.cantidad, 0)

  return (
    <div className={comun.pagina}>
      <PestanasCompras />
      {volver}

      {aviso && (
        <div className={estilos.exito} role="status">
          <p><span aria-hidden>✓</span> {aviso.mensaje}</p>
          {aviso.avisos.length > 0 && (
            <ul>{aviso.avisos.map((a) => <li key={a}>{a}</li>)}</ul>
          )}
        </div>
      )}

      {anulada && (
        <div className={estilos.anulada} role="note">
          <strong>Compra anulada</strong> el {fechaHora(compra.anuladaEn)}
          {compra.anuladaPor && <> por {compra.anuladaPor.nombre}</>}
          {' · '}motivo: {compra.motivoAnulacion}
          <span className={estilos.anuladaNota}>
            Lo que metió al inventario ya salió. No cuenta en los totales.
          </span>
        </div>
      )}

      {/* ── Cabecera ──────────────────────────────────────────────────────── */}
      <header className={comun.encabezado}>
        <div>
          <div className={estilos.etiquetas}>
            {compra.numeroFactura
              ? <span className={estilos.factura}>{compra.numeroFactura}</span>
              : <span className={estilos.sinNumero}>Sin número de factura</span>}
          </div>
          <h1 className={comun.titulo}>{compra.proveedor}</h1>
          <p className={comun.subtitulo}>
            Factura del <strong>{fechaDia(fechaLocal(compra.fechaDocumento))}</strong>
            {' · '}registrada el {fechaHora(compra.fechaRegistro)}
            {compra.registradoPor && <> por {compra.registradoPor.nombre}</>}
            {compra.modificadaEn && !anulada && <> · corregida el {fechaHora(compra.modificadaEn)}</>}
          </p>
        </div>

        {!anulada && (
          <div className={estilos.acciones}>
            <Link to={`/compras/historial/${compra.id}/corregir`} className={comun.accion}>
              Corregir
            </Link>
            <Boton variante="peligro" onClick={() => { setErrorAnular(null); setAnulando(true) }}>
              Anular
            </Boton>
          </div>
        )}
      </header>

      <section className={estilos.cifras} aria-label="Resumen de la compra">
        <div className={estilos.tarjeta}>
          <span className={estilos.tarjetaEtiqueta}>Total de la compra</span>
          <span className={estilos.tarjetaValor}>{formatoCOP(compra.total)}</span>
        </div>
        <div className={estilos.tarjeta}>
          <span className={estilos.tarjetaEtiqueta}>Pago</span>
          <span className={estilos.tarjetaValor}>{textoDelPago(compra.formaPago, compra.cuenta, compra.pagadaDeCaja)}</span>
        </div>
        <div className={estilos.tarjeta}>
          <span className={estilos.tarjetaEtiqueta}>Renglones</span>
          <span className={estilos.tarjetaValor}>{compra.renglones.length}</span>
        </div>
        <div className={estilos.tarjeta}>
          <span className={estilos.tarjetaEtiqueta}>Unidades</span>
          <span className={estilos.tarjetaValor}>{unidades}</span>
        </div>
      </section>

      {/* ── Renglones ─────────────────────────────────────────────────────── */}
      <section className={estilos.renglones}>
        {busqueda && (
          <div className={estilos.avisoBusqueda} role="status">
            <span>
              {coinciden > 0 ? (
                <>
                  Resaltado lo que buscaste: <strong>«{busqueda}»</strong>
                  {' · '}{coinciden} de {compra.renglones.length}{' '}
                  {compra.renglones.length === 1 ? 'renglón' : 'renglones'}
                </>
              ) : (
                <>Ningún renglón de esta compra tiene un repuesto con <strong>«{busqueda}»</strong>.</>
              )}
            </span>
            <Boton variante="fantasma" tamano="chico" onClick={quitarResaltado}>Quitar resaltado</Boton>
          </div>
        )}
        <div className={`scroll-x ${comun.marco}`}>
          <table className={comun.tabla}>
            <thead>
              <tr>
                <th className="cifra">#</th>
                <th>Código</th>
                <th>Repuesto</th>
                <th className="cifra">Cant.</th>
                <th>Costo capturado</th>
                <th className="cifra">Costo c/u</th>
                <th className="cifra">Total pagado</th>
                <th className="cifra"><PrecioFijado conHoy /></th>
              </tr>
            </thead>
            <tbody>
              {compra.renglones.map((r, i) => {
                // Se marca lo que el backend dijo que coincide; aquí solo se dibuja.
                const marcar = r.coincide ? busqueda : ''
                const porAplicacion = r.coincide && ![r.codigo, r.nombre, r.marca]
                  .some((texto) => muestraLaBusqueda(texto, busqueda))
                return (
                <tr key={r.lineaId} className={r.coincide ? estilos.filaResaltada : undefined}>
                  <td className={`cifra ${comun.tenue}`}>{i + 1}</td>
                  <td className={comun.mono}><Resaltado texto={r.codigo} busqueda={marcar} /></td>
                  <td>
                    {/* A la ficha del repuesto: ahí está su kardex con esta misma compra. */}
                    <Link to={`/inventario/${r.varianteId}`} className={comun.enlace}>
                      <Resaltado texto={r.nombre} busqueda={marcar} />
                    </Link>
                    <span className={estilos.marca}><Resaltado texto={r.marca} busqueda={marcar} /></span>
                    {/* Coincidió por la moto, que la tabla no muestra: sin esto no se entiende por qué salió. */}
                    {porAplicacion && (
                      <span className={estilos.aplicacion}>
                        Aplica a: <Resaltado texto={r.aplicacion} busqueda={marcar} />
                      </span>
                    )}
                  </td>
                  <td className="cifra">{r.cantidad}</td>
                  <td className={comun.tenue}>{MODO[r.modoCaptura] ?? r.modoCaptura}</td>
                  <td className="cifra">{formatoCosto(r.costoUnitario)}</td>
                  <td className="cifra"><strong>{formatoCOP(r.costoTotal)}</strong></td>
                  <td className="cifra">
                    {r.precioVenta == null ? (
                      <span className={comun.tenue}>sin cambio</span>
                    ) : (
                      <>
                        {formatoCOP(r.precioVenta)}
                        {r.precioActual !== r.precioVenta && (
                          <span className={estilos.hoy}>hoy {formatoCOP(r.precioActual)}</span>
                        )}
                      </>
                    )}
                  </td>
                </tr>
                )
              })}
            </tbody>
            <tfoot>
              <tr className={estilos.filaTotal}>
                <td colSpan={6}>Total de la compra</td>
                <td className="cifra">{formatoCOP(compra.total)}</td>
                <td />
              </tr>
              {suma !== compra.total && (
                <tr>
                  <td colSpan={8} className={estilos.descuadre} role="alert">
                    <span aria-hidden>⚠</span> La suma de los renglones ({formatoCOP(suma)}) no
                    cuadra con el total guardado ({formatoCOP(compra.total)}).
                  </td>
                </tr>
              )}
            </tfoot>
          </table>
        </div>
      </section>

      {/* ── Cómo estaban antes de corregir ─────────────────────────────────── */}
      {compra.reemplazados.length > 0 && (
        <section className={estilos.seccion}>
          <h2 className={estilos.seccionTitulo}>Renglones antes de corregir</h2>
          <p className={estilos.seccionNota}>
            Así estaban los renglones que se cambiaron o se quitaron en una corrección. Se guardan como
            historial: no suman en el total ni en el inventario.
          </p>
          <div className={`scroll-x ${comun.marco} ${estilos.historico}`}>
            <table className={comun.tabla}>
              <thead>
                <tr>
                  <th>Código</th>
                  <th>Repuesto</th>
                  <th className="cifra">Cant.</th>
                  <th className="cifra">Total pagado</th>
                  <th className="cifra"><PrecioFijado /></th>
                  <th>Qué pasó</th>
                  <th>Corregido el</th>
                </tr>
              </thead>
              <tbody>
                {compra.reemplazados.map((r) => (
                  <tr key={r.lineaId}>
                    <td className={comun.mono}>{r.codigo}</td>
                    <td>{r.nombre} <span className={comun.tenue}>{r.marca}</span></td>
                    <td className="cifra">{r.cantidad}</td>
                    <td className="cifra">{formatoCOP(r.costoTotal)}</td>
                    <td className="cifra">
                      {r.precioVenta == null ? <span className={comun.tenue}>sin cambio</span>
                        : formatoCOP(r.precioVenta)}
                    </td>
                    <td>
                      {queLePasoAlRenglon(r, compra) === 'QUITADO'
                        ? <span className={estilos.pasoQuitado}>Se quitó</span>
                        : <span className={estilos.pasoCambiado}>Se cambió</span>}
                    </td>
                    <td className={comun.tenue}>{fechaHora(r.reemplazadaEn)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {/* ── Rastro ────────────────────────────────────────────────────────── */}
      {compra.correcciones.length > 0 && (
        <section className={estilos.seccion}>
          <h2 className={estilos.seccionTitulo}>Correcciones</h2>
          <ol className={estilos.rastro}>
            {[...compra.correcciones].reverse().map((evento) => (
              <li key={`${evento.accion}-${evento.ocurridoEn}`} className={estilos.evento}>
                <div className={estilos.eventoCabecera}>
                  <span className={evento.accion === 'ANULAR_COMPRA' ? estilos.accionAnular : estilos.accion}>
                    {ACCION[evento.accion] ?? evento.accion}
                  </span>
                  <span className={comun.tenue}>{fechaHora(evento.ocurridoEn)}</span>
                  <span className={comun.tenue}>· {evento.quien?.nombre ?? 'quién no quedó registrado'}</span>
                </div>
                <p className={estilos.eventoMotivo}>“{evento.motivo}”</p>
                <DesgloseCambios desglose={desgloseDeCambios(evento.antes, evento.despues)} compacto />
              </li>
            ))}
          </ol>
        </section>
      )}

      {anulando && (
        <ModalMotivo
          titulo="Anular compra"
          descripcion={`Todo lo que esta compra metió al inventario va a salir: ${compra.renglones.length} `
            + `${compra.renglones.length === 1 ? 'renglón' : 'renglones'}, ${unidades} unidades. `
            + 'La compra queda en el historial marcada como anulada y no cuenta en los totales.'}
          textoConfirmar="Anular compra"
          peligro
          enviando={enviando}
          error={errorAnular}
          onConfirmar={anular}
          onCerrar={() => { if (!enviando) setAnulando(false) }}
        />
      )}
    </div>
  )
}
