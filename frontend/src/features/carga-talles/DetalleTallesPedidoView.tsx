import { useEffect, useState } from 'react';
import AppHeader from '../../components/AppHeader';
import LoginView from '../../components/LoginView';
import { AuthProvider, useAuth } from '../../context/AuthContext';
import { obtenerCargaTallesInterno } from '../../services/cargaTallesService';
import CargaTallesDetalleTabla from './CargaTallesDetalleTabla';
import type { CargaTallesResponse } from '../../types/cargaTalles';

interface DetalleTallesPedidoViewProps {
  idPedido: number;
}

/** Contenido de la pestaña aparte que abre "Ver detalle" desde Ficha Técnica — mismo login que
 *  el resto de la app (lee la sesión de localStorage, ver AuthProvider), pero sin sidebar ni
 *  navegación: es una vista de una sola pantalla para consultar el detalle alumno por alumno. */
function DetalleTallesPedidoContenido({ idPedido }: DetalleTallesPedidoViewProps) {
  const { usuario } = useAuth();
  const [carga, setCarga] = useState<CargaTallesResponse | null>(null);
  const [estadoCarga, setEstadoCarga] = useState<'cargando' | 'listo' | 'error'>('cargando');

  useEffect(() => {
    obtenerCargaTallesInterno(idPedido)
      .then((data) => {
        setCarga(data);
        setEstadoCarga('listo');
      })
      .catch(() => setEstadoCarga('error'));
  }, [idPedido]);

  if (!usuario) {
    return <LoginView />;
  }

  return (
    <div className="tw-scope px-8 pt-7 pb-12">
      <AppHeader title="Detalle de talles" />

      {estadoCarga === 'cargando' && <p className="text-sm text-wc-text-muted">Cargando…</p>}
      {estadoCarga === 'error' && (
        <p className="text-sm text-wc-text-muted">No se pudo cargar el detalle de este pedido.</p>
      )}

      {estadoCarga === 'listo' && carga && (
        <div className="flex flex-col gap-4">
          <div className="rounded-xl border border-wc-border bg-white p-4 shadow-sm">
            <h2 className="text-sm font-bold text-wc-text">{carga.nombreColegio}</h2>
            <p className="text-xs text-wc-text-muted">{carga.curso}</p>
          </div>

          <CargaTallesDetalleTabla carga={carga} productosConTalle={carga.productos.filter((p) => p.tieneTalle)} />
        </div>
      )}
    </div>
  );
}

export default function DetalleTallesPedidoView({ idPedido }: DetalleTallesPedidoViewProps) {
  return (
    <AuthProvider>
      <DetalleTallesPedidoContenido idPedido={idPedido} />
    </AuthProvider>
  );
}
