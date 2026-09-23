import { useState } from 'react'
import Modal from '../Modal'
import Boton from '../Boton'
import Campo from '../Campo'
import { clientesApi } from '../../api/cliente'
import { datosDelCliente, problemasDeDatos } from '../../utils/clientes'
import estilos from './ModalCliente.module.css'

/**
 * Crear un cliente o cambiar sus datos (spec 0008, RF-001 y RF-004).
 *
 * El cajero **completa** lo que falta: lo que ya está escrito le aparece sin poder cambiarlo, porque corregirlo es del
 * administrador (el servidor igual lo negaría). El administrador corrige todo, y queda en la auditoría.
 *
 * @param cliente       el que se edita, o `null` para uno nuevo
 * @param soloCompletar el cajero: lo escrito no se cambia
 * @param onRepetido    al crear, la cédula ya era de otro: recibe ese cliente
 */
export default function ModalCliente({ cliente, soloCompletar = false, onGuardado, onRepetido, onCerrar }) {
  const nuevo = !cliente
  const [datos, setDatos] = useState(() => datosDelCliente(cliente))
  const [intentado, setIntentado] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)
  const problemas = problemasDeDatos(datos)
  const visibles = intentado ? problemas : {}
  const bloqueado = (campo) => soloCompletar && Boolean(cliente?.[campo])

  const cambiar = (campo) => (e) => {
    setDatos((d) => ({ ...d, [campo]: e.target.value }))
    setError(null)
  }

  async function guardar() {
    setIntentado(true)
    if (Object.keys(problemas).length > 0) return
    setEnviando(true)
    setError(null)
    try {
      onGuardado(nuevo ? await clientesApi.crear(datos) : await clientesApi.actualizar(cliente.id, datos))
    } catch (e) {
      if (nuevo && e.esClienteRepetido && e.cuerpo.cliente && onRepetido) {
        onRepetido(e.cuerpo.cliente)
        return
      }
      setError(e)
    } finally {
      setEnviando(false)
    }
  }

  const alTeclear = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      guardar()
    }
  }

  const campo = (nombre, etiqueta, { ayuda, ...props } = {}) => (
    <Campo etiqueta={etiqueta} value={datos[nombre]} onChange={cambiar(nombre)} onKeyDown={alTeclear}
      error={visibles[nombre]} disabled={enviando || bloqueado(nombre)} autoComplete="off"
      ayuda={bloqueado(nombre) ? 'Corregirlo es del administrador' : ayuda} {...props} />
  )

  return (
    <Modal
      abierto
      onCerrar={onCerrar}
      titulo={nuevo ? 'Cliente nuevo' : `Datos de ${cliente.nombre}`}
      ancho={520}
      pie={
        <>
          <Boton variante="fantasma" onClick={onCerrar} disabled={enviando}>Cancelar</Boton>
          <Boton variante="primario" onClick={guardar} disabled={enviando}>
            {enviando ? 'Guardando…' : error?.estado === 0 ? 'Reintentar' : nuevo ? 'Crear cliente' : 'Guardar datos'}
          </Boton>
        </>
      }
    >
      {campo('nombre', 'Nombre', { requerido: true, autoFocus: nuevo })}
      {campo('documento', 'Cédula o NIT', { ayuda: 'Opcional, pero sin ella dos clientes con el mismo nombre se confunden' })}
      {campo('celular', 'Celular', { inputMode: 'tel', ayuda: 'Opcional, pero sin él no hay a quién llamarle a cobrar' })}
      {campo('direccion', 'Dirección')}
      {campo('nota', 'Nota', { placeholder: 'Ej.: el del taller de la 5' })}
      {error && (
        <p role="alert" className={error.estado === 0 ? estilos.sinConexion : estilos.error}>
          <span aria-hidden>⚠</span> {error.estado === 0 ? 'No hay conexión con el servidor' : error.message}
        </p>
      )}
    </Modal>
  )
}
