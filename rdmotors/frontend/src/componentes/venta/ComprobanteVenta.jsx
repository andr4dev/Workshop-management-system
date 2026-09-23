import { useEffect, useMemo, useState } from 'react'
import AvisoCarga from '../AvisoCarga'
import Boton from '../Boton'
import Ticket from './Ticket'
import { tiendaApi } from '../../api/cliente'
import { esteEquipoEsElMostrador, imprimirHtml } from '../../utils/imprimir'
import { armarTicket, htmlDelTicket, problemasDelTicket } from '../../utils/ticket'
import estilos from './ComprobanteVenta.module.css'

/**
 * El comprobante de una venta, para ver e imprimir (spec 0003, RF-022). Trae los datos de la tienda al
 * abrirse: si alguien los cambió, sale con los de hoy. Las cifras son las de la venta guardada.
 */
export default function ComprobanteVenta({ venta }) {
  const [intento, setIntento] = useState(0)
  const [carga, setCarga] = useState({ intento: null, tienda: null, error: null })
  const [impresion, setImpresion] = useState(null)

  useEffect(() => {
    let vigente = true
    tiendaApi.obtener()
      .then((tienda) => { if (vigente) setCarga({ intento, tienda, error: null }) })
      .catch((error) => { if (vigente) setCarga({ intento, tienda: null, error }) })
    return () => { vigente = false }
  }, [intento])

  const cargando = carga.intento !== intento
  const ticket = useMemo(() => (carga.tienda ? armarTicket(venta, carga.tienda) : null), [venta, carga.tienda])
  const problemas = ticket ? problemasDelTicket(ticket) : []
  const html = useMemo(() => (ticket ? htmlDelTicket(ticket) : null), [ticket])

  async function imprimir() {
    setImpresion('ENVIANDO')
    setImpresion(await imprimirHtml(html) ? 'ENVIADO' : 'FALLO')
  }

  return (
    <section className={estilos.comprobante} aria-label={`Comprobante de la venta N.º ${venta.numero}`}>
      <div className={estilos.barra}>
        <Boton variante="primario" onClick={imprimir} disabled={!ticket || impresion === 'ENVIANDO'}>
          {impresion === 'ENVIANDO' ? 'Imprimiendo…' : 'Imprimir'}
        </Boton>
        {impresion === 'ENVIADO' && <span className={estilos.enviado} role="status">Enviado a la impresora</span>}
        {impresion === 'FALLO' && (
          <span className={estilos.fallo} role="alert"><span aria-hidden>⚠</span> No se pudo imprimir. Intenta de nuevo.</span>
        )}
      </div>

      {!esteEquipoEsElMostrador() && (
        <p className={estilos.nota}>
          La ticketera está en el computador del mostrador: desde este equipo, Imprimir abre su diálogo de impresión.
        </p>
      )}

      {problemas.length > 0 && (
        <div className={estilos.problemas} role="alert">
          <strong><span aria-hidden>⚠</span> Este comprobante no cuadra.</strong>
          <ul>{problemas.map((p) => <li key={p}>{p}</li>)}</ul>
        </div>
      )}

      {cargando && !ticket && <p className={estilos.nota}>Cargando comprobante…</p>}
      {!cargando && carga.error && <AvisoCarga error={carga.error} onReintentar={() => setIntento((n) => n + 1)} />}
      {html && <Ticket html={html} titulo={`Comprobante N.º ${venta.numero}`} />}
    </section>
  )
}
