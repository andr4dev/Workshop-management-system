import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import AvisoCarga from '../componentes/AvisoCarga'
import Boton from '../componentes/Boton'
import PestanasVenta from '../componentes/venta/PestanasVenta'
import { turnosApi, ventasApi } from '../api/cliente'
import { fechaHora, formatoCOP } from '../utils/formato'
import { numeroDeVenta, resumenDelTurno, textoDelPago, textoDeRepuestos } from '../utils/ventasDelTurno'
import comun from './Listado.module.css'
import estilos from './Ventas.module.css'

const HORA = new Intl.DateTimeFormat('es-CO', { hour: 'numeric', minute: '2-digit', timeZone: 'America/Bogota' })
const unaOVarias = (n) => `${n} ${n === 1 ? 'venta' : 'ventas'}`

/**
 * Las ventas del turno abierto (spec 0003, H7, H10 y RF-024): de la más reciente a la más antigua, con
 * cuánto entró en efectivo y cuánto por transferencia. Y buscar cualquier venta por su número, de este
 * turno o de otro, para reimprimirla cuando el cliente vuelve con su ticket.
 */
export default function Ventas() {
  const navigate = useNavigate()
  const [intento, setIntento] = useState(0)
  const [carga, setCarga] = useState({ intento: null, turno: null, ventas: null, error: null })

  const [numero, setNumero] = useState('')
  const [buscando, setBuscando] = useState(false)
  const [errorBusqueda, setErrorBusqueda] = useState(null)

  useEffect(() => {
    let vigente = true
    Promise.all([turnosApi.abierto(), ventasApi.delTurnoAbierto()])
      .then(([turno, ventas]) => { if (vigente) setCarga({ intento, turno, ventas, error: null }) })
      // Si falla, se conserva lo anterior: mejor verlo con el aviso que una pantalla vacía.
      .catch((error) => { if (vigente) setCarga((c) => ({ ...c, intento, error })) })
    return () => { vigente = false }
  }, [intento])

  const cargando = carga.intento !== intento
  const { turno, ventas } = carga

  async function buscar(e) {
    e.preventDefault()
    const buscado = numeroDeVenta(numero)
    if (buscado == null) {
      setErrorBusqueda('Escribe el número de la venta, por ejemplo 12')
      return
    }
    setBuscando(true)
    setErrorBusqueda(null)
    try {
      const venta = await ventasApi.porNumero(buscado)
      navigate(`/vender/ventas/${venta.id}`)
    } catch (error) {
      setErrorBusqueda(error.estado === 404 ? `No hay una venta con el número ${buscado}`
        : error.estado === 0 ? 'No hay conexión con el servidor' : error.message)
      setBuscando(false)
    }
  }

  return (
    <div className={comun.pagina}>
      <PestanasVenta />

      <header className={comun.encabezado}>
        <div>
          <h1 className={comun.titulo}>Ventas del turno</h1>
          <p className={comun.subtitulo}>
            {turno ? `Turno abierto desde ${fechaHora(turno.abiertoEn)} · ` : ''}De la más reciente a la más antigua.
          </p>
        </div>

        {/* Buscar va arriba y siempre: el cliente que vuelve con su ticket puede traer una venta de ayer. */}
        <form className={estilos.buscar} onSubmit={buscar} role="search" aria-label="Buscar una venta por número">
          <label className={estilos.buscarEtiqueta} htmlFor="buscar-numero">Buscar venta N.º</label>
          <input id="buscar-numero" className={`${comun.control} ${estilos.buscarCampo}`} value={numero}
            onChange={(e) => { setNumero(e.target.value); setErrorBusqueda(null) }}
            inputMode="numeric" autoComplete="off" placeholder="12" aria-invalid={errorBusqueda ? 'true' : undefined} />
          <Boton type="submit" variante="secundario" disabled={buscando}>{buscando ? 'Buscando…' : 'Buscar'}</Boton>
          {errorBusqueda && <p className={estilos.buscarError} role="alert"><span aria-hidden>⚠</span> {errorBusqueda}</p>}
        </form>
      </header>

      <AvisoCarga error={cargando ? null : carga.error} onReintentar={() => setIntento((n) => n + 1)}
        desactualizado={ventas != null} />

      {!ventas && cargando && <p className={comun.vacio}>Cargando ventas…</p>}

      {ventas && !turno && (
        <div className={comun.vacio}>
          <p>No hay un turno abierto. Las ventas de turnos anteriores se encuentran por su número.</p>
          <Link to="/vender" className={comun.accion}>Ir a vender</Link>
        </div>
      )}

      {ventas && turno && (
        <>
          <Totales ventas={ventas} atenuado={cargando} />
          <Lista ventas={ventas} atenuado={cargando} onAbrir={(id) => navigate(`/vender/ventas/${id}`)} />
        </>
      )}
    </div>
  )
}

