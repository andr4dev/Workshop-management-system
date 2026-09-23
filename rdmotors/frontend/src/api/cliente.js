/**
 * Cliente HTTP.
 *
 * Una sola puerta hacia el backend. Cuando llegue la rebanada 4 (sin conexión), la cola offline
 * se enchufa AQUÍ y ninguna pantalla se entera — igual que en el car-wash, donde `offlineApiClient`
 * envuelve al cliente normal.
 */

import { EVENTO_SIN_SESION, leerCookie } from '../utils/sesion.js'

/*
 * La sesión (spec 0004) no la maneja este código: el servidor la deja en una cookie que la página no puede leer, y
 * el navegador la manda sola. Quién hace cada cosa lo decide el servidor, no la página.
 *
 * Lo que sí se manda a mano es el token anti-CSRF: el servidor lo deja en la cookie XSRF-TOKEN y lo exige de vuelta
 * en el encabezado X-XSRF-TOKEN de cada escritura. Una página de otro sitio no puede leerlo.
 */
const ENCABEZADO_CSRF = 'X-XSRF-TOKEN'

/** Error con el cuerpo que devolvió el backend, para poder leer sus campos. */
export class ErrorApi extends Error {
  constructor(estado, cuerpo) {
    super(cuerpo?.mensaje ?? `Error ${estado}`)
    this.estado = estado
    this.cuerpo = cuerpo ?? {}
  }

  /** 409: el código ya lo usa otro repuesto. `cuerpo.nombreDelQueLoTiene` dice cuál. */
  get esCodigoDuplicado() {
    return this.estado === 409 && this.cuerpo.nombreDelQueLoTiene != null
  }

  /** 409 sin código: otra corrección se guardó mientras la pantalla tenía la compra abierta. */
  get esConflictoDeVersion() {
    return this.estado === 409 && this.cuerpo.nombreDelQueLoTiene == null
  }

  /**
   * 409: un gasto o retiro es más de lo que debería haber en el cajón (spec 0006, decisión 3). No es un rechazo:
   * se pregunta "¿Seguro?" y se reenvía con `confirmado`. El mensaje no dice la cifra.
   */
  get esConfirmarMonto() {
    return this.estado === 409 && this.cuerpo.codigo === 'CONFIRMAR_MONTO'
  }

  /**
   * 409: esa cédula ya es de otro cliente (spec 0008). `cuerpo.cliente` es ese: la pantalla lo usa en vez de crear
   * otro, así la deuda de una persona no queda partida en dos.
   */
  get esClienteRepetido() {
    return this.estado === 409 && this.cuerpo.codigo === 'CLIENTE_REPETIDO'
  }

  /** 409: el turno ya se había cerrado (doble clic, otra pestaña). La pantalla carga ese cierre. */
  get esTurnoCerrado() {
    return this.estado === 409 && this.cuerpo.codigo === 'TURNO_CERRADO'
  }

  /** 422: regla de negocio. El mensaje está escrito para que lo lea el cajero. */
  get esReglaDeNegocio() {
    return this.estado === 422
  }

  /** El código que manda el servidor (`SIN_SESION`, `NO_PERMITIDO`, `CREDENCIALES`…), o `null`. */
  get codigo() {
    return this.cuerpo.codigo ?? null
  }
}

