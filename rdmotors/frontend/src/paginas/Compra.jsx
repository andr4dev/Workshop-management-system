import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import Boton from '../componentes/Boton'
import Campo from '../componentes/Campo'
import ModalMotivo from '../componentes/ModalMotivo'
import Renglon from '../componentes/compra/Renglon'
import ModalRepuestoNuevo from '../componentes/compra/ModalRepuestoNuevo'
import ModalProveedorNuevo from '../componentes/compra/ModalProveedorNuevo'
import ModalCuentaNueva from '../componentes/compra/ModalCuentaNueva'
import ModalCuentas from '../componentes/compra/ModalCuentas'
import ModalRepuestoRepetido from '../componentes/compra/ModalRepuestoRepetido'
import PestanasCompras from '../componentes/compra/PestanasCompras'
import { comprasApi, cuentasApi, proveedoresApi, repuestosApi, turnosApi } from '../api/cliente'
import { llaveNueva } from '../utils/venta'
import { desgloseDeCambios } from '../utils/auditoria'
import {
  codigoDelRenglon, estaVacio, hayCostosEscritos, opcionDeCajon, precioParaEnviar, problemaDelRenglon,
  problemasDelPago, renglonesRepetidos, sumarIva, textoDelPago, ultimaCategoriaElegida, ultimaCompraDe,
} from '../utils/compra'
import {
  fotografiaDelDetalle, fotografiaDelFormulario, lineaParaCorregir, mismaEntrada, renglonesDesdeDetalle,
} from '../utils/correccion'
import { formatoCOP, formatoCosto, GUION, idLocal, margen, soloDigitos } from '../utils/formato'
import estilos from './Compra.module.css'

const hoy = () => new Date().toLocaleDateString('en-CA')   // YYYY-MM-DD en hora local

const renglonVacio = () => ({
  id: idLocal(),
  codigo: '',
  repuesto: null,
  repuestoNuevo: null,
  cantidad: '',
  modo: 'TOTAL',
  costoTotal: '',
  costoUnitario: '',
  precioVenta: '',
  buscando: false,
  // Último código que ya se consultó. Sin esto, al cerrar el modal el foco vuelve al
  // campo, su onBlur vuelve a buscar, y el modal se reabre solo: no había forma de salir.
  codigoBuscado: null,
  // Lo que se pagó la vez anterior, para mostrarlo. Y si el costo del renglón viene de ahí y
  // nadie lo ha tocado todavía.
  ultimaCompra: null,
  costoSugerido: false,
  error: null,
  // Un fallo de red NO es culpa del renglón: se trata distinto y se puede reintentar.
  sinConexion: false,
})

/**
 * Registrar una compra, o corregir una ya registrada (spec 0002, RF-014).
 *
 * <p>Corregir usa esta misma pantalla, cargada con la compra: el administrador corrige donde la
 * capturó, con las mismas ayudas. Lo que cambia es el final: en vez de registrar, muestra qué va a
 * cambiar y pide el motivo.
 *
 * @param correccion el detalle de la compra a corregir; sin él, se registra una nueva
 */
