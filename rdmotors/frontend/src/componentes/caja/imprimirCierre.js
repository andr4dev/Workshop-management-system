import { tiendaApi } from '../../api/cliente'
import { imprimirHtml } from '../../utils/imprimir'
import {
  armarComprobanteCierre, htmlDelComprobanteCierre, problemasDelComprobanteCierre,
} from '../../utils/comprobanteCierre'

/**
 * Manda el comprobante de un turno cerrado a la impresora (spec 0006, RF-019). NUNCA lanza: el turno ya está
 * cerrado y un problema de impresión no puede deshacerlo ni romper la pantalla. Devuelve si se pudo mandar.
 *
 * Un comprobante cuyas partes no cuadran no sale solo por la impresora: se ve en rojo en su detalle.
 */
export async function imprimirCierre(turno) {
  try {
    const comprobante = armarComprobanteCierre(turno, await tiendaApi.obtener())
    if (problemasDelComprobanteCierre(comprobante).length > 0) return false
    return await imprimirHtml(htmlDelComprobanteCierre(comprobante))
  } catch {
    return false
  }
}
