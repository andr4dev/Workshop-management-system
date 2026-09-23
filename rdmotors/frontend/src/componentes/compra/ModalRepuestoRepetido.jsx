import Modal from '../Modal'
import Boton from '../Boton'

/**
 * Aviso de repuesto repetido en la misma compra.
 *
 * Un repuesto va una sola vez por compra: si fueran dos renglones podrían traer dos precios de
 * venta distintos y no habría forma de saber cuál vale. El backend lo rechaza igual; este aviso
 * lo frena en el momento de teclear el código, antes de que se capture nada en ese renglón.
 *
 * Cerrarlo de cualquier forma (botón, X, Escape) hace lo mismo: el padre limpia el renglón
 * repetido y lleva al renglón donde ya está el repuesto.
 */
export default function ModalRepuestoRepetido({ codigo, numero, original, onEntendido }) {
  const nombre = original?.repuesto?.nombre ?? original?.repuestoNuevo?.nombreConcepto
  const marca = original?.repuesto?.marca ?? original?.repuestoNuevo?.marcaRepuesto

  return (
    <Modal
      abierto
      onCerrar={onEntendido}
      titulo="Este repuesto ya está en la compra"
      ancho={460}
      pie={<Boton variante="primario" onClick={onEntendido}>Ir al renglón {numero}</Boton>}
    >
      <p style={{ margin: '0 0 12px' }}>
        El código <strong>{codigo}</strong> ya está en el renglón {numero}
        {nombre && <> ({nombre}{marca && ` · ${marca}`})</>}.
      </p>
      <p style={{ margin: 0, color: 'var(--text-muted)' }}>
        No se puede repetir en la misma compra. Si llegaron más unidades, súmalas en ese renglón.
      </p>
    </Modal>
  )
}
