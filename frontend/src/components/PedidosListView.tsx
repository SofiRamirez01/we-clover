import { useEffect, useMemo, useState } from 'react';
import './PedidosListView.css';
import AppHeader from './AppHeader';
import PestanaPedidos from '../features/base-ventas/PestanaPedidos';
import PestanaHistorico from '../features/base-ventas/PestanaHistorico';
import { promocionesDisponibles } from '../features/base-ventas/formato';
import { obtenerOpcionesFiltroPedidos } from '../services/pedidoService';
import { leerUsuarioGuardado } from '../utils/authStorage';
import type { OpcionesFiltroPedidos, PedidoResponse } from '../types/pedido';

type Pestana = 'pedidos' | 'historico';

const ROL_VENDEDOR = 'ROLE_VENDEDOR';
/** Recuerda la pestaña al volver de editar un pedido (la vista se desmonta). */
const PESTANA_STORAGE_KEY = 'weclover.baseVentas.pestana';

function leerPestana(): Pestana {
  try {
    return sessionStorage.getItem(PESTANA_STORAGE_KEY) === 'historico' ? 'historico' : 'pedidos';
  } catch {
    return 'pedidos';
  }
}

function guardarPestana(pestana: Pestana) {
  try {
    sessionStorage.setItem(PESTANA_STORAGE_KEY, pestana);
  } catch {
    // Sin sessionStorage (ventana privada, bloqueado): se vuelve a Pedidos, no pasa nada.
  }
}

interface PedidosListViewProps {
  onNuevoPedido: () => void;
  onEditarPedido: (pedido: PedidoResponse) => void;
  mensajeExito?: string | null;
}

/**
 * Base Ventas: pestaña Pedidos (lo que está en curso) e Histórico (todas las promociones, de
 * solo lectura, M5.4). Los filtros y la paginación se resuelven en el backend
 * (GET /pedidos/listado); un vendedor solo ve sus propias ventas en las dos pestañas.
 */
export default function PedidosListView({ onNuevoPedido, onEditarPedido, mensajeExito }: PedidosListViewProps) {
  const [pestana, setPestana] = useState<Pestana>(leerPestana);
  const [opciones, setOpciones] = useState<OpcionesFiltroPedidos>({ promociones: [], molderias: [] });
  const [recargasOpciones, setRecargasOpciones] = useState(0);
  const anioActual = new Date().getFullYear();
  const esVendedor = leerUsuarioGuardado()?.rol === ROL_VENDEDOR;

  useEffect(() => {
    let cancelado = false;
    obtenerOpcionesFiltroPedidos()
      .then((data) => {
        if (!cancelado) setOpciones(data);
      })
      .catch(() => {
        // Sin opciones el listado igual funciona: queda la promoción del año actual y ningún
        // filtro de moldería.
      });
    return () => {
      cancelado = true;
    };
  }, [recargasOpciones]);

  const promociones = useMemo(
    () => promocionesDisponibles(opciones.promociones, anioActual),
    [opciones.promociones, anioActual],
  );

  function elegirPestana(nueva: Pestana) {
    setPestana(nueva);
    guardarPestana(nueva);
  }

  return (
    <div className="pedidos-list-view">
      <AppHeader title="Base Ventas" />

      {mensajeExito && (
        <div className="alerta alerta--exito" role="status">
          {mensajeExito}
        </div>
      )}

      <div className="base-ventas-encabezado">
        <div className="base-ventas-tabs" role="tablist" aria-label="Base Ventas">
          <button
            type="button"
            role="tab"
            id="tab-pedidos"
            aria-selected={pestana === 'pedidos'}
            aria-controls="panel-base-ventas"
            className={`base-ventas-tab ${pestana === 'pedidos' ? 'base-ventas-tab--activa' : ''}`}
            onClick={() => elegirPestana('pedidos')}
          >
            Pedidos
          </button>
          <button
            type="button"
            role="tab"
            id="tab-historico"
            aria-selected={pestana === 'historico'}
            aria-controls="panel-base-ventas"
            className={`base-ventas-tab ${pestana === 'historico' ? 'base-ventas-tab--activa' : ''}`}
            onClick={() => elegirPestana('historico')}
          >
            Histórico
          </button>
        </div>
        {esVendedor && <span className="base-ventas-aviso">Estás viendo solo tus ventas.</span>}
      </div>

      <div id="panel-base-ventas" role="tabpanel" aria-labelledby={pestana === 'pedidos' ? 'tab-pedidos' : 'tab-historico'}>
        {pestana === 'pedidos' ? (
          <PestanaPedidos
            promociones={promociones}
            onEditarPedido={onEditarPedido}
            onNuevoPedido={onNuevoPedido}
            onPedidosImportados={() => setRecargasOpciones((valor) => valor + 1)}
          />
        ) : (
          <PestanaHistorico promociones={promociones} molderias={opciones.molderias} anioActual={anioActual} />
        )}
      </div>
    </div>
  );
}
