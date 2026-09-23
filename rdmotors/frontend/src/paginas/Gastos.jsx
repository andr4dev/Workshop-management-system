import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import AvisoCarga from '../componentes/AvisoCarga'
import Boton from '../componentes/Boton'
import ModalMotivo from '../componentes/ModalMotivo'
import PestanasReportes from '../componentes/reportes/PestanasReportes'
import ModalCategoriasGasto from '../componentes/caja/ModalCategoriasGasto'
import ModalGasto from '../componentes/caja/ModalGasto'
import { categoriasGastoApi, gastosApi, turnosApi } from '../api/cliente'
import { fechaDia, formatoCOP } from '../utils/formato'
import {
  consultaDeGastos, consultaDeTotalesDeGastos, filtrosDeGastos, hayFiltrosDeGastos, problemaDelRango, sePuedeAnular,
  textoDelOrigen,
} from '../utils/gastos'
import { fechaLocal, rangoDePagina } from '../utils/inventario'
import comun from './Listado.module.css'
import estilos from './Caja.module.css'

/**
 * Los gastos del negocio (spec 0006, RF-007a), en Reportes: del cajón y por fuera, por fecha y categoría, con
 * cuánto suman y cuánto salió del cajón. Es lo que después usa el reporte de ganancia (spec 0007).
 *
 * Aquí se registran, se consultan, se anulan y se administran las categorías. Registrar desde aquí es para el
 * socio, que paga cosas por fuera del cajón y las anota cuando quiera: el modal arranca en "por fuera del cajón".
 * Desde Vender › Caja se siguen registrando los del turno.
 *
 * Los filtros viven en la URL, como en el historial de compras. Los anulados siguen en la lista, tachados, y no
 * suman.
 */
