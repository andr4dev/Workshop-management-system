import { useState } from 'react'
import Modal from '../Modal'
import Boton from '../Boton'
import Campo from '../Campo'
import { cuentasApi } from '../../api/cliente'

/**
 * Crear la cuenta desde la que se transfiere, sin salirse de la compra.
 *
 * Mismo patrón que el proveedor nuevo: el padre lo monta solo al abrir, así que cada apertura
 * arranca limpia sin efectos.
 *
 * Se pide un nombre para RECONOCERLA, no el número. Para el reporte basta "Bancolombia ···4521",
 * y un número de cuenta completo no tiene por qué quedar guardado en el sistema.
 */
export default function ModalCuentaNueva({ abierto, onCerrar, onCreada }) {
  const [nombre, setNombre] = useState('')
  const [error, setError] = useState(null)
  const [guardando, setGuardando] = useState(false)

  async function guardar() {
    if (!nombre.trim()) {
      setError('Escribe un nombre para reconocerla')
      return
    }
    setGuardando(true)
    setError(null)
    try {
      onCreada(await cuentasApi.crear({ nombre: nombre.trim() }))
    } catch (e) {
      // Si ya existe, el mensaje dice cuál: se elige en la lista en vez de crearla otra vez.
      setError(e.message)
      setGuardando(false)
    }
  }

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      titulo="Cuenta nueva"
      ancho={460}
      pie={
        <>
          <Boton variante="fantasma" onClick={onCerrar}>Cancelar</Boton>
          <Boton variante="primario" onClick={guardar} disabled={guardando}>
            {guardando ? 'Guardando…' : 'Crear y usar'}
          </Boton>
        </>
      }
    >
      <Campo
        etiqueta="Nombre de la cuenta"
        requerido
        value={nombre}
        onChange={(e) => { setNombre(e.target.value); setError(null) }}
        onKeyDown={(e) => e.key === 'Enter' && guardar()}
        placeholder="Nequi del dueño"
        ayuda="Un nombre para reconocerla, como «Bancolombia ···4521». No escribas el número completo."
        error={error}
        maxLength={80}
        autoComplete="off"
      />
    </Modal>
  )
}
