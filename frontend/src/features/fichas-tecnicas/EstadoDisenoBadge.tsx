import { estadoDiseno } from './telaUtils';
import type { ProductoResponse } from '../../types/pedido';

const CheckIcon = () => (
  <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round">
    <path d="M4 12.5 9.5 18 20 6" />
  </svg>
);

const WarnIcon = () => (
  <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 9v4M12 17h.01M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z" />
  </svg>
);

/** Verifica que la ficha técnica de la prenda esté completa (imagen, tela, colores por
 *  posición y color de cierre si aplica) y lo muestra como un badge — "Falta" lleva un tooltip
 *  con el detalle de qué falta, para no tener que abrir el modal de edición para averiguarlo. */
export default function EstadoDisenoBadge({ producto }: { producto: ProductoResponse }) {
  const { completo, faltantes } = estadoDiseno(producto);

  if (completo) {
    return (
      <span className="inline-flex w-fit items-center gap-1 rounded-full bg-wc-green/10 px-2 py-0.5 text-[11px] font-bold text-wc-green-dark">
        <CheckIcon /> Completo
      </span>
    );
  }

  return (
    <span
      title={`Falta: ${faltantes.join(', ')}`}
      className="inline-flex w-fit items-center gap-1 rounded-full bg-red-50 px-2 py-0.5 text-[11px] font-bold text-red-600"
    >
      <WarnIcon /> Falta {faltantes.length > 1 ? `(${faltantes.length})` : faltantes[0]}
    </span>
  );
}
