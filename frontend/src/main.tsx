import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import './styles/shared.css'
import './styles/tailwind.css'
import App from './App.tsx'
import CargaTallesPublicaView from './features/carga-talles/CargaTallesPublicaView.tsx'

// La carga de talles es la única ruta genuinamente pública del sistema (sin login, ver
// CargaTallesPublicaController) — el resto de la app no rutea por URL en absoluto (todo es una
// sola SPA con estado interno). Se resuelve acá, antes de montar <App/> (que exige login), en
// vez de sumar una librería de routing entera para esta única ruta.
const coincidenciaCargaTalles = window.location.pathname.match(/^\/carga-talles\/([^/]+)\/?$/);

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {coincidenciaCargaTalles ? <CargaTallesPublicaView token={coincidenciaCargaTalles[1]} /> : <App />}
  </StrictMode>,
)
