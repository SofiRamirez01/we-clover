import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import './styles/shared.css'
import './styles/tailwind.css'
import App from './App.tsx'
import CargaTallesPublicaView from './features/carga-talles/CargaTallesPublicaView.tsx'
import DetalleTallesPedidoView from './features/carga-talles/DetalleTallesPedidoView.tsx'

// La carga de talles es la única ruta genuinamente pública del sistema (sin login, ver
// CargaTallesPublicaController) — el resto de la app no rutea por URL en absoluto (todo es una
// sola SPA con estado interno). El detalle de talles por pedido (Ficha Técnica > "Ver detalle")
// sí exige login, pero también se resuelve por URL porque se abre en una pestaña aparte con
// window.open — no tiene sentido sumar una librería de routing entera para estas dos rutas.
const coincidenciaCargaTalles = window.location.pathname.match(/^\/carga-talles\/([^/]+)\/?$/);
const coincidenciaDetalleTalles = window.location.pathname.match(/^\/fichas-tecnicas\/pedidos\/(\d+)\/talles\/?$/);

function contenido() {
  if (coincidenciaCargaTalles) return <CargaTallesPublicaView token={coincidenciaCargaTalles[1]} />;
  if (coincidenciaDetalleTalles) return <DetalleTallesPedidoView idPedido={Number(coincidenciaDetalleTalles[1])} />;
  return <App />;
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {contenido()}
  </StrictMode>,
)
