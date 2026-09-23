/**
 * Gastos y retiros (spec 0006, fases 1 y 2), sin JSX, para probarlos con `node --test`.
 */
import { soloDigitos } from './formato.js'

export const TAMANO_GASTOS = 25

export const NATURALEZAS = { GASTO: 'Gasto', COSTO: 'Costo' }

/** Un monto escrito en pantalla, en pesos: "15000", "15.000" o "$ 15.000". `null` si no hay nada escrito. */
export function montoDesdeTexto(texto) {
  const digitos = soloDigitos(texto)
  return digitos === '' ? null : Number(digitos)
}

// ── La lista ─────────────────────────────────────────────────────────────────

const CLAVES = ['desde', 'hasta', 'categoriaId']

/** Los filtros, leídos de la URL: al volver a la lista, está donde se dejó. */
export function filtrosDeGastos(params) {
  const pagina = Number(params.get('p'))
  return {
    desde: params.get('desde') ?? '',
    hasta: params.get('hasta') ?? '',
    categoriaId: params.get('categoriaId') ?? '',
    pagina: Number.isInteger(pagina) && pagina > 0 ? pagina : 0,
  }
}

export const hayFiltrosDeGastos = (filtros) => CLAVES.some((clave) => filtros[clave])

export function problemaDelRango({ desde, hasta }) {
  return desde && hasta && desde > hasta ? 'La fecha inicial es posterior a la final' : null
}

/** Lo que se le pide al backend para los totales: solo los filtros con valor. */
export function consultaDeTotalesDeGastos(filtros) {
  return Object.fromEntries(CLAVES.filter((clave) => filtros[clave]).map((clave) => [clave, filtros[clave]]))
}

/** Lo mismo, con la página: la lista y los totales filtran exactamente igual. */
export function consultaDeGastos(filtros) {
  return { ...consultaDeTotalesDeGastos(filtros), pagina: filtros.pagina ?? 0, tamano: TAMANO_GASTOS }
}

/**
 * De dónde salió la plata, como se lee en la lista: "Del cajón", "Efectivo por fuera" o
 * "Transferencia · Nequi del dueño".
 */
export function textoDelOrigen(gasto) {
  if (gasto.delCajon) return 'Del cajón'
  if (gasto.formaPago === 'TRANSFERENCIA') return gasto.cuenta ? `Transferencia · ${gasto.cuenta}` : 'Transferencia'
  return 'Efectivo por fuera'
}

/**
 * ¿Se puede anular desde la pantalla? Uno por fuera, siempre. Uno del cajón, solo mientras su turno siga
 * abierto: el arqueo de un turno cerrado ya se firmó (RF-006). El backend lo exige igual; esto evita ofrecer
 * un botón que va a responder que no.
 */
export function sePuedeAnular(gasto, turnoAbiertoId) {
  if (gasto.anuladoEn) return false
  return !gasto.delCajon || gasto.turnoId === turnoAbiertoId
}

/** Las que se ofrecen para un gasto nuevo: las activas, por nombre. */
export function categoriasParaElegir(categorias) {
  return (categorias ?? []).filter((c) => c.activa).sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))
}

// ── Registrar un gasto ───────────────────────────────────────────────────────

/** Sin turno abierto, uno nuevo arranca por fuera del cajón: del cajón no se podría registrar. */
export function gastoNuevo({ hayTurno, hoy }) {
  return {
    categoriaId: '', monto: '', descripcion: '', delCajon: hayTurno, formaPago: '', cuentaId: '', fecha: hoy,
    delMes: false, delMesTocado: false,
  }
}

/**
 * Al elegir la categoría, un gasto nuevo toma de ella si es del mes (spec 0007, RF-008a): *Arriendo* sale marcado.
 * Si el cajero ya tocó la casilla, se respeta lo que eligió.
 */
export function conCategoria(gasto, categoria) {
  return {
    ...gasto,
    categoriaId: categoria?.id ?? '',
    delMes: gasto.delMesTocado ? gasto.delMes : Boolean(categoria?.mensual),
  }
}

/**
 * Qué le falta al gasto, campo por campo. `null` en los que están bien. Se calcula al pintar: al corregir el
 * dato, el aviso se va solo.
 */
