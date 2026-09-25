import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import Boton from '../componentes/Boton'
import Campo from '../componentes/Campo'
import Modal from '../componentes/Modal'
import AvisoCarga from '../componentes/AvisoCarga'
import PestanasCompras from '../componentes/compra/PestanasCompras'
import ModalProveedorNuevo from '../componentes/compra/ModalProveedorNuevo'
import { cargasApi, catalogoApi, cuentasApi, proveedoresApi } from '../api/cliente'
import {
  conteos, cuadre, FILTROS, filtrar, pagina, porcentajeEscrito, precioEscrito, resumenParaConfirmar,
  textoDeGanancia,
} from '../utils/carga'
import { fechaHora, formatoCOP, formatoCosto, GUION } from '../utils/formato'
import comun from './Listado.module.css'
import estilos from './PreCarga.module.css'

const REDONDEOS = [1, 50, 100, 500, 1000]

/**
 * La pre-carga de una factura (spec 0012, H2 a H7): cada renglón con su costo con IVA, su precio sugerido y lo que le
 * falta, antes de que nada entre al inventario.
 *
 * <p><b>Se guarda sola</b>: cada gesto viaja al servidor en el momento —los que se escriben, al salir del campo— y la
 * respuesta es la carga entera revisada de nuevo, que reemplaza a la que había. Los gestos van en fila, uno detrás
 * del otro: así la última respuesta que llega es la del último gesto.
 *
 * <p>En el celular cada renglón es una tarjeta, no una fila de una tabla ancha, y se ven de a 50.
 */