async function pedir(metodo, ruta, cuerpo, { reintentoCsrf = false } = {}) {
  const escribe = metodo !== 'GET'
  let respuesta
  try {
    respuesta = await fetch(ruta, {
      method: metodo,
      headers: {
        'Content-Type': 'application/json',
        ...(escribe ? { [ENCABEZADO_CSRF]: leerCookie('XSRF-TOKEN', document.cookie) ?? '' } : {}),
      },
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    })
  } catch {
    // fetch solo lanza si no hubo respuesta: red caída o servidor apagado.
    throw new ErrorApi(0, { mensaje: 'No hay conexión con el servidor' })
  }

  const texto = await respuesta.text()

  // No todo lo que llega es JSON: una página de error del proxy, un 502, un timeout de un
  // intermediario. Sin este try, un SyntaxError crudo se escapa del manejo de errores y la
  // pantalla revienta con un mensaje que nadie entiende.
  let datos
  try {
    datos = texto ? JSON.parse(texto) : null
  } catch {
    throw new ErrorApi(respuesta.status || 0, {
      mensaje: respuesta.ok
        ? 'El servidor respondió algo inesperado'
        : `El servidor falló (${respuesta.status})`,
    })
  }

  if (!respuesta.ok) {
    const error = new ErrorApi(respuesta.status, datos)
    // Sin la cookie anti-CSRF todavía (primera escritura de la pestaña): una lectura la trae, y se reintenta una vez.
    if (error.codigo === 'CSRF' && !reintentoCsrf) {
      await fetch('/api/instalacion').catch(() => {})
      return pedir(metodo, ruta, cuerpo, { reintentoCsrf: true })
    }
    // La sesión venció o la sacaron: la app vuelve a *Entrar* sin perder la pantalla en la que estaba.
    if (error.codigo === 'SIN_SESION') window.dispatchEvent(new CustomEvent(EVENTO_SIN_SESION))
    throw error
  }
  return datos
}

export const api = {
  get: (ruta) => pedir('GET', ruta),
  post: (ruta, cuerpo) => pedir('POST', ruta, cuerpo),
  put: (ruta, cuerpo) => pedir('PUT', ruta, cuerpo),
  borrar: (ruta) => pedir('DELETE', ruta),
}

/** Entrar, salir y quién soy (spec 0004). El token viaja en una cookie que esta página no lee. */
export const sesionApi = {
  /** Quién está adentro; 401 `SIN_SESION` si nadie. */
  yo: () => api.get('/api/sesion'),
  entrar: ({ usuario, contrasena }) => api.post('/api/sesion', { usuario, contrasena }),
  salir: () => api.borrar('/api/sesion'),
  /** Devuelve la sesión con la versión nueva: la cookie vieja deja de valer y el servidor pone otra. */
  cambiarContrasena: ({ actual, nueva }) => api.put('/api/sesion/contrasena', { actual, nueva }),
}

/** El primer administrador (spec 0004, RF-018): solo mientras no haya ningún usuario. */
export const instalacionApi = {
  estado: () => api.get('/api/instalacion'),
  crearAdministrador: ({ nombre, usuario, contrasena }) =>
    api.post('/api/instalacion/administrador', { nombre, usuario, contrasena }),
}

/** Escapa el texto antes de meterlo en la URL. Un espacio sin codificar rompe la petición. */
const q = (texto) => encodeURIComponent(texto)

export const catalogoApi = {
  categorias: () => api.get('/api/categorias'),
  buscarConceptos: (texto) => api.get(`/api/productos?q=${q(texto)}`),
}

export const repuestosApi = {
  crear: (datos) => api.post('/api/repuestos', datos),
  // PUT y no PATCH: reemplaza la ficha entera, así repetirlo deja el mismo estado.
  actualizar: (id, datos) => api.put(`/api/repuestos/${id}`, datos),
  porCodigo: (codigo) => api.get(`/api/repuestos?codigo=${q(codigo)}`),
  porTexto: (texto) => api.get(`/api/repuestos?q=${q(texto)}`),
  ficha: (id) => api.get(`/api/repuestos/${id}`),
  kardex: (id) => api.get(`/api/repuestos/${id}/kardex`),
  /** Las correcciones de la ficha, de la más antigua a la más reciente. */
  correcciones: (id) => api.get(`/api/repuestos/${id}/correcciones`),
}

export const inventarioApi = {
  /**
   * @param categoriaId     solo esa categoría; `sinCategoria` solo los que no tienen (no las dos)
   * @param conStockPrimero el orden del catálogo del mostrador: los agotados al final (spec 0005)
   */
  listar: ({ texto = '', soloStockBajo = false, categoriaId = '', sinCategoria = false, conStockPrimero = false,
    pagina = 0, tamano = 25 } = {}) =>
    api.get(`/api/inventario?q=${q(texto)}&soloStockBajo=${soloStockBajo}`
      + (categoriaId ? `&categoriaId=${categoriaId}` : '')
      + (sinCategoria ? '&sinCategoria=true' : '')
      + (conStockPrimero ? '&conStockPrimero=true' : '')
      + `&pagina=${pagina}&tamano=${tamano}`),
  /** Cuántos repuestos de lo buscado hay en cada categoría; `categoriaId` nulo = sin categoría. */
  categorias: (texto = '') => api.get(`/api/inventario/categorias?q=${q(texto)}`),
  resumen: () => api.get('/api/inventario/resumen'),
}

