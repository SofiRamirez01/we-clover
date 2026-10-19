import api from './api';
import type { TipoInsumoCatalogo } from '../types/tipoInsumo';

export async function listarTiposInsumo(): Promise<TipoInsumoCatalogo[]> {
  const { data } = await api.get<TipoInsumoCatalogo[]>('/tipos-insumo');
  return data;
}