export default function PreCarga() {
  const { id } = useParams()
  const [carga, setCargaEstado] = useState(null)
  const cargaActual = useRef(null)
  const [errorCarga, setErrorCarga] = useState(null)
  const [intento, setIntento] = useState(0)

  const [proveedores, setProveedores] = useState([])
  const [cuentas, setCuentas] = useState([])
  const [categorias, setCategorias] = useState([])

  const [pendientes, setPendientes] = useState(0)
  const [guardadoEn, setGuardadoEn] = useState(null)
  const [errorGesto, setErrorGesto] = useState(null)
  const cola = useRef(Promise.resolve())

  const [filtro, setFiltro] = useState('TODOS')
  const [texto, setTexto] = useState('')
  const [numeroPagina, setNumeroPagina] = useState(0)
  const [marcados, setMarcados] = useState(() => new Set())
  const [marcaEnBloque, setMarcaEnBloque] = useState('')
  const [categoriaEnBloque, setCategoriaEnBloque] = useState('')

  const [corrigiendo, setCorrigiendo] = useState(null)
  const [confirmando, setConfirmando] = useState(false)
  const [descartando, setDescartando] = useState(false)
  const [proveedorNuevo, setProveedorNuevo] = useState(false)

  const setCarga = useCallback((nueva) => {
    cargaActual.current = nueva
    setCargaEstado(nueva)
  }, [])

  useEffect(() => {
    let vigente = true
    cargasApi.detalle(id)
      .then((c) => { if (vigente) { setCarga(c); setErrorCarga(null) } })
      .catch((e) => vigente && setErrorCarga(e))
    return () => { vigente = false }
  }, [id, intento, setCarga])

  useEffect(() => {
    proveedoresApi.activos().then(setProveedores).catch(() => setProveedores([]))
    cuentasApi.activas().then(setCuentas).catch(() => setCuentas([]))
    catalogoApi.categorias().then(setCategorias).catch(() => setCategorias([]))
  }, [])

  /** Un gesto, en fila detrás de los anteriores. `llamada` recibe la carga como está justo antes de mandarlo. */
  const gesto = useCallback((llamada) => {
    setPendientes((n) => n + 1)
    const siguiente = cola.current
      .then(() => llamada(cargaActual.current))
      .then((nueva) => {
        setCarga(nueva)
        setGuardadoEn(new Date())
        setErrorGesto(null)
      })
      .catch((e) => setErrorGesto(e))
      .finally(() => setPendientes((n) => n - 1))
    cola.current = siguiente
    return siguiente
  }, [setCarga])

  if (errorCarga && !carga) {
    return (
      <div className={comun.pagina}>
        <PestanasCompras />
        <AvisoCarga error={errorCarga} onReintentar={() => setIntento((n) => n + 1)} />
      </div>
    )
  }
  if (!carga) return <div className={comun.pagina}><PestanasCompras /><p>Cargando la pre-carga…</p></div>

  const titulo = `${carga.numeroFactura ?? carga.nombreArchivo ?? 'Pre-carga'}${carga.proveedor ? ` · ${carga.proveedor}` : ''}`

  if (carga.estado !== 'BORRADOR') {
    return (
      <div className={comun.pagina}>
        <PestanasCompras />
        <Link to="/compras/cargas" className={comun.volver}>← Cargas</Link>
        <h1 className={comun.titulo}>{titulo}</h1>
        {carga.estado === 'CONFIRMADA' ? (
          <p className={estilos.cerrada}>
            Confirmada {fechaHora(carga.cerradaEn)}: entró como una compra.{' '}
            <Link to={`/compras/historial/${carga.compraId}`}>Ver la compra</Link> — ahí se corrige o se anula.
          </p>
        ) : (
          <p className={estilos.cerrada}>Descartada {fechaHora(carga.cerradaEn)}. No entró nada al inventario.</p>
        )}
      </div>
    )
  }

  const t = carga.totales
  const estadoCuadre = cuadre(t)
  const lista = filtrar(carga.renglones, filtro, texto)
  const pag = pagina(lista, numeroPagina)
  const cuantos = conteos(carga.renglones)

  const datos = (c) => ({
    proveedorId: c.proveedorId, numeroFactura: c.numeroFactura, fechaFactura: c.fechaFactura,
    formaPago: c.formaPago, cuentaId: c.cuentaId,
  })
  const guardarDatos = (cambio) => gesto((c) => cargasApi.datos(id, { ...datos(c), ...cambio }))
  const guardarRegla = (cambio) => gesto((c) => cargasApi.regla(id,
    { ivaPct: c.ivaPct, gananciaPct: c.gananciaPct, redondeo: c.redondeo, ...cambio }))

  function marcar(posicion, si) {
    setMarcados((antes) => {
      const nuevos = new Set(antes)
      if (si) nuevos.add(posicion)
      else nuevos.delete(posicion)
      return nuevos
    })
  }

  function marcaParaVarios(losQueNoTienen) {
    const marca = marcaEnBloque.trim()
    if (!marca) return
    gesto(() => cargasApi.marca(id, { posiciones: [...marcados], losQueNoTienen, marca }))
      .then(() => setMarcados(new Set()))
  }

  function categoriaParaVarios(losQueNoTienen) {
    if (!categoriaEnBloque) return
    gesto(() => cargasApi.categoria(id, { posiciones: [...marcados], losQueNoTienen, categoriaId: categoriaEnBloque }))
      .then(() => setMarcados(new Set()))
  }

  const nitSinProveedor = !carga.proveedorId && carga.nitProveedor

  return (
    <div className={comun.pagina}>
      <PestanasCompras />
      <Link to="/compras/cargas" className={comun.volver}>← Cargas</Link>

      <header className={comun.encabezado}>
        <div>
          <h1 className={comun.titulo}>{titulo}</h1>
          <p className={comun.subtitulo}>
            {carga.renglones.length} renglones leídos de {carga.nombreArchivo}. Nada de esto está en el inventario todavía.
          </p>
        </div>
        <span className={estilos.guardado} aria-live="polite">
          {pendientes > 0 ? 'Guardando…' : guardadoEn ? `Guardado ${guardadoEn.toLocaleTimeString('es-CO', { hour: 'numeric', minute: '2-digit' })}` : ''}
        </span>
      </header>

      {errorGesto && (
        <p className={estilos.errorGesto} role="alert">
          <span aria-hidden>⚠</span> {errorGesto.estado === 0 ? 'No hay conexión: ese cambio no se guardó.' : errorGesto.message}
        </p>
      )}

      {/* ── La compra que va a ser ─────────────────────────────────────────── */}
      <section className={estilos.panel}>
        <h2 className={estilos.subtitulo}>La compra</h2>
        <div className={estilos.rejilla}>
          <Campo etiqueta="Proveedor" requerido>
            <select
              className={comun.control}
              value={carga.proveedorId ?? ''}
              onChange={(e) => guardarDatos({ proveedorId: e.target.value || null })}
            >
              <option value="">Elige el proveedor…</option>
              {proveedores.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
            </select>
          </Campo>
          <Campo etiqueta="N.º de factura">
            <CampoTexto className={comun.control} valor={carga.numeroFactura ?? ''}
              onGuardar={(v) => guardarDatos({ numeroFactura: v.trim() || null })} />
          </Campo>
          <Campo etiqueta="Fecha de la factura" requerido>
            <input type="date" className={comun.control} value={carga.fechaFactura ?? ''}
              onChange={(e) => guardarDatos({ fechaFactura: e.target.value || null })} />
          </Campo>
          <Campo etiqueta="Forma de pago" requerido>
            <div className={estilos.segmento} role="group" aria-label="Forma de pago">
              {[['EFECTIVO', 'Efectivo'], ['TRANSFERENCIA', 'Transferencia']].map(([valor, nombre]) => (
                <button key={valor} type="button" aria-pressed={carga.formaPago === valor}
                  className={carga.formaPago === valor ? estilos.segmentoActivo : estilos.segmentoOpcion}
                  onClick={() => guardarDatos({ formaPago: valor, ...(valor === 'EFECTIVO' ? { cuentaId: null } : {}) })}>
                  {nombre}
                </button>
              ))}
            </div>
          </Campo>
          {carga.formaPago === 'TRANSFERENCIA' && (
            <Campo etiqueta="Desde la cuenta" requerido>
              <select className={comun.control} value={carga.cuentaId ?? ''}
                onChange={(e) => guardarDatos({ cuentaId: e.target.value || null })}>
                <option value="">Elige la cuenta…</option>
                {cuentas.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
              </select>
            </Campo>
          )}
        </div>
        {nitSinProveedor && (
          <p className={estilos.nota}>
            El NIT de la factura ({carga.nitProveedor}) no está en ningún proveedor.{' '}
            <button type="button" className={estilos.enlace} onClick={() => setProveedorNuevo(true)}>
              Crear el proveedor con ese NIT
            </button>
          </p>
        )}
      </section>

      {/* ── La regla del precio y el cuadre ────────────────────────────────── */}
      <section className={estilos.dosColumnas}>
        <div className={estilos.panel}>
          <h2 className={estilos.subtitulo}>Precio sugerido</h2>
          <p className={estilos.explicacion}>
            Costo por unidad (el valor total del renglón entre la cantidad), más el IVA, más la ganancia, redondeado
            hacia arriba. Los precios que cambies a mano no se mueven al cambiar esto.
          </p>
          <div className={estilos.rejillaRegla}>
            <Campo etiqueta="IVA %">
              <CampoTexto className={comun.control} inputMode="decimal" valor={String(carga.ivaPct)}
                onGuardar={(v) => { const n = porcentajeEscrito(v); if (n != null) guardarRegla({ ivaPct: n }) }} />
            </Campo>
            <Campo etiqueta="Ganancia %">
              <CampoTexto className={comun.control} inputMode="decimal" valor={String(carga.gananciaPct)}
                onGuardar={(v) => { const n = porcentajeEscrito(v); if (n != null) guardarRegla({ gananciaPct: n }) }} />
            </Campo>
            <Campo etiqueta="Redondear a">
              <select className={comun.control} value={carga.redondeo}
                onChange={(e) => guardarRegla({ redondeo: Number(e.target.value) })}>
                {REDONDEOS.map((r) => <option key={r} value={r}>{r === 1 ? 'Sin redondear' : formatoCOP(r)}</option>)}
              </select>
            </Campo>
          </div>
        </div>

        <div className={`${estilos.panel} ${estadoCuadre.estado === 'CUADRA' ? estilos.cuadra : estilos.noCuadra}`}>
          <h2 className={estilos.subtitulo}>¿Cuadra con la factura?</h2>
          <p className={estilos.textoCuadre}>
            <span aria-hidden>{estadoCuadre.estado === 'CUADRA' ? '✓' : '⚠'}</span> {estadoCuadre.texto}
          </p>
          {!carga.subtotalLeido && (
            <Campo etiqueta="Sub-total de la factura (antes del IVA)" requerido>
              <CampoTexto className={comun.control} inputMode="numeric"
                valor={carga.subtotalFactura != null ? String(carga.subtotalFactura) : ''}
                onGuardar={(v) => gesto(() => cargasApi.subtotal(id, precioEscrito(v)))} />
            </Campo>
          )}
          <dl className={estilos.cifras}>
            <dt>Entra sin IVA</dt><dd>{formatoCOP(t.subtotalIncluido)}</dd>
            <dt>IVA ({carga.ivaPct}%)</dt><dd>{formatoCOP(t.iva)}</dd>
            <dt>Compra por</dt><dd className={estilos.total}>{formatoCOP(t.totalConIva)}</dd>
            {carga.totalImpreso != null && (<><dt>La factura dice</dt><dd>{formatoCOP(carga.totalImpreso)}</dd></>)}
          </dl>
        </div>
      </section>

      {/* ── Los renglones ──────────────────────────────────────────────────── */}
      <section className={estilos.herramientas}>
        <div className={estilos.filtros} role="group" aria-label="Filtrar renglones">
          {FILTROS.map((f) => (
            <button key={f.id} type="button" aria-pressed={filtro === f.id}
              className={filtro === f.id ? estilos.chipActivo : estilos.chip}
              onClick={() => { setFiltro(f.id); setNumeroPagina(0) }}>
              {f.nombre} <span className={estilos.cuenta}>{cuantos[f.id]}</span>
            </button>
          ))}
        </div>
        <input className={`${comun.control} ${estilos.buscar}`} type="search" placeholder="Buscar código, descripción o marca"
          value={texto} onChange={(e) => { setTexto(e.target.value); setNumeroPagina(0) }} aria-label="Buscar renglón" />

        <div className={estilos.bloque}>
          <input className={comun.control} placeholder="Marca" value={marcaEnBloque}
            onChange={(e) => setMarcaEnBloque(e.target.value)} aria-label="Marca para varios" />
          <Boton onClick={() => marcaParaVarios(false)} disabled={!marcaEnBloque.trim() || marcados.size === 0}>
            A los {marcados.size} marcados
          </Boton>
          <Boton onClick={() => marcaParaVarios(true)} disabled={!marcaEnBloque.trim()}>
            A todos los que no tienen
          </Boton>
        </div>
        <div className={estilos.bloque}>
          <select className={comun.control} value={categoriaEnBloque} onChange={(e) => setCategoriaEnBloque(e.target.value)}
            aria-label="Categoría para varios">
            <option value="">Categoría…</option>
            {categorias.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
          </select>
          <Boton onClick={() => categoriaParaVarios(false)} disabled={!categoriaEnBloque || marcados.size === 0}>
            A los {marcados.size} marcados
          </Boton>
          <Boton onClick={() => categoriaParaVarios(true)} disabled={!categoriaEnBloque}>
            A todos los que no tienen
          </Boton>
        </div>
      </section>

      {lista.length === 0 ? (
        <p className={comun.vacio}>Ningún renglón con ese filtro.</p>
      ) : (
        <ul className={estilos.renglones}>
          {pag.renglones.map((r) => (
            <TarjetaRenglon
              key={r.posicion}
              renglon={r}
              categorias={categorias}
              marcado={marcados.has(r.posicion)}
              onMarcar={(si) => marcar(r.posicion, si)}
              onPrecio={(precio) => gesto(() => cargasApi.precio(id, r.posicion, precio))}
              onSugerido={() => gesto(() => cargasApi.volverAlSugerido(id, r.posicion))}
              onMarca={(marca) => gesto(() => cargasApi.marca(id, { posiciones: [r.posicion], marca }))}
              onCategoria={(categoriaId) => gesto(() => cargasApi.categoria(id, { posiciones: [r.posicion], categoriaId }))}
              onQuitar={(valor) => gesto(() => cargasApi.quitado(id, r.posicion, valor))}
              onPrecioNuevo={(valor) => gesto(() => cargasApi.precioNuevo(id, r.posicion, valor))}
              onCorregir={() => setCorrigiendo(r)}
            />
          ))}
        </ul>
      )}

      {pag.paginas > 1 && (
        <nav className={comun.paginacion} aria-label="Páginas">
          <span className={comun.rango}>Página {pag.actual + 1} de {pag.paginas} · {lista.length} renglones</span>
          <Boton onClick={() => setNumeroPagina(pag.actual - 1)} disabled={pag.actual === 0}>Anterior</Boton>
          <Boton onClick={() => setNumeroPagina(pag.actual + 1)} disabled={pag.actual >= pag.paginas - 1}>Siguiente</Boton>
        </nav>
      )}

      {/* ── Confirmar ──────────────────────────────────────────────────────── */}
      <section className={estilos.pie}>
        {carga.problemas.length > 0 && (
          <ul className={estilos.problemasCarga} role="alert">
            {carga.problemas.map((p) => <li key={p.tipo}><span aria-hidden>⚠</span> {p.mensaje}</li>)}
          </ul>
        )}
        <div className={estilos.acciones}>
          <Boton variante="fantasma" onClick={() => setDescartando(true)}>Descartar la carga</Boton>
          <Boton variante="primario" tamano="grande" disabled={!carga.sePuedeConfirmar || pendientes > 0}
            onClick={() => setConfirmando(true)}>
            Confirmar: compra por {formatoCOP(t.totalConIva)}
          </Boton>
        </div>
      </section>

      {corrigiendo && (
        <ModalLectura
          renglon={corrigiendo}
          onCerrar={() => setCorrigiendo(null)}
          onGuardar={(datosLectura) => {
            gesto(() => cargasApi.lectura(id, corrigiendo.posicion, datosLectura))
            setCorrigiendo(null)
          }}
        />
      )}
      {confirmando && (
        <ModalConfirmar
          carga={carga}
          onCerrar={() => setConfirmando(false)}
          onRecargar={() => setIntento((n) => n + 1)}
        />
      )}
      {descartando && (
        <ModalDescartar id={id} onCerrar={() => setDescartando(false)} onDescartada={() => setIntento((n) => n + 1)} />
      )}
      {proveedorNuevo && (
        <ModalProveedorNuevo
          abierto
          nombreInicial={carga.origen === 'PDF_JOTAPARTES' ? 'Importadora Jotapartes' : ''}
          nitInicial={carga.nitProveedor ?? ''}
          onCerrar={() => setProveedorNuevo(false)}
          onCreado={(p) => {
            setProveedorNuevo(false)
            setProveedores((antes) => [...antes, p])
            guardarDatos({ proveedorId: p.id })
          }}
        />
      )}
    </div>
  )
}

/**
 * Un campo que se guarda al salir de él o con Enter, no con cada tecla. Mientras se escribe, lo que llega del
 * servidor no pisa lo escrito.
 */
function CampoTexto({ valor, onGuardar, className, ...props }) {
  const [texto, setTexto] = useState(valor)
  const [editando, setEditando] = useState(false)
  const mostrado = editando ? texto : valor

  // Lo que dice el campo al salir, no el último estado pintado: pegar un precio y tocar otro lado de inmediato
  // sale antes de que React alcance a pintar lo pegado.
  function guardar(escrito) {
    setEditando(false)
    if (escrito !== valor) onGuardar(escrito)
  }

  return (
    <input
      className={className}
      value={mostrado}
      onFocus={() => { setTexto(valor); setEditando(true) }}
      onChange={(e) => setTexto(e.target.value)}
      onBlur={(e) => guardar(e.currentTarget.value)}
      onKeyDown={(e) => { if (e.key === 'Enter') e.currentTarget.blur() }}
      {...props}
    />
  )
}

function TarjetaRenglon({
  renglon: r, categorias, marcado, onMarcar, onPrecio, onSugerido, onMarca, onCategoria, onQuitar, onPrecioNuevo,
  onCorregir,
}) {
  const reposicion = r.existente != null
  const problemas = r.problemas ?? []
  const avisos = r.avisos ?? []
  const precioQueQueda = reposicion && !r.aplicarPrecioNuevo ? r.existente.precio : r.precioFinal
  const clase = r.quitado ? estilos.quitado : problemas.length ? estilos.conProblema : ''

  return (
    <li className={`${estilos.renglon} ${clase}`}>
      <div className={estilos.cabezaRenglon}>
        <label className={estilos.marcar}>
          <input type="checkbox" checked={marcado} onChange={(e) => onMarcar(e.target.checked)} disabled={r.quitado} />
          <span className={comun.mono}>{r.codigo ?? 'Sin código'}</span>
        </label>
        <span className={estilos.ubicacion}>{r.ubicacion}</span>
        {reposicion && <span className={estilos.sello}>Ya existe · stock {r.existente.stock} · {formatoCOP(r.existente.precio)}</span>}
        {r.ajustadoAMano && <span className={estilos.selloAjustado}>Ajustado a mano</span>}
      </div>

      <p className={estilos.descripcion}>{r.descripcion ?? GUION}</p>

      <div className={estilos.cuerpo}>
        <dl className={estilos.numeros}>
          <dt>Cantidad</dt><dd>{r.cantidad ?? GUION}{r.unidad ? ` ${r.unidad}` : ''}</dd>
          <dt>Valor total</dt><dd>{r.valorTotal != null ? formatoCOP(r.valorTotal) : GUION}</dd>
          <dt>Costo c/u con IVA</dt><dd>{formatoCosto(r.costoPorUnidad)}</dd>
          <dt>Sugerido</dt><dd>{r.sugerido != null ? formatoCOP(r.sugerido) : GUION}</dd>
        </dl>

        <div className={estilos.precio}>
          <label className={estilos.etiqueta} htmlFor={`precio-${r.posicion}`}>
            {reposicion && !r.aplicarPrecioNuevo ? 'Precio nuevo (no se aplica)' : 'Precio de venta'}
          </label>
          <CampoTexto id={`precio-${r.posicion}`} className={estilos.entradaPrecio} inputMode="numeric"
            disabled={r.quitado}
            valor={r.precioFinal != null ? formatoCOP(r.precioFinal) : ''}
            onGuardar={(v) => { const n = precioEscrito(v); if (n) onPrecio(n) }} />
          {r.ajustadoAMano && !r.quitado && (
            <button type="button" className={estilos.enlace} onClick={onSugerido}>Volver al sugerido</button>
          )}
          <span className={estilos.ganancia}>
            {reposicion && !r.aplicarPrecioNuevo && `Con el precio que ya tiene (${formatoCOP(r.existente.precio)}): `}
            {textoDeGanancia(r.costoPorUnidad, precioQueQueda)}
          </span>
          {reposicion && (
            <label className={estilos.opcion}>
              <input type="checkbox" checked={r.aplicarPrecioNuevo} disabled={r.quitado}
                onChange={(e) => onPrecioNuevo(e.target.checked)} />
              Cambiarle el precio al que ya existe
            </label>
          )}
        </div>

        {!reposicion && (
          <div className={estilos.ficha}>
            <label className={estilos.etiqueta} htmlFor={`marca-${r.posicion}`}>
              Marca {r.marcaPropuesta && <span className={estilos.propuesta}>propuesta</span>}
            </label>
            <CampoTexto id={`marca-${r.posicion}`} className={estilos.entrada} disabled={r.quitado}
              valor={r.marca ?? ''} onGuardar={(v) => { if (v.trim()) onMarca(v.trim()) }} />
            {r.marcaPropuesta && !r.quitado && (
              <button type="button" className={estilos.enlace} onClick={() => onMarca(r.marca)}>Está bien</button>
            )}
            <label className={estilos.etiqueta} htmlFor={`categoria-${r.posicion}`}>
              Categoría {r.categoriaPropuesta && <span className={estilos.propuesta}>propuesta</span>}
            </label>
            <select id={`categoria-${r.posicion}`} className={estilos.entrada} disabled={r.quitado}
              value={r.categoriaId ?? ''} onChange={(e) => e.target.value && onCategoria(e.target.value)}>
              <option value="">Elige…</option>
              {categorias.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
            </select>
            {r.categoriaPropuesta && !r.quitado && (
              <button type="button" className={estilos.enlace} onClick={() => onCategoria(r.categoriaId)}>Está bien</button>
            )}
          </div>
        )}
      </div>

      {problemas.length > 0 && (
        <ul className={estilos.problemas}>
          {problemas.map((p, i) => <li key={i}><span aria-hidden>⚠</span> {p.mensaje}</li>)}
        </ul>
      )}
      {avisos.length > 0 && (
        <ul className={estilos.avisos}>
          {avisos.map((p, i) => <li key={i}><span aria-hidden>⚠</span> {p.mensaje}</li>)}
        </ul>
      )}

      <div className={estilos.pieRenglon}>
        <button type="button" className={estilos.enlace} onClick={onCorregir} disabled={r.quitado}>Corregir lo leído</button>
        <button type="button" className={estilos.enlace} onClick={() => onQuitar(!r.quitado)}>
          {r.quitado ? 'Volver a incluirlo' : 'Quitar de la carga'}
        </button>
      </div>
    </li>
  )
}

/** Lo que se leyó mal, escrito mirando el papel (§6 del spec). */
function ModalLectura({ renglon, onCerrar, onGuardar }) {
  const [codigo, setCodigo] = useState(renglon.codigo ?? '')
  const [descripcion, setDescripcion] = useState(renglon.descripcion ?? '')
  const [cantidad, setCantidad] = useState(renglon.cantidad != null ? String(renglon.cantidad) : '')
  const [valorTotal, setValorTotal] = useState(renglon.valorTotal != null ? String(renglon.valorTotal) : '')
  const [precioUnitario, setPrecioUnitario] = useState(renglon.precioUnitario != null ? String(renglon.precioUnitario) : '')
  const [descuento, setDescuento] = useState(renglon.descuentoPct != null ? String(renglon.descuentoPct) : '')

  const guardar = () => onGuardar({
    codigo: codigo.trim() || null,
    descripcion: descripcion.trim() || null,
    cantidad: cantidad.trim() && /^\d+$/.test(cantidad.trim()) ? Number(cantidad.trim()) : null,
    valorTotal: precioEscrito(valorTotal),
    precioUnitario: precioEscrito(precioUnitario),
    descuentoPct: porcentajeEscrito(descuento),
  })

  return (
    <Modal abierto titulo={`Corregir lo leído · ${renglon.ubicacion ?? ''}`} onCerrar={onCerrar}
      pie={<><Boton variante="fantasma" onClick={onCerrar}>Cancelar</Boton><Boton variante="primario" onClick={guardar}>Guardar</Boton></>}>
      <p className={estilos.explicacion}>Mira el renglón en la factura en papel y escribe lo que dice.</p>
      <div className={estilos.rejilla}>
        <Campo etiqueta="Código" value={codigo} onChange={(e) => setCodigo(e.target.value)} />
        <Campo etiqueta="Cantidad" inputMode="numeric" value={cantidad} onChange={(e) => setCantidad(e.target.value)} />
        <Campo etiqueta="Valor total del renglón" inputMode="numeric" value={valorTotal} onChange={(e) => setValorTotal(e.target.value)}
          ayuda="De todas las unidades, sin IVA" />
        <Campo etiqueta="Precio unitario" inputMode="numeric" value={precioUnitario} onChange={(e) => setPrecioUnitario(e.target.value)} />
        <Campo etiqueta="Descuento %" inputMode="decimal" value={descuento} onChange={(e) => setDescuento(e.target.value)} />
      </div>
      <Campo etiqueta="Descripción" value={descripcion} onChange={(e) => setDescripcion(e.target.value)} />
    </Modal>
  )
}

/** El resumen antes del último clic, y el resultado. */
function ModalConfirmar({ carga, onCerrar, onRecargar }) {
  const [estado, setEstado] = useState({ enviando: false, error: null, hecha: null })

  async function confirmar() {
    setEstado({ enviando: true, error: null, hecha: null })
    try {
      const hecha = await cargasApi.confirmar(carga.id, carga.totales.reposiciones)
      setEstado({ enviando: false, error: null, hecha })
    } catch (e) {
      setEstado({ enviando: false, error: e, hecha: null })
    }
  }

  if (estado.hecha) {
    const h = estado.hecha
    return (
      <Modal abierto titulo="Entró al inventario" onCerrar={onRecargar}
        pie={<Link to={`/compras/historial/${h.compraId}`} className={comun.accion}>Ver la compra</Link>}>
        <p>Una compra por <strong>{formatoCOP(h.total)}</strong>: {h.renglones} repuestos, {h.unidades.toLocaleString('es-CO')} unidades.</p>
        <p className={estilos.explicacion}>Si algo quedó mal, se corrige o se anula en la compra, como cualquier otra.</p>
      </Modal>
    )
  }

  const cambio = estado.error?.estado === 409
  return (
    <Modal abierto titulo="Confirmar la carga" onCerrar={onCerrar}
      pie={cambio
        ? <Boton variante="primario" onClick={() => { onCerrar(); onRecargar() }}>Volver a revisar</Boton>
        : <><Boton variante="fantasma" onClick={onCerrar} disabled={estado.enviando}>Todavía no</Boton>
          <Boton variante="primario" onClick={confirmar} disabled={estado.enviando}>
            {estado.enviando ? 'Registrando…' : 'Confirmar'}
          </Boton></>}>
      <ul className={estilos.resumen}>
        {resumenParaConfirmar(carga).map((linea) => <li key={linea}>{linea}</li>)}
      </ul>
      {estado.enviando && (
        <p className={estilos.explicacion}>Registrando {carga.totales.incluidos} renglones. Con una factura grande tarda casi un minuto: no cierres esta pantalla.</p>
      )}
      {estado.error && (
        <p className={estilos.errorGesto} role="alert"><span aria-hidden>⚠</span> {estado.error.estado === 0
          ? 'Se cortó la conexión. Vuelve a confirmar: si ya había entrado, no se duplica.' : estado.error.message}</p>
      )}
    </Modal>
  )
}

function ModalDescartar({ id, onCerrar, onDescartada }) {
  const [error, setError] = useState(null)
  async function descartar() {
    try {
      await cargasApi.descartar(id)
      onDescartada()
    } catch (e) {
      setError(e)
    }
  }
  return (
    <Modal abierto titulo="Descartar la carga" onCerrar={onCerrar}
      pie={<><Boton variante="fantasma" onClick={onCerrar}>No</Boton><Boton variante="peligro" onClick={descartar}>Descartarla</Boton></>}>
      <p>Se pierde lo revisado en esta pre-carga. No entra nada al inventario, y la factura se puede volver a subir.</p>
      {error && <p className={estilos.errorGesto} role="alert"><span aria-hidden>⚠</span> {error.message}</p>}
    </Modal>
  )
}