export const proveedoresApi = {
  activos: () => api.get('/api/proveedores'),
  crear: (datos) => api.post('/api/proveedores', datos),
}

export const cuentasApi = {
  activas: () => api.get('/api/cuentas'),
  crear: (datos) => api.post('/api/cuentas', datos),
  // No hay borrar: una cuenta con compras se desactiva y las compras la siguen nombrando.
  desactivar: (id) => api.post(`/api/cuentas/${id}/desactivacion`),
}

export const turnosApi = {
  /** El turno abierto, o `null` si no hay (el servidor responde 204: no es un error). */
  abierto: () => api.get('/api/turnos/abierto'),
  abrir: (datos) => api.post('/api/turnos', datos),
  /**
   * Un turno con sus gastos, retiros, compras de caja y ventas. Abierto, `cierre` viene en null: lo que
   * debería haber no sale del servidor antes de cerrar (spec 0006, RF-023).
   */
  detalle: (id) => api.get(`/api/turnos/${id}`),
  /** Los cerrados, del más reciente al más antiguo. */
  cerrados: ({ pagina = 0, tamano = 25 } = {}) => api.get(`/api/turnos?estado=CERRADO&pagina=${pagina}&tamano=${tamano}`),
  /** Sin llave: repetirlo responde 409 `TURNO_CERRADO` (ver `ErrorApi.esTurnoCerrado`). Devuelve el turno cerrado. */
  cerrar: (id, contado) => api.post(`/api/turnos/${id}/cierre`, { contado }),
  /** Una sola vez por cierre. */
  observaciones: (id, observaciones) => api.put(`/api/turnos/${id}/observaciones`, { observaciones }),
}

export const categoriasGastoApi = {
  /** Activas y desactivadas: los gastos viejos siguen nombrando a las desactivadas. */
  todas: () => api.get('/api/categorias-gasto'),
  crear: (datos) => api.post('/api/categorias-gasto', datos),
  /** Reemplaza el nombre y si se paga cada mes. */
  actualizar: (id, { nombre, mensual }) => api.put(`/api/categorias-gasto/${id}`, { nombre, mensual }),
  // No hay borrar: una categoría con gastos se desactiva.
  desactivar: (id) => api.post(`/api/categorias-gasto/${id}/desactivacion`),
}

export const gastosApi = {
  /** Con la misma llave, repetirlo devuelve el mismo gasto. 409 `CONFIRMAR_MONTO` si hay que confirmar. */
  registrar: (datos) => api.post('/api/gastos', datos),
  /** `consulta`: los filtros con valor, `pagina` y `tamano` (ver `consultaDeGastos`). */
  listar: (consulta) => api.get(`/api/gastos?${new URLSearchParams(consulta)}`),
  totales: (consulta) => api.get(`/api/gastos/totales?${new URLSearchParams(consulta)}`),
  anular: (id, motivo) => api.post(`/api/gastos/${id}/anulacion`, { motivo }),
}

export const reportesApi = {
  /**
   * Los resultados de un período: cifras, desgloses y día por día, de un solo cálculo en el servidor.
   * `consulta`: `desde`, `hasta` (días de Colombia) y `gastosDelMes` (ver `consultaDeResultados`). 422 con el
   * porqué si el período está al revés, pasa de 366 días o termina después de hoy.
   */
  resultados: (consulta) => api.get(`/api/reportes/resultados?${new URLSearchParams(consulta)}`),
}

export const retirosApi = {
  registrar: (datos) => api.post('/api/retiros', datos),
  anular: (id, motivo) => api.post(`/api/retiros/${id}/anulacion`, { motivo }),
}

