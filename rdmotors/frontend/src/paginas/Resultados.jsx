import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import AvisoCarga from '../componentes/AvisoCarga'
import PestanasReportes from '../componentes/reportes/PestanasReportes'
import SelectorPeriodo from '../componentes/reportes/SelectorPeriodo'
import CifraGrande from '../componentes/reportes/CifraGrande'
import PanelCalculo from '../componentes/reportes/PanelCalculo'
import TablaPorDia from '../componentes/reportes/TablaPorDia'
import GraficaPorDia from '../componentes/reportes/GraficaPorDia'
import SinCosto from '../componentes/reportes/SinCosto'
import AyudaDe from '../componentes/reportes/AyudaDe'
import RepuestosDelPeriodo from '../componentes/reportes/RepuestosDelPeriodo'
import CategoriasDeRepuesto from '../componentes/reportes/CategoriasDeRepuesto'
import GastosYControl from '../componentes/reportes/GastosYControl'
import InventarioYCompras from '../componentes/reportes/InventarioYCompras'
import { reportesApi } from '../api/cliente'
import { formatoCOP, GUION } from '../utils/formato'
import {
  hoyEnColombia, periodoDesdeUrl, problemaDelPeriodo, textoSinMovimientos, tituloDelPeriodo, urlDelPeriodo,
} from '../utils/periodo'
import {
  avisoDeGastosDelMes, calculoDe, CIFRAS, columnasQueNoCuadran, conComa, consultaDeResultados, hayMovimientos,
  pagosCuadran, porcentajeDeDescuentos, textoDeVariacion, textoSinCosto, variacion,
} from '../utils/resultados'
import comun from './Listado.module.css'
import estilos from '../componentes/reportes/Resultados.module.css'

const COLUMNAS = {
  ventas: 'ventas', ventasNetas: 'ventas netas', costoVendido: 'costo vendido', costosAdicionales: 'costos adicionales',
  utilidadBruta: 'utilidad bruta', gastos: 'gastos', utilidadOperativa: 'utilidad operativa',
}

/**
 * Reportes › Resultados (spec 0007): cuánto se vendió, cuánto dejó la mercancía y cuánto se ganó en un período.
 *
 * Las cifras llegan hechas del servidor, de un solo cálculo; aquí se muestran y se comprueba que cuadren. El período
 * vive en la dirección: un enlace o volver atrás deja el mismo reporte.
 *
 * Mientras llega un período nuevo se ve el anterior, atenuado. Si falla, no queda a la vista: unas cifras de otro
 * período se leerían como las de este.
 *
 * El orden es el de RF-027: las cuatro cifras grandes con su comparación, las que importan, el día por día, y lo de
 * P2: repuestos, categorías, gastos por categoría y control. Al final, lo de P3: el inventario de hoy y lo comprado.
 */
export default function Resultados() {
  const [params, setParams] = useSearchParams()
  const hoy = hoyEnColombia()
  const periodo = periodoDesdeUrl(params, hoy)
  const problema = problemaDelPeriodo(periodo, hoy)
  const [intento, setIntento] = useState(0)
  const [panel, setPanel] = useState(null)   // una de CIFRAS | 'SIN_COSTO'

  const clave = problema ? null : JSON.stringify({ consulta: consultaDeResultados(periodo), intento })
  const [carga, setCarga] = useState({ clave: null, datos: null, error: null })

  useEffect(() => {
    if (!clave) return
    let vigente = true
    reportesApi.resultados(JSON.parse(clave).consulta)
      .then((datos) => { if (vigente) setCarga({ clave, datos, error: null }) })
      .catch((error) => { if (vigente) setCarga({ clave, datos: null, error }) })
    return () => { vigente = false }
  }, [clave])

  function cambiar(nuevo, { reemplazar = false } = {}) {
    setPanel(null)
    setParams(urlDelPeriodo(nuevo), { replace: reemplazar })
  }

  const cargando = clave != null && carga.clave !== clave
  const r = problema ? null : carga.datos

  return (
    <div className={comun.pagina}>
      <PestanasReportes />

      <header className={comun.encabezado}>
        <div>
          <h1 className={comun.titulo}>Resultados</h1>
          <p className={comun.subtitulo}>Cuánto se vendió, cuánto dejó la mercancía y cuánto se ganó.</p>
        </div>
      </header>

      <SelectorPeriodo periodo={periodo} hoy={hoy} problema={problema} onCambio={cambiar} />

      <AvisoCarga error={cargando ? null : carga.error} onReintentar={() => setIntento((n) => n + 1)} />
      {!r && cargando && <p className={comun.vacio}>Calculando los resultados…</p>}

      {r && !hayMovimientos(r) && (
        <div className={`${comun.vacio} ${cargando ? estilos.atenuado : ''}`}>
          <p>{textoSinMovimientos(r)}.</p>
        </div>
      )}
      {r && hayMovimientos(r) && <Reporte r={r} tipo={periodo.tipo} cargando={cargando} onPanel={setPanel} />}

      {/* P3: datos aparte, con sus propias consultas; se ven aunque el reporte no haya cargado. */}
      <InventarioYCompras periodo={problema ? null : periodo} />

      {r && panel === 'SIN_COSTO' && <SinCosto sinCosto={r.sinCosto} onCerrar={() => setPanel(null)} />}
      {r && panel && panel !== 'SIN_COSTO' && <PanelCalculo calculo={calculoDe(panel, r)} onCerrar={() => setPanel(null)} />}
    </div>
  )
}

