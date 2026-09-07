import api from './api';
import type { GrupoTalleOption } from '../types/pieza';

export async function listarGruposTalle(): Promise<GrupoTalleOption[]> {
  const { data } = await api.get<GrupoTalleOption[]>('/grupos-talle');
  return data;
}
