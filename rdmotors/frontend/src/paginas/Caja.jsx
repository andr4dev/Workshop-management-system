import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import AvisoCarga from '../componentes/AvisoCarga'
import Boton from '../componentes/Boton'
import ModalMotivo from '../componentes/ModalMotivo'
import PestanasVenta from '../componentes/venta/PestanasVenta'
import ModalAbrirTurno from '../componentes/venta/ModalAbrirTurno'
import DesgloseArqueo from '../componentes/caja/DesgloseArqueo'
import ProducidoDelTurno from '../componentes/caja/ProducidoDelTurno'
import ModalCategoriasGasto from '../componentes/caja/ModalCategoriasGasto'
import ModalCerrarTurno from '../componentes/caja/ModalCerrarTurno'
import ModalGasto from '../componentes/caja/ModalGasto'
import ModalRetiro from '../componentes/caja/ModalRetiro'
import TablaMovimientos from '../componentes/caja/TablaMovimientos'
import AbonosDelTurno from '../componentes/caja/AbonosDelTurno'
import { gastosApi, retirosApi, turnosApi } from '../api/cliente'
import { useSesion } from '../componentes/sesion/contexto'
import { esAdministrador, esTurnoAjeno, textoDeTurnoAjeno } from '../utils/permisos'
import { fechaHora, formatoCOP } from '../utils/formato'
import comun from './Listado.module.css'
import estilos from './Caja.module.css'

/**
 * La caja del turno abierto (spec 0006, RF-023): lo que debería haber en el cajón en vivo, con de dónde sale cada
 * peso; lo que salió del cajón; registrar gastos y retiros; y cerrar el turno con lo contado.
 *
 * Los gastos se registran aquí, con un botón que abre el modal: del cajón o por fuera. La lista de gastos es de
 * Reportes. Sin turno abierto, ofrece abrirlo, y un gasto por fuera del cajón se puede registrar igual.
 *
 * <p>El gasto por fuera del cajón y las categorías son del administrador (spec 0004, §5). Con el turno de otra
 * persona abierto, un cajero ve de quién es y nada más: no mueve plata en un turno ajeno.
 */
