import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import Boton from '../componentes/Boton'
import InsigniaStock from '../componentes/inventario/InsigniaStock'
import { inventarioApi } from '../api/cliente'
import { useSesion } from '../componentes/sesion/contexto'
import { formatoCOP, formatoCosto, GUION, margen } from '../utils/formato'
import { paginaDeLaUrl, rangoDePagina } from '../utils/inventario'
import { esAdministrador, veCostos } from '../utils/permisos'
import estilos from './Inventario.module.css'

const TAMANO = 25
const numero = (n) => Number(n ?? 0).toLocaleString('es-CO')

/**
 * Lo que hay en el almacén.
 *
 * <p>Los filtros viven en la URL (`?q=filtro&bajo=1&p=2`), no solo en el estado. Así, al abrir un
 * repuesto y volver, la lista está donde se dejó, y un filtro se puede compartir o dejar en
 * favoritos ("lo que hay que pedir" es `/inventario?bajo=1`).
 *
 * <p>El cajero lo ve sin costos (spec 0004, decisión 1): ni el valor del inventario ni las columnas de costo, margen
 * y valor. Se esconden por su rol, no porque falte el dato: el servidor tampoco se los manda.
 */
export default function Inventario() {
  const { usuario } = useSesion()
  const conCostos = veCostos(usuario)
  const navigate = useNavigate()
  const location = useLocation()
  const [params, setParams] = useSearchParams()
  const texto = params.get('q') ?? ''
  const soloBajo = params.get('bajo') === '1'
  // Spec 0005, RF-012: los repuestos de antes de que la categoría fuera obligatoria, para corregirlos.
  const sinCategoria = params.get('sin') === '1'
  const pagina = paginaDeLaUrl(params.get('p'))

  // Lo tecleado va aparte de la URL: la URL cambia con una pausa, para no pedir una página por
  // cada letra.
  const [escrito, setEscrito] = useState(texto)
  const [intento, setIntento] = useState(0)

  // Cada respuesta recuerda para qué filtros llegó. "Cargando" se DERIVA de comparar esa clave
  // con la actual, en vez de prenderse y apagarse con setState dentro del efecto.
  const clave = JSON.stringify([texto, soloBajo, sinCategoria, pagina, intento])
  const [listado, setListado] = useState({ clave: null, datos: null, error: null })
  const [resumen, setResumen] = useState({ datos: null, error: null })

  useEffect(() => {
    const limpio = escrito.trim()
    if (limpio === texto) return
    const espera = setTimeout(() => {
      setParams((actual) => {
        const nuevos = new URLSearchParams(actual)
        if (limpio) nuevos.set('q', limpio)
        else nuevos.delete('q')
        nuevos.delete('p')   // otra búsqueda empieza en la primera página
        return nuevos
      }, { replace: true })
    }, 250)
    return () => clearTimeout(espera)
  }, [escrito, texto, setParams])

  useEffect(() => {
    let vigente = true
    inventarioApi.listar({ texto, soloStockBajo: soloBajo, sinCategoria, pagina, tamano: TAMANO })
      .then((datos) => { if (vigente) setListado({ clave, datos, error: null }) })
      // Si falla, se conservan los datos anteriores: mejor verlos atenuados que una pantalla vacía.
      .catch((error) => { if (vigente) setListado((l) => ({ clave, datos: l.datos, error })) })
    // Una respuesta que llega tarde, de una búsqueda que ya se cambió, no pisa la actual.
    return () => { vigente = false }
  }, [clave, texto, soloBajo, sinCategoria, pagina])

  useEffect(() => {
    let vigente = true
    inventarioApi.resumen()
      .then((datos) => { if (vigente) setResumen({ datos, error: null }) })
      .catch((error) => { if (vigente) setResumen((r) => ({ ...r, error })) })
    return () => { vigente = false }
  }, [intento])

  const cargando = listado.clave !== clave
  const error = cargando ? null : listado.error
  const datos = listado.datos
  const reintentar = () => setIntento((n) => n + 1)

  function cambiarParametro(nombre, valor) {
    setParams((actual) => {
      const nuevos = new URLSearchParams(actual)
      if (valor) nuevos.set(nombre, valor)
      else nuevos.delete(nombre)
      if (nombre !== 'p') nuevos.delete('p')
      return nuevos
    })
  }

  function limpiarBusqueda() {
    setEscrito('')
    cambiarParametro('q', null)
  }

  /** Se lleva los filtros para que "← Inventario" vuelva a esta misma lista. */
  const irAFicha = (id) => navigate(`/inventario/${id}`, { state: { desde: location.search } })

  const r = resumen.datos

  return (
    <div className={estilos.pagina}>
      <header className={estilos.encabezado}>
        <div>
          <h1 className={estilos.titulo}>Inventario</h1>
          <p className={estilos.subtitulo}>
            {conCostos
              ? 'Stock y costo salen del kardex: cambian con compras, ventas y ajustes, nunca a mano.'
              : 'El stock sale del kardex: cambia con compras, ventas y ajustes, nunca a mano.'}
          </p>
        </div>
        {esAdministrador(usuario) && <Link to="/compras" className={estilos.accion}>+ Registrar compra</Link>}
      </header>

      {/* ── Totales ───────────────────────────────────────────────────────── */}
      <section className={estilos.resumen} aria-label="Totales del inventario">
        <div className={estilos.tarjeta}>
          <span className={estilos.tarjetaEtiqueta}>Referencias</span>
          <span className={estilos.tarjetaValor}>{r ? numero(r.referencias) : GUION}</span>
        </div>
        <div className={estilos.tarjeta}>
          <span className={estilos.tarjetaEtiqueta}>Unidades</span>
          <span className={estilos.tarjetaValor}>{r ? numero(r.unidades) : GUION}</span>
        </div>
        {conCostos && <div className={estilos.tarjeta}>
          <span className={estilos.tarjetaEtiqueta}>Valor al costo</span>
          <span className={estilos.tarjetaValor}>{r ? formatoCOP(r.valor) : GUION}</span>
          {/* Si hay unidades sin costo conocido, el valor se queda corto y se dice. */}
          <span className={r?.sinCosto > 0 ? estilos.tarjetaAviso : estilos.tarjetaNota}>
            {r?.sinCosto > 0
              ? `No incluye ${r.sinCosto} ${r.sinCosto === 1 ? 'repuesto' : 'repuestos'} sin costo`
              : 'Unidades × costo promedio'}
          </span>
        </div>}
        <div className={estilos.tarjeta}>
          <span className={estilos.tarjetaEtiqueta}>Por pedir</span>
          <span className={estilos.tarjetaValor}>{r ? numero(r.conStockBajo) : GUION}</span>
          <span className={estilos.tarjetaNota}>En su mínimo o por debajo</span>
        </div>
      </section>

      {/* ── Filtros ───────────────────────────────────────────────────────── */}
      <section className={estilos.filtros}>
        <input
          type="search"
          className={estilos.buscador}
          value={escrito}
          onChange={(e) => setEscrito(e.target.value)}
          placeholder="Buscar por código, nombre, marca o moto…"
          aria-label="Buscar en el inventario"
          autoComplete="off"
          spellCheck="false"
        />
        <button
          type="button"
          className={`${estilos.chip} ${soloBajo ? estilos.chipActivo : ''}`}
          aria-pressed={soloBajo}
          onClick={() => cambiarParametro('bajo', soloBajo ? null : '1')}
        >
          Solo por pedir{r ? ` (${numero(r.conStockBajo)})` : ''}
        </button>
        <button
          type="button"
          className={`${estilos.chip} ${sinCategoria ? estilos.chipActivo : ''}`}
          aria-pressed={sinCategoria}
          title="Los que no aparecen por categoría en el catálogo del mostrador. Se corrigen desde su ficha."
          onClick={() => cambiarParametro('sin', sinCategoria ? null : '1')}
        >
          Sin categoría
        </button>
      </section>

      {/* ── Errores ───────────────────────────────────────────────────────── */}
      {error && (
        <div className={error.estado === 0 ? estilos.avisoRed : estilos.avisoError} role="alert">
          <span>
            <span aria-hidden>⚠</span> {error.estado === 0
              ? 'No hay conexión con el servidor.'
              : error.message}
            {datos && ' Lo que ves puede estar desactualizado.'}
          </span>
          <button type="button" className={estilos.reintentar} onClick={reintentar}>
            Reintentar
          </button>
        </div>
      )}

      {/* ── Listado ───────────────────────────────────────────────────────── */}
      {!datos && cargando && <p className={estilos.vacio}>Cargando inventario…</p>}

      {datos && (
        <Listado
          conCostos={conCostos}
          puedeComprar={esAdministrador(usuario)}
          datos={datos}
          cargando={cargando}
          texto={texto}
          soloBajo={soloBajo}
          sinCategoria={sinCategoria}
          onAbrir={irAFicha}
          onLimpiar={limpiarBusqueda}
          onQuitarFiltro={() => cambiarParametro('bajo', null)}
          onQuitarSinCategoria={() => cambiarParametro('sin', null)}
          onPagina={(p) => cambiarParametro('p', p > 0 ? String(p) : null)}
        />
      )}
    </div>
  )
}

