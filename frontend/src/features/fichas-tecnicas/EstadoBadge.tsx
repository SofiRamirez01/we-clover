import { BUCKET_POR_ESTADO, ESTADO_PEDIDO_LABELS } from '../../types/pedido';
import type { EstadoPedido } from '../../types/pedido';

// Mismos colores que .estado-badge en PedidosListView.css, para que el estado se vea
// igual acá que en el listado de Pedidos (celeste = en producción, verde = entregado,
// ámbar = pendiente).
export const ESTILO_POR_BUCKET: Record<string, string> = {
  pendiente: 'bg-[#fef3e2] text-[#a9660a]',
  en_produccion: 'bg-[#e8f1fb] text-[#1d5fa8]',
  entregado: 'bg-wc-green/10 text-wc-green-dark',
  cancelado: 'bg-red-100 text-red-700',
};

export function estiloBadgeEstado(estado: EstadoPedido): string {
  return ESTILO_POR_BUCKET[BUCKET_POR_ESTADO[estado]];
}

export default function EstadoBadge({ estado }: { estado: EstadoPedido }) {
  return (
    <span className={`inline-block rounded-full px-2.5 py-1 text-[11px] font-bold ${estiloBadgeEstado(estado)}`}>
      {ESTADO_PEDIDO_LABELS[estado]}
    </span>
  );
}
