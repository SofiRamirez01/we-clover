import EstadoBadge from '../fichas-tecnicas/EstadoBadge';
import { ETAPAS_PRODUCCION, ETAPA_PRODUCCION_LABELS } from '../../types/produccion';
import type { ProduccionPedidoResponse, UsuarioResumen } from '../../types/produccion';
import type { EstadoBandera } from '../../types/pedido';
import FilaProductoProduccion from './FilaProductoProduccion';
import PrioridadInline from './PrioridadInline';

const ChevronDownIcon = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
    <path d="m6 9 6 6 6-6" />
  </svg>
);

interface FilaPedidoProduccionProps {
  pedido: ProduccionPedidoResponse;
  expandido: boolean;
  empleados: UsuarioResumen[];
  guardandoClave: string | null;
  onToggleExpandir: () => void;
  onMarcarEtapa: (idProducto: number, etapa: (typeof ETAPAS_PRODUCCION)[number], completado: boolean, idEmpleado: number | null) => void;
  onMarcarBandera: (idProducto: number, estadoBandera: EstadoBandera) => void;
  onCambiarPrioridad: (prioridad: number) => void;
  onQuitarPrioridad: () => void;
}

export default function FilaPedidoProduccion({
  pedido,
  expandido,
  empleados,
  guardandoClave,
  onToggleExpandir,
  onMarcarEtapa,
  onMarcarBandera,
  onCambiarPrioridad,
  onQuitarPrioridad,
}: FilaPedidoProduccionProps) {
  return (
    <div className="overflow-hidden rounded-lg border border-wc-border bg-white">
      <div
        role="button"
        tabIndex={0}
        onClick={onToggleExpandir}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onToggleExpandir();
          }
        }}
        aria-expanded={expandido}
        className="flex w-full cursor-pointer flex-wrap items-center gap-3 px-3 py-2.5 text-left transition hover:bg-wc-bg"
      >
        <span className={`shrink-0 text-wc-text-muted transition-transform ${expandido ? 'rotate-180' : ''}`}>
          <ChevronDownIcon />
        </span>

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-baseline gap-x-1.5 gap-y-0.5">
            <span className="text-base font-extrabold text-wc-text">#{pedido.codigoInterno}</span>
            <span className="truncate text-sm font-semibold text-wc-text">{pedido.colegio}</span>
            <span className="text-xs text-wc-text-muted">{pedido.curso}</span>
          </div>
        </div>

        <EstadoBadge estado={pedido.estadoActual} />

        <span className="w-16 shrink-0 text-right text-sm font-semibold text-wc-text">
          {Math.round(pedido.porcentajePagado)}% pago
        </span>

        <div onClick={(e) => e.stopPropagation()}>
          <PrioridadInline
            prioridadAutomatica={pedido.prioridadAutomatica}
            prioridadManual={pedido.prioridadManual}
            onCambiar={onCambiarPrioridad}
            onQuitar={onQuitarPrioridad}
          />
        </div>
      </div>

      {expandido && (
        <div className="overflow-x-auto border-t border-wc-border">
          <table className="w-full min-w-[720px] border-collapse">
            <thead>
              <tr className="bg-wc-bg text-[11px] font-bold uppercase tracking-wide text-wc-text-muted">
                <th className="px-2 py-2 text-left">Prenda</th>
                <th className="px-2 py-2 text-center">Cant.</th>
                {ETAPAS_PRODUCCION.map((etapa) => (
                  <th key={etapa} className="px-2 py-2 text-center">
                    {ETAPA_PRODUCCION_LABELS[etapa]}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {pedido.productos.map((producto) => (
                <FilaProductoProduccion
                  key={producto.id}
                  producto={producto}
                  empleados={empleados}
                  guardandoClave={guardandoClave}
                  onMarcarEtapa={onMarcarEtapa}
                  onMarcarBandera={onMarcarBandera}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
