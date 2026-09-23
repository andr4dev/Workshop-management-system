import Ayuda from '../Ayuda'
import { AYUDA } from '../../utils/ayudaReportes'

/** La ayuda (?) de una cifra de los reportes: qué es, cómo se calcula y qué no incluye (RF-016). */
export default function AyudaDe({ clave }) {
  const ayuda = AYUDA[clave]
  return (
    <Ayuda sobre={ayuda.titulo.toLowerCase()} titulo={ayuda.titulo}>
      <span>{ayuda.que}</span>
      <span><strong>Cómo se calcula:</strong> {ayuda.formula}</span>
      {ayuda.noIncluye && <span><strong>No incluye:</strong> {ayuda.noIncluye}</span>}
    </Ayuda>
  )
}
