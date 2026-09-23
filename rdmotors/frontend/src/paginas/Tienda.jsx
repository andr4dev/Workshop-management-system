import { useEffect, useMemo, useState } from 'react'
import AvisoCarga from '../componentes/AvisoCarga'
import Boton from '../componentes/Boton'
import Campo from '../componentes/Campo'
import PestanasAjustes from '../componentes/PestanasAjustes'
import Ticket from '../componentes/venta/Ticket'
import { tiendaApi } from '../api/cliente'
import { armarTicket, htmlDelTicket, VENTA_DE_EJEMPLO } from '../utils/ticket'
import comun from './Listado.module.css'
import estilos from './Tienda.module.css'

/** Los largos máximos son los de la base (V10); el servidor los vuelve a exigir. */
const CAMPOS = [
  { nombre: 'nombreComercial', etiqueta: 'Nombre comercial', maximo: 80, requerido: true, ejemplo: 'RD MOTORS' },
  { nombre: 'nit', etiqueta: 'NIT', maximo: 30, ejemplo: '900.123.456-7' },
  { nombre: 'direccion', etiqueta: 'Dirección', maximo: 120, ejemplo: 'Calle 10 # 5-20, Sincelejo' },
  { nombre: 'telefono', etiqueta: 'Teléfono', maximo: 40, ejemplo: '300 123 4567' },
  { nombre: 'mensajePie', etiqueta: 'Mensaje al pie', maximo: 160, ejemplo: 'Gracias por su compra' },
]

const VACIOS = Object.fromEntries(CAMPOS.map((c) => [c.nombre, '']))
const aFormulario = (datos) => Object.fromEntries(CAMPOS.map((c) => [c.nombre, datos[c.nombre] ?? '']))

/**
 * Los datos de la tienda que salen en cada comprobante (spec 0003, RF-033). Al lado, un comprobante de
 * muestra que cambia mientras se escribe: se ve cómo queda en el papel antes de guardar.
 */
export default function Tienda() {
  const [intento, setIntento] = useState(0)
  const [carga, setCarga] = useState({ intento: null, error: null })
  const [formulario, setFormulario] = useState(VACIOS)
  const [guardados, setGuardados] = useState(null)
  const [guardando, setGuardando] = useState(false)
  const [resultado, setResultado] = useState(null)

  useEffect(() => {
    let vigente = true
    tiendaApi.obtener()
      .then((datos) => {
        if (!vigente) return
        setFormulario(aFormulario(datos))
        setGuardados(aFormulario(datos))
        setCarga({ intento, error: null })
      })
      .catch((error) => { if (vigente) setCarga({ intento, error }) })
    return () => { vigente = false }
  }, [intento])

  const cargando = carga.intento !== intento
  const sinCambios = guardados != null && CAMPOS.every((c) => formulario[c.nombre] === guardados[c.nombre])
  const sinNombre = formulario.nombreComercial.trim() === ''

  // La muestra usa lo escrito tal cual, con los vacíos como "no dado": igual que lo guardará el servidor.
  const muestra = useMemo(() => armarTicket(VENTA_DE_EJEMPLO, Object.fromEntries(
    CAMPOS.map((c) => [c.nombre, formulario[c.nombre].trim() || null]),
  )), [formulario])

  const cambiar = (nombre) => (e) => {
    setFormulario((f) => ({ ...f, [nombre]: e.target.value }))
    setResultado(null)
  }

  async function guardar(e) {
    e.preventDefault()
    if (sinNombre || guardando) return
    setGuardando(true)
    setResultado(null)
    try {
      const datos = await tiendaApi.actualizar(formulario)
      setFormulario(aFormulario(datos))
      setGuardados(aFormulario(datos))
      setResultado({ ok: true })
    } catch (error) {
      setResultado({ ok: false, mensaje: error.estado === 0 ? 'No hay conexión con el servidor. No se guardó.' : error.message })
    } finally {
      setGuardando(false)
    }
  }

  return (
    <div className={comun.pagina}>
      <PestanasAjustes />
      <header className={comun.encabezado}>
        <div>
          <h1 className={comun.titulo}>Datos de la tienda</h1>
          <p className={comun.subtitulo}>Lo que sale arriba y al pie de cada comprobante. Lo vacío no se imprime.</p>
        </div>
      </header>

      {cargando && !guardados && <p className={comun.vacio}>Cargando datos…</p>}
      {!cargando && carga.error && <AvisoCarga error={carga.error} onReintentar={() => setIntento((n) => n + 1)} />}

      {guardados && (
        <div className={estilos.columnas}>
          <form className={estilos.formulario} onSubmit={guardar} noValidate>
            {CAMPOS.map((c) => (
              <Campo
                key={c.nombre}
                etiqueta={c.etiqueta}
                requerido={c.requerido}
                value={formulario[c.nombre]}
                onChange={cambiar(c.nombre)}
                maxLength={c.maximo}
                placeholder={c.ejemplo}
                autoComplete="off"
                error={c.requerido && sinNombre ? 'Es lo primero que sale en el comprobante' : null}
              />
            ))}

            <div className={estilos.acciones}>
              <Boton type="submit" variante="primario" disabled={sinNombre || sinCambios || guardando}>
                {guardando ? 'Guardando…' : 'Guardar'}
              </Boton>
              {resultado?.ok && <span className={estilos.guardado} role="status">Guardado. Los próximos comprobantes salen así.</span>}
              {resultado && !resultado.ok && (
                <span className={estilos.error} role="alert"><span aria-hidden>⚠</span> {resultado.mensaje}</span>
              )}
            </div>
          </form>

          <section className={estilos.muestra} aria-label="Así sale en el comprobante">
            <h2 className={estilos.muestraTitulo}>Así sale en el comprobante</h2>
            <p className={estilos.muestraNota}>Con una venta de ejemplo: no es una venta real.</p>
            <Ticket html={htmlDelTicket(muestra)} titulo="Comprobante de ejemplo" />
          </section>
        </div>
      )}
    </div>
  )
}
