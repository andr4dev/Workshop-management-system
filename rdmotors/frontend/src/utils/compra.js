import { soloDigitos } from './formato.js'

/**
 * La categoría del último repuesto nuevo de la compra (spec 0005, RF-010). El formulario del siguiente
 * arranca con ella: una factura de Jotapartes trae veinte filtros seguidos, y escoger "FILTROS" veinte
 * veces es el clic de más que haría pesada la categoría obligatoria. Un repuesto que reutiliza un
 * concepto existente no escoge categoría, así que no cuenta. `null` si no hay ninguno.
 */
export function ultimaCategoriaElegida(renglones) {
  const conCategoria = (renglones ?? []).filter((r) => r.repuestoNuevo?.categoriaId)
  return conCategoria.length > 0 ? conCategoria[conCategoria.length - 1].repuestoNuevo.categoriaId : null
}

/**
 * La última compra de un repuesto, sacada de su kardex.
 *
 * Sirve para rellenar el renglón con lo que se pagó la vez anterior: casi todas las compras
 * repiten proveedor y costo, y así el administrador solo toca lo que cambió.
 *
 * @param movimientos el kardex tal como lo devuelve el backend: del MÁS RECIENTE al más antiguo.
 * @returns `null` si nunca se ha comprado.
 */
export function ultimaCompraDe(movimientos) {
  // Solo COMPRA: una VENTA también trae costo unitario, pero es el promedio vigente, no lo que
  // se le pagó a un proveedor. Sugerir ese número sería sugerir un costo que nadie facturó.
  const m = (movimientos ?? []).find((x) => x.tipo === 'COMPRA' && x.costoUnitario != null)
  if (!m) return null

  return {
    // Redondeado para el campo, que acepta solo dígitos. `soloDigitos("13333.3333")` daría
    // "133333333": mil veces el costo real.
    costoUnitario: Math.round(Number(m.costoUnitario)),
    // Exacto, para mostrar de dónde salió.
    costoExacto: Number(m.costoUnitario),
    cuando: m.cuando,
  }
}

/**
 * Qué precio de venta se manda al registrar la compra.
 *
 * El campo llega relleno con el precio actual. Si nadie lo cambió se manda `null`, que en el
 * backend significa "no tocar el precio". Mandar el mismo valor no cambiaría el precio, pero sí
 * dejaría un precio anotado en ese renglón de la compra, y entonces ya no se podría saber en qué
 * compras se cambió el precio de verdad.
 */
export function precioParaEnviar(precioEscrito, precioActual) {
  const escrito = Number(soloDigitos(precioEscrito)) || null
  if (escrito == null || escrito === Number(precioActual)) return null
  return escrito
}

/** "13 ago". Vacío si no hay fecha. */
export function fechaCorta(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString('es-CO', { day: 'numeric', month: 'short' })
}

/** Código del repuesto de un renglón, sea uno que ya existe o uno que se va a crear. */
export const codigoDelRenglon = (r) => r.repuesto?.codigo ?? r.repuestoNuevo?.codigo ?? null

/**
 * Renglones cuyo repuesto ya aparece en un renglón anterior de la misma compra.
 *
 * El backend rechaza el mismo repuesto dos veces en una compra: dos renglones podrían traer dos
 * precios de venta distintos para el mismo repuesto y no habría forma de saber cuál vale. Esto lo
 * detecta mientras se captura, en vez de al registrar, cuando ya se llenó todo.
 *
 * @returns Map: id del renglón repetido → id del renglón donde aparece primero.
 */
export function renglonesRepetidos(renglones) {
  const primero = new Map()
  const repetidos = new Map()
  for (const r of renglones) {
    const codigo = codigoDelRenglon(r)
    if (!codigo) continue
    if (primero.has(codigo)) repetidos.set(r.id, primero.get(codigo))
    else primero.set(codigo, r.id)
  }
  return repetidos
}

/** Un renglón sin nada escrito. Se ignora al registrar (suele ser el último, el que queda libre). */
export function estaVacio(r) {
  return !r.codigo?.trim() && !r.repuesto && !r.repuestoNuevo && !r.cantidad
    && !r.costoTotal && !r.costoUnitario && !r.precioVenta
}

/**
 * Qué le falta a un renglón para poder registrarse. `null` si está completo o vacío.
 *
 * Al registrar, un renglón con datos pero incompleto NO se descarta en silencio: se detiene el
 * registro y se dice qué falta. Descartarlo guardaría la compra sin esas unidades y el usuario
 * creería que sí entraron.
 */
