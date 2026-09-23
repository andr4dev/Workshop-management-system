/**
 * El papel de 80 mm, compartido por los dos documentos que salen por la ticketera: el comprobante de una
 * venta (spec 0003) y el del cierre de caja (spec 0006). Un solo juego de estilos: si el margen que evita
 * que el precio se corte se arregla aquí, se arregla en los dos.
 */

/** Todo texto que viene de datos se escapa: un nombre con "<" no puede romper el documento. */
export const esc = (texto) => String(texto ?? '')
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')

/** Una fila con el texto a la izquierda y la cifra a la derecha. */
export const fila = (izquierda, derecha, clase = '') =>
  `<div class="fila ${clase}"><span class="izq">${esc(izquierda)}</span><span class="der">${esc(derecha)}</span></div>`

const ESTILOS = `
  @page { size: 80mm auto; margin: 0; }
  * { margin: 0; padding: 0; box-sizing: border-box; }
  html { background: #fff; }
  html, body { width: 80mm; }
  body {
    font-family: 'Courier New', Courier, monospace;
    font-size: 12px; line-height: 1.5; color: #000;
    /* El área imprimible de una ticketera de 80 mm es de unos 72 mm: con estos laterales el precio no
       se corta contra el borde. Abajo, papel de sobra para arrancar el ticket. */
    padding: 6mm 5mm 20mm;
  }
  .centro { text-align: center; }
  .tienda { font-size: 16px; font-weight: bold; letter-spacing: .5px; overflow-wrap: anywhere; }
  .dato { font-size: 11px; overflow-wrap: anywhere; }
  .documento { margin-top: 6px; font-size: 11px; font-weight: bold; letter-spacing: 2px; }
  .anulada { margin: 6px 0 2px; padding: 2px 0; border: 2px solid #000; text-align: center;
             font-size: 18px; font-weight: bold; letter-spacing: 4px; }
  hr { margin: 8px 0; border: 0; border-top: 1px dashed #000; }
  .fila { display: flex; justify-content: space-between; gap: 8px; }
  .izq { min-width: 0; overflow-wrap: anywhere; }
  .der { white-space: nowrap; text-align: right; }
  .texto { overflow-wrap: anywhere; }
  .renglon + .renglon { margin-top: 5px; }
  .renglon .texto { font-weight: bold; }
  .detalle { font-size: 11px; }
  .sangria { padding-left: 3mm; font-size: 11px; }
  .total { margin-top: 3px; font-size: 16px; font-weight: bold; }
  .etiqueta { margin-bottom: 2px; font-size: 11px; font-weight: bold; letter-spacing: 1px; }
  .pie { margin-top: 12px; text-align: center; font-size: 10.5px; overflow-wrap: anywhere; }
`

/**
 * El documento completo. Es el mismo HTML el que se imprime y el que se ve en pantalla (en un iframe): así
 * ver el documento muestra exactamente lo que sale en papel.
 */
export function documento80mm(titulo, cuerpo) {
  return `<!doctype html>
<html lang="es"><head><meta charset="utf-8"><title>${esc(titulo)}</title>
<style>${ESTILOS}</style></head>
<body>
${cuerpo}
</body></html>`
}
