import api from './api';
import type { TipoTelaCatalogo } from '../types/tipoTela';

export async function listarTiposTela(): Promise<TipoTelaCatalogo[]> {
  const { data } = await api.get<TipoTelaCatalogo[]>('/tipos-tela');
  return data;
}
