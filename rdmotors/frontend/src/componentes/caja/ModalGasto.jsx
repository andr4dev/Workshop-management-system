import { useEffect, useState } from 'react'
import Modal from '../Modal'
import Boton from '../Boton'
import Campo from '../Campo'
import AvisoCarga from '../AvisoCarga'
import AvisoConfirmarMonto from './AvisoConfirmarMonto'
import Segmento from './Segmento'
import { categoriasGastoApi, cuentasApi, gastosApi } from '../../api/cliente'
import {
  categoriasParaElegir, comandoDelGasto, conCategoria, gastoNuevo, montoDesdeTexto, NATURALEZAS, problemasDelGasto,
  sinProblemas,
} from '../../utils/gastos'
import { formatoCOP } from '../../utils/formato'
import { llaveNueva } from '../../utils/venta'
import estilos from './Caja.module.css'

const hoyLocal = () => new Date().toLocaleDateString('en-CA')   // YYYY-MM-DD en hora local

/**
 * Registrar un gasto (spec 0006, H1 y H10): del cajón del turno abierto, o pagado por fuera (el arriendo por
 * Nequi).
 *
 * La llave nace al abrir el modal y viaja igual en cada reintento: un doble clic o un corte de red no dejan dos
 * gastos. Si es más de lo que debería haber en el cajón, el servidor pide confirmar y aquí se pregunta.
 *
 * El padre lo monta solo al abrir: cada apertura arranca limpia, con una llave nueva.
 *
 * @param soloDelCajon el cajero solo registra gastos del cajón: el gasto por fuera es del administrador (spec 0004)
 * @param porFuera     arranca en "por fuera del cajón": desde Reportes, el socio registra lo que pagó él, cuando
 *                     quiera, sin pasar por el turno. Si hay turno abierto puede escoger igual "del cajón"
 */
