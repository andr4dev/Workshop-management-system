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
 * precio y cuántos hay; los agotados al final y apagados. Se agrega con [+] o con Enter sobre la marcada, con las
 * MISMAS reglas que el buscador de la venta (`onAgregar` es el `agregar` de Vender y devuelve el problema si no se
 * pudo). Tocar la fila solo la marca: un toque de más, sobre todo en el celular, no mete nada a la venta.
 * **Esc** vuelve a la venta.
 *
 * No muestra el costo: la pantalla de venta es del cajero.
 *
 * Un aceite que paga comisión por cambio (spec 0015) se pregunta en su misma fila antes de agregarlo: desde aquí no se
 * ven los renglones de la venta, y sin escoger no se cobra. Un toque dice a la vez que sí y quién lo cambió.
 *
 * @param bloqueado     se ve pero no se agrega (RF-008)
 * @param porQueNo      por qué no se puede agregar, para decirlo
 * @param pideCambio    si a ese repuesto hay que preguntarle si se cambia aquí
 * @param personas      a quién se le puede anotar el cambio
 * @param quienRegistra el que está vendiendo: va primero, y es a quien se le anota si no hay más
 * @param yaEscogido    lo escogido en ese aceite si ya está en la venta: una unidad más sigue igual, y se dice
 */
export default function Catalogo({
  onAgregar, onCerrar, bloqueado = false, porQueNo = null, pideCambio = () => false, personas = [], quienRegistra = null,
  yaEscogido = () => '',
}) {
  const [escrito, setEscrito] = useState('')
  const [texto, setTexto] = useState('')
  const [elegida, setElegida] = useState(TODAS)
  const [intento, setIntento] = useState(0)
  const [conteos, setConteos] = useState({ clave: null, datos: null, error: null })
  const [lista, setLista] = useState({ clave: null, datos: null, error: null, cargandoMas: false })
  const [resaltado, setResaltado] = useState({ clave: null, indice: -1 })
  const [mensaje, setMensaje] = useState(null)
  const [preguntando, setPreguntando] = useState(null)
  const campo = useRef(null)
  const filas = useRef(null)
  const primeraOpcion = useRef(null)

  // El que está vendiendo primero: casi siempre es quien cambia el aceite.
  const quienes = [...(personas.length > 0 ? personas : [quienRegistra].filter(Boolean))]
    .sort((a, b) => (b.id === quienRegistra?.id) - (a.id === quienRegistra?.id))

  // Al preguntar, el cursor pasa a la primera respuesta: Enter la escoge, Tab pasa a las otras, Esc no agrega.
  useEffect(() => {
    if (preguntando) primeraOpcion.current?.focus()
  }, [preguntando])

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

  /** @param eleccion si se cambia aquí y quién, cuando se contestó la pregunta del cambio de aceite */
  function agregar(repuesto, eleccion = null) {
    if (!bloqueado && !eleccion && pideCambio(repuesto)) {
      setPreguntando(repuesto.id)
      setMensaje(null)
      return
    }
    setPreguntando(null)
    campo.current?.focus()
    if (bloqueado) {
      setMensaje({ tipo: 'problema', texto: porQueNo ?? 'Ahora no se puede agregar a la venta.' })
      return
    }
    // Antes de agregar: después, el renglón ya cambió.
    const previo = eleccion ? '' : yaEscogido(repuesto)
    const problema = onAgregar(repuesto, eleccion)
    setMensaje(problema
      ? { tipo: 'problema', texto: problema }
      : { tipo: 'agregado', texto: `Agregado a la venta: ${repuesto.nombre} ${repuesto.marca}${textoDeLaEleccion(repuesto, eleccion, previo)}` })
  }

  function textoDeLaEleccion(repuesto, eleccion, previo) {
    if (previo) return `. Otra unidad, igual que la que ya estaba: ${previo}`
    if (!eleccion) return ''
    if (eleccion.cambio === 'NO_SE_CAMBIA') return `, sin cambio (${formatoCOP(sinComision(repuesto))})`
    return `, lo cambia ${quienes.find((p) => p.id === eleccion.cambioPorId)?.nombre ?? 'quien se escogió'}`
  }

  function cancelarPregunta() {
    setPreguntando(null)
    campo.current?.focus()
  }

  function alTeclear(e) {
    if (preguntando && e.key === 'Escape') {
      e.preventDefault()
      cancelarPregunta()
      return
    }
    // Mientras se pregunta, Enter y Tab son de las respuestas; en el buscador, el teclado sigue como siempre.
    if (preguntando && e.target !== campo.current) return
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
        onChange={(e) => { setEscrito(e.target.value); setMensaje(null); setPreguntando(null) }}
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
              onMouseDown={sinQuitarFoco} onClick={() => { setElegida(c.clave); setMensaje(null); setPreguntando(null) }}>
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
                onMouseDown={sinQuitarFoco} onClick={() => setResaltado({ clave: claveLista, indice: i })}>
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
                {preguntando === r.id && (
                  // Lo de adentro no es la fila: ni la agrega al tocarlo ni le quita el clic a sus botones.
                  <div className={estilos.pregunta} role="group" aria-label={`¿Se le cambia el aceite aquí? ${r.nombre}`}
                    onMouseDown={(e) => e.stopPropagation()} onClick={(e) => e.stopPropagation()}>
                    <span className={estilos.preguntaTexto}>¿Se cambia aquí?</span>
                    <span className={estilos.respuestas}>
                      <span className={estilos.respuestaTexto}>Sí, a {formatoCOP(r.precio)}. Lo cambia:</span>
                      {quienes.map((p, j) => (
                        <button key={p.id} ref={j === 0 ? primeraOpcion : undefined} type="button"
                          className={estilos.respuesta}
                          onClick={() => agregar(r, { cambio: 'SE_CAMBIA', cambioPorId: p.id })}>
                          {p.nombre}
                        </button>
                      ))}
                    </span>
                    <span className={estilos.respuestas}>
                      <button type="button" className={estilos.respuesta}
                        onClick={() => agregar(r, { cambio: 'NO_SE_CAMBIA' })}>
                        No se cambia, a {formatoCOP(sinComision(r))}
                      </button>
                      <button type="button" className={estilos.cancelar} onClick={cancelarPregunta}>Cancelar</button>
                    </span>
                  </div>
                )}
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

/** El precio si el aceite no se cambia aquí: sin la comisión ($65.000 → $62.000). */
const sinComision = (r) => Number(r.precio) - Number(r.comisionCambio)
