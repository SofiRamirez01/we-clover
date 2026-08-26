import api from './api';
import type { PaletaColorCreateRequest, PaletaColorResponse, TipoTela } from '../types/paletaColores';

export async function listarPaletaColores(tipoTela?: TipoTela): Promise<PaletaColorResponse[]> {
  const { data } = await api.get<PaletaColorResponse[]>('/paleta-colores', {
    params: tipoTela ? { tipoTela } : undefined,
  });
  return data;
}

export async function crearColorPaleta(payload: PaletaColorCreateRequest): Promise<PaletaColorResponse> {
  const { data } = await api.post<PaletaColorResponse>('/paleta-colores', payload);
  return data;
}
