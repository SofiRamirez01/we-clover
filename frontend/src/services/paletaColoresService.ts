import api from './api';
import type { PaletaColorCreateRequest, PaletaColorResponse } from '../types/paletaColores';

/**
 * tipoTela es el código de texto (ej. "FRIZA", "RIBB"), no el union `TipoTela` acotado a telas
 * de cuerpo: los insumos secundarios (Capucha/Puños/Cierre) usan códigos fuera de ese union.
 * Sin argumento trae todos los colores activos de cualquier tela.
 */
export async function listarPaletaColores(tipoTela?: string): Promise<PaletaColorResponse[]> {
  const { data } = await api.get<PaletaColorResponse[]>('/paleta-colores', {
    params: tipoTela ? { tipoTela } : undefined,
  });
  return data;
}

export async function crearColorPaleta(payload: PaletaColorCreateRequest): Promise<PaletaColorResponse> {
  const { data } = await api.post<PaletaColorResponse>('/paleta-colores', payload);
  return data;
}
