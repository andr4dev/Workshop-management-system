import { useEffect, useRef, useState } from 'react'
import AvisoCarga from '../AvisoCarga'
import Boton from '../Boton'
import { inventarioApi } from '../../api/cliente'
import { formatoCOP } from '../../utils/formato'
import {
  categoriaVigente, chipsDeCategorias, consultaDelCatalogo, juntarPaginas, moverResaltado, sePuedeAgregar, TODAS,
} from '../../utils/catalogo'
import estilos from './Catalogo.module.css'

/**
 * El catálogo del mostrador (spec 0005, H1, H2 y H5): *"muéstreme qué aceites tiene"*.
 *
 * Categorías con cuántos repuestos de lo buscado tiene cada una, un buscador propio y la lista con
 * precio y cuántos hay; los agotados al final y apagados. Se agrega con [+], con clic en la fila o con
 * Enter sobre la marcada, con las MISMAS reglas que el buscador de la venta (`onAgregar` es el `agregar`
 * de Vender y devuelve el problema si no se pudo). **Esc** vuelve a la venta.
 *
 * No muestra el costo: la pantalla de venta es del cajero.
 *
 * @param bloqueado    se ve pero no se agrega (RF-008)
 * @param porQueNo     por qué no se puede agregar, para decirlo
 */
