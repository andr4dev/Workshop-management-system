import { useCallback, useEffect, useRef, useState } from 'react'
import AvisoCarga from '../componentes/AvisoCarga'
import Boton from '../componentes/Boton'
import BuscadorVenta from '../componentes/venta/BuscadorVenta'
import Catalogo from '../componentes/venta/Catalogo'
import ModalAbrirTurno from '../componentes/venta/ModalAbrirTurno'
import ModalCobro from '../componentes/venta/ModalCobro'
import ModalDescuento from '../componentes/venta/ModalDescuento'
import ModalTicket from '../componentes/venta/ModalTicket'
import PestanasVenta from '../componentes/venta/PestanasVenta'
import RenglonVenta from '../componentes/venta/RenglonVenta'
import { useSesion } from '../componentes/sesion/contexto'
import { repuestosApi, tiendaApi, turnosApi, ventasApi } from '../api/cliente'
import { fechaHora, formatoCOP } from '../utils/formato'
import { esteEquipoEsElMostrador, imprimirHtml } from '../utils/imprimir'
import { armarTicket, htmlDelTicket, problemasDelTicket } from '../utils/ticket'
import { esTurnoAjeno, textoDeTurnoAjeno } from '../utils/permisos'
import {
  agregarRenglon, aplicarProblemas, cambiarCantidad, CLAVE_BORRADOR, claveDelBorrador, comandoDeCobro, consultaDePerdida,
  problemaParaAgregar, problemasDeLaVenta, quitarRenglon, refrescarRenglones, restaurarVenta, serializarVenta,
  textoDePerdida, totalesDe, unidadesDe, ventaAlVolver, ventaNueva,
} from '../utils/venta'
import comun from './Listado.module.css'
import estilos from './Vender.module.css'

/** Lee el borrador guardado sin reventar si el navegador bloquea el almacenamiento. */
function leerBorrador(clave) {
  try {
    return restaurarVenta(localStorage.getItem(clave))
  } catch {
    return null
  }
}

function almacenamientoDisponible() {
  try {
    localStorage.setItem(`${CLAVE_BORRADOR}:prueba`, '1')
    localStorage.removeItem(`${CLAVE_BORRADOR}:prueba`)
    return true
  } catch {
    return false
  }
}

/**
 * La venta de mostrador (spec 0003).
 *
 * <p>Primero teclado: el cursor vive en el buscador y vuelve solo después de agregar. **F4** abre el
 * descuento y **F9** el cobro. Sin turno abierto no se vende (RF-002).
 *
 * <p>La venta que se arma se guarda en el navegador con cada cambio (RF-028), **a nombre de quien entró** (spec
 * 0004, RF-021). Si se cierra la pestaña o se va la luz, al volver **se retoma sola**, armada como estaba;
 * *Cancelar venta* la descarta. Otra persona que entra en el mismo equipo no la ve. Mientras no se cobre no tiene
 * número ni mueve stock.
 *
 * <p>Si al cobrar se cae la red, no se sabe si quedó cobrada: la venta se bloquea y solo se puede
 * reintentar ese mismo cobro, con su misma llave. El servidor devuelve la venta si ya existía, así que
 * reintentar nunca cobra dos veces.
 *
 * <p>Al cobrar en el computador del mostrador, el comprobante sale solo por la ticketera (RF-020). La
 * impresión nunca deshace ni frena la venta: si falla, se avisa y se reimprime (RF-021).
 *
 * <p>El turno es de quien lo abrió (spec 0004, decisión 2): con el turno de otra persona abierto, un cajero ve de
 * quién es y el catálogo, pero no vende. El aviso de venta a pérdida lo calcula el servidor: el cajero no recibe
 * costos. Se pregunta medio segundo después del último cambio, no con cada tecla.
 *
 * <p>**F2** abre el catálogo (spec 0005) en el lugar del buscador y los renglones: el total y *Cobrar*
 * siguen a la derecha. No es un modal a propósito: el cajero ve cómo sube el total mientras agrega.
 */
