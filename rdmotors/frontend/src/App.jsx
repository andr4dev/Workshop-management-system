import { useState } from 'react'
import { BrowserRouter, Link, Navigate, NavLink, Route, Routes, useLocation } from 'react-router-dom'
import Vender from './paginas/Vender'
import Ventas from './paginas/Ventas'
import DetalleVenta from './paginas/DetalleVenta'
import Tienda from './paginas/Tienda'
import Usuarios from './paginas/Usuarios'
import Entradas from './paginas/Entradas'
import Caja from './paginas/Caja'
import Gastos from './paginas/Gastos'
import Respaldo from './paginas/Respaldo'
import Resultados from './paginas/Resultados'
import Turnos from './paginas/Turnos'
import DetalleTurno from './paginas/DetalleTurno'
import Compra from './paginas/Compra'
import HistorialCompras from './paginas/HistorialCompras'
import DetalleCompra from './paginas/DetalleCompra'
import CorregirCompra from './paginas/CorregirCompra'
import Inventario from './paginas/Inventario'
import Cartera from './paginas/Cartera'
import Correos from './paginas/Correos'
import FichaCliente from './paginas/FichaCliente'
import FichaRepuesto from './paginas/FichaRepuesto'
import AvisoCarga from './componentes/AvisoCarga'
import AvisoDeRespaldo from './componentes/AvisoDeRespaldo'
import ProveedorDeSesion from './componentes/sesion/ProveedorDeSesion'
import Entrar from './componentes/sesion/Entrar'
import CrearAdministrador from './componentes/sesion/CrearAdministrador'
import CambiarContrasena from './componentes/sesion/CambiarContrasena'
import MenuDeUsuario from './componentes/sesion/MenuDeUsuario'
import { useSesion } from './componentes/sesion/contexto'
import { pantallaDeSesion } from './utils/sesion'
import { esAdministrador, MENSAJE_ES_DEL_ADMINISTRADOR, modulosPara, puedeAbrir } from './utils/permisos'
import estilos from './App.module.css'

/**
 * Un módulo por pestaña, y cada uno dueño de sus pantallas:
 *
 *   Vender      el mostrador: turno de caja, venta, y las ventas del turno con su comprobante
 *               (spec 0003); la caja con sus gastos, retiros y cierre (spec 0006). Es la pantalla de inicio
 *   Cartera     quién debe, cuánto y desde cuándo; la ficha de cada cliente con sus ventas fiadas y sus
 *               abonos (spec 0008)
 *   Compras     registrar, e historial con el detalle de cada factura
 *   Inventario  lo que hay, y la ficha de cada repuesto con su kardex
 *   Reportes    los resultados del período: ventas, utilidad bruta y ganancia (spec 0007), y los gastos del
 *               negocio (spec 0006)
 *
 * El inventario NO va dentro de compras. Hoy su kardex solo tiene compras, pero cuando llegue el
 * mostrador tendrá ventas, devoluciones y ajustes: es de inventario, no de quien le da entrada.
 *
 * Antes que todo, **hay que entrar** (spec 0004): sin sesión solo se ve *Entrar*, o *Crear el administrador* la
 * primera vez. Si la sesión se cae, se vuelve a *Entrar* sin perder la dirección: al entrar, se sigue ahí.
 */
export default function App() {
  return (
    <ProveedorDeSesion>
      <BrowserRouter>
        <Puerta />
      </BrowserRouter>
    </ProveedorDeSesion>
  )
}

/** Qué se ve según la sesión: entrar, instalar, cambiar la contraseña obligatoria, o el sistema. */
function Puerta() {
  const sesion = useSesion()
  const pantalla = pantallaDeSesion(sesion)

  if (sesion.error && !sesion.usuario) {
    return (
      <main className={estilos.cargando}>
        <AvisoCarga error={sesion.error} onReintentar={sesion.recargar} />
      </main>
    )
  }
  if (pantalla === 'CARGANDO') return <main className={estilos.cargando}>Cargando…</main>
  if (pantalla === 'INSTALAR') return <CrearAdministrador />
  if (pantalla === 'ENTRAR') return <Entrar />
  if (pantalla === 'CAMBIAR_CONTRASENA') return <CambiarContrasena obligatoria />
  return <Sistema />
}

