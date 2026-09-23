import estilos from './AvisoCarga.module.css'

/**
 * El aviso de "no se pudo cargar", con su botón de reintentar.
 *
 * Dos colores a propósito:
 *   - ámbar si no hay conexión (`estado === 0`): no es un error de datos y se arregla reintentando;
 *   - rojo si el servidor respondió con un error: algo está mal y hay que leerlo.
 *
 * @param desactualizado si la pantalla sigue mostrando datos de antes, se dice.
 */
export default function AvisoCarga({ error, onReintentar, desactualizado = false }) {
  if (!error) return null
  const sinRed = error.estado === 0
  return (
    <div className={sinRed ? estilos.sinRed : estilos.error} role="alert">
      <span>
        <span aria-hidden>⚠</span> {sinRed ? 'No hay conexión con el servidor.' : error.message}
        {desactualizado && ' Lo que ves puede estar desactualizado.'}
      </span>
      {onReintentar && (
        <button type="button" className={estilos.reintentar} onClick={onReintentar}>
          Reintentar
        </button>
      )}
    </div>
  )
}
