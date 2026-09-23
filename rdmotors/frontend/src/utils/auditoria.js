/**
 * Convierte el antes y el después de una compra en un desglose que se entiende de un vistazo.
 *
 * Se usa en dos sitios con las mismas fotos: el modal que confirma una corrección ("esto es lo que
 * vas a cambiar") y el rastro del detalle ("esto es lo que cambió"). Una sola función para los dos,
 * para que lo que se confirmó y lo que quedó registrado se lean igual.
 *
 * La foto tiene la forma que arma `Compra.fotografia()` en el backend:
 *   { estado, proveedor, fechaDocumento, numeroFactura, formaPago, cuenta, pagadaDeCaja, total,
 *     renglones: [{ codigo, repuesto, cantidad, modo, costoUnitario, costoTotal, precioVenta }] }
 *
 * Devuelve textos ya formateados, no números: la pantalla solo los acomoda.
 */
import { textoDelPago } from './compra.js'
import { fechaDia, formatoCOP, formatoCosto } from './formato.js'
import { fechaLocal } from './inventario.js'

const ESTADOS = { VIGENTE: 'Vigente', ANULADA: 'Anulada' }

const textoFecha = (iso) => (iso ? fechaDia(fechaLocal(iso)) : '—')
const textoPrecio = (precio) => (precio == null ? 'no lo cambia' : formatoCOP(precio))
const unidades = (n) => `${n} ${Math.abs(Number(n)) === 1 ? 'unidad' : 'unidades'}`

/** El costo por unidad sale del total: es el mismo cálculo de los dos lados, se capture como se capture. */
function textoUnitario(renglon) {
  const cantidad = Number(renglon.cantidad)
  if (!(cantidad > 0)) return '—'
  const unitario = Number(renglon.costoTotal) / cantidad
  // $2.500 se lee mejor que $2.500,00: los centavos solo cuando los hay.
  return Math.abs(unitario - Math.round(unitario)) < 0.005
    ? formatoCOP(Math.round(unitario))
    : formatoCosto(unitario)
}

/**
 * @returns {{
 *   factura: { etiqueta, antes, despues }[],
 *   renglones: { codigo, repuesto, tipo: 'cambiado'|'agregado'|'quitado',
 *                filas: { etiqueta, antes, despues, igual? }[], inventario }[],
 *   total: { antes, despues, diferencia } | null,
 *   vacio: boolean,
 * }}
 * En un renglón agregado `antes` es null; en uno quitado, `despues`. `igual` marca una fila que no
 * cambió pero se muestra porque explica las demás: el costo por unidad cuando cambia la cantidad.
 */
export function desgloseDeCambios(antes, despues) {
  if (!antes || !despues) return { factura: [], renglones: [], total: null, vacio: true }

  const factura = []
  const campo = (etiqueta, a, b, texto = (x) => x ?? '—') => {
    if ((a ?? null) !== (b ?? null)) factura.push({ etiqueta, antes: texto(a), despues: texto(b) })
  }
  campo('Estado', antes.estado, despues.estado, (e) => ESTADOS[e] ?? e)
  campo('Proveedor', antes.proveedor, despues.proveedor)
  campo('Fecha de la factura', antes.fechaDocumento, despues.fechaDocumento, textoFecha)
  campo('N.º de factura', antes.numeroFactura, despues.numeroFactura, (n) => n ?? 'sin número')
  // Las fotos de antes del spec 0006 no dicen si fue del cajón: no lo fue.
  const pagoAntes = textoDelPago(antes.formaPago, antes.cuenta, Boolean(antes.pagadaDeCaja))
  const pagoDespues = textoDelPago(despues.formaPago, despues.cuenta, Boolean(despues.pagadaDeCaja))
  if (pagoAntes !== pagoDespues) factura.push({ etiqueta: 'Pago', antes: pagoAntes, despues: pagoDespues })

  const renglones = desgloseDeRenglones(antes.renglones ?? [], despues.renglones ?? [])

  const diferencia = Number(despues.total ?? 0) - Number(antes.total ?? 0)
  const total = diferencia === 0 ? null : {
    antes: formatoCOP(antes.total),
    despues: formatoCOP(despues.total),
    // El menos es el tipográfico (−), no un guion: junto a una cifra, el guion se pierde.
    diferencia: `${diferencia > 0 ? '+' : '−'}${formatoCOP(Math.abs(diferencia))}`,
  }

  return { factura, renglones, total, vacio: factura.length === 0 && renglones.length === 0 && !total }
}

/**
 * Por código: el repuesto es lo que el administrador reconoce en la factura. Primero los renglones
 * en el orden en que quedan, y al final los que se quitan.
 */
