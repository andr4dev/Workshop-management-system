import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import AvisoCarga from '../componentes/AvisoCarga'
import { comprasApi } from '../api/cliente'
import Compra from './Compra'
import comun from './Listado.module.css'

/**
 * Corregir una compra (spec 0002, RF-014): la carga y abre la pantalla de registrar con ella.
 *
 * La pantalla de compra se monta **solo cuando la compra ya llegó**, con la compra como punto de
 * partida. Así arranca con los datos correctos desde el primer render, sin un efecto que rellene el
 * formulario después — y sin el parpadeo de un formulario vacío.
 */
export default function CorregirCompra() {
  const { id } = useParams()
  const [intento, setIntento] = useState(0)
  const clave = `${id}|${intento}`
  const [estado, setEstado] = useState({ clave: null, compra: null, error: null })

  useEffect(() => {
    let vigente = true
    comprasApi.detalle(id)
      .then((compra) => { if (vigente) setEstado({ clave, compra, error: null }) })
      .catch((error) => { if (vigente) setEstado({ clave, compra: null, error }) })
    return () => { vigente = false }
  }, [id, clave])

  const volver = <Link to={`/compras/historial/${id}`} className={comun.volver}>← Volver a la compra</Link>

  if (estado.clave !== clave) {
    return <div className={comun.pagina}>{volver}<p className={comun.vacio}>Cargando compra…</p></div>
  }

  if (estado.error) {
    return (
      <div className={comun.pagina}>
        {volver}
        {estado.error.estado === 404
          ? <p className={comun.vacio}>Esa compra no existe.</p>
          : <AvisoCarga error={estado.error} onReintentar={() => setIntento((n) => n + 1)} />}
      </div>
    )
  }

  if (estado.compra.estado === 'ANULADA') {
    return (
      <div className={comun.pagina}>
        {volver}
        <p className={comun.vacio}>Esta compra está anulada: ya no se puede corregir.</p>
      </div>
    )
  }

  // La key: si se vuelve a cargar (otra versión), la pantalla arranca de cero con la nueva.
  return <Compra key={`${estado.compra.id}-${estado.compra.version}`} correccion={estado.compra} />
}