export default function Vender() {
  // ── Turno ──────────────────────────────────────────────────────────────────
  const [intentoTurno, setIntentoTurno] = useState(0)
  const [estadoTurno, setEstadoTurno] = useState({ intento: null, turno: null, error: null })
  const [abriendoTurno, setAbriendoTurno] = useState(false)

  useEffect(() => {
    let vigente = true
    turnosApi.abierto()
      .then((turno) => { if (vigente) setEstadoTurno({ intento: intentoTurno, turno, error: null }) })
      .catch((error) => { if (vigente) setEstadoTurno({ intento: intentoTurno, turno: null, error }) })
    return () => { vigente = false }
  }, [intentoTurno])

  const cargandoTurno = estadoTurno.intento !== intentoTurno
  const { turno } = estadoTurno
  const recargarTurno = useCallback(() => setIntentoTurno((n) => n + 1), [])

  // ── Venta ──────────────────────────────────────────────────────────────────
  // RF-028: la venta que quedó sin terminar se retoma sola, sin preguntar. Lo guardado se lee una sola vez: al
  // cambiar de persona se sale del sistema, y Vender vuelve a montarse con la clave de la que entra.
  const { usuario } = useSesion()
  const clave = claveDelBorrador(usuario.id)
  const [alVolver] = useState(() => ventaAlVolver(leerBorrador(clave)))
  const [venta, setVenta] = useState(alVolver.venta)
  const [sePuedeGuardar] = useState(almacenamientoDisponible)
  const [aviso, setAviso] = useState(alVolver.aviso)
  const [cobrada, setCobrada] = useState(null)
  const [descontando, setDescontando] = useState(false)
  const [cobrando, setCobrando] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [errorCobro, setErrorCobro] = useState(null)
  const [impresion, setImpresion] = useState({ ventaId: null, estado: null })
  const [viendoTicket, setViendoTicket] = useState(false)
  const [viendoCatalogo, setViendoCatalogo] = useState(false)
  const buscador = useRef(null)

  const totales = totalesDe(venta)
  const problemas = problemasDeLaVenta(venta)
  const bloqueada = venta.enviada
  const ajeno = esTurnoAjeno(usuario, turno)
  const puedeCobrar = Boolean(turno) && !ajeno && problemas.length === 0 && !bloqueada

  // El aviso de pérdida (spec 0004, RF-011). Se conserva el último mientras llega el nuevo: así no parpadea con
  // cada unidad que se suma. Si el servidor no responde, no hay aviso: el cobro no depende de él.
  const consulta = JSON.stringify(consultaDePerdida(venta))
  const [avisoPerdida, setAvisoPerdida] = useState(null)
  useEffect(() => {
    if (!turno || ajeno || venta.renglones.length === 0) return
    let vigente = true
    const espera = setTimeout(() => {
      ventasApi.avisoDePerdida(JSON.parse(consulta))
        .then((aviso) => { if (vigente) setAvisoPerdida(aviso) })
        .catch(() => { if (vigente) setAvisoPerdida(null) })
    }, 500)
    return () => { vigente = false; clearTimeout(espera) }
  }, [consulta, turno, ajeno, venta.renglones.length])
  const perdida = venta.renglones.length > 0 && !bloqueada ? avisoPerdida : null

  // Se guarda con cada cambio. Como lo guardado ya se retomó al abrir, guardar nunca pisa una venta pendiente.
  useEffect(() => {
    if (!sePuedeGuardar) return
    try {
      if (venta.renglones.length > 0) localStorage.setItem(clave, serializarVenta(venta))
      else localStorage.removeItem(clave)
    } catch {
      // Sin almacenamiento la venta sigue funcionando; solo no sobrevive a cerrar la pestaña.
    }
  }, [venta, sePuedeGuardar, clave])

  // RF-029: precio y stock pudieron cambiar mientras la venta estaba guardada. Una que se mandó a cobrar no se
  // revisa (ver ventaAlVolver), y si mientras llegan las fichas el cajero la cobra, tampoco se toca.
  useEffect(() => {
    if (!alVolver.revisarPrecios) return
    let vigente = true
    Promise.all(alVolver.venta.renglones.map((r) => repuestosApi.ficha(r.varianteId).catch(() => null)))
      .then((fichas) => {
        if (!vigente) return
        setVenta((v) => (v.enviada ? v : { ...v, renglones: refrescarRenglones(v.renglones, fichas) }))
        if (fichas.includes(null)) setAviso('No se pudo revisar si cambiaron todos los precios. Se revisan al cobrar.')
      })
    return () => { vigente = false }
  }, [alVolver])

  // Volver al buscador es un pedido que se cumple en un efecto, no con un setTimeout: el efecto corre
  // después de pintar (con el buscador ya habilitado si la venta estaba bloqueada) y después de que el
  // modal que se cerró devuelva el foco a donde lo tenía, así que el último en mover el cursor es este.
  const [pedidoDeFoco, setPedidoDeFoco] = useState(0)
  useEffect(() => {
    if (pedidoDeFoco) buscador.current?.focus()
  }, [pedidoDeFoco])
  const enfocarBuscador = () => setPedidoDeFoco((n) => n + 1)

  // Al cerrar el catálogo, el cursor vuelve al buscador de la venta.
  const cerrarCatalogo = useCallback(() => {
    setViendoCatalogo(false)
    setPedidoDeFoco((n) => n + 1)
  }, [])

  function agregar(repuesto) {
    const problema = problemaParaAgregar(repuesto, venta.renglones)
    if (problema) return problema
    setVenta((v) => ({ ...v, renglones: agregarRenglon(v.renglones, repuesto) }))
    setCobrada(null)
    setAviso(null)
    return null
  }

  function cancelarVenta() {
    setVenta(ventaNueva())
    setAviso(null)
    enfocarBuscador()
  }

  /**
   * El comprobante de la venta recién cobrada, con las cifras que devolvió el servidor. En un celular o
   * una tablet no se imprime solo (se abriría el diálogo en cada venta): se reimprime desde el mostrador.
   * Un ticket que no cuadra no sale solo; se ve en "Ver comprobante" con el aviso.
   *
   * @param siempre Reimprimir: lo pidió el cajero, así que se imprime en cualquier equipo
   */
  async function imprimirComprobante(vendida, { siempre = false } = {}) {
    if (!siempre && !esteEquipoEsElMostrador()) {
      setImpresion({ ventaId: vendida.id, estado: 'OTRO_EQUIPO' })
      return
    }
    setImpresion({ ventaId: vendida.id, estado: 'IMPRIMIENDO' })
    let estado
    try {
      const ticket = armarTicket(vendida, await tiendaApi.obtener())
      if (problemasDelTicket(ticket).length > 0) estado = 'NO_CUADRA'
      else estado = await imprimirHtml(htmlDelTicket(ticket)) ? 'ENVIADO' : 'FALLO'
    } catch {
      estado = 'FALLO'
    }
    // Si mientras tanto se cobró otra, este resultado ya no es el que se muestra.
    setImpresion((actual) => (actual.ventaId === vendida.id ? { ventaId: vendida.id, estado } : actual))
  }

  async function cobrar(cobro) {
    const comando = comandoDeCobro(venta, cobro)
    // Se marca ANTES de mandar: si la pestaña se cierra o la red se cae en el medio, lo guardado dice
    // que este cobro quedó sin respuesta y con qué llave reintentarlo.
    setVenta((v) => ({ ...v, enviada: true, cobro }))
    setEnviando(true)
    setErrorCobro(null)
    try {
      const resultado = await ventasApi.cobrar(comando)
      setVenta(ventaNueva())
      setCobrada(resultado)
      setCobrando(false)
      // Cobrada, se vuelve a la venta: el siguiente cliente arranca en el buscador.
      setViendoCatalogo(false)
      setAviso(null)
      enfocarBuscador()
      // Sin await: la venta ya está cobrada y el cajero sigue con el siguiente cliente mientras imprime.
      imprimirComprobante(resultado)
    } catch (e) {
      if (e.estado === 0) {
        // Sin respuesta: queda bloqueada y marcada como enviada. Solo se reintenta lo mismo.
        return
      }
      // Respuesta definitiva: la venta NO se cobró. Se desbloquea para corregirla.
      setVenta((v) => ({
        ...v,
        enviada: false,
        renglones: e.cuerpo?.problemas ? aplicarProblemas(v.renglones, e.cuerpo.problemas) : v.renglones,
      }))
      if (e.cuerpo?.problemas) {
        setCobrando(false)
        setAviso('No se cobró: revisa los renglones marcados.')
      } else {
        setErrorCobro(e.message)
        if (/turno/i.test(e.message)) recargarTurno()
      }
    } finally {
      setEnviando(false)
    }
  }

  const abrirCobro = useCallback(() => {
    if (puedeCobrar || bloqueada) setCobrando(true)
  }, [puedeCobrar, bloqueada])
  const abrirDescuento = useCallback(() => {
    if (turno && !ajeno && venta.renglones.length > 0 && !bloqueada) setDescontando(true)
  }, [turno, ajeno, venta.renglones.length, bloqueada])
  const cerrarCobro = useCallback(() => { if (!enviando) setCobrando(false) }, [enviando])
  const cerrarDescuento = useCallback(() => setDescontando(false), [])

  // Atajos visibles en pantalla (skill frontend): F2 catálogo, F4 descuento, F9 cobrar. F4 y F9 abren su
  // modal encima del catálogo, sin cerrarlo: al cancelar, el cursor vuelve a donde estaba.
  useEffect(() => {
    function alTeclear(e) {
      if (cobrando || descontando || abriendoTurno || viendoTicket) return
      if (e.key === 'F9') { e.preventDefault(); abrirCobro() }
      if (e.key === 'F4') { e.preventDefault(); abrirDescuento() }
      if (e.key === 'F2') {
        e.preventDefault()
        if (viendoCatalogo) cerrarCatalogo()
        else setViendoCatalogo(true)
      }
    }
    window.addEventListener('keydown', alTeclear)
    return () => window.removeEventListener('keydown', alTeclear)
  }, [cobrando, descontando, abriendoTurno, viendoTicket, viendoCatalogo, abrirCobro, abrirDescuento, cerrarCatalogo])

  // ── Pantalla ───────────────────────────────────────────────────────────────
  const estadoImpresion = cobrada && impresion.ventaId === cobrada.id ? impresion.estado : null

  return (
    <div className={comun.pagina}>
      <PestanasVenta />

      <header className={comun.encabezado}>
        <div>
          <h1 className={comun.titulo}>Vender</h1>
          <p className={comun.subtitulo}>Enter agrega · F2 catálogo · F4 descuento · F9 cobrar</p>
        </div>
      </header>

      {cargandoTurno && !turno && <p className={comun.vacio}>Cargando turno…</p>}
      {!cargandoTurno && estadoTurno.error && <AvisoCarga error={estadoTurno.error} onReintentar={recargarTurno} />}

      {!cargandoTurno && !estadoTurno.error && !turno && (viendoCatalogo ? (
        // Sin turno el catálogo se ve pero no agrega (spec 0005, RF-008): el cliente puede preguntar qué
        // hay antes de que se abra la caja.
        <Catalogo onAgregar={agregar} onCerrar={cerrarCatalogo} bloqueado
          porQueNo="No hay un turno abierto: el catálogo se puede ver, pero para vender hay que abrir el turno." />
      ) : (
        <div className={comun.vacio}>
          <p>
            <strong>No hay un turno abierto.</strong> Para vender, abre el turno con la plata con que
            arranca el cajón.
          </p>
          <span className={estilos.accionesSinTurno}>
            <Boton variante="primario" onClick={() => setAbriendoTurno(true)}>Abrir turno</Boton>
            <Boton variante="secundario" onClick={() => setViendoCatalogo(true)}>
              Ver catálogo <kbd className={estilos.tecla}>F2</kbd>
            </Boton>
          </span>
        </div>
      ))}

      {turno && ajeno && (viendoCatalogo ? (
        <Catalogo onAgregar={agregar} onCerrar={cerrarCatalogo} bloqueado porQueNo={textoDeTurnoAjeno(turno)} />
      ) : (
        <div className={comun.vacio} role="status">
          <p><strong>{textoDeTurnoAjeno(turno)}.</strong></p>
          <span className={estilos.accionesSinTurno}>
            <Boton variante="secundario" onClick={() => setViendoCatalogo(true)}>
              Ver catálogo <kbd className={estilos.tecla}>F2</kbd>
            </Boton>
          </span>
        </div>
      ))}

      {turno && !ajeno && (
        <>
          <section className={estilos.turno} aria-label="Turno abierto">
            <span className={estilos.turnoEstado}>Turno abierto</span>
            {turno.abiertoPor && <span>de {turno.abiertoPor.nombre}</span>}
            <span>desde {fechaHora(turno.abiertoEn)}</span>
            <span className={estilos.turnoFondo}>Fondo {formatoCOP(turno.fondo)}</span>
          </section>

          {cobrada && (
            <div className={estilos.cobrada} role="status">
              <span>
                <strong>Venta N.º {cobrada.numero} cobrada</strong> · Total {formatoCOP(cobrada.total)}
              </span>
              {cobrada.cambio > 0 && (
                <span className={estilos.cobradaCambio}>Cambio {formatoCOP(cobrada.cambio)}</span>
              )}
              {cobrada.fiado > 0 && (
                <span className={estilos.cobradaFiado}>
                  Fiado {formatoCOP(cobrada.fiado)} a {cobrada.cliente?.nombre}
                  {cobrada.debeDespues != null && ` · debe ${formatoCOP(cobrada.debeDespues)} en total`}
                </span>
              )}
              <span className={estilos.cobradaAcciones}>
                <Boton variante="secundario" tamano="chico" onClick={() => setViendoTicket(true)}>Ver comprobante</Boton>
                <Boton variante="fantasma" tamano="chico" disabled={estadoImpresion === 'IMPRIMIENDO'}
                  onClick={() => { imprimirComprobante(cobrada, { siempre: true }); enfocarBuscador() }}>
                  {/* En un celular o tablet no se imprimió al cobrar: no es "re". */}
                  {estadoImpresion === 'OTRO_EQUIPO' ? 'Imprimir' : 'Reimprimir'}
                </Boton>
              </span>
              <button type="button" className={estilos.cerrarAviso} onClick={() => setCobrada(null)} aria-label="Cerrar aviso">×</button>
              <AvisoImpresion estado={estadoImpresion} />
            </div>
          )}

          {bloqueada && !cobrando && (
            <div className={estilos.sinRespuesta} role="alert">
              <span><span aria-hidden>⚠</span> Esta venta se mandó a cobrar y no hubo respuesta. No se sabe si quedó cobrada.</span>
              <Boton variante="primario" tamano="chico" onClick={() => setCobrando(true)}>Reintentar cobro</Boton>
            </div>
          )}

          {!sePuedeGuardar && (
            <p className={estilos.nota}>Este navegador no deja guardar la venta: si se cierra la pestaña, se pierde.</p>
          )}

          <div className={estilos.mostrador}>
            <section className={estilos.venta} aria-label="Venta en curso">
              {viendoCatalogo ? (
                <Catalogo
                  onAgregar={agregar}
                  onCerrar={cerrarCatalogo}
                  bloqueado={bloqueada}
                  porQueNo={bloqueada ? 'Esta venta se mandó a cobrar y no hubo respuesta: primero reintenta el cobro.' : null}
                />
              ) : (
              <>
              <div className={estilos.buscarFila}>
                <BuscadorVenta ref={buscador} onAgregar={agregar} deshabilitado={bloqueada} />
                <Boton variante="secundario" className={estilos.botonCatalogo} onClick={() => setViendoCatalogo(true)}>
                  Catálogo <kbd className={estilos.tecla}>F2</kbd>
                </Boton>
              </div>

              {aviso && <p className={estilos.aviso} role="alert">{aviso}</p>}

              {venta.renglones.length === 0 ? (
                <p className={estilos.vacia}>Escribe el código o el nombre del repuesto y presiona Enter.</p>
              ) : (
                <ul className={estilos.renglones} aria-label="Repuestos de la venta">
                  {venta.renglones.map((r) => (
                    <RenglonVenta
                      key={r.varianteId}
                      renglon={r}
                      bloqueado={bloqueada}
                      onCantidad={(texto) => setVenta((v) => ({ ...v, renglones: cambiarCantidad(v.renglones, r.varianteId, texto) }))}
                      onQuitar={() => { setVenta((v) => ({ ...v, renglones: quitarRenglon(v.renglones, r.varianteId) })); enfocarBuscador() }}
                    />
                  ))}
                </ul>
              )}
              </>
              )}
            </section>

            <aside className={estilos.resumen} aria-label="Total de la venta">
              <dl className={estilos.cifras}>
                <div><dt>{unidadesDe(venta.renglones)} {unidadesDe(venta.renglones) === 1 ? 'unidad' : 'unidades'}</dt><dd>{formatoCOP(totales.subtotal)}</dd></div>
                {venta.descuento && (
                  <div className={estilos.descuento}>
                    <dt>
                      Descuento{venta.descuento.modo === 'PORCENTAJE' ? ` (${venta.descuento.valor}%)` : ''}
                      <span className={estilos.motivo}>{venta.descuento.motivo}</span>
                    </dt>
                    <dd>− {formatoCOP(totales.descuento)}</dd>
                  </div>
                )}
                <div className={estilos.total}><dt>Total</dt><dd>{formatoCOP(totales.total)}</dd></div>
              </dl>

              {perdida && (
                <p className={estilos.perdida}>
                  <span aria-hidden>⚠</span> {textoDePerdida(perdida)}
                </p>
              )}

              <Boton variante="primario" tamano="grande" className={estilos.botonCobrar}
                onClick={abrirCobro} disabled={!puedeCobrar && !bloqueada}>
                {bloqueada ? 'Reintentar cobro' : 'Cobrar'} <kbd className={estilos.tecla}>F9</kbd>
              </Boton>
              <Boton variante="secundario" onClick={abrirDescuento}
                disabled={venta.renglones.length === 0 || bloqueada}>
                {venta.descuento ? 'Cambiar descuento' : 'Descuento'} <kbd className={estilos.tecla}>F4</kbd>
              </Boton>
              {venta.renglones.length > 0 && !bloqueada && (
                <Boton variante="fantasma" onClick={cancelarVenta}>Cancelar venta</Boton>
              )}
            </aside>
          </div>
        </>
      )}

      {abriendoTurno && (
        <ModalAbrirTurno
          abierto
          onCerrar={() => setAbriendoTurno(false)}
          onAbierto={(nuevo) => { setEstadoTurno({ intento: intentoTurno, turno: nuevo, error: null }); setAbriendoTurno(false) }}
          onYaHabiaUno={() => { setAbriendoTurno(false); recargarTurno() }}
        />
      )}

      {descontando && (
        <ModalDescuento
          subtotal={totales.subtotal}
          actual={venta.descuento}
          onCerrar={cerrarDescuento}
          onAplicar={(descuento) => {
            setVenta((v) => ({ ...v, descuento }))
            setDescontando(false)
            enfocarBuscador()
          }}
        />
      )}

      {viendoTicket && cobrada && (
        <ModalTicket venta={cobrada} onCerrar={() => { setViendoTicket(false); enfocarBuscador() }} />
      )}

      {cobrando && (
        <ModalCobro
          total={totales.total}
          cobroAnterior={venta.cobro}
          enviando={enviando}
          error={errorCobro}
          sinRespuesta={bloqueada && !enviando}
          onCobrar={cobrar}
          onCerrar={cerrarCobro}
        />
      )}
    </div>
  )
}

/** Qué pasó con el comprobante de la venta recién cobrada. Nada mientras sale bien. */
function AvisoImpresion({ estado }) {
  const avisos = {
    IMPRIMIENDO: [estilos.impresion, 'Imprimiendo comprobante…'],
    ENVIADO: [estilos.impresion, 'Comprobante enviado a la impresora.'],
    OTRO_EQUIPO: [estilos.impresion, 'El comprobante se imprime desde el computador del mostrador, en Ventas del turno.'],
    FALLO: [estilos.impresionFallo, '⚠ No se pudo imprimir el comprobante. La venta quedó cobrada: usa Reimprimir.'],
    NO_CUADRA: [estilos.impresionFallo, '⚠ El comprobante no cuadra y no se imprimió. Revísalo en Ver comprobante.'],
  }
  if (!avisos[estado]) return null
  const [clase, texto] = avisos[estado]
  return <p className={clase} role={clase === estilos.impresionFallo ? 'alert' : undefined}>{texto}</p>
}
