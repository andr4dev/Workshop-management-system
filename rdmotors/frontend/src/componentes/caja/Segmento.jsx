import estilos from './Caja.module.css'

/**
 * Opciones excluyentes en una barra: "Del cajón / Por fuera", "Efectivo / Transferencia". Ninguna viene
 * marcada si `valor` está vacío: lo que no se eligió no se registra como si se hubiera elegido.
 *
 * @param opciones [valor, texto, deshabilitada?][]
 */
export default function Segmento({ etiqueta, opciones, valor, onCambio }) {
  return (
    <div className={estilos.segmento} role="group" aria-label={etiqueta}>
      {opciones.map(([opcion, texto, deshabilitada = false]) => (
        <button
          key={opcion}
          type="button"
          aria-pressed={valor === opcion}
          disabled={deshabilitada}
          className={valor === opcion ? estilos.opcionActiva : estilos.opcion}
          onClick={() => onCambio(opcion)}
        >
          {texto}
        </button>
      ))}
    </div>
  )
}
