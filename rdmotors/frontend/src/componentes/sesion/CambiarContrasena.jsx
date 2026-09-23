import { useState } from 'react'
import Boton from '../Boton'
import Campo from '../Campo'
import Modal from '../Modal'
import { sesionApi } from '../../api/cliente'
import { problemasDeCambio } from '../../utils/sesion'
import { useSesion } from './contexto'
import { PantallaDeEntrada } from './Entrar'
import estilos from './Sesion.module.css'

/**
 * Cambiar la contraseña (spec 0004, H8, RF-014 y RF-017).
 *
 * @param obligatoria entró con una contraseña inicial o restablecida: es una pantalla completa y no deja hacer otra
 *                    cosa hasta cambiarla (el servidor tampoco). Si no, es un modal desde el menú de la persona.
 */
export default function CambiarContrasena({ obligatoria = false, onCerrar }) {
  const { entro, salir, usuario } = useSesion()
  const [datos, setDatos] = useState({ actual: '', nueva: '', confirmacion: '' })
  const [intento, setIntento] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)
  const [listo, setListo] = useState(false)

  const problemas = problemasDeCambio(datos)
  const mostrar = (campo) => (intento ? problemas[campo] : null)
  const cambiar = (cambio) => { setDatos((d) => ({ ...d, ...cambio })); setError(null) }

  async function enviar(e) {
    e?.preventDefault()
    setIntento(true)
    if (Object.keys(problemas).length > 0) return
    setEnviando(true)
    try {
      // El servidor pone una cookie nueva: la de antes deja de valer.
      const nuevo = await sesionApi.cambiarContrasena(datos)
      entro(nuevo)
      if (!obligatoria) setListo(true)
    } catch (err) {
      setError(err.estado === 0 ? 'No hay conexión con el servidor.' : err.message)
    } finally {
      setEnviando(false)
    }
  }

  const campos = (
    <>
      <Campo etiqueta={obligatoria ? 'Contraseña que te dieron' : 'Contraseña actual'} type="password" requerido
        value={datos.actual} autoComplete="current-password" autoFocus error={mostrar('actual')}
        onChange={(e) => cambiar({ actual: e.target.value })} />
      <Campo etiqueta="Contraseña nueva" type="password" value={datos.nueva} autoComplete="new-password" requerido
        ayuda="Al menos 6 caracteres" error={mostrar('nueva')} onChange={(e) => cambiar({ nueva: e.target.value })} />
      <Campo etiqueta="Repite la nueva" type="password" value={datos.confirmacion} autoComplete="new-password"
        requerido error={mostrar('confirmacion')} onChange={(e) => cambiar({ confirmacion: e.target.value })} />
      {error && <p className={estilos.error} role="alert"><span aria-hidden>⚠</span> {error}</p>}
    </>
  )

  if (obligatoria) {
    return (
      <PantallaDeEntrada titulo="Cambia tu contraseña"
        texto={`Hola, ${usuario?.nombre}. Entraste con una contraseña que te dieron: elige la tuya antes de seguir.`}>
        <form className={estilos.formulario} onSubmit={enviar} noValidate>
          {campos}
          <div className={estilos.acciones}>
            <Boton variante="fantasma" onClick={salir} disabled={enviando}>Salir</Boton>
            <Boton variante="primario" type="submit" disabled={enviando}>{enviando ? 'Guardando…' : 'Guardar y entrar'}</Boton>
          </div>
        </form>
      </PantallaDeEntrada>
    )
  }

  return (
    <Modal abierto onCerrar={onCerrar} titulo="Cambiar mi contraseña" ancho={440}
      pie={listo
        ? <Boton variante="primario" onClick={onCerrar}>Listo</Boton>
        : (
          <>
            <Boton variante="fantasma" onClick={onCerrar} disabled={enviando}>Cancelar</Boton>
            <Boton variante="primario" onClick={() => enviar()} disabled={enviando}>{enviando ? 'Guardando…' : 'Cambiar'}</Boton>
          </>
        )}>
      {listo
        ? <p className={estilos.texto} role="status">Listo: tu contraseña cambió. En los otros equipos donde habías entrado, tendrás que entrar de nuevo.</p>
        : <form className={estilos.formulario} onSubmit={enviar} noValidate>{campos}</form>}
    </Modal>
  )
}
