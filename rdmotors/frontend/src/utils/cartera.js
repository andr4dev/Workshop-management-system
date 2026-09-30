/**
 * La Cartera (spec 0008), sin JSX, para probarla con `node --test`.
 *
 * Las cifras las calcula el servidor: lo que debe cada cliente, desde cuándo y el total por cobrar. Aquí solo se dicen
 * en palabras y se comprueba que las partes cuadren; si no cuadran, se dice, nunca se "arregla" una cifra.
 */
import { formatoCOP } from './formato.js'
import { diasEntre } from './periodo.js'

export const ESTADOS = { PENDIENTE: 'Pendiente', ABONADA: 'Abonada', PAGADA: 'Pagada', ANULADA: 'Anulada' }

export const FORMAS = { EFECTIVO: 'Efectivo', TRANSFERENCIA: 'Transferencia' }

export const VISTAS = [
  { valor: 'DEBEN', texto: 'Deben' },
  { valor: 'HISTORIAL', texto: 'Historial completo' },
]

/** Por qué fecha se filtra (RF-022): cuándo se fió, o cuándo abonó. */
export const MODOS_FECHA = [
  { valor: 'VENTA', texto: 'Fecha de la venta' },
  { valor: 'ABONO', texto: 'Fecha del abono' },
]

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sept', 'oct', 'nov', 'dic']

/** "12 sept 2026" desde "2026-09-12", sin pasar por la zona del equipo: es un día, no un instante. */
export function fechaCorta(texto) {
  if (!texto) return ''
  const [a, m, d] = texto.split('-').map(Number)
  return `${d} ${MESES[m - 1]} ${a}`
}

/** Cuántos días lleva debiendo: 0 si es de hoy. */
export const diasDebiendo = (desde, hoy) => (desde ? diasEntre(desde, hoy) - 1 : null)

/** "desde hoy", "desde ayer" o "desde el 12 sept 2026 · hace 9 días". `null` si está al día. */
export function desdeCuandoEnPalabras(desde, hoy) {
  const dias = diasDebiendo(desde, hoy)
  if (dias == null) return null
  if (dias <= 0) return 'desde hoy'
  if (dias === 1) return 'desde ayer'
  return `desde el ${fechaCorta(desde)} · hace ${dias} días`
}

/** La marca de deuda vieja (spec 0008, H13): lo que debe hace más de 30 días. */
export const DIAS_DEUDA_VIEJA = 30
export const esDeudaVieja = (desde, hoy) => (diasDebiendo(desde, hoy) ?? 0) > DIAS_DEUDA_VIEJA

export const textoPendientes = (n) => (n === 1 ? '1 venta pendiente' : `${n} ventas pendientes`)

/** Lo de arriba de la lista: "3 clientes deben · $180.000 por cobrar". */
export function resumenDeLaCartera({ deben, porCobrar, clientes }, vista) {
  if (vista === 'HISTORIAL') {
    const n = clientes.length
    return `${n} ${n === 1 ? 'cliente ha tenido' : 'clientes han tenido'} fiado · ${formatoCOP(porCobrar)} por cobrar`
  }
  if (deben === 0) return 'Nadie debe: todo al día'
  return `${deben} ${deben === 1 ? 'cliente debe' : 'clientes deben'} · ${formatoCOP(porCobrar)} por cobrar`
}

/** "Venta N.º 41" o "Saldo del cuaderno". */
export const nombreDeLaDeuda = (d) => (d.origen === 'CUADERNO' ? 'Saldo del cuaderno' : `Venta N.º ${d.numeroVenta}`)

/**
 * Las deudas de la ficha juntas por venta (spec 0016: una por producto). En el orden en que vienen (la venta más
 * reciente primero), y los productos de cada venta en su orden. El saldo del cuaderno y una venta *por venta* (de
 * antes, que no se partió) son un grupo de una sola deuda.
 *
 * Cada grupo trae sus cifras (`monto`, `abonado`, `pendiente`, sin lo anulado) y su estado.
 */