export default function Catalogo({ onAgregar, onCerrar, bloqueado = false, porQueNo = null }) {
  const [escrito, setEscrito] = useState('')
  const [texto, setTexto] = useState('')
  const [elegida, setElegida] = useState(TODAS)
  const [intento, setIntento] = useState(0)
  const [conteos, setConteos] = useState({ clave: null, datos: null, error: null })
  const [lista, setLista] = useState({ clave: null, datos: null, error: null, cargandoMas: false })
  const [resaltado, setResaltado] = useState({ clave: null, indice: -1 })
  const [mensaje, setMensaje] = useState(null)
  const campo = useRef(null)
  const filas = useRef(null)

  // Lo escrito se busca tras una pausa, no con cada letra.
  useEffect(() => {
    const espera = setTimeout(() => setTexto(escrito.trim()), 250)
    return () => clearTimeout(espera)
  }, [escrito])

  // ── Los números de las categorías, de lo buscado ────────────────────────────
  const claveConteos = JSON.stringify([texto, intento])
  useEffect(() => {
    let vigente = true
    inventarioApi.categorias(texto)
      .then((datos) => { if (vigente) setConteos({ clave: claveConteos, datos, error: null }) })
      .catch((error) => { if (vigente) setConteos((c) => ({ ...c, clave: claveConteos, error })) })
    return () => { vigente = false }
  }, [claveConteos, texto])

  // Se pintan los últimos que llegaron; para decidir la categoría vigente solo cuentan los de este texto.
  const chips = conteos.datos ? chipsDeCategorias(conteos.datos) : null
  const categoria = categoriaVigente(conteos.clave === claveConteos ? chips : null, elegida)

  // ── La lista, de a 50 ───────────────────────────────────────────────────────
  const claveLista = JSON.stringify([texto, categoria, intento])
  useEffect(() => {
    let vigente = true
    inventarioApi.listar(consultaDelCatalogo({ texto, categoria, pagina: 0 }))
      .then((pagina) => {
        if (vigente) setLista({ clave: claveLista, datos: juntarPaginas(null, pagina), error: null, cargandoMas: false })
      })
      // Si falla, se conserva lo anterior atenuado: mejor verlo con el aviso que una lista vacía.
      .catch((error) => { if (vigente) setLista((l) => ({ ...l, clave: claveLista, error, cargandoMas: false })) })
    return () => { vigente = false }
  }, [claveLista, texto, categoria])

  const cargando = lista.clave !== claveLista
  const elementos = lista.datos?.elementos ?? []
  const indice = resaltado.clave === claveLista ? Math.min(resaltado.indice, elementos.length - 1) : -1

  useEffect(() => {
    filas.current?.querySelector('[aria-selected="true"]')?.scrollIntoView({ block: 'nearest' })
  }, [indice])

  function verMas() {
    const clave = claveLista
    setLista((l) => ({ ...l, cargandoMas: true }))
    inventarioApi.listar(consultaDelCatalogo({ texto, categoria, pagina: lista.datos.siguiente }))
      .then((pagina) => setLista((l) => (l.clave === clave
        ? { ...l, datos: juntarPaginas(l.datos, pagina), error: null, cargandoMas: false } : l)))
      .catch((error) => setLista((l) => (l.clave === clave ? { ...l, error, cargandoMas: false } : l)))
  }

  function agregar(repuesto) {
    campo.current?.focus()
    if (bloqueado) {
      setMensaje({ tipo: 'problema', texto: porQueNo ?? 'Ahora no se puede agregar a la venta.' })
      return
    }
    const problema = onAgregar(repuesto)
    setMensaje(problema
      ? { tipo: 'problema', texto: problema }
      : { tipo: 'agregado', texto: `Agregado a la venta: ${repuesto.nombre} ${repuesto.marca}` })
  }

  function alTeclear(e) {
    if (e.key === 'Escape') {
      e.preventDefault()
      onCerrar()
    } else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      setResaltado({ clave: claveLista, indice: moverResaltado(indice, elementos.length, e.key) })
    } else if (e.key === 'Enter' && indice >= 0) {
      e.preventDefault()
      agregar(elementos[indice])
    }
  }

  // Tocar un botón no le quita el cursor al buscador: el teclado sigue funcionando.
  const sinQuitarFoco = (e) => e.preventDefault()

  return (
    <section className={estilos.catalogo} aria-label="Catálogo" onKeyDown={alTeclear}>
      <div className={estilos.cabecera}>
        <h2 className={estilos.titulo}>Catálogo</h2>
        <span className={estilos.ayuda}>↑↓ elegir · Enter agrega · Esc vuelve</span>
        <Boton variante="secundario" tamano="chico" onClick={onCerrar}>Volver a la venta</Boton>
      </div>

      <input
        ref={campo}
        className={estilos.campo}
        value={escrito}
        onChange={(e) => { setEscrito(e.target.value); setMensaje(null) }}
        placeholder="Buscar en el catálogo: código, nombre, marca o moto"
        aria-label="Buscar en el catálogo"
        autoComplete="off"
        spellCheck="false"
        // Se abre para buscar: el cursor ya está aquí.
        autoFocus
      />

      {chips && (
        <div className={`${estilos.categorias} ${conteos.clave !== claveConteos ? estilos.atenuado : ''}`}
          role="group" aria-label="Categorías">
          {chips.map((c) => (
            <button key={c.clave} type="button" aria-pressed={c.clave === categoria}
              className={c.clave === categoria ? estilos.categoriaActiva : estilos.categoria}
              onMouseDown={sinQuitarFoco} onClick={() => { setElegida(c.clave); setMensaje(null) }}>
              {c.nombre} <span className={estilos.cuenta}>{c.repuestos}</span>
            </button>
          ))}
        </div>
      )}

      {bloqueado && porQueNo && <p className={estilos.nota}>{porQueNo}</p>}
      {mensaje && (
        <p className={mensaje.tipo === 'problema' ? estilos.problema : estilos.agregado} role="status">{mensaje.texto}</p>
      )}
      <AvisoCarga error={cargando ? null : (lista.error ?? conteos.error)} onReintentar={() => setIntento((n) => n + 1)}
        desactualizado={lista.datos != null} />

      {!lista.datos && cargando && <p className={estilos.vacio}>Cargando catálogo…</p>}

      {lista.datos && elementos.length === 0 && !cargando && (
        <p className={estilos.vacio}>{texto ? `No hay repuestos con «${texto}».` : 'Todavía no hay repuestos.'}</p>
      )}

      {elementos.length > 0 && (
        <ul ref={filas} className={`${estilos.lista} ${cargando ? estilos.atenuado : ''}`} role="listbox"
          aria-label="Repuestos del catálogo" aria-busy={cargando}>
          {elementos.map((r, i) => {
            const agregable = sePuedeAgregar(r, bloqueado)
            return (
              <li key={r.id} role="option" aria-selected={i === indice}
                className={`${estilos.fila} ${r.stock > 0 ? '' : estilos.agotado} ${i === indice ? estilos.resaltada : ''}`}
                onMouseDown={sinQuitarFoco} onClick={() => agregar(r)}>
                <span className={estilos.repuesto}>
                  <span className={estilos.nombre}>{r.nombre} <span className={estilos.marca}>{r.marca}</span></span>
                  <span className={estilos.detalle}>
                    <span className={estilos.codigo}>{r.codigo}</span>
                    {r.aplicacion && <span className={estilos.aplicacion}> · {r.aplicacion}</span>}
                  </span>
                </span>
                <span className={r.stock > 0 ? estilos.stock : estilos.sinStock}>
                  {r.stock > 0 ? `hay ${r.stock}` : 'sin stock'}
                </span>
                <span className={estilos.precio}>{Number(r.precio) > 0 ? formatoCOP(r.precio) : 'sin precio'}</span>
                <button type="button" className={estilos.agregar} disabled={!agregable}
                  aria-label={`Agregar ${r.codigo} a la venta`} title={agregable ? 'Agregar a la venta' : undefined}
                  onMouseDown={sinQuitarFoco} onClick={(e) => { e.stopPropagation(); agregar(r) }}>
                  +
                </button>
              </li>
            )
          })}
        </ul>
      )}

      {lista.datos?.hayMas && (
        <div className={estilos.mas}>
          <span className={estilos.rango}>{elementos.length} de {lista.datos.total}</span>
          <Boton variante="secundario" onMouseDown={sinQuitarFoco} onClick={verMas} disabled={lista.cargandoMas}>
            {lista.cargandoMas ? 'Cargando…' : 'Ver más'}
          </Boton>
        </div>
      )}
    </section>
  )
}
