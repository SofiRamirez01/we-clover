import api from './api';
import type { TablaTalleOption } from '../types/pieza';

export async function listarTablasTalle(idGrupoTalle: number): Promise<TablaTalleOption[]> {
  const { data } = await api.get<TablaTalleOption[]>('/tablas-talle', {
    params: { idGrupoTalle },
  });
  return data;
}
