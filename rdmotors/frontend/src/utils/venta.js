/**
 * La lógica de la venta de mostrador (spec 0003, fase 3), sin JSX, para probarla con `node --test`.
 *
 * La venta que se está armando vive aquí como un objeto plano —el "borrador"— que se guarda en el
 * navegador con cada cambio (RF-028). El servidor recalcula todo al cobrar: estas cuentas son para
 * mostrarle al cajero lo mismo que va a cobrar el servidor, y usan exactamente sus reglas.
 */
import { porQueNoSeLeFia } from './clientes.js'
import { formatoCOP, soloDigitos } from './formato.js'

export const CLAVE_BORRADOR = 'rdmotors:venta-en-curso'

/**
 * Dónde se guarda la venta a medias de esa persona (spec 0004, RF-021). Es de quien la armó: otra persona que
 * entra en el mismo equipo no la hereda, y su dueño la encuentra al volver. Una que se mandó a cobrar sin
 * respuesta sigue bloqueada para su dueño.
 */
export const claveDelBorrador = (usuarioId) => `${CLAVE_BORRADOR}:${usuarioId}`

/** Los motivos de descuento que se dan de un toque (spec 0003, RF-032). */
export const MOTIVOS_FRECUENTES = ['Cliente frecuente', 'Negociación', 'Producto con detalle']

// ── La llave y la venta nueva ────────────────────────────────────────────────

/**
 * Un UUID v4 para la llave contra el doble cobro. Con `crypto.getRandomValues`, que existe también en
 * la tablet que entra por `http://192.168.x.x`; `crypto.randomUUID` no (ver `idLocal` en formato.js).
 * El backend la guarda como `uuid`, así que tiene que ser uno de verdad.
 */
