import api from './api';
import type { AlertaPriorizacion, PriorizacionRequest, TandaResponse } from '../types/produccion';

/** Tandas en orden de cola con sus totales reales. Por defecto solo las no cerradas. */
export async function listarTandas(incluirCerradas = false): Promise<TandaResponse[]> {
  const { data } = await api.get<TandaResponse[]>('/tandas', { params: { incluirCerradas } });
  return data;
}

/** Solo ROLE_ADMINISTRATIVO. */
export async function listarAlertasPriorizacion(): Promise<AlertaPriorizacion[]> {
  const { data } = await api.get<AlertaPriorizacion[]>('/tandas/alertas');
  return data;
}

/** Aplica de una vez todo lo que cambió el popup de priorización (todo o nada). */
export async function guardarPriorizacion(request: PriorizacionRequest): Promise<void> {
  await api.post('/tandas/priorizacion', request);
}

export async function renombrarTanda(idTanda: number, nombre: string): Promise<TandaResponse> {
  const { data } = await api.patch<TandaResponse>(`/tandas/${idTanda}`, { nombre });
  return data;
}

/** Solo se puede eliminar una tanda vacía. */
export async function eliminarTanda(idTanda: number): Promise<void> {
  await api.delete(`/tandas/${idTanda}`);
}
