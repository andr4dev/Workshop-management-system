import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import AvisoCarga from '../componentes/AvisoCarga'
import Boton from '../componentes/Boton'
import ModalCliente from '../componentes/clientes/ModalCliente'
import { carteraApi } from '../api/cliente'
import { formatoCOP } from '../utils/formato'
import { identificacion } from '../utils/clientes'
import {
  desdeCuandoEnPalabras, esDeudaVieja, fechaCorta, MODOS_FECHA, resumenDeLaCartera, textoPendientes, VISTAS,
} from '../utils/cartera'
import { htmlDeLaCartera } from '../utils/carteraImpresa'
import { imprimirHtml } from '../utils/imprimir'
import { tiendaApi } from '../api/cliente'
import { ATAJOS, hoyEnColombia, periodoDelAtajo } from '../utils/periodo'
import comun from './Listado.module.css'
import estilos from './Cartera.module.css'

/**
 * La Cartera (spec 0008, H3 y H5), como la del car‑wash: arriba cuántos deben y cuánto hay por cobrar; una tarjeta por
 * cliente con lo que debe, cuántas ventas tiene pendientes y desde cuándo. Se abre para ver su ficha.
 *
 *   - *Deben*: los que deben hoy, del que más al que menos.
 *   - *Historial completo*: todos los que alguna vez tuvieron fiado, también los que están al día.
 *
 * La ven los dos roles: no trae costos. Lo que se busca (nombre, cédula o celular) queda en la dirección.
 */
