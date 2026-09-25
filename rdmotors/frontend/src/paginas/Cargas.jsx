import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import Boton from '../componentes/Boton'
import AvisoCarga from '../componentes/AvisoCarga'
import PestanasCompras from '../componentes/compra/PestanasCompras'
import { cargasApi } from '../api/cliente'
import { problemaDelArchivo } from '../utils/carga'
import { fechaHora } from '../utils/formato'
import comun from './Listado.module.css'
import estilos from './Cargas.module.css'

const ORIGEN = { PDF_JOTAPARTES: 'PDF de Jotapartes', EXCEL: 'Excel', CSV: 'CSV' }

/**
 * Cargar una factura entera de una vez (spec 0012, H1 y H8): se sube el archivo y se abre su pre-carga. Nada entra al
 * inventario hasta confirmar. Debajo, las pre-cargas sin terminar —se retoman desde cualquier equipo— y las últimas
 * cerradas.
 */
export default function Cargas() {
  const navigate = useNavigate()
  const entrada = useRef(null)
  const [archivo, setArchivo] = useState(null)
  const [subiendo, setSubiendo] = useState(false)
  const [error, setError] = useState(null)
  const [yaCargada, setYaCargada] = useState(null)
  const [lista, setLista] = useState({ datos: null, error: null })
  const [intento, setIntento] = useState(0)

  useEffect(() => {
    let vigente = true
    cargasApi.lista()
      .then((datos) => vigente && setLista({ datos, error: null }))
      .catch((e) => vigente && setLista((antes) => ({ datos: antes.datos, error: e })))
    return () => { vigente = false }
  }, [intento])

  function elegir(e) {
    const elegido = e.target.files?.[0] ?? null
    setArchivo(elegido)
    setError(elegido ? problemaDelArchivo(elegido) : null)
    setYaCargada(null)
  }

  async function subir() {
    const problema = problemaDelArchivo(archivo)
    if (problema) {
      setError(problema)
      return
    }
    setSubiendo(true)
    setError(null)
    setYaCargada(null)
    try {
      const carga = await cargasApi.subir(archivo)
      navigate(`/compras/cargas/${carga.id}`)
    } catch (e) {
      if (e.esFacturaYaCargada) setYaCargada(e.cuerpo.cargaId)
      setError(e.estado === 0 ? 'No hay conexión con el servidor' : e.message)
      setSubiendo(false)
    }
  }

  const borradores = lista.datos?.filter((c) => c.estado === 'BORRADOR') ?? []
  const cerradas = lista.datos?.filter((c) => c.estado !== 'BORRADOR') ?? []

  return (
    <div className={comun.pagina}>
      <PestanasCompras />

      <header className={comun.encabezado}>
        <div>
          <h1 className={comun.titulo}>Cargar factura</h1>
          <p className={comun.subtitulo}>
            Sube la factura y revisa la pre-carga: los precios sugeridos, lo que falta y lo que ya existía.
            Nada entra al inventario hasta que confirmes.
          </p>
        </div>
      </header>

      <section className={estilos.subir}>
        <div className={estilos.eleccion}>
          <input
            ref={entrada}
            type="file"
            accept=".pdf,.xlsx,.csv,.txt"
            onChange={elegir}
            className={estilos.archivo}
            aria-label="Archivo de la factura"
          />
          <Boton variante="primario" onClick={subir} disabled={!archivo || subiendo}>
            {subiendo ? 'Leyendo la factura…' : 'Subir y revisar'}
          </Boton>
        </div>
        <p className={estilos.ayuda}>
          El <strong>PDF de Importadora Jotapartes</strong> tal como llega por correo. Para otro proveedor, o
          mercancía sin factura, llena la <a href={cargasApi.urlPlantilla} download>plantilla</a> (código,
          descripción, cantidad y valor total; marca y categoría si las sabes) y súbela como .xlsx o .csv.
        </p>
        {subiendo && (
          <p className={estilos.leyendo}>Una factura de 30 páginas tarda unos segundos en leerse. No cierres esta pantalla.</p>
        )}
        {error && (
          <p className={estilos.error} role="alert">
            <span aria-hidden>⚠</span> {error}
            {yaCargada && <> <Link to={`/compras/cargas/${yaCargada}`}>Abrir la que ya está</Link></>}
          </p>
        )}
      </section>

      <AvisoCarga error={lista.error} onReintentar={() => setIntento((n) => n + 1)} desactualizado={!!lista.datos} />

      <h2 className={estilos.seccion}>Sin terminar</h2>
      {lista.datos && borradores.length === 0 && (
        <p className={estilos.nada}>No hay pre-cargas a medias.</p>
      )}
      <ul className={estilos.lista}>
        {borradores.map((c) => (
          <li key={c.id} className={estilos.item}>
            <div>
              <strong>{c.numeroFactura ?? c.nombreArchivo ?? 'Sin número'}</strong>
              <span className={estilos.detalle}>
                {ORIGEN[c.origen]} · {c.renglones} renglones · guardada {fechaHora(c.modificadaEn)}
              </span>
            </div>
            <Link to={`/compras/cargas/${c.id}`} className={comun.accion}>Seguir revisando</Link>
          </li>
        ))}
      </ul>

      {cerradas.length > 0 && (
        <>
          <h2 className={estilos.seccion}>Últimas cerradas</h2>
          <ul className={estilos.lista}>
            {cerradas.map((c) => (
              <li key={c.id} className={estilos.item}>
                <div>
                  <strong>{c.numeroFactura ?? c.nombreArchivo ?? 'Sin número'}</strong>
                  <span className={estilos.detalle}>
                    {c.estado === 'CONFIRMADA' ? 'Confirmada' : 'Descartada'} {fechaHora(c.cerradaEn)} · {c.renglones} renglones
                  </span>
                </div>
                {c.compraId && (
                  <Link to={`/compras/historial/${c.compraId}`} className={comun.accion}>Ver la compra</Link>
                )}
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}