function Reporte({ r, tipo, cargando, onPanel }) {
  const c = r.cifras
  /** Frente al período anterior (RF-022). Que suban los gastos es malo. */
  const comparar = (clave, subirEsBueno = true) => {
    const v = variacion(c[clave], r.anterior.cifras[clave])
    const tono = v.direccion === 'IGUAL' ? 'NEUTRA' : (v.direccion === 'SUBE') === subirEsBueno ? 'BUENA' : 'MALA'
    return { texto: textoDeVariacion(v, formatoCOP), tono }
  }
  const hayRenglonesSinCosto = r.sinCosto.renglones > 0
  const avisoDelMes = avisoDeGastosDelMes(r, formatoCOP)
  const noCuadran = columnasQueNoCuadran(r)
  const cuadra = (cifra) => calculoDe(cifra, r).cuadra
  const descuentos = porcentajeDeDescuentos(c)
  const atenuado = cargando ? estilos.atenuado : ''

  return (
    <div aria-busy={cargando}>
      <section className={`${estilos.franja} ${atenuado}`} aria-label="Las cifras del período">
        <CifraGrande ayuda="ventasNetas" etiqueta="Ventas netas" valor={c.ventasNetas}
          nota={`${c.ventas} ${c.ventas === 1 ? 'venta' : 'ventas'} · ${c.unidades} ${c.unidades === 1 ? 'unidad' : 'unidades'}`}
          cuadra={cuadra(CIFRAS.VENTAS_NETAS) && pagosCuadran(c)} variacion={comparar('ventasNetas')}
          onVerCalculo={() => onPanel(CIFRAS.VENTAS_NETAS)} />
        <CifraGrande ayuda="utilidadBruta" etiqueta="Utilidad bruta" valor={c.utilidadBruta} margen={c.margenBruto}
          asterisco={hayRenglonesSinCosto} nota="Lo que dejó la mercancía"
          cuadra={cuadra(CIFRAS.UTILIDAD_BRUTA)} variacion={comparar('utilidadBruta')}
          onVerCalculo={() => onPanel(CIFRAS.UTILIDAD_BRUTA)} />
        <CifraGrande ayuda="gastos" etiqueta="Gastos del local" valor={c.gastos}
          nota={c.costosAdicionales
            ? `Más ${formatoCOP(c.costosAdicionales)} de costos adicionales, que restan en la utilidad bruta`
            : 'Del cajón y por fuera'}
          cuadra={cuadra(CIFRAS.GASTOS)} variacion={comparar('gastos', false)} onVerCalculo={() => onPanel(CIFRAS.GASTOS)} />
        <CifraGrande destacada ayuda="utilidadOperativa" etiqueta="Utilidad operativa" valor={c.utilidadOperativa}
          margen={c.margenOperativo} asterisco={hayRenglonesSinCosto} nota="La ganancia del período"
          cuadra={cuadra(CIFRAS.UTILIDAD_OPERATIVA)} variacion={comparar('utilidadOperativa')}
          onVerCalculo={() => onPanel(CIFRAS.UTILIDAD_OPERATIVA)} />
      </section>
      <p className={`${estilos.comparacion} ${atenuado}`}>
        Las flechas comparan con {tituloDelPeriodo({ tipo, desde: r.anterior.desde, hasta: r.anterior.hasta })}.
      </p>

      {(hayRenglonesSinCosto || avisoDelMes || !pagosCuadran(c)) && (
        <div className={`${estilos.avisos} ${atenuado}`}>
          {hayRenglonesSinCosto && (
            <p className={estilos.avisoSinCosto}>
              <span><span aria-hidden>*</span> {textoSinCosto(r.sinCosto.renglones)}.</span>
              <button type="button" className={estilos.enlaceBoton} onClick={() => onPanel('SIN_COSTO')}>¿Cuáles?</button>
            </p>
          )}
          {avisoDelMes && <p className={estilos.aviso}>{avisoDelMes} <AyudaDe clave="gastosDelMes" /></p>}
          {!pagosCuadran(c) && (
            <p className={estilos.descuadre} role="alert">
              <span aria-hidden>⚠</span> Efectivo, transferencia y fiado suman{' '}
              {formatoCOP(c.efectivo + c.transferencia + (c.fiado ?? 0))} y las ventas netas son {formatoCOP(c.ventasNetas)}.
            </p>
          )}
        </div>
      )}

      <h2 className={estilos.seccion}>Las cifras que importan</h2>
      <section className={`${estilos.importantes} ${atenuado}`} aria-label="Las cifras que importan">
        <div className={estilos.tarjeta}>
          <span className={estilos.etiqueta}>Ventas</span>
          <span className={estilos.valorChico}>{c.ventas}</span>
          <p className={estilos.nota}>{c.unidades} {c.unidades === 1 ? 'unidad vendida' : 'unidades vendidas'}</p>
        </div>
        <div className={estilos.tarjeta}>
          <span className={estilos.etiqueta}>Ticket promedio <AyudaDe clave="ticket" /></span>
          <span className={estilos.valorChico}>{c.ticketPromedio == null ? GUION : formatoCOP(c.ticketPromedio)}</span>
          <p className={estilos.nota}>Lo que dejó cada venta</p>
        </div>
        <div className={estilos.tarjeta}>
          <span className={estilos.etiqueta}>Descuentos <AyudaDe clave="descuentos" /></span>
          <span className={estilos.valorChico}>{formatoCOP(c.descuentos)}</span>
          <p className={estilos.nota}>
            {c.ventasConDescuento
              ? `En ${c.ventasConDescuento} ${c.ventasConDescuento === 1 ? 'venta' : 'ventas'} · ${conComa(descuentos)} % de lo vendido`
              : 'No se dieron descuentos'}
          </p>
        </div>
        {/* El fiado y la cartera (spec 0008, RF-024): lo vendido fiado es de este período; lo por cobrar, de hoy. */}
        {(c.fiado > 0 || r.cartera?.porCobrar > 0 || r.cartera?.cobrado > 0) && (
          <div className={estilos.tarjeta}>
            <span className={estilos.etiqueta}>Fiado y cartera</span>
            <dl className={estilos.pagos}>
              <dt>Vendido fiado</dt><dd>{formatoCOP(c.fiado)}</dd>
              <dt>Cobrado en abonos</dt><dd>{formatoCOP(r.cartera?.cobrado ?? 0)}</dd>
              <dt>Por cobrar hoy</dt><dd>{formatoCOP(r.cartera?.porCobrar ?? 0)}</dd>
            </dl>
            <p className={estilos.nota}>
              {r.cartera?.clientesQueDeben
                ? `${r.cartera.clientesQueDeben} ${r.cartera.clientesQueDeben === 1 ? 'cliente debe' : 'clientes deben'} hoy · `
                : ''}
              Un abono es un cobro, no una venta
            </p>
          </div>
        )}

        <div className={estilos.tarjeta}>
          <span className={estilos.etiqueta}>Cómo pagaron <AyudaDe clave="pagos" /></span>
          <dl className={estilos.pagos}>
            <dt>Efectivo</dt><dd>{formatoCOP(c.efectivo)}</dd>
            <dt>Transferencia</dt><dd>{formatoCOP(c.transferencia)}</dd>
            {/* Lo fiado es venta del día en que se vendió, aunque todavía no haya entrado (spec 0008). */}
            {c.fiado > 0 && <><dt>Fiado</dt><dd>{formatoCOP(c.fiado)}</dd></>}
          </dl>
          {c.ventasNetas > 0 && (
            <div className={estilos.proporcion} aria-hidden>
              <span className={estilos.proporcionEfectivo} style={{ width: `${(c.efectivo / c.ventasNetas) * 100}%` }} />
              <span className={estilos.proporcionTransferencia} style={{ width: `${(c.transferencia / c.ventasNetas) * 100}%` }} />
              {c.fiado > 0 && (
                <span className={estilos.proporcionFiado} style={{ width: `${(c.fiado / c.ventasNetas) * 100}%` }} />
              )}
            </div>
          )}
        </div>
      </section>

      <h2 className={estilos.seccion}>
        {r.agrupacion === 'SEMANA' ? 'Semana por semana' : 'Día por día'} <AyudaDe clave="diaPorDia" />
      </h2>
      {noCuadran.length > 0 && (
        <p className={estilos.descuadre} role="alert" style={{ marginBottom: 'var(--esp-3)' }}>
          <span aria-hidden>⚠</span> Las filas no suman las cifras de arriba en: {noCuadran.map((col) => COLUMNAS[col]).join(', ')}.
        </p>
      )}
      <GraficaPorDia r={r} cargando={cargando} />
      <TablaPorDia r={r} cargando={cargando} />

      {r.repuestos.length > 0 && (
        <>
          <RepuestosDelPeriodo r={r} cargando={cargando} />
          <CategoriasDeRepuesto r={r} cargando={cargando} />
        </>
      )}
      <GastosYControl r={r} cargando={cargando} />
    </div>
  )
}
