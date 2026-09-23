import Boton from '../Boton'
import estilos from './Caja.module.css'

/**
 * La confirmación de la decisión 3 del spec 0006: el monto es más de lo que debería haber en el cajón. Casi
 * siempre es un cero de más; pero si hubo un sobrante real la plata sí está, así que no se bloquea.
 */
export default function AvisoConfirmarMonto({ enviando, onRevisar, onConfirmar }) {
  return (
    <div className={estilos.confirmar} role="alert">
      <span>
        <strong><span aria-hidden>⚠</span> Es más de lo que debería haber en el cajón. ¿Seguro?</strong>
        {' '}Revisa que no tenga un cero de más.
      </span>
      <div className={estilos.acciones}>
        <Boton variante="secundario" onClick={onRevisar} disabled={enviando}>Revisar el monto</Boton>
        <Boton variante="primario" onClick={onConfirmar} disabled={enviando}>
          {enviando ? 'Registrando…' : 'Sí, registrar'}
        </Boton>
      </div>
    </div>
  )
}
