import { useState } from 'react'
import estilos from './Ticket.module.css'

/** El alto del papel, redondeado hacia arriba: un alto con decimales cortado hacia abajo saca barra. */
function altoDelDocumento(marco) {
  const cuerpo = marco.contentDocument?.body
  return cuerpo ? Math.ceil(cuerpo.getBoundingClientRect().height) + 1 : null
}

/**
 * Un documento de 80 mm en pantalla: el comprobante de una venta (spec 0003, RF-022) o el del cierre de caja
 * (spec 0006, RF-019). Es el MISMO documento que va a la impresora, dentro de un iframe. No es un dibujo parecido; es el ticket. Así lo que se ve es lo que sale en papel, y una copia
 * es igual al original.
 *
 * `sandbox` sin scripts: el ticket no tiene ninguno, y un dato con código tampoco podría correr. Deja
 * leer el alto del documento para que el papel se vea entero, sin barra de desplazamiento.
 */
export default function Ticket({ html, titulo }) {
  const [alto, setAlto] = useState(null)

  return (
    <div className={estilos.mesa}>
      <iframe
        title={titulo}
        className={estilos.papel}
        srcDoc={html}
        sandbox="allow-same-origin"
        style={alto ? { height: alto } : undefined}
        onLoad={(e) => setAlto(altoDelDocumento(e.currentTarget))}
      />
    </div>
  )
}