export default function Cartera() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const vista = params.get('vista') === 'HISTORIAL' ? 'HISTORIAL' : 'DEBEN'
  const buscado = params.get('q') ?? ''
  const modoFecha = params.get('modoFecha') === 'ABONO' ? 'ABONO' : 'VENTA'
  const atajo = params.get('cuando') ?? ''
  const hoyDelFiltro = hoyEnColombia()
  const periodo = atajo ? periodoDelAtajo(atajo, hoyDelFiltro) : null
  const [texto, setTexto] = useState(buscado)
  const [intento, setIntento] = useState(0)
  const [creando, setCreando] = useState(false)
  const clave = JSON.stringify({ vista, buscado, modoFecha, atajo, intento })
  const [lista, setLista] = useState({ clave: null, datos: null, error: null })
  const hoy = hoyEnColombia()

  // Lo escrito se busca tras una pausa, y se guarda en la dirección: al volver de una ficha sigue ahí.
  useEffect(() => {
    if (texto.trim() === buscado) return
    const pausa = setTimeout(() => {
      setParams((p) => {
        const nuevos = new URLSearchParams(p)
        if (texto.trim()) nuevos.set('q', texto.trim())
        else nuevos.delete('q')
        return nuevos
      }, { replace: true })
    }, 300)
    return () => clearTimeout(pausa)
  }, [texto, buscado, setParams])

  useEffect(() => {
    let vigente = true
    carteraApi.lista({ vista, q: buscado, modoFecha, desde: periodo?.desde ?? null, hasta: periodo?.hasta ?? null })
      .then((datos) => { if (vigente) setLista({ clave, datos, error: null }) })
      .catch((error) => { if (vigente) setLista((l) => ({ clave, datos: l.datos, error })) })
    return () => { vigente = false }
  }, [clave, vista, buscado, modoFecha, periodo?.desde, periodo?.hasta])

  const cargando = lista.clave !== clave
  const datos = lista.datos

  function cambiarVista(valor) {
    cambiarParametro('vista', valor === 'DEBEN' ? null : valor)
  }

  function cambiarParametro(nombre, valor) {
    setParams((p) => {
      const nuevos = new URLSearchParams(p)
      if (valor) nuevos.set(nombre, valor)
      else nuevos.delete(nombre)
      return nuevos
    })
  }

  /** La lista que se está viendo, en hoja carta: el diálogo de impresión deja guardarla como PDF (H12). */
  async function exportar() {
    if (!datos) return
    const tienda = await tiendaApi.obtener().catch(() => ({ nombreComercial: 'RD MOTORS' }))
    imprimirHtml(htmlDeLaCartera(datos, { vista, modoFecha, desde: periodo?.desde, hasta: periodo?.hasta }, tienda, hoy))
  }

  return (
    <div className={comun.pagina}>
      <header className={comun.encabezado}>
        <div>
          <h1 className={comun.titulo}>Cartera</h1>
          <p className={comun.subtitulo}>
            {datos ? resumenDeLaCartera(datos, vista) : 'Quién debe, cuánto y desde cuándo'}
          </p>
        </div>
        <span className={estilos.accionesEncabezado}>
          <Boton variante="secundario" onClick={() => setCreando(true)}>Cliente nuevo</Boton>
          <Boton variante="fantasma" onClick={exportar} disabled={!datos || datos.clientes.length === 0}>
            Imprimir o PDF
          </Boton>
        </span>
      </header>

      <div className={estilos.filtros}>
        <div className={estilos.segmento} role="group" aria-label="Qué clientes ver">
          {VISTAS.map((v) => (
            <button key={v.valor} type="button" aria-pressed={vista === v.valor}
              className={vista === v.valor ? estilos.segmentoActivo : estilos.segmentoOpcion}
              onClick={() => cambiarVista(v.valor)}>
              {v.texto}
            </button>
          ))}
        </div>
        <input className={estilos.buscar} value={texto} onChange={(e) => setTexto(e.target.value)}
          placeholder="Buscar por nombre, cédula o celular" aria-label="Buscar cliente" autoComplete="off" />
      </div>

      {/* Filtrar por cuándo se fió, o por cuándo abonó (RF-022). Sin período, se ve todo. */}
      <div className={estilos.filtros}>
        <div className={estilos.segmento} role="group" aria-label="Por qué fecha filtrar">
          {MODOS_FECHA.map((m) => (
            <button key={m.valor} type="button" aria-pressed={modoFecha === m.valor}
              className={modoFecha === m.valor ? estilos.segmentoActivo : estilos.segmentoOpcion}
              onClick={() => cambiarParametro('modoFecha', m.valor === 'VENTA' ? null : m.valor)}>
              {m.texto}
            </button>
          ))}
        </div>
        <div className={estilos.atajos}>
          <button type="button" aria-pressed={!atajo}
            className={!atajo ? estilos.atajoActivo : estilos.atajo}
            onClick={() => cambiarParametro('cuando', null)}>Todo</button>
          {ATAJOS.map(([valor, texto]) => (
            <button key={valor} type="button" aria-pressed={atajo === valor}
              className={atajo === valor ? estilos.atajoActivo : estilos.atajo}
              onClick={() => cambiarParametro('cuando', valor)}>{texto}</button>
          ))}
        </div>
      </div>

      <AvisoCarga error={cargando ? null : lista.error} onReintentar={() => setIntento((n) => n + 1)}
        desactualizado={datos != null} />
      {!datos && cargando && <p className={comun.vacio}>Cargando la cartera…</p>}
      {datos && datos.clientes.length === 0 && (
        <div className={comun.vacio}>
          <p>
            {buscado ? `Nadie coincide con «${buscado}».`
              : vista === 'DEBEN' ? 'Nadie debe: todo al día.' : 'Todavía no se le ha fiado a nadie.'}
          </p>
        </div>
      )}

      {datos && datos.clientes.length > 0 && (
        <ul className={`${estilos.tarjetas} ${cargando ? comun.atenuado : ''}`} aria-busy={cargando}>
          {datos.clientes.map((c) => {
            const desde = desdeCuandoEnPalabras(c.desde, hoy)
            return (
              <li key={c.id}>
                <Link to={`/cartera/${c.id}`} className={estilos.tarjeta}>
                  <span className={estilos.tarjetaDatos}>
                    <span className={estilos.nombre}>{c.nombre}</span>
                    {identificacion(c) && <span className={estilos.tenue}>{identificacion(c)}</span>}
                    <span className={estilos.meta}>
                      {vista === 'HISTORIAL'
                        ? `Fiado ${formatoCOP(c.fiadoTotal)} · Pagado ${formatoCOP(c.pagadoTotal)}`
                          + (c.ultimoMovimiento ? ` · Último movimiento ${fechaCorta(hoyEnColombia(new Date(c.ultimoMovimiento)))}` : '')
                        : [textoPendientes(c.pendientes), desde].filter(Boolean).join(' · ')}
                    </span>
                    <span className={estilos.marcas}>
                      {c.fiadoCerrado && <span className={estilos.marcaCerrado}>Fiado cerrado</span>}
                      {esDeudaVieja(c.desde, hoy) && <span className={estilos.marcaVieja}>Más de 30 días</span>}
                      {c.aFavor > 0 && <span className={estilos.marcaFavor}>A favor {formatoCOP(c.aFavor)}</span>}
                    </span>
                  </span>
                  <span className={c.debe > 0 ? estilos.debe : estilos.alDia}>
                    {c.debe > 0 ? formatoCOP(c.debe) : 'Al día'}
                  </span>
                </Link>
              </li>
            )
          })}
        </ul>
      )}

      {creando && (
        <ModalCliente
          onCerrar={() => setCreando(false)}
          onGuardado={(nuevo) => navigate(`/cartera/${nuevo.id}`)}
          onRepetido={(existente) => navigate(`/cartera/${existente.id}`)}
        />
      )}
    </div>
  )
}
