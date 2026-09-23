import { useState } from 'react'
import Modal from './Modal'
import Boton from './Boton'
import Campo from './Campo'
import DesgloseCambios from './DesgloseCambios'
import estilos from './ModalMotivo.module.css'

/**
 * Confirmar una acción que queda en la auditoría: corregir o anular una compra.
 *
 * Muestra **qué va a cambiar** antes de pedir el motivo. El administrador firma algo que acaba de
 * leer, no un botón. Y el motivo es obligatorio: sin él, dentro de tres meses nadie sabe por qué
 * esa factura cambió.
 *
 * El padre lo monta solo al abrir: cada apertura arranca con el motivo en blanco.
 *
 * @param desglose  lo que devuelve `desgloseDeCambios`; sin él (anular), solo la descripción
 * @param peligro   para anular: el botón de confirmar es el destructivo
 * @param error     lo que respondió el backend; el modal queda abierto para no perder el motivo
 * @param placeholder un ejemplo de motivo de ESA acción: el de compras no sirve para anular una venta
 */
export default function ModalMotivo({
  titulo, descripcion, desglose, textoConfirmar, peligro = false, enviando = false, error,
  placeholder = 'Ej.: se digitaron 100 unidades y eran 10', onConfirmar, onCerrar,
}) {
  const [motivo, setMotivo] = useState('')
  const [faltaMotivo, setFaltaMotivo] = useState(false)

  function confirmar() {
    if (!motivo.trim()) {
      setFaltaMotivo(true)
      return
    }
    onConfirmar(motivo.trim())
  }

  return (
    <Modal
      abierto
      onCerrar={onCerrar}
      titulo={titulo}
      ancho={desglose ? 660 : 560}
      pie={
        <>
          <Boton variante="fantasma" onClick={onCerrar} disabled={enviando}>Cancelar</Boton>
          <Boton variante={peligro ? 'peligro' : 'primario'} onClick={confirmar} disabled={enviando}>
            {enviando ? 'Guardando…' : textoConfirmar}
          </Boton>
        </>
      }
    >
      {descripcion && <p className={estilos.descripcion}>{descripcion}</p>}

      {desglose && !desglose.vacio && (
        <div className={estilos.cambios}>
          <p className={estilos.cambiosTitulo}>Lo que cambia</p>
          <DesgloseCambios desglose={desglose} textoDespues="Queda" />
        </div>
      )}

      <Campo
        etiqueta="Motivo"
        requerido
        error={faltaMotivo ? 'Escribe el motivo: queda en el registro de auditoría' : null}
        ayuda="Queda guardado con la fecha y quién lo hizo"
      >
        <textarea
          className={estilos.motivo}
          value={motivo}
          maxLength={300}
          rows={3}
          placeholder={placeholder}
          onChange={(e) => { setMotivo(e.target.value); setFaltaMotivo(false) }}
        />
      </Campo>

      {error && (
        <p className={estilos.error} role="alert">
          <span aria-hidden>⚠</span> {error}
        </p>
      )}
    </Modal>
  )
}
