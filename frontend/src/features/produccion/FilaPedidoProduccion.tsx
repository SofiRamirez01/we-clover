import EstadoBadge from '../fichas-tecnicas/EstadoBadge';
import { ESTADO_BANDERA_LABELS, ETAPAS_PRODUCCION, ETAPA_PRODUCCION_LABELS } from '../../types/produccion';
import type { NotaPedido, ProduccionPedidoResponse, ProduccionProductoResponse, UsuarioResumen } from '../../types/produccion';
import type { EstadoBandera } from '../../types/pedido';
import FilaProductoProduccion from './FilaProductoProduccion';
import PanelSeguimientoPedido from './PanelSeguimientoPedido';
import { COLOR_SIN_TANDA, colorDeTanda, formatearFechaCorta } from './tandaVisual';

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
  puedeEditarUbicacion: boolean;
  onToggleExpandir: () => void;
  onMarcarEtapa: (
    idProducto: number,
    etapa: (typeof ETAPAS_PRODUCCION)[number],
    completado: boolean,
    idEmpleado: number | null,
    comentario?: string,
  ) => void;
  onMarcarBandera: (idProducto: number, estadoBandera: EstadoBandera) => void;
  onAgregarNota: (texto: string) => Promise<NotaPedido | null>;
  onCambiarUbicacion: (ubicacion: string) => Promise<boolean>;
}

/** Estado legible de una prenda: el visual de etapas, o el de la bandera si es Bandera. */
function estadoDePrenda(producto: ProduccionProductoResponse): string {
  if (producto.esBandera) {
    return ESTADO_BANDERA_LABELS[producto.estadoBandera ?? 'PENDIENTE'];
  }
  const estado = producto.estadoVisual ?? 'PENDIENTE';
  return estado.charAt(0) + estado.slice(1).toLowerCase();
}

function clasesEstadoPrenda(producto: ProduccionProductoResponse): string {
  const estado = producto.esBandera ? producto.estadoBandera : producto.estadoVisual;
  if (estado === 'TERMINADO' || estado === 'RECIBIDO') return 'border-emerald-200 bg-emerald-50 text-emerald-800';
  if (!estado || estado === 'PENDIENTE') return 'border-wc-border bg-white text-wc-text-muted';
  return 'border-amber-200 bg-amber-50 text-amber-800';
}

export default function FilaPedidoProduccion({
  pedido,
  expandido,
  empleados,
  guardandoClave,
  puedeEditarUbicacion,
  onToggleExpandir,
  onMarcarEtapa,
  onMarcarBandera,
  onAgregarNota,
  onCambiarUbicacion,
}: FilaPedidoProduccionProps) {
  const colorTanda = pedido.tanda ? colorDeTanda(pedido.tanda.id) : COLOR_SIN_TANDA;
  // Un pedido puede entrar a una tanda antes de estar listo: no se bloquea, se avisa.
  const noEstaListo =
    pedido.tanda != null && (pedido.estadoActual === 'PRESUPUESTADO' || pedido.estadoActual === 'SENADO');

  return (
    <div className="overflow-hidden rounded-lg border border-wc-border bg-white" style={{ borderLeft: `4px solid ${colorTanda}` }}>
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
        className="flex w-full cursor-pointer flex-col gap-1.5 px-3 py-2 text-left transition hover:bg-wc-bg"
      >
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <span className={`shrink-0 text-wc-text-muted transition-transform ${expandido ? 'rotate-180' : ''}`}>
            <ChevronDownIcon />
          </span>

          <span
            className="flex h-7 min-w-7 shrink-0 items-center justify-center rounded-md px-1.5 text-sm font-extrabold text-white"
            style={{ backgroundColor: colorTanda }}
            title={pedido.tanda ? `Tanda ${pedido.tanda.nombre}` : 'Sin tanda'}
          >
            {pedido.tanda ? pedido.tanda.nombre : '—'}
          </span>

          <div className="min-w-[12rem] flex-1">
            <div className="flex flex-wrap items-baseline gap-x-1.5 gap-y-0.5">
              <span className="text-base font-extrabold text-wc-text">#{pedido.codigoInterno}</span>
              <span className="truncate text-sm font-semibold text-wc-text">{pedido.colegio}</span>
              <span className="text-xs text-wc-text-muted">{pedido.curso}</span>
            </div>
          </div>

          {noEstaListo && (
            <span
              className="shrink-0 rounded-full border border-amber-300 bg-amber-50 px-2 py-0.5 text-[11px] font-semibold text-amber-800"
              title="Está en una tanda pero todavía no cumple los requisitos para producción"
            >
              ⚠ Todavía no está listo
            </span>
          )}

          <EstadoBadge estado={pedido.estadoActual} />

          <span className="shrink-0 whitespace-nowrap text-right text-sm font-semibold text-wc-text">
            {Math.round(pedido.porcentajePagado)}% pago
          </span>

          <span className="shrink-0 whitespace-nowrap text-right text-xs text-wc-text-muted" title="Fecha estimada de entrega">
            Entrega {formatearFechaCorta(pedido.fechaEstimadaEntrega)}
          </span>
        </div>

        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 pl-7">
          <div className="flex flex-wrap items-center gap-1">
            {pedido.productos.map((producto) => (
              <span
                key={producto.id}
                className={`rounded border px-1.5 py-0.5 text-[11px] font-medium ${clasesEstadoPrenda(producto)}`}
              >
                {producto.tipoPrenda ?? 'Sin tipo'} {producto.cantidadTotal} · {estadoDePrenda(producto)}
              </span>
            ))}
          </div>

          {pedido.ubicacionActual && (
            <span className="text-xs text-wc-text" title="Ubicación actual">
              📍 {pedido.ubicacionActual}
            </span>
          )}

          {pedido.ultimaNota && (
            <span className="min-w-0 max-w-full truncate text-xs italic text-wc-text-muted" title={pedido.ultimaNota.texto}>
              “{pedido.ultimaNota.texto}”
              {pedido.cantidadNotas > 1 ? ` (+${pedido.cantidadNotas - 1})` : ''}
            </span>
          )}
        </div>
      </div>

      {expandido && (
        <>
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
          <PanelSeguimientoPedido
            pedido={pedido}
            puedeEditarUbicacion={puedeEditarUbicacion}
            onAgregarNota={onAgregarNota}
            onCambiarUbicacion={onCambiarUbicacion}
          />
        </>
      )}
    </div>
  );
}
