import api from './api';
import type { ImportacionKommoResponse, SincronizacionKommo } from '../types/kommo';

/** Importa (o simula, con simular=true) los leads de Kommo actualizados en el rango de fechas. */
export async function importarDesdeKommo(params: {
  desde: string;
  hasta: string;
  simular: boolean;
}): Promise<ImportacionKommoResponse> {
  const { data } = await api.post<ImportacionKommoResponse>('/pedidos/importar-kommo', null, { params });
  return data;
}

/** Última importación real desde Kommo; null si nunca se sincronizó (el backend responde 204). */
export async function obtenerUltimaSincronizacionKommo(): Promise<SincronizacionKommo | null> {
  const { data, status } = await api.get<SincronizacionKommo | ''>('/kommo/sincronizaciones/ultima');
  return status === 204 || !data ? null : data;
}