export const ventasApi = {
  /**
   * Cobra la venta. Con la misma llave, repetirlo devuelve la misma venta: es seguro reintentar tras un
   * corte. 409 con `problemas` si algún renglón no se puede cobrar tal como está.
   */
  cobrar: (datos) => api.post('/api/ventas', datos),
  delTurnoAbierto: () => api.get('/api/ventas?turno=abierto'),
  porNumero: (numero) => api.get(`/api/ventas/numero/${numero}`),
  detalle: (id) => api.get(`/api/ventas/${id}`),
  /** Sin llave: repetirlo responde 422 "ya fue anulada", nunca devuelve el stock dos veces. */
  anular: (id, motivo) => api.post(`/api/ventas/${id}/anulacion`, { motivo }),
  /** ¿Queda a pérdida? El cajero recibe solo `bajoCosto` y `sinCosto`; el administrador, además las cifras. */
  avisoDePerdida: (consulta) => api.post('/api/ventas/aviso-de-perdida', consulta),
}

/** Los clientes (spec 0008): a quién se le fía y a nombre de quién va una venta. */
export const clientesApi = {
  /** Por nombre, cédula o celular, con lo que debe cada uno. Sin texto, ninguno. */
  buscar: (texto) => api.get(`/api/clientes?q=${q(texto)}`),
  /** 409 `CLIENTE_REPETIDO` con el que ya tiene esa cédula (ver `ErrorApi.esClienteRepetido`). */
  crear: (datos) => api.post('/api/clientes', datos),
  /**
   * PUT: reemplaza los datos. El cajero puede completar lo que falta; cambiar lo escrito responde 403: es del
   * administrador.
   */
  actualizar: (id, datos) => api.put(`/api/clientes/${id}`, datos),
  /** La ficha: datos, lo que debe, cada venta fiada con su estado y cada abono. */
  ficha: (id) => api.get(`/api/clientes/${id}`),
  /** Del administrador. Devuelve la ficha. */
  cerrarFiado: (id, motivo) => api.post(`/api/clientes/${id}/cierre-del-fiado`, { motivo }),
  abrirFiado: (id) => api.post(`/api/clientes/${id}/apertura-del-fiado`),
  /** Lo que ya debía en el cuaderno, una sola vez. Del administrador. */
  saldoDelCuaderno: (id, datos) => api.post(`/api/clientes/${id}/saldo-del-cuaderno`, datos),
  /** Sus compras, fiadas o de contado, de la más reciente a la más antigua. */
  ventas: (id, { pagina = 0, tamano = 25 } = {}) =>
    api.get(`/api/clientes/${id}/ventas?pagina=${pagina}&tamano=${tamano}`),
}

/** La Cartera (spec 0008): quién debe y cuánto. `vista`: `DEBEN` o `HISTORIAL`; `q`: nombre, cédula o celular. */
export const carteraApi = {
  lista: ({ vista = 'DEBEN', q: texto = '', modoFecha = 'VENTA', desde = null, hasta = null } = {}) =>
    api.get(`/api/cartera?vista=${vista}&q=${q(texto)}&modoFecha=${modoFecha}`
      + (desde && hasta ? `&desde=${desde}&hasta=${hasta}` : '')),
}

/** Los abonos de los clientes (spec 0008, H4). */
export const abonosApi = {
  /**
   * Con la misma llave, repetirlo devuelve el mismo abono: es seguro reintentar tras un corte. `primeroA` es la deuda
   * que el cliente dijo que paga; sin ella, a lo más viejo.
   */
  registrar: (datos) => api.post('/api/abonos', datos),
  /** Para reimprimir el recibo. */
  ver: (id) => api.get(`/api/abonos/${id}`),
  /** Del administrador; en efectivo, solo mientras su turno siga abierto. */
  anular: (id, motivo) => api.post(`/api/abonos/${id}/anulacion`, { motivo }),
}