function desgloseDeRenglones(antes, despues) {
  const porCodigoAntes = new Map(antes.map((r) => [r.codigo, r]))
  const codigosDespues = new Set(despues.map((r) => r.codigo))
  const grupos = []

  for (const nuevo of despues) {
    const viejo = porCodigoAntes.get(nuevo.codigo)
    if (!viejo) {
      grupos.push({
        codigo: nuevo.codigo,
        repuesto: nuevo.repuesto,
        tipo: 'agregado',
        filas: [
          { etiqueta: 'Cantidad', antes: null, despues: String(nuevo.cantidad) },
          { etiqueta: 'Total pagado', antes: null, despues: formatoCOP(nuevo.costoTotal) },
          { etiqueta: 'Costo por unidad', antes: null, despues: textoUnitario(nuevo) },
          ...(nuevo.precioVenta == null ? []
            : [{ etiqueta: 'Precio de venta', antes: null, despues: formatoCOP(nuevo.precioVenta) }]),
        ],
        inventario: `+${unidades(nuevo.cantidad)}`,
      })
      continue
    }

    const cambiaCantidad = Number(viejo.cantidad) !== Number(nuevo.cantidad)
    const cambiaTotal = Number(viejo.costoTotal) !== Number(nuevo.costoTotal)
    const cambiaPrecio = (viejo.precioVenta ?? null) !== (nuevo.precioVenta ?? null)
    if (!cambiaCantidad && !cambiaTotal && !cambiaPrecio) continue

    const filas = []
    if (cambiaCantidad) {
      filas.push({ etiqueta: 'Cantidad', antes: String(viejo.cantidad), despues: String(nuevo.cantidad) })
    }
    if (cambiaTotal) {
      filas.push({
        etiqueta: 'Total pagado', antes: formatoCOP(viejo.costoTotal), despues: formatoCOP(nuevo.costoTotal),
      })
    }
    if (cambiaCantidad || cambiaTotal) {
      // Es la cifra que mueve el costo promedio. Si quedó igual, decirlo tranquiliza: 12 por $30.000
      // y 17 por $42.500 es el mismo precio, solo que llegaron más.
      const unitarioAntes = textoUnitario(viejo)
      const unitarioDespues = textoUnitario(nuevo)
      filas.push({
        etiqueta: 'Costo por unidad', antes: unitarioAntes, despues: unitarioDespues,
        igual: unitarioAntes === unitarioDespues,
      })
    }
    if (cambiaPrecio) {
      filas.push({
        etiqueta: 'Precio de venta', antes: textoPrecio(viejo.precioVenta), despues: textoPrecio(nuevo.precioVenta),
      })
    }

    const diferencia = Number(nuevo.cantidad) - Number(viejo.cantidad)
    grupos.push({
      codigo: nuevo.codigo,
      repuesto: nuevo.repuesto ?? viejo.repuesto,
      tipo: 'cambiado',
      filas,
      inventario: diferencia > 0 ? `+${unidades(diferencia)}`
        : diferencia < 0 ? `−${unidades(-diferencia)}`
          : 'sin cambio',
    })
  }

  for (const viejo of antes) {
    if (codigosDespues.has(viejo.codigo)) continue
    grupos.push({
      codigo: viejo.codigo,
      repuesto: viejo.repuesto,
      tipo: 'quitado',
      filas: [
        { etiqueta: 'Cantidad', antes: String(viejo.cantidad), despues: null },
        { etiqueta: 'Total pagado', antes: formatoCOP(viejo.costoTotal), despues: null },
      ],
      inventario: `−${unidades(viejo.cantidad)}`,
    })
  }
  return grupos
}

/**
 * Lo mismo para la ficha de un repuesto (spec 0002, H7). La foto es la que arma
 * `Variante.fotografiaDeFicha()`: { codigo, nombre, categoria, aplicacion, marca, precio, stockMinimo }.
 *
 * El stock y el costo no están: no se corrigen desde la ficha, los mueve el kardex.
 */
export function cambiosDeFicha(antes, despues) {
  if (!antes || !despues) return []
  const cambios = []

  const campo = (etiqueta, clave, texto = (x) => x ?? '—') => {
    const a = antes[clave] ?? null
    const b = despues[clave] ?? null
    // El jsonb puede devolver 5 donde se guardó 5.0: las cifras se comparan como número.
    const iguales = typeof a === 'number' || typeof b === 'number' ? Number(a) === Number(b) : a === b
    if (!iguales) cambios.push(`${etiqueta}: ${texto(a)} → ${texto(b)}`)
  }

  campo('Código', 'codigo')
  campo('Nombre', 'nombre')
  campo('Categoría', 'categoria', (c) => c ?? 'Sin clasificar')
  campo('Aplicación', 'aplicacion')
  campo('Marca', 'marca')
  campo('Precio de venta', 'precio', (p) => (p == null ? '—' : formatoCOP(p)))
  campo('Avisar con', 'stockMinimo', (n) => (n == null ? '—' : `${n} o menos`))
  return cambios
}
