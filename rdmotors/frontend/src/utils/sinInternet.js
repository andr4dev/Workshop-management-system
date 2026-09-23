/**
 * Que la pantalla no dependa de internet (spec 0009, RF-002).
 *
 * En la tienda el servidor está al lado, pero una sola línea —una fuente de Google, un ícono de un CDN— haría que
 * la pantalla se viera rota justo el día en que se cae el internet. Aquí está lo que busca la prueba que vigila eso.
 *
 * No se busca "cualquier http": un comentario que nombra `http://192.168.1.5:5174` es documentación, no una
 * descarga. Lo que se busca es **lo que el navegador iría a pedir**: una hoja de estilos, un script, una fuente o
 * una imagen traída de afuera.
 */

/** Lo que un navegador descargaría de internet al abrir la página. */
const FORMAS_DE_PEDIR_AFUERA = [
  // En CSS: @import url(https://…) y url(https://…) dentro de font-face o background.
  { que: '@import de afuera', patron: /@import\s+(url\()?["']?https?:\/\//gi },
  { que: 'url() de afuera', patron: /url\(\s*["']?https?:\/\//gi },
  // En HTML: <link href="https://…"> y <script src="https://…">.
  { que: '<link> de afuera', patron: /<link[^>]+href\s*=\s*["']https?:\/\//gi },
  { que: '<script> de afuera', patron: /<script[^>]+src\s*=\s*["']https?:\/\//gi },
  // Los sospechosos de siempre, escritos de cualquier forma.
  { que: 'un CDN', patron: /(fonts\.googleapis\.com|fonts\.gstatic\.com|cdn\.jsdelivr\.net|unpkg\.com|cdnjs\.cloudflare\.com)/gi },
]

/**
 * Qué cosas de ese archivo se traerían de internet.
 *
 * @param texto el contenido del archivo
 * @returns una lista de descripciones; vacía si no pide nada de afuera
 */
export function loQuePideDeInternet(texto) {
  if (!texto) return []
  const encontrado = []
  for (const { que, patron } of FORMAS_DE_PEDIR_AFUERA) {
    const coincidencias = texto.match(patron)
    if (coincidencias) {
      for (const c of coincidencias) encontrado.push(`${que}: ${c.trim()}`)
    }
  }
  return encontrado
}
