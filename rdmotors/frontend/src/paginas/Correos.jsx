import { useEffect, useRef, useState } from 'react'
import AvisoCarga from '../componentes/AvisoCarga'
import Boton from '../componentes/Boton'
import PestanasAjustes from '../componentes/PestanasAjustes'
import { correosApi } from '../api/cliente'
import { fechaHora } from '../utils/formato'
import { hoyEnColombia } from '../utils/periodo'
import {
  avisoDeLosCorreos, destinatariosDesdeTexto, ESTADOS, problemaDeLosDestinatarios, textoDelCorreo, TIPOS,
} from '../utils/correos'
import comun from './Listado.module.css'
import estilos from './Correos.module.css'

/**
 * El correo del cierre de caja (spec 0010). Del administrador.
 *
 * <p>Responde tres preguntas: <b>a quién le llega</b>, <b>si la cuenta de Brevo está lista</b> y <b>si los últimos
 * salieron</b>. Un correo que no salió no es un error de esta pantalla: se muestra con lo que dijo Brevo, que es lo
 * que hay que leerle a quien vaya a arreglarlo.
 */
export default function Correos() {
  const [intento, setIntento] = useState(0)
  const [carga, setCarga] = useState({ intento: null, datos: null, error: null })
  const [texto, setTexto] = useState('')
  /** Si el administrador ya escribió, una recarga no le pisa lo que lleva escrito. */
  const tocado = useRef(false)
  const [guardando, setGuardando] = useState(false)
  const [probando, setProbando] = useState(false)
  const [aviso, setAviso] = useState(null)
  const [error, setError] = useState(null)
  const hoy = hoyEnColombia()

  useEffect(() => {
    let vigente = true
    correosApi.estado()
      .then((datos) => {
        if (!vigente) return
        setCarga({ intento, datos, error: null })
        if (!tocado.current) setTexto(datos.destinatarios.join('\n'))
      })
      .catch((e) => { if (vigente) setCarga((c) => ({ ...c, intento, error: e })) })
    return () => { vigente = false }
  }, [intento])

  const datos = carga.datos
  const correos = destinatariosDesdeTexto(texto)
  const problema = problemaDeLosDestinatarios(correos)
  const cambiaron = datos && correos.join(',') !== datos.destinatarios.join(',')
  const alerta = avisoDeLosCorreos(datos)
  const recargar = () => setIntento((n) => n + 1)

  async function hacer(accion, textoDelAviso) {
    setError(null)
    setAviso(null)
    try {
      const resultado = await accion()
      setAviso(typeof textoDelAviso === 'function' ? textoDelAviso(resultado) : textoDelAviso)
      tocado.current = false
      recargar()
    } catch (e) {
      setError(e.message)
    }
  }

  async function guardar() {
    setGuardando(true)
    await hacer(() => correosApi.destinatarios(correos),
      correos.length === 0 ? 'Listo: nadie recibirá el resumen del cierre.'
        : `Listo: el resumen del cierre llegará a ${correos.length} ${correos.length === 1 ? 'correo' : 'correos'}.`)
    setGuardando(false)
  }

  async function probar() {
    setProbando(true)
    await hacer(correosApi.prueba, (correo) => (correo.estado === 'ENVIADO'
      ? 'Correo de prueba enviado: revisa la bandeja (y el spam la primera vez).'
      : `No salió: ${correo.ultimoError}`))
    setProbando(false)
  }

  return (
    <div className={comun.pagina}>
      <PestanasAjustes />

      <header className={comun.encabezado}>
        <div>
          <h1 className={comun.titulo}>Correos</h1>
          <p className={comun.subtitulo}>
            Al cerrar un turno, el resumen sale por correo. Si no hay internet, espera y sale cuando vuelva.
          </p>
        </div>
        <Boton variante="secundario" onClick={probar} disabled={probando || !datos || datos.destinatarios.length === 0}>
          {probando ? 'Mandando…' : 'Mandar uno de prueba'}
        </Boton>
      </header>

      <AvisoCarga error={carga.error} onReintentar={recargar} desactualizado={Boolean(datos)} />
      {aviso && <p className={estilos.exito} role="status">{aviso}</p>}
      {error && <p className={estilos.avisoGrave} role="alert"><span aria-hidden>⚠</span> {error}</p>}

      {datos && (
        <>
          {alerta && (
            <p className={alerta.tono === 'GRAVE' ? estilos.avisoGrave : estilos.aviso} role="alert">
              <span aria-hidden>⚠</span> {alerta.texto}
            </p>
          )}

          <section className={estilos.tarjetas} aria-label="Cómo está configurado">
            <div className={estilos.tarjeta}>
              <span className={estilos.etiqueta}>A quién le llega</span>
              <label className={estilos.etiquetaCampo} htmlFor="destinatarios">
                Un correo por línea, hasta 5
              </label>
              <textarea
                id="destinatarios"
                className={`${estilos.textarea} ${problema ? estilos.conError : ''}`}
                rows={4}
                value={texto}
                onChange={(e) => { setTexto(e.target.value); tocado.current = true; setAviso(null) }}
                placeholder={'ruben@rdmotors.co\nsocio@gmail.com'}
                autoComplete="off"
                spellCheck="false"
              />
              {problema && <span className={estilos.errorCampo} role="alert"><span aria-hidden>⚠</span> {problema}</span>}
              <div className={estilos.acciones}>
                <Boton variante="primario" onClick={guardar} disabled={guardando || Boolean(problema) || !cambiaron}>
                  {guardando ? 'Guardando…' : 'Guardar'}
                </Boton>
              </div>
              <p className={estilos.nota}>Sin ningún correo, el resumen del cierre queda apagado.</p>
            </div>

            <div className={estilos.tarjeta}>
              <span className={estilos.etiqueta}>La cuenta de Brevo</span>
              <span className={datos.listoParaMandar ? estilos.valorBien : estilos.valorFalta}>
                {datos.listoParaMandar ? 'Lista' : 'Falta configurar'}
              </span>
              <p className={estilos.nota}>
                {datos.listoParaMandar
                  ? <>Los correos salen de <strong>{datos.remitente}</strong>.</>
                  : datos.loQueFalta}
              </p>
              <p className={estilos.nota}>
                La llave va en el servidor (variable <code>BREVO_API_KEY</code>), nunca en esta pantalla. El remitente
                tiene que estar verificado en la cuenta de Brevo.
              </p>
            </div>
          </section>

          <h2 className={estilos.seccion}>Los últimos correos</h2>
          <div className={`scroll-x ${comun.marco}`}>
            {datos.ultimos.length === 0 ? (
              <p className={estilos.vacio}>Todavía no se ha mandado ninguno. El próximo cierre de turno deja el primero.</p>
            ) : (
              <table className={comun.tabla}>
                <thead>
                  <tr>
                    <th>Cuándo</th>
                    <th>Qué</th>
                    <th>Qué pasó</th>
                    <th>A quién</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {datos.ultimos.map((c) => (
                    <tr key={c.id} className={c.estado === 'FALLO' ? estilos.filaFallo : undefined}>
                      <td>{fechaHora(c.creadoEn)}</td>
                      <td>{TIPOS[c.tipo] ?? c.tipo}</td>
                      <td>
                        <span className={estilos[`estado${c.estado}`]}>{ESTADOS[c.estado] ?? c.estado}</span>
                        <span className={estilos.detalle}> · {textoDelCorreo(c, hoy)}</span>
                      </td>
                      <td className={estilos.destinatarios}>{c.destinatarios.join(', ')}</td>
                      <td>
                        {c.estado === 'FALLO' && (
                          <Boton variante="fantasma" tamano="chico"
                            onClick={() => hacer(() => correosApi.reintentar(c.id), 'Vuelve a la cola: sale en el próximo minuto.')}>
                            Reintentar
                          </Boton>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </>
      )}
    </div>
  )
}
