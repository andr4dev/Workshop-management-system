import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import AvisoCarga from '../componentes/AvisoCarga'
import Boton from '../componentes/Boton'
import Modal from '../componentes/Modal'
import ModalMotivo from '../componentes/ModalMotivo'
import Ticket from '../componentes/venta/Ticket'
import ModalCliente from '../componentes/clientes/ModalCliente'
import ModalAbono from '../componentes/clientes/ModalAbono'
import ModalSaldoCuaderno from '../componentes/clientes/ModalSaldoCuaderno'
import { useSesion } from '../componentes/sesion/contexto'
import { abonosApi, clientesApi, tiendaApi } from '../api/cliente'
import { fechaHora, formatoCOP } from '../utils/formato'
import { esteEquipoEsElMostrador, imprimirHtml } from '../utils/imprimir'
import { armarRecibo, htmlDelRecibo, problemasDelRecibo } from '../utils/reciboAbono'
import { esAdministrador } from '../utils/permisos'
import { hoyEnColombia } from '../utils/periodo'
import {
  desdeCuandoEnPalabras, ESTADOS, fechaCorta, FORMAS, nombreDeLaDeuda, problemasDeLaFicha,
} from '../utils/cartera'
import comun from './Listado.module.css'
import estilos from './Cartera.module.css'

const CLASE_ESTADO = {
  PENDIENTE: 'estadoPendiente', ABONADA: 'estadoAbonada', PAGADA: 'estadoPagada', ANULADA: 'estadoAnulada',
}

/**
 * La ficha de un cliente en la Cartera (spec 0008, RF-019), como el detalle del car‑wash: sus datos, lo que debe y desde
 * cuándo, **cada venta fiada con su estado** y los abonos que le aplicaron debajo, y la línea de tiempo de abonos con
 * quién los recibió.
 *
 * Las cifras son las del servidor; si las partes no cuadran se dice en rojo. El cajero completa los datos que faltan;
 * corregirlos y cerrar el fiado es del administrador.
 */
