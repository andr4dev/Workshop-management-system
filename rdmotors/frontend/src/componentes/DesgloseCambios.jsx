import estilos from './DesgloseCambios.module.css'

const ETIQUETA_TIPO = { cambiado: 'Cambia', agregado: 'Se agrega', quitado: 'Se quita' }

/**
 * Lo que cambia en una compra, acomodado para leerlo de un vistazo: cada dato con su valor de antes
 * y el de después en columnas, cada renglón en su bloque con lo que le pasa al inventario, y el total
 * con la diferencia.
 *
 * Reemplaza la lista de frases ("ABC123: cantidad 12 → 17 · pagado $30.000 → $42.500"), que obligaba
 * a leer la línea entera para saber qué se movió.
 *
 * @param desglose       lo que devuelve `desgloseDeCambios`
 * @param textoDespues   "Queda" al confirmar (todavía no pasó), "Después" en el rastro
 */
export default function DesgloseCambios({ desglose, textoDespues = 'Después', compacto = false }) {
  if (!desglose || desglose.vacio) return null
  const { factura, renglones, total } = desglose

  return (
    <div className={`${estilos.desglose} ${compacto ? estilos.compacto : ''}`}>
      <div className={estilos.columnas} aria-hidden>
        <span />
        <span>Antes</span>
        <span />
        <span>{textoDespues}</span>
      </div>

      {factura.length > 0 && (
        <section className={estilos.bloque} aria-label="Datos de la factura">
          <p className={estilos.bloqueTitulo}>Datos de la factura</p>
          {factura.map((f) => <Fila key={f.etiqueta} fila={f} />)}
        </section>
      )}

      {renglones.map((g) => (
        <section key={`${g.tipo}-${g.codigo}`} className={`${estilos.bloque} ${estilos[g.tipo]}`}
          aria-label={`${ETIQUETA_TIPO[g.tipo]} ${g.codigo}`}>
          <div className={estilos.renglonCabecera}>
            <span className={estilos.tipo}>{ETIQUETA_TIPO[g.tipo]}</span>
            <span className={estilos.codigo}>{g.codigo}</span>
            {g.repuesto && <span className={estilos.repuesto}>{g.repuesto}</span>}
            <span className={estilos.inventario}>
              Inventario <strong>{g.inventario}</strong>
            </span>
          </div>
          {g.filas.map((f) => <Fila key={f.etiqueta} fila={f} />)}
        </section>
      ))}

      {total && (
        <div className={estilos.total}>
          <span className={estilos.etiqueta}>Total de la factura</span>
          <span className={estilos.antes}>{total.antes}</span>
          <span className={estilos.flecha} aria-label="pasa a">→</span>
          <span className={estilos.despues}>
            {total.despues}
            <span className={estilos.diferencia}>{total.diferencia}</span>
          </span>
        </div>
      )}
    </div>
  )
}

/**
 * Una fila con sus dos lados. En un renglón que se agrega no hay "antes", y en uno que se quita no
 * hay "después": ese lado queda con un guion tenue, sin flecha, para que no parezca un cambio a nada.
 */
function Fila({ fila }) {
  const { etiqueta, antes, despues, igual } = fila
  const soloUnLado = antes == null || despues == null
  return (
    <div className={estilos.fila}>
      <span className={estilos.etiqueta}>{etiqueta}</span>
      <span className={`${estilos.antes} ${despues == null ? estilos.tachado : ''}`}>{antes ?? '—'}</span>
      <span className={estilos.flecha} aria-label={soloUnLado ? undefined : 'pasa a'}>
        {soloUnLado ? '' : '→'}
      </span>
      <span className={igual ? estilos.igual : estilos.despues}>
        {despues ?? '—'}
        {igual && <span className={estilos.nota}> · igual</span>}
      </span>
    </div>
  )
}
