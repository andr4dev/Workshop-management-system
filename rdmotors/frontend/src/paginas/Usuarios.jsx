import { useEffect, useState } from 'react'
import AvisoCarga from '../componentes/AvisoCarga'
import Boton from '../componentes/Boton'
import Campo from '../componentes/Campo'
import Modal from '../componentes/Modal'
import PestanasAjustes from '../componentes/PestanasAjustes'
import { usuariosApi } from '../api/cliente'
import { useSesion } from '../componentes/sesion/contexto'
import { fechaHora } from '../utils/formato'
import {
  comandoDelUsuario, NOMBRE_DE_ROL, ordenarUsuarios, porQueNoCambiaAcajero, porQueNoSeDesactiva,
  problemasDelUsuario, ROLES, sinProblemas, usuarioNuevo,
} from '../utils/usuarios'
import comun from './Listado.module.css'
import estilos from './Usuarios.module.css'

/**
 * Las personas que entran al sistema (spec 0004, fase 4). Del administrador.
 *
 * <p>No se borra a nadie: se desactiva, y lo que hizo lo sigue nombrando (RF-015). Siempre queda al menos un
 * administrador activo, y nadie se desactiva a sí mismo (RF-016): aquí se dice <b>por qué</b> no se puede, junto al
 * botón, y el servidor lo vuelve a exigir.
 *
 * <p>La contraseña que se pone al crear o al restablecer es temporal: la persona la cambia al entrar (RF-014). La
 * restablecida se muestra <b>una sola vez</b>, para dictarla.
 */