function Listado({ conCostos, puedeComprar, datos, cargando, texto, soloBajo, sinCategoria, onAbrir, onLimpiar, onQuitarFiltro, onQuitarSinCategoria, onPagina }) {
  const { elementos, total, numero: pagina, tamano, totalPaginas } = datos

  if (total === 0) {
    if (texto) {
      return (
        <div className={estilos.vacio}>
          <p>Nada coincide con <strong>“{texto}”</strong>{soloBajo && ' entre los que hay que pedir'}.</p>
          <Boton variante="secundario" onClick={onLimpiar}>Limpiar búsqueda</Boton>
        </div>
      )
    }
    if (sinCategoria) {
      return (
        <div className={estilos.vacio}>
          <p>Todos los repuestos{soloBajo && ' por pedir'} tienen categoría: todos salen en el catálogo del mostrador.</p>
          <Boton variante="secundario" onClick={onQuitarSinCategoria}>Ver todo el inventario</Boton>
        </div>
      )
    }
    if (soloBajo) {
      return (
        <div className={estilos.vacio}>
          <p>Ningún repuesto está en su mínimo. No hay nada por pedir.</p>
          <Boton variante="secundario" onClick={onQuitarFiltro}>Ver todo el inventario</Boton>
        </div>
      )
    }
    return (
      <div className={estilos.vacio}>
        <p>Todavía no hay repuestos. Entran al inventario al registrar su primera compra.</p>
        {puedeComprar && <Link to="/compras" className={estilos.accion}>Registrar compra</Link>}
      </div>
    )
  }

  // La página pedida ya no existe (la URL es vieja, o el filtro dejó menos resultados).
  if (elementos.length === 0) {
    return (
      <div className={estilos.vacio}>
        <p>Esa página ya no existe.</p>
        <Boton variante="secundario" onClick={() => onPagina(0)}>Ir a la primera</Boton>
      </div>
    )
  }

  return (
    <>
      <div className={`scroll-x ${estilos.marco} ${cargando ? estilos.atenuado : ''}`}
        aria-busy={cargando}>
        <table className={estilos.tabla}>
          <thead>
            <tr>
              <th>Código</th>
              <th>Repuesto</th>
              <th>Categoría</th>
              <th className="cifra">Stock</th>
              {conCostos && <th className="cifra">Costo promedio</th>}
              <th className="cifra">Precio venta</th>
              {conCostos && <th className="cifra">Margen</th>}
              {conCostos && <th className="cifra">Valor</th>}
            </tr>
          </thead>
          <tbody>
            {elementos.map((rep) => {
              const m = margen(rep.costoPromedio, rep.precio)
              return (
                <tr
                  key={rep.id}
                  className={estilos.fila}
                  // Toda la fila abre la ficha; el enlace del nombre es para el teclado.
                  onClick={(e) => { if (!e.target.closest('a')) onAbrir(rep.id) }}
                >
                  <td className={estilos.codigo}>{rep.codigo}</td>
                  <td>
                    <Link
                      to={`/inventario/${rep.id}`}
                      className={estilos.nombre}
                      onClick={(e) => { e.preventDefault(); onAbrir(rep.id) }}
                    >
                      {rep.nombre}
                    </Link>
                    <span className={estilos.detalle}>
                      {rep.marca}{rep.aplicacion && ` · ${rep.aplicacion}`}
                    </span>
                  </td>
                  <td className={estilos.categoria}>{rep.categoria ?? 'Sin categoría'}</td>
                  <td className="cifra">
                    <span className={estilos.stock}>
                      <strong>{rep.stock}</strong>
                      <InsigniaStock stock={rep.stock} stockMinimo={rep.stockMinimo} />
                    </span>
                  </td>
                  {conCostos && <td className="cifra">{formatoCosto(rep.costoPromedio)}</td>}
                  <td className="cifra">{formatoCOP(rep.precio)}</td>
                  {conCostos && (
                    <td className={`cifra ${m && m.utilidad < 0 ? estilos.enPerdida : ''}`}>
                      {m ? `${m.utilidad < 0 ? '⚠ ' : ''}${m.porcentaje.toFixed(0)}%` : GUION}
                    </td>
                  )}
                  {conCostos && <td className="cifra">{rep.valor == null ? GUION : formatoCOP(rep.valor)}</td>}
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      <nav className={estilos.paginacion} aria-label="Páginas">
        <span className={estilos.rango}>{rangoDePagina(pagina, tamano, total)}</span>
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