function Totales({ ventas, atenuado }) {
  const r = resumenDelTurno(ventas)
  return (
    <section className={`${estilos.totales} ${atenuado ? comun.atenuado : ''}`} aria-label="Totales del turno">
      <div className={estilos.tarjeta}>
        <span className={estilos.etiqueta}>Vendido</span>
        <span className={estilos.valor}>{formatoCOP(r.total)}</span>
        <span className={estilos.nota}>
          {unaOVarias(r.ventas)}
          {r.conDescuento > 0 && ` · ${r.conDescuento} con descuento`}
          {r.anuladas > 0 && ` · ${r.anuladas} ${r.anuladas === 1 ? 'anulada no suma' : 'anuladas no suman'}`}
        </span>
      </div>
      <div className={estilos.tarjeta}>
        <span className={estilos.etiqueta}>Efectivo</span>
        <span className={estilos.valor}>{formatoCOP(r.efectivo)}</span>
        {/* Decía "lo que entró al cajón", y desde el fiado (spec 0008) eso no es cierto: al cajón también entran
            los abonos de los clientes, que no son ventas y viven en Caja. */}
        <span className={estilos.nota}>De las ventas, sin el cambio · el cajón completo va en Caja</span>
      </div>
      <div className={estilos.tarjeta}>
        <span className={estilos.etiqueta}>Transferencia</span>
        <span className={estilos.valor}>{formatoCOP(r.transferencia)}</span>
        <span className={estilos.nota}>Incluye QR</span>
      </div>
      {r.fiado > 0 && (
        <div className={estilos.tarjeta}>
          <span className={estilos.etiqueta}>Fiado</span>
          <span className={estilos.valor}>{formatoCOP(r.fiado)}</span>
          <span className={estilos.nota}>No entró al cajón: se fue a la cartera</span>
        </div>
      )}
      {r.descuadre !== 0 && (
        <p className={estilos.descuadre} role="alert">
          <span aria-hidden>⚠</span> Los totales no cuadran: efectivo, transferencia y fiado suman{' '}
          {formatoCOP(r.efectivo + r.transferencia + r.fiado)} y lo vendido es {formatoCOP(r.total)}.
        </p>
      )}
    </section>
  )
}

function Lista({ ventas, atenuado, onAbrir }) {
  if (ventas.length === 0) {
    return (
      <div className={comun.vacio}>
        <p>Todavía no hay ventas en este turno.</p>
        <Link to="/vender" className={comun.accion}>Ir a vender</Link>
      </div>
    )
  }

  return (
    <div className={`scroll-x ${comun.marco} ${atenuado ? comun.atenuado : ''}`} aria-busy={atenuado}>
      <table className={comun.tabla}>
        <thead>
          <tr>
            <th className="cifra">N.º</th>
            <th>Hora</th>
            <th>Repuestos</th>
            <th>Pago</th>
            <th className="cifra">Total</th>
          </tr>
        </thead>
        <tbody>
          {ventas.map((v) => {
            const anulada = v.estado === 'ANULADA'
            return (
              <tr key={v.id} className={comun.fila} onClick={(e) => { if (!e.target.closest('a')) onAbrir(v.id) }}>
                <td className="cifra">
                  {/* El número es el enlace: con teclado, Tab llega a cada venta. */}
                  <Link to={`/vender/ventas/${v.id}`} className={comun.enlace}>{v.numero}</Link>
                </td>
                <td>{HORA.format(new Date(v.cobradaEn))}</td>
                <td className={estilos.repuestos}>{textoDeRepuestos(v.renglones)}</td>
                <td>
                  {textoDelPago(v.pagos, v.fiado)}
                  {v.cliente && <span className={estilos.cliente}>{v.cliente.nombre}</span>}
                  {anulada && <span className={comun.anulada}>Anulada</span>}
                </td>
                <td className="cifra">
                  {v.descuento > 0 && (
                    <span className={estilos.conDescuento} title={`Descuento de ${formatoCOP(v.descuento)} · ${v.descuentoMotivo}`}>
                      Descuento
                    </span>
                  )}
                  <strong className={anulada ? comun.tachado : undefined}>{formatoCOP(v.total)}</strong>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