export default function ModalGasto({ turnoAbierto, soloDelCajon = false, porFuera = false, onRegistrado, onCerrar }) {
  const hoy = hoyLocal()
  const hayTurno = Boolean(turnoAbierto)
  const [llave] = useState(() => llaveNueva())
  const [gasto, setGasto] = useState(() => gastoNuevo({ hayTurno: hayTurno && !porFuera, hoy }))
  const [intento, setIntento] = useState(0)
  const [carga, setCarga] = useState({ categorias: null, cuentas: [], error: null })
  const [intentoRegistrar, setIntentoRegistrar] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState(null)
  const [pideConfirmar, setPideConfirmar] = useState(false)

  useEffect(() => {
    let vigente = true
    Promise.all([categoriasGastoApi.todas(), cuentasApi.activas()])
      .then(([categorias, cuentas]) => { if (vigente) setCarga({ categorias, cuentas, error: null }) })
      .catch((e) => { if (vigente) setCarga((c) => ({ ...c, error: e })) })
    return () => { vigente = false }
  }, [intento])

  const problemas = problemasDelGasto(gasto, { hayTurno, hoy })
  const mostrar = (campo) => (intentoRegistrar ? problemas[campo] : null)
  const categorias = categoriasParaElegir(carga.categorias)
  const elegida = categorias.find((c) => c.id === gasto.categoriaId)
  const monto = montoDesdeTexto(gasto.monto)

  function cambiar(cambios) {
    setGasto((g) => ({ ...g, ...cambios }))
    setError(null)
    setPideConfirmar(false)
  }

  async function registrar(confirmado = false) {
    setIntentoRegistrar(true)
    if (!sinProblemas(problemas)) return
    setEnviando(true)
    setError(null)
    try {
      onRegistrado(await gastosApi.registrar(comandoDelGasto(gasto, llave, confirmado)))
    } catch (e) {
      if (e.esConfirmarMonto) setPideConfirmar(true)
      else setError(e.estado === 0 ? 'No hay conexión con el servidor. Intenta de nuevo: el gasto no queda dos veces.' : e.message)
      setEnviando(false)
    }
  }

  return (
    <Modal
      abierto
      onCerrar={onCerrar}
      titulo="Registrar gasto"
      ancho={560}
      pie={
        <>
          <Boton variante="fantasma" onClick={onCerrar} disabled={enviando}>Cancelar</Boton>
          <Boton variante="primario" onClick={() => registrar(false)} disabled={enviando || pideConfirmar}>
            {enviando && !pideConfirmar ? 'Registrando…' : 'Registrar gasto'}
          </Boton>
        </>
      }
    >
      <div className={estilos.formulario}>
        <Campo etiqueta="¿De dónde salió la plata?" requerido error={mostrar('origen')}>
          <Segmento
            etiqueta="De dónde salió la plata"
            valor={gasto.delCajon ? 'CAJON' : 'FUERA'}
            opciones={[['CAJON', 'Del cajón', !hayTurno], ['FUERA', 'Por fuera del cajón', soloDelCajon]]}
            onCambio={(opcion) => cambiar({ delCajon: opcion === 'CAJON' })}
          />
        </Campo>
        <p className={estilos.nota}>
          {gasto.delCajon
            ? `Resta de lo que debe haber en el cajón al cerrar el turno.${soloDelCajon
              ? ' Uno pagado por fuera del cajón lo registra el administrador.' : ''}`
            : hayTurno
              ? 'El arriendo por Nequi, la luz que pagó el dueño: es gasto del negocio, pero no toca el cajón.'
              : 'No hay un turno abierto: solo se registran gastos pagados por fuera del cajón.'}
        </p>

        <AvisoCarga error={carga.error} onReintentar={() => setIntento((n) => n + 1)} />

        <Campo etiqueta="Categoría" requerido error={mostrar('categoria')}
          ayuda={elegida ? `Cuenta como ${NATURALEZAS[elegida.naturaleza].toLowerCase()} en el reporte` : null}>
          <select className={estilos.select} value={gasto.categoriaId} disabled={!carga.categorias}
            onChange={(e) => {
              setGasto((g) => conCategoria(g, categorias.find((c) => c.id === e.target.value)))
              setError(null)
              setPideConfirmar(false)
            }}>
            <option value="">{carga.categorias ? 'Elige la categoría…' : 'Cargando…'}</option>
            {categorias.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
          </select>
        </Campo>

        <div className={estilos.dosColumnas}>
          <Campo
            etiqueta="Monto"
            requerido
            value={gasto.monto}
            onChange={(e) => cambiar({ monto: e.target.value })}
            inputMode="numeric"
            placeholder="15000"
            autoComplete="off"
            error={mostrar('monto')}
            ayuda={monto ? formatoCOP(monto) : null}
          />
          {!gasto.delCajon && (
            <Campo
              etiqueta="Fecha"
              requerido
              type="date"
              max={hoy}
              value={gasto.fecha}
              onChange={(e) => cambiar({ fecha: e.target.value })}
              error={mostrar('fecha')}
            />
          )}
        </div>

        <label className={estilos.casilla}>
          <input type="checkbox" checked={gasto.delMes}
            onChange={(e) => cambiar({ delMes: e.target.checked, delMesTocado: true })} />
          <span>
            Es un gasto del mes <span className={estilos.nota}>(arriendo, nómina, servicios)</span>
          </span>
        </label>

        <Campo
          etiqueta="¿En qué se gastó?"
          requerido
          value={gasto.descripcion}
          maxLength={300}
          onChange={(e) => cambiar({ descripcion: e.target.value })}
          placeholder="Flete Jotapartes FV-9912"
          autoComplete="off"
          error={mostrar('descripcion')}
        />

        {!gasto.delCajon && (
          <div className={estilos.dosColumnas}>
            <Campo etiqueta="Cómo se pagó" requerido error={mostrar('formaPago')}>
              <Segmento
                etiqueta="Cómo se pagó"
                valor={gasto.formaPago}
                opciones={[['EFECTIVO', 'Efectivo'], ['TRANSFERENCIA', 'Transferencia']]}
                onCambio={(forma) => cambiar({ formaPago: forma, cuentaId: forma === 'EFECTIVO' ? '' : gasto.cuentaId })}
              />
            </Campo>
            {gasto.formaPago === 'TRANSFERENCIA' && (
              <Campo etiqueta="Desde la cuenta" requerido error={mostrar('cuenta')}>
                <select className={estilos.select} value={gasto.cuentaId}
                  onChange={(e) => cambiar({ cuentaId: e.target.value })}>
                  <option value="">Elige la cuenta…</option>
                  {carga.cuentas.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
                </select>
              </Campo>
            )}
          </div>
        )}

        {pideConfirmar && (
          <AvisoConfirmarMonto enviando={enviando} onRevisar={() => setPideConfirmar(false)}
            onConfirmar={() => registrar(true)} />
        )}
        {error && <p className={estilos.error} role="alert"><span aria-hidden>⚠</span> {error}</p>}
      </div>
    </Modal>
  )
}