export default function Gastos() {
  const [params, setParams] = useSearchParams()
  const filtros = filtrosDeGastos(params)
  const errorRango = problemaDelRango(filtros)
  const [intento, setIntento] = useState(0)

  const clave = errorRango ? null : JSON.stringify({ consulta: consultaDeGastos(filtros), intento })
  const [listado, setListado] = useState({ clave: null, datos: null, error: null })
  const claveTotales = errorRango ? null : JSON.stringify({ consulta: consultaDeTotalesDeGastos(filtros), intento })
  const [totales, setTotales] = useState({ clave: null, datos: null, error: null })

  const [categorias, setCategorias] = useState([])
  const [turnoAbierto, setTurnoAbierto] = useState(null)
  const [modal, setModal] = useState(null)   // REGISTRAR | CATEGORIAS | { anular: gasto }
  const [aviso, setAviso] = useState(null)
  const [anulando, setAnulando] = useState(false)
  const [errorAnular, setErrorAnular] = useState(null)

  const cambiar = useCallback((cambios) => {
    setParams((actual) => {
      const nuevos = new URLSearchParams(actual)
      for (const [nombre, valor] of Object.entries(cambios)) {
        if (valor) nuevos.set(nombre, valor)
        else nuevos.delete(nombre)
      }
      if (!('p' in cambios)) nuevos.delete('p')
      return nuevos
    }, { replace: true })
  }, [setParams])

  useEffect(() => {
    categoriasGastoApi.todas().then(setCategorias).catch(() => setCategorias([]))
    turnosApi.abierto().then(setTurnoAbierto).catch(() => setTurnoAbierto(null))
  }, [intento])

  useEffect(() => {
    if (!clave) return
    let vigente = true
    gastosApi.listar(JSON.parse(clave).consulta)
      .then((datos) => { if (vigente) setListado({ clave, datos, error: null }) })
      .catch((error) => { if (vigente) setListado((l) => ({ clave, datos: l.datos, error })) })
    return () => { vigente = false }
  }, [clave])

  useEffect(() => {
    if (!claveTotales) return
    let vigente = true
    gastosApi.totales(JSON.parse(claveTotales).consulta)
      .then((datos) => { if (vigente) setTotales({ clave: claveTotales, datos, error: null }) })
      // Un total que falló no se deja a la vista: una cifra de plata vieja se lee como la respuesta.
      .catch((error) => { if (vigente) setTotales({ clave: claveTotales, datos: null, error }) })
    return () => { vigente = false }
  }, [claveTotales])

  const cargando = clave != null && listado.clave !== clave
  const datos = listado.datos
  const recargar = () => setIntento((n) => n + 1)

  function registrado(texto) {
    setModal(null)
    setAviso(texto)
    recargar()
  }

  async function anular(motivo) {
    setAnulando(true)
    setErrorAnular(null)
    try {
      await gastosApi.anular(modal.anular.id, motivo)
      registrado('Gasto anulado: sigue en la lista, tachado, y ya no suma.')
    } catch (e) {
      setErrorAnular(e.message)
    } finally {
      setAnulando(false)
    }
  }

  const categoriasOrdenadas = [...categorias].sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))

  return (
    <div className={comun.pagina}>
      <PestanasReportes />

      <header className={comun.encabezado}>
        <div>
          <h1 className={comun.titulo}>Gastos</h1>
          <p className={comun.subtitulo}>
            Del cajón y por fuera, por la fecha del gasto, del más reciente al más antiguo.
          </p>
        </div>
        <div className={estilos.barra}>
          <Boton variante="secundario" onClick={() => setModal('CATEGORIAS')}>Categorías</Boton>
          <Boton variante="primario" onClick={() => setModal('REGISTRAR')}>Registrar gasto</Boton>
        </div>
      </header>

      {aviso && (
        <p className={estilos.aviso} role="status">
          {aviso}
          <Boton variante="fantasma" tamano="chico" onClick={() => setAviso(null)}>Cerrar aviso</Boton>
        </p>
      )}

      <form className={comun.filtros} onSubmit={(e) => e.preventDefault()} aria-label="Filtros de gastos">
        <div className={comun.filtro}>
          <label className={comun.etiquetaFiltro} htmlFor="gastos-desde">Desde</label>
          <input id="gastos-desde" type="date" className={`${comun.control} ${errorRango ? comun.controlError : ''}`}
            value={filtros.desde} onChange={(e) => cambiar({ desde: e.target.value })} />
        </div>
        <div className={comun.filtro}>
          <label className={comun.etiquetaFiltro} htmlFor="gastos-hasta">Hasta</label>
          <input id="gastos-hasta" type="date" className={`${comun.control} ${errorRango ? comun.controlError : ''}`}
            value={filtros.hasta} onChange={(e) => cambiar({ hasta: e.target.value })} />
        </div>
        <div className={comun.filtro}>
          <label className={comun.etiquetaFiltro} htmlFor="gastos-categoria">Categoría</label>
          <select id="gastos-categoria" className={comun.control} value={filtros.categoriaId}
            onChange={(e) => cambiar({ categoriaId: e.target.value })}>
            <option value="">Todas</option>
            {categoriasOrdenadas.map((c) => (
              <option key={c.id} value={c.id}>{c.nombre}{c.activa ? '' : ' (desactivada)'}</option>
            ))}
          </select>
        </div>
        {hayFiltrosDeGastos(filtros) && (
          <Boton variante="fantasma" tamano="chico" onClick={() => setParams({}, { replace: true })}>Quitar filtros</Boton>
        )}
        {errorRango && <p className={comun.errorFiltro} role="alert"><span aria-hidden>⚠</span> {errorRango}</p>}
      </form>

      <Totales totales={totales} clave={claveTotales} />

      <AvisoCarga error={cargando ? null : listado.error} onReintentar={recargar} desactualizado={datos != null} />
      {!datos && cargando && <p className={comun.vacio}>Cargando gastos…</p>}

      {datos && datos.elementos.length === 0 && (
        <div className={comun.vacio}>
          <p>{hayFiltrosDeGastos(filtros) ? 'No hay gastos con esos filtros.' : 'Todavía no hay gastos registrados.'}</p>
        </div>
      )}

      {datos && datos.elementos.length > 0 && (
        <>
          <div className={`scroll-x ${comun.marco} ${cargando ? comun.atenuado : ''}`} aria-busy={cargando}>
            <table className={comun.tabla}>
              <thead>
                <tr>
                  <th>Fecha</th>
                  <th>Categoría</th>
                  <th>En qué</th>
                  <th>De dónde salió</th>
                  <th className="cifra">Monto</th>
                  <th aria-label="Acciones" />
                </tr>
              </thead>
              <tbody>
                {datos.elementos.map((g) => (
                  <tr key={g.id}>
                    <td>{fechaDia(fechaLocal(g.fecha))}</td>
                    <td>
                      {g.categoria}
                      {g.naturaleza === 'COSTO' && <span className={estilos.costo}>Costo</span>}
                      {g.delMes && <span className={estilos.delMes}>Del mes</span>}
                    </td>
                    <td>
                      {g.descripcion}
                      {g.registradoPor && <span className={estilos.motivo}>Registró {g.registradoPor.nombre}</span>}
                      {g.anuladoEn && (
                        <span className={estilos.motivo}>
                          Anulado{g.anuladoPor && ` por ${g.anuladoPor.nombre}`}: {g.motivoAnulacion}
                        </span>
                      )}
                    </td>
                    <td>
                      {textoDelOrigen(g)}
                      {g.anuladoEn && <span className={comun.anulada}>Anulado</span>}
                    </td>
                    <td className="cifra"><strong className={g.anuladoEn ? comun.tachado : undefined}>{formatoCOP(g.monto)}</strong></td>
                    <td className="cifra">
                      {sePuedeAnular(g, turnoAbierto?.id) && (
                        <Boton variante="fantasma" tamano="chico"
                          onClick={() => { setErrorAnular(null); setModal({ anular: g }) }}>Anular</Boton>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <nav className={comun.paginacion} aria-label="Páginas">
            <span className={comun.rango}>{rangoDePagina(datos.numero, datos.tamano, datos.total)}</span>
            <Boton variante="secundario" tamano="chico" disabled={datos.numero === 0}
              onClick={() => cambiar({ p: String(datos.numero - 1) })}>← Anterior</Boton>
            <Boton variante="secundario" tamano="chico" disabled={datos.numero + 1 >= datos.totalPaginas}
              onClick={() => cambiar({ p: String(datos.numero + 1) })}>Siguiente →</Boton>
          </nav>
        </>
      )}

      {modal === 'REGISTRAR' && (
        <ModalGasto turnoAbierto={turnoAbierto} porFuera onCerrar={() => setModal(null)}
          onRegistrado={(g) => registrado(`Gasto de ${formatoCOP(g.monto)} registrado${g.delCajon
            ? ': salió del cajón del turno abierto' : ''}.`)} />
      )}
      {modal === 'CATEGORIAS' && (
        <ModalCategoriasGasto onCerrar={() => setModal(null)}
          onCambio={() => categoriasGastoApi.todas().then(setCategorias).catch(() => {})} />
      )}
      {modal?.anular && (
        <ModalMotivo
          titulo="Anular gasto"
          descripcion={`${modal.anular.descripcion} · ${formatoCOP(modal.anular.monto)}. Queda en la lista, tachado, y deja de sumar.`}
          textoConfirmar="Anular"
          peligro
          enviando={anulando}
          error={errorAnular}
          placeholder="Ej.: se registró dos veces"
          onConfirmar={anular}
          onCerrar={() => setModal(null)}
        />
      )}
    </div>
  )
}

function Totales({ totales, clave }) {
  if (!clave) return null
  const cargando = totales.clave !== clave
  const t = totales.datos
  if (!t) {
    return cargando ? null : <AvisoCarga error={totales.error} />
  }
  const descuadre = t.delCajon + t.porFuera !== t.total
  return (
    <section className={`${estilos.totales} ${cargando ? comun.atenuado : ''}`} aria-label="Totales de los gastos">
      <div className={estilos.tarjeta}>
        <span className={estilos.etiqueta}>Gastado</span>
        <span className={estilos.valor}>{formatoCOP(t.total)}</span>
        <span className={estilos.nota}>{t.gastos} {t.gastos === 1 ? 'gasto' : 'gastos'} · los anulados no suman</span>
      </div>
      <div className={estilos.tarjeta}>
        <span className={estilos.etiqueta}>Salió del cajón</span>
        <span className={estilos.valor}>{formatoCOP(t.delCajon)}</span>
        <span className={estilos.nota}>Restó del arqueo de su turno</span>
      </div>
      <div className={estilos.tarjeta}>
        <span className={estilos.etiqueta}>Por fuera del cajón</span>
        <span className={estilos.valor}>{formatoCOP(t.porFuera)}</span>
        <span className={estilos.nota}>Transferencias y efectivo por fuera</span>
      </div>
      {descuadre && (
        <p className={estilos.descuadre} role="alert">
          <span aria-hidden>⚠</span> Los totales no cuadran: del cajón y por fuera suman {formatoCOP(t.delCajon + t.porFuera)} y
          lo gastado es {formatoCOP(t.total)}.
        </p>
      )}
    </section>
  )
}
