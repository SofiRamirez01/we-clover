import { ESTADO_TANDA_LABELS } from '../../types/produccion';
import type { ProduccionTanda, TandaResponse } from '../../types/produccion';
import { CLASES_ESTADO_TANDA, COLOR_SIN_TANDA, colorDeTanda } from './tandaVisual';

interface EncabezadoTandaProps {
  /** null = grupo "Sin tanda". */
  tanda: ProduccionTanda | null;
  /** Totales reales de la tanda (GET /api/tandas); puede faltar un instante mientras carga. */
  totales: TandaResponse | undefined;
  pedidosVisibles: number;
}

/** Encabezado de cada grupo de la grilla: la tanda es lo primero que se ve. Los totales son los
 *  de la tanda completa; si los filtros esconden algún pedido se aclara "x de y visibles". */
export default function EncabezadoTanda({ tanda, totales, pedidosVisibles }: EncabezadoTandaProps) {
  if (!tanda) {
    return (
      <div className="flex flex-wrap items-center gap-3 rounded-lg px-3 py-2" style={{ backgroundColor: 'rgba(107, 114, 104, 0.12)' }}>
        <span className="text-xl font-extrabold" style={{ color: COLOR_SIN_TANDA }}>
          Sin tanda
        </span>
        <span className="text-xs text-wc-text-muted">
          {pedidosVisibles} pedido{pedidosVisibles === 1 ? '' : 's'} · ordenados por puntaje sugerido (% de pago)
        </span>
      </div>
    );
  }

  const color = colorDeTanda(tanda.id);
  const total = totales?.cantidadPedidos ?? pedidosVisibles;

  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-lg px-3 py-2" style={{ backgroundColor: `${color}1f` }}>
      <span className="flex items-baseline gap-2" style={{ color }}>
        <span className="text-3xl font-black leading-none">{tanda.nombre}</span>
        {tanda.posicion != null && <span className="text-xs font-bold uppercase tracking-wide">Tanda {tanda.posicion}</span>}
      </span>

      <span className={`rounded-full px-2 py-0.5 text-[11px] font-bold ${CLASES_ESTADO_TANDA[tanda.estado]}`}>
        {ESTADO_TANDA_LABELS[tanda.estado]}
      </span>

      <span className="text-sm font-semibold text-wc-text">
        {total} pedido{total === 1 ? '' : 's'}
        {pedidosVisibles !== total && (
          <span className="ml-1 text-xs font-normal text-wc-text-muted">
            ({pedidosVisibles} de {total} visibles con los filtros)
          </span>
        )}
      </span>

      {totales && totales.unidadesPorTipoPrenda.length > 0 && (
        <span className="flex flex-wrap items-center gap-1">
          {totales.unidadesPorTipoPrenda.map((item) => (
            <span key={item.tipoPrenda} className="rounded border border-wc-border bg-white px-1.5 py-0.5 text-[11px] font-medium text-wc-text">
              {item.tipoPrenda} {item.unidades}
            </span>
          ))}
        </span>
      )}
    </div>
  );
}
