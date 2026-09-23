import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import AvisoCarga from '../componentes/AvisoCarga'
import Boton from '../componentes/Boton'
import PestanasAjustes from '../componentes/PestanasAjustes'
import { usuariosApi } from '../api/cliente'
import { fechaHora } from '../utils/formato'
import { paginaDeLaUrl, rangoDePagina } from '../utils/inventario'
import comun from './Listado.module.css'
import estilos from './Usuarios.module.css'

const TAMANO = 50

/**
 * El registro de entradas (spec 0004, RF-025): quién entró, cuándo y desde qué equipo, y los intentos que fallaron.
 * Del administrador.
 *
 * <p>Sale el usuario <b>tal cual se escribió</b>, aunque no exista nadie así: un montón de intentos con "admin" a las
 * 3 de la mañana es justo lo que hay que poder ver. La contraseña no se guarda nunca, ni cuando falla.
 */
export default function Entradas() {
  const [params, setParams] = useSearchParams()
  const pagina = paginaDeLaUrl(params.get('p'))
  const [intento, setIntento] = useState(0)
  const clave = JSON.stringify([pagina, intento])
  const [listado, setListado] = useState({ clave: null, datos: null, error: null })

  useEffect(() => {
    let vigente = true
    usuariosApi.entradas({ pagina, tamano: TAMANO })
      .then((datos) => { if (vigente) setListado({ clave, datos, error: null }) })
      .catch((error) => { if (vigente) setListado((l) => ({ clave, datos: l.datos, error })) })
    return () => { vigente = false }
  }, [clave, pagina])

  const cargando = listado.clave !== clave
  const datos = listado.datos
  const irA = (n) => setParams((actual) => {
    const nuevos = new URLSearchParams(actual)
    if (n > 0) nuevos.set('p', String(n))
    else nuevos.delete('p')
    return nuevos
  })

  return (
    <div className={comun.pagina}>
      <PestanasAjustes />

      <header className={comun.encabezado}>
        <div>
          <h1 className={comun.titulo}>Entradas</h1>
          <p className={comun.subtitulo}>
            Quién entró, cuándo y desde qué equipo, y los intentos que no pasaron. De la más reciente a la más antigua.
          </p>
        </div>
      </header>

      <AvisoCarga error={cargando ? null : listado.error} onReintentar={() => setIntento((n) => n + 1)}
        desactualizado={datos != null} />
      {!datos && cargando && <p className={comun.vacio}>Cargando entradas…</p>}
      {datos && datos.elementos.length === 0 && (
        <div className={comun.vacio}><p>Todavía no hay entradas registradas.</p></div>
      )}

      {datos && datos.elementos.length > 0 && (
        <>
          <div className={`scroll-x ${comun.marco} ${cargando ? comun.atenuado : ''}`} aria-busy={cargando}>
            <table className={comun.tabla}>
              <thead>
                <tr>
                  <th>Cuándo</th>
                  <th>Usuario escrito</th>
                  <th>Persona</th>
                  <th>¿Entró?</th>
                  <th>Equipo</th>
                </tr>
              </thead>
              <tbody>
                {datos.elementos.map((e, i) => (
                  <tr key={`${e.momento}-${i}`}>
                    <td>{fechaHora(e.momento)}</td>
                    <td className={comun.mono}>{e.usuarioEscrito}</td>
                    <td>{e.quien ? e.quien.nombre : <span className={comun.tenue}>Nadie se llama así</span>}</td>
                    <td>
                      <span className={`${estilos.estado} ${e.exito ? estilos.activo : ''}`}>
                        {e.exito ? 'Entró' : 'No entró'}
                      </span>
                    </td>
                    <td className={comun.tenue}>
                      {e.ip ?? '—'}
                      {e.navegador && <span className={estilos.navegador}>{e.navegador}</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <nav className={comun.paginacion} aria-label="Páginas">
            <span className={comun.rango}>{rangoDePagina(datos.numero, datos.tamano, datos.total)}</span>
            <Boton variante="secundario" tamano="chico" disabled={datos.numero === 0}
              onClick={() => irA(datos.numero - 1)}>← Anterior</Boton>
            <Boton variante="secundario" tamano="chico" disabled={datos.numero + 1 >= datos.totalPaginas}
              onClick={() => irA(datos.numero + 1)}>Siguiente →</Boton>
          </nav>
        </>
      )}
    </div>
  )
}
