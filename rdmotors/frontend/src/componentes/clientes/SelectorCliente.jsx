import { useEffect, useState } from 'react'
import Boton from '../Boton'
import { clientesApi } from '../../api/cliente'
import {
  camposQueFaltan, clienteNuevoDesde, datosDelCliente, identificacion, loQueFaltaEnPalabras, problemasDeDatos,
  textoDeuda,
} from '../../utils/clientes'
import estilos from './SelectorCliente.module.css'

const ETIQUETAS = { nombre: 'Nombre', documento: 'Cédula o NIT', celular: 'Celular', direccion: 'Dirección' }
const MINIMO_PARA_BUSCAR = 2

/** "No hay conexión con el servidor" en ámbar; lo demás, tal cual lo dijo el servidor (skill frontend). */
const textoDeError = (error) => (error?.estado === 0 ? 'No hay conexión con el servidor' : error?.message)

/**
 * A quién se le fía, o a nombre de quién va una venta (spec 0008, RF-003), sin salir del cobro.
 *
 *   - Se busca por nombre, cédula o celular, sin tildes. Flechas y Enter escogen; Esc borra.
 *   - Si no existe, *Cliente nuevo* lo crea ahí mismo. Si la cédula ya era de alguien, se usa ese: la deuda de una
 *     persona no se parte en dos.
 *   - Escogido, se ve lo que debe. Para fiar, si le falta la cédula o el celular, se completan ahí mismo (el cajero
 *     puede completar; corregir lo escrito es del administrador).
 *
 * @param cliente   el escogido, como lo devuelve el servidor, o `null`
 * @param onCambiar recibe el cliente escogido (o `null` para escoger otro)
 * @param paraFiar  exige la cédula y el celular
 */
