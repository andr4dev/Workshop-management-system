import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import AvisoCarga from '../componentes/AvisoCarga'
import Boton from '../componentes/Boton'
import ModalMotivo from '../componentes/ModalMotivo'
import ComprobanteVenta from '../componentes/venta/ComprobanteVenta'
import PestanasVenta from '../componentes/venta/PestanasVenta'
import { ventasApi } from '../api/cliente'
import { fechaHora, formatoCOP } from '../utils/formato'
import { textoDelPago } from '../utils/ventasDelTurno'
import comun from './Listado.module.css'
import estilos from './DetalleVenta.module.css'

const FORMAS = { EFECTIVO: 'Efectivo', TRANSFERENCIA: 'Transferencia' }

/**
 * Una venta (spec 0003, H7 y RF-022): qué se vendió, cómo se pagó, y su comprobante al lado para
 * reimprimirlo. Aquí llega la búsqueda por número.
 *
 * <p>Y aquí se anula (H8): con motivo, el stock vuelve y la venta queda marcada. No se edita: si quedó
 * mal, se anula y se vende de nuevo (RF-027).
 */
export default function DetalleVenta() {
  const { id } = useParams()
  const [intento, setIntento] = useState(0)
  const clave = `${id}|${intento}`
  const [carga, setCarga] = useState({ clave: null, venta: null, error: null })
  const [anulando, setAnulando] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [errorAnular, setErrorAnular] = useState(null)
  const [aviso, setAviso] = useState(null)

  useEffect(() => {
    let vigente = true
    ventasApi.detalle(id)
      .then((venta) => { if (vigente) setCarga({ clave, venta, error: null }) })
      .catch((error) => { if (vigente) setCarga({ clave, venta: null, error }) })
    return () => { vigente = false }
  }, [id, clave])

  const volver = <Link to="/vender/ventas" className={comun.volver}>← Ventas del turno</Link>

  if (carga.clave !== clave) {
    return (
      <div className={comun.pagina}>
        <PestanasVenta />
        {volver}
        <p className={comun.vacio}>Cargando venta…</p>
      </div>
    )
  }

  if (carga.error) {
    return (
      <div className={comun.pagina}>
        <PestanasVenta />
        {volver}
        {carga.error.estado === 404 ? (
          <div className={comun.vacio}>
            <p>Esa venta no existe. Puede que el enlace sea viejo.</p>
            <Link to="/vender/ventas" className={comun.accion}>Ver las ventas del turno</Link>
          </div>
        ) : (
          <AvisoCarga error={carga.error} onReintentar={() => setIntento((n) => n + 1)} />
        )}
      </div>
    )
  }

  const { venta } = carga
  const anulada = venta.estado === 'ANULADA'

  async function anular(motivo) {
    setEnviando(true)
    setErrorAnular(null)
    try {
      const respuesta = await ventasApi.anular(venta.id, motivo)
      setCarga((c) => ({ ...c, venta: respuesta }))
      setAnulando(false)
      setAviso('Venta anulada. El stock de sus repuestos volvió al inventario.')
    } catch (e) {
      if (e.estado === 422 && /ya fue anulada/i.test(e.message)) {
        // Otra pestaña, o un reintento tras un corte que sí llegó: se muestra como quedó.
        setAnulando(false)
        setAviso('Esta venta ya estaba anulada.')
        setIntento((n) => n + 1)
      } else {
        // El modal queda abierto con el motivo escrito: sin turno, se abre y se reintenta.
        setErrorAnular(e.estado === 0 ? 'No hay conexión con el servidor. No se anuló.' : e.message)
      }
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className={comun.pagina}>
      <PestanasVenta />
      {volver}

      <header className={comun.encabezado}>
        <div>
          <h1 className={comun.titulo}>
            Venta N.º {venta.numero}
            {anulada && <span className={comun.anulada}>Anulada</span>}
            {venta.descuento > 0 && <span className={estilos.conDescuento}>Con descuento</span>}
          </h1>
          <p className={comun.subtitulo}>
            Cobrada el {fechaHora(venta.cobradaEn)}
            {venta.vendidoPor && <span className={comun.tenue}> · la cobró {venta.vendidoPor.nombre}</span>}
          </p>
        </div>
        {!anulada && (
          <Boton variante="peligro" onClick={() => { setErrorAnular(null); setAnulando(true) }}>Anular venta</Boton>
        )}
      </header>

      {aviso && <p className={estilos.aviso} role="status">{aviso}</p>}

      {anulada && (
        <p className={estilos.anulada} role="note">
          <strong>Anulada</strong> el {fechaHora(venta.anuladaEn)}
          {venta.anuladaPor && ` por ${venta.anuladaPor.nombre}`}: {venta.motivoAnulacion}
        </p>
      )}

      <div className={estilos.columnas}>
        <div className={estilos.datos}>
          <section className={estilos.cifras} aria-label="Resumen">
            <div className={estilos.tarjeta}>
              <span className={estilos.tarjetaEtiqueta}>Total</span>
              <span className={`${estilos.tarjetaValor} ${anulada ? comun.tachado : ''}`}>{formatoCOP(venta.total)}</span>
            </div>
            <div className={estilos.tarjeta}>
              <span className={estilos.tarjetaEtiqueta}>Pago</span>
              <span className={estilos.tarjetaValor}>{textoDelPago(venta.pagos, venta.fiado)}</span>
            </div>
            <div className={estilos.tarjeta}>
              <span className={estilos.tarjetaEtiqueta}>Cambio</span>
              <span className={estilos.tarjetaValor}>{formatoCOP(venta.cambio)}</span>
            </div>
          </section>

          <div className={`scroll-x ${comun.marco}`}>
            <table className={`${comun.tabla} ${estilos.tablaRenglones}`}>
              <thead>
                <tr>
                  <th>Código</th>
                  <th>Repuesto</th>
                  <th className="cifra">Cant.</th>
                  <th className="cifra">Precio</th>
                  <th className="cifra">Total</th>
                </tr>
              </thead>
              <tbody>
                {venta.renglones.map((r) => (
                  <tr key={r.lineaId}>
                    <td className={comun.mono}>{r.codigo}</td>
                    <td>
                      {/* A la ficha: ahí está su kardex con esta misma venta. */}
                      <Link to={`/inventario/${r.varianteId}`} className={comun.enlace}>{r.nombre}</Link>
                      <span className={estilos.marca}>{r.marca}</span>
                    </td>
                    <td className="cifra">{r.cantidad}</td>
                    <td className="cifra">{formatoCOP(r.precioUnitario)}</td>
                    <td className="cifra"><strong>{formatoCOP(r.total)}</strong></td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                {venta.descuento > 0 && (
                  <>
                    <tr className={estilos.filaCifra}>
                      <td colSpan={4}>Subtotal</td>
                      <td className="cifra">{formatoCOP(venta.subtotal)}</td>
                    </tr>
                    <tr className={estilos.filaCifra}>
                      <td colSpan={4}>
                        Descuento
                        {venta.descuentoModo === 'PORCENTAJE' && ` (${Number(venta.descuentoPorcentaje).toLocaleString('es-CO')} %)`}
                        <span className={estilos.motivo}>{venta.descuentoMotivo}</span>
                      </td>
                      <td className="cifra">−{formatoCOP(venta.descuento)}</td>
                    </tr>
                  </>
                )}
                <tr className={estilos.filaTotal}>
                  <td colSpan={4}>Total</td>
                  <td className="cifra">{formatoCOP(venta.total)}</td>
                </tr>
              </tfoot>
            </table>
          </div>

          <section className={estilos.pagos} aria-label="Pagos">
            <h2 className={estilos.seccionTitulo}>Cómo pagó</h2>
            {venta.pagos.length === 0 && venta.fiado === 0 && (
              <p className={comun.tenue}>Sin pago: el descuento dejó la venta en $0.</p>
            )}
            <ul>
              {venta.pagos.map((p) => (
                <li key={p.forma}>
                  <span>{FORMAS[p.forma] ?? p.forma}</span>
                  <strong>{formatoCOP(p.monto)}</strong>
                  {p.recibido != null && (
                    <span className={estilos.recibido}>
                      pagó con {formatoCOP(p.recibido)} · cambio {formatoCOP(p.cambio)}
                    </span>
                  )}
                </li>
              ))}
              {venta.fiado > 0 && (
                <li>
                  <span>Fiado</span>
                  <strong>{formatoCOP(venta.fiado)}</strong>
                  <span className={estilos.recibido}>
                    a {venta.cliente?.nombre}
                    {venta.debeDespues != null && ` · quedó debiendo ${formatoCOP(venta.debeDespues)} en total`}
                  </span>
                </li>
              )}
            </ul>
            {venta.cliente && venta.fiado === 0 && (
              <p className={comun.tenue}>A nombre de {venta.cliente.nombre}.</p>
            )}
          </section>
        </div>

        <aside className={estilos.lateral}>
          <ComprobanteVenta venta={venta} />
        </aside>
      </div>

      {anulando && (
        <ModalMotivo
          titulo={`Anular la venta N.º ${venta.numero}`}
          descripcion={
            <>
              El stock de {venta.renglones.length === 1 ? 'su repuesto' : `sus ${venta.renglones.length} repuestos`} vuelve
              al inventario y la venta queda anulada con tu motivo; su número no se usa de nuevo.{' '}
              {venta.fiado > 0 ? (
                <>
                  A {venta.cliente?.nombre} se le quitan los <strong>{formatoCOP(venta.fiado)}</strong> fiados de lo que
                  debe; por eso no sale plata del cajón.
                  {venta.total - venta.fiado > 0 && (
                    <> Lo que pagó (<strong>{formatoCOP(venta.total - venta.fiado)}</strong>) se le devuelve y cuenta en
                      el turno abierto hoy.</>
                  )}
                </>
              ) : (
                <>
                  Al cliente se le devuelven <strong>{formatoCOP(venta.total)}</strong>
                  {' '}({textoDelPago(venta.pagos).toLowerCase()}), que cuentan en el turno abierto hoy.
                </>
              )}
              {' '}Si había que cobrarla distinto, después se vende de nuevo.
            </>
          }
          placeholder="Ej.: el cliente se arrepintió, o se cobró el repuesto equivocado"
          textoConfirmar="Anular venta"
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