/** Las personas que entran al sistema (spec 0004, fase 4). Solo el administrador. */
export const usuariosApi = {
  todos: () => api.get('/api/usuarios'),
  crear: (datos) => api.post('/api/usuarios', datos),
  cambiarRol: (id, rol) => api.put(`/api/usuarios/${id}/rol`, { rol }),
  desactivar: (id) => api.post(`/api/usuarios/${id}/desactivacion`),
  activar: (id) => api.post(`/api/usuarios/${id}/activacion`),
  /** El registro de entradas (RF-025), de la más reciente a la más antigua. */
  entradas: ({ pagina = 0, tamano = 50 } = {}) => api.get(`/api/usuarios/entradas?pagina=${pagina}&tamano=${tamano}`),
  /** Devuelve `{ contrasenaTemporal }`: se muestra una sola vez. */
  restablecer: (id) => api.post(`/api/usuarios/${id}/restablecimiento`),
}

/** El correo del cierre de caja (spec 0010). Todo es del administrador. */
export const correosApi = {
  estado: () => api.get('/api/correos'),
  destinatarios: (correos) => api.put('/api/correos/destinatarios', { correos }),
  /** Sale en el momento: la respuesta dice si salió o por qué no. */
  prueba: () => api.post('/api/correos/prueba'),
  reintentar: (id) => api.post(`/api/correos/${id}/reintento`),
}

/** El respaldo de la base (spec 0011). Todo es del administrador. */
export const respaldosApi = {
  estado: () => api.get('/api/respaldos'),
  /**
   * Baja la copia completa de la base al equipo de quien la pidió.
   *
   * <p>No pasa por `pedir`: lo que llega no es JSON, es el archivo. Se trae entero antes de guardarlo porque así se
   * puede mostrar el error de verdad —"no se pudo sacar la copia: …"— en vez de dejar que el navegador se lleve una
   * página de error con nombre de respaldo.
   *
   * @returns {Promise<{blob: Blob, nombre: string}>}
   */
  async bajarArchivo() {
    let respuesta
    try {
      respuesta = await fetch('/api/respaldos/archivo')
    } catch {
      throw new ErrorApi(0, { mensaje: 'No hay conexión con el servidor' })
    }
    if (!respuesta.ok) {
      const texto = await respuesta.text()
      let datos
      try {
        datos = texto ? JSON.parse(texto) : null
      } catch {
        // No siempre llega JSON: un 502 del intermediario, una página de error. Igual hay que decir algo legible.
        datos = { mensaje: `El servidor falló (${respuesta.status})` }
      }
      const error = new ErrorApi(respuesta.status, datos)
      if (error.codigo === 'SIN_SESION') window.dispatchEvent(new CustomEvent(EVENTO_SIN_SESION))
      throw error
    }
    return { blob: await respuesta.blob(), nombre: nombreDelArchivo(respuesta) }
  },
}

/** El nombre que el servidor le puso al archivo, o uno de respaldo si el encabezado no viene. */
function nombreDelArchivo(respuesta) {
  const encabezado = respuesta.headers.get('Content-Disposition') ?? ''
  return encabezado.match(/filename="([^"]+)"/)?.[1] ?? 'rdmotors.dump'
}

export const tiendaApi = {
  /** Los datos que encabezan el comprobante. Siempre hay: arranca con "RD MOTORS". */
  obtener: () => api.get('/api/tienda'),
  // PUT: reemplaza todos los datos, así repetirlo deja el mismo estado.
  actualizar: (datos) => api.put('/api/tienda', datos),
}

export const comprasApi = {
  registrar: (datos) => api.post('/api/compras', datos),
  /** `consulta`: los filtros con valor, `pagina` y `tamano` (ver `consultaDelHistorial`). */
  historial: (consulta) => api.get(`/api/compras?${new URLSearchParams(consulta)}`),
  /** Los totales de los mismos filtros, sin página (ver `consultaDeTotales`). Las anuladas no suman. */
  totales: (consulta) => api.get(`/api/compras/totales?${new URLSearchParams(consulta)}`),
  /** Con `repuesto`, cada renglón trae `coincide`: el que decide qué se resalta es el backend. */
  detalle: (id, repuesto) =>
    api.get(`/api/compras/${id}${repuesto ? `?repuesto=${q(repuesto)}` : ''}`),
  /** Devuelve `{ compra, avisos }`. 409 si otra corrección se guardó en el medio. */
  corregir: (id, datos) => api.post(`/api/compras/${id}/correcciones`, datos),
  anular: (id, datos) => api.post(`/api/compras/${id}/anulacion`, datos),
}