export default function FichaCliente() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { usuario } = useSesion()
  const admin = esAdministrador(usuario)
  const [intento, setIntento] = useState(0)
  const clave = `${id}:${intento}`
  const [estado, setEstado] = useState({ clave: null, ficha: null, error: null })
  const [editando, setEditando] = useState(false)
  const [abonando, setAbonando] = useState(false)
  const [recibido, setRecibido] = useState(null)
  const [viendoRecibo, setViendoRecibo] = useState(false)
  const [impresion, setImpresion] = useState(null)
  const [anulandoAbono, setAnulandoAbono] = useState(null)
  const [cerrando, setCerrando] = useState(false)
  const [cargandoSaldo, setCargandoSaldo] = useState(false)
  const [compras, setCompras] = useState(null)
  const [enviando, setEnviando] = useState(false)
  const [errorAccion, setErrorAccion] = useState(null)
  const hoy = hoyEnColombia()

  useEffect(() => {
    let vigente = true
    clientesApi.ficha(id)
      .then((ficha) => { if (vigente) setEstado({ clave, ficha, error: null }) })
      .catch((error) => { if (vigente) setEstado((e) => ({ clave, ficha: e.ficha, error })) })
    return () => { vigente = false }
  }, [clave, id])

  const recargar = () => setIntento((n) => n + 1)

  // Las compras del cliente (RF-023): fiadas o de contado. Se piden con la ficha; si fallan, la ficha se ve igual.
  useEffect(() => {
    let vigente = true
    clientesApi.ventas(id).then((p) => { if (vigente) setCompras(p) }).catch(() => { if (vigente) setCompras(null) })
    return () => { vigente = false }
  }, [clave, id])

  /**
   * El recibo del abono. Como el comprobante de la venta: en el computador del mostrador sale solo; en un celular se
   * reimprime desde ahí. Imprimir nunca deshace el abono: si falla, se avisa y se reimprime.
   */
  async function imprimirRecibo(abono, { siempre = false } = {}) {
    if (!siempre && !esteEquipoEsElMostrador()) {
      setImpresion('OTRO_EQUIPO')
      return
    }
    setImpresion('IMPRIMIENDO')
    try {
      const recibo = armarRecibo(abono, await tiendaApi.obtener())
      if (problemasDelRecibo(recibo).length > 0) setImpresion('NO_CUADRA')
      else setImpresion(await imprimirHtml(htmlDelRecibo(recibo)) ? 'ENVIADO' : 'FALLO')
    } catch {
      setImpresion('FALLO')
    }
  }

  function abonado(abono) {
    setAbonando(false)
    setRecibido(abono)
    setImpresion(null)
    recargar()
    imprimirRecibo(abono)
  }

  async function anularAbono(motivo) {
    setEnviando(true)
    setErrorAccion(null)
    try {
      await abonosApi.anular(anulandoAbono.id, motivo)
      setAnulandoAbono(null)
      setRecibido(null)
      recargar()
    } catch (e) {
      setErrorAccion(e.estado === 0 ? 'No hay conexión con el servidor' : e.message)
    } finally {
      setEnviando(false)
    }
  }
  const { ficha } = estado
  const cargando = estado.clave !== clave

  async function cerrarFiado(motivo) {
    setEnviando(true)
    setErrorAccion(null)
    try {
      setEstado({ clave, ficha: await clientesApi.cerrarFiado(id, motivo), error: null })
      setCerrando(false)
    } catch (e) {
      setErrorAccion(e.estado === 0 ? 'No hay conexión con el servidor' : e.message)
    } finally {
      setEnviando(false)
    }
  }

  async function abrirFiado() {
    setEnviando(true)
    setErrorAccion(null)
    try {
      setEstado({ clave, ficha: await clientesApi.abrirFiado(id), error: null })
    } catch (e) {
      setErrorAccion(e.estado === 0 ? 'No hay conexión con el servidor' : e.message)
    } finally {
      setEnviando(false)
    }
  }

  if (!ficha) {
    return (
      <div className={comun.pagina}>
        <Link to="/cartera" className={comun.volver}>← Cartera</Link>
        <AvisoCarga error={cargando ? null : estado.error} onReintentar={recargar} />
        {cargando && <p className={comun.vacio}>Cargando el cliente…</p>}
      </div>
    )
  }

  const { cliente } = ficha
  const problemas = problemasDeLaFicha(ficha)
  const desde = desdeCuandoEnPalabras(ficha.desde, hoy)

  return (
    <div className={comun.pagina}>
      <Link to="/cartera" className={comun.volver}>← Cartera</Link>

      <header className={comun.encabezado}>
        <div>
          <h1 className={comun.titulo}>{cliente.nombre}</h1>
          <p className={estilos.datos}>
            <span>{cliente.documento ? `Cédula o NIT ${cliente.documento}` : 'Sin cédula'}</span>
            <span>{cliente.celular ? `Celular ${cliente.celular}` : 'Sin celular'}</span>
            {cliente.direccion && <span>{cliente.direccion}</span>}
            {cliente.nota && <span>{cliente.nota}</span>}
          </p>
        </div>
        <div className={estilos.acciones}>
          {ficha.debe > 0 && (
            <Boton variante="primario" onClick={() => setAbonando(true)}>Abonar</Boton>
          )}
          <Boton variante="secundario" onClick={() => setEditando(true)}>
            {admin ? 'Editar datos' : 'Completar datos'}
          </Boton>
          {admin && !cliente.fiadoCerrado && (
            <Boton variante="peligro" onClick={() => { setErrorAccion(null); setCerrando(true) }}>Cerrar el fiado</Boton>
          )}
          {admin && cliente.fiadoCerrado && (
            <Boton variante="secundario" onClick={abrirFiado} disabled={enviando}>Abrir el fiado</Boton>
          )}
          {admin && !ficha.deudas.some((d) => d.origen === 'CUADERNO') && (
            <Boton variante="secundario" onClick={() => setCargandoSaldo(true)}>Saldo del cuaderno</Boton>
          )}
        </div>
      </header>

      <AvisoCarga error={cargando ? null : estado.error} onReintentar={recargar} desactualizado />
      {errorAccion && !cerrando && <p className={estilos.problemas} role="alert"><span aria-hidden>⚠</span> {errorAccion}</p>}

      {recibido && (
        <div className={estilos.recibido} role="status">
          <span>
            <strong>Abono N.º {recibido.numero} recibido</strong> · {formatoCOP(recibido.monto)} ·{' '}
            {cliente.nombre} debe {formatoCOP(recibido.debeAhora)}
          </span>
          <span className={estilos.acciones}>
            <Boton variante="secundario" tamano="chico" onClick={() => setViendoRecibo(true)}>Ver recibo</Boton>
            <Boton variante="fantasma" tamano="chico" disabled={impresion === 'IMPRIMIENDO'}
              onClick={() => imprimirRecibo(recibido, { siempre: true })}>
              {impresion === 'OTRO_EQUIPO' ? 'Imprimir' : 'Reimprimir'}
            </Boton>
            <button type="button" className={estilos.cerrarAviso} onClick={() => setRecibido(null)}
              aria-label="Cerrar aviso">×</button>
          </span>
          <AvisoImpresion estado={impresion} />
        </div>
      )}

      {cliente.fiadoCerrado && (
        <p className={estilos.cerrado} role="note">
          <span aria-hidden>⚠</span> A {cliente.nombre} no se le fía: {cliente.motivoFiadoCerrado}. Puede comprar de
          contado y abonar lo que debe.
        </p>
      )}
      {problemas.length > 0 && (
        <div className={estilos.problemas} role="alert">
          <span aria-hidden>⚠</span> Estas cifras no cuadran:
          <ul>{problemas.map((p) => <li key={p}>{p}</li>)}</ul>
        </div>
      )}

      <section className={`${estilos.cifras} ${cargando ? comun.atenuado : ''}`} aria-label="Lo que debe">
        <div className={estilos.cifra}>
          <span className={estilos.cifraEtiqueta}>Debe</span>
          <span className={`${estilos.cifraValor} ${ficha.debe > 0 ? estilos.cifraDebe : ''}`}>
            {ficha.debe > 0 ? formatoCOP(ficha.debe) : 'Al día'}
          </span>
          {desde && <span className={estilos.cifraNota}>{desde}</span>}
        </div>
        {ficha.aFavor > 0 && (
          <div className={estilos.cifra}>
            <span className={estilos.cifraEtiqueta}>A favor</span>
            <span className={estilos.cifraValor}>{formatoCOP(ficha.aFavor)}</span>
            <span className={estilos.cifraNota}>Se descuenta del próximo fiado</span>
          </div>
        )}
        <div className={estilos.cifra}>
          <span className={estilos.cifraEtiqueta}>Fiado en total</span>
          <span className={estilos.cifraValor}>{formatoCOP(ficha.fiadoTotal)}</span>
          <span className={estilos.cifraNota}>Sin lo anulado</span>
        </div>
        <div className={estilos.cifra}>
          <span className={estilos.cifraEtiqueta}>Pagado en total</span>
          <span className={estilos.cifraValor}>{formatoCOP(ficha.pagadoTotal)}</span>
          <span className={estilos.cifraNota}>En abonos</span>
        </div>
      </section>

      <h2 className={estilos.seccion}>Ventas fiadas</h2>
      {ficha.deudas.length === 0 ? (
        <p className={comun.tenue}>Todavía no se le ha fiado nada.</p>
      ) : (
        <ul className={estilos.deudas}>
          {ficha.deudas.map((d) => (
            <li key={d.id} className={`${estilos.deuda} ${d.estado === 'ANULADA' ? estilos.deudaAnulada : ''}`}>
              <div className={estilos.deudaCabecera}>
                <span className={estilos.deudaNombre}>
                  {d.ventaId ? <Link to={`/vender/ventas/${d.ventaId}`} className={comun.enlace}>{nombreDeLaDeuda(d)}</Link>
                    : nombreDeLaDeuda(d)}
                </span>
                <span className={comun.tenue}>{fechaCorta(d.fecha)}</span>
                <span className={`${estilos.estado} ${estilos[CLASE_ESTADO[d.estado]]}`}>{ESTADOS[d.estado]}</span>
                <span className={`${estilos.deudaMonto} ${d.estado === 'ANULADA' ? comun.tachado : ''}`}>
                  {formatoCOP(d.monto)}
                </span>
              </div>
              {d.estado !== 'ANULADA' && (
                <p className={estilos.deudaPartes}>
                  Abonado {formatoCOP(d.abonado)} · Pendiente <strong>{formatoCOP(d.pendiente)}</strong>
                  {d.motivo && ` · ${d.motivo}`}
                </p>
              )}
              {d.estado === 'ANULADA' && <p className={estilos.deudaPartes}>Se anuló la venta: ya no se debe.</p>}
              {d.abonos.length > 0 && (
                <ul className={estilos.abonosDeLaDeuda}>
                  {d.abonos.map((p, i) => (
                    <li key={`${p.abonoId}-${i}`} className={p.vigente ? undefined : estilos.movido}>
                      <span>Abono N.º {p.numero} · {fechaHora(p.recibidoEn)} · {FORMAS[p.forma]}</span>
                      <strong>{formatoCOP(p.monto)}</strong>
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ul>
      )}

      <h2 className={estilos.seccion}>Compras</h2>
      {!compras || compras.elementos.length === 0 ? (
        <p className={comun.tenue}>
          {compras ? 'Todavía no le hemos vendido nada a nombre suyo.' : 'Cargando sus compras…'}
        </p>
      ) : (
        <div className={`scroll-x ${comun.marco}`} style={{ marginBottom: 'var(--esp-5)' }}>
          <table className={comun.tabla}>
            <thead>
              <tr>
                <th className="cifra">N.º</th>
                <th>Cuándo</th>
                <th className="cifra">Total</th>
                <th className="cifra">Fiado</th>
              </tr>
            </thead>
            <tbody>
              {compras.elementos.map((v) => (
                <tr key={v.id}>
                  <td className="cifra">
                    <Link to={`/vender/ventas/${v.id}`} className={comun.enlace}>{v.numero}</Link>
                    {v.estado === 'ANULADA' && <span className={comun.anulada}>Anulada</span>}
                  </td>
                  <td>{fechaHora(v.cobradaEn)}</td>
                  <td className={`cifra ${v.estado === 'ANULADA' ? comun.tachado : ''}`}>{formatoCOP(v.total)}</td>
                  <td className="cifra">{v.fiado > 0 ? formatoCOP(v.fiado) : ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <h2 className={estilos.seccion}>Abonos</h2>
      {ficha.abonos.length === 0 ? (
        <p className={comun.tenue}>Todavía no ha abonado.</p>
      ) : (
        <ol className={estilos.linea}>
          {ficha.abonos.map((a) => (
            <li key={a.id} className={`${estilos.abono} ${a.anuladoEn ? estilos.abonoAnulado : ''}`}>
              <span>
                <strong>Abono N.º {a.numero}</strong> · {fechaHora(a.recibidoEn)} · {FORMAS[a.forma]}
                {a.recibidoPor && <span className={comun.tenue}> · lo recibió {a.recibidoPor.nombre}</span>}
                {admin && !a.anuladoEn && (
                  <button type="button" className={estilos.anular}
                    onClick={() => { setErrorAccion(null); setAnulandoAbono(a) }}>Anular</button>
                )}
              </span>
              <span className={estilos.abonoMonto}>{formatoCOP(a.monto)}</span>
              <span className={estilos.abonoDetalle}>
                {a.anuladoEn
                  ? `Anulado el ${fechaHora(a.anuladoEn)}${a.anuladoPor ? ` por ${a.anuladoPor.nombre}` : ''}: ${a.motivoAnulacion}`
                  : a.aplicaciones.filter((p) => p.vigente).map((p) => `${formatoCOP(p.monto)} a ${p.deuda}`).join(' · ')}
                {!a.anuladoEn && a.sinAplicar > 0 && ` · ${formatoCOP(a.sinAplicar)} a favor`}
                {a.referencia && ` · Ref. ${a.referencia}`}
                {a.nota && ` · ${a.nota}`}
              </span>
            </li>
          ))}
        </ol>
      )}

      {editando && (
        <ModalCliente
          cliente={cliente}
          soloCompletar={!admin}
          onCerrar={() => setEditando(false)}
          onGuardado={() => { setEditando(false); recargar() }}
          onRepetido={(existente) => navigate(`/cartera/${existente.id}`)}
        />
      )}

      {abonando && (
        <ModalAbono ficha={ficha} onCerrar={() => setAbonando(false)} onAbonado={abonado} />
      )}

      {viendoRecibo && recibido && (
        <Modal abierto onCerrar={() => setViendoRecibo(false)} titulo={`Recibo de abono N.º ${recibido.numero}`}
          ancho={440}>
          <ReciboEnPantalla abono={recibido} />
        </Modal>
      )}

      {cargandoSaldo && (
        <ModalSaldoCuaderno cliente={cliente} onCerrar={() => setCargandoSaldo(false)}
          onCargado={() => { setCargandoSaldo(false); recargar() }} />
      )}

      {anulandoAbono && (
        <ModalMotivo
          titulo={`Anular el abono N.º ${anulandoAbono.numero}`}
          descripcion={<>Los <strong>{formatoCOP(anulandoAbono.monto)}</strong> vuelven a deberse
            {anulandoAbono.forma === 'EFECTIVO' && ' y salen del cajón de su turno'}. Queda tachado, con tu motivo, en
            el registro de auditoría.</>}
          placeholder="Ej.: se registró dos veces"
          textoConfirmar="Anular abono"
          peligro
          enviando={enviando}
          error={errorAccion}
          onConfirmar={anularAbono}
          onCerrar={() => setAnulandoAbono(null)}
        />
      )}

      {cerrando && (
        <ModalMotivo
          titulo={`Cerrarle el fiado a ${cliente.nombre}`}
          descripcion={<>No se le fía más: puede seguir comprando de contado y abonar lo que debe
            {ficha.debe > 0 ? <> (<strong>{formatoCOP(ficha.debe)}</strong>)</> : ''}. Se puede volver a abrir.</>}
          placeholder="Ej.: no paga desde julio"
          textoConfirmar="Cerrar el fiado"
          peligro
          enviando={enviando}
          error={errorAccion}
          onConfirmar={cerrarFiado}
          onCerrar={() => setCerrando(false)}
        />
      )}
    </div>
  )
}

/** El recibo del abono en pantalla: el MISMO documento que sale por la ticketera. */
function ReciboEnPantalla({ abono }) {
  const [tienda, setTienda] = useState(null)

  useEffect(() => {
    let vigente = true
    tiendaApi.obtener().then((datos) => { if (vigente) setTienda(datos) }).catch(() => {})
    return () => { vigente = false }
  }, [])

  if (!tienda) return <p>Cargando el recibo…</p>
  const recibo = armarRecibo(abono, tienda)
  const problemas = problemasDelRecibo(recibo)
  return (
    <>
      {problemas.length > 0 && (
        <p className={estilos.problemas} role="alert"><span aria-hidden>⚠</span> {problemas[0]}</p>
      )}
      <Ticket html={htmlDelRecibo(recibo)} titulo={`Recibo de abono N.º ${recibo.numero}`} />
    </>
  )
}

/** Qué pasó con el recibo del abono recién recibido. Nada mientras sale bien. */
function AvisoImpresion({ estado }) {
  const avisos = {
    IMPRIMIENDO: 'Imprimiendo el recibo…',
    ENVIADO: 'Recibo enviado a la impresora.',
    OTRO_EQUIPO: 'El recibo se imprime desde el computador del mostrador.',
    FALLO: '⚠ No se pudo imprimir el recibo. El abono quedó registrado: usa Reimprimir.',
    NO_CUADRA: '⚠ El recibo no cuadra y no se imprimió. Revísalo en Ver recibo.',
  }
  if (!avisos[estado]) return null
  return <p className={estilos.impresion} role={estado === 'FALLO' || estado === 'NO_CUADRA' ? 'alert' : undefined}>{avisos[estado]}</p>
}
