/**
 * Lógica del historial de compras: filtros ⇄ URL, fechas, y la comprobación de que el total de una
 * factura cuadre con sus renglones. Sin JSX, para probarla con `node --test`.
 */
import { paginaDeLaUrl } from './inventario.js'

export const TAMANO_HISTORIAL = 25

/** Los filtros que viven en la URL, con el mismo nombre que espera el backend. */
export const CLAVES_FILTRO = ['proveedorId', 'desde', 'hasta', 'formaPago', 'cuentaId', 'factura', 'repuesto']

/**
 * Por defecto se ven las vigentes (spec 0002, RF-006). "Todas" no existe para el backend: es no
 * mandar el estado.
 */
export const ESTADO_POR_DEFECTO = 'VIGENTE'
export const TODAS = 'TODAS'

/**
 * Lee los filtros de la URL. Lo que falta queda en `''`, que es lo que un `<select>` o un
 * `<input>` controlado necesita para no quejarse.
 */
export function filtrosDesdeUrl(params) {
  const filtros = {}
  for (const clave of CLAVES_FILTRO) filtros[clave] = params.get(clave) ?? ''
  filtros.estado = params.get('estado') || ESTADO_POR_DEFECTO
  filtros.pagina = paginaDeLaUrl(params.get('p'))
  return filtros
}

/**
 * Deja los filtros coherentes: una compra en efectivo no sale de ninguna cuenta, así que filtrar
 * "efectivo" desde una cuenta siempre daría vacío sin que se entienda por qué.
 */
export function normalizarFiltros(filtros) {
  return filtros.formaPago === 'EFECTIVO' ? { ...filtros, cuentaId: '' } : filtros
}

/** `null` si el rango sirve. Las fechas llegan como `2026-09-01`, que se comparan bien como texto. */
export function problemaDeFechas(desde, hasta) {
  if (desde && hasta && desde > hasta) return 'La fecha inicial es posterior a la final'
  return null
}

/** Ver las vigentes es lo normal: no cuenta como filtro. Ver anuladas o todas, sí. */
export const hayFiltros = (filtros) =>
  CLAVES_FILTRO.some((clave) => filtros[clave])
  || (filtros.estado != null && filtros.estado !== ESTADO_POR_DEFECTO)

/** Lo que se le pide al backend: solo los filtros con valor, más la página. */
export function consultaDelHistorial(filtros, tamano = TAMANO_HISTORIAL) {
  const consulta = {}
  for (const clave of CLAVES_FILTRO) {
    const valor = String(filtros[clave] ?? '').trim()
    if (valor) consulta[clave] = valor
  }
  const estado = filtros.estado || ESTADO_POR_DEFECTO
  if (estado !== TODAS) consulta.estado = estado
  consulta.pagina = filtros.pagina ?? 0
  consulta.tamano = tamano
  return consulta
}

/**
 * La dirección del detalle de una compra. Si la lista está buscando un repuesto, la búsqueda viaja en
 * la dirección (spec 0002, RF-026): así el detalle la resalta, y recargar la página no la pierde.
 */
export function rutaDelDetalle(id, repuesto) {
  const texto = String(repuesto ?? '').trim()
  return `/compras/historial/${id}${texto ? `?${new URLSearchParams({ repuesto: texto })}` : ''}`
}

/**
 * La misma tabla de letras que `TextoDeBusqueda` en el backend: sin tildes, sin diéresis, sin eñe.
 * Es una letra por otra, así las posiciones del texto original y del normalizado coinciden y se puede
 * resaltar el pedazo original ("BUJÍA") aunque se haya buscado "bujia".
 */
const CON_TILDE = 'áàäâãéèëêíìïîóòöôõúùüûñç'
const SIN_TILDE = 'aaaaaeeeeiiiiooooouuuunc'

/** Minúsculas y sin tildes, letra por letra: el resultado tiene el mismo largo que el original. */
export function sinTildes(texto) {
  let resultado = ''
  for (const letra of String(texto ?? '').split('')) {
    const minuscula = letra.toLowerCase()
    // Si pasar a minúscula cambia el largo (letras raras), se deja la original: no se corren posiciones.
    const base = minuscula.length === 1 ? minuscula : letra
    const posicion = CON_TILDE.indexOf(base)
    resultado += posicion < 0 ? base : SIN_TILDE[posicion]
  }
  return resultado
}

