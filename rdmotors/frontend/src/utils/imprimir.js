/**
 * Mandar un documento a la impresora desde el navegador (spec 0003, decisión 3), como el car-wash.
 *
 * El HTML va en un iframe oculto y se llama a `print()` sobre él. En el computador del mostrador
 * configurado con la guía (`docs/INSTALAR_TICKETERA.md`: ticketera predeterminada y acceso directo con
 * `--kiosk-printing`) el ticket sale solo. En cualquier otro navegador aparece el diálogo de impresión.
 *
 * NUNCA lanza: la venta ya está cobrada y un problema de impresión no puede deshacerla ni romper la
 * pantalla (RF-021). Devuelve si se pudo mandar. Que la ticketera tenga papel el navegador no lo sabe:
 * para eso está Reimprimir.
 */

/** Por debajo de este ancho es un celular (el mismo corte del resto de pantallas). */
export const ANCHO_CELULAR = 640

/**
 * ¿Este equipo es el del mostrador? Solo ahí se imprime al cobrar: con mouse y pantalla ancha. En un
 * celular o una tablet la venta se cobra igual y el comprobante se reimprime desde el mostrador
 * (spec 0003, RF-022); abrirles el diálogo de impresión en cada venta solo estorbaría.
 */
export function esElMostrador({ punteroGrueso, anchoPantalla }) {
  return !punteroGrueso && anchoPantalla > ANCHO_CELULAR
}

export function esteEquipoEsElMostrador() {
  try {
    return esElMostrador({
      punteroGrueso: window.matchMedia('(pointer: coarse)').matches,
      anchoPantalla: window.innerWidth,
    })
  } catch {
    return false
  }
}

/** Cuánto se espera a que el iframe cargue antes de darlo por fallido. */
const ESPERA_MAXIMA_MS = 5000

export function imprimirHtml(html) {
  return new Promise((resolver) => {
    let marco = null
    let terminado = false
    // Imprimir puede mover el foco al iframe, y al quitarlo el cursor quedaría en la nada: el cajero
    // escribiría el siguiente código sin que entre. Se devuelve a donde estaba.
    const focoPrevio = document.activeElement

    const terminar = (enviado) => {
      if (terminado) return
      terminado = true
      if (focoPrevio && document.activeElement !== focoPrevio) focoPrevio.focus?.()
      // Con margen: quitar el iframe mientras la impresora recibe el trabajo lo cancelaría.
      setTimeout(() => marco?.remove(), 1500)
      resolver(enviado)
    }

    try {
      marco = document.createElement('iframe')
      marco.setAttribute('aria-hidden', 'true')
      marco.tabIndex = -1
      marco.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;visibility:hidden'
      marco.onload = () => setTimeout(() => {
        try {
          marco.contentWindow.print()
          terminar(true)
        } catch {
          terminar(false)
        }
      }, 80)
      setTimeout(() => terminar(false), ESPERA_MAXIMA_MS)
      marco.srcdoc = html
      document.body.appendChild(marco)
    } catch {
      terminar(false)
    }
  })
}
