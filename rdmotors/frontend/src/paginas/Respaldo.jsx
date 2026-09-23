import { useEffect, useState } from 'react'
import AvisoCarga from '../componentes/AvisoCarga'
import Boton from '../componentes/Boton'
import PestanasAjustes from '../componentes/PestanasAjustes'
import { respaldosApi } from '../api/cliente'
import { fechaHora } from '../utils/formato'
import { hoyEnColombia } from '../utils/periodo'
import {
  avisoDelRespaldo, EVENTO_RESPALDO_HECHO, haceCuanto, pesoEnPalabras, textoDeLaCopia,
} from '../utils/respaldo'
import comun from './Listado.module.css'
import estilos from './Respaldo.module.css'

/**
 * El respaldo de la base (spec 0011). Del administrador.
 *
 * <p>Responde una sola pregunta: **¿si mañana pierdo el acceso a este sistema, qué me queda?** Y la respuesta ya no
 * es "hay catorce copias en una carpeta", porque el sistema no corre en un computador nuestro: la respuesta es
 * **"lo que te hayas bajado"**. Por eso lo primero que se ve es cuándo fue la última vez que se bajó una, y el
 * botón para bajar otra.
 *
 * <p>Un intento que falla **no es un error de la pantalla**: se muestra con lo que dijo el motor, porque es lo que
 * hay que leerle a quien vaya a arreglarlo.
 */
export default function Respaldo() {
  const [intento, setIntento] = useState(0)
  const [carga, setCarga] = useState({ intento: null, datos: null, error: null })
  const [bajando, setBajando] = useState(false)
  const [recien, setRecien] = useState(null)
  const hoy = hoyEnColombia()

  useEffect(() => {
    let vigente = true
    respaldosApi.estado()
      .then((datos) => { if (vigente) setCarga({ intento, datos, error: null }) })
      .catch((e) => { if (vigente) setCarga((c) => ({ ...c, intento, error: e })) })
    return () => { vigente = false }
  }, [intento])

  const cargando = carga.intento !== intento
  const datos = carga.datos
  const aviso = avisoDelRespaldo(datos)

  async function bajarla() {
    setBajando(true)
    setRecien(null)
    try {
      const { blob, nombre } = await respaldosApi.bajarArchivo()
      guardarEnElEquipo(blob, nombre)
      setRecien({ ok: true, nombre, bytes: blob.size })
      setIntento((n) => n + 1)
      // El aviso de arriba leyó el estado al cargar la página: sin esto seguiría diciendo "hace 9 días" con la
      // copia recién bajada a la vista en esta misma pantalla.
      window.dispatchEvent(new CustomEvent(EVENTO_RESPALDO_HECHO))
    } catch (e) {
      setRecien({ ok: false, error: e.message })
      // El intento fallido también quedó registrado en el servidor: se recarga para que aparezca en la lista.
      setIntento((n) => n + 1)
    } finally {
      setBajando(false)
    }
  }

  return (
    <div className={comun.pagina}>
      <PestanasAjustes />

      <header className={comun.encabezado}>
        <div>
          <h1 className={comun.titulo}>Respaldo</h1>
          <p className={comun.subtitulo}>
            Una copia completa del negocio, guardada por ti. Bájala de vez en cuando y no la borres.
          </p>
        </div>
        <Boton onClick={bajarla} disabled={bajando}>
          {bajando ? 'Sacando la copia…' : 'Bajar una copia'}
        </Boton>
      </header>

      <AvisoCarga error={carga.error} onReintentar={() => setIntento((n) => n + 1)} desactualizado={Boolean(datos)} />

      {datos && (
        <>
          {aviso && (
            <p className={aviso.tono === 'GRAVE' ? estilos.avisoGrave : estilos.aviso} role="alert">
              <span aria-hidden>⚠</span> {aviso.texto}
            </p>
          )}

          {recien && (
            <p className={recien.ok ? estilos.exito : estilos.avisoGrave} role="status">
              {recien.ok
                ? `Listo: se bajó ${recien.nombre} (${pesoEnPalabras(recien.bytes)}). Guárdalo donde no se pierda.`
                : `No se pudo sacar la copia: ${recien.error}`}
            </p>
          )}

          <section className={estilos.tarjetas} aria-label="Cómo va el respaldo">
            <div className={estilos.tarjeta}>
              <span className={estilos.etiqueta}>Última copia que bajaste</span>
              <span className={estilos.valor}>
                {datos.diasSinBajar == null ? 'Ninguna' : haceCuanto(datos.diasSinBajar)}
              </span>
              <p className={estilos.nota}>
                {datos.ultimaBuena
                  ? `${fechaHora(datos.ultimaBuena.hechoEn)} · ${pesoEnPalabras(datos.ultimaBuena.bytes)}`
                  : 'Nunca te has bajado una copia de este negocio'}
              </p>
            </div>
            <div className={estilos.tarjeta}>
              <span className={estilos.etiqueta}>Cada cuánto conviene</span>
              <span className={estilos.valor}>{datos.diasParaAvisar} días</span>
              <p className={estilos.nota}>
                Pasado ese tiempo sin bajar ninguna, te aparece un aviso al entrar.
              </p>
            </div>
            <div className={estilos.tarjeta}>
              <span className={estilos.etiqueta}>Dónde queda</span>
              <span className={estilos.ruta}>En tu equipo</span>
              <p className={estilos.nota}>
                El sistema no guarda copias: el archivo se baja y se va contigo. Ponlo en un disco externo o en tu
                nube personal, no solo en el computador del almacén.
              </p>
            </div>
          </section>

          <h2 className={estilos.seccion}>Las últimas veces</h2>
          {/* La tabla no cabe en un celular: se desliza dentro de su marco, como las demás listas. */}
          <div className={`scroll-x ${comun.marco} ${cargando ? comun.atenuado : ''}`} aria-busy={cargando}>
            {datos.copias.length === 0 ? (
              <p className={estilos.vacio}>
                Todavía no has bajado ninguna copia. Empieza con <b>Bajar una copia</b>.
              </p>
            ) : (
              <table className={comun.tabla}>
                <thead>
                  <tr>
                    <th>Cuándo</th>
                    <th>Qué pasó</th>
                    <th>Archivo</th>
                  </tr>
                </thead>
                <tbody>
                  {datos.copias.map((c) => (
                    <tr key={c.id} className={c.estado === 'FALLO' ? estilos.filaFallo : undefined}>
                      <td>{fechaHora(c.hechoEn)}</td>
                      <td>{textoDeLaCopia(c, hoy)}</td>
                      <td className={estilos.archivo}>{c.archivo}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          <p className={estilos.pie}>
            Para restaurar una copia, sigue <b>docs/RESPALDO_Y_RESTAURAR.md</b>. Una copia que nunca se restauró no
            es un respaldo: pruébala una vez, a propósito, antes de necesitarla.
          </p>
        </>
      )}
    </div>
  )
}

/**
 * Le entrega el archivo al navegador para que lo guarde donde el dueño quiera.
 *
 * <p>Se crea un enlace de mentira y se aprieta solo: es la única forma de que el archivo se guarde con su nombre en
 * vez de abrirse en una pestaña. La dirección temporal se suelta enseguida, o el archivo se queda en la memoria del
 * navegador hasta que se cierre la pestaña.
 */
function guardarEnElEquipo(blob, nombre) {
  const direccion = URL.createObjectURL(blob)
  const enlace = document.createElement('a')
  enlace.href = direccion
  enlace.download = nombre
  document.body.appendChild(enlace)
  enlace.click()
  enlace.remove()
  URL.revokeObjectURL(direccion)
}
