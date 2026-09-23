import { cloneElement, forwardRef, isValidElement, useId } from 'react'
import estilos from './Campo.module.css'

/**
 * Campo de formulario con su etiqueta y su ayuda.
 *
 * <p>OJO con la estructura: la etiqueta apunta al control con `htmlFor`, NO lo envuelve.
 * Envolver un `<select>` en un `<label>` hace que el navegador reenvíe el clic al control —
 * el desplegable se abre por el clic nativo y se cierra por el reenviado, así que parece que
 * "a veces no abre". Costó un bug reportado.
 *
 * `forwardRef` no es decorativo: el POS se opera con teclado y las pantallas necesitan poder
 * devolver el foco a un campo concreto tras agregar un renglón.
 */
const Campo = forwardRef(function Campo(
  { etiqueta, ayuda, error, requerido, className = '', children, id, ...props },
  ref,
) {
  const idAuto = useId()
  const idControl = id ?? idAuto

  return (
    <div className={`${estilos.campo} ${className}`}>
      {etiqueta && (
        <label className={estilos.etiqueta} htmlFor={idControl}>
          {etiqueta}
          {requerido && <span className={estilos.requerido} aria-hidden>*</span>}
        </label>
      )}

      {/* Si llega un control propio (select, textarea), se le inyecta el id para que la
          etiqueta siga apuntándole. Con cloneElement: los elementos de React son inmutables. */}
      {children
        ? (isValidElement(children) ? cloneElement(children, { id: idControl }) : children)
        : (
          <input
            ref={ref}
            id={idControl}
            className={`${estilos.entrada} ${error ? estilos.conError : ''}`}
            aria-invalid={error ? 'true' : undefined}
            {...props}
          />
        )}

      {/* El error no comunica solo con color: lleva su icono. Quien no distingue
          rojo de gris tiene que poder ver igual que algo está mal. */}
      {error && (
        <span className={estilos.error} role="alert">
          <span aria-hidden>⚠</span> {error}
        </span>
      )}
      {ayuda && !error && <span className={estilos.ayuda}>{ayuda}</span>}
    </div>
  )
})

export default Campo
