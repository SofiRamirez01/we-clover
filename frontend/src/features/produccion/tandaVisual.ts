import type { EstadoTanda } from '../../types/produccion';

/** Colores de tanda: se eligen por id (nunca por nombre), así renombrar una tanda no le cambia
 *  el color y dos tandas abiertas seguidas casi nunca comparten uno. */
const PALETA_TANDA = ['#025939', '#1d4ed8', '#b45309', '#7e22ce', '#be123c', '#0e7490', '#4d7c0f', '#c2410c'];

export function colorDeTanda(idTanda: number): string {
  return PALETA_TANDA[idTanda % PALETA_TANDA.length];
}

export const COLOR_SIN_TANDA = '#6b7268';

export const CLASES_ESTADO_TANDA: Record<EstadoTanda, string> = {
  PLANIFICADA: 'bg-slate-100 text-slate-700',
  EN_PRODUCCION: 'bg-amber-100 text-amber-800',
  CERRADA: 'bg-gray-200 text-gray-600',
};

/** dd/mm/aaaa a partir de un ISO de fecha o fecha-hora. */
export function formatearFechaCorta(iso: string | null): string {
  if (!iso) return '—';
  const [anio, mes, dia] = iso.slice(0, 10).split('-');
  return `${dia}/${mes}/${anio}`;
}
