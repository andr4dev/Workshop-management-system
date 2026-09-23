import { Fragment } from 'react'
import { partesResaltadas } from '../utils/historial'
import estilos from './Resaltado.module.css'

/**
 * Un texto con lo buscado marcado, como con un resaltador (spec 0002, RF-026).
 *
 * No decide si algo coincide: el padre lo pinta solo en los renglones que el backend marcó.
 */
export default function Resaltado({ texto, busqueda }) {
  return partesResaltadas(texto, busqueda).map((parte, i) => (parte.resaltado
    ? <mark key={i} className={estilos.marca}>{parte.texto}</mark>
    : <Fragment key={i}>{parte.texto}</Fragment>))
}
