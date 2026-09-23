import { useState } from 'react'
import Modal from '../Modal'
import Boton from '../Boton'
import Campo from '../Campo'
import { clientesApi } from '../../api/cliente'
import { formatoCOP, soloDigitos } from '../../utils/formato'
import { hoyEnColombia } from '../../utils/periodo'
import estilos from './ModalCliente.module.css'

/**
 * Lo que el cliente ya debía en el cuaderno, antes del sistema (spec 0008, H10 y RF-028).
 *
 * Se carga **una sola vez por cliente**, con la fecha desde la que lo debe —así los abonos lo pagan primero, por ser
 * lo más viejo— y su motivo, que queda en la auditoría. Es del administrador.
 */
export default function ModalSaldoCuaderno({ cliente, onCargado, onCerrar }) {
  const hoy = hoyEnColombia()
  const [monto, setMonto] = useState('')
  const [fecha, setFecha] = useState(hoy)
  const [motivo, setMotivo] = useState('')
  const [intentado, setIntentado] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)

  const valor = Number(soloDigitos(monto)) || 0
  const problemas = {
    monto: valor <= 0 ? 'Escribe cuánto debía' : null,
    fecha: !fecha ? 'Escribe desde cuándo lo debe' : fecha > hoy ? 'La fecha no puede ser después de hoy' : null,
    motivo: !motivo.trim() ? 'Escribe de dónde sale esa cifra: queda en el registro de auditoría' : null,
  }
  const visibles = intentado ? problemas : {}

  async function cargar() {
    setIntentado(true)
    if (Object.values(problemas).some(Boolean)) return
    setEnviando(true)
    setError(null)
    try {
      onCargado(await clientesApi.saldoDelCuaderno(cliente.id, { monto: valor, fecha, motivo: motivo.trim() }))
    } catch (e) {
      setError(e)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <Modal
      abierto
      onCerrar={onCerrar}
      titulo={`Saldo del cuaderno de ${cliente.nombre}`}
      ancho={520}
      pie={
        <>
          <Boton variante="fantasma" onClick={onCerrar} disabled={enviando}>Cancelar</Boton>
          <Boton variante="primario" onClick={cargar} disabled={enviando}>
            {enviando ? 'Cargando…' : `Cargar ${formatoCOP(valor)}`}
          </Boton>
        </>
      }
    >
      <p>
        Lo que <strong>{cliente.nombre}</strong> ya debía antes de usar el sistema. Se carga una sola vez y los abonos
        lo pagan primero, por ser lo más viejo.
      </p>
      <Campo etiqueta="Cuánto debía" requerido inputMode="numeric" value={monto} error={visibles.monto}
        onChange={(e) => { setMonto(soloDigitos(e.target.value)); setError(null) }} disabled={enviando} autoFocus />
      <Campo etiqueta="Desde cuándo" requerido type="date" value={fecha} max={hoy} error={visibles.fecha}
        onChange={(e) => { setFecha(e.target.value); setError(null) }} disabled={enviando} />
      <Campo etiqueta="De dónde sale" requerido value={motivo} error={visibles.motivo}
        ayuda="Queda con la fecha y quién lo hizo" placeholder="Ej.: lo que tenía anotado en el cuaderno"
        onChange={(e) => { setMotivo(e.target.value); setError(null) }} disabled={enviando} />
      {error && (
        <p role="alert" className={error.estado === 0 ? estilos.sinConexion : estilos.error}>
          <span aria-hidden>⚠</span> {error.estado === 0 ? 'No hay conexión con el servidor' : error.message}
        </p>
      )}
    </Modal>
  )
}
