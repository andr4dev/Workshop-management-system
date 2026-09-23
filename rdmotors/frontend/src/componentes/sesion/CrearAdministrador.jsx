import { useState } from 'react'
import Boton from '../Boton'
import Campo from '../Campo'
import { instalacionApi } from '../../api/cliente'
import { problemasDelAdministrador } from '../../utils/sesion'
import { useSesion } from './contexto'
import { PantallaDeEntrada } from './Entrar'
import estilos from './Sesion.module.css'

/**
 * El primer administrador (spec 0004, H6 y RF-018). Solo aparece mientras el sistema no tiene ningún usuario: quien
 * instala elige su usuario y su contraseña. No hay una contraseña por defecto que se olvide cambiar.
 */
export default function CrearAdministrador() {
  const { entro, recargar } = useSesion()
  const [datos, setDatos] = useState({ nombre: '', usuario: '', contrasena: '', confirmacion: '' })
  const [intento, setIntento] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)

  const problemas = problemasDelAdministrador(datos)
  const mostrar = (campo) => (intento ? problemas[campo] : null)
  const cambiar = (cambio) => { setDatos((d) => ({ ...d, ...cambio })); setError(null) }

  async function enviar(e) {
    e.preventDefault()
    setIntento(true)
    if (Object.keys(problemas).length > 0) return
    setEnviando(true)
    try {
      entro(await instalacionApi.crearAdministrador(datos))
    } catch (err) {
      // Otra pantalla lo creó primero: ya no es la primera vez, así que toca entrar.
      if (err.codigo === 'YA_INSTALADO') { recargar(); return }
      setError(err.estado === 0 ? 'No hay conexión con el servidor.' : err.message)
      setEnviando(false)
    }
  }

  return (
    <PantallaDeEntrada titulo="Crear el administrador"
      texto="Es la primera vez que se abre el sistema. Crea tu usuario de administrador: con él vas a crear a los cajeros.">
      <form className={estilos.formulario} onSubmit={enviar} noValidate>
        <Campo etiqueta="Tu nombre" value={datos.nombre} maxLength={80} autoComplete="name" autoFocus requerido
          ayuda="El que sale en el comprobante" error={mostrar('nombre')}
          onChange={(e) => cambiar({ nombre: e.target.value })} />
        <Campo etiqueta="Usuario" value={datos.usuario} maxLength={40} autoComplete="username" requerido
          autoCapitalize="none" spellCheck={false} ayuda="Con el que vas a entrar, sin espacios"
          error={mostrar('usuario')} onChange={(e) => cambiar({ usuario: e.target.value })} />
        <Campo etiqueta="Contraseña" type="password" value={datos.contrasena} autoComplete="new-password" requerido
          ayuda="Al menos 6 caracteres" error={mostrar('contrasena')}
          onChange={(e) => cambiar({ contrasena: e.target.value })} />
        <Campo etiqueta="Repite la contraseña" type="password" value={datos.confirmacion} autoComplete="new-password"
          requerido error={mostrar('confirmacion')} onChange={(e) => cambiar({ confirmacion: e.target.value })} />
        {error && <p className={estilos.error} role="alert"><span aria-hidden>⚠</span> {error}</p>}
        <div className={estilos.acciones}>
          <Boton variante="primario" type="submit" disabled={enviando}>
            {enviando ? 'Creando…' : 'Crear y entrar'}
          </Boton>
        </div>
      </form>
    </PantallaDeEntrada>
  )
}