export default function Usuarios() {
  const { usuario: yo } = useSesion()
  const [intento, setIntento] = useState(0)
  const [carga, setCarga] = useState({ intento: null, usuarios: null, error: null })
  const [modal, setModal] = useState(null)   // 'CREAR' | { restablecer: usuario } | { temporal, usuario }
  const [aviso, setAviso] = useState(null)
  const [error, setError] = useState(null)
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    let vigente = true
    usuariosApi.todos()
      .then((usuarios) => { if (vigente) setCarga({ intento, usuarios, error: null }) })
      .catch((e) => { if (vigente) setCarga((c) => ({ ...c, intento, error: e })) })
    return () => { vigente = false }
  }, [intento])

  const cargando = carga.intento !== intento
  const usuarios = carga.usuarios ?? []
  const recargar = () => setIntento((n) => n + 1)

  async function hacer(accion, textoDelAviso) {
    setEnviando(true)
    setError(null)
    try {
      await accion()
      setAviso(textoDelAviso)
      recargar()
    } catch (e) {
      setError(e.message)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className={comun.pagina}>
      <PestanasAjustes />

      <header className={comun.encabezado}>
        <div>
          <h1 className={comun.titulo}>Usuarios</h1>
          <p className={comun.subtitulo}>
            Quién entra al sistema y qué puede hacer. No se borra a nadie: se desactiva, y lo que hizo lo sigue
            nombrando.
          </p>
        </div>
        <Boton variante="primario" onClick={() => { setError(null); setModal('CREAR') }}>+ Crear usuario</Boton>
      </header>

      {aviso && (
        <p className={comun.vacio} role="status">
          {aviso} <Boton variante="fantasma" tamano="chico" onClick={() => setAviso(null)}>Cerrar aviso</Boton>
        </p>
      )}

      <AvisoCarga error={cargando ? null : carga.error} onReintentar={recargar} desactualizado={carga.usuarios != null} />
      {!carga.usuarios && cargando && <p className={comun.vacio}>Cargando usuarios…</p>}
      {error && <p className={estilos.error} role="alert"><span aria-hidden>⚠</span> {error}</p>}

      {carga.usuarios && (
        <div className={`scroll-x ${comun.marco} ${cargando ? comun.atenuado : ''}`} aria-busy={cargando}>
          <table className={comun.tabla}>
            <thead>
              <tr>
                <th>Persona</th>
                <th>Rol</th>
                <th>Estado</th>
                <th>Última entrada</th>
                <th aria-label="Acciones" />
              </tr>
            </thead>
            <tbody>
              {ordenarUsuarios(usuarios).map((u) => {
                const noSeDesactiva = porQueNoSeDesactiva(u, yo, usuarios)
                const noPasaACajero = porQueNoCambiaAcajero(u, usuarios)
                return (
                  <tr key={u.id} className={u.activo ? undefined : estilos.desactivado}>
                    <td>
                      <strong>{u.nombre}</strong>
                      <span className={comun.tenue}> · {u.usuario}</span>
                      {u.debeCambiarContrasena && (
                        <span className={comun.tenue}><br />Tiene que cambiar la contraseña al entrar</span>
                      )}
                    </td>
                    <td>
                      <select className={estilos.rol} value={u.rol} disabled={enviando || Boolean(noPasaACajero)}
                        title={noPasaACajero ?? `Rol de ${u.nombre}`}
                        aria-label={`Rol de ${u.nombre}`}
                        onChange={(e) => hacer(() => usuariosApi.cambiarRol(u.id, e.target.value),
                          `${u.nombre} ahora es ${NOMBRE_DE_ROL[e.target.value].toLowerCase()}. Entra de nuevo para ver sus pantallas.`)}>
                        {ROLES.map((r) => <option key={r.valor} value={r.valor}>{r.nombre}</option>)}
                      </select>
                    </td>
                    <td>
                      <span className={`${estilos.estado} ${u.activo ? estilos.activo : ''}`}>
                        {u.activo ? 'Activo' : 'Desactivado'}
                      </span>
                    </td>
                    <td className={comun.tenue}>{u.ultimaEntrada ? fechaHora(u.ultimaEntrada) : 'Nunca ha entrado'}</td>
                    <td>
                      <div className={estilos.acciones}>
                        <Boton variante="secundario" tamano="chico" disabled={enviando}
                          onClick={() => { setError(null); setModal({ restablecer: u }) }}>
                          Restablecer contraseña
                        </Boton>
                        {u.activo ? (
                          <Boton variante="peligro" tamano="chico" disabled={enviando || Boolean(noSeDesactiva)}
                            title={noSeDesactiva ?? undefined}
                            onClick={() => hacer(() => usuariosApi.desactivar(u.id),
                              `${u.nombre} quedó desactivado: ya no puede entrar.`)}>
                            Desactivar
                          </Boton>
                        ) : (
                          <Boton variante="secundario" tamano="chico" disabled={enviando}
                            onClick={() => hacer(() => usuariosApi.activar(u.id),
                              `${u.nombre} vuelve a entrar con su misma contraseña.`)}>
                            Activar
                          </Boton>
                        )}
                      </div>
                      {noSeDesactiva && u.activo && <span className={comun.tenue}>{noSeDesactiva}</span>}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {modal === 'CREAR' && (
        <ModalCrear
          enviando={enviando}
          error={error}
          onCerrar={() => setModal(null)}
          onCrear={async (datos) => {
            setEnviando(true)
            setError(null)
            try {
              const creado = await usuariosApi.crear(comandoDelUsuario(datos))
              setModal(null)
              setAviso(`${creado.nombre} ya puede entrar. Al hacerlo, el sistema le pide cambiar la contraseña.`)
              recargar()
            } catch (e) {
              setError(e.message)
            } finally {
              setEnviando(false)
            }
          }}
        />
      )}

      {modal?.restablecer && (
        <Modal abierto titulo="Restablecer la contraseña" onCerrar={() => setModal(null)}
          pie={(
            <>
              <Boton variante="fantasma" onClick={() => setModal(null)} disabled={enviando}>Cancelar</Boton>
              <Boton variante="primario" disabled={enviando}
                onClick={async () => {
                  setEnviando(true)
                  setError(null)
                  try {
                    const { contrasenaTemporal } = await usuariosApi.restablecer(modal.restablecer.id)
                    setModal({ temporal: contrasenaTemporal, usuario: modal.restablecer })
                    recargar()
                  } catch (e) {
                    setError(e.message)
                  } finally {
                    setEnviando(false)
                  }
                }}>
                {enviando ? 'Restableciendo…' : 'Restablecer'}
              </Boton>
            </>
          )}>
          <p className={estilos.nota}>
            A <strong>{modal.restablecer.nombre}</strong> se le pondrá una contraseña temporal. La que tiene ahora deja
            de servir y sus sesiones abiertas se cierran; al entrar con la temporal, elige la suya.
          </p>
          {error && <p className={estilos.error} role="alert">{error}</p>}
        </Modal>
      )}

      {modal?.temporal && (
        <Modal abierto titulo="Dicta esta contraseña" onCerrar={() => setModal(null)}
          pie={<Boton variante="primario" onClick={() => setModal(null)}>Listo</Boton>}>
          <p className={estilos.nota}>
            La contraseña temporal de <strong>{modal.usuario.nombre}</strong>. <strong>Se ve una sola vez:</strong> si
            se cierra esta ventana, toca restablecerla otra vez.
          </p>
          <code className={estilos.temporal}>{modal.temporal}</code>
          <p className={estilos.nota}>Al entrar con ella, el sistema le pide cambiarla de una vez.</p>
        </Modal>
      )}
    </div>
  )
}

/** El formulario de alguien nuevo: nombre, usuario, rol y su contraseña inicial. */
function ModalCrear({ enviando, error, onCerrar, onCrear }) {
  const [datos, setDatos] = useState(usuarioNuevo)
  const [intento, setIntento] = useState(false)
  const problemas = problemasDelUsuario(datos)
  const mostrar = (campo) => (intento ? problemas[campo] : null)
  const cambiar = (cambios) => setDatos((d) => ({ ...d, ...cambios }))

  function crear() {
    setIntento(true)
    if (sinProblemas(problemas)) onCrear(datos)
  }

  return (
    <Modal abierto titulo="Crear usuario" onCerrar={onCerrar}
      pie={(
        <>
          <Boton variante="fantasma" onClick={onCerrar} disabled={enviando}>Cancelar</Boton>
          <Boton variante="primario" onClick={crear} disabled={enviando}>
            {enviando ? 'Creando…' : 'Crear usuario'}
          </Boton>
        </>
      )}>
      <div className={estilos.formulario}>
        <Campo etiqueta="Nombre" requerido value={datos.nombre} error={mostrar('nombre')}
          ayuda="El que sale en el comprobante" autoComplete="off"
          onChange={(e) => cambiar({ nombre: e.target.value })} />
        <Campo etiqueta="Usuario" requerido value={datos.usuario} error={mostrar('usuario')}
          ayuda="Con el que va a entrar, sin espacios" autoComplete="off"
          onChange={(e) => cambiar({ usuario: e.target.value })} />
        <Campo etiqueta="Rol" requerido error={mostrar('rol')}
          ayuda={ROLES.find((r) => r.valor === datos.rol)?.explica}>
          <select className={estilos.rol} value={datos.rol} onChange={(e) => cambiar({ rol: e.target.value })}>
            {ROLES.map((r) => <option key={r.valor} value={r.valor}>{r.nombre}</option>)}
          </select>
        </Campo>
        <Campo etiqueta="Contraseña inicial" requerido value={datos.contrasena} error={mostrar('contrasena')}
          ayuda="La cambia al entrar. Al menos 6 caracteres" autoComplete="off"
          onChange={(e) => cambiar({ contrasena: e.target.value })} />
        {error && <p className={estilos.error} role="alert"><span aria-hidden>⚠</span> {error}</p>}
      </div>
    </Modal>
  )
}