export default function SelectorCliente({ cliente, onCambiar, paraFiar = false, deshabilitado = false }) {
  const [texto, setTexto] = useState('')
  const [resultados, setResultados] = useState({ clave: null, elementos: [], error: null })
  const [resaltado, setResaltado] = useState(-1)
  const [nuevo, setNuevo] = useState(null)
  const [aviso, setAviso] = useState(null)

  const clave = texto.trim().length >= MINIMO_PARA_BUSCAR ? texto.trim() : null
  const visibles = resultados.clave === clave ? resultados.elementos : []
  const indice = resaltado < visibles.length ? resaltado : -1

  useEffect(() => {
    if (!clave || cliente) return
    let vigente = true
    const pausa = setTimeout(() => {
      clientesApi.buscar(clave)
        .then((lista) => { if (vigente) setResultados({ clave, elementos: lista, error: null }) })
        .catch((error) => { if (vigente) setResultados({ clave, elementos: [], error }) })
    }, 250)
    return () => { vigente = false; clearTimeout(pausa) }
  }, [clave, cliente])

  function escoger(escogido, avisoDe = null) {
    setAviso(avisoDe)
    setNuevo(null)
    setTexto('')
    setResaltado(-1)
    onCambiar(escogido)
  }

  function alTeclear(e) {
    if (e.key === 'Enter') {
      e.preventDefault()
      if (indice >= 0) escoger(visibles[indice])
      else if (visibles.length === 1) escoger(visibles[0])
      else if (visibles.length > 1) setResaltado(0)
      else if (clave && resultados.clave === clave) setNuevo(clienteNuevoDesde(texto))
    } else if (e.key === 'ArrowDown' && visibles.length) {
      e.preventDefault()
      setResaltado((indice + 1) % visibles.length)
    } else if (e.key === 'ArrowUp' && visibles.length) {
      e.preventDefault()
      setResaltado(indice <= 0 ? visibles.length - 1 : indice - 1)
    } else if (e.key === 'Escape' && texto) {
      e.preventDefault()
      e.stopPropagation()
      setTexto('')
      setResaltado(-1)
    }
  }

  if (cliente) {
    return (
      <div className={estilos.selector}>
        {aviso && <p className={estilos.aviso} role="status">{aviso}</p>}
        <div className={estilos.escogido}>
          <div className={estilos.escogidoDatos}>
            <strong>{cliente.nombre}</strong>
            {identificacion(cliente) && <span className={estilos.tenue}>{identificacion(cliente)}</span>}
          </div>
          <span className={cliente.debe > 0 ? estilos.debe : estilos.alDia}>{textoDeuda(cliente.debe)}</span>
          <Boton variante="fantasma" tamano="chico" disabled={deshabilitado}
            onClick={() => { setAviso(null); onCambiar(null) }}>
            Cambiar
          </Boton>
        </div>
        {paraFiar && cliente.fiadoCerrado && (
          <p className={estilos.alerta} role="alert">
            <span aria-hidden>⚠</span> A {cliente.nombre} no se le fía: lo cerró el administrador
            {cliente.motivoFiadoCerrado ? ` (${cliente.motivoFiadoCerrado})` : ''}. Puede pagar de contado.
          </p>
        )}
        {/* Le faltan datos: se ofrecen ahí mismo, pero la venta no se detiene por eso. */}
        {paraFiar && !cliente.fiadoCerrado && camposQueFaltan(cliente).length > 0 && (
          <CompletarDatos cliente={cliente} deshabilitado={deshabilitado}
            onGuardado={(actualizado) => { setAviso(null); onCambiar(actualizado) }}
            onUsarOtro={(otro, texto) => escoger(otro, texto)} />
        )}
      </div>
    )
  }

  if (nuevo) {
    return (
      <ClienteNuevo inicial={nuevo} paraFiar={paraFiar} deshabilitado={deshabilitado}
        onCreado={(creado, avisoDe) => escoger(creado, avisoDe)}
        onCancelar={() => setNuevo(null)} />
    )
  }

  return (
    <div className={estilos.selector}>
      <label className={estilos.etiqueta} htmlFor="buscar-cliente">{paraFiar ? 'A quién se le fía' : 'Cliente'}</label>
      <div className={estilos.buscarFila}>
        <input
          id="buscar-cliente"
          className={estilos.campo}
          value={texto}
          onChange={(e) => { setTexto(e.target.value); setResaltado(-1) }}
          onKeyDown={alTeclear}
          placeholder="Nombre, cédula o celular"
          autoComplete="off"
          spellCheck="false"
          autoFocus
          disabled={deshabilitado}
        />
        <Boton variante="secundario" disabled={deshabilitado} onClick={() => setNuevo(clienteNuevoDesde(texto))}>
          Cliente nuevo
        </Boton>
      </div>

      {resultados.error && resultados.clave === clave && (
        <p className={estilos.sinConexion} role="alert">{textoDeError(resultados.error)}</p>
      )}
      {clave && resultados.clave === clave && !resultados.error && visibles.length === 0 && (
        <p className={estilos.tenue}>Nadie coincide con «{clave}». Enter lo crea como cliente nuevo.</p>
      )}

      {visibles.length > 0 && (
        <ul className={estilos.lista} role="listbox" aria-label="Clientes que coinciden">
          {visibles.map((c, i) => (
            <li key={c.id}>
              <button
                type="button"
                role="option"
                aria-selected={i === indice}
                className={`${estilos.opcion} ${i === indice ? estilos.opcionResaltada : ''}`}
                // onMouseDown: el clic no le quita el foco al campo, y el teclado sigue.
                onMouseDown={(e) => { e.preventDefault(); escoger(c) }}
                tabIndex={-1}
              >
                <span className={estilos.opcionNombre}>
                  {c.nombre}
                  <span className={estilos.tenue}>{identificacion(c) || 'Sin cédula ni celular'}</span>
                </span>
                <span className={c.debe > 0 ? estilos.debe : estilos.alDia}>{textoDeuda(c.debe)}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/** Un cliente nuevo, sin salir del cobro. Con la cédula de otro, se usa ese. */
function ClienteNuevo({ inicial, paraFiar, deshabilitado, onCreado, onCancelar }) {
  const [datos, setDatos] = useState(inicial)
  const [intentado, setIntentado] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)
  const problemas = problemasDeDatos(datos)
  const visibles = intentado ? problemas : {}

  async function guardar() {
    setIntentado(true)
    if (Object.keys(problemas).length > 0) return
    setGuardando(true)
    setError(null)
    try {
      onCreado(await clientesApi.crear(datos))
    } catch (e) {
      if (e.esClienteRepetido && e.cuerpo.cliente) {
        onCreado(e.cuerpo.cliente, `${e.message}: se usa ese cliente, no se crea otro.`)
        return
      }
      setError(e)
    } finally {
      setGuardando(false)
    }
  }

  const campo = (nombre, { requerido = false, inputMode } = {}) => (
    <div className={estilos.dato}>
      <label className={estilos.etiqueta} htmlFor={`cliente-${nombre}`}>
        {ETIQUETAS[nombre]}{requerido && <span className={estilos.requerido} aria-hidden> *</span>}
      </label>
      <input id={`cliente-${nombre}`} className={`${estilos.campo} ${visibles[nombre] ? estilos.conError : ''}`}
        value={datos[nombre]} inputMode={inputMode} autoComplete="off" disabled={deshabilitado || guardando}
        autoFocus={nombre === (inicial.nombre ? 'documento' : 'nombre')}
        aria-invalid={visibles[nombre] ? 'true' : undefined}
        onChange={(e) => { setDatos((d) => ({ ...d, [nombre]: e.target.value })); setError(null) }}
        onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); guardar() } }} />
      {visibles[nombre] && <span className={estilos.errorCampo} role="alert"><span aria-hidden>⚠</span> {visibles[nombre]}</span>}
    </div>
  )

  return (
    <div className={estilos.selector}>
      <p className={estilos.etiqueta}>Cliente nuevo</p>
      <div className={estilos.formulario}>
        {campo('nombre', { requerido: true })}
        {campo('documento')}
        {campo('celular', { inputMode: 'tel' })}
        {campo('direccion')}
      </div>
      {/* Se piden, no se exigen (decisión 2, cambiada el 2026-09-21): con el cliente enfrente, frenar la venta por
          un dato que no trae encima era peor que fiar con lo que hay. */}
      {paraFiar && (
        <p className={estilos.tenue}>
          Solo el nombre es obligatorio. La cédula y el celular se pueden completar después, pero <strong>sin
          celular no hay a quién llamarle a cobrar</strong>.
        </p>
      )}
      {error && (
        <p className={error.estado === 0 ? estilos.sinConexion : estilos.alerta} role="alert">
          <span aria-hidden>⚠</span> {textoDeError(error)}
        </p>
      )}
      <div className={estilos.acciones}>
        <Boton variante="fantasma" onClick={onCancelar} disabled={guardando}>Buscar otro</Boton>
        <Boton variante="primario" onClick={guardar} disabled={deshabilitado || guardando}>
          {guardando ? 'Guardando…' : error?.estado === 0 ? 'Reintentar' : 'Guardar cliente'}
        </Boton>
      </div>
    </div>
  )
}

/**
 * Lo que le falta al cliente: la cédula, el celular o los dos. Se ofrece completarlo aquí mismo —es el momento en
 * que el cliente está enfrente— pero **no frena el cobro**: se puede dejar para después.
 */
function CompletarDatos({ cliente, deshabilitado, onGuardado, onUsarOtro }) {
  const faltan = camposQueFaltan(cliente)
  const [oculto, setOculto] = useState(false)
  const [datos, setDatos] = useState(() => datosDelCliente(cliente))
  const [intentado, setIntentado] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)
  const problemas = problemasDeDatos(datos)
  const visibles = intentado ? problemas : {}
  if (oculto) return null

  async function guardar() {
    setIntentado(true)
    if (Object.keys(problemas).length > 0) return
    setGuardando(true)
    setError(null)
    try {
      onGuardado(await clientesApi.actualizar(cliente.id, datos))
    } catch (e) {
      setError(e)
    } finally {
      setGuardando(false)
    }
  }

  return (
    <div className={estilos.completar}>
      <p className={estilos.recordatorio}>
        A {cliente.nombre} le {faltan.length === 1 ? 'falta' : 'faltan'} {loQueFaltaEnPalabras(cliente)}.
        Puedes anotarlo ahora o seguir con la venta.
      </p>
      <div className={estilos.formulario}>
        {faltan.map((nombre) => (
          <div key={nombre} className={estilos.dato}>
            <label className={estilos.etiqueta} htmlFor={`completar-${nombre}`}>{ETIQUETAS[nombre]}</label>
            <input id={`completar-${nombre}`} className={`${estilos.campo} ${visibles[nombre] ? estilos.conError : ''}`}
              value={datos[nombre]} inputMode={nombre === 'celular' ? 'tel' : undefined} autoComplete="off"
              autoFocus={nombre === faltan[0]} disabled={deshabilitado || guardando}
              aria-invalid={visibles[nombre] ? 'true' : undefined}
              onChange={(e) => { setDatos((d) => ({ ...d, [nombre]: e.target.value })); setError(null) }}
              onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); guardar() } }} />
            {visibles[nombre] && <span className={estilos.errorCampo} role="alert"><span aria-hidden>⚠</span> {visibles[nombre]}</span>}
          </div>
        ))}
      </div>
      {error && (
        <p className={error.estado === 0 ? estilos.sinConexion : estilos.alerta} role="alert">
          <span aria-hidden>⚠</span> {textoDeError(error)}
          {error.esClienteRepetido && error.cuerpo.cliente && (
            <Boton variante="secundario" tamano="chico" className={estilos.usarOtro}
              onClick={() => onUsarOtro(error.cuerpo.cliente, `Se usa a ${error.cuerpo.cliente.nombre}, que ya tenía esa cédula.`)}>
              Usar a {error.cuerpo.cliente.nombre}
            </Boton>
          )}
        </p>
      )}
      <div className={estilos.acciones}>
        <Boton variante="fantasma" onClick={() => setOculto(true)} disabled={guardando}>Después</Boton>
        <Boton variante="primario" onClick={guardar} disabled={deshabilitado || guardando}>
          {guardando ? 'Guardando…' : error?.estado === 0 ? 'Reintentar' : 'Guardar datos'}
        </Boton>
      </div>
    </div>
  )
}
