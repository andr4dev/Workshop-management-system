/**
 * La geometría de la gráfica del día por día (spec 0007, RF-018): dos barras por día o por semana, ventas netas y
 * utilidad operativa. Una utilidad negativa baja del eje.
 *
 * Sin librería: son rectángulos en un SVG. Aquí se calcula dónde va cada uno, sin navegador, para poder probar que
 * una barra negativa queda bajo el cero y que nada sale NaN cuando todo es $0.
 */

/** Un paso "redondo" (1, 2 o 5 por una potencia de 10) para que las líneas del eje caigan en cifras legibles. */
function pasoRedondo(bruto) {
  if (bruto <= 0) return 1
  const potencia = 10 ** Math.floor(Math.log10(bruto))
  const fraccion = bruto / potencia
  const redondo = fraccion <= 1 ? 1 : fraccion <= 2 ? 2 : fraccion <= 5 ? 5 : 10
  return redondo * potencia
}

/**
 * Las marcas del eje: cifras redondas que cubren de `min` a `max` e incluyen el cero.
 *
 * @return { marcas, desde, hasta } — `desde` ≤ min y `hasta` ≥ max, en múltiplos del paso
 */
export function marcasDelEje(min, max, cuantas = 4) {
  const bajo = Math.min(0, min)
  const alto = Math.max(0, max)
  const paso = pasoRedondo((alto - bajo) / cuantas)
  const desde = Math.floor(bajo / paso) * paso
  const hasta = Math.max(Math.ceil(alto / paso) * paso, desde + paso)
  const marcas = []
  for (let v = desde; v <= hasta + paso / 2; v += paso) marcas.push(Math.round(v))
  return { marcas, desde, hasta }
}

/**
 * Dónde va cada barra.
 *
 * @param filas las del servidor: `ventasNetas` y `utilidadOperativa` de cada día o semana
 * @param medidas el ancho y alto del área de las barras, en unidades del SVG
 * @return { y(valor), cero, marcas, barras: [{ fila, x, ancho, ventas: {x, y, alto}, utilidad: {x, y, alto, negativa} }] }
 */
export function geometria(filas, { ancho, alto }) {
  const valores = filas.flatMap((f) => [f.ventasNetas, f.utilidadOperativa])
  const { marcas, desde, hasta } = marcasDelEje(Math.min(0, ...valores), Math.max(0, ...valores))
  const y = (valor) => ((hasta - valor) / (hasta - desde)) * alto
  const cero = y(0)

  const hueco = filas.length ? ancho / filas.length : ancho
  const anchoBarra = Math.max(1, Math.min(hueco * 0.36, 26))
  const separacion = Math.min(hueco * 0.06, 3)

  const barra = (x, valor) => {
    const tope = y(valor)
    return { x, y: Math.min(tope, cero), alto: Math.abs(cero - tope), negativa: valor < 0 }
  }

  const barras = filas.map((fila, i) => {
    const centro = hueco * i + hueco / 2
    return {
      fila,
      x: hueco * i,
      ancho: hueco,
      ventas: barra(centro - separacion / 2 - anchoBarra, fila.ventasNetas),
      utilidad: barra(centro + separacion / 2, fila.utilidadOperativa),
      anchoBarra,
    }
  })
  return { y, cero, marcas, barras }
}

/** Cada cuántas filas va una etiqueta abajo, para que no se monten: unas 12 como mucho. */
export function cadaCuantasEtiquetas(filas, maximo = 12) {
  return Math.max(1, Math.ceil(filas / maximo))
}

/** "$1,2 M", "$850 mil", "$900": lo corto que cabe en el eje. */
export function montoCorto(pesos) {
  const signo = pesos < 0 ? '−' : ''
  const v = Math.abs(pesos)
  const num = (n) => n.toLocaleString('es-CO', { maximumFractionDigits: 1 })
  if (v >= 1_000_000) return `${signo}$${num(v / 1_000_000)} M`
  if (v >= 1_000) return `${signo}$${num(v / 1_000)} mil`
  return `${signo}$${v}`
}