export function agruparPorVenta(deudas) {
  const grupos = []
  const porClave = new Map()
  for (const d of deudas) {
    const clave = d.ventaId ?? d.id
    if (!porClave.has(clave)) {
      const grupo = { clave, deudas: [] }
      porClave.set(clave, grupo)
      grupos.push(grupo)
    }
    porClave.get(clave).deudas.push(d)
  }
  return grupos.map(({ clave, deudas: suyas }) => {
    const productos = [...suyas].sort((a, b) => (a.posicion ?? 0) - (b.posicion ?? 0))
    const vigentes = productos.filter((d) => d.estado !== 'ANULADA')
    const suma = (campo) => vigentes.reduce((s, d) => s + d[campo], 0)
    const primera = productos[0]
    const monto = vigentes.length ? suma('monto') : productos.reduce((s, d) => s + d.monto, 0)
    const abonado = suma('abonado')
    const pendiente = suma('pendiente')
    const estado = vigentes.length === 0 ? 'ANULADA'
      : pendiente === 0 ? 'PAGADA' : abonado === 0 ? 'PENDIENTE' : 'ABONADA'
    return {
      clave, origen: primera.origen, ventaId: primera.ventaId, numeroVenta: primera.numeroVenta, fecha: primera.fecha,
      motivo: primera.motivo, venta: primera.venta ?? null, porProducto: productos.some((d) => d.lineaVentaId),
      productos, monto, abonado, pendiente, estado,
    }
  })
}

/**
 * Los renglones de una venta fiada por producto, cada uno con su deuda; el que no tiene deuda se pagó al llevárselo.
 * Sin lo que se llevó (no vino), solo las deudas.
 */
export function renglonesDelGrupo(grupo) {
  if (!grupo.venta) return grupo.productos.map((d) => ({ renglon: null, deuda: d }))
  return grupo.venta.renglones.map((r) => ({ renglon: r, deuda: grupo.productos.find((d) => d.lineaVentaId === r.lineaId) ?? null }))
}

/**
 * Lo que se puede marcar al abonar (spec 0016): los grupos con algo pendiente, **del más viejo al más nuevo** (el orden
 * en que se paga), y en cada uno sus deudas pendientes en el orden de la venta.
 */
export function paraAbonar(deudas) {
  return agruparPorVenta(deudas).filter((g) => g.pendiente > 0).reverse()
    .map((g) => ({ ...g, productos: g.productos.filter((d) => d.pendiente > 0) }))
}

/** Los ids marcados en el orden de la lista, y lo que falta de todos ellos. */
export function loMarcado(grupos, marcados) {
  const enOrden = grupos.flatMap((g) => g.productos).filter((d) => marcados.includes(d.id))
  return { ids: enOrden.map((d) => d.id), monto: enOrden.reduce((s, d) => s + d.pendiente, 0) }
}

/** "MOTUL 7100 10W30" o, si la deuda es de la venta entera, "Venta N.º 41". */
export const nombreDelProducto = (d) => d.descripcion ?? nombreDeLaDeuda(d)

/**
 * Qué se llevó en una venta fiada, en renglones para leer: "2 × MOTUL 7100 10W30" con su valor, y abajo lo que la
 * baja hasta lo fiado: el descuento y lo que pagó al llevárselo. Los renglones menos esas dos cosas suman `monto`.
 * `null` si la deuda no tiene venta (el saldo del cuaderno).
 */
export function loQueSeLlevo(deuda) {
  const v = deuda.venta
  if (!v) return null
  const renglones = v.renglones.map((r) => ({
    texto: `${r.cantidad} × ${r.nombre}`,
    detalle: [
      r.marca && !r.nombre.toUpperCase().includes(r.marca.toUpperCase()) ? r.marca : null,
      r.cantidad > 1 ? `${formatoCOP(r.precioUnitario)} c/u` : null,
      r.cambio === 'NO_SE_CAMBIA' ? 'sin cambio' : null,
      r.cambio === 'SE_CAMBIA' ? 'con cambio aquí' : null,
    ].filter(Boolean).join(' · '),
    total: r.total,
  }))
  const pagoAlLlevarselo = v.total - deuda.monto
  return {
    renglones,
    descuento: v.descuento > 0 ? { monto: v.descuento, motivo: v.motivoDescuento ?? '' } : null,
    pagoAlLlevarselo: pagoAlLlevarselo > 0 ? pagoAlLlevarselo : 0,
  }
}

/**
 * Si las cifras de la ficha no cuadran, por qué: cada deuda `abonado + pendiente = monto`, y lo que debe es la suma de
 * lo pendiente. El servidor ya lo cumple; una lista con algo es un error de programa y se muestra en rojo.
 */
export function problemasDeLaFicha(ficha) {
  const problemas = []
  let pendiente = 0
  for (const d of ficha.deudas) {
    if (d.estado !== 'ANULADA' && d.abonado + d.pendiente !== d.monto) {
      problemas.push(`${nombreDeLaDeuda(d)}: ${formatoCOP(d.abonado)} abonados y ${formatoCOP(d.pendiente)} pendientes no dan ${formatoCOP(d.monto)}`)
    }
    pendiente += d.pendiente
  }
  if (pendiente !== ficha.debe) {
    problemas.push(`Lo pendiente de sus ventas suma ${formatoCOP(pendiente)} y debe ${formatoCOP(ficha.debe)}`)
  }
  return problemas
}
