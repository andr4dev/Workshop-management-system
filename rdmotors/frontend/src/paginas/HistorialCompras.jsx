import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import Boton from '../componentes/Boton'
import AvisoCarga from '../componentes/AvisoCarga'
import PestanasCompras from '../componentes/compra/PestanasCompras'
import { comprasApi, cuentasApi, proveedoresApi } from '../api/cliente'
import { textoDelPago } from '../utils/compra'
import { fechaDia, formatoCOP } from '../utils/formato'
import Resaltado from '../componentes/Resaltado'
import {
  consultaDeTotales, consultaDelHistorial, filtrosDesdeUrl, hayFiltros, lineaDeLaFactura, normalizarFiltros,
  problemaDeFechas, resumenDeTotales, rutaDelDetalle, TODAS,
} from '../utils/historial'
import { fechaLocal, rangoDePagina } from '../utils/inventario'
import comun from './Listado.module.css'
import estilos from './HistorialCompras.module.css'

/**
 * Las compras registradas (spec 0002, H2).
 *
 * <p>Los filtros viven en la URL, como en el inventario: al abrir una factura y volver, la lista
 * está donde se dejó, y "las compras de Jotapartes en septiembre" queda como un enlace.
 *
 * <p>Las fechas son <b>de la factura</b>: lo que se compró en el período, aunque se haya
 * registrado después.
 *
 * <p>Arriba de la lista van los totales de esos mismos filtros, de todas las páginas (H6): cuánto se
 * pagó en efectivo y cuánto se transfirió desde cada cuenta. Las anuladas no suman.
 */
