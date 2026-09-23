import Modal from '../Modal'
import ComprobanteVenta from './ComprobanteVenta'

/** El comprobante de una venta en un modal: para verlo y reimprimirlo sin salir del mostrador. */
export default function ModalTicket({ venta, onCerrar }) {
  return (
    <Modal abierto onCerrar={onCerrar} titulo={`Comprobante N.º ${venta.numero}`} ancho={440}>
      <ComprobanteVenta venta={venta} />
    </Modal>
  )
}
