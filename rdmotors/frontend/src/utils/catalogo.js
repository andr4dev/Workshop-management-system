/**
 * El catálogo del mostrador (spec 0005): lo que decide qué categorías se muestran, qué se le pide al
 * servidor, cómo se juntan las páginas y qué se puede agregar. Puro y con pruebas; `Catalogo.jsx` solo
 * pinta.
 */

/** RF-007: de a 50. Una categoría grande (Motor es el 21% del catálogo de Jotapartes) no se trae entera. */
export const TAMANO_CATALOGO = 50

export const TODAS = 'todas'
export const SIN_CATEGORIA = 'sin'

/** "LUBRICANTES Y QUIMICOS" → "Lubricantes y quimicos": las categorías sembradas vienen en mayúsculas. */
export function nombreDeCategoria(nombre) {
  const texto = String(nombre ?? '').trim().toLowerCase()
  return texto ? texto[0].toUpperCase() + texto.slice(1) : ''
}

/**
 * Los botones de categorías (RF-002), a partir de lo que cuenta el servidor para lo buscado:
 * *Todas* primero con la suma, luego las categorías en su orden, y *Sin categoría* al final. Una
 * categoría con 0 no se muestra.
 *
 * @param conteos `GET /api/inventario/categorias`: `{ categoriaId, nombre, repuestos }`, ya en orden
 */
export function chipsDeCategorias(conteos) {
  const conAlgo = (conteos ?? []).filter((c) => c.repuestos > 0)
  const total = conAlgo.reduce((suma, c) => suma + c.repuestos, 0)
  return [
    { clave: TODAS, nombre: 'Todas', repuestos: total },
    ...conAlgo.filter((c) => c.categoriaId)
      .map((c) => ({ clave: c.categoriaId, nombre: nombreDeCategoria(c.nombre), repuestos: c.repuestos })),
    ...conAlgo.filter((c) => !c.categoriaId)
      .map((c) => ({ clave: SIN_CATEGORIA, nombre: 'Sin categoría', repuestos: c.repuestos })),
  ]
}

/**
 * La categoría que de verdad se filtra. Si se eligió *Frenos* y después se escribe *aceite*, Frenos ya
 * no tiene nada de lo buscado y su botón desaparece: filtrar por ella dejaría la lista vacía sin que se
 * vea por qué. Se vuelve a *Todas*. Mientras los números no han llegado, se respeta la elegida.
 */
export function categoriaVigente(chips, elegida) {
  if (!chips) return elegida
  return chips.some((c) => c.clave === elegida) ? elegida : TODAS
}

/** Lo que se le pide al listado del inventario: la categoría, y los que hay primero (RF-005). */
export function consultaDelCatalogo({ texto = '', categoria = TODAS, pagina = 0 }) {
  return {
    texto,
    categoriaId: categoria !== TODAS && categoria !== SIN_CATEGORIA ? categoria : '',
    sinCategoria: categoria === SIN_CATEGORIA,
    conStockPrimero: true,
    pagina,
    tamano: TAMANO_CATALOGO,
  }
}

/**
 * *Ver más* (RF-007): la página nueva se agrega debajo, sin repetir un repuesto que ya estaba (entre dos
 * pedidos pudo cambiar el stock y moverse de lugar).
 *
 * @param anteriores lo ya visto, o `null` si es la primera página
 * @param pagina     la respuesta del listado: `{ elementos, total, numero }`
 */
export function juntarPaginas(anteriores, pagina) {
  const vistos = new Set((anteriores?.elementos ?? []).map((r) => r.id))
  const elementos = [...(anteriores?.elementos ?? []), ...pagina.elementos.filter((r) => !vistos.has(r.id))]
  return {
    elementos,
    total: pagina.total,
    siguiente: pagina.numero + 1,
    hayMas: pagina.elementos.length > 0 && elementos.length < pagina.total,
  }
}

/** Flechas en la lista: sin resaltado, ↓ va al primero; no da la vuelta en los bordes. */
export function moverResaltado(indice, largo, tecla) {
  if (largo === 0) return -1
  if (tecla === 'ArrowDown') return Math.min(indice + 1, largo - 1)
  if (tecla === 'ArrowUp') return indice <= 0 ? 0 : indice - 1
  return indice
}

/**
 * Si el botón [+] está activo. Sin stock o sin precio no se agrega (RF-005); con la venta bloqueada (un
 * cobro sin respuesta, una venta pendiente, sin turno) se ve pero no se agrega (RF-008). Las demás
 * reglas —no pasar de lo que hay contando lo que ya está en la venta— las aplica la venta al agregar.
 */
export function sePuedeAgregar(repuesto, bloqueado) {
  return !bloqueado && Number(repuesto.stock) > 0 && Number(repuesto.precio) > 0
}