export function llaveNueva(bytesAleatorios = (n) => globalThis.crypto.getRandomValues(new Uint8Array(n))) {
  const b = bytesAleatorios(16)
  b[6] = (b[6] & 0x0f) | 0x40   // versión 4
  b[8] = (b[8] & 0x3f) | 0x80   // variante RFC 4122
  const h = [...b].map((x) => x.toString(16).padStart(2, '0')).join('')
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`
}

/**
 * @property enviada true desde que se mandó a cobrar hasta que el servidor responde algo definitivo.
 *   Si se cortó la red en el medio no se sabe si quedó cobrada: la venta se bloquea y solo se puede
 *   reintentar lo mismo, con la misma llave, para no cobrarla dos veces ni cobrar otra cosa.
 */
export function ventaNueva(llave = llaveNueva()) {
  return { llave, renglones: [], descuento: null, enviada: false, cobro: null }
}

// ── Renglones ────────────────────────────────────────────────────────────────

export function renglonDesdeRepuesto(r) {
  return {
    varianteId: r.id,
    codigo: r.codigo,
    nombre: r.nombre,
    marca: r.marca,
    precio: Number(r.precio),
    stock: Number(r.stock),
    costoPromedio: r.costoPromedio == null ? null : Number(r.costoPromedio),
    cantidad: 1,
    problema: null,
  }
}

/** `null` si se puede agregar una unidad más de ese repuesto; si no, por qué. */
export function problemaParaAgregar(repuesto, renglones) {
  if (!(Number(repuesto.precio) > 0)) return `${repuesto.codigo} no tiene precio de venta. Fíjalo en su ficha.`
  const yaEnLaVenta = renglones.find((r) => r.varianteId === repuesto.id)?.cantidad ?? 0
  const stock = Number(repuesto.stock)
  if (stock <= 0) return `No hay unidades de ${repuesto.codigo}.`
  if (yaEnLaVenta >= stock) return `Ya están en la venta las ${stock} unidades que hay de ${repuesto.codigo}.`
  return null
}

/** Si el repuesto ya está, le suma una unidad a su renglón (RF-006); si no, lo agrega al final. */
export function agregarRenglon(renglones, repuesto) {
  const existente = renglones.find((r) => r.varianteId === repuesto.id)
  if (!existente) return [...renglones, renglonDesdeRepuesto(repuesto)]
  return renglones.map((r) => (r.varianteId === repuesto.id ? { ...r, cantidad: r.cantidad + 1 } : r))
}

/** La cantidad escrita: solo dígitos, mínimo 1. Pasar del stock se permite escribirlo y se marca. */
export function cambiarCantidad(renglones, varianteId, texto) {
  const cantidad = Math.max(1, Number(soloDigitos(texto)) || 1)
  return renglones.map((r) => (r.varianteId === varianteId ? { ...r, cantidad, problema: null } : r))
}

export const quitarRenglon = (renglones, varianteId) => renglones.filter((r) => r.varianteId !== varianteId)

// ── Totales ──────────────────────────────────────────────────────────────────

export const subtotalDe = (renglones) => renglones.reduce((s, r) => s + r.precio * r.cantidad, 0)

/** "7,5" o "7.5" → 7.5. `null` si no es un número. */
export function porcentajeDesdeTexto(texto) {
  const limpio = String(texto ?? '').trim().replace(',', '.')
  if (!/^\d+(\.\d+)?$/.test(limpio)) return null
  return Number(limpio)
}

/**
 * El descuento en pesos. En porcentaje, redondeado al peso con HALF_UP, igual que `Descuento` del
 * backend (misma tabla de casos en las dos pruebas). Con enteros: el porcentaje se lleva a
 * centésimas para no pasar por decimales binarios.
 */
export function montoDescuento(descuento, subtotal) {
  if (!descuento) return 0
  if (descuento.modo === 'MONTO') return Number(descuento.valor)
  const centesimas = Math.round(Number(descuento.valor) * 100)
  return Math.floor((subtotal * centesimas + 5000) / 10000)
}

export function totalesDe(venta) {
  const subtotal = subtotalDe(venta.renglones)
  const descuento = montoDescuento(venta.descuento, subtotal)
  return { subtotal, descuento, total: subtotal - descuento }
}

/** Lo que impide cobrar, en frases. Vacío si se puede. */
export function problemasDeLaVenta(venta) {
  const problemas = []
  if (venta.renglones.length === 0) problemas.push('La venta no tiene repuestos')
  for (const r of venta.renglones) {
    if (r.problema) problemas.push(r.problema.texto)
    else if (r.cantidad > r.stock) {
      problemas.push(r.stock === 0 ? `Ya no hay unidades de ${r.codigo}` : `De ${r.codigo} solo hay ${r.stock}`)
    }
  }
  const { subtotal, descuento } = totalesDe(venta)
  if (descuento > subtotal) problemas.push('El descuento es mayor que el total')
  return problemas
}

/**
 * Lo que se le pregunta al servidor para el aviso de venta a pérdida (spec 0004, RF-011). Desde que el cajero no
 * recibe costos la cuenta la hace el servidor (`AvisoDePerdida`, con los mismos casos que tenía esta pantalla).
 */
export function consultaDePerdida(venta) {
  return {
    renglones: venta.renglones.map((r) => ({ varianteId: r.varianteId, cantidad: r.cantidad, precioVisto: r.precio })),
    descuento: venta.descuento
      ? { modo: venta.descuento.modo, valor: venta.descuento.valor, motivo: venta.descuento.motivo ?? null }
      : null,
  }
}

const repuestosSinCosto = (n) => (n === 1 ? '1 repuesto no tiene costo y no cuenta' : `${n} repuestos no tienen costo y no cuentan`)

/**
 * El aviso en palabras (revisado el 2026-09-15: "Queda $3.286 por debajo de lo que costó" no se entendía). Al
 * administrador le dice las dos cifras: con ellas ve de dónde sale. Al cajero, solo que queda por debajo de lo que
 * costaron: el servidor no le manda cuánto (spec 0004, decisión 1).
 *
 * @param aviso la respuesta del servidor: `{ bajoCosto, sinCosto }` y, para el administrador, `costo`, `cobrado` y
 *              `diferencia`. `null` o `bajoCosto: false`: no hay aviso
 */
export function textoDePerdida(aviso) {
  if (!aviso?.bajoCosto) return null
  const { costo, cobrado, diferencia, sinCosto = 0 } = aviso
  if (costo == null) {
    const base = sinCosto === 0 ? 'estos repuestos' : 'los repuestos con costo conocido'
    return `Venta a pérdida: el total queda por debajo de lo que costaron ${base}`
      + (sinCosto === 0 ? '' : `. ${repuestosSinCosto(sinCosto)}.`)
  }
  if (sinCosto === 0) {
    return `Venta a pérdida: estos repuestos te costaron ${formatoCOP(costo)} y cobras ${formatoCOP(cobrado)} `
      + `(pierdes ${formatoCOP(diferencia)})`
  }
  return `Venta a pérdida: los repuestos con costo conocido te costaron ${formatoCOP(costo)} y por ellos cobras `
    + `${formatoCOP(cobrado)} (pierdes ${formatoCOP(diferencia)}). ${repuestosSinCosto(sinCosto)}.`
}

// ── Cobro ────────────────────────────────────────────────────────────────────

/** Los billetes que tiene sentido ofrecer: exacto, y los redondeos y billetes por encima del total. */
export function billetesSugeridos(total) {
  if (total <= 0) return []
  const candidatos = [total]
  // Redondeos que se pagan con un billete o dos: $40.000 sobre $38.000. Múltiplos de $20.000 darían
  // sugerencias raras como $60.000 sobre $50.000.
  for (const multiplo of [10_000, 50_000, 100_000]) {
    candidatos.push(Math.ceil(total / multiplo) * multiplo)
  }
  return [...new Set(candidatos)].sort((a, b) => a - b).slice(0, 4)
}

/** Lo que paga ahora quien se lleva fiado, en pesos: 0 si no escribió nada. */
const pagaAhoraDe = (cobro) => Number(soloDigitos(cobro.pagaAhora)) || 0

/**
 * Los pagos del cobro. `cobro` es lo que se eligió en la pantalla:
 *   { forma: 'EFECTIVO' | 'TRANSFERENCIA' | 'MIXTO' | 'FIADO', recibido: texto, efectivo: texto,
 *     cliente, pagaAhora: texto, formaPagaAhora: 'EFECTIVO' | 'TRANSFERENCIA' }
 * En mixto, `efectivo` es la parte en efectivo y el resto va por transferencia. Fiado (spec 0008): lo que paga
 * ahora, si paga algo, en una sola forma; el resto queda debiendo. Lo fiado no es un pago (decisión 4).
 */
export function pagosDelCobro(cobro, total) {
  if (total <= 0) return []
  const recibido = soloDigitos(cobro.recibido) === '' ? null : Number(soloDigitos(cobro.recibido))
  if (cobro.forma === 'FIADO') {
    const pagaAhora = pagaAhoraDe(cobro)
    if (pagaAhora <= 0) return []
    const forma = cobro.formaPagaAhora === 'TRANSFERENCIA' ? 'TRANSFERENCIA' : 'EFECTIVO'
    return [{ forma, monto: pagaAhora, recibido: null }]
  }
  if (cobro.forma === 'TRANSFERENCIA') return [{ forma: 'TRANSFERENCIA', monto: total, recibido: null }]
  if (cobro.forma === 'MIXTO') {
    const efectivo = Number(soloDigitos(cobro.efectivo)) || 0
    const pagos = []
    if (efectivo > 0) pagos.push({ forma: 'EFECTIVO', monto: efectivo, recibido })
    if (total - efectivo > 0) pagos.push({ forma: 'TRANSFERENCIA', monto: total - efectivo, recibido: null })
    return pagos
  }
  return [{ forma: 'EFECTIVO', monto: total, recibido }]
}

/** Lo que queda debiendo el cliente: en fiado, el total menos lo que paga ahora; si no, 0. */
export function fiadoDelCobro(cobro, total) {
  if (cobro.forma !== 'FIADO' || total <= 0) return 0
  return Math.max(0, total - pagaAhoraDe(cobro))
}

/** `null` si se puede cobrar así; si no, qué falta. */
export function problemaDelCobro(cobro, total) {
  if (total <= 0) return null
  if (cobro.forma === 'FIADO') {
    const porQueNo = porQueNoSeLeFia(cobro.cliente)
    if (porQueNo) return porQueNo
    if (pagaAhoraDe(cobro) >= total) return 'Si paga todo no es fiado: cóbralo en efectivo o por transferencia'
    return null
  }
  const efectivo = pagosDelCobro(cobro, total).find((p) => p.forma === 'EFECTIVO')
  if (cobro.forma === 'MIXTO') {
    const parte = Number(soloDigitos(cobro.efectivo)) || 0
    if (parte <= 0) return 'Escribe cuánto paga en efectivo'
    if (parte >= total) return 'En mixto, la parte en efectivo tiene que ser menor que el total'
  }
  if (efectivo?.recibido != null && efectivo.recibido < efectivo.monto) {
    return `Con ${formatoCOP(efectivo.recibido)} no alcanza: en efectivo son ${formatoCOP(efectivo.monto)}`
  }
  return null
}

/** El cambio que se le devuelve al cliente. 0 si no se escribió lo recibido. */
export function cambioDelCobro(cobro, total) {
  const efectivo = pagosDelCobro(cobro, total).find((p) => p.forma === 'EFECTIVO')
  if (!efectivo || efectivo.recibido == null) return 0
  return Math.max(0, efectivo.recibido - efectivo.monto)
}

/**
 * Lo que se manda a `POST /api/ventas`. El precio que se vio viaja para que no se cobre otro. Con cliente, la venta
 * queda a su nombre; con fiado, lo que queda debiendo (spec 0008).
 */
export function comandoDeCobro(venta, cobro) {
  const { total } = totalesDe(venta)
  return {
    llave: venta.llave,
    renglones: venta.renglones.map((r) => ({ varianteId: r.varianteId, cantidad: r.cantidad, precioVisto: r.precio })),
    descuento: venta.descuento
      ? { modo: venta.descuento.modo, valor: Number(venta.descuento.valor), motivo: venta.descuento.motivo }
      : null,
    pagos: pagosDelCobro(cobro, total),
    clienteId: cobro.cliente?.id ?? null,
    fiado: fiadoDelCobro(cobro, total),
  }
}

// ── Lo que responde el servidor ─────────────────────────────────────────────

const textoDeProblema = (p) => ({
  PRECIO_CAMBIADO: `El precio cambió de ${formatoCOP(p.precioVisto)} a ${formatoCOP(p.precioActual)}. Ya está actualizado: revísalo con el cliente.`,
  SIN_STOCK: p.disponible === 0 ? 'Ya no quedan unidades' : `Solo quedan ${p.disponible}`,
  SIN_PRECIO: 'No tiene precio de venta. Fíjalo en su ficha.',
  INACTIVO: 'Está desactivado: no se vende',
  NO_EXISTE: 'Este repuesto ya no existe',
}[p.tipo] ?? 'No se puede cobrar')

/**
 * Marca los renglones con lo que respondió el servidor (409). El precio cambiado se actualiza al
 * vigente: el cajero lo ve antes de cobrar de nuevo. El stock se actualiza a lo que hay.
 */
export function aplicarProblemas(renglones, problemas) {
  const porRepuesto = new Map((problemas ?? []).map((p) => [p.varianteId, p]))
  return renglones.map((r) => {
    const p = porRepuesto.get(r.varianteId)
    if (!p) return r
    const actualizado = { ...r }
    if (p.tipo === 'PRECIO_CAMBIADO') actualizado.precio = p.precioActual
    if (p.tipo === 'SIN_STOCK') actualizado.stock = p.disponible
    // El precio actualizado ya no bloquea: queda como aviso para que el cajero lo mire.
    actualizado.problema = p.tipo === 'PRECIO_CAMBIADO' ? null : { tipo: p.tipo, texto: `${r.codigo}: ${textoDeProblema(p)}` }
    actualizado.aviso = p.tipo === 'PRECIO_CAMBIADO' ? textoDeProblema(p) : null
    return actualizado
  })
}

/** Al retomar una venta guardada: precio y stock se ponen al día con la ficha actual (RF-029). */
export function refrescarRenglon(renglon, repuesto) {
  const precio = Number(repuesto.precio)
  return {
    ...renglon,
    nombre: repuesto.nombre,
    marca: repuesto.marca,
    stock: Number(repuesto.stock),
    costoPromedio: repuesto.costoPromedio == null ? null : Number(repuesto.costoPromedio),
    precio,
    aviso: precio !== renglon.precio
      ? `El precio cambió de ${formatoCOP(renglon.precio)} a ${formatoCOP(precio)} mientras la venta estaba guardada.`
      : renglon.aviso ?? null,
  }
}

// ── Guardar y retomar ────────────────────────────────────────────────────────

export const serializarVenta = (venta) => JSON.stringify(venta)

/** `null` si lo guardado no es una venta con repuestos: una venta vacía no se ofrece retomar. */
export function restaurarVenta(texto) {
  if (!texto) return null
  try {
    const v = JSON.parse(texto)
    const valida = v && typeof v.llave === 'string' && Array.isArray(v.renglones) && v.renglones.length > 0
      && v.renglones.every((r) => typeof r.varianteId === 'string' && Number(r.cantidad) > 0 && Number.isFinite(Number(r.precio)))
    return valida ? { ...ventaNueva(v.llave), ...v } : null
  } catch {
    return null
  }
}

export const AVISO_RETOMADA = 'Se retomó la venta que quedó sin terminar. Si no la quieres, Cancelar venta la descarta.'

/**
 * Cómo arranca Vender con lo que había guardado (spec 0003, RF-028, revisado el 2026-09-16): **la venta se
 * retoma sola, sin preguntar**. El aviso que obligaba a elegir entre retomar y descartar bloqueaba el buscador
 * y el catálogo; ahora la venta aparece armada como estaba, y *Cancelar venta* la descarta.
 *
 * Una venta que se mandó a cobrar y no tuvo respuesta vuelve igual, **bloqueada**: solo se reintenta ese mismo
 * cobro, con su misma llave. Es lo que evita cobrar dos veces. No se le revisan precios: se reintenta exactamente
 * lo que se mandó.
 *
 * @param guardada la de `restaurarVenta`, o `null`
 * @return `{ venta, aviso, revisarPrecios }` — `revisarPrecios`: hay que poner al día precio y stock (RF-029)
 */
export function ventaAlVolver(guardada) {
  if (!guardada || guardada.renglones.length === 0) return { venta: ventaNueva(), aviso: null, revisarPrecios: false }
  if (guardada.enviada) return { venta: guardada, aviso: null, revisarPrecios: false }
  return { venta: guardada, aviso: AVISO_RETOMADA, revisarPrecios: true }
}

/**
 * RF-029: pone al día precio y stock con las fichas que llegaron, **por repuesto** y no por posición: si el
 * cajero agregó o quitó algo mientras llegaban, cada ficha cae en su renglón. Una ficha que no llegó (`null`) deja
 * el renglón como estaba; se revisa al cobrar.
 */
export function refrescarRenglones(renglones, fichas) {
  const porId = new Map(fichas.filter(Boolean).map((f) => [f.id, f]))
  return renglones.map((r) => (porId.has(r.varianteId) ? refrescarRenglon(r, porId.get(r.varianteId)) : r))
}

export const unidadesDe = (renglones) => renglones.reduce((s, r) => s + r.cantidad, 0)