export default function Caja() {
  const { usuario } = useSesion()
  const administrador = esAdministrador(usuario)
  const [intento, setIntento] = useState(0)
  const [carga, setCarga] = useState({ intento: null, turno: null, ajeno: null, error: null })
  const [modal, setModal] = useState(null)   // GASTO | RETIRO | CATEGORIAS | CERRAR | ABRIR | { anular: movimiento }
  const [aviso, setAviso] = useState(null)
  const [anulando, setAnulando] = useState(false)
  const [errorAnular, setErrorAnular] = useState(null)

  useEffect(() => {
    let vigente = true
    turnosApi.abierto()
      .then(async (abierto) => {
        // El de otra persona no se pide completo: el servidor no se lo muestra a un cajero.
        if (abierto && esTurnoAjeno(usuario, abierto)) return { turno: null, ajeno: abierto }
        return { turno: abierto ? await turnosApi.detalle(abierto.id) : null, ajeno: null }
      })
      .then(({ turno, ajeno }) => { if (vigente) setCarga({ intento, turno, ajeno, error: null }) })
      // Si falla, se conserva lo anterior: mejor verlo con el aviso que una pantalla vacía.
      .catch((error) => { if (vigente) setCarga((c) => ({ ...c, intento, error })) })
    return () => { vigente = false }
  }, [intento, usuario])

  const cargando = carga.intento !== intento
  const { turno, ajeno } = carga
  const recargar = () => setIntento((n) => n + 1)

  function registrado(texto) {
    setModal(null)
    setAviso(texto)
    recargar()
  }

  async function anular(motivo) {
    const { anular: m } = modal
    setAnulando(true)
    setErrorAnular(null)
    try {
      await (m.tipo === 'GASTO' ? gastosApi.anular(m.id, motivo) : retirosApi.anular(m.id, motivo))
      registrado(m.tipo === 'GASTO' ? 'Gasto anulado: ya no resta al cerrar.' : 'Retiro anulado: ya no resta al cerrar.')
    } catch (e) {
      setErrorAnular(e.message)
    } finally {
      setAnulando(false)
    }
  }

  return (
    <div className={comun.pagina}>
      <PestanasVenta />

      <header className={comun.encabezado}>
        <div>
          <h1 className={comun.titulo}>Caja</h1>
          <p className={comun.subtitulo}>Lo que sale del cajón en el turno, y el cierre con el conteo.</p>
        </div>
        <Link to="/vender/caja/turnos" className={comun.accion}>Turnos anteriores</Link>
      </header>

      {aviso && (
        <p className={estilos.aviso} role="status">
          {aviso}
          <Boton variante="fantasma" tamano="chico" onClick={() => setAviso(null)}>Cerrar aviso</Boton>
        </p>
      )}

      <AvisoCarga error={cargando ? null : carga.error} onReintentar={recargar} desactualizado={turno != null} />

      {cargando && !turno && !carga.error && <p className={comun.vacio}>Cargando la caja…</p>}

      {!cargando && !carga.error && ajeno && (
        <div className={comun.vacio} role="status">
          <p><strong>{textoDeTurnoAjeno(ajeno)}.</strong></p>
        </div>
      )}

      {!cargando && !carga.error && !turno && !ajeno && (
        <div className={comun.vacio}>
          <p><strong>No hay un turno abierto.</strong> Para vender y registrar lo que sale del cajón, abre el turno.</p>
          <div className={estilos.barraCentrada}>
            <Boton variante="primario" onClick={() => setModal('ABRIR')}>Abrir turno</Boton>
            {administrador && (
              <Boton variante="secundario" onClick={() => setModal('GASTO')}>Registrar gasto por fuera del cajón</Boton>
            )}
          </div>
        </div>
      )}

      {turno && (
        <div className={cargando ? comun.atenuado : undefined}>
          <section className={estilos.turno} aria-label="Turno abierto">
            <span className={estilos.turnoEstado}>Turno abierto</span>
            {turno.abiertoPor && <span>de {turno.abiertoPor.nombre}</span>}
            <span>desde {fechaHora(turno.abiertoEn)}</span>
            <span className={estilos.turnoFondo}>Fondo {formatoCOP(turno.fondo)}</span>
            <span>{turno.ventas.length} {turno.ventas.length === 1 ? 'venta' : 'ventas'}</span>
          </section>

          <div className={estilos.barra}>
            <Boton variante="secundario" onClick={() => setModal('GASTO')}>Registrar gasto</Boton>
            <Boton variante="secundario" onClick={() => setModal('RETIRO')}>Registrar retiro</Boton>
            {administrador && (
              <Boton variante="fantasma" onClick={() => setModal('CATEGORIAS')}>Categorías de gasto</Boton>
            )}
            <Boton variante="primario" className={estilos.empuje} onClick={() => setModal('CERRAR')}>Cerrar turno</Boton>
          </div>

          {/* Todo lo que produjo el turno, línea por línea; debajo, lo único por lo que se responde: el efectivo. */}
          <ProducidoDelTurno turno={turno} />

          <section className={estilos.panel} aria-label="Lo que debería haber en el cajón">
            <h2 className={estilos.panelTitulo}>Lo que debería haber en el cajón · por esto se responde</h2>
            <DesgloseArqueo turno={turno} />
          </section>

          <section className={estilos.seccion} aria-label="Lo que salió del cajón">
            <h2 className={estilos.seccionTitulo}>Lo que salió del cajón</h2>
            <TablaMovimientos turno={turno} onAnular={(m) => { setErrorAnular(null); setModal({ anular: m }) }} />
          </section>

          <section className={estilos.seccion} aria-label="Abonos de clientes">
            <h2 className={estilos.seccionTitulo}>Abonos de clientes</h2>
            <AbonosDelTurno abonos={turno.abonos} />
          </section>
        </div>
      )}

      {modal === 'ABRIR' && (
        <ModalAbrirTurno abierto onCerrar={() => setModal(null)}
          onAbierto={() => registrado('Turno abierto.')} onYaHabiaUno={() => { setModal(null); recargar() }} />
      )}
      {modal === 'GASTO' && (
        <ModalGasto turnoAbierto={turno} soloDelCajon={!administrador} onCerrar={() => setModal(null)}
          onRegistrado={(g) => registrado(`Gasto de ${formatoCOP(g.monto)} registrado${g.delCajon ? ' en el turno' : ''}.`)} />
      )}
      {modal === 'RETIRO' && (
        <ModalRetiro onCerrar={() => setModal(null)}
          onRegistrado={(r) => registrado(`Retiro de ${formatoCOP(r.monto)} registrado.`)} />
      )}
      {modal === 'CATEGORIAS' && (
        <ModalCategoriasGasto onCambio={() => {}} onCerrar={() => setModal(null)} />
      )}
      {modal === 'CERRAR' && turno && (
        <ModalCerrarTurno turno={turno} onCerrar={() => setModal(null)}
          onCerrado={() => registrado('Turno cerrado. Su cierre y su comprobante quedan en Turnos anteriores.')} />
      )}
      {modal?.anular && (
        <ModalMotivo
          titulo={modal.anular.tipo === 'GASTO' ? 'Anular gasto' : 'Anular retiro'}
          descripcion={`${modal.anular.detalle} · ${formatoCOP(modal.anular.monto)}. Queda en la lista, tachado, y deja de restar al cerrar.`}
          textoConfirmar="Anular"
          peligro
          enviando={anulando}
          error={errorAnular}
          placeholder="Ej.: se escribió $150.000 y eran $15.000"
          onConfirmar={anular}
          onCerrar={() => setModal(null)}
        />
      )}
    </div>
  )
}