/**
 * Parte un texto en pedazos para dibujar lo buscado resaltado: "FILTRO INOKI" con "inoki" da
 * [{ "FILTRO ", no }, { "INOKI", sí }]. Sin distinguir mayúsculas ni tildes, todas las veces.
 *
 * Solo sirve para DIBUJAR. Qué renglón coincide lo decide el backend (`coincide`), con la misma regla
 * que filtró la lista: si la pantalla decidiera por su cuenta, podría no resaltar una compra que sí
 * salió.
 */
export function partesResaltadas(texto, busqueda) {
  const valor = texto ?? ''
  const buscado = String(busqueda ?? '').trim()
  if (!valor) return []
  if (!buscado) return [{ texto: valor, resaltado: false }]

  // Se busca en las versiones normalizadas y se corta el original en las mismas posiciones. Con
  // indexOf lo escrito se busca tal cual: un "." o un "(" no son instrucciones.
  const donde = sinTildes(valor)
  const que = sinTildes(buscado)
  const partes = []
  let desde = 0
  for (let i = donde.indexOf(que); i >= 0; i = donde.indexOf(que, i + que.length)) {
    if (i > desde) partes.push({ texto: valor.slice(desde, i), resaltado: false })
    partes.push({ texto: valor.slice(i, i + que.length), resaltado: true })
    desde = i + que.length
  }
  if (desde < valor.length) partes.push({ texto: valor.slice(desde), resaltado: false })
  return partes
}

/** Si lo buscado se ve en ese texto. Para saber si hay que mostrar la aplicación de un renglón. */
export const muestraLaBusqueda = (texto, busqueda) =>
  partesResaltadas(texto, busqueda).some((p) => p.resaltado)

/** Cuántos repuestos que coinciden se muestran debajo de una factura antes de "y N más". */
export const COINCIDENCIAS_VISIBLES = 3

/**
 * Lo que se muestra debajo de una factura de la lista al buscar un repuesto (spec 0002, RF-027):
 * "FILTRO DE ACEITE INOKI × 10 · FILTRO ACEITE FACTORY × 5 · y 2 más".
 *
 * Cuáles coinciden lo decide el backend (`coinciden`), con la misma regla que resalta el detalle. Aquí
 * solo se decide cuántos caben y, si lo buscado no se ve en el nombre ni en la marca, **por qué salió**:
 * el código si coincidió por el código, o la moto si coincidió por la aplicación. Sin eso, buscar
 * "duke" mostraría "FILTRO ACEITE INOKI" y no se entendería qué tiene que ver.
 */
export function coincidenciasParaMostrar(coinciden, busqueda, maximo = COINCIDENCIAS_VISIBLES) {
  const lista = coinciden ?? []
  return {
    visibles: lista.slice(0, maximo).map((r) => {
      const seVe = muestraLaBusqueda(r.nombre, busqueda) || muestraLaBusqueda(r.marca, busqueda)
      return {
        lineaId: r.lineaId,
        descripcion: [r.nombre, r.marca].filter(Boolean).join(' '),
        cantidad: r.cantidad,
        porque: seVe ? null : (muestraLaBusqueda(r.codigo, busqueda) ? r.codigo : r.aplicacion) ?? null,
      }
    }),
    restantes: Math.max(lista.length - maximo, 0),
  }
}

/**
 * La línea debajo de cada factura de la lista (spec 0002, RF-027 y RF-028):
 *   · buscando un repuesto, los que coinciden, resaltados, y por qué salió cada uno (RF-027);
 *   · sin buscar, sus primeros renglones vigentes en el orden de la factura: "FILTRO DE ACEITE INOKI × 25 ·
 *     PASTILLAS FRENO CBI × 4 · y 1 más" (RF-028). Cuántos más sale de `renglones`, que cuenta los vigentes.
 *
 * @return `{ visibles, restantes, resaltar }` — `resaltar` es falso sin búsqueda: no hay nada que marcar
 */
