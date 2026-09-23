import { useState } from 'react'
import Modal from '../Modal'
import Boton from '../Boton'
import Campo from '../Campo'
import { proveedoresApi } from '../../api/cliente'

/**
 * Dar de alta un proveedor sin salirse de la compra.
 *
 * <p>El padre lo monta solo cuando se abre, así que cada apertura es una instancia nueva y no
 * hace falta un efecto que limpie el formulario. Derivar en vez de sincronizar.
 *
 * Llega la factura de un importador nuevo y el administrador la tiene enfrente: obligarlo a
 * abandonar lo que está capturando para irse a otra pantalla es perder lo escrito.
 *
 * Solo el nombre es obligatorio. Exigir NIT y teléfono para registrar una compra que YA ocurrió
 * sería pedir datos que quizá no tiene a mano, para bloquear algo que de todos modos pasó.
 */
export default function ModalProveedorNuevo({ abierto, onCerrar, onCreado }) {
  const [nombre, setNombre] = useState('')
  const [nit, setNit] = useState('')
  const [telefono, setTelefono] = useState('')
  const [error, setError] = useState(null)
  const [guardando, setGuardando] = useState(false)

  async function guardar() {
    if (!nombre.trim()) {
      setError('El nombre es obligatorio')
      return
    }
    setGuardando(true)
    setError(null)
    try {
      const creado = await proveedoresApi.crear({
        nombre: nombre.trim(),
        nit: nit.trim() || null,
        telefono: telefono.trim() || null,
      })
      onCreado(creado)
    } catch (e) {
      setError(e.message)
      setGuardando(false)
    }
  }

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      titulo="Proveedor nuevo"
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
        etiqueta="Nombre"
        requerido
        value={nombre}
        onChange={(e) => setNombre(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && guardar()}
        placeholder="Importadora Jotapartes"
        error={error}
        autoComplete="off"
      />

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginTop: 16 }}>
        <Campo
          etiqueta="NIT"
          ayuda="Opcional"
          value={nit}
          onChange={(e) => setNit(e.target.value)}
          placeholder="900123456-7"
          autoComplete="off"
        />
        <Campo
          etiqueta="Teléfono"
          ayuda="Opcional"
          value={telefono}
          onChange={(e) => setTelefono(e.target.value)}
          placeholder="3001234567"
          inputMode="tel"
          autoComplete="off"
        />
      </div>
    </Modal>
  )
}