export function problemaDelRenglon(r) {
  if (estaVacio(r)) return null
  if (!r.repuesto && !r.repuestoNuevo) return 'Falta el repuesto: teclea el código y pulsa Enter'
  if (!(Number(r.cantidad) > 0)) return 'Falta la cantidad'
  const costo = Number(soloDigitos(r.modo === 'TOTAL' ? r.costoTotal : r.costoUnitario))
  if (!(costo > 0)) return 'Falta el costo de compra'
  if (r.repuestoNuevo && !(Number(soloDigitos(r.precioVenta)) > 0)) return 'Falta el precio de venta'
  return null
}

/**
 * Qué le falta a la forma de pago de la compra. Se calcula al pintar: al elegir, el aviso se va.
 *
 * No hay forma de pago por defecto. Un "efectivo" puesto de antemano se registra sin que nadie lo
 * haya decidido, y el reporte lo cree.
 *
 * @returns `{ formaPago, cuenta }` con el mensaje de cada campo, o `null` en los que están bien.
 */
export function problemasDelPago({ formaPago, cuentaId }) {
  return {
    formaPago: formaPago ? null : 'Elige cómo se pagó',
    cuenta: formaPago === 'TRANSFERENCIA' && !cuentaId ? 'Elige desde qué cuenta salió' : null,
  }
}

/**
 * "Efectivo", "Efectivo · del cajón" o "Transferencia · Nequi del dueño". Vacío si todavía no se eligió.
 *
 * @param pagadaDeCaja si se pagó con plata del cajón (spec 0006): esa compra resta del arqueo de su turno
 */
export function textoDelPago(formaPago, cuenta, pagadaDeCaja = false) {
  if (formaPago === 'EFECTIVO') return pagadaDeCaja ? 'Efectivo · del cajón' : 'Efectivo'
  if (formaPago === 'TRANSFERENCIA') return cuenta ? `Transferencia · ${cuenta}` : 'Transferencia'
  return ''
}


/**
 * Qué se puede hacer con «Se pagó con plata del cajón» (spec 0006, RF-008 y RF-010). Solo existe en efectivo.
 *
 *   - Sin turno abierto no se marca: la compra no tendría de cuál arqueo salir.
 *   - Al corregir una compra que se pagó así en un turno que YA CERRÓ, ni la marca ni la forma de pago cambian
 *     (decisión 8 del plan): ese arqueo se firmó con la compra adentro.
 *
 * @param correccion  el detalle de la compra que se corrige, o null si se registra una nueva
 * @param turnoAbierto el turno abierto (`GET /api/turnos/abierto`), o null
 * @returns {{ visible, bloqueada, porQue }} `porQue` explica por qué no se puede tocar; null si se puede
 */
export function opcionDeCajon({ formaPago, correccion = null, turnoAbierto = null }) {
  const suTurnoCerro = Boolean(correccion?.pagadaDeCaja) && correccion.turnoId !== turnoAbierto?.id
  if (suTurnoCerro) {
    return {
      visible: true,
      bloqueada: true,
      bloqueaFormaDePago: true,
      porQue: 'Se pagó con plata del cajón en un turno que ya se cerró: no se cambia, ni su forma de pago.',
    }
  }
  if (formaPago !== 'EFECTIVO') return { visible: false, bloqueada: true, bloqueaFormaDePago: false, porQue: null }
  if (!turnoAbierto && !correccion?.pagadaDeCaja) {
    return {
      visible: true,
      bloqueada: true,
      bloqueaFormaDePago: false,
      porQue: 'No hay un turno abierto: para marcarla, abre el turno en Vender.',
    }
  }
  return { visible: true, bloqueada: false, bloqueaFormaDePago: false, porQue: null }
}

/**
 * El costo se escribe CON el IVA incluido (spec 0012, decisión 1 y RF-020): es la misma regla de la carga desde la
 * factura, y si las compras a mano lo metieran sin IVA el costo promedio de un repuesto mezclaría las dos cosas.
 *
 * Cuando la factura trae el IVA aparte, esto se lo suma a los costos ya escritos, redondeado al peso. Devuelve los
 * renglones nuevos y los de antes, para poder deshacerlo.
 */
export function sumarIva(renglones, porcentaje = 19) {
  const factor = 1 + porcentaje / 100
  const conIva = (texto) => {
    const digitos = String(texto ?? '').replace(/\D/g, '')
    return digitos ? String(Math.round(Number(digitos) * factor)) : texto
  }
  return renglones.map((r) => (r.modo === 'TOTAL'
    ? { ...r, costoTotal: conIva(r.costoTotal) }
    : { ...r, costoUnitario: conIva(r.costoUnitario) }))
}

/** Si algún renglón tiene un costo escrito: sin eso no hay a qué sumarle el IVA. */
export const hayCostosEscritos = (renglones) =>
  renglones.some((r) => String(r.modo === 'TOTAL' ? r.costoTotal : r.costoUnitario ?? '').replace(/\D/g, '') !== '')