function Sistema() {
  const { usuario } = useSesion()
  const { pathname } = useLocation()
  const [tema, setTema] = useState(() => document.documentElement.dataset.theme ?? 'light')

  function alternarTema() {
    const nuevo = tema === 'dark' ? 'light' : 'dark'
    setTema(nuevo)
    document.documentElement.dataset.theme = nuevo
    try {
      localStorage.setItem('rdmotors:tema', nuevo)
    } catch {
      // Navegación privada o almacenamiento bloqueado: el tema simplemente no se recuerda.
      // Nunca se rompe la app por no poder guardar una preferencia.
    }
  }

  const claseEnlace = ({ isActive }) => `${estilos.enlace} ${isActive ? estilos.activo : ''}`

  return (
    <div className={estilos.app}>
      <header className={estilos.barra}>
        {/* La marca vive AQUÍ: en la cabecera. Es el único sitio donde el rojo de RD Motors
            aparece en grande, para que no compita con el rojo de error del contenido. */}
        <div className={estilos.marca}>
          <span className={estilos.logoR}>RD</span>
          <span className={estilos.logoMotors}>MOTORS</span>
          <span className={estilos.tagline}>Almacén de repuesto</span>
        </div>

        <nav className={estilos.navegacion} aria-label="Módulos">
          {/* Cada rol ve sus módulos (spec 0004, §5): el cajero, Vender e Inventario. */}
          {modulosPara(usuario).map((m) => (
            <NavLink key={m.ruta} to={m.ruta} className={claseEnlace}>{m.nombre}</NavLink>
          ))}
        </nav>

        {/* Los datos de la tienda se tocan pocas veces: un botón discreto, no una pestaña más. Del administrador. */}
        {esAdministrador(usuario) && (
          <NavLink to="/tienda" className={estilos.tema} aria-label="Datos de la tienda" title="Datos de la tienda">
            ⚙
          </NavLink>
        )}

        <button
          className={estilos.tema}
          onClick={alternarTema}
          aria-label={tema === 'dark' ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
          title={tema === 'dark' ? 'Modo claro' : 'Modo oscuro'}
          type="button"
        >
          {tema === 'dark' ? '☀' : '☾'}
        </button>

        <MenuDeUsuario />
      </header>

      {/* Si el respaldo de anoche falló, el administrador lo ve al entrar (spec 0009, RF-006). */}
      <AvisoDeRespaldo />

      <main className={estilos.contenido}>
        {/* Una dirección del administrador, escrita a mano por un cajero: se dice, no se abre. El servidor igual la
            negaría; así no se ve una pantalla a medias con "no permitido" en cada parte. */}
        {!puedeAbrir(usuario, pathname) ? <EsDelAdministrador /> : (
        <Routes>
          <Route path="/" element={<Navigate to="/vender" replace />} />
          <Route path="/vender" element={<Vender />} />
          <Route path="/vender/ventas" element={<Ventas />} />
          <Route path="/vender/ventas/:id" element={<DetalleVenta />} />
          <Route path="/vender/caja" element={<Caja />} />
          <Route path="/vender/caja/turnos" element={<Turnos />} />
          <Route path="/vender/caja/turnos/:id" element={<DetalleTurno />} />
          {/* La lista de gastos vivió unas horas en Vender: los enlaces viejos llegan a Reportes. */}
          <Route path="/vender/gastos" element={<Navigate to="/reportes/gastos" replace />} />
          <Route path="/reportes" element={<Navigate to="/reportes/resultados" replace />} />
          <Route path="/reportes/resultados" element={<Resultados />} />
          <Route path="/reportes/gastos" element={<Gastos />} />
          <Route path="/tienda" element={<Tienda />} />
          <Route path="/respaldo" element={<Respaldo />} />
          <Route path="/correos" element={<Correos />} />
          <Route path="/usuarios" element={<Usuarios />} />
          <Route path="/usuarios/entradas" element={<Entradas />} />
          <Route path="/compras" element={<Compra />} />
          <Route path="/compras/historial" element={<HistorialCompras />} />
          <Route path="/compras/historial/:id" element={<DetalleCompra />} />
          <Route path="/compras/historial/:id/corregir" element={<CorregirCompra />} />
          <Route path="/cartera" element={<Cartera />} />
          <Route path="/cartera/:id" element={<FichaCliente />} />
          <Route path="/inventario" element={<Inventario />} />
          <Route path="/inventario/:id" element={<FichaRepuesto />} />
          <Route path="*" element={<Navigate to="/vender" replace />} />
        </Routes>
        )}
      </main>
    </div>
  )
}

function EsDelAdministrador() {
  return (
    <div className={estilos.cargando} role="alert">
      <h1 className={estilos.soloAdministrador}>{MENSAJE_ES_DEL_ADMINISTRADOR}</h1>
      <p>Esta parte la ve el administrador de la tienda. <Link to="/vender" className={estilos.volver}>Volver a Vender</Link></p>
    </div>
  )
}
