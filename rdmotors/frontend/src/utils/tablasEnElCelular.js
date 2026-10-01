/**
 * Las tablas en el celular: cada renglón se vuelve una tarjeta (ver `index.css`, "Tablas en el celular"). Para eso cada
 * celda lleva el nombre de su columna en `data-etiqueta`, y la que dice qué es el renglón (el repuesto, el cliente, el
 * proveedor…) lleva `data-principal` y va arriba, como título.
 *
 * No toca cómo funciona nada: solo pone atributos. Se hace aquí, una vez para toda la app, y no tabla por tabla: así una
 * tabla nueva sale bien en el celular sin acordarse de nada. Una celda que ya trae `data-principal` lo conserva.
 */

/** El título de la tarjeta: la primera columna, en este orden, que tenga la tabla. */
const PRINCIPALES = ['Repuesto', 'Repuestos', 'Cliente', 'Proveedor', 'Persona', 'Movimiento', 'Categoría', 'Qué',
  'Día', 'Semana', 'Cerró', 'Cuándo', 'N.º', '#', '']

/** Las tablas de la página, con sus etiquetas puestas. Exportada para probarla. */
export function etiquetar(raiz) {
  for (const tabla of raiz.querySelectorAll('table')) {
    const encabezados = [...tabla.querySelectorAll(':scope > thead > tr:first-child > th')]
    if (encabezados.length === 0) continue
    // Con colSpan, la columna de cada encabezado es la suma de los anteriores.
    const columnas = []
    for (const th of encabezados) {
      for (let i = 0; i < (th.colSpan || 1); i++) columnas.push(th.textContent.replace(/\s+/g, ' ').trim())
    }
    const principal = PRINCIPALES.map((p) => columnas.indexOf(p)).find((i) => i >= 0)
    for (const fila of tabla.querySelectorAll(':scope > tbody > tr, :scope > tfoot > tr')) {
      let columna = 0
      for (const celda of fila.children) {
        // Una celda que abarca varias columnas ("Total de la compra") es su propio rótulo: no lleva etiqueta.
        const etiqueta = (celda.colSpan || 1) > 1 ? '' : columnas[columna] ?? ''
        if (celda.getAttribute('data-etiqueta') !== etiqueta) celda.setAttribute('data-etiqueta', etiqueta)
        if (columna === principal && !celda.hasAttribute('data-principal')) celda.setAttribute('data-principal', '')
        // El número de renglón ("#") en el celular sobra: la tarjeta ya es el renglón.
        if (etiqueta === '#' && columna !== principal) celda.setAttribute('data-solo-escritorio', '')
        columna += celda.colSpan || 1
      }
    }
  }
}

/** Etiqueta lo que ya está y lo que vaya apareciendo. Una vez por cuadro, aunque cambien muchas cosas a la vez. */
export function etiquetarTablasEnElCelular(documento = document) {
  let pendiente = false
  const programar = () => {
    if (pendiente) return
    pendiente = true
    requestAnimationFrame(() => {
      pendiente = false
      etiquetar(documento)
    })
  }
  etiquetar(documento)
  new MutationObserver(programar).observe(documento.body, { childList: true, subtree: true, characterData: true })
}
