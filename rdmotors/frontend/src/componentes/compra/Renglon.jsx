import { useRef } from 'react'
import Boton from '../Boton'
import { formatoCOP, formatoCosto, GUION, margen, soloDigitos } from '../../utils/formato'
import { fechaCorta } from '../../utils/compra'
import estilos from './Renglon.module.css'

/**
 * Un renglón de la factura.
 *
 * El campo de código HACE DE BUSCADOR (RF-007b). Se teclea el código de la factura del proveedor
 * y al salir del campo —o con Enter— el sistema decide:
 *
 *     existe     → lo engancha; solo faltan cantidad y costo
 *     no existe  → abre la creación CON EL CÓDIGO YA PUESTO
 *
 * Nunca se escribe el código dos veces. Es la diferencia entre que capturar una factura de 20
 * renglones sea tedioso o sea rápido.
 */
export default function Renglon({
  renglon, onCambiar, onQuitar, onBuscarCodigo, onSoltarRepuesto, onEditarNuevo,
  onEditarExistente, onReintentar, puedeQuitar, problema,
}) {
  const refCodigo = useRef(null)
  const refCantidad = useRef(null)

  const cantidad = Number(renglon.cantidad) || 0
  const esNuevo = renglon.repuestoNuevo != null
  const repuesto = renglon.repuesto
  const enganchado = repuesto != null || esNuevo

  // El costo que el usuario NO escribió: el sistema lo deduce. Es todo el punto del modo lote.
  const costoTotal = Number(soloDigitos(renglon.costoTotal)) || 0
  const costoUnitarioEscrito = Number(soloDigitos(renglon.costoUnitario)) || 0

  const unitarioCalculado =
    renglon.modo === 'TOTAL'
      ? (cantidad > 0 ? costoTotal / cantidad : null)
      : (costoUnitarioEscrito || null)

  const totalCalculado =
    renglon.modo === 'TOTAL' ? costoTotal : costoUnitarioEscrito * cantidad

  // EL PRECIO VIVE AQUÍ. Un solo campo, esté el repuesto creado o por crear: es donde están el
  // costo y el margen, que es con lo que se decide. Tenerlo también en el modal hacía que las
  // dos cifras divergieran.
  const precioEscrito = Number(soloDigitos(renglon.precioVenta)) || null
  const precioEfectivo = precioEscrito ?? (esNuevo ? null : repuesto?.precio ?? null)

  const m = margen(unitarioCalculado, precioEfectivo)

  // El costo vino de la última compra y nadie lo ha tocado: se ve distinto para que no se
  // registre sin compararlo con la factura.
  const costoSugerido = renglon.costoSugerido && renglon.modo === 'UNITARIO'

  function alTeclearCodigo(e) {
    if (e.key === 'Enter') {
      e.preventDefault()
      onBuscarCodigo(renglon, () => refCantidad.current?.focus(), true)
    }
  }

  function soltar() {
    onSoltarRepuesto(renglon.id)
    // El foco vuelve al código: soltar el repuesto es para teclear otro.
    setTimeout(() => refCodigo.current?.select(), 0)
  }

  return (
    <div
      id={`renglon-${renglon.id}`}
      className={`${estilos.renglon} ${
        (renglon.error && !renglon.sinConexion) || problema ? estilos.conError : ''
      }`}
    >
      {/* ── Código: el buscador ───────────────────────────────────────────── */}
      <div className={estilos.celdaCodigo}>
        <input
          ref={refCodigo}
          className={estilos.entradaCodigo}
          value={renglon.codigo}
          onChange={(e) => onCambiar({
            ...renglon, codigo: e.target.value.toUpperCase(),
            codigoBuscado: null, error: null, sinConexion: false,
          })}
          onKeyDown={alTeclearCodigo}
          onBlur={() => onBuscarCodigo(renglon)}
          placeholder="Código"
          aria-label="Código del repuesto"
          autoComplete="off"
          spellCheck="false"
          readOnly={enganchado}
        />
        {renglon.buscando && <span className={estilos.buscando}>buscando…</span>}
      </div>

      {/* ── Qué repuesto quedó enganchado ─────────────────────────────────── */}
      <div className={estilos.celdaRepuesto}>
        {repuesto && (
          <>
            <span className={estilos.nombre}>{repuesto.nombre}</span>
            <span className={estilos.marca}>
              {repuesto.marca}
              <span className={estilos.stockActual}>· stock {repuesto.stock}</span>
            </span>
            {renglon.ultimaCompra && (
              <span className={estilos.historial}>
                última compra {formatoCosto(renglon.ultimaCompra.costoExacto)}
                {renglon.ultimaCompra.cuando && ` · ${fechaCorta(renglon.ultimaCompra.cuando)}`}
              </span>
            )}
          </>
        )}
        {esNuevo && (
          <>
            <span className={estilos.nombre}>{renglon.repuestoNuevo.nombreConcepto}</span>
            <span className={estilos.nuevo}>nuevo · {renglon.repuestoNuevo.marcaRepuesto}</span>
          </>
        )}
        {!enganchado && <span className={estilos.vacio}>Teclea el código y Enter</span>}

        {/* Cambiar de repuesto sin quitar el renglón: queda en blanco en su misma posición y el
            repuesto nuevo se rellena desde su última compra. */}
        {enganchado && (
          <div className={estilos.acciones}>
            <button
              type="button"
              className={estilos.enlace}
              onClick={() => (esNuevo ? onEditarNuevo(renglon) : onEditarExistente(renglon))}
            >
              {/* Un repuesto mal creado quedaba inservible: solo se podía eliminar el renglón
                  y el error seguía ahí para siempre (RF-009). */}
              Corregir ficha
            </button>
            <button type="button" className={estilos.enlace} onClick={soltar}>
              Cambiar repuesto
            </button>
          </div>
        )}
      </div>

      {/* ── Cantidad ──────────────────────────────────────────────────────── */}
      <input
        ref={refCantidad}
        className={`${estilos.entrada} ${estilos.cifra}`}
        value={renglon.cantidad}
        onChange={(e) => onCambiar({ ...renglon, cantidad: soloDigitos(e.target.value) })}
        placeholder="Cant."
        aria-label="Cantidad"
        inputMode="numeric"
        disabled={!enganchado}
      />

      {/* ── Modo de captura: por renglón, no por factura ───────────────────── */}
      <div className={estilos.modo} role="group" aria-label="Cómo viene el costo">
        <button
          type="button"
          className={renglon.modo === 'TOTAL' ? estilos.modoActivo : estilos.modoOpcion}
          onClick={() => onCambiar({ ...renglon, modo: 'TOTAL' })}
          disabled={!enganchado}
        >
          Total
        </button>
        <button
          type="button"
          className={renglon.modo === 'UNITARIO' ? estilos.modoActivo : estilos.modoOpcion}
          onClick={() => onCambiar({ ...renglon, modo: 'UNITARIO' })}
          disabled={!enganchado}
        >
          C/u
        </button>
      </div>

      {/* ── Costo de COMPRA ───────────────────────────────────────────────── */}
      <div className={estilos.celdaCosto}>
        <input
          className={`${estilos.entrada} ${estilos.cifra} ${costoSugerido ? estilos.sugerido : ''}`}
          value={renglon.modo === 'TOTAL' ? renglon.costoTotal : renglon.costoUnitario}
          onChange={(e) => onCambiar({
            ...renglon,
            [renglon.modo === 'TOTAL' ? 'costoTotal' : 'costoUnitario']: e.target.value,
            costoSugerido: false,
          })}
          title={costoSugerido ? 'Costo de la última compra: revísalo contra la factura' : undefined}
          placeholder={renglon.modo === 'TOTAL' ? 'Pagué' : 'C/u'}
          aria-label={renglon.modo === 'TOTAL' ? 'Total pagado, con IVA' : 'Costo por unidad, con IVA'}
          inputMode="numeric"
          disabled={!enganchado}
        />
        {/* La cifra deducida. Con 2 decimales a propósito: "me sale a $13.333,33 c/u" es
            exactamente lo que el usuario quiere ver, y ocultarlo escondería de dónde salió. */}
        {cantidad > 0 && unitarioCalculado != null && (
          <span className={estilos.deducido}>
            {renglon.modo === 'TOTAL'
              ? `→ ${formatoCosto(unitarioCalculado)} c/u`
              : `→ ${formatoCOP(totalCalculado)} total`}
          </span>
        )}
      </div>

      {/* ── Precio de VENTA — el único sitio del sistema ──────────────────── */}
      <div className={estilos.celdaPrecio}>
        <input
          className={`${estilos.entrada} ${estilos.cifra} ${
            esNuevo && !precioEscrito ? estilos.faltante : ''
          }`}
          value={renglon.precioVenta}
          onChange={(e) => onCambiar({ ...renglon, precioVenta: soloDigitos(e.target.value) })}
          placeholder={esNuevo ? 'Obligatorio' : repuesto ? String(repuesto.precio) : 'Precio'}
          aria-label="Precio de venta"
          inputMode="numeric"
          disabled={!enganchado}
        />
        {/* Vacío en uno existente = no tocar su precio. Reponer stock no debe reescribir en
            silencio un precio que nadie quiso cambiar (RF-018). En uno nuevo es obligatorio:
            un repuesto sin precio no se puede vender. */}
        {repuesto && (precioEscrito == null || precioEscrito === repuesto.precio) && (
          <span className={estilos.deducido}>sin cambio</span>
        )}
        {repuesto && precioEscrito != null && precioEscrito !== repuesto.precio && (
          <span className={estilos.cambioPrecio}>antes {formatoCOP(repuesto.precio)}</span>
        )}
        {esNuevo && !precioEscrito && (
          <span className={estilos.faltanteNota}>a qué lo vendes</span>
        )}
      </div>

      {/* ── Margen en vivo ────────────────────────────────────────────────── */}
      <div className={estilos.celdaMargen}>
        {m == null ? (
          <span className={estilos.sinMargen}>{GUION}</span>
        ) : m.utilidad < 0 ? (
          // Pérdida: no basta con pintarlo rojo. Lleva icono y dice cuánto se pierde.
          <span className={estilos.perdida}>
            <span aria-hidden>⚠</span> Pierdes {formatoCOP(Math.abs(m.utilidad))} c/u
          </span>
        ) : (
          <span className={estilos.ganancia}>
            <strong>{m.porcentaje.toFixed(0)}%</strong>
            <span className={estilos.utilidad}>+{formatoCOP(m.utilidad)} c/u</span>
          </span>
        )}
      </div>

      <div className={estilos.celdaAcciones}>
        <Boton
          variante="fantasma"
          tamano="chico"
          onClick={() => onQuitar(renglon.id)}
          disabled={!puedeQuitar}
          aria-label="Quitar renglón"
        >
          ×
        </Boton>
      </div>

      {problema && !renglon.error && (
        <p className={estilos.mensajeError} role="alert">
          <span aria-hidden>⚠</span> {problema}
        </p>
      )}

      {renglon.error && (
        <p
          className={renglon.sinConexion ? estilos.mensajeRed : estilos.mensajeError}
          role="alert"
        >
          <span aria-hidden>{renglon.sinConexion ? '⏱' : '⚠'}</span> {renglon.error}
          {renglon.sinConexion && (
            <button type="button" className={estilos.reintentar} onClick={() => onReintentar(renglon)}>
              Reintentar
            </button>
          )}
        </p>
      )}
    </div>
  )
}
