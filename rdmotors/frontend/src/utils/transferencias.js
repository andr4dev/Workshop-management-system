/**
 * La cuenta de transferencias del turno (el Daviplata de la tienda), sin JSX, para probarla con `node --test`
 * (2026-10-09). El servidor manda `turno.transferencias` ya calculado; aquí solo se ordena para mostrarlo.
 */
import { formatoCOP } from './formato.js'

/** Las partes, en el orden en que se leen. Las que restan llevan signo −1. */
const PARTES = [
  { clave: 'ventas', etiqueta: 'Ventas por transferencia', signo: 1, siempre: true },
  { clave: 'abonos', etiqueta: 'Abonos de clientes por transferencia', signo: 1 },
  { clave: 'desdeElCajon', etiqueta: 'Efectivo pasado del cajón', signo: 1 },
  { clave: 'devoluciones', etiqueta: 'Devuelto por ventas anuladas', signo: -1 },
  { clave: 'compras', etiqueta: 'Compras pagadas por transferencia', signo: -1 },
  { clave: 'gastos', etiqueta: 'Gastos pagados por transferencia', signo: -1 },
  { clave: 'retiros', etiqueta: 'Sacado de la cuenta', signo: -1 },
]

/**
 * Las filas del cuadre: con cuánto arrancó (si se anotó) y cada parte que no es cero. Las ventas siempre, aunque
 * sean $0: es la fila que más se busca.
 */
export function filasDeTransferencias(t) {
  if (!t) return []
  const filas = []
  if (t.saldoInicial != null) {
    filas.push({ clave: 'saldoInicial', etiqueta: 'Con cuánto arrancó la cuenta', signo: 1, monto: t.saldoInicial })
  }
  for (const p of PARTES) {
    const monto = t[p.clave] ?? 0
    if (monto !== 0 || p.siempre) filas.push({ clave: p.clave, etiqueta: p.etiqueta, signo: p.signo, monto })
  }
  return filas
}

/** Si las partes no suman lo que debería haber, por qué. Vacía si cuadra o si no hay con qué comparar. */
export function problemasDeTransferencias(t) {
  if (!t || t.esperado == null) return []
  const suma = filasDeTransferencias(t).reduce((s, f) => s + f.signo * f.monto, 0)
  return suma === t.esperado ? []
    : [`Las partes suman ${formatoCOP(suma)} y lo que debería haber es ${formatoCOP(t.esperado)}`]
}

/**
 * De dónde salió la plata de la cuenta: las compras y los gastos por transferencia del turno, y los retiros que la
 * movieron, en el orden en que pasaron. Lo anulado sigue en la lista, tachado: no restó.
 */
export function salidasDeTransferencias(turno) {
  const t = turno?.transferencias
  if (!t) return []
  const compras = (t.comprasPorTransferencia ?? []).map((c) => ({
    id: `compra-${c.id}`, cuando: c.fechaRegistro, titulo: 'Compra',
    detalle: [c.proveedor, c.numeroFactura].filter(Boolean).join(' · '), monto: c.total, signo: -1,
    anulado: c.estado === 'ANULADA',
  }))
  const gastos = (t.gastosPorTransferencia ?? []).map((g) => ({
    id: `gasto-${g.id}`, cuando: g.registradoEn, titulo: g.categoria ?? 'Gasto', detalle: g.descripcion,
    monto: g.monto, signo: -1, anulado: Boolean(g.anuladoEn),
  }))
  const retiros = (turno.retiros ?? []).filter((r) => r.tipo === 'A_TRANSFERENCIAS' || r.tipo === 'DE_TRANSFERENCIAS')
    .map((r) => ({
      id: `retiro-${r.id}`, cuando: r.registradoEn,
      titulo: r.tipo === 'A_TRANSFERENCIAS' ? 'Pasado del cajón' : 'Sacado de la cuenta', detalle: r.motivo,
      monto: r.monto, signo: r.tipo === 'A_TRANSFERENCIAS' ? 1 : -1, anulado: Boolean(r.anuladoEn),
    }))
  return [...compras, ...gastos, ...retiros].sort((a, b) => String(a.cuando).localeCompare(String(b.cuando)))
}
