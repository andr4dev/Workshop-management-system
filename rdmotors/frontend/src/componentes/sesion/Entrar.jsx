import { useState } from 'react'
import Boton from '../Boton'
import Campo from '../Campo'
import { sesionApi } from '../../api/cliente'
import { problemasParaEntrar } from '../../utils/sesion'
import { useSesion } from './contexto'
import estilos from './Sesion.module.css'

/** Una tarjeta centrada con la marca: *Entrar*, *Crear el administrador* y *Cambiar la contraseña* obligatoria. */
export function PantallaDeEntrada({ titulo, texto, children }) {
  return (
    <main className={estilos.fondo}>
      <section className={estilos.tarjeta} aria-labelledby="titulo-entrada">
        <div className={estilos.marca} aria-hidden><span>RD</span><span>MOTORS</span></div>
        <h1 className={estilos.titulo} id="titulo-entrada">{titulo}</h1>
        {texto && <p className={estilos.texto}>{texto}</p>}
        {children}
      </section>
    </main>
  )
}

/**
 * Entrar con usuario y contraseña (spec 0004, H1, RF-001 a RF-003).
 *
 * El servidor responde lo mismo si el usuario no existe o la contraseña está mal, y se muestra tal cual: esta
 * pantalla no sabe cuál de los dos falló, y no debe saberlo.
 */
export default function Entrar() {
  const { entro } = useSesion()
  const [datos, setDatos] = useState({ usuario: '', contrasena: '' })
  const [intento, setIntento] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)

  const problemas = problemasParaEntrar(datos)
  const cambiar = (cambio) => { setDatos((d) => ({ ...d, ...cambio })); setError(null) }

  async function enviar(e) {
    e.preventDefault()
    setIntento(true)
    if (Object.keys(problemas).length > 0) return
    setEnviando(true)
    try {
      entro(await sesionApi.entrar(datos))
    } catch (err) {
      setError(err.estado === 0 ? 'No hay conexión con el servidor.' : err.message)
      setDatos((d) => ({ ...d, contrasena: '' }))
      setEnviando(false)
    }
  }

  return (
    <PantallaDeEntrada titulo="Entrar" texto="Con tu usuario y tu contraseña.">
      <form className={estilos.formulario} onSubmit={enviar} noValidate>
        <Campo etiqueta="Usuario" value={datos.usuario} autoComplete="username" autoFocus
          autoCapitalize="none" spellCheck={false}
          error={intento ? problemas.usuario : null} onChange={(e) => cambiar({ usuario: e.target.value })} />
        <Campo etiqueta="Contraseña" type="password" value={datos.contrasena} autoComplete="current-password"
          error={intento ? problemas.contrasena : null} onChange={(e) => cambiar({ contrasena: e.target.value })} />
        {error && <p className={estilos.error} role="alert"><span aria-hidden>⚠</span> {error}</p>}
        <div className={estilos.acciones}>
          <Boton variante="primario" type="submit" disabled={enviando}>{enviando ? 'Entrando…' : 'Entrar'}</Boton>
        </div>
      </form>
    </PantallaDeEntrada>
  )
}