export function problemasDelGasto(gasto, { hayTurno, hoy }) {
  const monto = montoDesdeTexto(gasto.monto)
  const porFuera = !gasto.delCajon
  return {
    categoria: gasto.categoriaId ? null : 'Elige la categoría',
    monto: monto == null || monto <= 0 ? 'Escribe cuánto se gastó'
      : !Number.isSafeInteger(monto) ? 'Ese monto no es válido' : null,
    descripcion: gasto.descripcion.trim() ? null : 'Escribe en qué se gastó',
    origen: gasto.delCajon && !hayTurno
      ? 'No hay un turno abierto: regístralo como pagado por fuera del cajón, o abre el turno en Vender'
      : null,
    formaPago: porFuera && !gasto.formaPago ? 'Elige cómo se pagó' : null,
    cuenta: porFuera && gasto.formaPago === 'TRANSFERENCIA' && !gasto.cuentaId ? 'Elige desde qué cuenta salió' : null,
    fecha: !porFuera ? null
      : !gasto.fecha ? 'Escribe la fecha del gasto'
        : gasto.fecha > hoy ? 'La fecha no puede ser después de hoy' : null,
  }
}

export const sinProblemas = (problemas) => Object.values(problemas).every((p) => p == null)

/** Lo que se manda al servidor. La llave nace al abrir el formulario y viaja igual en cada reintento. */
export function comandoDelGasto(gasto, llave, confirmado = false) {
  const porFuera = !gasto.delCajon
  return {
    llave,
    categoriaId: gasto.categoriaId,
    monto: montoDesdeTexto(gasto.monto),
    descripcion: gasto.descripcion.trim(),
    delCajon: gasto.delCajon,
    formaPago: porFuera ? gasto.formaPago : null,
    cuentaId: porFuera && gasto.formaPago === 'TRANSFERENCIA' ? gasto.cuentaId : null,
    fecha: porFuera ? gasto.fecha : null,
    delMes: Boolean(gasto.delMes),
    confirmado,
  }
}

// ── Registrar un retiro ──────────────────────────────────────────────────────

export function problemasDelRetiro({ monto, motivo }) {
  const valor = montoDesdeTexto(monto)
  return {
    monto: valor == null || valor <= 0 ? 'Escribe cuánto se sacó'
      : !Number.isSafeInteger(valor) ? 'Ese monto no es válido' : null,
    motivo: motivo.trim() ? null : 'Escribe quién se la llevó o para qué',
  }
}

export function comandoDelRetiro({ monto, motivo }, llave, confirmado = false) {
  return { llave, monto: montoDesdeTexto(monto), motivo: motivo.trim(), confirmado }
}

// ── Lo que salió del cajón en el turno ───────────────────────────────────────

/**
 * Todo lo que sacó billetes del cajón en un turno, en una sola lista y en el orden en que pasó: gastos del
 * cajón, retiros, compras de caja y el efectivo devuelto por ventas anuladas en el turno (de este turno o de
 * otro). Los anulados siguen en la lista. Lo que debería haber lo trae el turno aparte, con su desglose.
 */
export function movimientosDelCajon(turno) {
  const gastos = (turno.gastos ?? []).map((g) => ({
    tipo: 'GASTO', id: g.id, cuando: g.registradoEn, titulo: g.categoria, detalle: g.descripcion, monto: g.monto,
    anulado: Boolean(g.anuladoEn), motivoAnulacion: g.motivoAnulacion,
    registradoPor: g.registradoPor?.nombre ?? null, anuladoPor: g.anuladoPor?.nombre ?? null,
  }))
  const retiros = (turno.retiros ?? []).map((r) => ({
    tipo: 'RETIRO', id: r.id, cuando: r.registradoEn, titulo: 'Retiro', detalle: r.motivo, monto: r.monto,
    anulado: Boolean(r.anuladoEn), motivoAnulacion: r.motivoAnulacion,
    registradoPor: r.registradoPor?.nombre ?? null, anuladoPor: r.anuladoPor?.nombre ?? null,
  }))
  const compras = (turno.compras ?? []).map((c) => ({
    tipo: 'COMPRA', id: c.id, cuando: c.fechaRegistro, titulo: 'Compra',
    detalle: [c.proveedor, c.numeroFactura].filter(Boolean).join(' · '), monto: c.total,
    anulado: c.estado === 'ANULADA', motivoAnulacion: null,
  }))
  const devoluciones = [
    ...(turno.ventas ?? []).filter((v) => v.anuladaEnTurnoId === turno.id),
    ...(turno.anuladasDeOtrosTurnos ?? []),
  ].filter((v) => v.efectivo > 0).map((v) => ({
    tipo: 'DEVOLUCION', id: v.id, cuando: v.anuladaEn, titulo: 'Venta anulada', detalle: `N.º ${v.numero}`,
    monto: v.efectivo, anulado: false, motivoAnulacion: null,
  }))
  return [...gastos, ...retiros, ...compras, ...devoluciones].sort((a, b) => String(a.cuando).localeCompare(String(b.cuando)))
}