export default function Compra({ correccion = null }) {
  const navigate = useNavigate()
  const [proveedores, setProveedores] = useState([])
  const [proveedorId, setProveedorId] = useState(correccion?.proveedorId ?? '')
  const [fechaDocumento, setFechaDocumento] = useState(correccion?.fechaDocumento ?? hoy())
  const [numeroFactura, setNumeroFactura] = useState(correccion?.numeroFactura ?? '')
  // Sin forma de pago por defecto: se elige en cada factura (ver problemasDelPago).
  const [formaPago, setFormaPago] = useState(correccion?.formaPago ?? '')
  const [cuentaId, setCuentaId] = useState(correccion?.cuentaId ?? '')
  // Se pagó con billetes del cajón (spec 0006): resta del arqueo del turno abierto.
  const [pagadaDeCaja, setPagadaDeCaja] = useState(Boolean(correccion?.pagadaDeCaja))
  const [turnoAbierto, setTurnoAbierto] = useState(null)
  const [cuentas, setCuentas] = useState([])
  const [modalCuenta, setModalCuenta] = useState(false)
  const [administrandoCuentas, setAdministrandoCuentas] = useState(false)
  const [renglones, setRenglones] = useState(() =>
    correccion ? renglonesDesdeDetalle(correccion, renglonVacio) : [renglonVacio()])
  // Corregir: lo que va a cambiar, mientras se pide el motivo.
  const [confirmacion, setConfirmacion] = useState(null)
  const [errorCorreccion, setErrorCorreccion] = useState(null)
  const [modal, setModal] = useState({
    abierto: false, codigo: '', renglonId: null, datos: null, existente: null,
  })
  const [modalProveedor, setModalProveedor] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [errorGeneral, setErrorGeneral] = useState(null)
  const [exito, setExito] = useState(null)
  // Código repetido tecleado: { renglonId, originalId, codigo }. Abre el aviso en el centro.
  const [repetido, setRepetido] = useState(null)
  // Los avisos de "falta la cantidad", etc. solo se muestran después de intentar registrar.
  const [intentoRegistrar, setIntentoRegistrar] = useState(false)
  /**
   * La llave contra el doble registro (spec 0009, RF-009). Vive mientras se captura esta factura: un doble clic o
   * un reintento tras un corte mandan la misma y el servidor devuelve la compra que ya existe. Se renueva solo
   * cuando la compra quedó registrada.
   */
  const [llave, setLlave] = useState(llaveNueva)

  useEffect(() => {
    proveedoresApi.activos().then(setProveedores).catch(() => setProveedores([]))
    // Sin respuesta se trata como sin turno: la casilla queda bloqueada y el backend lo exige igual.
    turnosApi.abierto().then(setTurnoAbierto).catch(() => setTurnoAbierto(null))
    cuentasApi.activas()
      .then((activas) => {
        // Al corregir, la cuenta de la compra tiene que estar en la lista aunque ya no esté activa:
        // si no, el select quedaría en blanco y parecería que la compra no tenía cuenta.
        const falta = correccion?.cuentaId && !activas.some((c) => c.id === correccion.cuentaId)
        setCuentas(falta ? [...activas, { id: correccion.cuentaId, nombre: correccion.cuenta }] : activas)
      })
      .catch(() => setCuentas([]))
  }, [correccion])

  const cambiarRenglon = (actualizado) =>
    setRenglones((rs) => rs.map((r) => (r.id === actualizado.id ? actualizado : r)))

  const parchar = (id, cambios) =>
    setRenglones((rs) => rs.map((r) => (r.id === id ? { ...r, ...cambios } : r)))

  /**
   * El campo de código haciendo de buscador. Encuentra → engancha; no encuentra → abre la
   * creación con el código ya puesto. Nunca se teclea dos veces.
   */
  async function buscarCodigo(renglon, alEncontrar, forzar = false) {
    const codigo = renglon.codigo.trim()
    if (!codigo || renglon.repuesto?.codigo === codigo || renglon.repuestoNuevo) return
    // Salir del campo (blur) es pasivo: no repite una búsqueda que ya se hizo. Enter sí la
    // fuerza, porque ahí el usuario está pidiéndola explícitamente.
    if (!forzar && renglon.codigoBuscado === codigo) return
    if (modal.abierto) return

    if (repetido) return

    // El mismo repuesto en dos renglones: no se engancha y se abre el aviso.
    const yaEsta = renglones.find((r) => r.id !== renglon.id && codigoDelRenglon(r) === codigo)
    if (yaEsta) {
      parchar(renglon.id, { codigoBuscado: codigo, error: null })
      setRepetido({ renglonId: renglon.id, originalId: yaEsta.id, codigo })
      return
    }

    parchar(renglon.id, { buscando: true, error: null, sinConexion: false })
    try {
      const [encontrado] = await repuestosApi.porCodigo(codigo)
      if (encontrado) {
        setRenglones((rs) => rs.map((r) => (r.id !== renglon.id ? r : {
          ...r, repuesto: encontrado, repuestoNuevo: null, buscando: false, codigoBuscado: codigo,
          // Se rellena con el precio actual. Si nadie lo cambia, al registrar se manda como
          // "no tocar" (ver precioParaEnviar).
          precioVenta: r.precioVenta || String(encontrado.precio),
        })))
        // Después de pintar: la cantidad está deshabilitada hasta que el renglón tiene repuesto.
        setTimeout(() => alEncontrar?.(), 0)
        completarConUltimaCompra(renglon.id, encontrado)
      } else {
        parchar(renglon.id, { buscando: false, codigoBuscado: codigo })
        setModal({ abierto: true, codigo, renglonId: renglon.id, datos: null })
      }
    } catch (e) {
      // Sin conexión no se marca el código como consultado: cuando vuelva el servidor
      // hay que poder reintentar el mismo código sin tener que reteclearlo.
      parchar(renglon.id, {
        buscando: false, error: e.message, sinConexion: e.estado === 0, codigoBuscado: null,
      })
    }
  }

  function crearRepuestoEnRenglon(datos) {
    // En el formulario se puede cambiar el código; si coincide con otro renglón, mismo aviso.
    const yaEsta = renglones.find(
      (r) => r.id !== modal.renglonId && codigoDelRenglon(r) === datos.codigo)
    if (yaEsta) {
      setRepetido({ renglonId: modal.renglonId, originalId: yaEsta.id, codigo: datos.codigo })
      cerrarModal()
      return
    }
    parchar(modal.renglonId, { repuestoNuevo: datos, repuesto: null, error: null })
    cerrarModal()
  }

  /**
   * Al cerrar el aviso de repetido: el renglón repetido se quita si estaba vacío, o se le borra el
   * código si ya tenía algo escrito. Nunca queda un código repetido en pantalla.
   */
  function cerrarRepetido() {
    const { renglonId, originalId } = repetido
    setRepetido(null)
    setRenglones((rs) => {
      const r = rs.find((x) => x.id === renglonId)
      const sinDatos = r && !r.cantidad && !r.costoTotal && !r.costoUnitario
      if (sinDatos && rs.length > 1) return rs.filter((x) => x.id !== renglonId)
      return rs.map((x) => (x.id !== renglonId ? x : { ...x, codigo: '', codigoBuscado: null }))
    })
    irARenglon(originalId)
  }

  /**
   * Rellena el costo con el de la última compra de ese repuesto.
   *
   * Solo si el renglón no tiene costo todavía: nunca pisa algo que el usuario escribió. Y si al
   * volver la respuesta el renglón ya apunta a otro repuesto, no hace nada.
   */
  async function completarConUltimaCompra(id, repuesto) {
    let ultima
    try {
      ultima = ultimaCompraDe(await repuestosApi.kardex(repuesto.id))
    } catch {
      return   // es solo una ayuda: si falla, el renglón queda vacío como antes
    }
    if (!ultima) return

    setRenglones((rs) => rs.map((r) => {
      if (r.id !== id || r.repuesto?.id !== repuesto.id) return r
      const sinCosto = !r.costoTotal && !r.costoUnitario
      return {
        ...r,
        ultimaCompra: ultima,
        ...(sinCosto
          ? { modo: 'UNITARIO', costoUnitario: String(ultima.costoUnitario), costoSugerido: true }
          : {}),
      }
    }))
  }

  /**
   * "Cambiar repuesto": el renglón queda en blanco. La cantidad, el costo y el precio eran de ese
   * repuesto; al teclear el nuevo código se rellenan otra vez desde la última compra del nuevo.
   * (Si se conservaran, el relleno no ocurriría: solo rellena renglones sin costo.)
   */
  function soltarRepuesto(id) {
    // Al corregir, el renglón sigue siendo el mismo de la factura: conserva cuál era y cómo estaba.
    setRenglones((rs) => rs.map((r) => (r.id !== id ? r
      : { ...renglonVacio(), id, lineaId: r.lineaId, original: r.original })))
  }

  /** Reabre la creación con lo que ya se había escrito, para corregirlo. */
  function editarNuevo(renglon) {
    setModal({
      abierto: true,
      codigo: renglon.repuestoNuevo.codigo,
      renglonId: renglon.id,
      datos: renglon.repuestoNuevo,
    })
  }

  /**
   * Corregir un repuesto que YA existe en la base (RF-009).
   *
   * Un renglón cargado desde una compra guardada no trae la ficha completa (categoría, stock
   * mínimo). Se pide antes de abrir: con la ficha incompleta, guardar borraría la categoría.
   */
  async function editarExistente(renglon) {
    let existente = renglon.repuesto
    if (existente.stockMinimo === undefined) {
      try {
        existente = await repuestosApi.ficha(existente.id)
      } catch (e) {
        parchar(renglon.id, { error: e.message, sinConexion: e.estado === 0 })
        return
      }
    }
    setModal({
      abierto: true, codigo: existente.codigo, renglonId: renglon.id,
      datos: null, existente,
    })
  }

  /** Vuelve del PUT: el renglón se refresca con la ficha corregida. */
  function repuestoActualizado(actualizado) {
    parchar(modal.renglonId, { repuesto: actualizado, codigo: actualizado.codigo, error: null })
    cerrarModal()
  }

  const cerrarModal = () =>
    setModal({ abierto: false, codigo: '', renglonId: null, datos: null, existente: null })

  function proveedorCreado(creado) {
    setProveedores((ps) => [...ps, creado])
    setProveedorId(creado.id)
    setModalProveedor(false)
  }

  function elegirFormaPago(forma) {
    setFormaPago(forma)
    // El efectivo no sale de ninguna cuenta: si quedara una elegida, el backend la rechazaría.
    if (forma === 'EFECTIVO') setCuentaId('')
    // Una transferencia nunca sale del cajón (spec 0006, RF-009).
    if (forma === 'TRANSFERENCIA') setPagadaDeCaja(false)
  }

  const cajon = opcionDeCajon({ formaPago, correccion, turnoAbierto })

  function cuentaCreada(creada) {
    setCuentas((cs) => [...cs, creada].sort((a, b) => a.nombre.localeCompare(b.nombre, 'es')))
    setCuentaId(creada.id)
    setModalCuenta(false)
  }

  /**
   * Una cuenta desactivada sale de la lista. Si era la elegida, se suelta: el backend rechazaría una
   * compra nueva desde ella.
   *
   * La excepción es corregir: la cuenta que YA tenía la compra se sigue aceptando aunque esté
   * inactiva, así que se queda en la lista y elegida. Quitarla obligaría a cambiar la forma de pago
   * de una compra que se pagó así.
   */
  function cuentaDesactivada(cuenta) {
    if (correccion?.cuentaId === cuenta.id) return
    setCuentas((cs) => cs.filter((c) => c.id !== cuenta.id))
    setCuentaId((actual) => (actual === cuenta.id ? '' : actual))
  }

  // Los costos antes de sumarles el IVA: para deshacerlo si fue un error (RF-020).
  const [antesDelIva, setAntesDelIva] = useState(null)

  function ponerIva() {
    setAntesDelIva(renglones)
    setRenglones((rs) => sumarIva(rs, 19))
  }

  function deshacerIva() {
    if (antesDelIva) setRenglones(antesDelIva)
    setAntesDelIva(null)
  }

  const agregar = () => setRenglones((rs) => [...rs, renglonVacio()])
  const quitar = (id) => setRenglones((rs) => rs.filter((r) => r.id !== id))

  // El total se suma desde los renglones, nunca se calcula restando ni se recibe hecho.
  const total = renglones.reduce((suma, r) => {
    // Un renglón de la compra que nadie tocó suma lo que se pagó de verdad, no el unitario redondeado.
    if (r.original && mismaEntrada(r)) return suma + Number(r.original.costoTotal)
    const cantidad = Number(r.cantidad) || 0
    return suma + (r.modo === 'TOTAL'
      ? Number(soloDigitos(r.costoTotal)) || 0
      : (Number(soloDigitos(r.costoUnitario)) || 0) * cantidad)
  }, 0)

  // Renglones ya enganchados que repiten repuesto (por ejemplo, armados antes de este aviso).
  const repetidos = renglonesRepetidos(renglones)

  /** Qué le falta a un renglón. Se calcula al pintar: al completar el dato, el aviso se va solo. */
  function problemaDe(r) {
    const original = repetidos.get(r.id)
    if (original) {
      return `Repuesto repetido: ya está en el renglón ${renglones.findIndex((x) => x.id === original) + 1}`
    }
    return problemaDelRenglon(r)
  }

  const problemasPago = problemasDelPago({ formaPago, cuentaId })
  const pagoCompleto = !problemasPago.formaPago && !problemasPago.cuenta

  const conProblema = renglones.filter((r) => problemaDe(r))
  const renglonesListos = renglones.filter((r) => !estaVacio(r) && !problemaDe(r))
  const puedeRegistrar = proveedorId && fechaDocumento && !enviando
    && renglones.some((r) => !estaVacio(r))

  function irARenglon(id) {
    // Con espera: si viene de cerrar un aviso, el modal devuelve el foco al cerrarse y hay que
    // llegar después.
    setTimeout(() => {
      const fila = id && document.getElementById(`renglon-${id}`)
      if (!fila) return
      fila.scrollIntoView({ behavior: 'smooth', block: 'center' })
      fila.querySelector('input[aria-label="Cantidad"]')?.focus({ preventScroll: true })
    }, 0)
  }

  async function registrar() {
    // Nada se descarta en silencio: si un renglón tiene datos pero está incompleto, no se
    // registra la compra y se marca qué falta.
    setIntentoRegistrar(true)
    // La forma de pago está arriba, así que se revisa primero: se lleva la vista al primer aviso.
    if (!pagoCompleto) {
      document.getElementById('forma-de-pago')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      return
    }
    if (conProblema.length > 0) {
      irARenglon(conProblema[0].id)
      return
    }
    if (correccion) {
      revisarCorreccion()
      return
    }
    setEnviando(true)
    setErrorGeneral(null)
    try {
      const respuesta = await comprasApi.registrar({
        llave,
        proveedorId,
        fechaDocumento,
        numeroFactura: numeroFactura.trim() || null,
        formaPago,
        cuentaId: formaPago === 'TRANSFERENCIA' ? cuentaId : null,
        pagadaDeCaja: formaPago === 'EFECTIVO' && pagadaDeCaja,
        lineas: renglonesListos.map((r) => {
          const cantidad = Number(r.cantidad)
          const esNuevo = r.repuestoNuevo != null
          // `nombreConcepto` es solo para pintar el renglón; el backend no lo espera.
          const { nombreConcepto, ...nuevo } = r.repuestoNuevo ?? {}
          return {
            varianteId: esNuevo ? null : r.repuesto.id,
            // El precio SIEMPRE sale del renglón. En un repuesto nuevo viaja dentro de sus
            // datos de creación porque el backend rechaza que venga por los dos caminos.
            repuestoNuevo: esNuevo
              ? { ...nuevo, precio: Number(soloDigitos(r.precioVenta)) }
              : null,
            cantidad,
            modo: r.modo,
            costoTotal: r.modo === 'TOTAL' ? Number(soloDigitos(r.costoTotal)) : null,
            costoUnitario: r.modo === 'UNITARIO' ? Number(soloDigitos(r.costoUnitario)) : null,
            // El precio de un repuesto nuevo va en sus datos de creación, no aquí.
            precioVenta: esNuevo ? null : precioParaEnviar(r.precioVenta, r.repuesto.precio),
          }
        }),
      })
      setExito(respuesta)
      // La factura siguiente es otra compra: otra llave.
      setLlave(llaveNueva())
      setIntentoRegistrar(false)
      setRenglones([renglonVacio()])
      setNumeroFactura('')
      // La forma de pago es de cada factura, no del proveedor: la siguiente se vuelve a elegir.
      setFormaPago('')
      setCuentaId('')
      setPagadaDeCaja(false)
    } catch (e) {
      setErrorGeneral(e.message)
    } finally {
      setEnviando(false)
    }
  }

  /** Antes de guardar una corrección: qué va a cambiar, dicho en palabras, y el motivo. */
  function revisarCorreccion() {
    const despues = fotografiaDelFormulario({
      estado: correccion.estado,
      proveedor: proveedores.find((p) => p.id === proveedorId)?.nombre ?? correccion.proveedor,
      fechaDocumento,
      numeroFactura,
      formaPago,
      cuenta: cuentas.find((c) => c.id === cuentaId)?.nombre ?? null,
      pagadaDeCaja,
      renglones: renglonesListos,
    })
    const desglose = desgloseDeCambios(fotografiaDelDetalle(correccion), despues)
    if (desglose.vacio) {
      setErrorGeneral('No hay cambios que guardar')
      return
    }
    setErrorGeneral(null)
    setErrorCorreccion(null)
    setConfirmacion({ desglose })
  }

  async function guardarCorreccion(motivo) {
    setEnviando(true)
    setErrorCorreccion(null)
    try {
      const { avisos } = await comprasApi.corregir(correccion.id, {
        version: correccion.version,
        motivo,
        proveedorId,
        fechaDocumento,
        numeroFactura: numeroFactura.trim() || null,
        formaPago,
        cuentaId: formaPago === 'TRANSFERENCIA' ? cuentaId : null,
        pagadaDeCaja: formaPago === 'EFECTIVO' && pagadaDeCaja,
        lineas: renglonesListos.map(lineaParaCorregir),
      })
      navigate(`/compras/historial/${correccion.id}`, {
        state: { mensaje: 'Corrección guardada', avisos },
      })
    } catch (e) {
      // El modal queda abierto con el motivo escrito. Si fue un conflicto de versión, el mensaje
      // ya dice que hay que volver a abrir la compra.
      setErrorCorreccion(e.message)
      setEnviando(false)
    }
  }

  if (exito) {
    return (
      <div className={estilos.exito}>
        <h2 className={estilos.exitoTitulo}>Compra registrada</h2>
        <p className={estilos.exitoProveedor}>
          {exito.proveedor} · factura del {exito.fechaDocumento}
          {' · '}<strong>{textoDelPago(exito.formaPago, exito.cuenta, exito.pagadaDeCaja)}</strong>
        </p>

        <div className="scroll-x">
          <table className={estilos.tablaExito}>
            <thead>
              <tr>
                <th>Repuesto</th>
                <th className="cifra">Cant.</th>
                <th className="cifra">Costo c/u</th>
                <th className="cifra">Total pagado</th>
                <th className="cifra">Precio venta</th>
                <th className="cifra">Utilidad c/u</th>
                <th className="cifra">Margen</th>
                <th className="cifra">Stock</th>
              </tr>
            </thead>
            <tbody>
              {exito.lineas.map((l) => {
                const m = margen(l.costoUnitario, l.precioVenta)
                return (
                  <tr key={l.codigo}>
                    <td>
                      <span className={estilos.mono}>{l.codigo}</span>
                      <span className={estilos.nombreLinea}>{l.nombre}</span>
                      <span className={estilos.marcaLinea}>{l.marca}</span>
                    </td>
                    <td className="cifra">{l.cantidad}</td>
                    <td className="cifra">{formatoCosto(l.costoUnitario)}</td>
                    <td className="cifra">{formatoCOP(l.costoTotal)}</td>
                    <td className="cifra">{formatoCOP(l.precioVenta)}</td>
                    <td className={`cifra ${m && m.utilidad < 0 ? estilos.enPerdida : ''}`}>
                      {m ? formatoCOP(m.utilidad) : GUION}
                    </td>
                    <td className={`cifra ${m && m.utilidad < 0 ? estilos.enPerdida : estilos.enGanancia}`}>
                      {m ? `${m.porcentaje.toFixed(0)}%` : GUION}
                    </td>
                    <td className="cifra"><strong>{l.stockResultante}</strong></td>
                  </tr>
                )
              })}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={3}>Total de la compra</td>
                <td className="cifra"><strong>{formatoCOP(exito.total)}</strong></td>
                <td colSpan={4} />
              </tr>
            </tfoot>
          </table>
        </div>

        <p className={estilos.notaExito}>
          El costo promedio de cada repuesto ya quedó recalculado. Si alguno tenía existencias
          a otro precio, su promedio está entre los dos.
        </p>

        <div className={estilos.accionesExito}>
          <Boton variante="primario" onClick={() => setExito(null)}>Registrar otra compra</Boton>
          <Link to={`/compras/historial/${exito.id}`} className={estilos.enlaceExito}>
            Ver en el historial
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className={estilos.pagina}>
      {correccion ? (
        <>
          <Link to={`/compras/historial/${correccion.id}`} className={estilos.volver}>
            ← Volver a la compra
          </Link>
          <h1 className={estilos.titulo}>Corregir compra</h1>
          <p className={estilos.avisoCorreccion}>
            Corrige lo que quedó mal y guarda: se te va a mostrar qué cambia y se pide el motivo.
            Solo los renglones que cambien mueven el inventario.
          </p>
        </>
      ) : (
        <>
          <PestanasCompras />
          <h1 className={estilos.titulo}>Registrar compra</h1>
        </>
      )}

      {/* ── Cabecera de la factura ────────────────────────────────────────── */}
      <section className={estilos.cabecera}>
        <Campo etiqueta="Proveedor" requerido>
          <div className={estilos.proveedorFila}>
            <select
              className={estilos.select}
              value={proveedorId}
              onChange={(e) => setProveedorId(e.target.value)}
            >
              <option value="">Elige un proveedor…</option>
              {proveedores.map((p) => (
                <option key={p.id} value={p.id}>{p.nombre}</option>
              ))}
            </select>
            {/* Llega la factura de un importador nuevo: darlo de alta no debería obligar
                a abandonar lo que ya se capturó. */}
            <Boton variante="secundario" onClick={() => setModalProveedor(true)}>
              + Nuevo
            </Boton>
          </div>
        </Campo>

        <Campo
          etiqueta="Fecha de la factura"
          requerido
          ayuda="La del documento, no la de hoy"
          type="date"
          value={fechaDocumento}
          onChange={(e) => setFechaDocumento(e.target.value)}
        />

        <Campo
          etiqueta="N.º de factura"
          value={numeroFactura}
          onChange={(e) => setNumeroFactura(e.target.value)}
          placeholder="FV-9912"
          autoComplete="off"
        />

        {/* ── Con qué se pagó ───────────────────────────────────────────────
            Ninguna opción viene marcada: un "efectivo" por omisión se registraría sin que
            nadie lo decidiera, y el reporte lo daría por cierto. */}
        <div className={estilos.pago} id="forma-de-pago">
          <Campo
            etiqueta="Forma de pago"
            requerido
            error={intentoRegistrar ? problemasPago.formaPago : null}
          >
            <div className={estilos.segmento} role="group" aria-label="Forma de pago">
              {[['EFECTIVO', 'Efectivo'], ['TRANSFERENCIA', 'Transferencia']].map(([valor, texto]) => (
                <button
                  key={valor}
                  type="button"
                  disabled={cajon.bloqueaFormaDePago}
                  aria-pressed={formaPago === valor}
                  className={formaPago === valor ? estilos.segmentoActivo : estilos.segmentoOpcion}
                  onClick={() => elegirFormaPago(valor)}
                >
                  {texto}
                </button>
              ))}
            </div>
          </Campo>

          {cajon.visible && (
            <div className={estilos.cajon}>
              <label className={cajon.bloqueada ? estilos.cajonBloqueado : estilos.cajonOpcion}>
                <input
                  type="checkbox"
                  checked={pagadaDeCaja}
                  disabled={cajon.bloqueada}
                  onChange={(e) => setPagadaDeCaja(e.target.checked)}
                />
                Se pagó con plata del cajón
              </label>
              <span className={estilos.cajonAyuda}>
                {cajon.porQue ?? 'Márcala si salió de los billetes del turno: resta de lo que debe haber al cerrar.'}
              </span>
            </div>
          )}

          {formaPago === 'TRANSFERENCIA' && (
            <Campo
              etiqueta="Desde la cuenta"
              requerido
              error={intentoRegistrar ? problemasPago.cuenta : null}
            >
              <div className={estilos.proveedorFila}>
                <select
                  className={estilos.select}
                  value={cuentaId}
                  onChange={(e) => setCuentaId(e.target.value)}
                  aria-label="Cuenta desde la que se transfirió"
                >
                  <option value="">Elige la cuenta…</option>
                  {cuentas.map((c) => (
                    <option key={c.id} value={c.id}>{c.nombre}</option>
                  ))}
                </select>
                <Boton variante="secundario" onClick={() => setModalCuenta(true)}>
                  + Nueva
                </Boton>
                <Boton variante="fantasma" onClick={() => setAdministrandoCuentas(true)}
                  title="Desactivar cuentas que ya no se usan">
                  Administrar
                </Boton>
              </div>
            </Campo>
          )}
        </div>
      </section>

      {/* ── Renglones ─────────────────────────────────────────────────────── */}
      {/* El costo va con el IVA incluido (spec 0012, RF-020): la misma regla de la carga desde la factura. */}
      <div className={estilos.iva}>
        <span>El costo se escribe <strong>con el IVA incluido</strong>: es lo que de verdad se pagó.</span>
        {!correccion && (antesDelIva
          ? (
            <button type="button" className={estilos.ivaBoton} onClick={deshacerIva}>
              Se sumó el 19% · Deshacer
            </button>
          ) : (
            <button type="button" className={estilos.ivaBoton} onClick={ponerIva}
              disabled={!hayCostosEscritos(renglones)}>
              La factura trae el IVA aparte: sumar 19%
            </button>
          ))}
      </div>
      <section className={estilos.renglones}>
        <div className={estilos.encabezados}>
          <span>Código</span><span>Repuesto</span><span>Cant.</span>
          <span>Costo viene por</span><span>Costo con IVA</span><span>Precio venta</span>
          <span>Margen</span><span />
        </div>

        {renglones.map((r) => (
          <Renglon
            key={r.id}
            renglon={r}
            onCambiar={cambiarRenglon}
            onQuitar={quitar}
            onBuscarCodigo={buscarCodigo}
            onSoltarRepuesto={soltarRepuesto}
            onReintentar={(r) => buscarCodigo(r, null, true)}
            onEditarNuevo={editarNuevo}
            onEditarExistente={editarExistente}
            puedeQuitar={renglones.length > 1}
            problema={intentoRegistrar ? problemaDe(r) : null}
          />
        ))}

        <Boton variante="secundario" onClick={agregar} className={estilos.agregar}>
          + Agregar renglón
        </Boton>
      </section>

      {/* ── Cierre ────────────────────────────────────────────────────────── */}
      <section className={estilos.pie}>
        <div className={estilos.total}>
          <span className={estilos.totalEtiqueta}>Total de la compra</span>
          <span className={estilos.totalCifra}>{formatoCOP(total)}</span>
        </div>

        {intentoRegistrar && !pagoCompleto && (
          <p className={estilos.errorGeneral} role="alert">
            <span aria-hidden>⚠</span> Falta la forma de pago
          </p>
        )}

        {intentoRegistrar && conProblema.length > 0 && (
          <p className={estilos.errorGeneral} role="alert">
            <span aria-hidden>⚠</span>
            {conProblema.length === 1
              ? ' Hay 1 renglón por completar'
              : ` Hay ${conProblema.length} renglones por completar`}
          </p>
        )}

        {errorGeneral && (
          <p className={estilos.errorGeneral} role="alert">
            <span aria-hidden>⚠</span> {errorGeneral}
          </p>
        )}

        <Boton
          variante="primario"
          tamano="grande"
          onClick={registrar}
          disabled={!puedeRegistrar}
        >
          {correccion
            ? 'Revisar y guardar corrección'
            : enviando ? 'Registrando…' : 'Registrar compra'}
        </Boton>
      </section>

      {repetido && (
        <ModalRepuestoRepetido
          codigo={repetido.codigo}
          numero={renglones.findIndex((r) => r.id === repetido.originalId) + 1}
          original={renglones.find((r) => r.id === repetido.originalId)}
          onEntendido={cerrarRepetido}
        />
      )}

      {/* Se monta solo al abrir: cada apertura es una instancia limpia, sin efectos que
          reseteen el formulario. */}
      {modal.abierto && (
      <ModalRepuestoNuevo
        abierto
        codigo={modal.codigo}
        datosPrevios={modal.datos}
        existente={modal.existente}
        categoriaSugerida={ultimaCategoriaElegida(renglones)}
        onCerrar={cerrarModal}
        onCrear={crearRepuestoEnRenglon}
        onActualizado={repuestoActualizado}
      />
      )}

      {modalProveedor && (
        <ModalProveedorNuevo
          abierto
          onCerrar={() => setModalProveedor(false)}
          onCreado={proveedorCreado}
        />
      )}

      {modalCuenta && (
        <ModalCuentaNueva
          abierto
          onCerrar={() => setModalCuenta(false)}
          onCreada={cuentaCreada}
        />
      )}

      {administrandoCuentas && (
        <ModalCuentas
          abierto
          onCerrar={() => setAdministrandoCuentas(false)}
          onDesactivada={cuentaDesactivada}
        />
      )}

      {confirmacion && (
        <ModalMotivo
          titulo="Guardar corrección"
          descripcion="Revisa lo que cambia. Queda registrado con el motivo, la fecha y quién lo hizo."
          desglose={confirmacion.desglose}
          textoConfirmar="Guardar corrección"
          enviando={enviando}
          error={errorCorreccion}
          onConfirmar={guardarCorreccion}
          onCerrar={() => { if (!enviando) setConfirmacion(null) }}
        />
      )}
    </div>
  )
}