export default function HistorialCompras() {
  const navigate = useNavigate()
  const location = useLocation()
  const [params, setParams] = useSearchParams()
  const filtros = normalizarFiltros(filtrosDesdeUrl(params))
  const errorFechas = problemaDeFechas(filtros.desde, filtros.hasta)

  // Los textos van aparte de la URL: la URL cambia con una pausa, no por cada letra.
  const [factura, setFactura] = useState(filtros.factura)
  const [repuesto, setRepuesto] = useState(filtros.repuesto)
  const [proveedores, setProveedores] = useState([])
  const [cuentas, setCuentas] = useState([])
  const [intento, setIntento] = useState(0)

  // La clave ES la consulta: cada respuesta recuerda para cuál llegó, y "cargando" se deriva de
  // compararlas. Con fechas al revés no hay clave y no se pide nada.
  const clave = errorFechas ? null : JSON.stringify({ consulta: consultaDelHistorial(filtros), intento })
  const [listado, setListado] = useState({ clave: null, datos: null, error: null })

  // Los totales van con su propia clave: no dependen de la página, así que pasar de página no los
  // vuelve a pedir. Sin clave cuando no hay nada que sumar (fechas al revés, solo anuladas).
  const consultaTotales = errorFechas ? null : consultaDeTotales(filtros)
  const claveTotales = consultaTotales ? JSON.stringify({ consulta: consultaTotales, intento }) : null
  const [totales, setTotales] = useState({ clave: null, datos: null, error: null })

  const cambiar = useCallback((cambios) => {
    setParams((actual) => {
      const nuevos = new URLSearchParams(actual)
      for (const [nombre, valor] of Object.entries(cambios)) {
        if (valor) nuevos.set(nombre, valor)
        else nuevos.delete(nombre)
      }
      // Otro filtro empieza en la primera página.
      if (!('p' in cambios)) nuevos.delete('p')
      return nuevos
    }, { replace: true })
  }, [setParams])

  useEffect(() => {
    proveedoresApi.activos().then(setProveedores).catch(() => setProveedores([]))
    cuentasApi.activas().then(setCuentas).catch(() => setCuentas([]))
  }, [])

  useEffect(() => {
    const limpio = factura.trim()
    if (limpio === filtros.factura) return
    const espera = setTimeout(() => cambiar({ factura: limpio }), 300)
    return () => clearTimeout(espera)
  }, [factura, filtros.factura, cambiar])

  useEffect(() => {
    const limpio = repuesto.trim()
    if (limpio === filtros.repuesto) return
    const espera = setTimeout(() => cambiar({ repuesto: limpio }), 300)
    return () => clearTimeout(espera)
  }, [repuesto, filtros.repuesto, cambiar])

  useEffect(() => {
    if (!clave) return
    let vigente = true
    comprasApi.historial(JSON.parse(clave).consulta)
      .then((datos) => { if (vigente) setListado({ clave, datos, error: null }) })
      // Si falla, se conservan los datos anteriores: mejor verlos atenuados que una pantalla vacía.
      .catch((error) => { if (vigente) setListado((l) => ({ clave, datos: l.datos, error })) })
    return () => { vigente = false }
  }, [clave])

  useEffect(() => {
    if (!claveTotales) return
    let vigente = true
    comprasApi.totales(JSON.parse(claveTotales).consulta)
      .then((datos) => { if (vigente) setTotales({ clave: claveTotales, datos, error: null }) })
      // A diferencia de la lista, un total que falló NO se deja a la vista: una cifra de plata vieja
      // junto a filtros nuevos se lee como la respuesta.
      .catch((error) => { if (vigente) setTotales({ clave: claveTotales, datos: null, error }) })
    return () => { vigente = false }
  }, [claveTotales])

  const cargando = clave != null && listado.clave !== clave
  const error = cargando ? null : listado.error
  const datos = listado.datos

  function limpiarFiltros() {
    setFactura('')
    setRepuesto('')
    setParams({}, { replace: true })
  }

  /**
   * Se lleva los filtros para que "← Historial" vuelva a esta misma lista, y la búsqueda de repuesto
   * en la dirección para que el detalle la resalte.
   */
  const rutaDe = (id) => rutaDelDetalle(id, filtros.repuesto)
  const irADetalle = (id) => navigate(rutaDe(id), { state: { desde: location.search } })

  return (
    <div className={comun.pagina}>
      <PestanasCompras />

      <header className={comun.encabezado}>
        <div>
          <h1 className={comun.titulo}>Historial de compras</h1>
          <p className={comun.subtitulo}>
            Por fecha de la factura, de la más reciente a la más antigua.
          </p>
        </div>
      </header>

      {/* ── Filtros ───────────────────────────────────────────────────────── */}
      {/* Etiquetas con htmlFor y no envolviendo: un <select> dentro de un <label> se abre y se
          cierra solo (ver Campo.jsx). */}
      <section className={comun.filtros} aria-label="Filtros del historial">
        <div className={comun.filtro}>
          <label className={comun.etiquetaFiltro} htmlFor="filtro-repuesto">Repuesto</label>
          <input id="filtro-repuesto" type="search" className={comun.control} value={repuesto}
            onChange={(e) => setRepuesto(e.target.value)} placeholder="Código, nombre, marca o moto"
            maxLength={80} autoComplete="off" spellCheck="false" />
        </div>

        <div className={comun.filtro}>
          <label className={comun.etiquetaFiltro} htmlFor="filtro-proveedor">Proveedor</label>
          <select id="filtro-proveedor" className={comun.control} value={filtros.proveedorId}
            onChange={(e) => cambiar({ proveedorId: e.target.value })}>
            <option value="">Todos</option>
            {proveedores.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
          </select>
        </div>

        <div className={comun.filtro}>
          <label className={comun.etiquetaFiltro} htmlFor="filtro-desde">Factura desde</label>
          <input id="filtro-desde" type="date" value={filtros.desde}
            className={`${comun.control} ${errorFechas ? comun.controlError : ''}`}
            onChange={(e) => cambiar({ desde: e.target.value })} />
        </div>

        <div className={comun.filtro}>
          <label className={comun.etiquetaFiltro} htmlFor="filtro-hasta">Hasta</label>
          <input id="filtro-hasta" type="date" value={filtros.hasta}
            className={`${comun.control} ${errorFechas ? comun.controlError : ''}`}
            onChange={(e) => cambiar({ hasta: e.target.value })} />
        </div>

        <div className={comun.filtro}>
          <label className={comun.etiquetaFiltro} htmlFor="filtro-pago">Pago</label>
          <select id="filtro-pago" className={comun.control} value={filtros.formaPago}
            onChange={(e) => cambiar({
              formaPago: e.target.value,
              // En efectivo no hay cuenta: se quita en vez de dejar un filtro que siempre da vacío.
              ...(e.target.value === 'EFECTIVO' ? { cuentaId: '' } : {}),
            })}>
            <option value="">Todos</option>
            <option value="EFECTIVO">Efectivo</option>
            <option value="TRANSFERENCIA">Transferencia</option>
          </select>
        </div>

        {filtros.formaPago !== 'EFECTIVO' && (
          <div className={comun.filtro}>
            <label className={comun.etiquetaFiltro} htmlFor="filtro-cuenta">Cuenta</label>
            <select id="filtro-cuenta" className={comun.control} value={filtros.cuentaId}
              onChange={(e) => cambiar({ cuentaId: e.target.value })}>
              <option value="">Todas</option>
              {cuentas.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
            </select>
          </div>
        )}

        <div className={comun.filtro}>
          <label className={comun.etiquetaFiltro} htmlFor="filtro-factura">N.º de factura</label>
          <input id="filtro-factura" type="search" className={comun.control} value={factura}
            onChange={(e) => setFactura(e.target.value)} placeholder="FV-9912"
            autoComplete="off" spellCheck="false" />
        </div>

        <div className={comun.filtro}>
          <label className={comun.etiquetaFiltro} htmlFor="filtro-estado">Estado</label>
          {/* Vigentes es lo normal y no se escribe en la URL: solo aparece si se cambia. */}
          <select id="filtro-estado" className={comun.control} value={filtros.estado}
            onChange={(e) => cambiar({ estado: e.target.value === 'VIGENTE' ? '' : e.target.value })}>
            <option value="VIGENTE">Vigentes</option>
            <option value="ANULADA">Anuladas</option>
            <option value="TODAS">Todas</option>
          </select>
        </div>

        {hayFiltros(filtros) && (
          <Boton variante="fantasma" onClick={limpiarFiltros}>Limpiar filtros</Boton>
        )}

        {errorFechas && (
          <p className={comun.errorFiltro} role="alert"><span aria-hidden>⚠</span> {errorFechas}</p>
        )}
      </section>

      <AvisoCarga error={error} onReintentar={() => setIntento((n) => n + 1)}
        desactualizado={datos != null} />

      {!errorFechas && (
        <BarraTotales
          soloAnuladas={consultaTotales == null}
          verTodas={filtros.estado === TODAS}
          cargando={claveTotales != null && totales.clave !== claveTotales}
          datos={totales.datos}
          error={totales.error}
        />
      )}

      {!datos && cargando && <p className={comun.vacio}>Cargando compras…</p>}

      {datos && !errorFechas && (
        <Listado
          datos={datos}
          cargando={cargando}
          conFiltros={hayFiltros(filtros)}
          repuesto={filtros.repuesto}
          otrosFiltros={hayFiltros({ ...filtros, repuesto: '' })}
          rutaDe={rutaDe}
          onAbrir={irADetalle}
          onLimpiar={limpiarFiltros}
          onPagina={(p) => cambiar({ p: p > 0 ? String(p) : '' })}
        />
      )}
    </div>
  )
}

const unaOVarias = (n) => `${n} ${n === 1 ? 'compra' : 'compras'}`

/**
 * Los totales del período filtrado (spec 0002, H6). Mientras llegan los de un filtro nuevo se ven
 * los anteriores atenuados; si fallan, no se ve ninguna cifra.
 */
function BarraTotales({ soloAnuladas, verTodas, cargando, datos, error }) {
  if (soloAnuladas) {
    return (
      <p className={estilos.notaTotales}>
        Las compras anuladas no suman en los totales: se deshicieron, esa plata no cuenta como pagada.
      </p>
    )
  }
  if (error && !cargando) {
    return (
      <p className={estilos.errorTotales} role="alert">
        <span aria-hidden>⚠</span> No se pudieron calcular los totales. {error.message}
      </p>
    )
  }

  const r = resumenDeTotales(datos)
  const cifra = (valor) => (r ? formatoCOP(valor) : '—')

  return (
    <section className={`${estilos.totales} ${cargando ? comun.atenuado : ''}`}
      aria-label="Totales de las compras filtradas" aria-busy={cargando}>
      <div className={estilos.tarjeta}>
        <span className={estilos.etiqueta}>Total pagado</span>
        <span className={estilos.valor}>{cifra(r?.total)}</span>
        <span className={estilos.nota}>
          {r ? unaOVarias(r.compras) : 'Calculando…'}
          {verTodas ? ' · sin contar las anuladas' : ''}
        </span>
      </div>

      <div className={estilos.tarjeta}>
        <span className={estilos.etiqueta}>Efectivo</span>
        <span className={estilos.valor}>{cifra(r?.efectivo.total)}</span>
        <span className={estilos.nota}>{r && unaOVarias(r.efectivo.compras)}</span>
      </div>

      <div className={estilos.tarjeta}>
        <span className={estilos.etiqueta}>Transferencia</span>
        <span className={estilos.valor}>{cifra(r?.transferencia.total)}</span>
        {r && r.transferencia.cuentas.length > 0 ? (
          <ul className={estilos.cuentas}>
            {r.transferencia.cuentas.map((c) => (
              <li key={c.id}>
                <span className={estilos.cuentaNombre}>{c.nombre}</span>
                <span className={estilos.cuentaCifra}>{formatoCOP(c.total)}</span>
                <span className={estilos.cuentaCompras}>{unaOVarias(c.compras)}</span>
              </li>
            ))}
          </ul>
        ) : (
          <span className={estilos.nota}>{r && unaOVarias(0)}</span>
        )}
      </div>

      {/* Las partes y el total salen de consultas distintas. Si no cuadran, se dice en rojo. */}
      {r && r.descuadre !== 0 && (
        <p className={estilos.descuadre} role="alert">
          <span aria-hidden>⚠</span> Los totales no cuadran: efectivo y transferencias suman{' '}
          {formatoCOP(r.total + r.descuadre)} y el total es {formatoCOP(r.total)}.
        </p>
      )}
    </section>
  )
}

function Listado({ datos, cargando, conFiltros, repuesto, otrosFiltros, rutaDe, onAbrir, onLimpiar, onPagina }) {
  const { elementos, total, numero: pagina, tamano, totalPaginas } = datos

  if (total === 0) {
    return conFiltros ? (
      <div className={comun.vacio}>
        <p>
          {repuesto
            ? `Ninguna compra${otrosFiltros ? ' con esos filtros' : ''} tiene un repuesto con «${repuesto}».`
            : 'Ninguna compra coincide con los filtros.'}
        </p>
        <Boton variante="secundario" onClick={onLimpiar}>Limpiar filtros</Boton>
      </div>
    ) : (
      <div className={comun.vacio}>
        <p>Todavía no hay compras registradas.</p>
        <Link to="/compras" className={comun.accion}>Registrar compra</Link>
      </div>
    )
  }

  // La página pedida ya no existe (la URL es vieja, o el filtro dejó menos resultados).
  if (elementos.length === 0) {
    return (
      <div className={comun.vacio}>
        <p>Esa página ya no existe.</p>
        <Boton variante="secundario" onClick={() => onPagina(0)}>Ir a la primera</Boton>
      </div>
    )
  }

  return (
    <>
      <div className={`scroll-x ${comun.marco} ${cargando ? comun.atenuado : ''}`} aria-busy={cargando}>
        <table className={comun.tabla}>
          <thead>
            <tr>
              <th>Fecha factura</th>
              <th>Proveedor</th>
              <th>Factura</th>
              <th>Pago</th>
              <th className="cifra">Renglones</th>
              <th className="cifra">Total</th>
            </tr>
          </thead>
          <tbody>
            {elementos.map((c) => (
              <tr
                key={c.id}
                className={comun.fila}
                // Toda la fila abre la compra; el enlace del proveedor es para el teclado.
                onClick={(e) => { if (!e.target.closest('a')) onAbrir(c.id) }}
              >
                <td>{fechaDia(fechaLocal(c.fechaDocumento))}</td>
                <td>
                  <Link to={rutaDe(c.id)} className={comun.enlace}
                    onClick={(e) => { e.preventDefault(); onAbrir(c.id) }}>
                    {c.proveedor}
                  </Link>
                  <LineaDeLaFactura factura={c} busqueda={repuesto} />
                </td>
                <td>
                  {c.numeroFactura
                    ? <span className={comun.mono}>{c.numeroFactura}</span>
                    : <span className={comun.tenue}>Sin número</span>}
                </td>
                <td>
                  {textoDelPago(c.formaPago, c.cuenta, c.pagadaDeCaja)}
                  {c.estado === 'ANULADA' && <span className={comun.anulada}>Anulada</span>}
                </td>
                <td className="cifra">{c.renglones}</td>
                <td className={`cifra ${c.estado === 'ANULADA' ? comun.tachado : ''}`}>
                  <strong>{formatoCOP(c.total)}</strong>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <nav className={comun.paginacion} aria-label="Páginas">
        <span className={comun.rango}>{rangoDePagina(pagina, tamano, total)}</span>
        <Boton variante="secundario" tamano="chico" disabled={pagina === 0}
          onClick={() => onPagina(pagina - 1)}>
          ← Anterior
        </Boton>
        <Boton variante="secundario" tamano="chico" disabled={pagina + 1 >= totalPaginas}
          onClick={() => onPagina(pagina + 1)}>
          Siguiente →
        </Boton>
      </nav>
    </>
  )
}

/**
 * Debajo del proveedor, qué trae la factura, para distinguirla sin abrirla (spec 0002, RF-027 y RF-028).
 * Sin buscar, sus primeros repuestos. Buscando uno, los que coinciden, resaltados: los mismos que se
 * resaltan al abrirla.
 */
function LineaDeLaFactura({ factura, busqueda }) {
  const { visibles, restantes, resaltar } = lineaDeLaFactura(factura, busqueda)
  if (visibles.length === 0) return null
  return (
    <span className={estilos.coinciden}>
      <span aria-hidden>↳ </span>
      {visibles.map((r, i) => (
        <span key={r.lineaId} className={estilos.coincidencia}>
          {i > 0 && ' · '}
          {resaltar ? <Resaltado texto={r.descripcion} busqueda={busqueda} /> : r.descripcion}
          {r.porque && <> (<Resaltado texto={r.porque} busqueda={busqueda} />)</>}
          <span className={estilos.cantidad}> × {r.cantidad}</span>
        </span>
      ))}
      {restantes > 0 && <span className={estilos.mas}> · y {restantes} más</span>}
    </span>
  )
}
