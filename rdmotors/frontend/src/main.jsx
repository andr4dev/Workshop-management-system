import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.jsx'
import './index.css'

// El tema se aplica antes del primer render para que no haya destello blanco al
// abrir en modo oscuro — en un local con poca luz ese flash molesta de verdad.
const temaGuardado = (() => {
  try {
    return localStorage.getItem('rdmotors:tema')
  } catch {
    return null   // navegación privada, o almacenamiento bloqueado
  }
})()

const prefiereOscuro = window.matchMedia?.('(prefers-color-scheme: dark)').matches
document.documentElement.dataset.theme = temaGuardado ?? (prefiereOscuro ? 'dark' : 'light')

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
