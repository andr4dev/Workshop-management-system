import { useEffect, useState } from 'react'
import Modal from '../Modal'
import Boton from '../Boton'
import { cuentasApi } from '../../api/cliente'
import estilos from './ModalCuentas.module.css'

/**
 * Administrar las cuentas desde las que se transfiere (spec 0002, RF-004).
 *
 * Solo se desactiva, no se borra: las compras que salieron de esa cuenta la siguen nombrando en el
 * historial y en los totales. Una desactivada deja de ofrecerse para compras nuevas.
 *
 * Desactivar pide confirmar en la misma fila: es de un clic, y quitar la cuenta equivocada deja al
 * cajero sin poder elegirla en la próxima compra.
 *
 * El padre lo monta solo al abrir: cada apertura lee la lista de nuevo.
 */
export default function ModalCuentas({ abierto, onCerrar, onDesactivada }) {
  const [cuentas, setCuentas] = useState(null)
  const [errorLista, setErrorLista] = useState(null)
  const [intento, setIntento] = useState(0)
  // La fila que está pidiendo confirmación, y la que se está enviando.
  const [confirmando, setConfirmando] = useState(null)
  const [enviando, setEnviando] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    let vigente = true
    cuentasApi.activas()
      .then((lista) => { if (vigente) { setCuentas(lista); setErrorLista(null) } })
      .catch((e) => { if (vigente) setErrorLista(e) })
    return () => { vigente = false }
  }, [intento])

  async function desactivar(cuenta) {
    setEnviando(cuenta.id)
    setError(null)
    try {
      await cuentasApi.desactivar(cuenta.id)
      setCuentas((cs) => cs.filter((c) => c.id !== cuenta.id))
      setConfirmando(null)
      onDesactivada(cuenta)
    } catch (e) {
      setError({ id: cuenta.id, mensaje: e.message })
    } finally {
      setEnviando(null)
    }
  }

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      titulo="Cuentas de transferencia"
      ancho={520}
      pie={<Boton variante="secundario" onClick={onCerrar}>Cerrar</Boton>}
    >
      <p className={estilos.explicacion}>
        Desactivar una cuenta la quita de la lista para compras nuevas. Las compras que ya salieron de
        ella la siguen mostrando, y sus totales no cambian.
      </p>

      {errorLista && (
        <div className={estilos.avisoRed} role="alert">
          <span><span aria-hidden>⚠</span> {errorLista.message}</span>
          <Boton variante="fantasma" tamano="chico" onClick={() => setIntento((n) => n + 1)}>
            Reintentar
          </Boton>
        </div>
      )}

      {!cuentas && !errorLista && <p className={estilos.vacio}>Cargando cuentas…</p>}

      {cuentas && cuentas.length === 0 && (
        <p className={estilos.vacio}>No hay cuentas activas. Se crean con «+ Nueva» al registrar una compra.</p>
      )}

      {cuentas && cuentas.length > 0 && (
        <ul className={estilos.lista}>
          {cuentas.map((c) => (
            <li key={c.id} className={estilos.fila}>
              {confirmando === c.id ? (
                <>
                  <span className={estilos.pregunta}>
                    ¿Desactivar <strong>{c.nombre}</strong>?
                  </span>
                  <span className={estilos.acciones}>
                    <Boton variante="fantasma" tamano="chico" disabled={enviando === c.id}
                      onClick={() => { setConfirmando(null); setError(null) }}>
                      No
                    </Boton>
                    <Boton variante="peligro" tamano="chico" disabled={enviando === c.id}
                      onClick={() => desactivar(c)}>
                      {enviando === c.id ? 'Desactivando…' : 'Sí, desactivar'}
                    </Boton>
                  </span>
                </>
              ) : (
                <>
                  <span className={estilos.nombre}>{c.nombre}</span>
                  <Boton variante="secundario" tamano="chico" disabled={enviando != null}
                    onClick={() => { setConfirmando(c.id); setError(null) }}>
                    Desactivar
                  </Boton>
                </>
              )}
              {error?.id === c.id && (
                <p className={estilos.error} role="alert">{error.mensaje}</p>
              )}
            </li>
          ))}
        </ul>
      )}
    </Modal>
  )
}
