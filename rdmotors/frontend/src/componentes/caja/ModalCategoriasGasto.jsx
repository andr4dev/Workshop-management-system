import { useEffect, useState } from 'react'
import Modal from '../Modal'
import Boton from '../Boton'
import Campo from '../Campo'
import AvisoCarga from '../AvisoCarga'
import Segmento from './Segmento'
import { categoriasGastoApi } from '../../api/cliente'
import { NATURALEZAS } from '../../utils/gastos'
import estilos from './Caja.module.css'

/**
 * Las categorías de gasto (spec 0006, H11 y RF-002a): crear, renombrar y desactivar.
 *
 * Al crear se dice UNA vez si es costo o gasto, y no cambia después: los gastos ya registrados la heredan, y
 * cambiarla movería plata entre la utilidad bruta y la neta de meses ya reportados.
 *
 * No hay borrar: una categoría con gastos se desactiva y sus gastos la siguen nombrando.
 *
 * *Se paga cada mes* (spec 0007): al registrar un gasto de esa categoría, la casilla "del mes" sale marcada. Es
 * una sugerencia; cambiarla no toca los gastos ya registrados.
 *
 * @param onCambio avisa al padre para que vuelva a leer las categorías
 */
export default function ModalCategoriasGasto({ onCambio, onCerrar }) {
  const [intento, setIntento] = useState(0)
  const [carga, setCarga] = useState({ categorias: null, error: null })
  const [nueva, setNueva] = useState({ nombre: '', naturaleza: '', mensual: false })
  const [intentoCrear, setIntentoCrear] = useState(false)
  const [creando, setCreando] = useState(false)
  const [errorCrear, setErrorCrear] = useState(null)
  // La fila que se está editando: { id, modo: 'RENOMBRAR' | 'DESACTIVAR', nombre }
  const [editando, setEditando] = useState(null)
  const [enviando, setEnviando] = useState(false)
  const [errorFila, setErrorFila] = useState(null)

  useEffect(() => {
    let vigente = true
    categoriasGastoApi.todas()
      .then((categorias) => { if (vigente) setCarga({ categorias, error: null }) })
      .catch((error) => { if (vigente) setCarga((c) => ({ ...c, error })) })
    return () => { vigente = false }
  }, [intento])

  const faltaNombre = !nueva.nombre.trim() ? 'Escribe el nombre' : null
  const faltaNaturaleza = !nueva.naturaleza ? 'Di si es un costo o un gasto' : null

  function actualizar(categoria) {
    setCarga((c) => ({ ...c, categorias: c.categorias.map((x) => (x.id === categoria.id ? categoria : x)) }))
    onCambio()
  }

  async function crear() {
    setIntentoCrear(true)
    if (faltaNombre || faltaNaturaleza) return
    setCreando(true)
    setErrorCrear(null)
    try {
      const creada = await categoriasGastoApi.crear({
        nombre: nueva.nombre.trim(), naturaleza: nueva.naturaleza, mensual: nueva.mensual,
      })
      setCarga((c) => ({ ...c, categorias: [...(c.categorias ?? []), creada] }))
      setNueva({ nombre: '', naturaleza: '', mensual: false })
      setIntentoCrear(false)
      onCambio()
    } catch (e) {
      setErrorCrear(e.message)
    } finally {
      setCreando(false)
    }
  }

  async function guardarFila() {
    setEnviando(true)
    setErrorFila(null)
    try {
      actualizar(editando.modo === 'RENOMBRAR'
        ? await categoriasGastoApi.actualizar(editando.id, { nombre: editando.nombre.trim(), mensual: editando.mensual })
        : await categoriasGastoApi.desactivar(editando.id))
      setEditando(null)
    } catch (e) {
      setErrorFila({ id: editando.id, mensaje: e.message })
    } finally {
      setEnviando(false)
    }
  }

  /** El interruptor de cada fila: reemplaza "se paga cada mes" dejando el nombre como está. */
  async function cambiarMensual(categoria) {
    setEnviando(true)
    setErrorFila(null)
    try {
      actualizar(await categoriasGastoApi.actualizar(categoria.id, { nombre: categoria.nombre, mensual: !categoria.mensual }))
    } catch (e) {
      setErrorFila({ id: categoria.id, mensaje: e.message })
    } finally {
      setEnviando(false)
    }
  }

  const ordenadas = [...(carga.categorias ?? [])].sort((a, b) =>
    Number(b.activa) - Number(a.activa) || a.nombre.localeCompare(b.nombre, 'es'))

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Categorías de gasto" ancho={620}
      pie={<Boton variante="secundario" onClick={onCerrar}>Cerrar</Boton>}>
      <div className={estilos.nueva}>
        <Campo etiqueta="Nueva categoría" value={nueva.nombre} maxLength={80} placeholder="Publicidad"
          autoComplete="off" error={intentoCrear ? faltaNombre ?? errorCrear : errorCrear}
          onChange={(e) => { setNueva((n) => ({ ...n, nombre: e.target.value })); setErrorCrear(null) }} />
        <Campo etiqueta="Es un…" error={intentoCrear ? faltaNaturaleza : null}>
          <Segmento etiqueta="Costo o gasto" valor={nueva.naturaleza}
            opciones={[['GASTO', 'Gasto'], ['COSTO', 'Costo']]}
            onCambio={(naturaleza) => setNueva((n) => ({ ...n, naturaleza }))} />
        </Campo>
        <Boton variante="primario" onClick={crear} disabled={creando}>{creando ? 'Creando…' : 'Crear'}</Boton>
        <label className={estilos.casilla}>
          <input type="checkbox" checked={nueva.mensual}
            onChange={(e) => setNueva((n) => ({ ...n, mensual: e.target.checked }))} />
          Se paga cada mes (arriendo, nómina)
        </label>
      </div>
      <p className={estilos.nota} style={{ marginBottom: 'var(--esp-3)' }}>
        <strong>Gasto</strong>: lo que cuesta tener la tienda abierta (arriendo, luz, almuerzo). <strong>Costo</strong>:
        lo que se le suma a la mercancía por fuera de la factura. Se decide al crearla y no cambia.
      </p>

      <AvisoCarga error={carga.error} onReintentar={() => setIntento((n) => n + 1)} />
      {!carga.categorias && !carga.error && <p className={estilos.vacio}>Cargando categorías…</p>}

      {carga.categorias && (
        <ul className={estilos.lista}>
          {ordenadas.map((c) => (
            <li key={c.id} className={estilos.fila}>
              {editando?.id === c.id && editando.modo === 'RENOMBRAR' ? (
                <span className={estilos.renombrar}>
                  <input className={estilos.select} value={editando.nombre} maxLength={80} aria-label="Nuevo nombre"
                    onChange={(e) => setEditando((ed) => ({ ...ed, nombre: e.target.value }))}
                    onKeyDown={(e) => e.key === 'Enter' && guardarFila()} />
                  <Boton variante="fantasma" tamano="chico" disabled={enviando} onClick={() => setEditando(null)}>Cancelar</Boton>
                  <Boton variante="primario" tamano="chico" disabled={enviando || !editando.nombre.trim()} onClick={guardarFila}>
                    {enviando ? 'Guardando…' : 'Guardar'}
                  </Boton>
                </span>
              ) : editando?.id === c.id ? (
                <>
                  <span>¿Desactivar <strong>{c.nombre}</strong>? Deja de ofrecerse; sus gastos la siguen nombrando.</span>
                  <span className={estilos.acciones}>
                    <Boton variante="fantasma" tamano="chico" disabled={enviando} onClick={() => setEditando(null)}>No</Boton>
                    <Boton variante="peligro" tamano="chico" disabled={enviando} onClick={guardarFila}>
                      {enviando ? 'Desactivando…' : 'Sí, desactivar'}
                    </Boton>
                  </span>
                </>
              ) : (
                <>
                  <span className={`${estilos.nombre} ${c.activa ? '' : estilos.desactivada}`}>
                    {c.nombre}
                    <span className={c.naturaleza === 'COSTO' ? estilos.etiquetaCosto : estilos.etiqueta}>
                      {NATURALEZAS[c.naturaleza]}
                    </span>
                    {!c.activa && <span className={estilos.etiqueta}>Desactivada</span>}
                  </span>
                  {c.activa && (
                    <span className={estilos.acciones}>
                      <label className={estilos.casilla} title="Al registrar un gasto de esta categoría, sale marcado como del mes">
                        <input type="checkbox" checked={c.mensual} disabled={enviando || editando != null}
                          onChange={() => cambiarMensual(c)} />
                        Cada mes
                      </label>
                      <Boton variante="fantasma" tamano="chico" disabled={editando != null}
                        onClick={() => { setEditando({ id: c.id, modo: 'RENOMBRAR', nombre: c.nombre, mensual: c.mensual }); setErrorFila(null) }}>
                        Renombrar
                      </Boton>
                      <Boton variante="secundario" tamano="chico" disabled={editando != null}
                        onClick={() => { setEditando({ id: c.id, modo: 'DESACTIVAR' }); setErrorFila(null) }}>
                        Desactivar
                      </Boton>
                    </span>
                  )}
                </>
              )}
              {errorFila?.id === c.id && <p className={estilos.errorFila} role="alert">{errorFila.mensaje}</p>}
            </li>
          ))}
        </ul>
      )}
    </Modal>
  )
}
