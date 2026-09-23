import { useEffect, useMemo, useState } from 'react'
import AvisoCarga from '../AvisoCarga'
import Boton from '../Boton'
import Ticket from '../venta/Ticket'
import { tiendaApi } from '../../api/cliente'
import { esteEquipoEsElMostrador, imprimirHtml } from '../../utils/imprimir'
import {
  armarComprobanteCierre, htmlDelComprobanteCierre, problemasDelComprobanteCierre,
} from '../../utils/comprobanteCierre'
import estilos from '../venta/ComprobanteVenta.module.css'

/**
 * El comprobante de un cierre de caja, para ver y reimprimir (spec 0006, H6 y RF-020). Es el mismo documento
 * que sale por la ticketera. Las cifras son las guardadas al cerrar.
 */
export default function ComprobanteCierre({ turno }) {
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
  const comprobante = useMemo(() => (carga.tienda ? armarComprobanteCierre(turno, carga.tienda) : null),
    [turno, carga.tienda])
  const problemas = comprobante ? problemasDelComprobanteCierre(comprobante) : []
  const html = useMemo(() => (comprobante ? htmlDelComprobanteCierre(comprobante) : null), [comprobante])

  async function imprimir() {
    setImpresion('ENVIANDO')
    setImpresion(await imprimirHtml(html) ? 'ENVIADO' : 'FALLO')
  }

  return (
    <section className={estilos.comprobante} aria-label="Comprobante del cierre">
      <div className={estilos.barra}>
        <Boton variante="primario" onClick={imprimir} disabled={!html || impresion === 'ENVIANDO'}>
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

      {cargando && !comprobante && <p className={estilos.nota}>Cargando comprobante…</p>}
      {!cargando && carga.error && <AvisoCarga error={carga.error} onReintentar={() => setIntento((n) => n + 1)} />}
      {html && <Ticket html={html} titulo="Comprobante del cierre de caja" />}
    </section>
  )
}