export function lineaDeLaFactura({ primeros, renglones, coinciden }, busqueda) {
  if (String(busqueda ?? '').trim()) {
    return { ...coincidenciasParaMostrar(coinciden, busqueda), resaltar: true }
  }
  const lista = primeros ?? []
  return {
    visibles: lista.map((r) => ({
      lineaId: r.lineaId,
      descripcion: [r.nombre, r.marca].filter(Boolean).join(' '),
      cantidad: r.cantidad,
      porque: null,
    })),
    restantes: Math.max((renglones ?? 0) - lista.length, 0),
    resaltar: false,
  }
}

/**
 * Qué le pasó a un renglón que ya no está vigente: 'CAMBIADO' si otra versión ocupa su lugar, o
 * 'QUITADO' si salió de la factura.
 *
 * Una corrección pone la versión nueva en la misma posición de la vieja, y un renglón agregado va
 * siempre al final (nunca reusa la posición de uno quitado). Así que se cambió si existe una versión
 * posterior en su posición: la vigente, o una reemplazada después que él. Sin eso, en la cadena
 * "12 → 17 → quitado" el de 12 se leería como quitado, y no lo fue: se cambió, y el que se quitó
 * fue el de 17.
 */
export function queLePasoAlRenglon(renglon, detalle) {
  const mismaPosicion = (r) => r.lineaId !== renglon.lineaId && r.posicion === renglon.posicion
  const hayVigente = (detalle.renglones ?? []).some(mismaPosicion)
  const hayPosterior = (detalle.reemplazados ?? []).some((r) => mismaPosicion(r)
    && new Date(r.reemplazadaEn) > new Date(renglon.reemplazadaEn))
  return hayVigente || hayPosterior ? 'CAMBIADO' : 'QUITADO'
}

/**
 * Suma de lo pagado por renglón. El detalle la compara contra el total de la compra: si no
 * cuadran, algo quedó mal guardado y la pantalla lo dice en vez de esconderlo.
 */
export const sumaDeRenglones = (renglones) =>
  (renglones ?? []).reduce((suma, r) => suma + Number(r.costoTotal ?? 0), 0)

/**
 * Lo que se pide para los totales (spec 0002, H6): los mismos filtros de la lista, sin página,
 * porque los totales son de todas las páginas.
 *
 * `null` si solo se ven anuladas: una anulada nunca suma (RF-010), así que no hay nada que pedir.
 */
export function consultaDeTotales(filtros) {
  if ((filtros.estado || ESTADO_POR_DEFECTO) === 'ANULADA') return null
  const consulta = consultaDelHistorial(filtros)
  delete consulta.pagina
  delete consulta.tamano
  return consulta
}

/**
 * Los totales como se pintan: el total, lo pagado en efectivo, y lo transferido con su desglose por
 * cuenta (de la que más movió a la que menos).
 *
 * `descuadre` es lo que las partes se pasan (o les falta) del total. El backend saca las partes y
 * el total con consultas distintas; si no cuadran, la pantalla lo dice en vez de esconderlo.
 */
export function resumenDeTotales(totales) {
  if (!totales) return null
  const partes = totales.partes ?? []
  const sumar = (lista, campo) => lista.reduce((suma, p) => suma + Number(p[campo] ?? 0), 0)
  const efectivo = partes.filter((p) => p.formaPago === 'EFECTIVO')
  const transferencias = partes.filter((p) => p.formaPago === 'TRANSFERENCIA')

  return {
    total: Number(totales.total ?? 0),
    compras: Number(totales.compras ?? 0),
    efectivo: { total: sumar(efectivo, 'total'), compras: sumar(efectivo, 'compras') },
    transferencia: {
      total: sumar(transferencias, 'total'),
      compras: sumar(transferencias, 'compras'),
      cuentas: transferencias
        .map((p) => ({ id: p.cuentaId, nombre: p.cuenta, total: Number(p.total), compras: Number(p.compras) }))
        .sort((a, b) => b.total - a.total || String(a.nombre).localeCompare(String(b.nombre), 'es')),
    },
    descuadre: sumar(partes, 'total') - Number(totales.total ?? 0),
  }
}
