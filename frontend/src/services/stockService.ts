import api from './api';
import type { StockGuardarCambiosRequest, StockResponse } from '../types/stock';

export async function listarStock(): Promise<StockResponse[]> {
  const { data } = await api.get<StockResponse[]>('/stock');
  return data;
}

export async function guardarCambiosStock(payload: StockGuardarCambiosRequest): Promise<StockResponse[]> {
  const { data } = await api.post<StockResponse[]>('/stock/guardar-cambios', payload);
  return data;
}

export async function eliminarFilaStock(id: number): Promise<void> {
  await api.delete(`/stock/${id}`);
}
